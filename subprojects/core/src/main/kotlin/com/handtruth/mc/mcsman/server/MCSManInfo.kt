package com.handtruth.mc.mcsman.server

interface MCSManInfo {
    val container: String
}

internal data class MCSManInfoImpl(
    override val container: String
) : MCSManInfo
