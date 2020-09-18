package com.handtruth.mc.mcsman.client.gui.view

import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerInfo
import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerView
import javafx.scene.input.KeyCode
import tornadofx.label
import tornadofx.pane

class SessionsView : MCSManControllerView(Info.name) {

    object Info : MCSManControllerInfo() {
        override val name = "session"
        override val viewClass = SessionsView::class
        override val keyCode = KeyCode.NUMPAD9
    }

    override val main = pane {
        label(Info.name)
    }
}
