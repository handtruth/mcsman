package com.handtruth.mc.mcsman.protocol

interface TypedPaket<E : Enum<E>> {
    val type: E
}
