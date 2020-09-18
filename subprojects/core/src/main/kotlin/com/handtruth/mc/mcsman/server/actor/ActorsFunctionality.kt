package com.handtruth.mc.mcsman.server.actor

import com.handtruth.mc.mcsman.AlreadyExistsMCSManException
import com.handtruth.mc.mcsman.AlreadyInStateMCSManException
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.event.*
import com.handtruth.mc.mcsman.server.ReactorContext
import com.handtruth.mc.mcsman.server.access.Accesses
import com.handtruth.mc.mcsman.server.access.BasePermission
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.model.GroupMemberTable
import com.handtruth.mc.mcsman.server.model.GroupTable
import com.handtruth.mc.mcsman.server.model.UserTable
import com.handtruth.mc.mcsman.server.session.privileged
import com.handtruth.mc.mcsman.server.util.selectUser
import com.handtruth.mc.mcsman.server.util.suspendTransaction
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.koin.core.KoinComponent
import org.koin.core.get

internal class ActorsFunctionality : KoinComponent {
    private val events: Events = get()
    private val db: Database = get()
    private val actors: Actors = get()
    private val accesses: Accesses = get()

    private suspend fun <P : BasePermission> Accesses.AssessesGeneralization<P>.revokeFor(subject: Actor) {
        list(subject).buffer().collect { revoke(it) }
    }

    private val accessControllers = listOf(accesses.global, accesses.server, accesses.image, accesses.volume)

    private suspend fun revokeAll(subject: Actor) {
        accessControllers.forEach { it.revokeFor(subject) }
    }

    private fun alreadyExists(name: String, type: String): Nothing =
        throw AlreadyExistsMCSManException("$type \"$name\" already exists")

    private fun notExists(name: String, type: String): Nothing =
        throw NotExistsMCSManException("$type \"$name\" not exists")

    private suspend inline fun modifyUser(name: String, noinline body: UserTable.(UpdateStatement) -> Unit) {
        suspendTransaction(db) {
            val table = UserTable
            val count = table.update({ table.name eq name }, body = body)
            count == 1 || notExists(name, "user")
        }
        actors.users.get(name).update()
    }

    private suspend inline fun modifyGroup(name: String, noinline body: GroupTable.(UpdateStatement) -> Unit) {
        suspendTransaction(db) {
            val table = GroupTable
            val count = table.update({ table.name eq name }, body = body)
            count == 1 || notExists(name, "group")
        }
        actors.groups.get(name).update()
    }

    fun initialize() {

        @OptIn(ReactorContext::class)
        events.react<GroupCreationEvent> { event ->
            val table = GroupTable
            if (event.direction) {
                suspendTransaction(db) {
                    val notExists = table.select { table.name eq event.group }.empty()
                    notExists || alreadyExists(event.group, "group")
                    table.insert {
                        it[table.name] = event.group
                        it[table.realName] = event.group
                        it[table.owner] = null
                    }
                }
            } else {
                val notExists = suspendTransaction(db) {
                    table.select { table.name eq event.group }.empty()
                }
                notExists && notExists(event.group, "group")
                privileged {
                    val group = actors.groups.get(event.group)
                    revokeAll(group)
                    group.members.buffer().collect {
                        group removeMember it
                    }
                    val owner = group.owner.get()
                    if (owner != null)
                        group.changeOwner(null)
                    if (group.name != group.realName)
                        group.changeRealName(group.name)
                    group.delete()
                }
            }
        }

        events.react<GroupMemberEvent> { event ->
            suspendTransaction(db) {
                val user = UserTable.let { table ->
                    table.slice(table.id).select { table.name eq event.user }
                }
                val group = GroupTable.let { table ->
                    table.slice(table.id).select { table.name eq event.group }
                }
                val table = GroupMemberTable
                if (event.direction) {
                    table.insert((user.alias("u") crossJoin group.alias("g")).selectAll())
                } else {
                    table.deleteWhere { (table.user inSubQuery user) and (table.group inSubQuery group) }
                }
            }
        }

        events.correct<ChangeGroupRealNameEvent> { event ->
            event.copy(was = actors.groups.get(event.group).realName)
        }

        events.react<ChangeGroupRealNameEvent> { event ->
            modifyGroup(event.group) {
                it[realName] = event.become
            }
        }

        events.correct<ChangeOwnerOfGroupEvent> { event ->
            event.copy(was = actors.groups.get(event.group).owner.get()?.name)
        }

        events.react<ChangeOwnerOfGroupEvent> { event ->
            modifyGroup(event.group) {
                val ownerName = event.become
                if (ownerName != null) {
                    it[owner] = selectUser(ownerName)
                } else {
                    it[owner] = null
                }
            }
        }

        @OptIn(ReactorContext::class)
        events.react<UserCreationEvent> { event ->
            val table = UserTable
            if (event.direction) {
                suspendTransaction(db) {
                    val notExists = table.select { table.name eq event.user }.empty()
                    notExists || alreadyExists(event.user, "user")
                    table.insert {
                        it[table.name] = event.user
                        it[table.realName] = event.user
                    }
                }
            } else {
                val notExists = suspendTransaction(db) {
                    table.select { table.name eq event.user }.empty()
                }
                notExists && notExists(event.user, "user")
                privileged {
                    val user = actors.users.get(event.user)
                    revokeAll(user)
                    user.groups.buffer().collect {
                        it removeMember user
                    }
                    user.ownedGroups.buffer().collect {
                        it.changeOwner(null)
                    }
                    if (user.blocked)
                        user.block(false)
                    if (user.email != null)
                        user.changeEMail(null)
                    if (user.realName != user.name)
                        user.changeRealName(user.name)
                    user.delete()
                }
            }
        }

        events.correct<ChangeUserRealNameEvent> { event ->
            event.copy(was = actors.users.get(event.user).realName)
        }

        events.react<ChangeUserRealNameEvent> { event ->
            modifyUser(event.user) {
                it[realName] = event.become
            }
        }

        events.correct<ChangeUserEMailEvent> { event ->
            event.copy(was = actors.users.get(event.user).email)
        }

        events.react<ChangeUserEMailEvent> { event ->
            modifyUser(event.user) {
                it[email] = event.become
            }
        }

        events.react<BlockUserEvent> { event ->
            val user = actors.users.get(event.user)
            if (event.direction == user.blocked)
                throw AlreadyInStateMCSManException("user ${event.user} already in the specified block state")
            modifyUser(event.user) {
                it[blocked] = event.direction
            }
        }
    }
}
