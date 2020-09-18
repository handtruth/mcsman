package com.handtruth.mc.mcsman.server.module

import kotlinx.serialization.Serializable

@Serializable
data class MinecraftConfig(
    override val enable: Boolean = true
) : ModuleConfig