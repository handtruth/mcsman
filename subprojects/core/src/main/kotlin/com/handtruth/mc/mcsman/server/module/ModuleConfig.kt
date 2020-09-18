package com.handtruth.mc.mcsman.server.module

import kotlinx.serialization.Serializable

/**
 * Base interface for all module configurations.
 */
interface ModuleConfig {
    val enable: Boolean
}

/**
 * Standard realization of ModuleConfig for modules without configurations
 */
@Serializable
data class SimpleModuleConfig(override val enable: Boolean = false) : ModuleConfig
