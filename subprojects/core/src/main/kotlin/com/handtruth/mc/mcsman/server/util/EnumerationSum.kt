package com.handtruth.mc.mcsman.server.util

import java.util.*

fun <E> compose(enumerations: List<Enumeration<E>>): Enumeration<E> {
    return if (enumerations.isEmpty())
        emptyEnumeration()
    else
        IterableEnumeration(enumerations.asSequence().flatMap { it.asSequence() }.iterator())
}

@Suppress("UNCHECKED_CAST")
fun <E> emptyEnumeration(): Enumeration<E> = EmptyEnumeration as Enumeration<E>

private object EmptyEnumeration : Enumeration<Nothing> {
    override fun hasMoreElements() = false
    override fun nextElement() = throw NoSuchElementException()
}

private class IterableEnumeration<E>(private val iterator: Iterator<E>) : Enumeration<E> {
    override fun hasMoreElements() = iterator.hasNext()
    override fun nextElement() = iterator.next()
}
