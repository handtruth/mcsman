package com.handtruth.mc.mcsman.client.server

import com.handtruth.mc.chat.ChatMessage
import com.handtruth.mc.mcsman.client.PaketMCSManClient
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.protocol.ServerPaket
import com.handtruth.mc.mcsman.protocol.VolumePaket
import com.handtruth.mc.paket.peek
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class PaketServers internal constructor(
    override val client: PaketMCSManClient
) : Servers {
    internal val connected: ConcurrentMap<Int, PaketServer> = ConcurrentHashMap(1)

    override fun get(id: Int): PaketServer = connected.getOrPut(id) { PaketServer(this, id) }

    override suspend fun get(name: String): PaketServer {
        val paket = client.request(ServerPaket.GetIdRequest(name)) {
            peek(ServerPaket.GetIdResponse)
        }
        return get(paket.server).apply { internalName = paket.name }
    }

    override suspend fun list(): List<PaketServer> {
        val paket = client.request(ServerPaket.ListRequest) {
            peek(ServerPaket.ListResponse)
        }
        return paket.ids.zip(paket.names) { id, name -> get(id).apply { internalName = name } }
    }

    override suspend fun create(name: String, image: ImageName, description: ChatMessage?): PaketServer {
        val paket = client.request(
            ServerPaket.CreateRequest(name, image.toString(), description?.toChatString().orEmpty())
        ) {
            peek(ServerPaket.CreateResponse)
        }
        return get(paket.server).apply { internalName = paket.name }
    }

    override val volumes = PaketVolumes()

    inner class PaketVolumes internal constructor() : Servers.Volumes {
        override val client get() = this@PaketServers.client

        override fun get(id: Int) = PaketVolume(this, id)

        override suspend fun get(server: Server, name: String): PaketVolume {
            val paket = client.request(VolumePaket.GetIdRequest(server.id, name)) {
                peek(VolumePaket.GetIdResponse)
            }
            return PaketVolume(this, paket.volume, client.servers.get(paket.server), paket.name)
        }

        override suspend fun list(): List<Volume> {
            val paket = client.request(VolumePaket.ListRequest) {
                peek(VolumePaket.ListResponse)
            }
            return paket.volumes.map { get(it) }
        }
    }
}
