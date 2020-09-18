package com.handtruth.mc.mcsman.server.util

import kotlin.coroutines.CoroutineContext

/**
 * This is a time paradox context! It allows you to create a *time paradox*!
 * If you want to destroy time you can add it into your coroutine context!
 */
object TimeParadox : CoroutineContext.Element, CoroutineContext.Key<TimeParadox> {
    override val key = this

    suspend inline fun exists(): Boolean = kotlin.coroutines.coroutineContext[this] != null
}
