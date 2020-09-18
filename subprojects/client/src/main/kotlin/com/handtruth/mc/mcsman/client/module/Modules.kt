package com.handtruth.mc.mcsman.client.module

import com.handtruth.mc.mcsman.client.util.NamedController

interface Modules : NamedController {
    override fun get(id: Int): Module
    override suspend fun get(name: String): Module

    override suspend fun list(): List<Module>
}
