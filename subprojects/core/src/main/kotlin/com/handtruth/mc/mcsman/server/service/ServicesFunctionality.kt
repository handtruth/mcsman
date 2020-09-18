package com.handtruth.mc.mcsman.server.service

import com.handtruth.mc.mcsman.AlreadyExistsMCSManException
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.event.ManageServiceEvent
import com.handtruth.mc.mcsman.event.SendCommand2ServiceEvent
import com.handtruth.mc.mcsman.event.ServiceCreationEvent
import com.handtruth.mc.mcsman.event.ServiceLifeEvent
import com.handtruth.mc.mcsman.server.ReactorContext
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.model.ServiceTable
import com.handtruth.mc.mcsman.server.util.suspendTransaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.koin.core.KoinComponent
import org.koin.core.get

internal class ServicesFunctionality : KoinComponent {
    private val events: Events = get()
    private val db: Database = get()
    private val services: Services = get()

    private fun notExists(name: String): Nothing =
        throw NotExistsMCSManException("service $name does not exists")

    private fun alreadyExists(name: String): Nothing {
        throw AlreadyExistsMCSManException("service $name already exists")
    }

    fun initialize() {
        events.correct<ServiceCreationEvent> { event ->
            if (!event.direction) {
                val table = ServiceTable
                val service = suspendTransaction(db) {
                    table.select { table.name eq event.service }.firstOrNull()
                } ?: notExists(event.service)
                event.copy(state = service[table.state].bytes, className = service[table.className])
            } else {
                event
            }
        }

        @OptIn(ReactorContext::class)
        events.react<ServiceCreationEvent> { event ->
            val table = ServiceTable
            if (event.direction) {
                val notExists = suspendTransaction(db) {
                    table.select { table.name eq event.service }.empty()
                }
                if (!notExists)
                    throw alreadyExists(event.service)
                val id = suspendTransaction(db) {
                    table.insertAndGetId {
                        it[table.name] = event.service
                        it[table.className] = event.className
                        it[table.state] = ExposedBlob(event.state)
                    }.value
                }
                services.get(id).invokeOnCreate()
            } else {
                val notExists = suspendTransaction(db) {
                    table.select { table.name eq event.service }.empty()
                }
                if (notExists)
                    notExists(event.service)
                services.get(event.service).delete()
            }
        }

        events.react<ManageServiceEvent> { event ->
            services.get(event.service).invokeManage(event.action)
        }

        events.react<SendCommand2ServiceEvent> { event ->
            services.get(event.service).internalSend.send(event.command)
        }

        events.register<ServiceLifeEvent>()
    }
}
