package com.handtruth.mc.mcsman.client.service

import com.handtruth.kommon.concurrent.later
import com.handtruth.mc.mcsman.client.util.PaketExecutable
import com.handtruth.mc.mcsman.common.model.ExecutableActions
import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.mcsman.protocol.ServicePaket
import com.handtruth.mc.paket.peek

class PaketService(
    override val controller: PaketServices,
    override val id: Int
) : PaketExecutable(), Service {
    override val client get() = controller.client

    private suspend fun get() = client.request(ServicePaket.GetRequest(id)) {
        peek(ServicePaket.GetResponse)
    }

    internal var internalName: String? = null

    override val name = later {
        internalName ?: get().name
    }

    override suspend fun inspect() = with(get()) {
        ServiceInfo(service, name, client.bundles.get(bundle), status, factory)
    }

    private suspend fun manage(action: ExecutableActions) {
        client.request(ServicePaket.ManageRequest(id, action))
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
        return client.request(ServicePaket.StatusRequest(id)) {
            peek(ServicePaket.StatusResponse).status
        }
    }

    override suspend fun send2input(line: String) {
        client.request(ServicePaket.CommandRequest(id, line))
    }

    override suspend fun listen() {
        client.request(ServicePaket.ListenRequest(id))
    }

    override suspend fun mute() {
        client.request(ServicePaket.MuteRequest(id))
    }
}
