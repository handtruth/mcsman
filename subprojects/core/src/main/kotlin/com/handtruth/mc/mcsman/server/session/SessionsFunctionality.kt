package com.handtruth.mc.mcsman.server.session

import com.handtruth.docker.DockerClient
import com.handtruth.kommon.getLog
import com.handtruth.mc.mcsman.AlreadyInStateMCSManException
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.common.event.catch
import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.mcsman.event.*
import com.handtruth.mc.mcsman.protocol.AuthorizationPaket
import com.handtruth.mc.mcsman.server.Config
import com.handtruth.mc.mcsman.server.actor.Actors
import com.handtruth.mc.mcsman.server.docker.Labels
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.model.LoginMethodTable
import com.handtruth.mc.mcsman.server.model.UserTable
import com.handtruth.mc.mcsman.server.service.Services
import com.handtruth.mc.mcsman.server.util.PasswordHashStrategy
import com.handtruth.mc.mcsman.server.util.register
import com.handtruth.mc.mcsman.server.util.selectUser
import com.handtruth.mc.mcsman.server.util.suspendTransaction
import com.handtruth.mc.mcsman.util.forever
import com.handtruth.mc.paket.peek
import io.ktor.network.sockets.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.koin.core.KoinComponent
import org.koin.core.get
import java.net.InetSocketAddress
import java.time.LocalDateTime
import java.time.OffsetDateTime

internal class SessionsFunctionality : KoinComponent {

    private val sessions: Sessions = get()
    private val users: Actors.Users = get()
    private val services: Services = get()
    private val db: Database = get()
    private val docker: DockerClient = get()
    private val config: Config = get()
    private val events: Events = get()

    companion object {
        private val offsetDateTime = OffsetDateTime.now().offset

        fun long2date(date: Long): LocalDateTime =
            LocalDateTime.ofEpochSecond(date, 0, offsetDateTime)

        fun date2long(date: LocalDateTime): Long = date.toEpochSecond(offsetDateTime)
    }

    init {
        PaketSession.recognizer.register { (peeking, session) ->
            val header = peeking.peek(AuthorizationPaket)
            val log = getLog()
            when (header.method) {
                "password" -> {
                    if (header.actor != AgentTypes.User)
                        return@register null
                    val paket = peeking.peek(AuthorizationPaket.Password)
                    val shouldRaise = mutableListOf<Event>()
                    val allowed = suspendTransaction(db) {
                        val table = LoginMethodTable
                        (table innerJoin UserTable).select {
                            (table.method eq "password") and (UserTable.name eq paket.login) and
                                    table.enabled and not(UserTable.blocked)
                        }.forEach {
                            val date = it[table.expiryDate]
                            if (date != null && date < LocalDateTime.now()) {
                                shouldRaise += LoginMethodEvent(
                                    it[table.method], it[table.algorithm], LoginMethodEvent.Data(it[table.data]),
                                    it[table.expiryDate]?.let { d -> date2long(d) }, paket.login, false
                                )
                                return@forEach
                            }
                            val algorithm = it[table.algorithm]
                            val hasher = PasswordHashStrategy[algorithm] ?: run {
                                log.warning { "hashed password has not hash algorithm implementation" }
                                return@forEach
                            }
                            if (hasher.verify(it[table.data], paket.password))
                                return@suspendTransaction true
                        }
                        false
                    }
                    shouldRaise.forEach { events.raise(it) }
                    if (allowed)
                        users.get(paket.login)
                    else
                        null
                }
                "ipaddress" -> {
                    if (header.actor != AgentTypes.Service)
                        return@register null
                    val address = session.socket.remoteAddress as InetSocketAddress
                    val ipAddress = address.address.hostAddress
                    docker.containers.list(all = true) {
                        label(Labels.type, Labels.Types.service)
                        label(Labels.network, config.network)
                    }.forEach {
                        val conf = it.inspect()
                        val ip = conf.networkSettings.networks[config.network]?.ipAddress ?: return@forEach
                        if (ipAddress == ip)
                            return@register services.get(conf.config.labels[Labels.name]!!)
                    }
                    null
                }
                else -> null
            }
        }

        events.react<LoginMethodEvent> { event ->
            val table = LoginMethodTable
            if (event.direction) {
                suspendTransaction(db) {
                    table.insert {
                        @Suppress("UNCHECKED_CAST")
                        it[table.user] = selectUser(event.user) as Expression<EntityID<Int>>
                        it[table.method] = event.method
                        it[table.algorithm] = event.algorithm
                        it[table.expiryDate] = event.expiryDate?.let { date -> long2date(date) }
                        it[table.data] = event.data.data
                    }
                }
            } else {
                val loginMethod = suspendTransaction(db) {
                    (table innerJoin UserTable).select {
                        (UserTable.name eq event.user) and (table.method eq event.method) and
                                (table.algorithm eq event.algorithm) and (table.data eq event.data.data) and
                                (table.expiryDate eq event.expiryDate?.let { long2date(it) })
                    }.firstOrNull() ?: throw NotExistsMCSManException("no such login method")
                }
                if (!loginMethod[table.enabled]) {
                    events.raise(
                        BlockLoginMethodEvent(
                            event.method, event.algorithm, event.data.data, event.user, false
                        )
                    )
                }
                suspendTransaction(db) {
                    table.deleteWhere { table.id eq loginMethod[table.id] }
                }
            }
        }

        events.react<BlockLoginMethodEvent> { event ->
            val table = LoginMethodTable
            val loginMethod = suspendTransaction(db) {
                (table innerJoin UserTable).select {
                    (UserTable.name eq event.user) and (table.method eq event.method) and
                            (table.algorithm eq event.algorithm) and (table.data eq event.data)
                }.firstOrNull() ?: throw NotExistsMCSManException("no such login method")
            }
            if (loginMethod[table.enabled] != event.direction)
                throw AlreadyInStateMCSManException("login method already in this state")
            table.update({ table.id eq loginMethod[table.id] }) {
                it[table.enabled] = !event.direction
            }
        }

        events.register<SessionLifeEvent>()

        sessions.launch {
            events.catch<MCSManLifeEvent> { it.success }
            try {
                val server = aSocket(get()).tcp().bind(InetSocketAddress("0.0.0.0", config.port))
                forever {
                    val socket = server.accept()
                    PaketSession(socket).start()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                getLog().fatal(e) { "error on server socket task" }
            }
        }
    }
}
