package com.handtruth.mc.mcsman.client.bundle

import com.handtruth.mc.mcsman.client.PaketMCSManClient
import com.handtruth.mc.mcsman.protocol.BundlePaket
import com.handtruth.mc.paket.peek

class PaketBundles internal constructor(
    override val client: PaketMCSManClient
) : Bundles {
    override fun get(id: Int) = PaketBundle(this, id)

    override suspend fun get(group: String, artifact: String): PaketBundle {
        val paket = client.request(BundlePaket.GetIdRequest(group, artifact)) { peek(BundlePaket.GetIdResponse) }
        return get(paket.bundle)
    }

    override suspend fun list(): List<PaketBundle> {
        val paket = client.request(BundlePaket.ListRequest) { peek(BundlePaket.ListResponse) }
        return paket.ids.map { get(it) }
    }
}
