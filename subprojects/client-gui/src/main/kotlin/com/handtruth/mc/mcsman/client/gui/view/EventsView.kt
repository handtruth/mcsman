package com.handtruth.mc.mcsman.client.gui.view

import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerInfo
import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerView
import javafx.scene.input.KeyCode
import tornadofx.label
import tornadofx.pane

class EventsView : MCSManControllerView(Info.name) {

    object Info : MCSManControllerInfo() {
        override val name = "event"
        override val viewClass = EventsView::class
        override val keyCode = KeyCode.NUMPAD2
    }

    override val main = pane {
        label(Info.name)
    }
}
