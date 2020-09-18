package com.handtruth.mc.mcsman.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExecutableStatus {
    @SerialName("stopped")
    Stopped,
    @SerialName("running")
    Running,
    @SerialName("paused")
    Paused
}
