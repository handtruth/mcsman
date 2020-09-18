package com.handtruth.mc.mcsman.common.model

import kotlinx.serialization.*

@Serializable(ImageName.Serializer::class)
data class ImageName(val repo: String, val tag: String) {
    companion object {
        operator fun invoke(name: String): ImageName {
            val index = name.lastIndexOf(':')
            return if (index == -1)
                ImageName(name, "latest")
            else
                ImageName(
                    name.substring(0, index),
                    name.substring(index + 1)
                )
        }
    }

    object Serializer : KSerializer<ImageName> {
        override val descriptor = PrimitiveDescriptor("ImageName", PrimitiveKind.STRING)
        override fun deserialize(decoder: Decoder) =
            ImageName(decoder.decodeString())
        override fun serialize(encoder: Encoder, value: ImageName) = encoder.encodeString(value.toString())
    }

    override fun toString() = "$repo:$tag"
}
