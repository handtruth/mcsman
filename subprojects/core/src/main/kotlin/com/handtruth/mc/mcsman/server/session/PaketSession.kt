package com.handtruth.mc.mcsman.server.session

import com.handtruth.mc.chat.ChatMessageJsonSerializer
import com.handtruth.mc.mcsman.AccessDeniedMCSManException
import com.handtruth.mc.mcsman.AlreadyInStateMCSManException
import com.handtruth.mc.mcsman.AuthenticationMCSManException
import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.common.access.ServerPermissions
import com.handtruth.mc.mcsman.common.access.VolumeAccessLevel
import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.mcsman.common.model.ExecutableActions
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.protocol.*
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.access.Accesses
import com.handtruth.mc.mcsman.server.actor.Actor
import com.handtruth.mc.mcsman.server.actor.Actors
import com.handtruth.mc.mcsman.server.actor.Group
import com.handtruth.mc.mcsman.server.actor.User
import com.handtruth.mc.mcsman.server.bundle.Bundles
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.module.Module
import com.handtruth.mc.mcsman.server.module.ModulePaketTransmitter
import com.handtruth.mc.mcsman.server.module.Modules
import com.handtruth.mc.mcsman.server.server.Server
import com.handtruth.mc.mcsman.server.server.Servers
import com.handtruth.mc.mcsman.server.service.Service
import com.handtruth.mc.mcsman.server.service.Services
import com.handtruth.mc.mcsman.server.util.*
import com.handtruth.mc.mcsman.util.Executable
import com.handtruth.mc.paket.*
import io.ktor.network.sockets.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.jetbrains.exposed.sql.Database
import org.koin.core.get
import java.util.concurrent.ConcurrentHashMap

class PaketSession(val socket: Socket) : Session() {
    private val sessions: Sessions = get()
    private val modules: Modules = get()
    private val servers: Servers = get()
    private val services: Services = get()
    private val actors: Actors = get()
    private val db: Database = get()
    private val accesses: Accesses = get()
    private val bundles: Bundles = get()
    private val events: Events = get()

    private class SessionUserAgent(override val represent: User) : UserAgent {

        private val atomicPrivileged = atomic(false)

        override var isPrivileged: Boolean
            get() = atomicPrivileged.value
            set(value) {
                atomicPrivileged.value = value
            }

        override fun toString() = "Agent($represent)"
    }

    private class SessionServiceAgent(override val represent: Service) : ServiceAgent {
        override fun toString() = "Agent($represent)"
    }

    private val ts = PaketTransmitter(socket.openReadChannel(), socket.openWriteChannel())

    override suspend fun authorize(): Agent {
        run {
            // Good old handshake
            val hs = withTimeout(1500) {
                ts.receive(HandshakePaket)
            }
            log.debug { "handshake received: $hs" }
            if (hs.version != 3)
                throw IllegalProtocolStateException("client sent wrong protocol version: ${hs.version}")
        }
        val actor: Actor = run {
            // Authorization
            withTimeout(1500) {
                ts.catchOrdinal()
                ts.peek(AuthorizationPaket)
            }
            recognizers.invokable(ts to this) ?: run {
                ts.send(
                    ErrorPaket(
                        ErrorPaket.ErrorCodes.Auth,
                        PaketID.Authorization,
                        "failed to authorize session"
                    )
                )
                throw AuthenticationMCSManException("failed to authorize session")
            }
        }
        ts.send(ErrorPaket.success(PaketID.Authorization))
        ts.drop()
        return when (actor) {
            is User -> SessionUserAgent(actor)
            is Service -> SessionServiceAgent(actor)
            else -> throw UnsupportedOperationException("currently MCSMan supports only user or service agent")
        }
    }

    override suspend fun talking() {
        val tss = run {
            // Spit receiver to main receiver and modules
            val mCount = modules.count()
            val extensionPaket = ExtensionPaket.Header.produce()
            ts.split(1 + mCount) {
                when (it.id) {
                    PaketID.NoOp -> -1
                    PaketID.Extension -> {
                        it.peek(extensionPaket)
                        val tsn = extensionPaket.module
                        check(tsn in 0 until mCount) { "module id does not belong to any module: $tsn" }
                        when (extensionPaket.type) {
                            ExtensionPaket.Types.Operate -> tsn + 1
                            ExtensionPaket.Types.Disconnect -> 0
                        }
                    }
                    else -> 0
                }
            }
        }
        val iter = tss.iterator()
        val main = iter.next()
        // launch coroutine for each module receiver
        iter.withIndex().forEach {
            launch(agent) { extensionHandler(it.index, it.value) }
        }
        main.use { mainHandler(it) }
    }

    override suspend fun closing() {
        socket.close()
        ts.close()
    }

    private val sendEvent = EventStreamPaket(events)
    private val recvEvent = EventStreamPaket(events)

