package com.handtruth.mc.mcsman.server.module

import com.handtruth.mc.mcsman.server.service.MCSHub
import com.handtruth.mc.mcsman.server.service.Services
import kotlinx.coroutines.launch
import org.koin.core.inject

@MCSManModule
@MCSManModule.Artifact("maven", "client", "jvm", "mvn:com.handtruth.mc/mcsman-module-minecraft-client-paket")
object Minecraft : ConfigurableModule<MinecraftConfig>(configDeserializer = MinecraftConfig.serializer()) {

    private val services: Services by inject()
    private val modules: Modules by inject()

    override fun initialize() {
        val config = config

        log.info { "MCSMan core module is here ${MCSMan.name}#${MCSMan.id}" }

        launch {
            try {
                if (config.enable) {
                    log.info { "creating MCSHub..." }
                    val service: MCSHub = services.getOrNull("mcshub") as MCSHub? ?: services.create("mcshub")
                    log.info { "MCSHub created" }
                } else {
                    log.info { "removing MCSHub..." }
                    (services.getOrNull("mcshub") as MCSHub?)?.remove()
                    log.info { "MCSHub removed" }
                }
            } catch (e: Throwable) {
                log.error(e)
            }
        }
    }
}
