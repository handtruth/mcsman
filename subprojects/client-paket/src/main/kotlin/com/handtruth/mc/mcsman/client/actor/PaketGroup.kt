package com.handtruth.mc.mcsman.client.actor

import com.handtruth.kommon.concurrent.later
import com.handtruth.kommon.concurrent.laterOf
import com.handtruth.mc.mcsman.protocol.GroupPaket
import com.handtruth.mc.paket.peek

class PaketGroup internal constructor(
    override val controller: PaketActors.PaketGroups,
    override val id: Int,
    name: String? = null
) : Group {
    private suspend fun get() = controller.client.request(GroupPaket.GetRequest(id)) { peek(GroupPaket.GetResponse) }

    override val name = if (name != null) laterOf(name) else later { get().name }

    override suspend fun inspect() = with(get()) {
        val users = controller.actors.users
        GroupInfo(
            group, name, realName, owner.let { if (it == 0) null else users.get(it) }, members.map { users.get(it) }
        )
    }

    override suspend fun setRealName(realName: String) {
        controller.client.request(GroupPaket.ChangeRealNameRequest(id, realName))
    }

    override suspend fun setOwner(user: User?) {
        controller.client.request(GroupPaket.ChangeOwnerRequest(id, user?.id ?: 0))
    }

    override suspend fun addMember(user: User) {
        controller.client.request(GroupPaket.AddMemberRequest(id, user.id))
    }

    override suspend fun removeMember(user: User) {
        controller.client.request(GroupPaket.RemoveMemberRequest(id, user.id))
    }

    override suspend fun remove() {
        controller.client.request(GroupPaket.RemoveRequest(id))
    }
}
