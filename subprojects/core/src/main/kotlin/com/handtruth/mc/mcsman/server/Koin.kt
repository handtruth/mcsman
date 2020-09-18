package com.handtruth.mc.mcsman.server

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.handtruth.docker.DockerClient
import com.handtruth.docker.container.DockerContainers
import com.handtruth.docker.image.DockerImages
import com.handtruth.docker.network.DockerNetworks
import com.handtruth.docker.volume.DockerVolumes
import com.handtruth.kommon.Log
import com.handtruth.kommon.LogFactory
import com.handtruth.kommon.LogLevel
import com.handtruth.kommon.default
import com.handtruth.mc.mcsman.common.event.EventsBase
import com.handtruth.mc.mcsman.server.access.Accesses
import com.handtruth.mc.mcsman.server.access.Permissions
import com.handtruth.mc.mcsman.server.actor.Actors
import com.handtruth.mc.mcsman.server.bundle.Bundles
import com.handtruth.mc.mcsman.server.docker.Attachments
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.module.Modules
import com.handtruth.mc.mcsman.server.server.Servers
import com.handtruth.mc.mcsman.server.service.Services
import com.handtruth.mc.mcsman.server.session.Sessions
import com.handtruth.mc.mcsman.server.util.DockerUtils
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transactionManager
import org.koin.dsl.bind
import org.koin.dsl.module
import org.slf4j.LoggerFactory
import java.sql.Connection

internal val appModule = module {

    single { loadConfig() } bind Config::class

    single {
        LogFactory("mcsman", LogLevel.Debug)
    }

    single {
        val config = get<Configuration>().database
        HikariConfig().apply {
            jdbcUrl = "jdbc:${config.url}"
            driverClassName = config.driver
            username = config.user
            password = config.password
            if (config.url.startsWith("sqlite:"))
                maximumPoolSize = 1
        }
    }

    single {
        val config: HikariConfig = get()
        Database.connect(HikariDataSource(config)).apply {
            if (config.jdbcUrl.startsWith("jdbc:sqlite:"))
                transactionManager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
        }
    }

    factory { (tag: String) -> Log.default(tag) }

    single { Permissions.Server() }
    single { Permissions.Global() }

    single { Accesses() }
    single { get<Accesses>().global }
    single { get<Accesses>().server }
    single { get<Accesses>().image }
    single { get<Accesses>().volume }
    single { Actors() }
    single { get<Actors>().users }
    single { get<Actors>().groups }
    single { Services() }
    single { Modules() }
    single { Events() } bind EventsBase::class
    single { Servers() }
    single { get<Servers>().volumes }
    single { Sessions() }
    single { Synchronizer() }
    single { Attachments() }
    single { Bundles() }

    single { DockerUtils(get(), get(), get()) }
    single { get<DockerUtils>().docker }
    single<MCSManInfo> { MCSManInfoImpl(get<DockerUtils>().mcsman.id) }
    single { get<DockerClient>().containers }
    single { get<DockerClient>().images }
    single { get<DockerClient>().networks }
    single { get<DockerClient>().volumes }

    factory { (id: String) -> get<DockerContainers>().wrap(id) }
    factory { (id: String) -> get<DockerImages>().wrap(id) }
    factory { (id: String) -> get<DockerNetworks>().wrap(id) }
    factory { (id: String) -> get<DockerVolumes>().wrap(id) }

}

internal fun setLogLevel(lvl: LogLevel) {
    val level = when (lvl) {
        LogLevel.None -> Level.ERROR
        LogLevel.Fatal -> Level.ERROR
        LogLevel.Error -> Level.ERROR
        LogLevel.Warning -> Level.WARN
        LogLevel.Info -> Level.INFO
        LogLevel.Verbose -> {
            System.setProperty("kotlinx.coroutines.debug", "")
            Level.DEBUG
        }
        LogLevel.Debug -> Level.TRACE
    }
    val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    root.level = level
}
