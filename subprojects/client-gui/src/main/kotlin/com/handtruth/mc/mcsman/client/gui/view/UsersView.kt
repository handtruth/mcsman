package com.handtruth.mc.mcsman.client.gui.view

import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerInfo
import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerView
import javafx.scene.input.KeyCode
import tornadofx.label
import tornadofx.pane

class UsersView : MCSManControllerView(Info.name) {

    object Info : MCSManControllerInfo() {
        override val name = "user"
        override val viewClass = UsersView::class
        override val keyCode = KeyCode.NUMPAD4
    }

    override val main = pane {
        label(Info.name)
    }
}
