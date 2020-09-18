package com.handtruth.mc.mcsman.client.actor

import com.handtruth.mc.mcsman.client.util.NamedEntityInfo
import com.handtruth.mc.mcsman.util.Removable

interface Group : Actor, Removable {
    override val controller: Actors.Groups

    override suspend fun inspect(): GroupInfo

    suspend fun setRealName(realName: String)
    suspend fun setOwner(user: User?)
    suspend fun addMember(user: User)
    suspend fun removeMember(user: User)
}

data class GroupInfo(
    override val id: Int,
    override val name: String,
    val realName: String,
    val owner: User?,
    val members: List<User>
) : NamedEntityInfo
