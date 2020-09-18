package com.handtruth.mc.mcsman.client.gui.view

import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerInfo
import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerView
import javafx.scene.input.KeyCode
import tornadofx.label
import tornadofx.pane

class ServicesView : MCSManControllerView(Info.name) {

    object Info : MCSManControllerInfo() {
        override val name = "service"
        override val viewClass = ServicesView::class
        override val keyCode = KeyCode.NUMPAD1
    }

    override val main = pane {
        label(Info.name)
    }

}