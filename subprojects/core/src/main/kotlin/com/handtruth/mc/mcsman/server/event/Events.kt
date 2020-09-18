package com.handtruth.mc.mcsman.server.event

import com.handtruth.kommon.Log
import com.handtruth.kommon.concurrent.ReadWriteMutex
import com.handtruth.kommon.getBeanOrNull
import com.handtruth.kommon.setBean
import com.handtruth.mc.mcsman.common.event.*
import com.handtruth.mc.mcsman.event.*
import com.handtruth.mc.mcsman.server.AgentCheck
import com.handtruth.mc.mcsman.server.MCSManCore
import com.handtruth.mc.mcsman.server.ReactorContext
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.model.internal.DBEventDecoder
import com.handtruth.mc.mcsman.server.model.internal.DBEventEncoder
import com.handtruth.mc.mcsman.server.model.varCharLength
import com.handtruth.mc.mcsman.server.util.Loggable
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.jvmErasure

@MCSManEvent.Table(
    ActorEvent::class,
    UserEvent::class,
    GroupEvent::class,
    PermissionSubjectEvent::class,
    VolumeEvent::class,
    DurationEvent::class,
    ServerEvent::class,
    ServiceEvent::class,
    ModuleEvent::class,
    SessionEvent::class,
    GroupMemberEvent::class,
    ServerCreationEvent::class,
    ChangeServerDescription::class,
    VolumeAccessEvent::class,
    SessionLifeEvent::class,
    ManageServerEvent::class,
    ServerLifeEvent::class,
    SendCommand2ServerEvent::class,
    ChangeVersionServerEvent::class,
    VolumeCreationEvent::class,
    ServerPermissionEvent::class,
    GlobalPermissionEvent::class,
    UserCreationEvent::class,
    BlockUserEvent::class,
    ChangeUserEMailEvent::class,
    ChangeUserRealNameEvent::class,
    GroupCreationEvent::class,
    ChangeOwnerOfGroupEvent::class,
    ChangeGroupRealNameEvent::class,
    ManageServiceEvent::class,
    ServiceLifeEvent::class,
    ServiceCreationEvent::class,
    SendCommand2ServiceEvent::class,
    ImageWildcardEvent::class,
    MCSManLifeEvent::class,
    LoginMethodEvent::class,
    BlockLoginMethodEvent::class
)
open class Events : EventsBase(), KoinComponent, CoroutineScope, Loggable {

    private val db: Database by inject()
    private val json = Json(JsonConfiguration.Stable)

    final override val coroutineContext: CoroutineContext = MCSManCore.fork("event") + Dispatchers.IO
    final override val log: Log = coroutineContext[Log]!!

    private val eventBus = BroadcastChannel<Event>(Channel.BUFFERED)

    override val bus: Flow<Event> get() = eventBus.openSubscription().consumeAsFlow()

    init {
        root.table = EventTable
        root.queries = EventQueries(EventTable)
    }

    @TransactionContext
    private fun store(event: Event): Int {
        val info = describe(event)
        val id = EventTable.insertAndGetId {
            it[className] = info.name
            it[success] = event.success
            it[timestamp] = Instant.now()
        }
        val encoder = DBEventEncoder(json)

        @Suppress("UNCHECKED_CAST")
        val serializer = info.serializer as KSerializer<Event>
        val table = info.table
        table.insert {
            it[table.id] = id
            encoder.switch(table, it)
            serializer.serialize(encoder, event)
        }
        for (parentInfo in info.interfaces) {
            if (parentInfo === root)
                continue
            val parent = parentInfo.table
            parent.insert {
                it[parent.id] = id
                encoder.switch(parent, it)
                serializer.serialize(encoder, event)
            }
        }
        return id.value
    }

    private val reactionMutex = ReadWriteMutex(Runtime.getRuntime().availableProcessors())

