package com.handtruth.mc.mcsman.server.model

import com.handtruth.mc.mcsman.common.access.VolumeAccessLevel
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.`java-time`.datetime

object UserTable : IntIdTable() {
    val name = varchar("name", varCharLength).uniqueIndex()
    val realName = varchar("real_name", varCharLength).index()
    val blocked = bool("blocked").default(false)
    val email = varchar("email", varCharLength).nullable().uniqueIndex()
}

object ServiceTable : IntIdTable() {
    val name = varchar("name", 60).uniqueIndex()
    val className = text("class_name")
    val state = blob("state")
}

object ServerTable : IntIdTable() {
    val name = varchar("name", 60).uniqueIndex()
    //val container = varchar("container", 64)
    val description = text("description")
    val game = varchar("game", varCharLength).index().nullable()
}

object VolumeTable : IntIdTable() {
    val name = varchar("name", varCharLength).index()
    val server = reference("server", ServerTable).index()
}

object LoginMethodTable : IntIdTable() {
    val method = varchar("method", varCharLength).index()
    val algorithm = varchar("algorithm", varCharLength)
    val data = text("data")
    val user = reference("user", UserTable).index()
    val enabled = bool("enabled").default(true)
    val expiryDate = datetime("expiry_date").nullable()
}

object GroupTable : IntIdTable() {
    val name = varchar("name", varCharLength).uniqueIndex()
    val realName = varchar("real_name", varCharLength).index()
    val owner = reference("owner", UserTable).nullable().index()
}

object GroupMemberTable : Table() {
    val user = reference("user", UserTable).index()
    val group = reference("group", GroupTable).index()

    override val primaryKey = PrimaryKey(user, group)
}

abstract class BasePermissionTable : Table() {
    val user = reference("user", UserTable).nullable().index()
    val group = reference("group", GroupTable).nullable().index()
}

abstract class BaseAllowableTable : BasePermissionTable() {
    val allowed = bool("allowed").default(true)
}

object ServerPermissionTable : BaseAllowableTable() {
    val server = reference("server", ServerTable).nullable()
    val permission = varchar("permission", varCharLength)
}

object VolumeAccessTable : BasePermissionTable() {
    val volume = reference("volume", VolumeTable).nullable()
    val accessLevel =
        enumerationByName("access_level", varCharLength, VolumeAccessLevel::class).default(
            VolumeAccessLevel.Read)
}

object ImageWildcardTable : BaseAllowableTable() {
    val wildcard = varchar("wildcard", varCharLength)
}

object GlobalPermissionTable : BaseAllowableTable() {
    val permission = varchar("permission", varCharLength)
}

val tables = listOf(
    UserTable, ServerTable, VolumeTable, LoginMethodTable,
    GroupTable, GroupMemberTable, ServerPermissionTable, VolumeAccessTable,
    ImageWildcardTable, GlobalPermissionTable, ServiceTable
)
