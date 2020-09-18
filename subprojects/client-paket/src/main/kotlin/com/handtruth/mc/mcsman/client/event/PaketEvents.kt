package com.handtruth.mc.mcsman.client.event

import com.handtruth.kommon.setBean
import com.handtruth.mc.mcsman.client.PaketMCSManClient
import com.handtruth.mc.mcsman.client.bundle.Bundle
import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.mcsman.protocol.EventPaket
import com.handtruth.mc.mcsman.protocol.EventStreamPaket
import com.handtruth.mc.mcsman.util.forever
import com.handtruth.mc.paket.peek
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PaketEvents internal constructor(
    override val client: PaketMCSManClient
) : Events() {
    private val eventSubscribers = atomic(0)

    internal val eventsChannel = BroadcastChannel<Event>(1)

    init {
        client.launch {
            val eventPaket = EventStreamPaket(this@PaketEvents)
            forever {
                client.eventTs.receive(eventPaket)
                eventsChannel.send(eventPaket.event)
            }
        }
    }

    override val bus: Flow<Event> = flow {
        val channel = eventsChannel.openSubscription()
        if (eventSubscribers.getAndIncrement() == 0)
            client.request(EventPaket.ListenRequest)
        try {
            forever {
                val event = channel.receive()
                emit(event)
            }
        } finally {
            channel.cancel()
            if (eventSubscribers.decrementAndGet() == 0)
                withContext(NonCancellable) {
                    client.request(EventPaket.MuteRequest)
                }
        }
    }

    override suspend fun raise(event: Event) {
        client.request(EventStreamPaket(this, event))
    }

    override suspend fun fetch() {
        val listPaket = client.request(EventPaket.ListRequest) { peek(EventPaket.ListResponse) }
        listPaket.names.map { name ->
            val paket = client.request(EventPaket.GetRequest(name)) { peek(EventPaket.GetResponse) }
            val info = simple(paket.className, paket.implements, paket.eventTypes)
            info.setBean<Bundle>(client.bundles.get(paket.bundle))
        }
    }
}