    private suspend fun mainHandler(ts: PaketTransmitter): Nothing {
        log.debug { "interred main handler" }
        ts.receiveAll {
            @Suppress("BlockingMethodInNonBlockingContext")
            when (val paketId = id) {
                PaketID.EventStream -> {
                    ts.handle {
                        it.peek(recvEvent)
                            if (!agent.isAdmin)
                                throw AccessDeniedMCSManException("event", agent.represent, "(privileged)")
                        events.raise(recvEvent.event)
                        null
                    }
                }
                PaketID.Stream -> {
                    val paket = ts.peek(StreamPaket)
                    when (paket.type) {
                        StreamPaket.Types.Input -> {
                            try {
                                val shadow: Executable = when (paket.source) {
                                    StreamPaket.Sources.Server -> servers.get(paket.identity)
                                    StreamPaket.Sources.Service -> services.get(paket.identity)
                                }
                                shadow.send2input(paket.line)
                            } catch (e: Exception) {
                                log.error(e) { "error on stream paket" }
                            }
                        }
                        else -> {
                            log.warning { "unable to send output stream paket from client anywhere" }
                        }
                    }
                }
                PaketID.Extension -> {
                    val paket = ts.peek(ExtensionPaket.Header)
                    assert(paket.type == ExtensionPaket.Types.Disconnect)
                    moduleConnections[modules.get(paket.module)]?.close() ?: log.warning {
                        "trying to close not opened connection to module ${paket.module}"
                    }
                }
                PaketID.Bundle -> ts.handleTyped(BundlePaket) { bundleHandler(it) }
                PaketID.Module -> ts.handleTyped(ModulePaket) { moduleHandler(it) }
                PaketID.Event -> ts.handleTyped(EventPaket) { eventHandler(it) }
                PaketID.Server -> ts.handleTyped(ServerPaket) { serverHandler(it) }
                PaketID.Service -> ts.handleTyped(ServicePaket) { serviceHandler(it) }
                PaketID.Volume -> ts.handleTyped(VolumePaket) { volumeHandler(it) }
                PaketID.Session -> ts.handleTyped(SessionPaket) { sessionHandle(it) }
                PaketID.User -> ts.handleTyped(UserPaket) { userHandle(it) }
                PaketID.Group -> ts.handleTyped(GroupPaket) { groupHandle(it) }
                PaketID.GlobalAccess -> ts.handleTyped(GlobalAccessPaket) { globalAccessHandle(it) }
                PaketID.ImageAccess -> ts.handleTyped(ImageAccessPaket) { imageAccessHandle(it) }
                PaketID.ServerAccess -> ts.handleTyped(ServerAccessPaket) { serverAccessHandle(it) }
                PaketID.VolumeAccess -> ts.handleTyped(VolumeAccessPaket) { volumeAccessHandle(it) }
                else -> log.warning { "unknown paket: $paketId" }
            }
        }
    }

    private fun PaketPeeking.bundleHandler(type: BundlePaket.Types): BundlePaket {
        return when (type) {
            BundlePaket.Types.GetId -> {
                val paket = peek(BundlePaket.GetIdRequest)
                val bundle = bundles.get(paket.group, paket.artifact)
                BundlePaket.GetIdResponse(bundle.id, bundle.group, bundle.artifact)
            }
            BundlePaket.Types.Get -> {
                val paket = peek(BundlePaket.GetRequest)
                val bundle = bundles.get(paket.bundle)
                val list = mutableListOf<Int>()
                bundle.dependencies.mapTo(list) { it.id }
                BundlePaket.GetResponse(bundle.id, bundle.group, bundle.artifact, bundle.version.toString(), list)
            }
            BundlePaket.Types.List -> {
                val list = mutableListOf<Int>()
                bundles.all.mapTo(list) { it.id }
                BundlePaket.ListResponse(list)
            }
        }
    }

    // Only 1 thread will write to the map at any time, but there can be multiple readers.
    // I believe in such scenario ConcurrentHashMap will never block a thread.
    private val moduleConnections: MutableMap<Module, ModulePaketTransmitter> = ConcurrentHashMap(1)
    override val connectedModules: Set<Module> get() = moduleConnections.keys

    private inner class ModulePaketTransmitterImpl(
        override val module: Module,
        private val ts: NestPaketTransmitter
    ) : ModulePaketTransmitter, PaketTransmitter {

        constructor(module: Module, ts: PaketTransmitter) : this(module, ts nest ExtensionPaket.Source(module.id))

        override val session get() = this@PaketSession

        override var broken: Boolean = false
            get() = ts.broken || field
            private set

        private inline fun <R> notBroken(block: () -> R): R {
            if (broken)
                throw BrokenObjectException()
            return block()
        }

        override val idOrdinal get() = notBroken { ts.idOrdinal }
        override val isCaught get() = notBroken { ts.isCaught }
        override val size get() = notBroken { ts.size }
        override suspend fun catchOrdinal() = notBroken { ts.catchOrdinal() }
        override suspend fun drop() = notBroken { ts.drop() }
        override suspend fun receive(paket: Paket) = notBroken { ts.receive(paket) }
        override fun peek(paket: Paket) = notBroken { ts.peek(paket) }
        override suspend fun send(paket: Paket) = notBroken { ts.send(paket) }

        override fun close() {
            // TODO: Seems to be blocking...
            moduleConnections.remove(module)
            broken = true
            launch(agent) {
                ts.send(ExtensionPaket.Disconnect(module.id))
                extensionHandler(module.id, ts)
            }
        }
    }

    private suspend fun extensionHandler(moduleId: Int, ts: PaketTransmitter) {
        assert(ts.catch() == PaketID.Extension)
        val module = modules.get(moduleId)
        val mts = ModulePaketTransmitterImpl(module, ts)
        moduleConnections[module] = mts
        module.onConnection(mts)
    }

