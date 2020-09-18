package com.handtruth.mc.mcsman.server.server

import com.handtruth.docker.DockerClient
import com.handtruth.docker.container.Container
import com.handtruth.docker.model.EndpointSettings
import com.handtruth.docker.model.RestartPolicy
import com.handtruth.kommon.Log
import com.handtruth.mc.mcsman.AlreadyExistsMCSManException
import com.handtruth.mc.mcsman.AlreadyInStateMCSManException
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.common.event.listen
import com.handtruth.mc.mcsman.common.model.ExecutableActions
import com.handtruth.mc.mcsman.event.*
import com.handtruth.mc.mcsman.server.Config
import com.handtruth.mc.mcsman.server.ReactorContext
import com.handtruth.mc.mcsman.server.access.Accesses
import com.handtruth.mc.mcsman.server.docker.Labels
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.model.ServerTable
import com.handtruth.mc.mcsman.server.model.VolumeTable
import com.handtruth.mc.mcsman.server.service.Services
import com.handtruth.mc.mcsman.server.session.getActorName
import com.handtruth.mc.mcsman.server.session.privileged
import com.handtruth.mc.mcsman.server.util.*
import com.handtruth.mc.paket.util.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.UpdateStatement
import org.koin.core.KoinComponent
import org.koin.core.get

internal class ServersFunctionality : KoinComponent, Loggable {
    private val db: Database = get()
    private val events: Events = get()
    private val dockerUtils: DockerUtils = get()
    private val docker: DockerClient = get()
    override val log: Log by logger("server")
    private val config: Config = get()
    private val servers: Servers = get()
    private val services: Services = get()
    private val accesses: Accesses = get()

    private suspend inline fun modifyServer(name: String, noinline body: ServerTable.(UpdateStatement) -> Unit) {
        suspendTransaction(db) {
            val table = ServerTable
            val count = table.update({ table.name eq name }, body = body)
            count == 1 || throw NotExistsMCSManException("server $name does not exists")
        }
        servers.get(name).update()
    }

    private suspend fun createDockerContainer(imageId: String, name: String): Container {
        val image = docker.images.wrap(imageId)
        val imageData = image.inspect()
        val paths = imageData.config.volumes.keys
        val names = uniqueNames(paths.map { Path(it) })
        var volumesCreated = 0

        return docker.containers.create(dockerUtils.dockerServerName(name)) {
            try {
                image(image)

                val address = "$name.${config.domain}"
                domainname = config.domain
                hostname = address

                label(Labels.type, Labels.Types.server)
                label(Labels.network, config.network)
                label(Labels.name, name)

                openStdin = true
                tty = false

                hostConfig {
                    for ((path, volumeName) in paths zip names) {
                        // Create volumes
                        val volume = dockerUtils.createServerVolume(volumeName, name)
                        mount(volume, path)
                        volumesCreated++
                    }

                    restartPolicy = RestartPolicy.OnFailure(config.server.maxRestartCount)
                }

                networkingConfig {
                    val endpoint = EndpointSettings(aliases = listOf(address, "mcs-$name"))
                    connect(dockerUtils.network, endpoint)
                }
            } catch (e: Exception) {
                // remove volumes that were created earlier
                log.error { "failed to create server \"$name\", aborting..." }
                for (i in 0 until volumesCreated) {
                    kotlin.runCatching {
                        dockerUtils.docker.volumes.remove(names[i], force = true)
                    }
                }
                throw e
            }
        }
    }

