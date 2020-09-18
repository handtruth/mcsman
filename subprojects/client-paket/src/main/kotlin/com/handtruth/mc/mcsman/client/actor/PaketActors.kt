package com.handtruth.mc.mcsman.client.actor

import com.handtruth.mc.mcsman.client.PaketMCSManClient
import com.handtruth.mc.mcsman.protocol.GroupPaket
import com.handtruth.mc.mcsman.protocol.UserPaket
import com.handtruth.mc.paket.peek

class PaketActors internal constructor(
    override val client: PaketMCSManClient
) : Actors {
    override val users = PaketUsers()
    override val groups = PaketGroups()

    inner class PaketUsers internal constructor() : Actors.Users {
        override val client: PaketMCSManClient get() = this@PaketActors.client
        override val actors: PaketActors get() = this@PaketActors

        override fun get(id: Int) = PaketUser(this, id)

        override suspend fun get(name: String): PaketUser {
            val paket = client.request(UserPaket.GetIdRequest(name)) { peek(UserPaket.GetIdResponse) }
            return PaketUser(this, paket.user, paket.name)
        }

        override suspend fun list(): List<PaketUser> {
            val paket = client.request(UserPaket.ListRequest) { peek(UserPaket.ListResponse) }
            return paket.ids.zip(paket.names) { id, name -> PaketUser(this, id, name) }
        }

        override suspend fun findByEmail(email: String?): List<PaketUser> {
            val paket = client.request(UserPaket.FindByEMailRequest(email.orEmpty())) {
                peek(UserPaket.FindByEMailResponse)
            }
            return paket.ids.zip(paket.names) { id, name -> PaketUser(this, id, name) }
        }

        override suspend fun findByRealName(realName: String): List<PaketUser> {
            val paket = client.request(UserPaket.FindByRealNameRequest(realName)) {
                peek(UserPaket.FindByRealNameResponse)
            }
            return paket.ids.zip(paket.names) { id, name -> PaketUser(this, id, name) }
        }

        override suspend fun create(name: String, realName: String?, email: String?): PaketUser {
            val paket = client.request(UserPaket.CreateRequest(name, realName.orEmpty(), email.orEmpty())) {
                peek(UserPaket.CreateResponse)
            }
            return PaketUser(this, paket.user, paket.name)
        }
    }

    inner class PaketGroups internal constructor() : Actors.Groups {
        override val client: PaketMCSManClient get() = this@PaketActors.client
        override val actors: PaketActors get() = this@PaketActors

        override fun get(id: Int) = PaketGroup(this, id)

        override suspend fun get(name: String): PaketGroup {
            val paket = client.request(GroupPaket.GetIdRequest(name)) { peek(GroupPaket.GetIdResponse) }
            return PaketGroup(this, paket.group, paket.name)
        }

        override suspend fun list(): List<PaketGroup> {
            val paket = client.request(GroupPaket.ListRequest) { peek(GroupPaket.ListResponse) }
            return paket.ids.zip(paket.names) { id, name -> PaketGroup(this, id, name) }
        }

        override suspend fun findByRealName(realName: String): List<PaketGroup> {
            val paket = client.request(GroupPaket.FindByRealNameRequest(realName)) {
                peek(GroupPaket.FindByRealNameResponse)
            }
            return paket.ids.zip(paket.names) { id, name -> PaketGroup(this, id, name) }
        }

        override suspend fun findByOwner(owner: User?): List<PaketGroup> {
            val paket = client.request(GroupPaket.FindByOwnerRequest(owner?.id ?: 0)) {
                peek(GroupPaket.FindByOwnerResponse)
            }
            return paket.ids.zip(paket.names) { id, name -> PaketGroup(this, id, name) }
        }

        override suspend fun create(name: String, realName: String?): PaketGroup {
            val paket = client.request(GroupPaket.CreateRequest(name, realName.orEmpty())) {
                peek(GroupPaket.CreateResponse)
            }
            return PaketGroup(this, paket.group, paket.name)
        }
    }
}
