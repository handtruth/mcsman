package com.handtruth.mc.mcsman.util

import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import kotlinx.coroutines.channels.ReceiveChannel
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

interface Executable {
    suspend fun start()
    suspend fun stop()
    suspend fun kill()
    suspend fun pause()
    suspend fun resume()

    suspend fun status(): ExecutableStatus

    suspend fun send2input(line: String)
    suspend fun subscribeInput(): ReceiveChannel<String>
    suspend fun subscribeOutput():  ReceiveChannel<String>
    suspend fun subscribeErrors(): ReceiveChannel<String>
}

suspend inline fun <R> Executable.withOutput(block: (ReceiveChannel<String>) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val channel = subscribeOutput()
    try {
        return block(channel)
    } finally {
        channel.cancel()
    }
}

suspend inline fun <R> Executable.withErrors(block: (ReceiveChannel<String>) -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val channel = subscribeErrors()
    try {
        return block(channel)
    } finally {
        channel.cancel()
    }
}

suspend inline fun Executable.listenOutput(block: (String) -> Unit): Nothing {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    withOutput {
        forever { block(it.receive()) }
    }
}

suspend inline fun Executable.listenErrors(block: (String) -> Unit): Nothing {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    withErrors {
        forever { block(it.receive()) }
    }
}
