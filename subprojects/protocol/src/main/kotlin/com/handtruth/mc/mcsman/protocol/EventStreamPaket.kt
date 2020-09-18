package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.mcsman.common.event.EventsBase
import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.mcsman.event.EventProjection
import com.handtruth.mc.paket.Paket

class EventStreamPaket(events: EventsBase, event: Event = EventProjection()) : Paket() {
    override val id = PaketID.EventStream

    var event by event(events, event)
}
