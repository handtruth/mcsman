package com.handtruth.mc.mcsman.client.access

import com.handtruth.mc.mcsman.client.PaketMCSManClient
import com.handtruth.mc.mcsman.client.actor.Actor
import com.handtruth.mc.mcsman.client.actor.Group
import com.handtruth.mc.mcsman.client.actor.User
import com.handtruth.mc.mcsman.client.server.Server
import com.handtruth.mc.mcsman.client.server.Volume
import com.handtruth.mc.mcsman.client.service.Service
import com.handtruth.mc.mcsman.client.util.subject
import com.handtruth.mc.mcsman.common.access.VolumeAccessLevel
import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.protocol.GlobalAccessPaket
import com.handtruth.mc.mcsman.protocol.ImageAccessPaket
import com.handtruth.mc.mcsman.protocol.ServerAccessPaket
import com.handtruth.mc.mcsman.protocol.VolumeAccessPaket
import com.handtruth.mc.paket.peek

class PaketAccesses internal constructor(override val client: PaketMCSManClient) : Accesses {
    override val global = PaketGlobalAccesses()
    override val server = PaketServerAccesses()
    override val volume = PaketVolumeAccesses()
    override val image = PaketImageAccesses()

    private val Actor?.agentType: AgentTypes
        get() = when (this) {
            null, is User -> AgentTypes.User
            is Group -> AgentTypes.Group
            is Service -> AgentTypes.Service
            else -> throw UnsupportedOperationException()
        }

    private inline val Actor?.id get() = this?.id ?: 0

    inner class PaketGlobalAccesses internal constructor() : Accesses.GlobalAccesses {
        override val accesses: PaketAccesses get() = this@PaketAccesses

        private suspend fun checkAllowed(subject: Actor?, permission: String): Boolean {
            return client.request(
                GlobalAccessPaket.CheckRequest(
                    subject.agentType, subject.id, permission
                )
            ) { peek(GlobalAccessPaket.CheckResponse) }.allowed
        }

        override suspend fun check(subject: Actor, permission: String) = checkAllowed(subject, permission)

        override suspend fun checkSelf(permission: String) = checkAllowed(null, permission)

        override suspend fun listForActor(actor: Actor?): List<GlobalAccess> {
            val paket = client.request(GlobalAccessPaket.ListForActorRequest(actor.agentType, actor.id)) {
                peek(GlobalAccessPaket.ListForActorResponse)
            }
            val receivedActor = client.subject(paket.agent, paket.actor)
            return paket.permissions.zip(paket.allowance) { permission, allowed ->
                GlobalAccess(receivedActor, permission, allowed)
            }
        }

        override suspend fun grant(access: GlobalAccess) {
            client.request(
                GlobalAccessPaket.GrantRequest(
                    access.subject.agentType, access.subject.id, access.permission, access.allowed
                )
            )
        }

        override suspend fun revoke(access: GlobalAccess) {
            client.request(
                GlobalAccessPaket.RevokeRequest(
                    access.subject.agentType, access.subject.id, access.permission, access.allowed
                )
            )
        }
    }

    inner class PaketServerAccesses internal constructor() : Accesses.ServerAccesses {
        override val accesses: PaketAccesses get() = this@PaketAccesses

        private fun serverOf(server: Int) = if (server == 0) null else client.servers.get(server)

        private suspend fun checkAllowed(subject: Actor?, server: Server?, permission: String): Boolean {
            return client.request(
                ServerAccessPaket.CheckRequest(
                    subject.agentType, subject.id, server?.id ?: 0, permission
                )
            ) { peek(ServerAccessPaket.CheckResponse) }.allowed
        }

        override suspend fun check(subject: Actor, server: Server?, permission: String) =
            checkAllowed(subject, server, permission)

        override suspend fun checkSelf(server: Server?, permission: String) =
            checkAllowed(null, server, permission)

        override suspend fun listForActor(actor: Actor?): List<ServerAccess> {
            val paket = client.request(ServerAccessPaket.ListForActorRequest(actor.agentType, actor.id)) {
                peek(ServerAccessPaket.ListForActorResponse)
            }
            val receivedActor = client.subject(paket.agent, paket.actor)
            val servers = paket.servers
            val permissions = paket.permissions
            val allowance = paket.allowance
            return List(servers.size) { i ->
                ServerAccess(receivedActor, serverOf(servers[i]), permissions[i], allowance[i])
            }
        }

        override suspend fun listForServer(server: Server?): List<ServerAccess> {
            val paket = client.request(ServerAccessPaket.ListForServerRequest(server?.id ?: 0)) {
                peek(ServerAccessPaket.ListForServerResponse)
            }
            val agentTypes = paket.agents
            val actorId = paket.actors
            val receivedServer = serverOf(paket.server)
            val permissions = paket.permissions
            val allowance = paket.allowance
            return List(agentTypes.size) { i ->
                ServerAccess(client.subject(agentTypes[i], actorId[i]), receivedServer, permissions[i], allowance[i])
            }
        }

        override suspend fun listForActorAndServer(actor: Actor?, server: Server?): List<ServerAccess> {
            val paket = client.request(
                ServerAccessPaket.ListForActorAndServerRequest(actor.agentType, actor.id, server?.id ?: 0)
            ) {
                peek(ServerAccessPaket.ListForActorAndServerResponse)
            }
            val receivedActor = client.subject(paket.agent, paket.actor)
            val receivedServer = serverOf(paket.server)
            return paket.permissions.zip(paket.allowance) { permission, allowance ->
                ServerAccess(receivedActor, receivedServer, permission, allowance)
            }
        }

        override suspend fun grant(access: ServerAccess) {
            client.request(
                ServerAccessPaket.GrantRequest(
                    access.subject.agentType, access.subject.id,
                    access.server?.id ?: 0, access.permission, access.allowed
                )
            )
        }

        override suspend fun revoke(access: ServerAccess) {
            client.request(
                ServerAccessPaket.RevokeRequest(
                    access.subject.agentType, access.subject.id,
                    access.server?.id ?: 0, access.permission, access.allowed
                )
            )
        }
    }

