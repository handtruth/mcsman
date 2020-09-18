package com.handtruth.mc.mcsman.server.event

import com.handtruth.mc.mcsman.event.Event
import kotlinx.io.Closeable
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal class CoroutineReactorContext : CoroutineContext.Element, Closeable {

    override val key get() = Key

    val pending: Queue<Event> = ConcurrentLinkedQueue<Event>()

    companion object Key : CoroutineContext.Key<CoroutineReactorContext>

    @Volatile var isActive: Boolean = true
        private set

    override fun close() {
        isActive = false
    }
}

internal suspend inline fun checkReactorContext(): CoroutineReactorContext {
    return coroutineContext[CoroutineReactorContext] ?: error("entity destruction allowed only inside reactor")
}
