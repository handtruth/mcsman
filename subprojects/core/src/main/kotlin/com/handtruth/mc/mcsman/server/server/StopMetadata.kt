package com.handtruth.mc.mcsman.server.server

import com.handtruth.docker.DockerClient
import com.handtruth.kommon.getLog
import com.handtruth.mc.mcsman.server.docker.Labels
import com.handtruth.mc.mcsman.server.session.sudo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import org.koin.core.KoinComponent
import org.koin.core.inject

@Serializable
internal sealed class StopMetadata {

    abstract suspend operator fun invoke(server: Server)

    @Serializable
    @SerialName("signal")
    data class Signal(val signal: Signals) : StopMetadata() {
        enum class Signals {
            SIGHUP, SIGINT, SIGQUIT, SIGILL, SIGTRAP, SIGABRT, SIGBUS, SIGFPE, SIGKILL, SIGUSR1, SIGSEGV, SIGUSR2,
            SIGPIPE, SIGALRM, SIGTERM, SIGSTKFLT, SIGCHLD, SIGCONT, SIGSTOP, SIGTSTP, SIGTTIN, SIGTTOU, SIGURG,
            SIGXCPU, SIGXFSZ, SIGVTALRM, SIGPROF, SIGWINCH, SIGIO, SIGPWR, SIGSYS
        }

        override suspend operator fun invoke(server: Server) {
            docker.containers.kill(server.info.await().id, signal.name)
        }
    }

    @Serializable
    @SerialName("command")
    data class Command(val command: String) : StopMetadata() {
        override suspend fun invoke(server: Server) {
            sudo {
                server.internalInput.send(command)
            }
        }
    }

    @Serializable
    @SerialName("default")
    object Default : StopMetadata() {
        override suspend fun invoke(server: Server) {
            docker.containers.stop(server.info.await().id)
        }
    }

    companion object : KoinComponent {
        private val json = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true))

        private val docker: DockerClient by inject()

        suspend fun extract(container: String) = run {
                val data = docker.containers.inspect(container).config.labels[Labels.Meta.stop]
                    ?: return Default
                try {
                    json.parse(serializer(), data)
                } catch (e: Exception) {
                    getLog().warning(e) { "failed to parse stop metadata: $data" }
                    Default
                }
            }
    }

}
