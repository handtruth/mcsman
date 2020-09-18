package com.handtruth.mc.mcsman.client.event

import com.handtruth.mc.mcsman.common.event.EventInfo
import com.handtruth.mc.mcsman.common.event.EventsBase
import com.handtruth.mc.mcsman.common.event.UnknownEvent
import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.mcsman.protocol.kbtPaket
import com.handtruth.mc.nbt.tags.ByteTag
import com.handtruth.mc.nbt.tags.CompoundTag
import kotlin.reflect.KClass

class PaketUnknownEvent internal constructor(
    private val events: EventsBase, className: String, val data: CompoundTag
) : UnknownEvent(className) {
    override val success get() = (data.value["success"] as ByteTag).byte != 0.toByte()

    override fun <E : Event> instanceOf(`class`: KClass<out E>): Boolean {
        return events.describe(className).interfaces.any { it is EventInfo.Full && it.`class` == `class` }
    }

    override fun <E : Event> treatAs(`class`: KClass<out E>): E {
        val typeInfo = events.describe(`class`).interfaces
            .find { it is EventInfo.Full && it.`class` == `class` } as EventInfo.Full?
            ?: error("$className not an instance of $`class`")
        @Suppress("UNCHECKED_CAST")
        return kbtPaket.fromNBT(typeInfo.serializer, data) as E
    }
}
