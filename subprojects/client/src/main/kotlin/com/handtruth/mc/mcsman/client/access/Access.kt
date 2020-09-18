package com.handtruth.mc.mcsman.client.access

import com.handtruth.mc.mcsman.client.actor.Actor
import com.handtruth.mc.mcsman.client.server.Server
import com.handtruth.mc.mcsman.client.server.Volume
import com.handtruth.mc.mcsman.common.access.VolumeAccessLevel

interface Access {
    val subject: Actor?
}

interface AllowableAccess : Access {
    val allowed: Boolean
}

data class GlobalAccess(
    override val subject: Actor?,
    val permission: String,
    override val allowed: Boolean
) : AllowableAccess

data class ServerAccess(
    override val subject: Actor?,
    val server: Server?,
    val permission: String,
    override val allowed: Boolean
) : AllowableAccess

data class VolumeAccess(
    override val subject: Actor?,
    val volume: Volume?,
    val accessLevel: VolumeAccessLevel
) : Access

data class ImageAccess(
    override val subject: Actor?,
    val wildcard: String,
    override val allowed: Boolean
) : AllowableAccess
