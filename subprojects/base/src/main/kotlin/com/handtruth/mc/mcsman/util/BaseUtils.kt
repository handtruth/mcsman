package com.handtruth.mc.mcsman.util

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

inline fun forever(block: () -> Unit): Nothing {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    while (true)
        block()
}
