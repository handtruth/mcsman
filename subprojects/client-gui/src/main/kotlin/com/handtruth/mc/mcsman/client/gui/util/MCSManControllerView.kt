package com.handtruth.mc.mcsman.client.gui.util

import com.handtruth.mc.mcsman.client.gui.view.MCSManView
import javafx.scene.Parent
import javafx.scene.control.MenuBar
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import tornadofx.*

abstract class MCSManControllerView(name: String) : CoroutineView(name, "MCSMan: $name") {

    var topMenu: MenuBar by singleAssign()
        private set

    abstract val main: Parent

    final override val root = borderpane {
        top = menubar {
            topMenu = this
            menu("MCSMan") {
                item("Main View") {
                    action {
                        val view = find<MCSManView>()
                        if (view.isDocked)
                            view.currentWindow?.requestFocus()
                        else
                            view.openWindow(escapeClosesWindow = false, owner = null)
                    }
                }
                checkmenuitem(
                    "Admin",
                    KeyCodeCombination(KeyCode.S, KeyCombination.ALT_DOWN, KeyCombination.CONTROL_DOWN),
                    selected = controller.sudo
                ) {
                    enableWhen(controller.sudoPossible)
                }
            }
        }
        controller.activateShortcuts(this@MCSManControllerView)
    }

    override fun onBeforeShow() {
        super.onBeforeShow()
        root.center = main
    }

    inline fun topMenu(block: MenuBar.() -> Unit) {
        with(topMenu, block)
    }

    open fun loadEntity(name: String) {}
}
