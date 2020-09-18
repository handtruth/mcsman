package com.handtruth.mc.mcsman.client.actor

import com.handtruth.kommon.concurrent.Later
import com.handtruth.kommon.concurrent.later
import com.handtruth.kommon.concurrent.laterOf
import com.handtruth.mc.mcsman.protocol.UserPaket
import com.handtruth.mc.paket.peek

class PaketUser internal constructor(
    override val controller: PaketActors.PaketUsers,
    override val id: Int,
    name: String? = null
) : User {
    private suspend fun get() = controller.client.request(UserPaket.GetRequest(id)) { peek(UserPaket.GetResponse) }

    override val name: Later<String> = if (name != null) laterOf(name) else later {
        get().name
    }

    override suspend fun inspect() = with(get()) {
        val groupsC = controller.actors.groups
        UserInfo(
            user, name, realName, email.let { if (it.isEmpty()) null else it }, blocked,
            groups.map { groupsC.get(it) }, ownedGroups.map { groupsC.get(it) }
        )
    }

    override suspend fun setEmail(email: String?) {
        controller.client.request(UserPaket.ChangeEMailRequest(id, email.orEmpty()))
    }

    override suspend fun setRealName(realName: String) {
        controller.client.request(UserPaket.ChangeRealNameRequest(id, realName))
    }

    override suspend fun block() {
        controller.client.request(UserPaket.BlockRequest(id))
    }

    override suspend fun unblock() {
        controller.client.request(UserPaket.UnblockRequest(id))
    }

    override suspend fun remove() {
        controller.client.request(UserPaket.RemoveRequest(id))
    }
}
