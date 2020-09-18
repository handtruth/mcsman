package com.handtruth.mc.mcsman.client.event

import com.handtruth.kommon.getBeanOrNull
import com.handtruth.mc.mcsman.client.MCSManClient
import com.handtruth.mc.mcsman.client.bundle.Bundle
import com.handtruth.mc.mcsman.common.event.EventInfo
import com.handtruth.mc.mcsman.common.event.EventsBase

val EventInfo.bundle: Bundle?
    get() = getBeanOrNull()

abstract class Events : EventsBase() {
    abstract val client: MCSManClient
    abstract suspend fun fetch()
}
