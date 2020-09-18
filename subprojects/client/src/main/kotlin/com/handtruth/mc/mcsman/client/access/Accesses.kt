package com.handtruth.mc.mcsman.client.access

import com.handtruth.mc.mcsman.client.MCSManClient
import com.handtruth.mc.mcsman.client.actor.Actor
import com.handtruth.mc.mcsman.client.server.Server
import com.handtruth.mc.mcsman.client.server.Volume
import com.handtruth.mc.mcsman.common.access.VolumeAccessLevel
import com.handtruth.mc.mcsman.common.model.ImageName

interface Accesses {
    val client: MCSManClient

    val global: GlobalAccesses
    val server: ServerAccesses
    val volume: VolumeAccesses
    val image: ImageAccesses

    interface AccessesCommon<R : Access> {
        val accesses: Accesses

        suspend fun listForActor(actor: Actor?): List<R>
        suspend fun grant(access: R)
        suspend fun revoke(access: R)
    }

    interface GlobalAccesses : AccessesCommon<GlobalAccess> {
        suspend fun check(subject: Actor, permission: String): Boolean
        suspend fun checkSelf(permission: String): Boolean
    }

    interface ServerAccesses : AccessesCommon<ServerAccess> {
        suspend fun check(subject: Actor, server: Server?, permission: String): Boolean
        suspend fun checkSelf(server: Server?, permission: String): Boolean
        suspend fun listForServer(server: Server?): List<ServerAccess>
        suspend fun listForActorAndServer(actor: Actor?, server: Server?): List<ServerAccess>
    }

    interface VolumeAccesses : AccessesCommon<VolumeAccess> {
        suspend fun check(subject: Actor, volume: Volume?, accessLevel: VolumeAccessLevel): Boolean
        suspend fun checkSelf(volume: Volume?, accessLevel: VolumeAccessLevel): Boolean
        suspend fun listForVolume(volume: Volume?): List<VolumeAccess>
        suspend fun listForActorAndVolume(actor: Actor?, volume: Volume?): List<VolumeAccess>
        suspend fun elevate(access: VolumeAccess)
        suspend fun demote(access: VolumeAccess)
    }

    interface ImageAccesses : AccessesCommon<ImageAccess> {
        suspend fun check(subject: Actor, image: ImageName): Boolean
        suspend fun checkSelf(image: ImageName): Boolean
    }
}