    private fun PaketPeeking.moduleHandler(type: ModulePaket.Types): ModulePaket {
        return when (type) {
            ModulePaket.Types.GetId -> {
                val paket = peek(ModulePaket.GetIdRequest)
                val module = modules.get(paket.name)
                ModulePaket.GetIdResponse(module.id, module.name)
            }
            ModulePaket.Types.Get -> {
                val paket = peek(ModulePaket.GetRequest)
                val info = modules.describeById(paket.module)
                val deps = mutableListOf<Int>()
                info.after.mapTo(deps) { it.id }
                ModulePaket.GetResponse(
                    info.id, info.module.name, bundles.get(info.module).id,
                    modules.configOf(info.module).enable, deps
                )
            }
            ModulePaket.Types.List -> {
                val ids = modules.ids.toMutableList()
                val names = modules.names.toMutableList()
                ModulePaket.ListResponse(ids, names)
            }
            ModulePaket.Types.Artifacts -> {
                val paket = peek(ModulePaket.ArtifactsRequest)
                val module = modules.get(paket.module)
                val types = mutableListOf<String>()
                val classes = mutableListOf<String>()
                val platforms = mutableListOf<String>()
                val uris = mutableListOf<String>()
                val artifactType = paket.artifactType
                val artifactClass = paket.`class`
                val platform = paket.platform
                for (artifact in module.artifacts) {
                    if (artifactType.isNotEmpty() && artifactType != artifact.type)
                        continue
                    if (artifactClass.isNotEmpty() && artifactClass != artifact.`class`)
                        continue
                    if (platform.isNotEmpty() && platform != artifact.platform)
                        continue
                    types += artifact.type
                    classes += artifact.`class`
                    platforms += artifact.platform
                    uris += artifact.uri.toString()
                }
                ModulePaket.ArtifactsResponse(module.id, types, classes, platforms, uris)
            }
        }
    }

    private fun PaketPeeking.eventHandler(type: EventPaket.Types): EventPaket? {
        return when (type) {
            EventPaket.Types.Get -> {
                val paket = peek(EventPaket.GetRequest)
                val eventInfo = events.describe(paket.className)
                val interfaces = mutableListOf<String>()
                eventInfo.interfaces.mapTo(interfaces) { it.name }
                EventPaket.GetResponse(eventInfo.name, bundles.get(eventInfo).id, eventInfo.type, interfaces)
            }
            EventPaket.Types.List -> {
                val all = mutableListOf<String>()
                events.all.mapTo(all) { it.name }
                EventPaket.ListResponse(all)
            }
            EventPaket.Types.Listen -> {
                if (listenEvents)
                    throw AlreadyInStateMCSManException("already listened")
                startEventsJob(this as PaketSender)
                null
            }
            EventPaket.Types.Mute -> {
                if (!listenEvents)
                    throw AlreadyInStateMCSManException("already muted")
                eventsJob.cancel()
                null
            }
        }
    }

    private fun CoroutineScope.forward(
        receiver: ReceiveChannel<String>, sender: PaketSender,
        type: StreamPaket.Types, source: StreamPaket.Sources, identity: Int
    ) {
        launch {
            try {
                for (line in receiver)
                    sender.send(StreamPaket(source, identity, type, line))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.error(e) { "error on output listen" }
            } finally {
                receiver.cancel()
            }
        }
    }

    private fun spawnListener(
        input: ReceiveChannel<String>, output: ReceiveChannel<String>, errors: ReceiveChannel<String>,
        source: StreamPaket.Sources, tx: PaketSender, connections: MutableSet<out IntIdShadow<*>>,
        shadow: IntIdShadow<*>
    ): Job {
        return launch(agent) {
            try {
                coroutineScope {
                    forward(input, tx, StreamPaket.Types.Input, source, shadow.id)
                    forward(output, tx, StreamPaket.Types.Output, source, shadow.id)
                    forward(errors, tx, StreamPaket.Types.Errors, source, shadow.id)
                }
            } finally {
                connections.remove(shadow)
            }
        }
    }

    private fun <A, B> Map<A, B>.split(): Pair<MutableList<A>, MutableList<B>> {
        val keys = mutableListOf<A>()
        val values = mutableListOf<B>()
        forEach { (k, v) -> keys += k; values += v }
        return keys to values
    }

    private val serverConnections: MutableMap<Server, Job> = ConcurrentHashMap(1)
    override val connectedServers: Set<Server> get() = serverConnections.keys

