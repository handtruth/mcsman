package com.handtruth.mc.mcsman.server.actor

import com.handtruth.mc.mcsman.AlreadyInStateMCSManException
import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.event.BlockUserEvent
import com.handtruth.mc.mcsman.event.ChangeUserEMailEvent
import com.handtruth.mc.mcsman.event.ChangeUserRealNameEvent
import com.handtruth.mc.mcsman.event.UserCreationEvent
import com.handtruth.mc.mcsman.server.AgentCheck
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.model.GroupMemberTable
import com.handtruth.mc.mcsman.server.model.GroupTable
import com.handtruth.mc.mcsman.server.model.UserTable
import com.handtruth.mc.mcsman.server.session.actorName
import com.handtruth.mc.mcsman.server.session.getAgent
import com.handtruth.mc.mcsman.server.util.IntIdShadow
import com.handtruth.mc.mcsman.server.util.suspendTransaction
import com.handtruth.mc.mcsman.util.Removable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jetbrains.exposed.sql.select
import org.koin.core.KoinComponent
import org.koin.core.get

class User : IntIdShadow<User>(), Actor, KoinComponent, Removable {

    override val controller = get<Actors.Users>()

    val name by UserTable.name
    val realName by UserTable.realName
    val blocked by UserTable.blocked
    val email by UserTable.email

    private inline val groupController get() = controller.actors.groups

    val groups: Flow<Group> =
        flow {
            val table = GroupMemberTable
            suspendTransaction(controller.db) {
                table.slice(GroupMemberTable.group)
                    .select { GroupMemberTable.user eq this@User.id }
                    .toList()
            }.forEach {
                emit(groupController.get(it[GroupMemberTable.group].value))
            }
        }

    val ownedGroups: Flow<Group> =
        flow {
            val table = GroupTable
            suspendTransaction(controller.db) {
                table.slice(table.id)
                    .select { GroupTable.owner eq this@User.id }
                    .toList()
            }.forEach {
                emit(groupController.get(it[table.id].value))
            }
        }

    private suspend fun checkPermissions(selfPerm: String, globalPerm: String): String {
        val agent = getAgent()
        @OptIn(TransactionContext::class)
        suspendTransaction(controller.db) {
            val global = controller.accesses.global
            val actor = agent.represent
            if (!(actor is User && this@User == actor && global.isAllowed(actor, selfPerm)))
                global.checkAllowed(agent, globalPerm, this@User)
        }
        return agent.actorName
    }

    private suspend inline fun checkChPermissions() =
        checkPermissions(GlobalPermissions.chSelf, GlobalPermissions.chUser)

    @AgentCheck
    suspend fun changeRealName(newRealName: String) {
        val actor = checkChPermissions()
        controller.events.raise(ChangeUserRealNameEvent(realName, newRealName, name, actor))
    }

    private suspend fun checkBlockPermission(): String {
        val agent = getAgent()
        @OptIn(TransactionContext::class)
        suspendTransaction(controller.db) {
            controller.accesses.global.checkAllowed(agent, GlobalPermissions.blockUser, this@User)
        }
        return agent.actorName
    }

    @AgentCheck
    suspend fun block(blocked: Boolean = true): Boolean {
        val actor = checkBlockPermission()
        return try {
            controller.events.raise(BlockUserEvent(name, actor, blocked))
            true
        } catch (e: AlreadyInStateMCSManException) {
            false
        }
    }

    @AgentCheck
    suspend fun changeEMail(newEMail: String?) {
        val actor = checkChPermissions()
        controller.events.raise(ChangeUserEMailEvent(email, newEMail, name, actor))
    }

    @AgentCheck
    override suspend fun remove() {
        val actor = checkPermissions(GlobalPermissions.rmSelf, GlobalPermissions.rmUser)
        controller.events.raise(UserCreationEvent(name, actor, false))
    }

    override fun toString() = "user: $name"
}
