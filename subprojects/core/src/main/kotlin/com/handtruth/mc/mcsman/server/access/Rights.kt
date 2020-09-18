package com.handtruth.mc.mcsman.server.access

import com.handtruth.mc.mcsman.common.access.VolumeAccessLevel
import com.handtruth.mc.mcsman.server.actor.Group
import com.handtruth.mc.mcsman.server.actor.User
import com.handtruth.mc.mcsman.server.server.Server
import com.handtruth.mc.mcsman.server.server.Volume

abstract class BasePermission {
    abstract val user: User?
    abstract val group: Group?
}

data class GlobalPermission(
    val permission: String,
    val allowed: Boolean,
    override val user: User?,
    override val group: Group?
) : BasePermission()

data class ServerPermission(
    val server: Server?,
    val permission: String,
    val allowed: Boolean,
    override val user: User?,
    override val group: Group?
) : BasePermission()

data class ImageWildcard(
    val wildcard: String,
    val allowed: Boolean,
    override val user: User?,
    override val group: Group?
) : BasePermission()

data class VolumeAccess(
    val volume: Volume?,
    val accessLevel: VolumeAccessLevel,
    override val user: User?,
    override val group: Group?
) : BasePermission()
