package com.handtruth.mc.mcsman.server

import com.handtruth.kommon.FatalError
import com.handtruth.kommon.Log
import com.handtruth.kommon.LogLevel
import com.handtruth.kommon.default
import com.handtruth.mc.mcsman.event.MCSManLifeEvent
import com.handtruth.mc.mcsman.server.bundle.Bundles
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.module.Modules
import com.handtruth.mc.mcsman.server.util.defaultExceptionHandler
import io.ktor.network.selector.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.koin.core.KoinComponent
import org.koin.core.context.startKoin
import org.koin.core.get
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module
import org.koin.logger.slf4jLogger
import kotlin.concurrent.thread
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.system.exitProcess

object MCSManCore : KoinComponent, CoroutineScope {
    private val log: Log = Log.default("mcsman", LogLevel.Debug)

    override var coroutineContext: CoroutineContext = EmptyCoroutineContext
    internal var dispatcher: CoroutineDispatcher? = null
    private var thread: Thread? = null
    val isMCSManThread get() = Thread.currentThread() == thread

    fun checkMCSManThread() {
        if (!isMCSManThread)
            throw NotMCSManThreadException()
    }

    lateinit var selector: SelectorManager
        private set

    fun fork(part: String, lvl: LogLevel = LogLevel.Error, supervised: Boolean = true): CoroutineContext {
        val parent = coroutineContext[Job]!!
        val tag = "mcsman/$part"
        val log = get<Log> { parametersOf(tag) }
        val job = if (supervised)
            SupervisorJob(parent)
        else
            Job(parent)
        val handler = defaultExceptionHandler(log, lvl)
        val name = CoroutineName(tag)
        return handler + name + log + job + Dispatchers.MCSMan
    }

    enum class Phases {
        NoOperation, Initialization, Synchronization, Running, Stopping
    }

    @Volatile
    var phase: Phases = Phases.NoOperation
        private set

    fun checkPhase(required: Phases) {
        if (phase != required)
            throw WrongPhaseMCSManException(required, phase)
    }

    internal fun checkInitialization() {
        checkMCSManThread()
        checkPhase(Phases.Initialization)
    }

    fun start() {
        runBlocking(CoroutineName("mcsman") + log) {
            val job = SupervisorJob(this.coroutineContext[Job]!!)
            this@MCSManCore.coroutineContext = this.coroutineContext + job
            val thread = Thread.currentThread()
            this@MCSManCore.thread = thread
            dispatcher = coroutineContext[CoroutineDispatcher]
            thread.name = "mcsman"
            try {
                @OptIn(KtorExperimentalAPI::class)
                val selectorManager = ActorSelectorManager(
                    this@MCSManCore.coroutineContext +
                            CoroutineName("${Definitions.name}/selector") + Dispatchers.IO
                )
                selector = selectorManager
                phase = Phases.Initialization
                val selectorModule = module {
                    single<SelectorManager>(createdAtStart = true) { selectorManager }
                }
                startKoin {
                    modules(appModule, selectorModule)
                    slf4jLogger()
                }
                log.info { "initializing modules..." }
                val bundles = get<Bundles>()
                bundles.findBundles()
                setLogLevel(get<Configuration>().verb)
                log.info { "configuration loaded" }
                val initialized = get<Modules>().initializeModules()
                log.debug { "initialization order: ${initialized.map { it.name }}" }
                phase = Phases.Synchronization
                log.info { "synchronization..." }
                get<Synchronizer>().initialize()
                log.info { "starting server socket..." }
                phase = Phases.Running
                log.info { "MCSMan started" }
                get<Events>().raise(MCSManLifeEvent())
                job.join()
            } catch (e: FatalError) {
                exitProcess(1)
            } catch (thr: Throwable) {
                try {
                    log.fatal(thr) { "error while initialization" }
                } catch (fatal: FatalError) {
                    exitProcess(2)
                }
            }
        }
    }

    @JvmStatic @PublishedApi
    internal fun main(args: Array<String>) {
        val hook = thread(start = false, name = "shutdown") {
            log.info { "shutdown sequence started..." }
            runBlocking {
                phase = Phases.Stopping
                get<Events>().raise(MCSManLifeEvent(direction = false))
                this@MCSManCore.coroutineContext[Job]!!.cancelAndJoin()
            }
            log.info { "mcsman stopped" }
        }
        Runtime.getRuntime().addShutdownHook(hook)
        start()
    }
}
