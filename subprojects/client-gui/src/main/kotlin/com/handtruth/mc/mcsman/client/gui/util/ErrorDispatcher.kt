package com.handtruth.mc.mcsman.client.gui.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlin.coroutines.CoroutineContext

object ErrorDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        throw Error("EMPTY DISPATCHER FAILURE!!!")
        //exitProcess(-1)
    }
}
