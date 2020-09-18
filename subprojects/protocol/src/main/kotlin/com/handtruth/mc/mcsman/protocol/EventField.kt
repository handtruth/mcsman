package com.handtruth.mc.mcsman.protocol

import com.handtruth.mc.mcsman.client.event.PaketUnknownEvent
import com.handtruth.mc.mcsman.common.event.EventInfo
import com.handtruth.mc.mcsman.common.event.EventsBase
import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.nbt.NBTBinaryCodec
import com.handtruth.mc.nbt.NBTBinaryConfig
import com.handtruth.mc.nbt.NBTSerialFormat
import com.handtruth.mc.nbt.plus
import com.handtruth.mc.paket.Codec
import com.handtruth.mc.paket.Field
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.fields.ByteArrayCodec
import com.handtruth.mc.paket.fields.BytesCodec
import com.handtruth.mc.paket.fields.StringCodec
import com.handtruth.mc.paket.read
import kotlinx.io.Input
import kotlinx.io.Output
import kotlinx.serialization.SerializationStrategy

internal val kbtPaket = NBTBinaryCodec(NBTBinaryConfig.KBT) + NBTSerialFormat()

class EventCodec(val events: EventsBase) : Codec<Event> {
    override fun measure(value: Event): Int {
        val info = events.describe(value)
        @Suppress("UNCHECKED_CAST")
        val bytes = kbtPaket.dump(info.serializer as SerializationStrategy<Event>, value)
        return StringCodec.measure(events.describe(value).name) + ByteArrayCodec.measure(bytes)
    }

    override fun read(input: Input, old: Event?): Event {
        val name = StringCodec.read(input)
        val bytes = BytesCodec.read(input)
        val tag = kbtPaket.read(bytes.input())
        val info = events.describeOrNull(name)
        if (info == null || info !is EventInfo.Full)
            return PaketUnknownEvent(events, name, tag)
        return kbtPaket.fromNBT(info.serializer, tag)
    }

    override fun write(output: Output, value: Event) {
        val info = events.describe(value)
        StringCodec.write(output, info.name)
        @Suppress("UNCHECKED_CAST")
        val bytes = kbtPaket.dump(info.serializer as SerializationStrategy<Event>, value)
        ByteArrayCodec.write(output, bytes)
    }
}

class EventField(events: EventsBase, initial: Event) : Field<Event>(EventCodec(events), initial)

fun Paket.event(events: EventsBase, initial: Event) = field(EventField(events, initial))
