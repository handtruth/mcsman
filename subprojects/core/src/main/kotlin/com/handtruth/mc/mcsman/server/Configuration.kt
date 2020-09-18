@file:UseSerializers(URISerializer::class, LogLevelSerializer::class)

package com.handtruth.mc.mcsman.server

import com.charleskorn.kaml.Yaml
import com.handtruth.docker.model.RegistryAuth
import com.handtruth.kommon.Log
import com.handtruth.kommon.LogLevel
import com.handtruth.kommon.default
import com.handtruth.mc.mcsman.MCSManException
import com.handtruth.mc.mcsman.common.model.ImageName
import com.handtruth.mc.mcsman.server.module.ModuleConfig
import com.handtruth.mc.mcsman.server.util.LogLevelSerializer
import com.handtruth.mc.mcsman.server.util.ModulesSerializer
import com.handtruth.mc.mcsman.server.util.URISerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import kotlin.system.exitProcess

private fun guessDBDriver(url: String): String = when {
    url.startsWith("postgresql:") -> "org.postgresql.Driver"
    url.startsWith("mysql:") -> "com.mysql.jdbc.Driver"
    url.startsWith("jdbc:oracle:") -> "oracle.jdbc.OracleDriver"
    url.startsWith("sqlite:") -> "org.sqlite.JDBC"
    url.startsWith("h2:") -> "org.h2.Driver"
    url.startsWith("sqlserver:") -> "com.microsoft.sqlserver.jdbc.SQLServerDriver"
    else -> throw ConfigMCSManException("Failed to guess JDBC driver from database URL")
}

interface Config {
    val domain: String
    val network: String
    val server: ServerConf
    val port: Int

    interface ServerConf {
        val image: ImageName
        val namefmt: String
        val maxRestartCount: Int
    }
}

@Serializable
internal data class Configuration(
    override val domain: String,
    override val port: Int = 1337,
    val verb: LogLevel = LogLevel.Info,
    override val server: ServerConf = ServerConf(),
    override val network: String = "mcsnet",
    @Serializable(ModulesSerializer::class)
    val module: Map<String, ModuleConfig> = yaml.parse(ModulesSerializer, "{}"),
    val docker: DockerConf = DockerConf(),
    val volume: VolumeConf = VolumeConf(),
    val service: ServiceConf = ServiceConf(),
    val database: DatabaseConf = DatabaseConf()
) : Config {
    @Serializable
    data class ServerConf(
        override val image: ImageName = ImageName("handtruth/mcscon", "latest"),
        val javaArgs: String = "-XX:+UnlockExperimentalVMOptions " +
                "-XX:+UseCGroupMemoryLimitForHeap " +
                "-Dfml.ignoreInvalidMinecraftCertificates=true",
        override val namefmt: String = "\$network-\$name-\$type",
        @SerialName("max_restart_count")
        override val maxRestartCount: Int = 3
    ) : Config.ServerConf

    @Serializable
    data class DockerConf(
        val version: String = "v1.40",
        val registry: RegistryAuth.Credentials = RegistryAuth.Credentials(),
        val url: URI = URI("unix:///var/run/docker.sock"),
        val labels: Map<String, String> = emptyMap()
    )

    @Serializable
    data class VolumeConf(
        val driver: String = "local",
        val namefmt: String = "\$network-\$server-\$name-\$type",
        val options: Map<String, String> = emptyMap()
    )

    @Serializable
    data class ServiceConf(
        val namefmt: String = "\$network-\$name-\$type"
    )

    @Serializable
    data class DatabaseConf(
        val url: String = "sqlite:mcsman.db",
        var driver: String = "",
        val user: String = "mcsman",
        val password: String = ""
    ) {
        init {
            driver = guessDBDriver(url)
        }
    }
}

class ConfigMCSManException(message: String) : MCSManException(message)

internal val yaml = Yaml()

const val filename = "mcsman.yml"

fun InputStream.pipe(stream: OutputStream, doClose: Boolean = true) {
    while (true) {
        val step = 1024
        val buffer = ByteArray(step)
        var received = 0
        while (received < step) {
            val r = read(buffer, received, step - received)
            if (r == -1) {
                stream.write(buffer, 0, received)
                if (doClose) {
                    close()
                    stream.close()
                }
                return
            }
            received += r
        }
        stream.write(buffer)
    }
}

internal fun loadConfig(): Configuration {
    val log: Log = Log.default("mcsman/config")
    try {
        val env = System.getenv("MCSMAN_CONFIG") ?: System.getProperty("MCSMAN_CONFIG")
        return if (!env.isNullOrBlank()) {
            log.info { "Loading configuration from MCSMAN_CONFIG environment variable" }
            yaml.parse(Configuration.serializer(), env)
        } else {
            log.info { "Loading configuration from configuration file" }
            val file = File("mcsman.yml")
            if (file.exists() && file.isFile) {
                yaml.parse(Configuration.serializer(), file.readText())
            } else {
                Configuration::class.java.getResourceAsStream(filename).pipe(file.outputStream())
                exitProcess(0)
            }
        }
    } catch (e: Exception) {
        log.fatal(e) { "error, while loading configuration" }
    }
}
