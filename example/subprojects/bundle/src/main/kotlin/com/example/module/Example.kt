package com.example.module

import com.example.event.ExampleEvent
import com.handtruth.mc.mcsman.common.event.MCSManEvent
import com.handtruth.mc.mcsman.server.module.MCSManModule
import com.handtruth.mc.mcsman.server.module.Module

@MCSManModule
@MCSManEvent.Table(ExampleEvent::class)
@MCSManEvent.Register(ExampleEvent::class)
object Example : Module()
