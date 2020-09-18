package com.handtruth.mc.mcsman.server.service

import com.handtruth.docker.container.Container
import com.handtruth.docker.model.EndpointSettings
import com.handtruth.docker.model.Mount
import com.handtruth.docker.model.PortTypes
import com.handtruth.docker.model.RestartPolicy
import com.handtruth.kommon.concurrent.Later
import com.handtruth.kommon.concurrent.later
import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.event.ServiceLifeEvent
import com.handtruth.mc.mcsman.event.Transitions
import com.handtruth.mc.mcsman.server.Configuration
import com.handtruth.mc.mcsman.server.ReactorContext
import com.handtruth.mc.mcsman.server.docker.Ext
import com.handtruth.mc.mcsman.server.docker.Labels
import com.handtruth.mc.mcsman.server.server.Server
import com.handtruth.mc.mcsman.server.server.Servers
import com.handtruth.mc.mcsman.server.util.DockerUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.yield
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import org.koin.core.KoinComponent
import org.koin.core.get

private const val companionNameState = "com.handtruth.mc.mcsman.service.companion"
private const val imageState = "com.handtruth.mc.mcsman.service.image"
private const val extState = "com.handtruth.mc.mcsman.service.ext"

sealed class DockerService(val image: ImageName, val extensions: List<Ext>) : Service() {

    private val dockerUtils: DockerUtils = get()
    private inline val docker get() = dockerUtils.docker
    private val config: Configuration = get()
    private val servers: Servers = get()

    private fun castPort(proto: Ext.Port.Protocols): PortTypes = when (proto) {
        Ext.Port.Protocols.TCP -> PortTypes.TCP
        Ext.Port.Protocols.UDP -> PortTypes.UDP
        Ext.Port.Protocols.SCTP -> PortTypes.SCTP
    }

    internal suspend fun createOrGetServiceContainer(
        server: String?,
        type: String
    ): Container {
        val imageName = image
        return dockerUtils.findService(name) ?: docker.containers.create(dockerUtils.dockerServiceName(name)) {
            log.info { "creating service Docker container" }

            val image = dockerUtils.prepareImage(imageName)

            image(image)

            label(Labels.type, Labels.Types.service)
            label(Labels.name, name)
            label(Labels.network, config.network)
            label(Labels.service, type)
            if (server != null)
                label(Labels.server, server)

            hostConfig {
                volumeDriver = config.volume.driver
                storageOpt += config.volume.options
                restartPolicy = RestartPolicy.UnlessStopped
                domainname = config.domain
                hostname = name

                for (ext in extensions) {
                    when (ext) {
                        is Ext.Cmd -> cmd = ext.command
                        is Ext.Env -> env(ext.key, ext.value)
                        is Ext.Port -> when (ext) {
                            is Ext.Port.Internal -> expose(ext.internal, castPort(ext.type))
                            is Ext.Port.External -> bind(
                                ext.internal, ext.external,
                                castPort(ext.type)
                            )
                        }
                        is Ext.Volume -> when (ext) {
                            is Ext.Volume.Server -> {
                                val volume = servers.volumes.get(ext.volume)
                                check(volume.server.get().name == server) {
                                    "failed to attach volume to unrelated service"
                                }
                                val dockerVolume = dockerUtils.findVolume(volume.server.get().name, volume.name)!!
                                val mount = Mount(
                                    ext.path.toString(), dockerVolume,
                                    ext.accessType == Ext.Volume.AccessTypes.RO
                                )
                                mount(mount)
                            }
                        }
                    }
                }
            }

            networkingConfig {
                connect(config.network, EndpointSettings(aliases = listOf(name)))
            }
        }
    }

    internal abstract suspend fun getContainer(): Container

    private val container = async { yield(); getContainer() }

    @ReactorContext
    override suspend fun onCreate() {
        container.await()
    }

    override suspend fun onStart() {
        container.await().start()
    }

    override suspend fun onStop() {
        container.await().stop()
    }

    override suspend fun onPause() {
        container.await().pause()
    }

    override suspend fun onResume() {
        container.await().unpause()
    }

    override suspend fun onKill() {
        container.await().kill()
    }

    override suspend fun getStatus(): ExecutableStatus {
        val status = container.await().inspect().state
        return when {
            status.paused -> ExecutableStatus.Paused
            status.running -> ExecutableStatus.Running
            else -> ExecutableStatus.Stopped
        }
    }

    @ReactorContext
    override suspend fun onRemove() {
        container.await().remove(force = true, v = true)
        log.info { "service container removed" }
    }

    private val connection = async {
        controller.attachments.attach(
            container.await().id, getStatus() != ExecutableStatus.Stopped,
            ServiceLifeEvent(Transitions.Starting, name, true),
            ServiceLifeEvent(Transitions.Starting, name, false),
            send, input, output, errors
        )
    }

    override suspend fun onUnload() {
        connection.await().close()
    }

    @MCSManService
    open class Global(
        @MCSManService.State(imageState)
        image: ImageName,
        @MCSManService.State(extState)
        extensions: List<Ext>
    ) : DockerService(image, extensions) {
        constructor(image: ImageName, vararg extensions: Ext) : this(image, extensions.asList())

        final override suspend fun getContainer() = createOrGetServiceContainer(null, Labels.Services.global)

        companion object Factory : KoinComponent {
            suspend fun create(name: String, image: ImageName, extensions: List<Ext>): Global =
                get<Services>().create(name) {
                    save(imageState, ImageName.serializer(), image)
                    save(extState, ListSerializer(Ext.serializer()), extensions)
                }

            suspend inline fun create(name: String, image: ImageName, vararg extensions: Ext) =
                create(name, image, extensions.toList())
        }
    }

    @MCSManService
    open class Companion(
        @MCSManService.State(companionNameState)
        val serverName: String,
        @MCSManService.State(imageState)
        image: ImageName,
        @MCSManService.State(extState)
        extensions: List<Ext>
    ) : DockerService(image, extensions) {
        constructor(serverName: String, image: ImageName, vararg extensions: Ext) : this(
            serverName, image, extensions.asList()
        )

        final override suspend fun getContainer() = createOrGetServiceContainer(serverName, Labels.Services.companion)

        val server: Later<Server> = later { get<Servers>().get(serverName) }

        companion object Factory : KoinComponent {
            suspend fun create(name: String, server: Server, image: ImageName, extensions: List<Ext>): Companion =
                get<Services>().create(name) {
                    save(companionNameState, String.serializer(), server.name)
                    save(imageState, ImageName.serializer(), image)
                    save(extState, ListSerializer(Ext.serializer()), extensions)
                }

            suspend inline fun create(
                name: String,
                server: Server,
                image: ImageName,
                vararg extensions: Ext
            ): Companion = create(name, server, image, extensions.toList())
        }
    }
}
