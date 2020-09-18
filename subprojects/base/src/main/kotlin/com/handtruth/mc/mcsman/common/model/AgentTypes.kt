package com.handtruth.mc.mcsman.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AgentTypes {
    @SerialName("user")
    User,
    @SerialName("group")
    Group,
    @SerialName("service")
    Service
}
