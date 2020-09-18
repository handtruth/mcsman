package com.handtruth.mc.mcsman.server.module

import com.handtruth.kommon.BeanJar
import com.handtruth.kommon.Log
import com.handtruth.kommon.LogLevel
import com.handtruth.mc.mcsman.common.module.Artifact
import com.handtruth.mc.mcsman.server.MCSManCore
import com.handtruth.mc.mcsman.server.util.Loggable
import com.handtruth.mc.paket.PaketTransmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.DeserializationStrategy
import org.koin.core.KoinComponent
import org.koin.core.get
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Base class for all MCSMan modules. If you want your module to be configurable, you should not extend this class but
 * rather look at [ConfigurableModule].
 *
 * @property name module name. This property is used by module identification between MCSMan restarts
 * @property configDeserializer deserialization strategy for module configuration in MCSMan configuration tree
 */
abstract class Module(
    name: String = "",
    val configDeserializer: DeserializationStrategy<out ModuleConfig> = SimpleModuleConfig.serializer()
) : BeanJar.Sync(), Loggable, CoroutineScope, KoinComponent {

    private fun createName(): String {
        val className = this::class.simpleName!!
        return (if (className.endsWith("module", ignoreCase = true))
            className.dropLast(6)
        else
            className).toLowerCase()
    }

    val name: String = if (name.isEmpty()) createName() else name

    /**
     * Module runtime identity. Mostly used in protocol communication. This property available after [initialize]
     * execution.
     */
    val id: Int by lazy { get<Modules>().describe(this).id }

    /**
     * Configuration class instance. This property available after [initialize] execution.
     *
     * @see ModuleConfig
     */
    protected open val config: ModuleConfig by lazy { get<Modules>().configOf(this) }

    lateinit var artifacts: List<Artifact>
        internal set

    /**
     * This function will be executed by MCSMan itself for each module in order. There should be performed registration
     * for annotations and tables creation.
     */
    protected open fun initialize() {}

    internal fun invokeInitialize() {
        initialize()
    }

    protected open fun shutdown() {}

    internal fun invokeShutdown() {
        shutdown()
    }

    protected fun <R> blocking(
        context: CoroutineContext = EmptyCoroutineContext,
        block: suspend CoroutineScope.() -> R
    ): R {
        MCSManCore.checkInitialization()
        return runBlocking(coroutineContext.minusKey(ContinuationInterceptor) + context, block)
    }

    /**
     * This function is called by MCSMan when client tries to connect to this module. By default connection drops
     * immediately. To handle client connections one should define MCSMan protocol extension through
     * com.handtruth.mc:paket-kotlin library.
     *
     * @see PaketTransmitter
     * @see com.handtruth.mc.paket.Paket
     */
    open fun onConnection(ts: ModulePaketTransmitter) {
        ts.close()
    }

    final override val coroutineContext = get<Modules>().fork(this.name, LogLevel.Fatal)
    final override val log = coroutineContext[Log]!!
}

/**
 * This class should be extended to define configurable module instances. Configurable module should have [ModuleConfig]
 * configuration class.
 *
 * @param name module name. This property is used by module identification between MCSMan restarts
 * @param configDeserializer deserialization strategy for module configuration in MCSMan configuration tree
 *
 * @see ModuleConfig
 */
abstract class ConfigurableModule<C : ModuleConfig>(
    name: String = "",
    configDeserializer: DeserializationStrategy<C>
) : Module(name, configDeserializer) {

    /**
     * Configuration class instance. This property available after [initialize] execution.
     *
     * @see ModuleConfig
     */
    @Suppress("UNCHECKED_CAST")
    final override val config: C
        get() = super.config as C

}
