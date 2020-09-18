package com.handtruth.mc.mcsman.client.bundle

import com.handtruth.kommon.concurrent.later
import com.handtruth.mc.mcsman.protocol.BundlePaket
import com.handtruth.mc.paket.peek

class PaketBundle internal constructor(
    override val controller: PaketBundles,
    override val id: Int
) : Bundle {
    private val bundleInfo = later {
        val paket = controller.client.request(BundlePaket.GetRequest(id)) { peek(BundlePaket.GetResponse) }
        BundleInfo(
            paket.bundle,
            paket.group,
            paket.artifact,
            paket.version,
            paket.dependencies.map { PaketBundle(controller, it) }
        )
    }

    override suspend fun inspect(): BundleInfo = bundleInfo.get()
}
