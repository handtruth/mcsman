package com.handtruth.mc.mcsman.server.util

import kotlinx.serialization.*
import java.net.URI

object URISerializer : KSerializer<URI> {
    override val descriptor =
        PrimitiveDescriptor("com.handtruth.mc.mcsman.util.URISerializer", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): URI = URI(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: URI) = encoder.encodeString(value.toString())
}
