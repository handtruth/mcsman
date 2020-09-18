package com.handtruth.mc.mcsman.common.event

import com.handtruth.mc.mcsman.event.CancellableEvent
import com.handtruth.mc.mcsman.event.DirectEvent
import com.handtruth.mc.mcsman.event.Event
import com.handtruth.mc.mcsman.event.MutateEvent
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.instanceParameter
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters

private fun <E : Event, R1, R2> E.invokeCopy(
    firstKey: KProperty1<in E, R1>, firstValue: R1,
    secondKey: KProperty1<in E, R2>?, secondValue: R2
): E {
    @Suppress("UNCHECKED_CAST")
    val klass = this::class as KClass<E>
    require(klass.isData) { "event class is not declared as data class" }
    val copy = klass.memberFunctions.find { it.name == "copy" }
    check(copy != null) { "missing copy function" }
    val valueParameters = copy.valueParameters
    val args: MutableMap<KParameter, Any?> = hashMapOf()
    args[copy.instanceParameter!!] = this
    args[firstKey.name.let { name -> valueParameters.find { it.name == name }!! }] = firstValue
    if (secondKey != null)
        args[secondKey.name.let { name -> valueParameters.find { it.name == name }!! }] = secondValue
    @Suppress("UNCHECKED_CAST")
    return copy.callBy(args) as E
}

private fun <E : Event, R> E.invokeCopy(key: KProperty1<in E, R>, value: R): E {
    return invokeCopy(key, value, null, null)
}

fun <E : Event> E.fail(): E {
    return invokeCopy(Event::success, false)
}

fun <E : DirectEvent> E.reverse(): E {
    return invokeCopy(DirectEvent::direction, !direction)
}

fun <E : MutateEvent> E.degrade(): E {
    return invokeCopy(MutateEvent::was, become, MutateEvent::become, was)
}

inline fun <reified E : CancellableEvent> E.cancel(): E = when (this) {
    is DirectEvent -> reverse<DirectEvent>()
    is MutateEvent -> degrade<MutateEvent>()
    else -> throw UnsupportedOperationException()
} as E

suspend inline fun EventsBase.listen(crossinline block: suspend (Event) -> Unit): Nothing {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    bus.collect(block)
    throw IllegalStateException("should not be here")
}

suspend inline fun EventsBase.listenSuccessful(crossinline block: suspend (Event) -> Unit): Nothing {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    listen { if (it.success) block(it) }
}

@JvmName("listenReified")
suspend inline fun <reified E : Event> EventsBase.listen(crossinline block: suspend (E) -> Unit): Nothing {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    listen { if (it is E) block(it) }
}

@JvmName("listenSuccessfulReified")
suspend inline fun <reified E : Event> EventsBase.listenSuccessful(crossinline block: suspend (E) -> Unit): Nothing {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    listen<E> { if (it.success) block(it) }
}

// TODO: Simplify when fixed https://youtrack.jetbrains.com/issue/KT-40124
suspend fun EventsBase.catch(lambda: suspend (Event) -> Boolean): Event {
    contract {
        callsInPlace(lambda, InvocationKind.AT_LEAST_ONCE)
    }
    return bus.first(lambda)
}

@JvmName("collectFirstExact")
suspend inline fun <reified E : Event> EventsBase.catch(lambda: (E) -> Boolean = { true }): E {
    contract {
        callsInPlace(lambda, InvocationKind.AT_LEAST_ONCE)
    }
    return catch { it is E } as E
}
