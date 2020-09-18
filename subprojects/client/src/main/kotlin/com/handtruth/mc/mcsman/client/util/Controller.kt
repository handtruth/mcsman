package com.handtruth.mc.mcsman.client.util

import com.handtruth.mc.mcsman.client.MCSManClient

interface Controller {
    val client: MCSManClient

    fun get(id: Int): Entity
    suspend fun list(): List<Entity>
}

interface NamedController : Controller {
    override fun get(id: Int): NamedEntity
    suspend fun get(name: String): NamedEntity

    override suspend fun list(): List<NamedEntity>
}
