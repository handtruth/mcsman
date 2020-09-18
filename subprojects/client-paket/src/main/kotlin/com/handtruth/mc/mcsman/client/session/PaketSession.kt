package com.handtruth.mc.mcsman.client.session

import com.handtruth.mc.mcsman.client.util.subject
import com.handtruth.mc.mcsman.protocol.SessionPaket
import com.handtruth.mc.paket.peek

class PaketSession internal constructor(
    override val controller: PaketSessions,
    override val id: Int
) : Session {
    override suspend fun inspect(): SessionInfo {
        val client = controller.client
        val paket = client.request(SessionPaket.GetRequest(id)) { peek(SessionPaket.GetResponse) }
        val services = client.services
        val modules = client.modules
        val servers = client.servers
        return SessionInfo(
            paket.session, paket.privileged,
            controller.client.subject(paket.agent, paket.actor),
            paket.modules.map { modules.get(it) },
            paket.servers.map { servers.get(it) },
            paket.services.map { services.get(it) },
            paket.listenEvents
        )
    }

    override suspend fun remove() {
        controller.client.request(SessionPaket.DisconnectRequest(id))
    }
}
