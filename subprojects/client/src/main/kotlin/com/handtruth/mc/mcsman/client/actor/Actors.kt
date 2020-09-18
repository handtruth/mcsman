package com.handtruth.mc.mcsman.client.actor

import com.handtruth.mc.mcsman.client.MCSManClient
import com.handtruth.mc.mcsman.client.util.NamedController

interface Actors {
    val client: MCSManClient

    val users: Users

    val groups: Groups

    interface Users : NamedController {
        val actors: Actors

        override fun get(id: Int): User
        override suspend fun get(name: String): User

        override suspend fun list(): List<User>
        suspend fun findByRealName(realName: String): List<User>
        suspend fun findByEmail(email: String?): List<User>
        suspend fun create(name: String, realName: String? = null, email: String? = null): User
    }

    interface Groups : NamedController {
        val actors: Actors

        override fun get(id: Int): Group
        override suspend fun get(name: String): Group

        override suspend fun list(): List<Group>

        suspend fun findByRealName(realName: String): List<Group>
        suspend fun findByOwner(owner: User?): List<Group>
        suspend fun create(name: String, realName: String? = null): Group
    }
}
