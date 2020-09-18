package com.handtruth.mc.mcsman.server

import com.handtruth.docker.DockerClient
import com.handtruth.docker.model.system.SystemEvent
import com.handtruth.docker.model.system.SystemEventFilters
import com.handtruth.kommon.Log
import com.handtruth.mc.mcsman.event.*
import com.handtruth.mc.mcsman.server.docker.Labels
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.event.Reactor
import com.handtruth.mc.mcsman.server.model.ServerTable
import com.handtruth.mc.mcsman.server.model.ServiceTable
import com.handtruth.mc.mcsman.server.service.DockerService
import com.handtruth.mc.mcsman.server.util.TaskBranch
import com.handtruth.mc.mcsman.server.util.suspendTransaction
import com.handtruth.mc.mcsman.server.util.unreachable
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.koin.core.inject

open class Synchronizer : TaskBranch {

    final override val coroutineContext = MCSManCore.fork("synchronizer", supervised = false)

    final override val log = coroutineContext[Log]!!

    private val docker: DockerClient by inject()
    private val conf: Configuration by inject()
    private val events: Events by inject()
    private val db: Database by inject()

    private val task = launch(start = CoroutineStart.LAZY) {
        log.info { "starting real time synchronizer" }
        docker.system.events {
            scope += SystemEventFilters.Scope.Local
            label(Labels.network, conf.network)
        }.buffer().collect {
            log.verbose { "process docker event: $it" }
            try {
                when (it.type) {
                    "container" -> container(it)
                }
            } catch (e: Exception) {
                log.error(e) { "synchronization failed" }
            }
        }
    }

    internal suspend fun initialize() {
        log.info { "starting synchronization phase..." }
        coroutineScope {
            launch { prepareServers() }
            launch { prepareServices() }
        }
        log.info { "synchronization done" }
        start()
    }

    private suspend fun prepareServers() {
        val list = docker.containers.listRaw(all = true) {
            label(Labels.name)
            label(Labels.network, conf.network)
            label(Labels.type, Labels.Types.server)
        }
        coroutineScope {
            list.forEach { response ->
                launch(Dispatchers.Default) {
                    val name = response.labels[Labels.name] ?: unreachable
                    val notExists = suspendTransaction(db) {
                        val table = ServerTable
                        table.select { table.name eq name }.empty()
                    }
                    if (notExists) {
                        log.info { "server \"$name\" not registered. Creating it..." }
                        val game = response.labels[Labels.game]
                        events.raise(ServerCreationEvent(response.image, game, name, "docker", direction = true))
                    }
                }
            }
        }
        log.info { "all the missing servers were created" }
        val names = suspendTransaction(db) {
            val table = ServerTable
            table.slice(table.name).selectAll().map { it[table.name] }
        }
        val map = list.associateBy { it.labels[Labels.name] ?: unreachable }
        coroutineScope {
            for (name in names) {
                launch(Dispatchers.Default) {
                    if (map[name] == null) {
                        log.info { "server \"$name\" was removed with docker. Removing it..." }
                        events.raise(ServerCreationEvent("", null, name, "docker", direction = false))
                    }
                }
            }
        }
        log.info { "all the deleted servers are removed" }
    }

    private suspend fun prepareServices() {
        val map = docker.containers.listRaw(all = true) {
            label(Labels.name)
            label(Labels.network, conf.network)
            label(Labels.type, Labels.Types.service)
        }.associateBy { it.labels[Labels.name] ?: unreachable }
        val names = suspendTransaction(db) {
            val table = ServiceTable
            table.slice(table.name).selectAll().map { it[table.name] }
        }
        coroutineScope {
            for (name in names) {
                launch(Dispatchers.Default) {
                    if (map[name] == null) {
                        log.warning { "service \"$name\" was removed with docker. Removing it..." }
                        events.raise(
                            ServiceCreationEvent(ByteArray(0), "", name, "docker", direction = false)
                        )
                    }
                }
            }
        }
    }

    protected open fun start() {
        task.start()
    }

