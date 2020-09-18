package com.handtruth.mc.mcsman.client.bundle

import com.handtruth.mc.mcsman.client.util.Controller

interface Bundles : Controller {
    override fun get(id: Int): Bundle
    suspend fun get(group: String, artifact: String): Bundle

    override suspend fun list(): List<Bundle>
}
