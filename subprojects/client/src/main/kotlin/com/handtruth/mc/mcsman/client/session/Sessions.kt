package com.handtruth.mc.mcsman.client.session

import com.handtruth.kommon.concurrent.Later
import com.handtruth.mc.mcsman.client.util.Controller

interface Sessions : Controller {
    override fun get(id: Int): Session
    val current: Later<Session>

    override suspend fun list(): List<Session>
}
