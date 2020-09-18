package com.handtruth.mc.mcsman.client.gui.util

import com.handtruth.kommon.Log
import com.handtruth.kommon.TaggedLog
import com.handtruth.mc.mcsman.client.gui.AppController
import com.handtruth.mc.mcsman.client.gui.model.AppScope
import javafx.scene.Node
import javafx.scene.control.ButtonBase
import kotlinx.coroutines.*
import tornadofx.View
import tornadofx.action
import tornadofx.find
import kotlin.coroutines.CoroutineContext

abstract class CoroutineView(name: String, title: String? = null, icon: Node? = null) : View(title, icon), CoroutineScope {

    val controller: AppController by lazy { find<AppController>(scope) }

    final override val scope = super.scope as AppScope

    final override var coroutineContext: CoroutineContext = ErrorDispatcher
        private set

    protected val logger: Log by lazy { controller.logFactory.log(name) }

    override fun onDock() {
        super.onDock()
        val context = controller.coroutineContext
        val name = (logger as TaggedLog).tag
        coroutineContext = context + Job(context[Job]) + CoroutineName(name) + logger
    }

    fun ButtonBase.asyncAction(block: suspend CoroutineScope.() -> Unit) {
        action {
            @Suppress("SuspendFunctionOnCoroutineScope")
            launch(start = CoroutineStart.UNDISPATCHED, block = block)
        }
    }

    override fun onUndock() {
        super.onUndock()
        cancel()
        coroutineContext = ErrorDispatcher
    }
}
