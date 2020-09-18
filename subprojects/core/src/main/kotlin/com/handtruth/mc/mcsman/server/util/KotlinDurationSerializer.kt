package com.handtruth.mc.mcsman.server.util

import kotlinx.serialization.*
import kotlin.time.*

object KotlinDurationSerializer : KSerializer<Duration> {
    private val pattern = Regex("""\d+[yMwdhms]""")

    override val descriptor = PrimitiveDescriptor("Duration", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Duration {
        val string = decoder.decodeString()
        var duration = Duration.ZERO
        pattern.findAll(string).forEach {
            val value = string.substring(it.range.first, it.range.last - 1).toInt()
            duration += when (string[it.range.last]) {
                'y' -> (value * 365).days
                'M' -> (value * 30).days
                'w' -> (value * 7).days
                'd' -> value.days
                'h' -> value.hours
                'm' -> value.minutes
                's' -> value.seconds
                else -> return@forEach
            }
        }
        return duration
    }

    override fun serialize(encoder: Encoder, value: Duration) = throw UnsupportedOperationException()
}
