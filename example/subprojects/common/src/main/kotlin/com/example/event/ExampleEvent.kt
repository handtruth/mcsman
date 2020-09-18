package com.example.event

import com.handtruth.mc.mcsman.common.event.MCSManEvent
import com.handtruth.mc.mcsman.event.ActorEvent
import com.handtruth.mc.mcsman.event.ModuleEvent

@MCSManEvent("example")
data class ExampleEvent(
    override val actor: String,
    override val module: String,
    override val success: Boolean = true
) : ActorEvent, ModuleEvent
