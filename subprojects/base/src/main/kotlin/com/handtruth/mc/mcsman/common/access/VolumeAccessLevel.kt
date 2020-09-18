package com.handtruth.mc.mcsman.common.access

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class VolumeAccessLevel {
    @SerialName("read")
    Read,
    @SerialName("write")
    Write,
    @SerialName("owner")
    Owner;

    override fun toString(): String {
        return super.toString().toLowerCase()
    }
}