    private suspend fun PaketPeeking.serverHandler(type: ServerPaket.Types): ServerPaket? {
        return when (type) {
            ServerPaket.Types.GetId -> {
                val paket = peek(ServerPaket.GetIdRequest)
                val id = servers.getId(paket.name)
                ServerPaket.GetIdResponse(id, paket.name)
            }
            ServerPaket.Types.Get -> {
                val paket = peek(ServerPaket.GetRequest)
                val server = servers.get(paket.server)
                val agent = getAgent()
                @OptIn(TransactionContext::class)
                suspendTransaction(db) {
                    accesses.server.checkAllowed(agent, server, ServerPermissions.info)
                }
                val volumes = mutableListOf<Int>()
                server.volumes.buffer().collect { volumes += it.id }
                val info = server.info.await()
                ServerPaket.GetResponse(
                    server.id, server.name, server.status(), info.imageID, server.imageName()?.toString().orEmpty(),
                    server.game.orEmpty(), server.description, volumes
                )
            }
            ServerPaket.Types.List -> {
                val (ids, names) = servers.listNames().split()
                ServerPaket.ListResponse(ids, names)
            }
            ServerPaket.Types.Status -> {
                val paket = peek(ServerPaket.StatusRequest)
                val status = servers.get(paket.server).status()
                ServerPaket.StatusResponse(paket.server, status)
            }
            ServerPaket.Types.Create -> {
                val paket = peek(ServerPaket.CreateRequest)
                val image = if (paket.image.isEmpty()) null else ImageName(paket.image)
                val description =
                    if (paket.description.isEmpty()) null
                    else json.parse(ChatMessageJsonSerializer, paket.description)
                val server = servers.create(paket.name, image, description)
                ServerPaket.CreateResponse(server.id, server.name)
            }
            ServerPaket.Types.Manage -> {
                val paket = peek(ServerPaket.ManageRequest)
                val server = servers.get(paket.server)
                when (paket.action) {
                    ExecutableActions.Start -> server.start()
                    ExecutableActions.Stop -> server.stop()
                    ExecutableActions.Pause -> server.pause()
                    ExecutableActions.Resume -> server.resume()
                    ExecutableActions.Kill -> server.kill()
                }
                null
            }
            ServerPaket.Types.Command -> {
                val paket = peek(ServerPaket.CommandRequest)
                val server = servers.get(paket.server)
                server.send2input(paket.command)
                null
            }
            ServerPaket.Types.Listen -> {
                val paket = peek(ServerPaket.ListenRequest)
                val server = servers.get(paket.server)
                serverConnections.containsKey(server) &&
                        throw AlreadyInStateMCSManException("server \"${server.name}\" is already being listened")
                val input = server.subscribeInput()
                val output = server.subscribeOutput()
                val errors = server.subscribeErrors()
                val tx = this as PaketSender
                serverConnections[server] =
                    spawnListener(
                        input, output, errors,
                        StreamPaket.Sources.Server, tx, serverConnections.keys, server
                    )
                null
            }
            ServerPaket.Types.Mute -> {
                val paket = peek(ServerPaket.MuteRequest)
                val server = servers.get(paket.server)
                val connection = serverConnections[server]
                    ?: throw AlreadyInStateMCSManException("not connected to server \"${server.name}\"")
                connection.cancelAndJoin()
                null
            }
            ServerPaket.Types.ChangeDescription -> {
                val paket = peek(ServerPaket.ChangeDescriptionRequest)
                val server = servers.get(paket.server)
                server.changeDescription(json.parse(ChatMessageJsonSerializer, paket.description))
                null
            }
            ServerPaket.Types.Upgrade -> {
                val paket = peek(ServerPaket.UpgradeRequest)
                val server = servers.get(paket.server)
                server.upgrade()
                null
            }
            ServerPaket.Types.Remove -> {
                val paket = peek(ServerPaket.RemoveRequest)
                val server = servers.get(paket.server)
                server.remove()
                null
            }
        }
    }

    private val serviceConnections: MutableMap<Service, Job> = ConcurrentHashMap(1)
    override val connectedServices: Set<Service> get() = serviceConnections.keys

    private suspend fun PaketPeeking.serviceHandler(type: ServicePaket.Types): ServicePaket? {
        return when (type) {
            ServicePaket.Types.GetId -> {
                val paket = peek(ServicePaket.GetIdRequest)
                val id = services.getId(paket.name)
                ServicePaket.GetIdResponse(id, paket.name)
            }
            ServicePaket.Types.Get -> {
                val paket = peek(ServicePaket.GetRequest)
                val agent = getAgent()
                @OptIn(TransactionContext::class)
                suspendTransaction(db) {
                    accesses.global.checkAllowed(agent, GlobalPermissions.service, "service info")
                }
                privileged {
                    val service = services.get(paket.service)
                    ServicePaket.GetResponse(
                        service.id,
                        service.name,
                        bundles.get(service).id,
                        service.status(),
                        service::class.qualifiedName!!
                    )
                }
            }
            ServicePaket.Types.List -> {
                val (ids, names) = services.listNames().split()
                ServicePaket.ListResponse(ids, names)
            }
            ServicePaket.Types.Status -> {
                val paket = peek(ServicePaket.StatusRequest)
                val service = services.get(paket.service)
                ServicePaket.StatusResponse(service.id, service.status())
            }
            ServicePaket.Types.Manage -> {
                val paket = peek(ServicePaket.ManageRequest)
                val service = services.get(paket.service)
                when (paket.action) {
                    ExecutableActions.Start -> service.start()
                    ExecutableActions.Stop -> service.stop()
                    ExecutableActions.Pause -> service.pause()
                    ExecutableActions.Resume -> service.resume()
                    ExecutableActions.Kill -> service.kill()
                }
                null
            }
            ServicePaket.Types.Command -> {
                val paket = peek(ServicePaket.CommandRequest)
                val service = services.get(paket.service)
                service.send2input(paket.command)
                null
            }
            ServicePaket.Types.Listen -> {
                val paket = peek(ServicePaket.ListenRequest)
                val service = services.get(paket.service)
                serviceConnections.containsKey(service) &&
                        throw AlreadyInStateMCSManException("service \"${service.name}\" is already being listened")
                val input = service.subscribeInput()
                val output = service.subscribeOutput()
                val errors = service.subscribeErrors()
                val tx = this as PaketSender
                serviceConnections[service] =
                    spawnListener(
                        input, output, errors,
                        StreamPaket.Sources.Service, tx, serviceConnections.keys, service
                    )
                null
            }
            ServicePaket.Types.Mute -> {
                val paket = peek(ServicePaket.MuteRequest)
                val service = services.get(paket.service)
                val connection = serviceConnections[service]
                    ?: throw AlreadyInStateMCSManException("not connected to service \"${service.name}\"")
                connection.cancelAndJoin()
                null
            }
        }
    }

    private suspend fun PaketPeeking.volumeHandler(type: VolumePaket.Types): VolumePaket {
        return when (type) {
            VolumePaket.Types.GetId -> {
                val paket = peek(VolumePaket.GetIdRequest)
                val id = servers.volumes.getId(paket.server, paket.name)
                VolumePaket.GetIdResponse(id, paket.server, paket.name)
            }
            VolumePaket.Types.Get -> {
                val paket = peek(VolumePaket.GetRequest)
                val agent = getAgent()
                val volume = servers.volumes.get(paket.volume)
                @OptIn(TransactionContext::class)
                suspendTransaction(db) {
                    accesses.volume.checkAllowed(agent, volume, VolumeAccessLevel.Read)
                }
                VolumePaket.GetResponse(volume.id, volume.name, volume.server.get().id)
            }
            VolumePaket.Types.List -> {
                val list = servers.volumes.listIds().toMutableList()
                VolumePaket.ListResponse(list)
            }
        }
    }

