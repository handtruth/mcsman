package com.handtruth.mc.mcsman.server.service

import com.handtruth.kommon.Log
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.event.ServiceCreationEvent
import com.handtruth.mc.mcsman.server.AgentCheck
import com.handtruth.mc.mcsman.server.MCSManCore
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.access.Accesses
import com.handtruth.mc.mcsman.server.docker.Attachments
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.model.ServiceTable
import com.handtruth.mc.mcsman.server.server.Servers
import com.handtruth.mc.mcsman.server.session.getActorName
import com.handtruth.mc.mcsman.server.session.getAgent
import com.handtruth.mc.mcsman.server.util.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.inject
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

private val allServiceVar = ThreadLocal<State>()
internal var allService
    get() = allServiceVar.get() ?: error("you may not instantiate service class directly")
    private set(value) {
        allServiceVar.set(value)
    }

open class Services : IntIdShadow.IntIdController<Service>(ServiceTable), NamedShadowsController<Service, Int>,
    TaskBranch {

    internal val events: Events by inject()
    internal val accesses: Accesses by inject()
    internal val attachments: Attachments by inject()
    internal val servers: Servers by inject()

    final override val coroutineContext = MCSManCore.fork("service")
    final override val log = coroutineContext[Log]!!

    internal val serviceMutex = Mutex()

    override suspend fun spawn(): Service {
        serviceMutex.withLock {
            val typeName = allRow[ServiceTable.className]
            val state = NBTComponentState(allRow[ServiceTable.state].bytes)
            allService = state

            val type = serviceClasses[typeName] ?: throw NotExistsMCSManException("service type is not registered")
            val constructor = type.constructors.find { it.hasAnnotation<MCSManService.Constructor>() }
                ?: type.primaryConstructor ?: error("service ($type) has no valid constructors")
            val args: Map<KParameter, Any?> = constructor.parameters.associateWith { parameter ->
                val typeParameter = parameter.type
                val klass = typeParameter.jvmErasure
                val annotation = parameter.findAnnotation<MCSManService.State>()
                when {
                    State::class.isSubclassOf(klass) -> state
                    annotation != null -> {
                        val name = annotation.name.let { if (it.isEmpty()) parameter.name!! else it }
                        val serializer = serializer(typeParameter)
                        if (typeParameter.isMarkedNullable)
                            state.loadOrNull(name, serializer)
                        else
                            @Suppress("UNCHECKED_CAST")
                            state.load(name, serializer as KSerializer<Any>)
                    }
                    else -> getKoin().get(klass)
                }
            }
            return type.primaryConstructor!!.callBy(args)
        }
    }

    override suspend fun getOrNull(name: String): Service? {
        val table = ServiceTable
        return findOne(table.select { table.name eq name })
    }

    private val serviceClasses: MutableMap<String, KClass<out Service>> = hashMapOf()

    internal fun register(type: KClass<out Service>) {
        MCSManCore.checkInitialization()
        serviceClasses[type.qualifiedName!!] = type
    }

    @AgentCheck
    suspend fun listNames(): Map<Int, String> {
        val agent = getAgent()
        @OptIn(TransactionContext::class)
        return suspendTransaction(db) {
            accesses.global.checkAllowed(agent, GlobalPermissions.service, "list services")
            val table = ServiceTable
            table.slice(table.id, table.name).selectAll().associate { it[table.id].value to it[table.name] }
        }
    }

    @AgentCheck
    suspend fun getId(name: String): Int {
        val agent = getAgent()
        @OptIn(TransactionContext::class)
        return suspendTransaction(db) {
            accesses.global.checkAllowed(agent, GlobalPermissions.service, "get service id")
            val table = ServiceTable
            table.slice(table.id).select { table.name eq name }.limit(1).firstOrNull()?.let { it[table.id].value }
                ?: throw NotExistsMCSManException("service $name does not exists")
        }
    }

    @AgentCheck
    suspend fun <S : Service> findByType(`class`: KClass<out S>): List<S> {
        accesses.global.checkAllowed(GlobalPermissions.service, "find services")
        val keys = serviceClasses.entries.mapNotNull { if (it.value.isSubclassOf(`class`)) it.key else null }
        val table = ServiceTable
        @Suppress("UNCHECKED_CAST")
        return loadAll(table.select { table.className inList keys }) as List<S>
    }

    @AgentCheck
    suspend inline fun <reified S : Service> findByType(): List<S> = findByType(S::class)

    suspend fun <S : Service> create(type: KClass<out S>, name: String, initial: SaveState): S {
        val actor = getActorName()
        events.raise(ServiceCreationEvent(initial.toByteArray(), type.qualifiedName!!, name, actor, true))
        @Suppress("UNCHECKED_CAST")
        return get(name) as S
    }

    suspend inline fun <S : Service> create(type: KClass<out S>, name: String, initial: SaveState.() -> Unit = {}): S {
        return create(type, name, SaveState().apply(initial))
    }

    suspend inline fun <reified S : Service> create(name: String, initial: SaveState): S {
        return create(S::class, name, initial)
    }

    suspend inline fun <reified S : Service> create(name: String, initial: SaveState.() -> Unit = {}): S {
        return create(S::class, name, initial)
    }

}
