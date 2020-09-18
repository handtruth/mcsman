package com.handtruth.mc.mcsman.client.session

import com.handtruth.kommon.concurrent.Later
import com.handtruth.kommon.concurrent.later
import com.handtruth.mc.mcsman.client.PaketMCSManClient
import com.handtruth.mc.mcsman.protocol.SessionPaket
import com.handtruth.mc.paket.peek

class PaketSessions internal constructor(
    override val client: PaketMCSManClient
) : Sessions {
    override fun get(id: Int) = PaketSession(this, id)

    override suspend fun list(): List<PaketSession> {
        val paket = client.request(SessionPaket.ListRequest) { peek(SessionPaket.ListResponse) }
        return paket.sessions.map { get(it) }
    }

    override val current: Later<PaketSession> = later {
        val paket = client.request(SessionPaket.GetIdRequest) { peek(SessionPaket.GetIdResponse) }
        get(paket.session)
    }
}