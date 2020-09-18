package com.handtruth.mc.mcsman.client.module

import com.handtruth.kommon.concurrent.Later
import com.handtruth.mc.mcsman.client.bundle.Bundle
import com.handtruth.mc.mcsman.client.util.NamedEntity
import com.handtruth.mc.mcsman.client.util.NamedEntityInfo
import com.handtruth.mc.mcsman.common.module.Artifact

interface Module : NamedEntity {
    override val controller: Modules

    override suspend fun inspect(): ModuleInfo

    val connection: Later<ModuleConnection>

    suspend fun artifacts(type: String? = null, `class`: String? = null, platform: String? = null): List<Artifact>
}

interface ModuleConnection {
    fun onDisconnect() {}
}

data class ModuleInfo(
    override val id: Int,
    override val name: String,
    val bundle: Bundle,
    val enabled: Boolean,
    val depends: List<Module>
) : NamedEntityInfo