    private var eventsJob: Job = Job()

    private fun startEventsJob(sender: PaketSender) {
        eventsJob.cancel()
        eventsJob = launch(agent) {
            eventFlow.collect {
                try {
                    sendEvent.event = it
                    sender.send(sendEvent)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error(e)
                }
            }
        }
    }

    private fun Actor?.asProto(): Pair<AgentTypes, Int> {
        return when (this) {
            is User -> AgentTypes.User to id
            is Group -> AgentTypes.Group to id
            is Service -> AgentTypes.Service to id
            null -> AgentTypes.User to 0
            else -> unreachable
        }
    }

    private fun Agent?.asProto(): Pair<AgentTypes, Int> {
        return this?.represent?.asProto() ?: (AgentTypes.User to 0)
    }

    private suspend fun PaketPeeking.sessionHandle(type: SessionPaket.Types): SessionPaket? {
        return when (type) {
            SessionPaket.Types.GetId -> SessionPaket.GetIdResponse(this@PaketSession.id)
            SessionPaket.Types.Get -> {
                val paket = peek(SessionPaket.GetRequest)
                val session = if (paket.session == this@PaketSession.id || paket.session == 0)
                    this@PaketSession
                else
                    sessions.get(paket.session)
                val (agentType, identity) = session.agent.asProto()
                val modulesList = mutableListOf<Int>()
                val serversList = mutableListOf<Int>()
                val servicesList = mutableListOf<Int>()
                session.connectedModules.mapTo(modulesList) { it.id }
                session.connectedServers.mapTo(serversList) { it.id }
                session.connectedServices.mapTo(servicesList) { it.id }
                SessionPaket.GetResponse(
                    session.id, session.state == State.Talking && session.agent.isAdmin, agentType, identity,
                    modulesList, serversList, servicesList, session.listenEvents
                )
            }
            SessionPaket.Types.List -> SessionPaket.ListResponse(sessions.list())
            SessionPaket.Types.Upgrade -> {
                val agent = getAgent()
                if (agent.isAdmin)
                    throw AlreadyInStateMCSManException("already privileged session")
                @OptIn(TransactionContext::class)
                suspendTransaction(db) {
                    accesses.global.checkAllowed(agent, GlobalPermissions.admin, "privileged session")
                }
                agent as SessionUserAgent
                agent.isPrivileged = true
                null
            }
            SessionPaket.Types.Downgrade -> {
                val agent = getAgent()
                if (!agent.isAdmin)
                    throw AlreadyInStateMCSManException("already regular session")
                agent as SessionUserAgent
                agent.isPrivileged = false
                null
            }
            SessionPaket.Types.Disconnect -> {
                val paket = peek(SessionPaket.DisconnectRequest)
                sessions.get(paket.session).cancel()
                null
            }
        }
    }

    private suspend fun PaketPeeking.userHandle(type: UserPaket.Types): UserPaket? {
        return when (type) {
            UserPaket.Types.GetId -> {
                val paket = peek(UserPaket.GetIdRequest)
                val id = actors.users.getId(paket.name)
                UserPaket.GetIdResponse(id, paket.name)
            }
            UserPaket.Types.Get -> {
                val paket = peek(UserPaket.GetRequest)
                if (!agent.isAdmin || agent.represent.let { it !is User || it.id != paket.user })
                    @OptIn(TransactionContext::class)
                    suspendTransaction(db) {
                        accesses.global.checkAllowed(agent, GlobalPermissions.getUser, "get user")
                    }
                val user = actors.users.get(paket.user)
                val groups = mutableListOf<Int>()
                user.groups.buffer().collect {
                    groups += it.id
                }
                val owned = mutableListOf<Int>()
                user.ownedGroups.buffer().collect {
                    groups += it.id
                }
                UserPaket.GetResponse(
                    paket.user, user.name, user.realName, user.email.orEmpty(), user.blocked, groups, owned
                )
            }
            UserPaket.Types.List -> {
                val (ids, names) = actors.users.listNames().split()
                UserPaket.ListResponse(ids, names)
            }
            UserPaket.Types.FindByRealName -> {
                val paket = peek(UserPaket.FindByRealNameRequest)
                val (ids, names) = actors.users.findByRealName(paket.realName).split()
                UserPaket.FindByRealNameResponse(paket.realName, ids, names)
            }
            UserPaket.Types.FindByEMail -> {
                val paket = peek(UserPaket.FindByEMailRequest)
                val email = if (paket.email.isEmpty()) null else paket.email
                val (ids, names) = actors.users.findByEmail(email).split()
                UserPaket.FindByEMailResponse(paket.email, ids, names)
            }
            UserPaket.Types.Create -> {
                val paket = peek(UserPaket.CreateRequest)
                val user = actors.users.create(
                    paket.name,
                    if (paket.realName.isEmpty()) paket.name else paket.realName,
                    if (paket.email.isEmpty()) null else paket.email
                )
                UserPaket.CreateResponse(user.id, user.name)
            }
            UserPaket.Types.ChangeRealName -> {
                val paket = peek(UserPaket.ChangeRealNameRequest)
                val user = actors.users.get(paket.user)
                user.changeRealName(paket.realName)
                null
            }
            UserPaket.Types.ChangeEMail -> {
                val paket = peek(UserPaket.ChangeEMailRequest)
                val user = actors.users.get(paket.user)
                user.changeEMail(paket.email)
                null
            }
            UserPaket.Types.Block -> {
                val paket = peek(UserPaket.BlockRequest)
                val user = actors.users.get(paket.user)
                user.block()
                null
            }
            UserPaket.Types.Unblock -> {
                val paket = peek(UserPaket.UnblockRequest)
                val user = actors.users.get(paket.user)
                user.block(false)
                null
            }
            UserPaket.Types.Remove -> {
                val paket = peek(UserPaket.RemoveRequest)
                val user = actors.users.get(paket.user)
                user.remove()
                null
            }
        }
    }

