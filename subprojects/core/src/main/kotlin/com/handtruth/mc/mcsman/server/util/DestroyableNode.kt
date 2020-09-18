package com.handtruth.mc.mcsman.server.util

import com.handtruth.mc.mcsman.MCSManException
import kotlinx.atomicfu.atomic
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class DestroyableNode {
    private val state = atomic(false)

    val isDestroyed get() = state.value
    @PublishedApi
    internal fun destroy() {
        state.value = false
    }
}

class ObjectDestroyedMCSManException : MCSManException("attempt to use the destroyed object")

inline fun <R> DestroyableNode.invoke(block: () -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    if (isDestroyed)
        throw ObjectDestroyedMCSManException()
    return block()
}
