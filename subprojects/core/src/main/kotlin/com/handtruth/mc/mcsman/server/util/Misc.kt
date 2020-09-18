package com.handtruth.mc.mcsman.server.util

import com.handtruth.kommon.FatalError
import com.handtruth.kommon.Log
import com.handtruth.kommon.LogLevel
import com.handtruth.kommon.getLog
import com.handtruth.mc.mcsman.server.TransactionContext
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.parameter.parametersOf
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.system.exitProcess

fun KoinComponent.logger(name: String) = inject<Log> { parametersOf(name) }

suspend fun <R> suspendTransaction(db: Database, @OptIn(TransactionContext::class) block: Transaction.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return withContext(Dispatchers.IO) {
        org.jetbrains.exposed.sql.transactions.transaction(db, block)
    }
}

private const val onBagMessage = "FATAL BUG (not supposed to be here)"

interface Loggable {
    val log: Log
}

fun Loggable.unreachable(message: () -> Any?): Nothing {
    contract {
        callsInPlace(message, InvocationKind.EXACTLY_ONCE)
    }
    log.fatal { "$onBagMessage: ${message()}" }
}

fun unreachable(log: Log): Nothing {
    log.fatal { onBagMessage }
}

val Loggable.unreachable: Nothing get() = unreachable(log)

suspend inline fun unreachable(): Nothing = unreachable(getLog())

@PublishedApi
internal inline fun test(log: Log, value: Boolean, message: () -> Any?) {
    if (!value)
        log.fatal(message)
}

inline fun Loggable.testL(value: Boolean, message: () -> Any?) {
    contract {
        callsInPlace(message, InvocationKind.AT_MOST_ONCE)
        returns() implies value
    }
    test(log, value, message)
}

suspend inline fun testC(value: Boolean, message: () -> Any?) {
    contract {
        callsInPlace(message, InvocationKind.AT_MOST_ONCE)
        returns() implies value
    }
    test(getLog(), value, message)
}

inline fun CoroutineScope.testS(value: Boolean, message: () -> Any?) {
    contract {
        callsInPlace(message, InvocationKind.AT_MOST_ONCE)
        returns() implies value
    }
    test(getLog(coroutineContext), value, message)
}

internal fun defaultExceptionHandler(log: Log, lvl: LogLevel = LogLevel.Error) = CoroutineExceptionHandler { ctx, thr ->
    when (thr) {
        is CancellationException -> {
        }
        is FatalError -> exitProcess(3)
        else -> {
            log.exception(lvl, { "error occurred" }, thr)
            ctx[Job]?.cancel()
        }
    }
}

suspend fun waitCancellation(): Nothing {
    suspendCancellableCoroutine<Nothing> {}
}

suspend fun waitAndGetCancellation(): CancellationException {
    try {
        suspendCancellableCoroutine<Unit> {}
        unreachable()
    } catch (e: CancellationException) {
        return e
    }
}