    private suspend fun PaketPeeking.groupHandle(type: GroupPaket.Types): GroupPaket? {
        return when (type) {
            GroupPaket.Types.GetId -> {
                val paket = peek(GroupPaket.GetIdRequest)
                val id = actors.groups.getId(paket.name)
                GroupPaket.GetIdResponse(id, paket.name)
            }
            GroupPaket.Types.Get -> {
                val paket = peek(GroupPaket.GetRequest)
                val group = actors.groups.get(paket.group)
                @OptIn(TransactionContext::class)
                suspendTransaction(db) {
                    if (!agent.represent.let {
                            agent.isAdmin || it is Group && it.id == paket.group ||
                                    it is User && actors.groups.isUserAllowed(it.id, paket.group)
                        }) {
                        accesses.global.checkAllowed(agent, GlobalPermissions.getGroup, "get group")
                    }
                }
                val members = mutableListOf<Int>()
                group.members.buffer().collect {
                    members += it.id
                }
                GroupPaket.GetResponse(
                    group.id, group.name, group.realName, group.owner.get()?.id ?: 0, members
                )
            }
            GroupPaket.Types.List -> {
                val (idsShort, names) = actors.groups.listNames().split()
                val ids = mutableListOf<Int>()
                idsShort.mapTo(ids) { it }
                GroupPaket.ListResponse(ids, names)
            }
            GroupPaket.Types.FindByRealName -> {
                val paket = peek(GroupPaket.FindByRealNameRequest)
                val (idsShort, names) = actors.groups.findByRealName(paket.realName).split()
                val ids = mutableListOf<Int>()
                idsShort.mapTo(ids) { it }
                GroupPaket.FindByRealNameResponse(paket.realName, ids, names)
            }
            GroupPaket.Types.FindByOwner -> {
                val paket = peek(GroupPaket.FindByOwnerRequest)
                val (idsShort, names) = actors.groups
                    .findByOwner(if (paket.user == 0) null else paket.user).split()
                val ids = mutableListOf<Int>()
                idsShort.mapTo(ids) { it }
                GroupPaket.FindByOwnerResponse(paket.user, ids, names)
            }
            GroupPaket.Types.Create -> {
                val paket = peek(GroupPaket.CreateRequest)
                val group = actors.groups.create(
                    paket.name, if (paket.realName.isEmpty()) paket.name else paket.realName, true
                )
                GroupPaket.CreateResponse(group.id, group.name)
            }
            GroupPaket.Types.ChangeRealName -> {
                val paket = peek(GroupPaket.ChangeRealNameRequest)
                actors.groups.get(paket.group).changeRealName(paket.realName)
                null
            }
            GroupPaket.Types.ChangeOwner -> {
                val paket = peek(GroupPaket.ChangeOwnerRequest)
                val group = actors.groups.get(paket.group)
                val owner = if (paket.user == 0) null else actors.users.get(paket.user)
                group.changeOwner(owner)
                null
            }
            GroupPaket.Types.AddMember -> {
                val paket = peek(GroupPaket.AddMemberRequest)
                val group = actors.groups.get(paket.group)
                val user = actors.users.get(paket.user)
                group addMember user
                null
            }
            GroupPaket.Types.RemoveMember -> {
                val paket = peek(GroupPaket.RemoveMemberRequest)
                val group = actors.groups.get(paket.group)
                val user = actors.users.get(paket.user)
                group removeMember user
                null
            }
            GroupPaket.Types.Remove -> {
                val paket = peek(GroupPaket.RemoveRequest)
                actors.groups.get(paket.group).remove()
                null
            }
        }
    }

    private suspend inline fun <R> withFromProto(agentType: AgentTypes, identity: Int, block: (Actor?) -> R): R {
        if (identity == 0)
            return block(null)
        val shadow: Actor = when (agentType) {
            AgentTypes.User -> actors.users.get(identity)
            AgentTypes.Group -> actors.groups.get(identity)
            AgentTypes.Service -> services.get(identity)
        }
        return block(shadow)
    }

    private suspend fun PaketPeeking.globalAccessHandle(type: GlobalAccessPaket.Types): GlobalAccessPaket? {
        return when (type) {
            GlobalAccessPaket.Types.Check -> {
                val paket = peek(GlobalAccessPaket.CheckRequest)
                val allowed = withFromProto(paket.agent, paket.actor) { subject ->
                    val it = subject ?: agent.represent
                    @OptIn(TransactionContext::class)
                    suspendTransaction(db) {
                        if (it != agent.represent)
                            accesses.global.checkAllowed(agent, GlobalPermissions.globalCheck, "check global")
                        accesses.global.isAllowed(it, paket.permission)
                    }
                }
                GlobalAccessPaket.CheckResponse(paket.agent, paket.actor, paket.permission, allowed)
            }
            GlobalAccessPaket.Types.ListForActor -> {
                val paket = peek(GlobalAccessPaket.ListForActorRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    if (!agent.isAdmin && agent.represent != actor)
                        @OptIn(TransactionContext::class)
                        suspendTransaction(db) {
                            accesses.global.checkAllowed(
                                agent, GlobalPermissions.globalPermList, "list global permissions"
                            )
                        }
                    val permissions = mutableListOf<String>()
                    val allowance = mutableListOf<Boolean>()
                    accesses.global.list(actor).buffer().collect {
                        permissions += it.permission
                        allowance += it.allowed
                    }
                    GlobalAccessPaket.ListForActorResponse(paket.agent, paket.actor, permissions, allowance)
                }
            }
            GlobalAccessPaket.Types.Grant -> {
                val paket = peek(GlobalAccessPaket.GrantRequest)
                withFromProto(paket.agent, paket.actor) { accesses.global.grant(it, paket.permission, paket.allowed) }
                null
            }
            GlobalAccessPaket.Types.Revoke -> {
                val paket = peek(GlobalAccessPaket.RevokeRequest)
                withFromProto(paket.agent, paket.actor) { accesses.global.revoke(it, paket.permission, paket.allowed) }
                null
            }
        }
    }

