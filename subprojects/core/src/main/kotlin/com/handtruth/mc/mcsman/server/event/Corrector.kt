package com.handtruth.mc.mcsman.server.event

import com.handtruth.mc.mcsman.event.Event

interface Corrector<E : Event> {
    suspend fun correct(event: E): E
}