    private suspend fun container(dockerEvent: SystemEvent) {
        val attributes = dockerEvent.actor.attributes
        val name = attributes[Labels.name] ?: return
        val type = attributes[Labels.type] ?: return
        when (dockerEvent.action) {
            "create" -> {
                when (type) {
                    Labels.Types.server -> {
                        events.reaction(Reactor.Types.Write) {
                            val notExists = suspendTransaction(db) {
                                ServerTable.select { ServerTable.name eq name }.empty()
                            }
                            if (notExists) {
                                events.raise(
                                    ServerCreationEvent(
                                        attributes["image"] ?: error("no \"image\" attribute"),
                                        attributes[Labels.game],
                                        attributes[Labels.name] ?: error("no \"${Labels.name}\" attribute"),
                                        "docker", direction = true
                                    )
                                )
                            }
                        }
                    }
                    Labels.Types.service -> { /* do nothing */
                    }
                    else -> {
                        log.error {
                            "IT'S TIME TO STOP!!! WHAT DO YOU WANT? BREAK MCSMAN?! FUCK YOU! " +
                                    "(MCSMan detected that you created container that is not a server)"
                        }
                    }
                }
            }
            "destroy" -> {
                when (attributes[Labels.type]) {
                    Labels.Types.server -> {
                        events.reaction(Reactor.Types.Write) {
                            val exists = suspendTransaction(db) {
                                !ServerTable.select { ServerTable.name eq name }.empty()
                            }
                            if (exists)
                                events.raise(
                                    ServerCreationEvent("", "", name, "docker", direction = false)
                                )
                        }
                    }
                    Labels.Types.service -> {
                        events.reaction(Reactor.Types.Write) {
                            val exists = suspendTransaction(db) {
                                !ServiceTable.select { ServiceTable.name eq name }.empty()
                            }
                            if (exists) {
                                log.warning { "trying to remove service \"$name\" by external docker daemon manipulation" }
                                val className = when (val serviceType = attributes[Labels.service]) {
                                    null -> {
                                        log.warning { "service type not set. This is not how MCSMan does..." }
                                        return@reaction
                                    }
                                    Labels.Services.companion -> DockerService.Companion::class
                                    Labels.Services.global -> DockerService.Global::class
                                    Labels.Services.replica -> {
                                        log.error {
                                            "replica service type not implemented yet. " +
                                                    "This value is reserved for MCSMan IV"
                                        }
                                        return@reaction
                                    }
                                    else -> {
                                        log.error { "unknown service type: $serviceType" }
                                        return@reaction
                                    }
                                }
                                events.raise(
                                    ServiceCreationEvent(
                                        ByteArray(0), className.qualifiedName!!, name, "docker", direction = false
                                    )
                                )
                            }
                        }
                    }
                    else -> log.warning { "YES!!! DELETE YOUR SHIT" }
                }
            }
            "die" -> {
                when (type) {
                    Labels.Types.server -> events.raise(
                        ServerLifeEvent(Transitions.Starting, name, direction = false)
                    )
                    Labels.Types.service -> events.raise(
                        ServiceLifeEvent(Transitions.Starting, name, direction = false)
                    )
                }
            }
            "start" -> {
                when (type) {
                    Labels.Types.server -> events.raise(
                        ServerLifeEvent(Transitions.Starting, name, direction = true)
                    )
                    Labels.Types.service -> events.raise(
                        ServiceLifeEvent(Transitions.Starting, name, direction = true)
                    )
                }
            }
            "pause" -> {
                when (type) {
                    Labels.Types.server -> events.raise(
                        ServerLifeEvent(Transitions.Pausing, name, direction = true)
                    )
                    Labels.Types.service -> events.raise(
                        ServiceLifeEvent(Transitions.Pausing, name, direction = true)
                    )
                }
            }
            "unpause" -> {
                when (type) {
                    Labels.Types.server -> events.raise(
                        ServerLifeEvent(Transitions.Pausing, name, direction = false)
                    )
                    Labels.Types.service -> events.raise(
                        ServiceLifeEvent(Transitions.Pausing, name, direction = false)
                    )
                }
            }
        }
    }
}