    private suspend fun PaketPeeking.imageAccessHandle(type: ImageAccessPaket.Types): ImageAccessPaket? {
        return when (type) {
            ImageAccessPaket.Types.Check -> {
                val paket = peek(ImageAccessPaket.CheckRequest)
                val allowed = withFromProto(paket.agent, paket.actor) { subject ->
                    val it = subject ?: agent.represent
                    @OptIn(TransactionContext::class)
                    suspendTransaction(db) {
                        if (it != agent.represent)
                            accesses.global.checkAllowed(agent, GlobalPermissions.imageCheck, "check image")
                        accesses.image.isAllowed(it, ImageName(paket.image))
                    }
                }
                ImageAccessPaket.CheckResponse(paket.agent, paket.actor, paket.image, allowed)
            }
            ImageAccessPaket.Types.ListForActor -> {
                val paket = peek(ImageAccessPaket.ListForActorRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    if (!agent.isAdmin && agent.represent != actor)
                        @OptIn(TransactionContext::class)
                        suspendTransaction(db) {
                            accesses.global.checkAllowed(
                                agent, GlobalPermissions.imagePermList, "list image permissions"
                            )
                        }
                    val wildcards = mutableListOf<String>()
                    val allowance = mutableListOf<Boolean>()
                    accesses.image.list(actor).buffer().collect {
                        wildcards += it.wildcard
                        allowance += it.allowed
                    }
                    ImageAccessPaket.ListForActorResponse(paket.agent, paket.actor, wildcards, allowance)
                }
            }
            ImageAccessPaket.Types.Grant -> {
                val paket = peek(ImageAccessPaket.GrantRequest)
                withFromProto(paket.agent, paket.actor) { accesses.image.grant(it, paket.wildcard, paket.allowed) }
                null
            }
            ImageAccessPaket.Types.Revoke -> {
                val paket = peek(ImageAccessPaket.RevokeRequest)
                withFromProto(paket.agent, paket.actor) { accesses.image.revoke(it, paket.wildcard, paket.allowed) }
                null
            }
        }
    }

    private suspend fun PaketPeeking.serverAccessHandle(type: ServerAccessPaket.Types): ServerAccessPaket? {
        return when (type) {
            ServerAccessPaket.Types.Check -> {
                val paket = peek(ServerAccessPaket.CheckRequest)
                val allowed = withFromProto(paket.agent, paket.actor) { subject ->
                    val it = subject ?: agent.represent
                    val server = servers.getOrNull(paket.server)
                    @OptIn(TransactionContext::class)
                    suspendTransaction(db) {
                        if (it != agent.represent)
                            accesses.server.checkAllowed(agent, server, ServerPermissions.check)
                        accesses.server.isAllowed(it, server, paket.permission)
                    }
                }
                ServerAccessPaket.CheckResponse(paket.agent, paket.actor, paket.server, paket.permission, allowed)
            }
            ServerAccessPaket.Types.ListForActor -> {
                val paket = peek(ServerAccessPaket.ListForActorRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    if (!agent.isAdmin && agent.represent != actor)
                        @OptIn(TransactionContext::class)
                        suspendTransaction(db) {
                            accesses.server.checkAllowed(agent, ServerPermissions.permList)
                        }
                    val servers = mutableListOf<Int>()
                    val permissions = mutableListOf<String>()
                    val allowance = mutableListOf<Boolean>()
                    accesses.server.list(actor).buffer().collect {
                        servers += it.server?.id ?: 0
                        permissions += it.permission
                        allowance += it.allowed
                    }
                    ServerAccessPaket.ListForActorResponse(paket.agent, paket.actor, servers, permissions, allowance)
                }
            }
            ServerAccessPaket.Types.ListForServer -> {
                val paket = peek(ServerAccessPaket.ListForServerRequest)
                val server = servers.getOrNull(paket.server)
                @OptIn(TransactionContext::class)
                suspendTransaction(db) {
                    accesses.server.checkAllowed(agent, server, ServerPermissions.permList)
                }
                val agents = mutableListOf<AgentTypes>()
                val actors = mutableListOf<Int>()
                val permissions = mutableListOf<String>()
                val allowance = mutableListOf<Boolean>()
                accesses.server.list(server).buffer().collect {
                    val actor: Actor? = (it.user ?: it.group)
                    val (agentType, identity) = actor.asProto()
                    agents += agentType
                    actors += identity
                    permissions += it.permission
                    allowance += it.allowed
                }
                ServerAccessPaket.ListForServerResponse(paket.server, agents, actors, permissions, allowance)
            }
            ServerAccessPaket.Types.ListForActorAndServer -> {
                val paket = peek(ServerAccessPaket.ListForActorAndServerRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    val server = servers.getOrNull(paket.server)
                    if (!agent.isAdmin && agent.represent != actor)
                        @OptIn(TransactionContext::class)
                        suspendTransaction(db) {
                            accesses.server.checkAllowed(agent, ServerPermissions.permList)
                        }
                    val permissions = mutableListOf<String>()
                    val allowance = mutableListOf<Boolean>()
                    accesses.server.list(actor, server).buffer().collect {
                        permissions += it.permission
                        allowance += it.allowed
                    }
                    ServerAccessPaket.ListForActorAndServerResponse(
                        paket.agent, paket.actor, paket.server, permissions, allowance
                    )
                }
            }
            ServerAccessPaket.Types.Grant -> {
                val paket = peek(ServerAccessPaket.GrantRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    val server = servers.getOrNull(paket.server)
                    accesses.server.grant(actor, server, paket.permission, paket.allowed)
                }
                null
            }
            ServerAccessPaket.Types.Revoke -> {
                val paket = peek(ServerAccessPaket.RevokeRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    val server = servers.getOrNull(paket.server)
                    accesses.server.revoke(actor, server, paket.permission, paket.allowed)
                }
                null
            }
        }
    }

