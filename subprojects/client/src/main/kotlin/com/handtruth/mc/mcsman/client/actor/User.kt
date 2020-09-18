package com.handtruth.mc.mcsman.client.actor

import com.handtruth.mc.mcsman.client.util.NamedEntityInfo
import com.handtruth.mc.mcsman.util.Removable

interface User : Actor, Removable {
    override val controller: Actors.Users

    override suspend fun inspect(): UserInfo

    suspend fun setRealName(realName: String)
    suspend fun setEmail(email: String?)
    suspend fun block()
    suspend fun unblock()
}

data class UserInfo(
    override val id: Int,
    override val name: String,
    val realName: String,
    val email: String?,
    val blocked: Boolean,
    val groups: List<Group>,
    val ownedGroups: List<Group>
) : NamedEntityInfo
