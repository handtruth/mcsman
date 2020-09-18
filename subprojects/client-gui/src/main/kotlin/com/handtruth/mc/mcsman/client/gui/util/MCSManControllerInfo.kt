package com.handtruth.mc.mcsman.client.gui.util

import javafx.scene.input.KeyCode
import tornadofx.Component
import kotlin.reflect.KClass

abstract class MCSManControllerInfo {
    abstract val viewClass: KClass<out MCSManControllerView>
    abstract val name: String
    abstract val keyCode: KeyCode

    fun open(component: Component) {
        val view = component.find(viewClass)
        if (view.isDocked)
            view.currentWindow?.requestFocus()
        else
            view.openWindow(escapeClosesWindow = false, owner = null)
    }
}