    private suspend fun PaketPeeking.volumeAccessHandle(type: VolumeAccessPaket.Types): VolumeAccessPaket? {
        return when (type) {
            VolumeAccessPaket.Types.Check -> {
                val paket = peek(VolumeAccessPaket.CheckRequest)
                val allowed = withFromProto(paket.agent, paket.actor) { subject ->
                    val it = subject ?: agent.represent
                    val volume = servers.volumes.getOrNull(paket.volume)
                    @OptIn(TransactionContext::class)
                    suspendTransaction(db) {
                        if (it != agent.represent)
                            accesses.volume.checkAllowed(agent, volume, VolumeAccessLevel.Owner)
                        accesses.volume.isAllowed(it, volume, paket.level)
                    }
                }
                VolumeAccessPaket.CheckResponse(paket.agent, paket.actor, paket.volume, paket.level, allowed)
            }
            VolumeAccessPaket.Types.ListForActor -> {
                val paket = peek(VolumeAccessPaket.ListForActorRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    if (!agent.isAdmin && agent.represent != actor)
                        @OptIn(TransactionContext::class)
                        suspendTransaction(db) {
                            accesses.volume.checkAllowed(agent, null, VolumeAccessLevel.Owner)
                        }
                    val volumes = mutableListOf<Int>()
                    val levels = mutableListOf<VolumeAccessLevel>()
                    accesses.volume.list(actor).buffer().collect {
                        volumes += it.volume?.id ?: 0
                        levels += it.accessLevel
                    }
                    VolumeAccessPaket.ListForActorResponse(paket.agent, paket.actor, volumes, levels)
                }
            }
            VolumeAccessPaket.Types.ListForVolume -> {
                val paket = peek(VolumeAccessPaket.ListForVolumeRequest)
                val volume = servers.volumes.getOrNull(paket.volume)
                @OptIn(TransactionContext::class)
                suspendTransaction(db) {
                    accesses.volume.checkAllowed(agent, volume, VolumeAccessLevel.Owner)
                }
                val agents = mutableListOf<AgentTypes>()
                val actors = mutableListOf<Int>()
                val levels = mutableListOf<VolumeAccessLevel>()
                accesses.volume.list(volume).buffer().collect {
                    val actor: Actor? = it.user ?: it.group
                    val (agentType, identity) = actor.asProto()
                    agents += agentType
                    actors += identity
                    levels += it.accessLevel
                }
                VolumeAccessPaket.ListForVolumeResponse(paket.volume, agents, actors, levels)
            }
            VolumeAccessPaket.Types.ListForActorAndVolume -> {
                val paket = peek(VolumeAccessPaket.ListForActorAndVolumeRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    val volume = servers.volumes.getOrNull(paket.volume)
                    if (!agent.isAdmin && agent.represent != actor)
                        @OptIn(TransactionContext::class)
                        suspendTransaction(db) {
                            accesses.volume.checkAllowed(agent, volume, VolumeAccessLevel.Owner)
                        }
                    val levels = mutableListOf<VolumeAccessLevel>()
                    accesses.volume.list(actor, volume).buffer().collect {
                        levels += it.accessLevel
                    }
                    VolumeAccessPaket.ListForActorAndVolumeResponse(paket.agent, paket.actor, paket.volume, levels)
                }
            }
            VolumeAccessPaket.Types.Grant -> {
                val paket = peek(VolumeAccessPaket.GrantRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    val volume = servers.volumes.getOrNull(paket.volume)
                    accesses.volume.setAccess(actor, volume, paket.level)
                }
                null
            }
            VolumeAccessPaket.Types.Revoke -> {
                val paket = peek(VolumeAccessPaket.RevokeRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    val volume = servers.volumes.getOrNull(paket.volume)
                    accesses.volume.revoke(actor, volume, paket.level)
                }
                null
            }
            VolumeAccessPaket.Types.Elevate -> {
                val paket = peek(VolumeAccessPaket.ElevateRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    val volume = servers.volumes.getOrNull(paket.volume)
                    accesses.volume.setAccess(actor, volume, paket.level, true)
                }
                null
            }
            VolumeAccessPaket.Types.Demote -> {
                val paket = peek(VolumeAccessPaket.DemoteRequest)
                withFromProto(paket.agent, paket.actor) { actor ->
                    val volume = servers.volumes.getOrNull(paket.volume)
                    accesses.volume.setAccess(actor, volume, paket.level, false)
                }
                null
            }
        }
    }

    companion object {
        private val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true))

        private val recognizers = FirstNotNullReactiveProperty<Pair<PaketPeeking, PaketSession>, Actor>()
        val recognizer: Reactive<Pair<PaketPeeking, PaketSession>, Actor?> get() = recognizers
    }
}
