package com.handtruth.mc.mcsman.server.util

import com.handtruth.kommon.LogLevel
import kotlinx.serialization.*

object LogLevelSerializer : KSerializer<LogLevel> {
    override val descriptor = PrimitiveDescriptor("log_level", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LogLevel = LogLevel.getByName(decoder.decodeString())!!

    override fun serialize(encoder: Encoder, value: LogLevel) = encoder.encodeString(value.actualName)
}
