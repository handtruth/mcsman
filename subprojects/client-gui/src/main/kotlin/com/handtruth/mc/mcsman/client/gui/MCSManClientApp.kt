package com.handtruth.mc.mcsman.client.gui

import com.handtruth.mc.mcsman.client.gui.assets.Assets
import com.handtruth.mc.mcsman.client.gui.model.AppScope
import com.handtruth.mc.mcsman.client.gui.view.LoginView
import tornadofx.App
import tornadofx.addStageIcon

class MCSManClientApp : App(LoginView::class, Styles::class, AppScope()) {
    init {
        addStageIcon(Assets.icon, scope)
    }
}