    internal suspend fun <R> reaction(
        reactorType: Reactor.Types = Reactor.Types.Unknown,
        block: suspend CoroutineScope.() -> R
    ): R {
        val context = kotlin.coroutines.coroutineContext[CoroutineReactorContext]
        if (context == null) {
            var isolated = when (reactorType) {
                Reactor.Types.Unknown, Reactor.Types.Write -> true
                Reactor.Types.Read -> false
            }
            (if (isolated) reactionMutex.write else reactionMutex.read).lock()
            CoroutineReactorContext().use { root ->
                try {
                    val result = withContext(root + NonCancellable, block)
                    // Success, raise all the events
                    root.pending.forEach { eventBus.send(it) }
                    return result
                } catch (e: Exception) {
                    // Failure, collect events
                    val pending = mutableListOf<Event>()
                    root move pending
                    // Check cancellation possible
                    if (pending.any { it is CancellableEvent }) {
                        // Check isolation level
                        if (!isolated) {
                            log.warning(e) {
                                "cancellable event reactor isolation is " +
                                        "$reactorType. Consider specify reactor type Write explicitly"
                            }
                            reactionMutex.read.unlock()
                            reactionMutex.write.lock()
                            isolated = true
                        }
                        // Execute time machine
                        val append = mutableListOf<Event>()
                        for (i in pending.indices.reversed()) {
                            val next = pending[i]
                            if (next.success && next is CancellableEvent) {
                                @Suppress("UNCHECKED_CAST")
                                val timeReactor = describe(next).reactor as Reactor<CancellableEvent>?
                                if (timeReactor != null) {
                                    // Cancel cancellable event
                                    val cancelled = next.cancel()
                                    append += try {
                                        withContext(root + NonCancellable) {
                                            timeReactor.react(cancelled)
                                        }
                                        root move append
                                        cancelled
                                    } catch (e: Exception) {
                                        // Very bad, but lets stop oscillations here
                                        log.error(e) { "failed to cancel custom reaction" }
                                        root move append
                                        cancelled.fail()
                                    }
                                }
                            }
                        }
                        pending += append
                    }
                    pending.forEach { eventBus.send(it) }
                    throw e
                } finally {
                    (if (isolated) reactionMutex.write else reactionMutex.read).unlock()
                }
            }
        } else {
            return coroutineScope(block)
        }
    }

    override suspend fun raise(event: Event) {
        withContext(Dispatchers.Default + NonCancellable) {
            val eventInfo = describe(event)

            @Suppress("UNCHECKED_CAST")
            val reactor = eventInfo.reactor as Reactor<Event>?
            if (reactor == null) {
                eventBus.send(event)
            } else {
                val context = kotlin.coroutines.coroutineContext[CoroutineReactorContext]
                if (context == null) {
                    var isolated = when (reactor.type) {
                        Reactor.Types.Unknown -> event is CancellableEvent
                        Reactor.Types.Read -> false
                        Reactor.Types.Write -> true
                    }
                    (if (isolated) reactionMutex.write else reactionMutex.read).lock()
                    @Suppress("UNCHECKED_CAST")
                    val corrector = eventInfo.corrector as Corrector<Event>?
                    val corrected = corrector?.correct(event) ?: event
                    CoroutineReactorContext().use { root ->
                        try {
                            withContext(root) {
                                reactor.react(corrected)
                            }
                            // Success, raise all the events
                            root.pending.forEach { eventBus.send(it) }
                            eventBus.send(corrected)
                        } catch (e: Exception) {
                            // Failure, collect events
                            val pending = mutableListOf<Event>()
                            root move pending
                            pending += corrected.fail()
                            // Check cancellation possible
                            if (pending.any { it is CancellableEvent }) {
                                // Check isolation level
                                if (!isolated) {
                                    log.warning(e) {
                                        "cancellable event reactor ($reactor) isolation is " +
                                                "${reactor.type}. Consider specify reactor type Write explicitly"
                                    }
                                    reactionMutex.read.unlock()
                                    reactionMutex.write.lock()
                                    isolated = true
                                }
                                // Execute time machine
                                val append = mutableListOf<Event>()
                                for (i in pending.indices.reversed()) {
                                    val next = pending[i]
                                    if (next.success && next is CancellableEvent) {
                                        @Suppress("UNCHECKED_CAST")
                                        val timeReactor = describe(next).reactor as Reactor<CancellableEvent>?
                                        if (timeReactor != null) {
                                            // Cancel cancellable event
                                            val cancelled = next.cancel()
                                            append += try {
                                                withContext(root + NonCancellable) {
                                                    timeReactor.react(cancelled)
                                                }
                                                root move append
                                                cancelled
                                            } catch (e: Exception) {
                                                // Very bad, but lets stop oscillations here
                                                log.error(e) { "failed to cancel event: $event" }
                                                root move append
                                                cancelled.fail()
                                            }
                                        }
                                    }
                                }
                                pending += append
                            }
                            pending.forEach { eventBus.send(it) }
                            throw e
                        } finally {
                            (if (isolated) reactionMutex.write else reactionMutex.read).unlock()
                        }
                    }
                } else {
                    @Suppress("UNCHECKED_CAST")
                    val corrector = eventInfo.corrector as Corrector<Event>?
                    val corrected = corrector?.correct(event) ?: event
                    try {
                        reactor.react(corrected)
                        context.pending += corrected
                    } catch (e: Exception) {
                        context.pending += corrected.fail()
                        throw e
                    }
                }
            }
        }
    }

