package com.handtruth.mc.mcsman.server.util

interface SuspendInvokable<A, R> {
    suspend operator fun invoke(argument: A): R
}

interface Reactive<A, R> {
    fun register(invokable: SuspendInvokable<A, R>)
    fun forget(invokable: SuspendInvokable<A, R>)
}

operator fun <A, R> Reactive<A, R>.plusAssign(invokable: SuspendInvokable<A, R>) {
    register(invokable)
}

operator fun <A, R> Reactive<A, R>.minusAssign(invokable: SuspendInvokable<A, R>) {
    forget(invokable)
}

inline fun <A, R> Reactive<A, R>.register(crossinline invokable: suspend (A) -> R) {
    register(object : SuspendInvokable<A, R> {
        override suspend fun invoke(argument: A): R {
            return invokable(argument)
        }
    })
}

inline operator fun <A, R> Reactive<A, R>.plusAssign(crossinline invokable: suspend (A) -> R) {
    register(object : SuspendInvokable<A, R> {
        override suspend fun invoke(argument: A): R {
            return invokable(argument)
        }
    })
}

sealed class ReactiveProperty<A, R, S> : Reactive<A, R> {

    @Volatile var listeners: Set<SuspendInvokable<A, R>> = emptySet()

    final override fun register(invokable: SuspendInvokable<A, R>) {
        synchronized(this) {
            listeners = listeners + invokable
        }
    }

    final override fun forget(invokable: SuspendInvokable<A, R>) {
        synchronized(this) {
            listeners = listeners - invokable
        }
    }

    protected val subscribers: Set<SuspendInvokable<A, R>> get() = listeners

    abstract val invokable: SuspendInvokable<A, S>

}

abstract class SyncReactiveProperty<A, R, S> : ReactiveProperty<A, R, S>() {
    protected abstract fun first(): S

    protected abstract fun aggregate(acc: S, result: R): S

    protected abstract fun shouldContinue(acc: S): Boolean

    override val invokable = object : SuspendInvokable<A, S> {
        override suspend fun invoke(argument: A): S {
            var acc = first()
            subscribers.forEach {
                with(it) {
                    acc = aggregate(acc, invoke(argument))
                    if (!shouldContinue(acc))
                        return acc
                }
            }
            return acc
        }
    }
}

class FirstNotNullReactiveProperty<A, R> : SyncReactiveProperty<A, R?, R?>() {
    override fun first(): R? = null

    override fun aggregate(acc: R?, result: R?): R? = acc ?: result

    override fun shouldContinue(acc: R?) = acc == null
}
