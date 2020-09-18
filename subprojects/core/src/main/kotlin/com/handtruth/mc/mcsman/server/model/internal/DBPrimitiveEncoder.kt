package com.handtruth.mc.mcsman.server.model.internal

import kotlinx.serialization.*
import kotlinx.serialization.modules.SerialModule

internal class DBPrimitiveEncoder(override val context: SerialModule) : Encoder {
    lateinit var kind: SerialKind
        private set
    lateinit var value: Any
        private set

    override fun beginStructure(descriptor: SerialDescriptor, vararg typeSerializers: KSerializer<*>): Nothing =
            throw UnsupportedOperationException("Should be primitive")

    override fun encodeBoolean(value: Boolean) {
        this.value = value
        kind = PrimitiveKind.BOOLEAN
    }

    override fun encodeByte(value: Byte) {
        this.value = value
        kind = PrimitiveKind.BYTE
    }

    override fun encodeChar(value: Char) {
        this.value = value
        kind = PrimitiveKind.CHAR
    }

    override fun encodeDouble(value: Double) {
        this.value = value
        kind = PrimitiveKind.DOUBLE
    }

    override fun encodeEnum(enumDescriptor: SerialDescriptor, index: Int) {
        this.value = enumDescriptor.getElementName(index)
        kind = UnionKind.ENUM_KIND
    }

    override fun encodeFloat(value: Float) {
        this.value = value
        kind = PrimitiveKind.FLOAT
    }

    override fun encodeInt(value: Int) {
        this.value = value
        kind = PrimitiveKind.INT
    }

    override fun encodeLong(value: Long) {
        this.value = value
        kind = PrimitiveKind.LONG
    }

    override fun encodeNull() {
        this.value = value
        kind = StructureKind.OBJECT
    }

    override fun encodeShort(value: Short) {
        this.value = value
        kind = PrimitiveKind.SHORT
    }

    override fun encodeString(value: String) {
        this.value = value
        kind = PrimitiveKind.STRING
    }

    override fun encodeUnit() {
        this.value = Unit
        kind = StructureKind.OBJECT
    }
}
