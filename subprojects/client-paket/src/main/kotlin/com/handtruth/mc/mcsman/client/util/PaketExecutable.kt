package com.handtruth.mc.mcsman.client.util

import com.handtruth.mc.mcsman.client.PaketMCSManClient
import com.handtruth.mc.mcsman.util.Executable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

abstract class PaketExecutable internal constructor(): Executable {
    protected abstract val client: PaketMCSManClient

    internal val input = BroadcastChannel<String>(1)
    internal val output = BroadcastChannel<String>(1)
    internal val errors = BroadcastChannel<String>(1)

    protected abstract suspend fun listen()
    protected abstract suspend fun mute()

    private val listeners = atomic(0) // awesome anime, actually

    private val mutex = Mutex()

    private inner class OutputReceiver(private val channel: ReceiveChannel<String>) : ReceiveChannel<String> by channel {
        override fun cancel(cause: CancellationException?) {
            if (listeners.decrementAndGet() == 0)
                client.launch { mute() }
            channel.cancel(cause)
        }
    }

    final override suspend fun subscribeInput(): ReceiveChannel<String> {
        if (listeners.getAndIncrement() == 0)
            listen()
        return OutputReceiver(input.openSubscription())
    }

    final override suspend fun subscribeOutput(): ReceiveChannel<String> {
        if (listeners.getAndIncrement() == 0)
            listen()
        return OutputReceiver(output.openSubscription())
    }

    final override suspend fun subscribeErrors(): ReceiveChannel<String> {
        if (listeners.getAndIncrement() == 0)
            listen()
        return OutputReceiver(errors.openSubscription())
    }
}