    private infix fun CoroutineReactorContext.move(to: MutableCollection<Event>) {
        val queue = pending
        while (queue.isNotEmpty())
            to.add(queue.remove())
    }

    init {
        launch(NonCancellable) {
            listen { event ->
                @OptIn(TransactionContext::class)
                transaction(db) { store(event) }
                log.verbose { "raised event: $event" }
                if (event is MCSManLifeEvent && !event.direction)
                    throw CancellationException()
            }
        }
    }

    @TransactionContext
    fun describeOrNull(id: Int): EventInfo.Full? {
        val event = EventTable.select { EventTable.id eq id }.firstOrNull() ?: return null
        return describe(event[EventTable.className]) as EventInfo.Full?
    }

    @TransactionContext
    fun describe(id: Int) = describeOrNull(id)
        ?: throw NotFoundEventException("event #$id not found in database")

    @TransactionContext
    fun getEventOrNull(id: Int): Event? {
        val info = describeOrNull(id) ?: return null
        return getEvent(id, info)
    }

    @TransactionContext
    fun getEvent(id: Int): Event {
        val info = describe(id)
        return getEvent(id, info)
    }

    @TransactionContext
    private fun getEvent(id: Int, info: EventInfo.Full): Event {
        val queries = info.queries
        val columns = queries.load.columns.associateBy { it.name }
        val result = queries.load.select { EventTable.id eq id }.first()
        val decoder = DBEventDecoder(json)
        val deserializer = info.serializer
        decoder.switch(columns, result)
        return deserializer.deserialize(decoder)
    }

    @TransactionContext
    fun getEvents(info: EventInfo.Full): List<Event> {
        return when (info.type) {
            EventTypes.Class -> getEventShadows(info)
            else -> getEventImplementations(info)
        }
    }

    @TransactionContext
    fun getEvents(info: EventInfo.Full, filter: Op<Boolean>): List<Event> {
        return when (info.type) {
            EventTypes.Class -> getEventShadows(info, filter)
            else -> getEventImplementations(info, filter)
        }
    }

    @TransactionContext
    inline fun getEvents(info: EventInfo.Full, filter: SqlExpressionBuilder.() -> Op<Boolean>): List<Event> =
        getEvents(info, SqlExpressionBuilder.filter())

    @TransactionContext
    fun <E : Event> getEvents(`class`: KClass<out E>): List<E> {
        @Suppress("UNCHECKED_CAST")
        return getEvents(describe(`class`)) as List<E>
    }

    @TransactionContext
    inline fun <reified E : Event> getEvents() = getEvents(E::class)

    @TransactionContext
    inline fun <E : Event> getEvents(`class`: KClass<out E>, filter: SqlExpressionBuilder.() -> Op<Boolean>): List<E> {
        @Suppress("UNCHECKED_CAST")
        return getEvents(describe(`class`), filter) as List<E>
    }

    @TransactionContext
    inline fun <reified E : Event> getEvents(filter: SqlExpressionBuilder.() -> Op<Boolean>) =
        getEvents(E::class, filter)

    @TransactionContext
    private inline fun getEventShadowsFormQuery(info: EventInfo.Full, filter: (ColumnSet) -> Query): List<Event> {
        val queries = info.queries
        val columns = queries.load.columns.associateBy { it.name }
        val decoder = DBEventDecoder(json)
        val deserializer = info.serializer
        return filter(queries.load)
            .orderBy(EventTable.timestamp, SortOrder.DESC).map { result ->
                decoder.switch(columns, result)
                deserializer.deserialize(decoder)
            }
    }

    @TransactionContext
    fun getEventShadows(info: EventInfo.Full, filter: Op<Boolean>): List<Event> =
        getEventShadowsFormQuery(info) { it.select(filter) }

