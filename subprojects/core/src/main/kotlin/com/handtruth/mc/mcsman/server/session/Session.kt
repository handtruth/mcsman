package com.handtruth.mc.mcsman.server.session

import com.handtruth.kommon.BeanContainer
import com.handtruth.kommon.BeanJar
import com.handtruth.kommon.Log
import com.handtruth.mc.mcsman.common.event.listen
import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.mcsman.event.SessionLifeEvent
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.module.Module
import com.handtruth.mc.mcsman.server.server.Server
import com.handtruth.mc.mcsman.server.service.Service
import com.handtruth.mc.mcsman.server.util.Loggable
import com.handtruth.mc.mcsman.server.util.unreachable
import com.handtruth.mc.mcsman.server.util.waitCancellation
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.parameter.parametersOf
import kotlin.coroutines.CoroutineContext

abstract class Session : CoroutineScope, Loggable, BeanContainer, KoinComponent {
    enum class State {
        Stranger, Talking, Closed
    }

    val id = ids.getAndIncrement()

    final override val beanJar = BeanJar.Sync()
    final override val log: Log
    final override val coroutineContext: CoroutineContext

    private val controller: Sessions = get()
    private val events: Events = get()

    init {
        val name = "session/$id"
        log = get { parametersOf(name) }
        coroutineContext = CoroutineName(name) + log + Job(controller.coroutineContext[Job]) + Dispatchers.Default
    }

    lateinit var agent: Agent
        private set
    @Volatile
    var state: State = State.Stranger
        private set

    private val job = launch(start = CoroutineStart.LAZY) {
        controller.putSession(this@Session)
        log.verbose { "new session" }
        try {
            val agent = authorize()
            this@Session.agent = agent
            log.verbose { "client authorized: $agent" }
            state = State.Talking
            events.raise(SessionLifeEvent(id, agent.actorName, direction = true))
            sudo(agent) {
                talking()
            }
        } catch (e: Exception) {
            log.error(e)
        } finally {
            state = State.Closed
            withContext(NonCancellable) {
                try {
                    log.verbose { "closing" }
                    closing()
                } catch (e: Exception) {
                    log.error(e) { "ERROR ON SESSION CLOSING!!!" }
                }
                controller.removeSession(this@Session)
                if (this@Session::agent.isInitialized)
                    events.raise(SessionLifeEvent(id, agent.actorName, direction = false))
                log.verbose { "disconnected" }
            }
        }
    }

    fun start() = job.start()

    private val _listenEvents = atomic(false)

    val listenEvents: Boolean = _listenEvents.value

    private fun beginListen() {
        check(!_listenEvents.getAndSet(true)) { "session already listen events" }
    }

    private fun endListen() {
        _listenEvents.getAndSet(false) || unreachable
    }

    protected val eventFlow: Flow<Event> = flow {
        beginListen()
        try {
            withContext<Nothing>(agent) {
                events.listen {
                    if (events.isAllowed(it))
                        emit(it)
                }
            }
        } finally {
            endListen()
        }
    }

    protected abstract suspend fun authorize(): Agent
    protected open suspend fun talking(): Unit = waitCancellation()
    protected open suspend fun closing() {}

    abstract val connectedModules: Set<Module>
    abstract val connectedServers: Set<Server>
    abstract val connectedServices: Set<Service>

    private companion object {
        private val ids = atomic(1)
    }
}
