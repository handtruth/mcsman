package com.handtruth.mc.mcsman.client

import com.handtruth.mc.mcsman.*
import com.handtruth.mc.mcsman.client.access.PaketAccesses
import com.handtruth.mc.mcsman.client.actor.PaketActors
import com.handtruth.mc.mcsman.client.bundle.PaketBundles
import com.handtruth.mc.mcsman.client.event.PaketEvents
import com.handtruth.mc.mcsman.client.module.PaketModuleSpecification
import com.handtruth.mc.mcsman.client.module.PaketModules
import com.handtruth.mc.mcsman.client.server.PaketServers
import com.handtruth.mc.mcsman.client.service.PaketServices
import com.handtruth.mc.mcsman.client.session.PaketSessions
import com.handtruth.mc.mcsman.client.util.PaketExecutable
import com.handtruth.mc.mcsman.client.util.loadObjects
import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.mcsman.protocol.*
import com.handtruth.mc.mcsman.util.forever
import com.handtruth.mc.paket.*
import io.ktor.network.sockets.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetSocketAddress
import kotlin.coroutines.CoroutineContext

class PaketMCSManClient(
    context: CoroutineContext,
    private val ts: PaketTransmitter,
    modules: List<PaketModuleSpecification>
) : MCSManClient {
    private fun prepareContext(context: CoroutineContext): CoroutineContext {
        val context1 = context + Job(context[Job])
        val name = context[CoroutineName]
        return context1 + CoroutineName(
            if (name != null)
                name.name + "/mcsman-client"
            else
                "mcsman-client"
        )
    }

    override val coroutineContext: CoroutineContext = prepareContext(context)

    private val mainTs: PaketTransmitter
    internal val eventTs: PaketReceiver
    private val streamTs: PaketTransmitter
    internal val extTs: PaketTransmitter

    init {
        val tss = ts.split(4) {
            when (ts.id) {
                PaketID.NoOp -> -1
                PaketID.EventStream -> 1
                PaketID.Stream -> 2
                PaketID.Extension -> 3
                else -> 0
            }
        }
        mainTs = tss[0]
        eventTs = tss[1]
        streamTs = tss[2]
        extTs = tss[3]
    }

    init {
        launch {
            ts.use { _ ->
                forever {
                    val paket = streamTs.receive(StreamPaket)
                    val executable: PaketExecutable
                    when (paket.source) {
                        StreamPaket.Sources.Server -> executable = servers.connected[paket.identity] ?: return@forever
                        StreamPaket.Sources.Service -> executable = services.connected[paket.identity] ?: return@forever
                    }
                    val channel = when (paket.type) {
                        StreamPaket.Types.Input -> executable.input
                        StreamPaket.Types.Output -> executable.output
                        StreamPaket.Types.Errors -> executable.errors
                    }
                    channel.send(paket.line)
                }
            }
        }
    }

    private val mutex = Mutex()

    internal suspend fun request(paket: Paket): Unit = request(paket) {}
    internal suspend fun send(paket: Paket) = mainTs.send(paket)

    internal suspend inline fun <R> request(paket: Paket, block: PaketPeeking.() -> R): R {
        mutex.withLock {
            mainTs.send(paket)
            val id = mainTs.catch()
            try {
                return if (id == PaketID.Error) {
                    val error = mainTs.peek(ErrorPaket)
                    when (error.code) {
                        ErrorPaket.ErrorCodes.Success -> block(mainTs)
                        ErrorPaket.ErrorCodes.Auth -> throw AuthenticationMCSManException(error.message)
                        ErrorPaket.ErrorCodes.Unknown -> throw UnknownMCSManException(error.message)
                        ErrorPaket.ErrorCodes.AccessDenied -> throw AccessDeniedMCSManException(error.message)
                        ErrorPaket.ErrorCodes.AlreadyInState -> throw AlreadyInStateMCSManException(error.message)
                        ErrorPaket.ErrorCodes.NotExists -> throw NotExistsMCSManException(error.message)
                        ErrorPaket.ErrorCodes.AlreadyExists -> throw AlreadyExistsMCSManException(error.message)
                    }
                } else {
                    block(mainTs)
                }
            } finally {
                mainTs.drop()
            }
        }
    }

    suspend fun handshake(address: String, port: UShort) {
        mainTs.send(HandshakePaket(address, port))
    }

    suspend fun authorizeCustom(paket: AuthorizationPaket) {
        request(paket)
    }

    override suspend fun authorizeByPassword(login: String, password: String, type: AgentTypes) {
        authorizeCustom(AuthorizationPaket.Password(login, password, type))
    }

    suspend fun authorizeByIpAddress(type: AgentTypes = AgentTypes.Service) {
        authorizeCustom(AuthorizationPaket.IPAddress(type))
    }

    override suspend fun authorizeByToken(token: String, type: AgentTypes) {
        authorizeCustom(AuthorizationPaket.Token(token, type))
    }

    override suspend fun enterAdminState() {
        request(SessionPaket.UpgradeRequest)
    }

    override suspend fun leaveAdminState() {
        request(SessionPaket.DowngradeRequest)
    }

    suspend fun touch() {
        send(NoOpPaket)
    }

    override val events get() = PaketEvents(this)
    override val servers = PaketServers(this)
    override val services = PaketServices(this)
    override val modules = PaketModules(this, modules)
    override val sessions = PaketSessions(this)
    override val actors = PaketActors(this)
    override val accesses = PaketAccesses(this)
    override val bundles = PaketBundles(this)

    init {
        modules.forEach { it.invokeInitialize(this) }
    }

    override fun close() {
        events.eventsChannel.close()
        cancel()
    }
}

suspend fun MCSManClient.Companion.connect(
    socket: Socket, context: CoroutineContext, classLoader: ClassLoader? = null
): PaketMCSManClient {
    val modules: List<PaketModuleSpecification> = if (classLoader != null)
        loadObjects(classLoader)
    else
        loadObjects()
    val client = PaketMCSManClient(
        context, PaketTransmitter(socket.openReadChannel(), socket.openWriteChannel()), modules
    )
    try {
        val address = socket.remoteAddress as InetSocketAddress
        client.handshake(address.hostString, address.port.toUShort())
        return client
    } catch (thr: Throwable) {
        client.close()
        throw thr
    }
}
