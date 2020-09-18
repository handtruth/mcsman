package com.handtruth.mc.mcsman.server.util

import com.handtruth.mc.mcsman.server.model.GroupTable
import com.handtruth.mc.mcsman.server.model.ServerTable
import com.handtruth.mc.mcsman.server.model.UserTable
import com.handtruth.mc.mcsman.server.model.VolumeTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.wrapAsExpression

fun selectUser(name: String): Expression<EntityID<Int>?> {
    val table = UserTable
    return wrapAsExpression(table.slice(table.id).select { table.name eq name })
}

fun selectGroup(name: String): Expression<EntityID<Int>?> {
    val table = GroupTable
    return wrapAsExpression(table.slice(table.id).select { table.name eq name })
}

fun selectServer(name: String): Expression<EntityID<Int>?> {
    val table = ServerTable
    return wrapAsExpression(table.slice(table.id).select { table.name eq name })
}

fun selectVolume(server: String, volume: String): Expression<EntityID<Int>?> {
    val table = VolumeTable innerJoin ServerTable
    return wrapAsExpression(table.slice(VolumeTable.id).select {
        (ServerTable.name eq server) and (VolumeTable.name eq volume)
    })
}
