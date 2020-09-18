package com.handtruth.mc.mcsman.server.service

import com.handtruth.kommon.Log
import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.common.model.ExecutableActions
import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.mcsman.event.ManageServiceEvent
import com.handtruth.mc.mcsman.event.SendCommand2ServiceEvent
import com.handtruth.mc.mcsman.event.ServiceCreationEvent
import com.handtruth.mc.mcsman.server.AgentCheck
import com.handtruth.mc.mcsman.server.ReactorContext
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.actor.Actor
import com.handtruth.mc.mcsman.server.model.ServiceTable
import com.handtruth.mc.mcsman.server.session.actorName
import com.handtruth.mc.mcsman.server.session.getAgent
import com.handtruth.mc.mcsman.server.util.*
import com.handtruth.mc.mcsman.util.Executable
import com.handtruth.mc.mcsman.util.Removable
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.jetbrains.exposed.sql.update
import org.koin.core.get

abstract class Service : IntIdShadow<Service>(), Savable, Actor, Executable, Removable, TaskBranch {

    final override val controller = get<Services>()

    val name by ServiceTable.name

    final override val coroutineContext = controller.fork(name)
    final override val log = coroutineContext[Log]!!

    final override val stateProperties = mutableListOf<Savable.Property<*>>()
    final override val state: State = allService

    private suspend fun checkPermission(): String {
        val agent = getAgent()
        @OptIn(TransactionContext::class)
        suspendTransaction(controller.db) {
            controller.accesses.global.checkAllowed(agent, GlobalPermissions.service, this@Service)
        }
        return agent.actorName
    }

    private suspend inline fun <R> perform(block: (String) -> R): R = node.invoke {
        val actor = checkPermission()
        block(actor)
    }

    private suspend inline fun raise(event: (String) -> Event) = perform {
        controller.events.raise(event(it))
    }

    private suspend inline fun manage(action: ExecutableActions) = raise {
        ManageServiceEvent(action, name, it)
    }

    @ReactorContext
    protected open suspend fun onCreate() {}
    @ReactorContext
    internal suspend fun invokeOnCreate() = onCreate()

    @AgentCheck
    final override suspend fun start() = manage(ExecutableActions.Start)
    protected open suspend fun onStart() {}

    @AgentCheck
    final override suspend fun stop() = manage(ExecutableActions.Stop)
    protected open suspend fun onStop() {}

    @AgentCheck
    final override suspend fun pause() = manage(ExecutableActions.Pause)
    protected open suspend fun onPause() {}

    @AgentCheck
    final override suspend fun resume() = manage(ExecutableActions.Resume)
    protected open suspend fun onResume() {}

    @AgentCheck
    final override suspend fun kill() = manage(ExecutableActions.Kill)
    protected open suspend fun onKill() {}

    internal suspend fun invokeManage(action: ExecutableActions) = when (action) {
        ExecutableActions.Start -> onStart()
        ExecutableActions.Stop -> onStop()
        ExecutableActions.Pause -> onPause()
        ExecutableActions.Resume -> onResume()
        ExecutableActions.Kill -> onKill()
    }

    @AgentCheck
    final override suspend fun status(): ExecutableStatus = perform { getStatus() }
    protected abstract suspend fun getStatus(): ExecutableStatus

    private val sendChannel = Channel<String>()
    protected val send: ReceiveChannel<String> get() = sendChannel
    internal val internalSend: SendChannel<String> get() = sendChannel

    @AgentCheck
    override suspend fun send2input(line: String) = raise { SendCommand2ServiceEvent(line, name, it) }

    private val inputChannel = BroadcastChannel<String>(1)
    protected val input: SendChannel<String> get() = inputChannel

    @AgentCheck
    override suspend fun subscribeInput(): ReceiveChannel<String> = perform {
        inputChannel.openSubscription()
    }

    private val outputChannel = BroadcastChannel<String>(1)
    protected val output: SendChannel<String> get() = outputChannel

    @AgentCheck
    override suspend fun subscribeOutput(): ReceiveChannel<String> = perform {
        outputChannel.openSubscription()
    }

    private val errorsChannel = BroadcastChannel<String>(1)
    protected val errors: SendChannel<String> get() = errorsChannel

    @AgentCheck
    override suspend fun subscribeErrors(): ReceiveChannel<String> = perform {
        errorsChannel.openSubscription()
    }

    protected open suspend fun onUnload() {}

    final override fun onDispose() {
        controller.launch {
            controller.serviceMutex.withLock {
                onUnload()
                save()
                val bytes = state.toByteArray()
                suspendTransaction(controller.db) {
                    val table = ServiceTable
                    table.update({ table.id eq this@Service.id }) { it[table.state] = ExposedBlob(bytes) }
                }
            }
            log.verbose { "service \"$name\" saved" }
            inputChannel.close()
            outputChannel.close()
            errorsChannel.close()
            super.onDispose()
        }
    }

    override suspend fun remove() = raise {
        ServiceCreationEvent(ByteArray(0), "", name, it, false)
    }

    @ReactorContext
    protected open suspend fun onRemove() {}

    @ReactorContext
    final override suspend fun onDelete() {
        controller.serviceMutex.withLock {
            onRemove()
            super.onDelete()
        }
    }

    override fun toString() = "service: $name"
}
