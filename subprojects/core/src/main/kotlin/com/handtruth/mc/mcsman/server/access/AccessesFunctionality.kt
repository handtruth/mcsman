package com.handtruth.mc.mcsman.server.access

import com.handtruth.mc.mcsman.AlreadyExistsMCSManException
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.event.*
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.model.*
import com.handtruth.mc.mcsman.server.session.getActorName
import com.handtruth.mc.mcsman.server.util.*
import org.jetbrains.exposed.sql.*
import org.koin.core.KoinComponent
import org.koin.core.get

internal class AccessesFunctionality : KoinComponent {
    private val events: Events = get()
    private val db: Database = get()

    private fun BasePermissionTable.acts(event: PermissionSubjectEvent): Op<Boolean> =
        with(SqlExpressionBuilder) {
            val userSubject = event.userSubject
            val groupSubject = event.groupSubject
            when {
                userSubject == null && groupSubject == null -> user.isNull() and group.isNull()
                userSubject != null -> user eq selectUser(userSubject)
                groupSubject != null -> group eq selectGroup(groupSubject)
                else -> throw UnsupportedOperationException()
            }
        }

    private fun errMessage(
        event: PermissionSubjectEvent,
        obj: String?,
        permission: String?,
        allowed: Boolean?
    ): String {
        val userSubject = event.userSubject
        val groupSubject = event.groupSubject
        val subject = when {
            userSubject == null && groupSubject == null -> "everyone"
            userSubject != null -> "user [$userSubject]"
            groupSubject != null -> "group [$groupSubject]"
            else -> throw UnsupportedOperationException()
        }
        return "access${if (permission != null) " $permission" else ""}${if (allowed != null) " ($allowed)" else ""}" +
                "${if (obj != null) " on \"$obj\"" else ""} for $subject"
    }

    private fun notExists(
        event: PermissionSubjectEvent,
        obj: String?,
        permission: String?,
        allowed: Boolean?
    ): Nothing {
        throw NotExistsMCSManException(errMessage(event, obj, permission, allowed) + " not exists")
    }

    private fun alreadyExists(
        event: PermissionSubjectEvent, obj: String?, permission: String?, allowed: Boolean?
    ): Nothing {
        throw AlreadyExistsMCSManException(errMessage(event, obj, permission, allowed) + " already exists")
    }

    fun initialize() {

        events.react<GlobalPermissionEvent> { event ->
            val actor = getActorName()
            val table = GlobalPermissionTable
            val filter = Op.build { table.acts(event) and (table.permission eq event.permission) }
            if (event.direction) {
                val notExisted = suspendTransaction(db) {
                    table.select { filter and (table.allowed eq event.allowed) }.empty() ||
                            alreadyExists(event, null, event.permission, event.allowed)
                    table.select { filter }.empty()
                }
                if (!notExisted) {
                    events.raise(
                        GlobalPermissionEvent(
                            event.permission, !event.allowed, actor,
                            event.userSubject, event.groupSubject, false
                        )
                    )
                }
                suspendTransaction(db) {
                    table.insert {
                        it[table.permission] = event.permission
                        it[table.allowed] = event.allowed
                        val userSubject = event.userSubject
                        if (userSubject != null)
                            it[table.user] = selectUser(userSubject)
                        val groupSubject = event.groupSubject
                        if (groupSubject != null)
                            it[table.group] = selectGroup(groupSubject)
                    }
                }
            } else {
                val count = suspendTransaction(db) {
                    table.deleteWhere { filter and (table.allowed eq event.allowed) }
                }
                count == 0 && notExists(event, null, event.permission, event.allowed)
            }
        }

        events.react<ServerPermissionEvent> { event ->
            val actor = getActorName()
            val table = ServerPermissionTable
            val filter = Op.build {
                table.acts(event) and
                        (if (event.server.isNotEmpty()) table.server eq selectServer(event.server)
                        else table.server.isNull()) and (table.permission eq event.permission)
            }
            if (event.direction) {
                val notExisted = suspendTransaction(db) {
                    table.select { filter and (table.allowed eq event.allowed) }.empty() ||
                            alreadyExists(event, event.server, event.permission, event.allowed)
                    table.select { filter }.empty()
                }
                if (!notExisted) {
                    events.raise(
                        ServerPermissionEvent(
                            event.permission, !event.allowed, event.server, actor,
                            event.userSubject, event.groupSubject, false
                        )
                    )
                }
                suspendTransaction(db) {
                    table.insert {
                        it[table.permission] = event.permission
                        it[table.allowed] = event.allowed
                        val server = event.server
                        if (server.isNotEmpty())
                            it[table.server] = selectServer(server)
                        val userSubject = event.userSubject
                        if (userSubject != null)
                            it[table.user] = selectUser(userSubject)
                        val groupSubject = event.groupSubject
                        if (groupSubject != null)
                            it[table.group] = selectGroup(groupSubject)
                    }
                }
            } else {
                val count = suspendTransaction(db) {
                    table.deleteWhere { filter and (table.allowed eq event.allowed) }
                }
                count == 0 && notExists(event, event.server, event.permission, event.allowed)
            }
        }

        events.react<ImageWildcardEvent> { event ->
            val actor = getActorName()
            val table = ImageWildcardTable
            val filter = Op.build {
                table.acts(event) and (table.wildcard eq event.wildcard)
            }
            if (event.direction) {
                val notExisted = suspendTransaction(db) {
                    table.select { filter and (table.allowed eq event.allowed) }.empty() ||
                            alreadyExists(event, "wildcard: ${event.wildcard}", null, event.allowed)
                    table.select { filter }.empty()
                }
                if (!notExisted) {
                    events.raise(
                        ImageWildcardEvent(
                            event.wildcard, !event.allowed, actor,
                            event.userSubject, event.groupSubject, false
                        )
                    )
                }
                suspendTransaction(db) {
                    table.insert {
                        it[table.allowed] = event.allowed
                        it[table.wildcard] = event.wildcard
                        val userSubject = event.userSubject
                        if (userSubject != null)
                            it[table.user] = selectUser(userSubject)
                        val groupSubject = event.groupSubject
                        if (groupSubject != null)
                            it[table.group] = selectGroup(groupSubject)
                    }
                }
            } else {
                val count = suspendTransaction(db) {
                    table.deleteWhere { filter and (table.allowed eq event.allowed) }
                }
                count == 0 && notExists(event, "wildcard: ${event.wildcard}", null, event.allowed)
            }
        }

        events.react<VolumeAccessEvent> { event ->
            val actor = getActorName()
            val table = VolumeAccessTable
            require(!event.server.isEmpty() || event.volume.isEmpty())
            val filter = Op.build {
                table.acts(event) and (if (event.server.isNotEmpty())
                    table.volume eq selectVolume(event.server, event.volume)
                else
                    table.volume.isNull())
            }
            if (event.direction) {
                val oldLevel = suspendTransaction(db) {
                    table.select { filter and (table.accessLevel eq event.accessLevel) }.empty() ||
                            alreadyExists(
                                event, "volume: ${event.volume} of server: ${event.server}",
                                event.accessLevel.name, null
                            )
                    table.select { filter }.firstOrNull()?.let { it[table.accessLevel] }
                }
                if (oldLevel != null) {
                    events.raise(
                        VolumeAccessEvent(
                            oldLevel, event.volume, event.server, actor,
                            event.userSubject, event.groupSubject, false
                        )
                    )
                }
                suspendTransaction(db) {
                    table.insert {
                        it[table.accessLevel] = event.accessLevel
                        if (event.server.isNotEmpty())
                            it[table.volume] = selectVolume(event.server, event.volume)
                        val userSubject = event.userSubject
                        if (userSubject != null)
                            it[table.user] = selectUser(userSubject)
                        val groupSubject = event.groupSubject
                        if (groupSubject != null)
                            it[table.group] = selectGroup(groupSubject)
                    }
                }
            } else {
                val count = suspendTransaction(db) {
                    table.deleteWhere { filter and (table.accessLevel eq event.accessLevel) }
                }
                count == 0 && notExists(
                    event, "volume: ${event.volume} of server: ${event.server}",
                    event.accessLevel.name, null
                )
            }
        }
    }

}
