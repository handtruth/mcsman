package com.handtruth.mc.mcsman.client.server

import com.handtruth.kommon.concurrent.Later
import com.handtruth.kommon.concurrent.later
import com.handtruth.kommon.concurrent.laterOf
import com.handtruth.mc.mcsman.protocol.VolumePaket
import com.handtruth.mc.paket.peek

class PaketVolume internal constructor(
    override val controller: PaketServers.PaketVolumes,
    override val id: Int,
    server: PaketServer? = null,
    name: String? = null
) : Volume {

    private val info = later {
        val paket = controller.client.request(VolumePaket.GetRequest(id)) { peek(VolumePaket.GetResponse) }
        VolumeInfo(paket.volume, controller.client.servers.get(paket.server), paket.name)
    }

    override val server: Later<PaketServer> = if (server != null) laterOf(server) else later {
        info.get().server as PaketServer
    }

    override val name = if (name != null) laterOf(name) else later { info.get().name }

    override suspend fun inspect() = info.get()
}
