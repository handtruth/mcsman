package com.handtruth.mc.mcsman.server.access

import com.handtruth.mc.mcsman.AccessDeniedMCSManException
import com.handtruth.mc.mcsman.AlreadyExistsMCSManException
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.common.access.ServerPermissions
import com.handtruth.mc.mcsman.common.access.VolumeAccessLevel
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.event.GlobalPermissionEvent
import com.handtruth.mc.mcsman.event.ImageWildcardEvent
import com.handtruth.mc.mcsman.event.ServerPermissionEvent
import com.handtruth.mc.mcsman.event.VolumeAccessEvent
import com.handtruth.mc.mcsman.server.AgentCheck
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.actor.Actor
import com.handtruth.mc.mcsman.server.actor.Actors
import com.handtruth.mc.mcsman.server.actor.Group
import com.handtruth.mc.mcsman.server.actor.User
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.model.*
import com.handtruth.mc.mcsman.server.server.Server
import com.handtruth.mc.mcsman.server.server.Servers
import com.handtruth.mc.mcsman.server.server.Volume
import com.handtruth.mc.mcsman.server.session.Agent
import com.handtruth.mc.mcsman.server.session.actorName
import com.handtruth.mc.mcsman.server.session.getAgent
import com.handtruth.mc.mcsman.server.session.isAdmin
import com.handtruth.mc.mcsman.server.util.suspendTransaction
import com.handtruth.mc.mcsman.server.util.toDbWildcard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.jetbrains.exposed.sql.*
import org.koin.core.KoinComponent
import org.koin.core.inject

class Accesses : KoinComponent {

    private val globalPermissions: Permissions.Global by inject()
    private val serverPermissions: Permissions.Server by inject()
    private val servers: Servers by inject()
    private val events: Events by inject()
    private val db: Database by inject()
    private val actors: Actors by inject()

    @TransactionContext
    private fun Query.andAll(column: Expression<Boolean>): Boolean {
        val iter = withDistinct().iterator()
        if (!iter.hasNext())
            return false
        var result = iter.next()[column]
        while (iter.hasNext())
            result = result && iter.next()[column]
        return result
    }

    private fun memberOf(user: User): Query =
        GroupMemberTable.let { table -> table.slice(table.group).select { table.user eq user.id } }

    private fun BasePermissionTable.filterBy(actor: User): Op<Boolean> = Op.build {
        (user eq actor.id) or (group inSubQuery memberOf(actor)) or (user.isNull() and group.isNull())
    }

    private fun BasePermissionTable.filterBy(actor: Group): Op<Boolean> = Op.build {
        (group eq actor.id) or (user.isNull() and group.isNull())
    }

    private fun BasePermissionTable.filterBy(actor: Actor?): Op<Boolean> = when (actor) {
        null -> Op.build { user.isNull() and group.isNull() }
        is User -> filterBy(actor)
        is Group -> filterBy(actor)
        else -> throw UnsupportedOperationException()
    }

    private companion object {

        private val volumeAccessOps = with(SqlExpressionBuilder) {
            VolumeAccessTable.let { table ->
                arrayOf(
                    table.accessLevel inList listOf(
                        VolumeAccessLevel.Read, VolumeAccessLevel.Write, VolumeAccessLevel.Owner
                    ),
                    table.accessLevel inList listOf(
                        VolumeAccessLevel.Write, VolumeAccessLevel.Owner
                    ),
                    table.accessLevel eq VolumeAccessLevel.Owner
                )
            }
        }

        private fun filterVolumeAccess(accessLevel: VolumeAccessLevel): Op<Boolean> =
            volumeAccessOps[accessLevel.ordinal]

    }

    private fun BasePermissionTable.acts(actor: Actor?): Op<Boolean> = with(SqlExpressionBuilder) {
        when (actor) {
            null -> user.isNull() and group.isNull()
            is User -> user eq actor.id
            is Group -> group eq actor.id
            else -> throw UnsupportedOperationException()
        }
    }