    @TransactionContext
    inline fun getEventShadows(info: EventInfo.Full, filter: SqlExpressionBuilder.() -> Op<Boolean>): List<Event> =
        getEventShadows(info, SqlExpressionBuilder.filter())

    @TransactionContext
    fun getEventShadows(info: EventInfo.Full): List<Event> = getEventShadowsFormQuery(info) { it.selectAll() }

    @Suppress("UNCHECKED_CAST")
    @TransactionContext
    inline fun <E : Event> getEventShadows(
        `class`: KClass<out E>,
        filter: SqlExpressionBuilder.() -> Op<Boolean>
    ): List<E> =
        getEventShadows(describe(`class`), filter) as List<E>

    @TransactionContext
    inline fun <reified E : Event> getEventShadows(filter: SqlExpressionBuilder.() -> Op<Boolean>): List<E> =
        getEventShadows(E::class, filter)

    @Suppress("UNCHECKED_CAST")
    @TransactionContext
    fun <E : Event> getEventShadows(`class`: KClass<out E>): List<E> =
        getEventShadows(describe(`class`)) as List<E>

    @TransactionContext
    inline fun <reified E : Event> getEventShadows(): List<E> = getEventShadows(E::class)

    @TransactionContext
    private fun getEventImplementations(info: EventInfo.Full): List<Event> =
        info.queries.load
            .slice(EventTable.id, EventTable.className)
            .selectAll()
            .map { getEvent(it[EventTable.id].value, describe(it[EventTable.className]) as EventInfo.Full) }

    @TransactionContext
    private fun getEventImplementations(info: EventInfo, filter: Op<Boolean>): List<Event> =
        info.queries.load
            .select { filter }
            .map { getEvent(it[EventTable.id].value, describe(it[EventTable.className]) as EventInfo.Full) }

    // TODO: Remove when https://github.com/Kotlin/kotlinx.serialization/issues/840 will be resolved
    private fun descriptorFor(info: EventInfo.Full, index: Int, name: String): SerialDescriptor {
        return try {
            info.serializer.descriptor.getElementDescriptor(index)
        } catch (e: IndexOutOfBoundsException) {
            val type = info.`class`.memberProperties.find { it.name == name }!!.returnType
            val descriptor = type.jvmErasure.serializer().descriptor
            if (type.isMarkedNullable)
                descriptor.nullable
            else
                descriptor
        }
    }

    private fun tryFindTable(info: EventInfo.Full): InterfaceEventTable? {
        return try {
            val tableClass = info.`class`.java.classLoader.loadClass("${info.`class`.java.name}Table").kotlin
            tableClass.objectInstance as InterfaceEventTable
        } catch (e: ClassNotFoundException) {
            null
        }
    }

    private fun createTable(info: EventInfo.Full): EventTableBase {
        val descriptor = info.serializer.descriptor
        val table = InterfaceEventTable(info.name)
        val fieldNames = descriptor.elementNames().toMutableSet()
        info.interfaces.forEach {
            it as EventInfo.Full
            fieldNames.removeAll(it.serializer.descriptor.elementNames())
        }
        for (name in fieldNames) {
            val index = descriptor.getElementIndex(name)
            val meta = descriptorFor(info, index, name)
            with(table) {
                val column = when (meta.kind) {
                    PrimitiveKind.BOOLEAN -> bool(name)
                    PrimitiveKind.BYTE, PrimitiveKind.SHORT -> short(name)
                    PrimitiveKind.CHAR -> char(name)
                    PrimitiveKind.INT -> integer(name)
                    PrimitiveKind.LONG -> long(name)
                    PrimitiveKind.FLOAT -> float(name)
                    PrimitiveKind.DOUBLE -> double(name)
                    PrimitiveKind.STRING -> varchar(name, varCharLength)
                    else -> table.text(name)
                }
                if (meta.isNullable)
                    column.nullable()
            }
        }
        return table
    }

    private fun prepareTable(info: EventInfo.Full): EventTableBase {
        info.getBeanOrNull<EventTableBase>()?.let { return it }
        val table = tryFindTable(info) ?: createTable(info)
        info.table = table

        val parentTables = info.interfaces.map {
            it.getBeanOrNull<EventTableBase>() ?: prepareTable(it as EventInfo.Full)
        }
        var join = table innerJoin EventTable
        parentTables.forEach {
            it is EventTable && return@forEach
            join = join innerJoin it
        }
        info.queries = EventQueries(join)

        return table
    }

