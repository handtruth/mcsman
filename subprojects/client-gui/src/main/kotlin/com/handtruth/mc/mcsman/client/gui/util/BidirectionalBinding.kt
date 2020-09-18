package com.handtruth.mc.mcsman.client.gui.util

import javafx.beans.property.Property
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue

private class DirectObserver<A, B>(val from: Array<Property<A?>>,
                                   val to: Array<Property<B?>>,
                                   val converter: (List<A?>) -> List<B?>) : ChangeListener<A?> {
    var ignoreCount = 0

    lateinit var other: DirectObserver<B, A>

    override fun changed(observable: ObservableValue<out A?>, oldValue: A?, newValue: A?) {
        if (ignoreCount < from.size) {
            ++ignoreCount
            return
        }
        val input = List(from.size) { from[it].value!! }
        val output = converter(input)
        other.ignoreCount = 0
        for ((i, each) in to.withIndex())
            each.value = output[i]
    }
}

fun <A, B> twoWayBind(first: Array<Property<A?>>,
                      second: Array<Property<B?>>,
                      forward: (List<A?>) -> List<B?>,
                      backward: (List<B?>) -> List<A?>) {
    val forwardObserver = DirectObserver(first, second, forward)
    val backwardObserver = DirectObserver(second, first, backward)
    forwardObserver.other = backwardObserver
    backwardObserver.other = forwardObserver

    val input = List(second.size) { second[it].value }
    val output = backward(input)

    for ((i, each) in first.withIndex()) {
        each.value = output[i]
        each.addListener(forwardObserver)
    }
    for (each in second)
        each.addListener(backwardObserver)
}

class BindBagArray<A, B>(val first: Array<Property<A?>>) {
    var forward: ((List<A?>) -> List<B?>)? = null
    var backward: ((List<B?>) -> List<A?>)? = null

    infix fun forward(action: (List<A?>) -> List<B?>): BindBagArray<A, B> {
        require(forward == null) { "forward action already specified" }
        this.forward = action
        return this
    }

    infix fun backward(action: (List<B?>) -> List<A?>): BindBagArray<A, B> {
        require(backward == null) { "backward action already specified" }
        backward = action
        return this
    }

    infix fun bind2way(other: Array<Property<B?>>) {
        val backward = requireNotNull(backward) { "transform backward function not given" }
        val forward = requireNotNull(forward) { "transform forward function not given" }
        twoWayBind(first, other, forward, backward)
    }
}

infix fun <A, B> Array<Property<A?>>.forward(action: (List<A?>) -> List<B?>): BindBagArray<A, B> {
    val bag = BindBagArray<A, B>(this)
    bag.forward = action
    return bag
}

infix fun <A, B> Array<Property<A?>>.backward(action: (List<B?>) -> List<A?>): BindBagArray<A, B> {
    val bag = BindBagArray<A, B>(this)
    bag.backward = action
    return bag
}

class BindBag<A, B>(val first: Property<A?>) {
    var forward: ((A?) -> B?)? = null
    var backward: ((B?) -> A?)? = null

    infix fun forward(action: (A?) -> B?): BindBag<A, B> {
        require(forward == null) { "forward action already specified" }
        this.forward = action
        return this
    }

    infix fun backward(action: (B?) -> A?): BindBag<A, B> {
        require(backward == null) { "backward action already specified" }
        backward = action
        return this
    }

    infix fun bind2way(other: Property<B?>) {
        val backward = requireNotNull(backward) { "transform backward function not given" }
        val forward = requireNotNull(forward) { "transform forward function not given" }
        twoWayBind(arrayOf(first), arrayOf(other),
            { listOf(forward(it.first())) }, { listOf(backward(it.first())) })
    }
}

infix fun <A, B> Property<A?>.forward(action: (A?) -> B?): BindBag<A, B> {
    val bag = BindBag<A, B>(this)
    bag.forward = action
    return bag
}

infix fun <A, B> Property<A?>.backward(action: (B?) -> A?): BindBag<A, B> {
    val bag = BindBag<A, B>(this)
    bag.backward = action
    return bag
}
