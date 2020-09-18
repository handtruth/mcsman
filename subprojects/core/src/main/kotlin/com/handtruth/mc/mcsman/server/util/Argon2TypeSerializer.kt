package com.handtruth.mc.mcsman.server.util

import com.handtruth.mc.mcsman.server.ConfigMCSManException
import com.kosprov.jargon2.api.Jargon2
import kotlinx.serialization.*

object Argon2TypeSerializer : KSerializer<Jargon2.Type> {
    override val descriptor = PrimitiveDescriptor("Argon2Type", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Jargon2.Type {
        val value = decoder.decodeString()
        if (!value.startsWith("argon2", ignoreCase = true))
            throw ConfigMCSManException("not an argon hash algorithm")
        return when {
            value.regionMatches(6, "i", 0, 1, ignoreCase = true) ->
                Jargon2.Type.ARGON2i
            value.regionMatches(6, "d", 0, 1, ignoreCase = true) ->
                Jargon2.Type.ARGON2d
            value.regionMatches(6, "id", 0, 2, ignoreCase = true) ->
                Jargon2.Type.ARGON2id
            else -> throw ConfigMCSManException("unknown argon hash algorithm")
        }
    }

    override fun serialize(encoder: Encoder, value: Jargon2.Type) = encoder.encodeString(value.value)
}