    override fun register(`class`: KClass<out Event>): EventInfo.Full {
        MCSManCore.checkInitialization()
        val info = super.register(`class`)
        prepareTable(info)
        return info
    }

    fun <E : Event> react(`class`: KClass<E>, reactor: Reactor<E>) {
        register(`class`).setBean<Reactor<*>>(reactor)
    }

    inline fun <reified E : Event> react(reactor: Reactor<E>) {
        react(E::class, reactor)
    }

    inline fun <reified E : Event> react(
        type: Reactor.Types = Reactor.Types.Unknown,
        @OptIn(ReactorContext::class)
        noinline reactor: suspend (E) -> Unit
    ) {
        react(object : Reactor<E> {
            override suspend fun react(event: E) = reactor(event)
            override val type = type
            override fun toString() = "reactor: ${E::class.qualifiedName}"
        })
    }

    inline fun <reified E : Event> reactEarlier(
        type: Reactor.Types = Reactor.Types.Unknown,
        @OptIn(ReactorContext::class)
        crossinline reactor: suspend (E) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val oldReactor = register<E>().reactor as Reactor<E>?
        react(object : Reactor<E> {
            override suspend fun react(event: E) {
                reactor(event)
                oldReactor?.react(event)
            }

            override val type = type
            override fun toString() = "reactor: ${E::class.qualifiedName}"
        })
    }

    inline fun <reified E : Event> reactLater(
        type: Reactor.Types = Reactor.Types.Unknown,
        @OptIn(ReactorContext::class)
        crossinline reactor: suspend (E) -> Unit
    ) {
        @Suppress("UNCHECKED_CAST")
        val oldReactor = register<E>().reactor as Reactor<E>?
        react(object : Reactor<E> {
            override suspend fun react(event: E) {
                oldReactor?.react(event)
                reactor(event)
            }

            override val type = type
            override fun toString() = "reactor: ${E::class.qualifiedName}"
        })
    }

    fun <E : Event> correct(`class`: KClass<E>, corrector: Corrector<E>) {
        register(`class`).setBean<Corrector<*>>(corrector)
    }

    inline fun <reified E : Event> correct(corrector: Corrector<E>) {
        correct(E::class, corrector)
    }

    inline fun <reified E : Event> correct(crossinline corrector: suspend (E) -> E) {
        correct(object : Corrector<E> {
            override suspend fun correct(event: E) = corrector(event)
            override fun toString() = "corrector: ${E::class.qualifiedName}"
        })
    }

    fun <E : Event> veto(`class`: KClass<E>, veto: Veto<E>): EventInfo {
        return register(`class`).apply { setBean<Veto<*>>(veto) }
    }

    inline fun <reified E : Event> veto(veto: Veto<E>): EventInfo {
        return veto(E::class, veto)
    }

    inline fun <reified E : Event> vetoOnly(crossinline veto: suspend (E) -> Veto.Answer): EventInfo {
        return veto(object : Veto<E> {
            override suspend fun impose(event: E) = veto(event)
            override fun toString() = "veto: ${E::class.qualifiedName}"
        })
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified E : Event> veto(crossinline veto: suspend (E) -> Veto.Answer): EventInfo {
        val oldVeto = register<E>().veto ?: return vetoOnly(veto)
        oldVeto as Veto<E>
        return veto(object : Veto<E> {
            override suspend fun impose(event: E) = Veto.compose(event, oldVeto,
                object : Veto<E> {
                    override suspend fun impose(event: E) = veto(event)
                    override fun toString() = "veto: ${E::class.qualifiedName}"
                })

            override fun toString() = "veto: ${E::class.qualifiedName}"
        })
    }

    @AgentCheck
    suspend fun isAllowed(event: Event): Boolean {
        val info = describe(event)
        val vetoes = mutableListOf<Veto<*>>()
        info.veto?.let { vetoes += it }
        info.interfaces.mapNotNullTo(vetoes) { it.veto }
        @Suppress("UNCHECKED_CAST")
        vetoes as List<Veto<Event>>
        return when (Veto.compose(event, vetoes)) {
            Veto.Answer.Unknown, Veto.Answer.Deny -> false
            Veto.Answer.Allow -> true
        }
    }

    val tables = all.map { it.table }
}
