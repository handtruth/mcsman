package com.handtruth.mc.mcsman.common.event

import com.handtruth.mc.mcsman.event.Event
import kotlin.reflect.KClass

abstract class UnknownEvent(val className: String) : Event {
    abstract fun <E : Event> instanceOf(`class`: KClass<out E>): Boolean
    abstract fun <E : Event> treatAs(`class`: KClass<out E>): E
    inline fun <reified E : Event> instanceOf() = instanceOf(E::class)
    inline fun <reified E : Event> treatAs() = treatAs(E::class)
}
