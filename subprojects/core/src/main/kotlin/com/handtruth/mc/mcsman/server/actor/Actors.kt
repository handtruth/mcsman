package com.handtruth.mc.mcsman.server.actor

import com.handtruth.kommon.Log
import com.handtruth.kommon.LogLevel
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.event.*
import com.handtruth.mc.mcsman.server.AgentCheck
import com.handtruth.mc.mcsman.server.MCSManCore
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.access.Accesses
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.model.GroupMemberTable
import com.handtruth.mc.mcsman.server.model.GroupTable
import com.handtruth.mc.mcsman.server.model.UserTable
import com.handtruth.mc.mcsman.server.session.actorName
import com.handtruth.mc.mcsman.server.session.getAgent
import com.handtruth.mc.mcsman.server.util.*
import kotlinx.coroutines.CoroutineDispatcher
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.inject

open class Actors : TaskBranch {

    private val accesses: Accesses by inject()
    private val events: Events by inject()

    final override val coroutineContext = MCSManCore.fork("actor")
    final override val log = coroutineContext[Log]!!
    final override fun fork(part: String, lvl: LogLevel, dispatcher: CoroutineDispatcher) =
        super.fork(part, lvl, dispatcher)

    open inner class Groups : IntIdShadow.IntIdController<Group>(GroupTable), NamedShadowsController<Group, Int> {
        internal val accesses get() = this@Actors.accesses
        internal val events get() = this@Actors.events
        val actors get() = this@Actors

        final override val coroutineContext = this@Actors.fork("group")
        final override val log = coroutineContext[Log]!!

        override suspend fun spawn() = Group()

        override suspend fun getOrNull(name: String): Group? {
            val table = GroupTable
            return findOne(table.select { table.name eq name })
        }

        @TransactionContext
        fun isUserAllowed(user: Int, group: String): Boolean {
            val isNotOwner = GroupTable.let { table ->
                table.select { (table.owner eq user) and (table.name eq group) }.empty()
            }
            return !isNotOwner || !GroupMemberTable.let { table ->
                table.innerJoin(GroupTable, { table.group }, { GroupTable.id })
                    .select { (table.user eq user) and (GroupTable.name eq group) }.empty()
            }
        }

        @TransactionContext
        fun isUserAllowed(user: Int, group: Int): Boolean {
            val isNotOwner = GroupTable.let { table ->
                table.select { (table.owner eq user) and (table.id eq group) }.empty()
            }
            return !isNotOwner || !GroupMemberTable.let { table ->
                table.select { (table.user eq user) and (table.group eq group) }.empty()
            }
        }

        @AgentCheck
        suspend fun getId(name: String): Int {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            return suspendTransaction(db) {
                if (!agent.represent.let {
                        it is Group && it.name == name || it is User && isUserAllowed(it.id, name)
                    })
                    accesses.global.checkAllowed(agent, GlobalPermissions.groupList, "group: $name")
                val table = GroupTable
                table.slice(table.id).select { table.name eq name }.limit(1).firstOrNull()?.let { it[table.id].value }
                    ?: throw NotExistsMCSManException("group $name does not exists")
            }
        }

        @AgentCheck
        suspend fun listNames(): Map<Int, String> {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            return suspendTransaction(db) {
                accesses.global.checkAllowed(agent, GlobalPermissions.groupList, "list groups")
                val table = GroupTable
                table.slice(table.id, table.name).selectAll().associate { it[table.id].value to it[table.name] }
            }
        }

        @AgentCheck
        suspend fun findByRealName(realName: String): Map<Int, String> {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            return suspendTransaction(db) {
                accesses.global.checkAllowed(agent, GlobalPermissions.searchGroup, "search group by real name")
                val table = GroupTable
                val wildcard = toDbWildcard(realName)
                table.slice(table.id, table.name).select { table.realName like wildcard }
                    .associate { it[table.id].value to it[table.name] }
            }
        }

        @AgentCheck
        suspend fun findByOwner(owner: Int?): Map<Int, String> {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            return suspendTransaction(db) {
                accesses.global.checkAllowed(agent, GlobalPermissions.searchGroup, "search group by owner")
                val table = GroupTable
                table.slice(table.id, table.name).select { table.owner eq owner }
                    .associate { it[table.id].value to it[table.name] }
            }
        }

        @AgentCheck
        open suspend fun create(name: String, realName: String = name, own: Boolean = true): Group {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                accesses.global.checkAllowed(agent, GlobalPermissions.mkGroup, "create group")
            }
            val actor = agent.actorName
            events.raise(GroupCreationEvent(name, actor))
            val group = get(name)
            if (name != realName)
                events.raise(ChangeGroupRealNameEvent(name, realName, name, actor))
            if (own)
                events.raise(ChangeOwnerOfGroupEvent(null, actor, name, actor))
            return group
        }
    }

    open val groups: Groups = Groups()

    open inner class Users : IntIdShadow.IntIdController<User>(UserTable), NamedShadowsController<User, Int> {
        internal val accesses get() = this@Actors.accesses
        internal val events get() = this@Actors.events
        val actors get() = this@Actors

        final override val coroutineContext = this@Actors.fork("user")
        final override val log = coroutineContext[Log]!!

        override suspend fun spawn() = User()

        override suspend fun getOrNull(name: String): User? {
            val table = UserTable
            return findOne(table.select { table.name eq name })
        }

        @AgentCheck
        suspend fun getId(name: String): Int {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            return suspendTransaction(db) {
                if (!agent.represent.let { it is User && it.name == name })
                    accesses.global.checkAllowed(agent, GlobalPermissions.userList, "user: $name")
                val table = UserTable
                table.slice(table.id).select { table.name eq name }.limit(1).firstOrNull()?.let { it[table.id].value }
                    ?: throw NotExistsMCSManException("user $name does not exists")
            }
        }

        @AgentCheck
        suspend fun listNames(): Map<Int, String> {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            return suspendTransaction(db) {
                accesses.global.checkAllowed(agent, GlobalPermissions.userList, "list users")
                val table = UserTable
                table.slice(table.id, table.name).selectAll().associate { it[table.id].value to it[table.name] }
            }
        }

        @AgentCheck
        suspend fun findByRealName(realName: String): Map<Int, String> {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            return suspendTransaction(db) {
                accesses.global.checkAllowed(agent, GlobalPermissions.searchUser, "search user by real name")
                val table = UserTable
                val wildcard = toDbWildcard(realName)
                table.slice(table.id, table.name).select { table.realName like wildcard }
                    .associate { it[table.id].value to it[table.name] }
            }
        }

        @AgentCheck
        suspend fun findByEmail(email: String?): Map<Int, String> {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            return suspendTransaction(db) {
                accesses.global.checkAllowed(agent, GlobalPermissions.searchUser, "search user by email")
                val table = UserTable
                table.slice(table.id, table.name).select { table.email eq email }
                    .associate { it[table.id].value to it[table.name] }
            }
        }

        @AgentCheck
        open suspend fun create(name: String, realName: String = name, email: String? = null): User {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                accesses.global.checkAllowed(agent, GlobalPermissions.mkUser, "create user")
            }
            val actor = agent.actorName
            events.raise(UserCreationEvent(name, actor, true))
            val user = get(name)
            if (name != realName)
                events.raise(ChangeUserRealNameEvent(name, realName, name, actor))
            if (email != null)
                events.raise(ChangeUserEMailEvent(null, email, name, actor))
            return user
        }
    }

    open val users: Users = Users()
}
