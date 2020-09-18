package com.handtruth.mc.mcsman.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ExecutableActions {
    @SerialName("start")
    Start,
    @SerialName("stop")
    Stop,
    @SerialName("pause")
    Pause,
    @SerialName("resume")
    Resume,
    @SerialName("kill")
    Kill
}
