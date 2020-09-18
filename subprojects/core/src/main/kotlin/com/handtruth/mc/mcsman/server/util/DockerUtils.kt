package com.handtruth.mc.mcsman.server.util

import com.handtruth.docker.DockerClient
import com.handtruth.docker.container.Container
import com.handtruth.docker.image.Image
import com.handtruth.docker.model.EndpointSettings
import com.handtruth.docker.model.container.ContainersListResponse
import com.handtruth.docker.network.Network
import com.handtruth.docker.volume.Volume
import com.handtruth.kommon.Log
import com.handtruth.kommon.LogFactory
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.server.Configuration
import com.handtruth.mc.mcsman.server.MCSManInitializeException
import com.handtruth.mc.mcsman.server.docker.Labels
import io.ktor.http.*
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import java.net.URI
import java.util.*

internal class DockerUtils internal constructor(
    private val config: Configuration, val logger: LogFactory, val db: Database
) : Loggable {

    override val log: Log

    val docker: DockerClient

    init {
        val logFactory = logger.factory("docker")
        log = logFactory.log()
        val url = resolveDockerURL(config.docker.url)
        var dockerClient: Result<DockerClient> = Result.failure(Exception("UNREACHABLE"))
        runBlocking {
            for (i in 0..10) {
                try {
                    val client = DockerClient(url, logFactory)
                    client.system.ping()
                    dockerClient = Result.success(client)
                    break
                } catch (e: Exception) {
                    dockerClient = Result.failure(e)
                }
                log.debug { "#$i retrying connect..." }
                delay(100)
            }
        }
        docker = dockerClient.getOrElse {
            log.fatal(it) { "failed to connect to docker daemon" }
        }
    }

    val network: Network
    val mcsman: Container

    init {
        val (network, mcsman) = runBlocking {
            log.verbose { "connecting to Docker..." }
            docker.system.ping()
            log.verbose { "initializing network..." }
            val network = initializeNetwork()
            log.verbose { "configuring MCSMan container..." }
            val mcsman = initializeMCSManContainer(network)
            network to mcsman
        }
        this.network = network
        this.mcsman = mcsman
    }

    private fun resolveDockerURL(url: URI): Url {
        return Url(
            when (url.scheme) {
                "unix" -> {
                    //Runtime.getRuntime().exec(arrayOf("tlproxy", "tcp::2375", "unix:${url.path}"))
                    Runtime.getRuntime().exec(
                        arrayOf(
                            "socat",
                            "TCP4-LISTEN:2375,bind=127.0.0.1,reuseaddr,fork",
                            "UNIX-CLIENT:${url.path}"
                        )
                    )
                    "http://localhost:2375/${config.docker.version}"
                }
                "tcp" -> {
                    "http://${url.host}:${url.port}/${config.docker.version}"
                }
                else -> throw MCSManInitializeException("unsupported docker daemon socket: $url")
            }
        )
    }

    private suspend fun initializeNetwork(): Network {
        val networks = docker.networks.list {
            label(Labels.name, config.network)
            label(Labels.type, Labels.Types.network)
            label(Labels.network, config.network)
        }
        return when (networks.size) {
            0 -> {
                log.verbose { "network \"${config.network}\" not found, creating new..." }
                docker.networks.create(config.network) {
                    attachable = true
                    driver = "bridge"
                    labels[Labels.name] = config.network
                    labels[Labels.type] = Labels.Types.network
                    labels[Labels.network] = config.network
                }
            }
            1 -> {
                log.verbose { "network \"${config.network}\" found" }
                networks.first()
            }
            else -> log.fatal { "multiple docker networks with name \"${config.network}\" exists" }
        }
    }

    private suspend fun initializeMCSManContainer(network: Network): Container {
        val containers = docker.containers.list {
            label(Labels.type, Labels.Types.mcsman)
            for ((key, value) in config.docker.labels)
                label(key, value)
        }
        when (containers.size) {
            0 -> log.fatal {
                "unable to find MCSMan itself." +
                        " Does it have \"${Labels.type}=${Labels.Types.mcsman}\" label?" +
                        " Also MCSMan must be inside the docker container"
            }
            1 -> {
                val mcsmanCon = containers.first()
                var hasNotMCSNet = true
                for (net in mcsmanCon.inspect().networkSettings.networks.values) {
                    if (net.networkId == network.id)
                        hasNotMCSNet = false
                    else
                        docker.networks.disconnect(net.networkId, mcsmanCon, force = true)
                }
                if (hasNotMCSNet) {
                    network.connect(mcsmanCon, EndpointSettings(aliases = listOf("mcsman")))
                }
                return mcsmanCon
            }
            else -> log.fatal { "multiple MCSMan instances are not supported" }
        }
    }

    suspend inline fun prepareImage(name: String): Image = prepareImage(ImageName(name))

    suspend fun prepareImage(fullTag: ImageName): Image {
        val name = fullTag.toString()
        val image = docker.images.find(name)
        return image ?: run {
            var message: Any? = null
            docker.images.pull(fullTag.repo, tag = fullTag.tag, auth = config.docker.registry).collect {
                message = it
            }
            log.info { message }
            docker.images.find(name)!!
        }
    }

    suspend fun prepareImage(image: Image): Image {
        var message: Any? = null
        image.pull(auth = config.docker.registry).collect {
            message = it
        }
        log.info { message }
        return image
    }

    private object UUIDGenerator {
        override fun toString() = UUID.randomUUID().toString()
    }

    private val dictionary = persistentMapOf(
        "network" to config.network,
        "domain" to config.domain,
        "uuid" to UUIDGenerator
    )

    private val volumeDictionary = dictionary.put("type", Labels.Types.volume)
    private val serverDictionary = dictionary.put("type", Labels.Types.server)
    private val serviceDictionary = dictionary.put("type", Labels.Types.service)

    fun dockerVolumeName(name: String, server: String) =
        translate(config.volume.namefmt, volumeDictionary.mutate {
            it["name"] = name
            it["server"] = server
        })

    fun dockerServerName(name: String) = translate(
        config.server.namefmt,
        serverDictionary.put("name", name)
    )

    fun dockerServiceName(name: String) = translate(
        config.service.namefmt,
        serviceDictionary.put("name", name)
    )

    suspend fun createServerVolume(name: String, server: String): Volume {
        val volumeName = dockerVolumeName(name, server)
        return docker.volumes.create(volumeName) {
            labels[Labels.name] = name
            labels[Labels.network] = config.network
            labels[Labels.type] = Labels.Types.volume
            labels[Labels.server] = server
            driver = config.volume.driver
            driverOpts += config.volume.options
        }
    }

    private suspend fun findContainers(labels: Map<String, String>): List<ContainersListResponse> {
        return docker.containers.listRaw(all = true) {
            labels.forEach { (key, value) -> label(key, value) }
        }
    }

    private suspend fun findContainer(labels: Map<String, String>): ContainersListResponse? {
        val existing = findContainers(labels)
        return when (existing.size) {
            0 -> null
            1 -> existing.first()
            else -> error("several docker containers with the exact same parameters: $labels")
        }
    }

    suspend fun findServerRaw(name: String): ContainersListResponse? = findContainer(
        mapOf(
            Labels.type to Labels.Types.server,
            Labels.network to config.network,
            Labels.name to name
        )
    )

    suspend inline fun findServer(name: String) = findServerRaw(name)?.let { docker.containers.wrap(it.id) }

    suspend fun findServiceRaw(name: String): ContainersListResponse? = findContainer(
        mapOf(
            Labels.type to Labels.Types.service,
            Labels.network to config.network,
            Labels.name to name
        )
    )

    suspend fun findService(name: String): Container? = findServiceRaw(name)?.let { docker.containers.wrap(it.id) }

    suspend fun findCompanions(server: String): List<String> {
        return findContainers(
            mapOf(
                Labels.type to Labels.Types.service,
                Labels.network to config.network,
                Labels.service to Labels.Services.companion,
                Labels.server to server
            )
        ).mapNotNull { it.labels[Labels.name] }
    }

    suspend fun findVolume(server: String, volume: String): Volume? {
        val existing = docker.volumes.list {
            label(Labels.name, volume)
            label(Labels.network, config.network)
            label(Labels.type, Labels.Types.volume)
            label(Labels.server, server)
        }
        return when (existing.size) {
            0 -> null
            1 -> existing.first()
            else -> error("several volumes for server \"$server\" named \"$volume\" were found")
        }
    }
}
