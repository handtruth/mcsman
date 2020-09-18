package com.handtruth.mc.mcsman.server.event

import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.mcsman.server.util.SuspendInvokable

typealias EventChecker = SuspendInvokable<Event, Boolean?>
