package com.handtruth.mc.mcsman.server.server

import com.handtruth.kommon.Log
import com.handtruth.mc.chat.ChatMessage
import com.handtruth.mc.mcsman.common.access.ServerPermissions
import com.handtruth.mc.mcsman.common.model.ExecutableActions
import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.event.*
import com.handtruth.mc.mcsman.server.AgentCheck
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.model.ServerTable
import com.handtruth.mc.mcsman.server.model.VolumeTable
import com.handtruth.mc.mcsman.server.session.actorName
import com.handtruth.mc.mcsman.server.session.getAgent
import com.handtruth.mc.mcsman.server.util.*
import com.handtruth.mc.mcsman.util.Executable
import com.handtruth.mc.mcsman.util.Removable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.exposed.sql.select
import org.koin.core.get

class Server : IntIdShadow<Server>(), Executable, Removable, TaskBranch {

    val name by ServerTable.name
    val description by ServerTable.description
    val game by ServerTable.game

    val volumes: Flow<Volume> =
        flow {
            val table = VolumeTable
            suspendTransaction(controller.db) {
                table.slice(table.id)
                    .select { VolumeTable.server eq this@Server.id }
                    .toList()
            }.forEach {
                emit(controller.volumes.get(it[table.id].value))
            }
        }

    override val controller = get<Servers>()

    override val coroutineContext = controller.fork(name)

    override val log = coroutineContext[Log]!!

    internal val info = async {
        controller.dockerUtils.findServerRaw(name)!!
    }

    suspend fun imageName(): ImageName? {
        val imageId = info.await().imageID
        val data = controller.dockerUtils.docker.images.inspect(imageId)
        return data.repoTags.firstOrNull()?.let { ImageName(it) }
    }

    private suspend inline fun checkPermission(permission: String): String {
        val agent = getAgent()
        @OptIn(TransactionContext::class)
        suspendTransaction(controller.db) {
            controller.accesses.server.checkAllowed(agent, this@Server, permission)
        }
        return agent.actorName
    }

    private suspend inline fun raise(permission: String, lambda: (String) -> Event) = node.invoke {
        val actor = checkPermission(permission)
        controller.events.raise(lambda(actor))
    }

    private suspend inline fun manage(action: ExecutableActions, permission: String) {
        raise(permission) { ManageServerEvent(action, name, it) }
    }

    @AgentCheck
    override suspend fun start() = manage(ExecutableActions.Start, ServerPermissions.start)

    @AgentCheck
    override suspend fun stop() = manage(ExecutableActions.Stop, ServerPermissions.stop)

    @AgentCheck
    override suspend fun kill() = manage(ExecutableActions.Kill, ServerPermissions.kill)

    @AgentCheck
    override suspend fun pause() = manage(ExecutableActions.Pause, ServerPermissions.pause)

    @AgentCheck
    override suspend fun resume() = manage(ExecutableActions.Resume, ServerPermissions.resume)

    @AgentCheck
    override suspend fun status(): ExecutableStatus = node.invoke {
        checkPermission(ServerPermissions.status)
        val state = controller.dockerUtils.docker.containers.inspect(info.await().id).state
        return when {
            state.paused -> ExecutableStatus.Paused
            state.running -> ExecutableStatus.Running
            else -> ExecutableStatus.Stopped
        }
    }

    private val sendChannel = Channel<String>(Channel.RENDEZVOUS)

    internal val internalInput: SendChannel<String>
        get() = sendChannel

    @AgentCheck
    override suspend fun send2input(line: String) = raise(ServerPermissions.send) {
        SendCommand2ServerEvent(line, name, it)
    }

    private val inputChannel = BroadcastChannel<String>(1)

    @AgentCheck
    override suspend fun subscribeInput(): ReceiveChannel<String> = node.invoke {
        checkPermission(ServerPermissions.input)
        return inputChannel.openSubscription()
    }

    private val outputChannel = BroadcastChannel<String>(1)

    @AgentCheck
    override suspend fun subscribeOutput(): ReceiveChannel<String> = node.invoke {
        checkPermission(ServerPermissions.output)
        return outputChannel.openSubscription()
    }

    private val errorsChannel = BroadcastChannel<String>(1)

    @AgentCheck
    override suspend fun subscribeErrors(): ReceiveChannel<String> = node.invoke {
        checkPermission(ServerPermissions.errors)
        return errorsChannel.openSubscription()
    }

    @AgentCheck
    override suspend fun remove() = raise(ServerPermissions.remove) {
        ServerCreationEvent(info.await().imageID, game, name, it, false)
    }

    @AgentCheck
    suspend fun changeDescription(description: ChatMessage) = raise(ServerPermissions.chDesc) {
        ChangeServerDescription(this.description, description.toChatString(), name, it)
    }

    @AgentCheck
    suspend fun upgrade(imageId: String? = null) = raise(ServerPermissions.upgrade) {
        val info = info.await()
        val newImageId = if (imageId == null) {
            val tag = info.image
            get<DockerUtils>().prepareImage(tag).id
        } else {
            imageId
        }
        ChangeVersionServerEvent(info.imageID, newImageId, name, it)
    }

    override fun onDispose() {
        cancel()
        if (connection.isCompleted)
            connection.getCompleted().close()
        sendChannel.close()
        outputChannel.close()
        errorsChannel.close()
    }

    private val connection = async {
        controller.attachments.attach(
            info.await().id, status() != ExecutableStatus.Stopped,
            ServerLifeEvent(Transitions.Starting, name, true),
            ServerLifeEvent(Transitions.Starting, name, false),
            sendChannel, inputChannel, outputChannel, errorsChannel
        )
    }

    override fun toString() = "server: $name"
}
