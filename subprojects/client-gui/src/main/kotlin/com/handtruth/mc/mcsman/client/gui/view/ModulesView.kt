package com.handtruth.mc.mcsman.client.gui.view

import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerInfo
import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerView
import javafx.scene.input.KeyCode
import tornadofx.label
import tornadofx.pane

class ModulesView : MCSManControllerView(Info.name) {

    object Info : MCSManControllerInfo() {
        override val name = "module"
        override val viewClass = ModulesView::class
        override val keyCode = KeyCode.NUMPAD6
    }

    override val main = pane {
        label(Info.name)
    }

}