    fun initialize() {
        @OptIn(ReactorContext::class)
        events.react<ServerCreationEvent> { event ->
            checkImageId(event.image)

            if (event.direction) {
                // Check existence
                val table = ServerTable
                suspendTransaction(db) {
                    table.select { table.name eq event.server }.empty() ||
                            throw AlreadyExistsMCSManException("server \"${event.server}\" already exists")
                }
                // Create container
                val container = dockerUtils.findServer(event.server) ?: createDockerContainer(event.image, event.server)
                val network = container.inspect().networkSettings.networks.entries.find { it.key == config.network }
                if (network == null) {
                    val name = event.server
                    val address = "$name.${config.domain}"
                    dockerUtils.network.connect(container, EndpointSettings(aliases = listOf(address, "mcs-$name")))
                }
                // Register entities
                suspendTransaction(db) {
                    table.insert {
                        it[table.name] = event.server
                        it[table.game] = event.game
                        it[table.description] = ""
                    }
                }
            } else {
                withContext(TimeParadox) {
                    privileged {
                        val server = servers.get(event.server)
                        val actor = getActorName()
                        val companions = dockerUtils.findCompanions(event.server)
                        for (companion in companions)
                            services.get(companion).remove()
                        server.volumes.buffer().collect {
                            events.raise(VolumeCreationEvent(it.name, event.server, false))
                        }
                        accesses.server.list(server).buffer().collect {
                            accesses.server.revoke(it)
                        }
                        if (server.description.isNotEmpty())
                            events.raise(ChangeServerDescription(server.description, "", event.server, actor))
                        dockerUtils.findServer(event.server)?.remove(v = true, force = true)
                        server.delete()
                    }
                }
            }
        }

        @OptIn(ReactorContext::class)
        events.react<VolumeCreationEvent> { event ->
            requireNotNull(kotlin.coroutines.coroutineContext[TimeParadox]) { "easter egg" }
            if (event.direction) {
                suspendTransaction(db) {
                    val table = VolumeTable
                    table.insert {
                        @Suppress("UNCHECKED_CAST")
                        it[table.server] = selectServer(event.server) as Expression<EntityID<Int>>
                        it[table.name] = event.volume
                    }
                }
            } else {
                servers.volumes.borrow(event.server, event.volume).delete()
            }
        }

        events.correct<ChangeServerDescription> { event ->
            event.copy(was = servers.get(event.server).description)
        }

        events.react<ChangeServerDescription> { event ->
            modifyServer(event.server) {
                it[description] = event.become
            }
        }

        events.react<ManageServerEvent> { event ->
            val container = dockerUtils.findServer(event.server) ?: return@react
            when (event.action) {
                ExecutableActions.Start -> container.start()
                ExecutableActions.Stop -> {
                    val stopper = StopMetadata.extract(container.id)
                    val server = servers.get(event.server)
                    stopper.invoke(server)
                }
                ExecutableActions.Pause -> container.pause()
                ExecutableActions.Resume -> container.unpause()
                ExecutableActions.Kill -> container.kill()
            }
        }

        events.correct<ChangeVersionServerEvent> { event ->
            event.copy(was = dockerUtils.findServerRaw(event.server)!!.imageID)
        }

        events.react<ChangeVersionServerEvent> { event ->
            if (event.was == event.become)
                throw AlreadyInStateMCSManException("server already at specified version")
            checkImageId(event.become)
            // CRITICAL SECTION
            // TODO: Synchronizer should handle this situation
            try {
                val container = dockerUtils.findServer(event.server)!!
                container.remove(v = true, force = true)
                createDockerContainer(event.become, event.server)
            } catch (e: Exception) {
                dockerUtils.findServer(event.server) ?: createDockerContainer(event.was, event.server)
                throw e
            }
            // END OF CRITICAL SECTION
        }

        events.register<ServerLifeEvent>()
    }

    suspend fun listen(): Nothing = withContext(TimeParadox) {
        events.listen<ServerCreationEvent> { event ->
            val container = dockerUtils.findServer(event.server) ?: log.fatal {
                "recently created server does not exists, MCSMan can't afford such situations: $event"
            }
            val volumes = container.inspect().config.volumes.keys
            val names = uniqueNames(volumes.map { Path(it) })
            for (name in names) {
                try {
                    events.raise(VolumeCreationEvent(name, event.server))
                } catch (e: CancellationException) {
                    /* do nothing */
                } catch (e: Exception) {
                    log.error(e) { "failed to create a volume \"${name}\" for server \"${event.server}\"" }
                }
            }
        }
    }
}
