package com.handtruth.mc.mcsman.server.event

import com.handtruth.mc.mcsman.event.Event

interface Reactor<in E : Event> {
    suspend fun react(event: E)
    val type: Types get() = Types.Unknown

    enum class Types {
        Unknown, Read, Write
    }
}
