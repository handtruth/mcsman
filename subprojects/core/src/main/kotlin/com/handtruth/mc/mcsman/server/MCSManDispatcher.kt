package com.handtruth.mc.mcsman.server

import com.google.auto.service.AutoService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.MainCoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.internal.MainDispatcherFactory
import kotlin.coroutines.CoroutineContext

val Dispatchers.MCSMan: MCSManDispatcher
    get() = MainMCSManDispatcher

sealed class MCSManDispatcher : MainCoroutineDispatcher() {

    abstract override val immediate: MCSManDispatcher

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        MCSManCore.dispatcher!!.dispatch(context, block)
    }
}

internal object MCSManImmediate : MCSManDispatcher() {
    override val immediate = this

    override fun isDispatchNeeded(context: CoroutineContext) = !MCSManCore.isMCSManThread
}

internal object MainMCSManDispatcher : MCSManDispatcher() {
    override val immediate = MCSManImmediate
}

@AutoService(MainDispatcherFactory::class)
@OptIn(InternalCoroutinesApi::class)
internal class MCSManDispatcherFactory : MainDispatcherFactory {
    override val loadPriority = 2

    override fun createDispatcher(allFactories: List<MainDispatcherFactory>) = MainMCSManDispatcher
}
