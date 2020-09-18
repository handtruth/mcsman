package com.handtruth.mc.mcsman.server.server

import com.handtruth.kommon.Log
import com.handtruth.mc.chat.ChatMessage
import com.handtruth.mc.chat.buildChat
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.common.access.ServerPermissions
import com.handtruth.mc.mcsman.common.access.VolumeAccessLevel
import com.handtruth.mc.mcsman.common.event.listenSuccessful
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.event.ServerCreationEvent
import com.handtruth.mc.mcsman.event.VolumeCreationEvent
import com.handtruth.mc.mcsman.server.AgentCheck
import com.handtruth.mc.mcsman.server.Configuration
import com.handtruth.mc.mcsman.server.MCSManCore
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.access.Accesses
import com.handtruth.mc.mcsman.server.docker.Attachments
import com.handtruth.mc.mcsman.server.docker.Labels
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.model.ServerTable
import com.handtruth.mc.mcsman.server.model.VolumeTable
import com.handtruth.mc.mcsman.server.session.actorName
import com.handtruth.mc.mcsman.server.session.getAgent
import com.handtruth.mc.mcsman.server.session.privileged
import com.handtruth.mc.mcsman.server.util.*
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.inject
import kotlin.time.seconds

open class Servers : IntIdShadow.IntIdController<Server>(ServerTable), NamedShadowsController<Server, Int>, TaskBranch {
    internal val events: Events by inject()
    internal val accesses: Accesses by inject()
    internal val dockerUtils: DockerUtils by inject()
    internal val attachments: Attachments by inject()
    private val config: Configuration by inject()

    final override val coroutineContext = MCSManCore.fork("server")
    final override val log = coroutineContext[Log]!!

    override suspend fun spawn() = Server()

    override suspend fun getOrNull(name: String): Server? {
        val table = ServerTable
        return findOne(table.select { table.name eq name })
    }

    private fun createServerDescription(game: String?, name: String, user: String): ChatMessage {
        return buildChat {
            if (game != null) {
                bold { text(game) }
                text(" ")
            }
            text("server ")
            bold {
                color(ChatMessage.Color.Gold) { text(name) }
            }
            text(" created by ")
            bold { text(user) }
        }
    }

    @AgentCheck
    suspend fun listNames(): Map<Int, String> {
        val agent = getAgent()
        @OptIn(TransactionContext::class)
        return suspendTransaction(db) {
            accesses.global.checkAllowed(agent, GlobalPermissions.serverList, "list servers")
            val table = ServerTable
            table.slice(table.id, table.name).selectAll().associate { it[table.id].value to it[table.name] }
        }
    }

    @AgentCheck
    suspend fun getId(name: String): Int {
        val agent = getAgent()
        @OptIn(TransactionContext::class)
        return suspendTransaction(db) {
            accesses.server.checkAllowed(agent, name, ServerPermissions.getId)
            val table = ServerTable
            table.slice(table.id).select { table.name eq name }.limit(1).firstOrNull()?.let { it[table.id].value }
                ?: throw NotExistsMCSManException("server $name does not exists")
        }
    }

    @AgentCheck
    open suspend fun create(name: String, image: ImageName? = null, description: ChatMessage? = null): Server {
        val agent = getAgent()
        val actor = agent.actorName
        val imageName = image ?: config.server.image
        @OptIn(TransactionContext::class)
        suspendTransaction(db) {
            accesses.global.checkAllowed(agent, GlobalPermissions.mkServer, "create server")
            accesses.image.checkAllowed(agent, imageName)
        }
        val img = dockerUtils.prepareImage(imageName)
        val imgInfo = img.inspect()
        val game = imgInfo.config.labels[Labels.game]
        launch(NonCancellable) {
            // Is it normal? I guess not...
            withTimeoutOrNull(10.seconds) {
                privileged<Nothing> {
                    events.listenSuccessful<VolumeCreationEvent> { event ->
                        if (event.server == name) {
                            val volume = volumes.borrow(name, event.volume)
                            accesses.volume.setAccess(
                                agent.represent, volume, VolumeAccessLevel.Owner, true
                            )
                        }
                    }
                }
            }
        }
        events.raise(ServerCreationEvent(img.id, game, name, actor))
        val server = get(name)
        privileged {
            server.changeDescription(description ?: createServerDescription(game, name, actor))
            accesses.server.grant(agent.represent, server, ServerPermissions.owner)
        }
        return server
    }

    open inner class Volumes : IntIdShadow.IntIdController<Volume>(VolumeTable) {
        internal val events: Events by inject()
        internal val accesses: Accesses by inject()
        internal val servers: Servers get() = this@Servers

        final override val coroutineContext = MCSManCore.fork("volume", supervised = false)
        final override val log = coroutineContext[Log]!!

        override suspend fun spawn() = Volume()

        suspend fun borrow(server: String, volume: String): Volume {
            val join = VolumeTable innerJoin ServerTable
            return findOne(join.select { (ServerTable.name eq server) and (VolumeTable.name eq volume) })!!
        }

        @AgentCheck
        suspend fun listIds(): List<Int> {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            return suspendTransaction(db) {
                accesses.global.checkAllowed(agent, GlobalPermissions.volumeList, "list volumes")
                val table = VolumeTable
                table.slice(table.id, table.name).selectAll().map { it[table.id].value }
            }
        }

        @AgentCheck
        suspend fun getId(server: Int, name: String): Int {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            return suspendTransaction(db) {
                accesses.volume.checkAllowed(agent, server, name, VolumeAccessLevel.Read)
                val table = VolumeTable
                table.slice(table.id).select { (table.server eq server) and (table.name eq name) }.limit(1)
                    .firstOrNull()?.let { it[table.id].value }
                    ?: throw NotExistsMCSManException("volume $name of server #$server does not exists")
            }
        }
    }

    open val volumes = Volumes()
}
