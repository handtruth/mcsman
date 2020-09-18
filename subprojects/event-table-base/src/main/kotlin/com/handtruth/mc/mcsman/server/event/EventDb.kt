package com.handtruth.mc.mcsman.server.event

import com.handtruth.mc.mcsman.server.model.varCharLength
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.`java-time`.timestamp

sealed class EventTableBase(name: String) : IdTable<Int>(name)

object EventTable : EventTableBase("event") {
    override val id: Column<EntityID<Int>> = integer("id").autoIncrement().entityId()
    override val primaryKey by lazy { super.primaryKey ?: PrimaryKey(id) }

    val className = varchar("className", varCharLength).index()
    val success = bool("success")
    val timestamp = timestamp("timestamp")
}

open class InterfaceEventTable(name: String) : EventTableBase("${name}_event") {
    final override val id = integer("id").entityId()

    init {
        id.references(EventTable.id)
    }

    final override val primaryKey by lazy { super.primaryKey ?: PrimaryKey(id) }
}

object CancellableEventTable : InterfaceEventTable("cancellable")

object DirectEventTable : InterfaceEventTable("direct") {
    val direction = bool("direction")
}

object MutateEventTable : InterfaceEventTable("mutate")
