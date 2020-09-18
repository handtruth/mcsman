package com.handtruth.mc.mcsman.event

import com.handtruth.mc.mcsman.common.event.MCSManEvent
import kotlinx.serialization.Serializable

/**
 * Base class for all events
 */
@MCSManEvent("")
interface Event {
    val success: Boolean
}

@Serializable
data class EventProjection(
    override val success: Boolean = true
) : Event

@MCSManEvent("cancellable")
interface CancellableEvent : Event

@Serializable
data class CancellableEventProjection(
    override val success: Boolean = true
) : Event

@MCSManEvent("direct")
interface DirectEvent : CancellableEvent {
    val direction: Boolean
}

@Serializable
data class DirectEventProjection(
    override val direction: Boolean = true,
    override val success: Boolean = true
) : DirectEvent

@MCSManEvent("mutate_event")
interface MutateEvent : CancellableEvent {
    val was: Any?
    val become: Any?
}

@Serializable
data class MutateEventProjection(
    override val was: Unit = Unit,
    override val become: Unit = Unit,
    override val success: Boolean = true
) : MutateEvent
