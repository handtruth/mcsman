package com.handtruth.mc.mcsman.server.actor

import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.event.ChangeGroupRealNameEvent
import com.handtruth.mc.mcsman.event.ChangeOwnerOfGroupEvent
import com.handtruth.mc.mcsman.event.GroupCreationEvent
import com.handtruth.mc.mcsman.event.GroupMemberEvent
import com.handtruth.mc.mcsman.server.AgentCheck
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.model.GroupMemberTable
import com.handtruth.mc.mcsman.server.model.GroupTable
import com.handtruth.mc.mcsman.server.session.Agent
import com.handtruth.mc.mcsman.server.session.actorName
import com.handtruth.mc.mcsman.server.session.getAgent
import com.handtruth.mc.mcsman.server.util.IntIdShadow
import com.handtruth.mc.mcsman.server.util.invoke
import com.handtruth.mc.mcsman.server.util.suspendTransaction
import com.handtruth.mc.mcsman.util.Removable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.exposed.sql.select
import org.koin.core.KoinComponent
import org.koin.core.get

class Group : IntIdShadow<Group>(), Actor, KoinComponent,
    Removable {

    override val controller = get<Actors.Groups>()

    private inline val users get() = controller.actors.users

    val name by GroupTable.name
    val realName by GroupTable.realName
    val owner by users with GroupTable.owner

    val members: Flow<User> =
        flow {
            val table = GroupMemberTable
            suspendTransaction(controller.db) {
                table.slice(GroupMemberTable.user)
                    .select { GroupMemberTable.group eq this@Group.id }
                    .toList()
            }.forEach {
                emit(users.get(it[GroupMemberTable.user].value))
            }
        }

    @TransactionContext
    private fun permGroupCheck(agent: Agent, permission: String) {
        GroupTable.let { table ->
            table.slice(table.owner).select { table.id eq this@Group.id }.first()[table.owner]?.value
        }?.let { owner ->
            agent.represent.let { it is User && it.id == owner } && return
        }
        controller.accesses.global.checkAllowed(agent, permission, this)
    }

    private suspend inline fun chGroupCheck(permission: String = GlobalPermissions.chGroup): String {
        val agent = getAgent()
        @OptIn(TransactionContext::class)
        suspendTransaction(controller.db) {
            permGroupCheck(agent, permission)
        }
        return agent.actorName
    }

    @AgentCheck
    suspend infix fun addMember(user: User) = node.invoke {
        val actor = chGroupCheck()
        controller.events.raise(GroupMemberEvent(user.name, name, actor, true))
    }

    @AgentCheck
    suspend infix fun removeMember(user: User) = node.invoke {
        val actor = chGroupCheck()
        controller.events.raise(GroupMemberEvent(user.name, name, actor, false))
    }

    @AgentCheck
    suspend fun changeOwner(user: User?) = node.invoke {
        val actor = chGroupCheck()
        controller.events.raise(ChangeOwnerOfGroupEvent(owner.get()?.name, user?.name, name, actor))
    }

    @AgentCheck
    suspend fun changeRealName(newRealName: String) = node.invoke {
        val actor = chGroupCheck()
        controller.events.raise(ChangeGroupRealNameEvent(realName, newRealName, name, actor))
    }

    @AgentCheck
    override suspend fun remove() {
        val actor = chGroupCheck(GlobalPermissions.rmGroup)
        controller.events.raise(GroupCreationEvent(name, actor, false))
    }

    override fun toString() = "group: $name"
}
