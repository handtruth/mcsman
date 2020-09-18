package com.handtruth.mc.mcsman.client.service

import com.handtruth.mc.mcsman.client.PaketMCSManClient
import com.handtruth.mc.mcsman.protocol.ServicePaket
import com.handtruth.mc.paket.peek
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class PaketServices internal constructor(
    override val client: PaketMCSManClient
) : Services {
    internal val connected: ConcurrentMap<Int, PaketService> = ConcurrentHashMap(1)

    override fun get(id: Int): PaketService = connected.getOrPut(id) { PaketService(this, id) }

    override suspend fun get(name: String): PaketService {
        val paket = client.request(ServicePaket.GetIdRequest(name)) { peek(ServicePaket.GetIdResponse) }
        return get(paket.service).apply { internalName = paket.name }
    }

    override suspend fun list(): List<PaketService> {
        val paket = client.request(ServicePaket.ListRequest) { peek(ServicePaket.ListResponse) }
        return paket.ids.zip(paket.names) { id, name -> get(id).apply { internalName = name } }
    }
}
