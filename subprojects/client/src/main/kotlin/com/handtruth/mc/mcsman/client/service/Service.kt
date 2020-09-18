package com.handtruth.mc.mcsman.client.service

import com.handtruth.mc.mcsman.client.actor.Actor
import com.handtruth.mc.mcsman.client.bundle.Bundle
import com.handtruth.mc.mcsman.client.util.NamedEntityInfo
import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.mcsman.util.Executable

interface Service : Actor, Executable {
    override val controller: Services

    override suspend fun inspect(): ServiceInfo
}

data class ServiceInfo(
    override val id: Int,
    override val name: String,
    val bundle: Bundle,
    val status: ExecutableStatus,
    val factory: String
) : NamedEntityInfo
