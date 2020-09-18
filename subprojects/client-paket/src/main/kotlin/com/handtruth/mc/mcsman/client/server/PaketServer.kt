package com.handtruth.mc.mcsman.client.server

import com.handtruth.kommon.concurrent.later
import com.handtruth.mc.chat.ChatMessage
import com.handtruth.mc.mcsman.client.util.PaketExecutable
import com.handtruth.mc.mcsman.common.model.ExecutableActions
import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.protocol.ServerPaket
import com.handtruth.mc.paket.peek

class PaketServer internal constructor(
    override val controller: PaketServers,
    override val id: Int
) : PaketExecutable(), Server {
    internal var internalName: String? = null
    override val client get() = controller.client

    private suspend fun get() = client.request(ServerPaket.GetRequest(id)) { peek(ServerPaket.GetResponse) }

    override val name = later { internalName ?: get().name }

    override suspend fun inspect() = with(get()) {
        ServerInfo(
            server, name, status, imageId, ImageName(image),
            if (game.isEmpty()) null else game, ChatMessage.parse(description),
            volumes.map { controller.volumes.get(it) }
        )
    }

    override suspend fun setDescription(description: ChatMessage) {
        client.request(ServerPaket.ChangeDescriptionRequest(id, description))
    }

    override suspend fun upgrade() {
        client.request(ServerPaket.UpgradeRequest(id))
    }

    private suspend fun manage(action: ExecutableActions) {
        client.request(ServerPaket.ManageRequest(id, action))
    }

    override suspend fun start() {
        manage(ExecutableActions.Start)
    }

    override suspend fun stop() {
        manage(ExecutableActions.Stop)
    }

    override suspend fun pause() {
        manage(ExecutableActions.Pause)
    }

    override suspend fun resume() {
        manage(ExecutableActions.Resume)
    }

    override suspend fun kill() {
        manage(ExecutableActions.Kill)
    }

    override suspend fun status(): ExecutableStatus {
        return client.request(ServerPaket.StatusRequest(id)) {
            peek(ServerPaket.StatusResponse).status
        }
    }

    override suspend fun remove() {
        client.request(ServerPaket.RemoveRequest(id))
    }

    override suspend fun send2input(line: String) {
        client.request(ServerPaket.CommandRequest(id, line))
    }

    override suspend fun listen() {
        client.request(ServerPaket.ListenRequest(id))
    }

    override suspend fun mute() {
        client.request(ServerPaket.MuteRequest(id))
    }
}
