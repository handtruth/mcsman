package com.handtruth.mc.mcsman.client.bundle

import com.handtruth.mc.mcsman.client.util.Entity
import com.handtruth.mc.mcsman.client.util.EntityInfo

interface Bundle : Entity {
    override val controller: Bundles
    override suspend fun inspect(): BundleInfo
}

data class BundleInfo(
    override val id: Int,
    val group: String,
    val artifact: String,
    val version: String,
    val dependencies: List<Bundle>
) : EntityInfo