    inner class PaketVolumeAccesses internal constructor() : Accesses.VolumeAccesses {
        override val accesses: PaketAccesses get() = this@PaketAccesses

        private fun volumeOf(volume: Int) = if (volume == 0) null else client.servers.volumes.get(volume)

        private suspend fun checkAllowed(subject: Actor?, volume: Volume?, accessLevel: VolumeAccessLevel): Boolean {
            return client.request(
                VolumeAccessPaket.CheckRequest(
                    subject.agentType, subject.id, volume?.id ?: 0, accessLevel
                )
            ) { peek(VolumeAccessPaket.CheckResponse) }.allowed
        }

        override suspend fun check(subject: Actor, volume: Volume?, accessLevel: VolumeAccessLevel) =
            checkAllowed(subject, volume, accessLevel)

        override suspend fun checkSelf(volume: Volume?, accessLevel: VolumeAccessLevel) =
            checkAllowed(null, volume, accessLevel)

        override suspend fun listForActor(actor: Actor?): List<VolumeAccess> {
            val paket = client.request(VolumeAccessPaket.ListForActorRequest(actor.agentType, actor.id)) {
                peek(VolumeAccessPaket.ListForActorResponse)
            }
            val receivedActor = client.subject(paket.agent, paket.actor)
            return paket.volumes.zip(paket.levels) { volume, level ->
                VolumeAccess(receivedActor, volumeOf(volume), level)
            }
        }

        override suspend fun listForVolume(volume: Volume?): List<VolumeAccess> {
            val paket = client.request(VolumeAccessPaket.ListForVolumeRequest(volume?.id ?: 0)) {
                peek(VolumeAccessPaket.ListForVolumeResponse)
            }
            val agentTypes = paket.agents
            val actors = paket.actors
            val receivedVolume = volumeOf(paket.volume)
            val levels = paket.levels
            return List(agentTypes.size) { i ->
                VolumeAccess(client.subject(agentTypes[i], actors[i]), receivedVolume, levels[i])
            }
        }

        override suspend fun listForActorAndVolume(actor: Actor?, volume: Volume?): List<VolumeAccess> {
            val paket = client.request(
                VolumeAccessPaket.ListForActorAndVolumeRequest(actor.agentType, actor.id, volume?.id ?: 0)
            ) {
                peek(VolumeAccessPaket.ListForActorAndVolumeResponse)
            }
            val receivedActor = client.subject(paket.agent, paket.actor)
            val receivedVolume = volumeOf(paket.volume)
            val levels = paket.levels
            return levels.map { level ->
                VolumeAccess(receivedActor, receivedVolume, level)
            }
        }

        override suspend fun grant(access: VolumeAccess) {
            client.request(
                VolumeAccessPaket.GrantRequest(
                    access.subject.agentType, access.subject.id, access.volume?.id ?: 0, access.accessLevel
                )
            )
        }

        override suspend fun revoke(access: VolumeAccess) {
            client.request(
                VolumeAccessPaket.RevokeRequest(
                    access.subject.agentType, access.subject.id, access.volume?.id ?: 0, access.accessLevel
                )
            )
        }

        override suspend fun elevate(access: VolumeAccess) {
            client.request(
                VolumeAccessPaket.ElevateRequest(
                    access.subject.agentType, access.subject.id, access.volume?.id ?: 0, access.accessLevel
                )
            )
        }

        override suspend fun demote(access: VolumeAccess) {
            client.request(
                VolumeAccessPaket.DemoteRequest(
                    access.subject.agentType, access.subject.id, access.volume?.id ?: 0, access.accessLevel
                )
            )
        }
    }

    inner class PaketImageAccesses internal constructor() : Accesses.ImageAccesses {
        override val accesses: PaketAccesses get() = this@PaketAccesses

        private suspend fun checkAllowed(subject: Actor?, image: ImageName): Boolean {
            return client.request(
                ImageAccessPaket.CheckRequest(
                    subject.agentType, subject.id, image.toString()
                )
            ) { peek(ImageAccessPaket.CheckResponse) }.allowed
        }

        override suspend fun check(subject: Actor, image: ImageName) = checkAllowed(subject, image)

        override suspend fun checkSelf(image: ImageName) = checkAllowed(null, image)

        override suspend fun listForActor(actor: Actor?): List<ImageAccess> {
            val paket = client.request(ImageAccessPaket.ListForActorRequest(actor.agentType, actor.id)) {
                peek(ImageAccessPaket.ListForActorResponse)
            }
            val receivedActor = client.subject(paket.agent, paket.actor)
            return paket.wildcards.zip(paket.allowance) { wildcard, allowed ->
                ImageAccess(receivedActor, wildcard, allowed)
            }
        }

        override suspend fun grant(access: ImageAccess) {
            client.request(
                ImageAccessPaket.GrantRequest(
                    access.subject.agentType, access.subject.id, access.wildcard, access.allowed
                )
            )
        }

        override suspend fun revoke(access: ImageAccess) {
            client.request(
                ImageAccessPaket.RevokeRequest(
                    access.subject.agentType, access.subject.id, access.wildcard, access.allowed
                )
            )
        }
    }
}
