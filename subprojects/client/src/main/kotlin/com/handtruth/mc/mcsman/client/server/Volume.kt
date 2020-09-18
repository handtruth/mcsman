package com.handtruth.mc.mcsman.client.server

import com.handtruth.kommon.concurrent.Later
import com.handtruth.mc.mcsman.client.util.Entity
import com.handtruth.mc.mcsman.client.util.EntityInfo

interface Volume : Entity {
    override val controller: Servers.Volumes

    val server: Later<Server>
    val name: Later<String>

    override suspend fun inspect(): VolumeInfo
}

data class VolumeInfo(
    override val id: Int,
    val server: Server,
    val name: String
) : EntityInfo
