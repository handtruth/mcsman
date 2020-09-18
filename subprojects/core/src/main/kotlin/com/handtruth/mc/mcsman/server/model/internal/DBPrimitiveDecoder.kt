package com.handtruth.mc.mcsman.server.model.internal

import kotlinx.serialization.CompositeDecoder
import kotlinx.serialization.Decoder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialDescriptor

internal class DBPrimitiveDecoder(private val composite: CompositeDecoder) : Decoder {
    var value: Any? = null

    override val context get() = composite.context
    override val updateMode get() = composite.updateMode

    override fun beginStructure(descriptor: SerialDescriptor, vararg typeParams: KSerializer<*>) =
            throw UnsupportedOperationException()
    override fun decodeBoolean() = value as Boolean
    override fun decodeByte() = (value as Short).toByte()
    override fun decodeChar() = value as Char
    override fun decodeDouble() = value as Double
    override fun decodeEnum(enumDescriptor: SerialDescriptor) = enumDescriptor.getElementIndex(value as String)
    override fun decodeFloat() = value as Float
    override fun decodeInt() = value as Int
    override fun decodeLong() = value as Long
    override fun decodeNotNullMark() = value as Boolean
    override fun decodeNull() = value as Nothing?
    override fun decodeShort() = value as Short
    override fun decodeString() = value as String
    override fun decodeUnit() = Unit
}
