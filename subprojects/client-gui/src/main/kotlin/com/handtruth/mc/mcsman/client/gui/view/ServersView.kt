package com.handtruth.mc.mcsman.client.gui.view

import com.handtruth.mc.mcsman.client.gui.model.ServerScope
import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerInfo
import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerView
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.ObservableList
import javafx.scene.control.Tab
import javafx.scene.input.KeyCode
import javafx.stage.StageStyle
import kotlinx.coroutines.launch
import tornadofx.*

class ServersView : MCSManControllerView(Info.name) {

    object Info : MCSManControllerInfo() {
        override val name = "server"
        override val viewClass = ServersView::class
        override val keyCode = KeyCode.NUMPAD8
    }

    val logFactory = controller.logFactory.factory("server")

    private var tabs: ObservableList<Tab> by singleAssign()

    val dangerMode = SimpleBooleanProperty(false)

    init {
        with(topMenu) {
            menu("Server") {
                item("Connect") {
                    action {
                        find<ConnectView>(scope).openModal(
                            block = false, stageStyle = StageStyle.UNIFIED, resizable = false
                        )
                    }
                }
                item("Create")
            }
            menu {
                checkbox("Danger", dangerMode)
            }
            menu {
                checkbox("Save Tabs")
            }
        }
    }

    override val main = tabpane {
        root.prefWidth = 1000.0
        root.prefHeight = 600.0
        this@ServersView.tabs = tabs
    }

    override fun loadEntity(name: String) {
        launch {
            try {
                val servers = controller.client.servers
                val server = name.toIntOrNull()?.let { servers.get(it) } ?: servers.get(name)
                val serverName = server.name.get()
                tabs.add(Tab(serverName).apply {
                    val c = find<ServerFragment>(ServerScope(scope, server))
                    this += c
                })
            } catch (e: Exception) {
                logger.error(e)
                error("Failed to Open Server", controller.failMessage(e), title = "Terrible Error!")
            }
        }
    }

    override fun onDock() {
        super.onDock()
        with(main) {
            //setRegion<ServerFragment>(ServerScope(scope, controller.client.servers.get(1)), BorderPane::centerProperty)
        }
    }
}
