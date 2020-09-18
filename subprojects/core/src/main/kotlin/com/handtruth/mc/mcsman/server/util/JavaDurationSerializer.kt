package com.handtruth.mc.mcsman.server.util

import kotlinx.serialization.*
import java.time.Duration

object JavaDurationSerializer : KSerializer<Duration> {
    override val descriptor = PrimitiveDescriptor("java.time.Duration", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder) = Duration.parse(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: Duration) = encoder.encodeString(value.toString())
}
