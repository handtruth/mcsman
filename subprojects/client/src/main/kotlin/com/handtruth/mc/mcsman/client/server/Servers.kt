package com.handtruth.mc.mcsman.client.server

import com.handtruth.mc.chat.ChatMessage
import com.handtruth.mc.mcsman.client.util.Controller
import com.handtruth.mc.mcsman.client.util.NamedController
import com.handtruth.mc.mcsman.common.model.ImageName

interface Servers : NamedController {
    override fun get(id: Int): Server
    override suspend fun get(name: String): Server

    val volumes: Volumes

    override suspend fun list(): List<Server>

    suspend fun create(name: String, image: ImageName, description: ChatMessage? = null): Server

    interface Volumes : Controller {
        override fun get(id: Int): Volume
        suspend fun get(server: Server, name: String): Volume

        override suspend fun list(): List<Volume>
    }
}
