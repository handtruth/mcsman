package com.handtruth.mc.mcsman.server.util

import kotlinx.serialization.*
import java.io.File

object FileSerializer : KSerializer<File> {
    override val descriptor = PrimitiveDescriptor("File", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder) = File(decoder.decodeString())
    override fun serialize(encoder: Encoder, value: File) = encoder.encodeString(value.path)
}