    private fun subjectOf(actor: Actor?): Pair<String?, String?> = when (actor) {
        null -> null to null
        is User -> actor.name to null
        is Group -> null to actor.name
        else -> throw UnsupportedOperationException()
    }

    interface AssessesGeneralization<P : BasePermission> {
        suspend fun grant(rights: P): Boolean
        suspend fun revoke(rights: P): Boolean
        fun list(subject: Actor?): Flow<P>
    }

    inner class GlobalAccesses : AssessesGeneralization<GlobalPermission> {

        @TransactionContext
        fun isAllowed(actor: Actor, permission: String): Boolean {
            actor !is User && actor !is Group && return true
            val all = globalPermissions.getSuperiors(permission) + permission
            return GlobalPermissionTable.let { table ->
                table.slice(table.allowed).select { table.filterBy(actor) and (table.permission inList all) }
                    .andAll(table.allowed)
            }
        }

        suspend fun isAllowed(permission: String): Boolean {
            val agent = getAgent()
            return agent.isAdmin || @OptIn(TransactionContext::class) suspendTransaction(db) {
                isAllowed(agent.represent, permission)
            }
        }

        @TransactionContext
        fun checkAllowed(agent: Agent, permission: String, obj: Any) {
            agent.isAdmin || isAllowed(agent.represent, permission) ||
                    throw AccessDeniedMCSManException(
                        obj.toString(),
                        agent.represent,
                        permission
                    )
        }

        @AgentCheck
        suspend fun checkAllowed(permission: String, obj: Any) {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, permission, obj)
            }
        }

        @AgentCheck
        suspend fun grant(subject: Actor?, permission: String, allowed: Boolean = true): Boolean {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, GlobalPermissions.grantGlobal, "global permission grant")
            }
            val s = subjectOf(subject)
            return try {
                events.raise(GlobalPermissionEvent(permission, allowed, agent.actorName, s.first, s.second))
                true
            } catch (e: AlreadyExistsMCSManException) {
                false
            }
        }

        @AgentCheck
        override suspend fun grant(rights: GlobalPermission): Boolean {
            return grant(rights.user ?: rights.group, rights.permission, rights.allowed)
        }

        @AgentCheck
        suspend fun revoke(subject: Actor?, permission: String, allowed: Boolean = true): Boolean {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, GlobalPermissions.revokeGlobal, "global permission revoke")
            }
            val s = subjectOf(subject)
            return try {
                events.raise(
                    GlobalPermissionEvent(permission, allowed, agent.actorName, s.first, s.second, false)
                )
                true
            } catch (e: NotExistsMCSManException) {
                false
            }
        }

        @AgentCheck
        override suspend fun revoke(rights: GlobalPermission): Boolean {
            return revoke(rights.user ?: rights.group, rights.permission, rights.allowed)
        }

        override fun list(subject: Actor?): Flow<GlobalPermission> = flow {
            val table = GlobalPermissionTable
            val rows = suspendTransaction(db) {
                table.select { table.acts(subject) }.toList()
            }
            rows.forEach { row ->
                emit(
                    GlobalPermission(
                        permission = row[table.permission], allowed = row[table.allowed],
                        user = row[table.user]?.let { actors.users.get(it.value) },
                        group = row[table.group]?.let { actors.groups.get(it.value) }
                    )
                )
            }
        }

    }

    val global = GlobalAccesses()

    inner class ServerAccesses : AssessesGeneralization<ServerPermission> {

        @TransactionContext
        private fun isAllowed(actor: Actor?, server: Int?, permission: String): Boolean {
            actor !is User && actor !is Group && return true
            val all = serverPermissions.getSuperiors(permission) + permission
            return ServerPermissionTable.let { table ->
                table.slice(table.allowed)
                    .select {
                        ((table.server eq server) or table.server.isNull()) and
                                table.filterBy(actor) and (table.permission inList all)
                    }
                    .andAll(table.allowed)
            }
        }

        suspend fun isAllowed(server: Int?, permission: String): Boolean {
            val agent = getAgent()
            return agent.isAdmin || @OptIn(TransactionContext::class) suspendTransaction(db) {
                isAllowed(agent.represent, server, permission)
            }
        }

        @TransactionContext
        private fun isAllowed(actor: Actor?, server: String, permission: String): Boolean {
            actor !is User && actor !is Group && return true
            val all = serverPermissions.getSuperiors(permission) + permission
            return ServerPermissionTable.let { table ->
                table.leftJoin(ServerTable).slice(table.allowed)
                    .select {
                        ((ServerTable.name eq server) or table.server.isNull()) and
                                table.filterBy(actor) and (table.permission inList all)
                    }
                    .andAll(table.allowed)
            }
        }

        suspend fun isAllowed(server: String, permission: String): Boolean {
            val agent = getAgent()
            return agent.isAdmin || @OptIn(TransactionContext::class) suspendTransaction(db) {
                isAllowed(agent.represent, server, permission)
            }
        }

        @TransactionContext
        fun isAllowed(actor: Actor?, server: Server?, permission: String): Boolean {
            return isAllowed(actor, server?.id, permission)
        }

        suspend fun isAllowed(server: Server?, permission: String): Boolean {
            val agent = getAgent()
            return agent.isAdmin || @OptIn(TransactionContext::class) suspendTransaction(db) {
                isAllowed(agent.represent, server, permission)
            }
        }

        @TransactionContext
        fun checkAllowed(agent: Agent, server: String, permission: String) {
            agent.isAdmin || isAllowed(agent.represent, server, permission) ||
                    throw AccessDeniedMCSManException(
                        "server: $server",
                        agent.represent,
                        permission
                    )
        }

        @AgentCheck
        suspend fun checkAllowed(server: String, permission: String) {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, server, permission)
            }
        }

        @TransactionContext
        fun checkAllowed(agent: Agent, server: Server?, permission: String) {
            agent.isAdmin || isAllowed(agent.represent, server, permission) ||
                    throw AccessDeniedMCSManException(
                        if (server == null) "all servers"
                        else "server: ${server.name}",
                        agent.represent, permission
                    )
        }

        @AgentCheck
        suspend fun checkAllowed(server: Server?, permission: String) {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, server, permission)
            }
        }

        @TransactionContext
        fun checkAllowed(agent: Agent, permission: String) {
            checkAllowed(agent, null, permission)
        }

        @AgentCheck
        suspend fun checkAllowed(permission: String) {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, permission)
            }
        }

        @AgentCheck
        suspend fun grant(subject: Actor?, server: Server?, permission: String, allowed: Boolean = true): Boolean {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, server, ServerPermissions.grant)
            }
            val s = subjectOf(subject)
            return try {
                events.raise(
                    ServerPermissionEvent(
                        permission, allowed, server?.name.orEmpty(),
                        agent.actorName, s.first, s.second
                    )
                )
                true
            } catch (e: AlreadyExistsMCSManException) {
                false
            }
        }

        @AgentCheck
        override suspend fun grant(rights: ServerPermission): Boolean {
            return grant(rights.user ?: rights.group, rights.server, rights.permission, rights.allowed)
        }

        @AgentCheck
        suspend fun revoke(subject: Actor?, server: Server?, permission: String, allowed: Boolean = true): Boolean {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, server, ServerPermissions.revoke)
            }
            val s = subjectOf(subject)
            return try {
                events.raise(
                    ServerPermissionEvent(
                        permission, allowed, server?.name.orEmpty(),
                        agent.actorName, s.first, s.second, false
                    )
                )
                true
            } catch (e: NotExistsMCSManException) {
                false
            }
        }

        @AgentCheck
        override suspend fun revoke(rights: ServerPermission): Boolean {
            return revoke(rights.user ?: rights.group, rights.server, rights.permission, rights.allowed)
        }

        private suspend inline fun FlowCollector<ServerPermission>.emitResult(query: Query) {
            val table = ServerPermissionTable
            val rows = suspendTransaction(db) {
                query.toList()
            }
            rows.forEach { row ->
                emit(
                    ServerPermission(
                        server = row[table.server]?.let { servers.get(it.value) },
                        permission = row[table.permission], allowed = row[table.allowed],
                        user = row[table.user]?.let { actors.users.get(it.value) },
                        group = row[table.group]?.let { actors.groups.get(it.value) }
                    )
                )
            }
        }

        fun list(subject: Actor?, server: Server?): Flow<ServerPermission> = flow {
            val table = ServerPermissionTable
            emitResult(table.select { table.acts(subject) and (table.server eq server?.id) })
        }

        override fun list(subject: Actor?): Flow<ServerPermission> = flow {
            val table = ServerPermissionTable
            emitResult(table.select { table.acts(subject) })
        }

        fun list(server: Server?): Flow<ServerPermission> = flow {
            val table = ServerPermissionTable
            emitResult(table.select { table.server eq server?.id })
        }

    }

    val server = ServerAccesses()

    inner class ImageAccesses : AssessesGeneralization<ImageWildcard> {

        @TransactionContext
        fun isAllowed(actor: Actor?, image: ImageName): Boolean {
            actor !is User && actor !is Group && return true
            return ImageWildcardTable.let { table ->
                table.slice(table.allowed)
                    .select { table.filterBy(actor) and (LikeOp(stringParam(image.toString()), table.wildcard)) }
                    .andAll(table.allowed)
            }
        }

        suspend fun isAllowed(image: ImageName): Boolean {
            val agent = getAgent()
            return agent.isAdmin || @OptIn(TransactionContext::class) suspendTransaction(db) {
                isAllowed(agent.represent, image)
            }
        }

        @TransactionContext
        fun checkAllowed(agent: Agent, image: ImageName) {
            agent.isAdmin || isAllowed(agent.represent, image) ||
                    throw AccessDeniedMCSManException(
                        "image: $image",
                        agent.represent,
                        "use"
                    )
        }

        @AgentCheck
        suspend fun checkAllowed(image: ImageName) {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, image)
            }
        }

        @AgentCheck
        suspend fun grant(subject: Actor?, wildcard: String, allowed: Boolean = true): Boolean {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                global.checkAllowed(agent, GlobalPermissions.grantImage, "wildcard permission grant")
            }
            val template = toDbWildcard(wildcard)
            val s = subjectOf(subject)
            return try {
                events.raise(ImageWildcardEvent(template, allowed, agent.actorName, s.first, s.second))
                true
            } catch (e: AlreadyExistsMCSManException) {
                false
            }
        }

        @AgentCheck
        override suspend fun grant(rights: ImageWildcard): Boolean {
            return grant(rights.user ?: rights.group, rights.wildcard, rights.allowed)
        }

        @AgentCheck
        suspend fun revoke(subject: Actor?, wildcard: String, allowed: Boolean = true): Boolean {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                global.checkAllowed(agent, GlobalPermissions.revokeImage, "wildcard permission revoke")
            }
            val template = toDbWildcard(wildcard)
            val s = subjectOf(subject)
            return try {
                events.raise(ImageWildcardEvent(template, allowed, agent.actorName, s.first, s.second, false))
                true
            } catch (e: NotExistsMCSManException) {
                false
            }
        }

        @AgentCheck
        override suspend fun revoke(rights: ImageWildcard): Boolean {
            return revoke(rights.user ?: rights.group, rights.wildcard, rights.allowed)
        }

        override fun list(subject: Actor?): Flow<ImageWildcard> = flow {
            val table = ImageWildcardTable
            val rows = suspendTransaction(db) {
                table.select { table.acts(subject) }.toList()
            }
            rows.forEach { row ->
                emit(
                    ImageWildcard(
                        wildcard = row[table.wildcard], allowed = row[table.allowed],
                        user = row[table.user]?.let { actors.users.get(it.value) },
                        group = row[table.group]?.let { actors.groups.get(it.value) }
                    )
                )
            }
        }

    }

    val image = ImageAccesses()

    inner class VolumeAccesses : AssessesGeneralization<VolumeAccess> {

        @TransactionContext
        fun isAllowed(actor: Actor?, volume: Volume?, accessLevel: VolumeAccessLevel): Boolean {
            actor !is User && actor !is Group && return true
            return !VolumeAccessTable.let { table ->
                table.select {
                    ((table.volume eq volume?.id) or table.volume.isNull()) and
                            table.filterBy(actor) and filterVolumeAccess(accessLevel)
                }.empty()
            }
        }

        suspend fun isAllowed(volume: Volume?, accessLevel: VolumeAccessLevel): Boolean {
            val agent = getAgent()
            return agent.isAdmin || @OptIn(TransactionContext::class) suspendTransaction(db) {
                isAllowed(agent.represent, volume, accessLevel)
            }
        }

        @TransactionContext
        fun isAllowed(actor: Actor?, server: String, volume: String, accessLevel: VolumeAccessLevel): Boolean {
            actor !is User && actor !is Group && return true
            return !VolumeAccessTable.let { table ->
                (table leftJoin (VolumeTable innerJoin ServerTable)).select {
                    (((VolumeTable.name eq volume) and (ServerTable.name eq server)) or table.volume.isNull()) and
                            table.filterBy(actor) and filterVolumeAccess(accessLevel)
                }.empty()
            }
        }

        suspend fun isAllowed(server: String, volume: String, accessLevel: VolumeAccessLevel): Boolean {
            val agent = getAgent()
            return agent.isAdmin || @OptIn(TransactionContext::class) suspendTransaction(db) {
                isAllowed(agent.represent, server, volume, accessLevel)
            }
        }

        @TransactionContext
        fun isAllowed(actor: Actor?, server: Int, volume: String, accessLevel: VolumeAccessLevel): Boolean {
            actor !is User && actor !is Group && return true
            return !VolumeAccessTable.let { table ->
                (table leftJoin VolumeTable).select {
                    (((VolumeTable.name eq volume) and (VolumeTable.server eq server)) or table.volume.isNull()) and
                            table.filterBy(actor) and filterVolumeAccess(accessLevel)
                }.empty()
            }
        }

        suspend fun isAllowed(server: Int, volume: String, accessLevel: VolumeAccessLevel): Boolean {
            val agent = getAgent()
            return agent.isAdmin || @OptIn(TransactionContext::class) suspendTransaction(db) {
                isAllowed(agent.represent, server, volume, accessLevel)
            }
        }

        @TransactionContext
        fun checkAllowed(agent: Agent, volume: Volume?, accessLevel: VolumeAccessLevel) {
            agent.isAdmin || isAllowed(agent.represent, volume, accessLevel) ||
                    throw AccessDeniedMCSManException(
                        if (volume == null) "all volumes"
                        else "volume: ${volume.name}",
                        agent.represent, accessLevel.name
                    )
        }

        @AgentCheck
        suspend fun checkAllowed(volume: Volume?, accessLevel: VolumeAccessLevel) {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, volume, accessLevel)
            }
        }

        @TransactionContext
        fun checkAllowed(agent: Agent, server: Int, volume: String, accessLevel: VolumeAccessLevel) {
            agent.isAdmin || isAllowed(agent.represent, server, volume, accessLevel) ||
                    throw AccessDeniedMCSManException(
                        "volume: ${volume}",
                        agent.represent,
                        accessLevel.name
                    )
        }

        @AgentCheck
        suspend fun checkAllowed(server: Int, volume: String, accessLevel: VolumeAccessLevel) {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, server, volume, accessLevel)
            }
        }

        @TransactionContext
        fun checkAllowed(agent: Agent, accessLevel: VolumeAccessLevel) {
            checkAllowed(agent, null, accessLevel)
        }

        @AgentCheck
        suspend fun checkAllowed(accessLevel: VolumeAccessLevel) {
            val agent = getAgent()
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, accessLevel)
            }
        }

        @AgentCheck
        suspend fun setAccess(
                subject: Actor?, volume: Volume?, accessLevel: VolumeAccessLevel?, upgrade: Boolean? = null
        ): Boolean {
            val agent = getAgent()
            val actor = agent.actorName

            @OptIn(TransactionContext::class)
            val level = suspendTransaction(db) {
                checkAllowed(agent, volume, VolumeAccessLevel.Owner)
                val table = VolumeAccessTable
                val filter = Op.build { table.acts(subject) and (table.volume eq volume?.id) }
                // If already exists
                table.select { filter }.firstOrNull()?.let { it[table.accessLevel] }
            }
            val general = subjectOf(subject)
            val volumeName = volume?.name.orEmpty()
            val serverName = volume?.server?.get()?.name.orEmpty()
            return if (level != null) {
                val destruction = VolumeAccessEvent(
                    level, volumeName, serverName, actor,
                    general.first, general.second, direction = false
                )
                when {
                    upgrade != true && accessLevel == null -> {
                        events.raise(destruction)
                        true
                    }
                    accessLevel != null && (
                            upgrade != null && (
                                    upgrade && level < accessLevel || !upgrade && level > accessLevel
                                    ) || upgrade == null && level != accessLevel
                            ) -> {
                        events.raise(destruction)
                        val event = VolumeAccessEvent(
                            accessLevel, volumeName, serverName, actor,
                            general.first, general.second, direction = true
                        )
                        events.raise(event)
                        true
                    }
                    else -> false
                }
            } else {
                if (upgrade != false && accessLevel != null) {
                    val event = VolumeAccessEvent(
                        accessLevel, volumeName, serverName, actor,
                        general.first, general.second, direction = true
                    )
                    events.raise(event)
                    true
                } else {
                    false
                }
            }
        }

        @AgentCheck
        suspend fun setAccess(rights: VolumeAccess, upgrade: Boolean? = null): Boolean {
            return setAccess(rights.user ?: rights.group, rights.volume, rights.accessLevel, upgrade)
        }

        @AgentCheck
        override suspend fun grant(rights: VolumeAccess): Boolean {
            return setAccess(rights.user ?: rights.group, rights.volume, rights.accessLevel)
        }

        @AgentCheck
        suspend fun revoke(subject: Actor?, volume: Volume?, accessLevel: VolumeAccessLevel): Boolean {
            val agent = getAgent()
            val actor = agent.actorName
            @OptIn(TransactionContext::class)
            suspendTransaction(db) {
                checkAllowed(agent, volume, VolumeAccessLevel.Owner)
            }
            val s = subjectOf(subject)
            return try {
                events.raise(
                    VolumeAccessEvent(
                        accessLevel, volume?.name.orEmpty(), volume?.server?.get()?.name.orEmpty(),
                        actor, s.first, s.second, false
                    )
                )
                true
            } catch (e: NotExistsMCSManException) {
                false
            }
        }

        @AgentCheck
        override suspend fun revoke(rights: VolumeAccess): Boolean {
            return revoke(rights.user ?: rights.group, rights.volume, rights.accessLevel)
        }

        private suspend inline fun FlowCollector<VolumeAccess>.emitResult(query: Query) {
            val table = VolumeAccessTable
            val rows = suspendTransaction(db) {
                query.toList()
            }
            rows.forEach { row ->
                emit(
                    VolumeAccess(
                        volume = row[table.volume]?.let { servers.volumes.get(it.value) },
                        accessLevel = row[table.accessLevel],
                        user = row[table.user]?.let { actors.users.get(it.value) },
                        group = row[table.group]?.let { actors.groups.get(it.value) }
                    )
                )
            }
        }

        fun list(subject: Actor?, volume: Volume?): Flow<VolumeAccess> = flow {
            val table = VolumeAccessTable
            emitResult(table.select { table.acts(subject) and (table.volume eq volume?.id) })
        }

        override fun list(subject: Actor?): Flow<VolumeAccess> = flow {
            val table = VolumeAccessTable
            emitResult(table.select { table.acts(subject) })
        }

        fun list(volume: Volume?): Flow<VolumeAccess> = flow {
            val table = VolumeAccessTable
            emitResult(table.select { table.volume eq volume?.id })
        }

    }

    val volume = VolumeAccesses()

}
