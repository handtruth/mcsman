package com.handtruth.mc.mcsman.client.service

import com.handtruth.mc.mcsman.client.util.NamedController

interface Services : NamedController {
    override fun get(id: Int): Service
    override suspend fun get(name: String): Service

    override suspend fun list(): List<Service>
}
