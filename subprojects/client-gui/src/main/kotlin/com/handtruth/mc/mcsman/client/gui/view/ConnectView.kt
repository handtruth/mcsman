package com.handtruth.mc.mcsman.client.gui.view

import com.handtruth.mc.mcsman.MCSManException
import com.handtruth.mc.mcsman.client.gui.Styles
import com.handtruth.mc.mcsman.client.gui.model.ConnectViewModel
import com.handtruth.mc.mcsman.client.gui.util.CoroutineView
import com.handtruth.mc.mcsman.client.gui.util.disabled
import javafx.scene.control.ButtonBar
import javafx.scene.control.ComboBox
import javafx.scene.text.Text
import kotlinx.coroutines.launch
import tornadofx.*

class ConnectView : CoroutineView("server[connect]") {
    private val model: ConnectViewModel by inject()
    private val main: ServersView by inject()

    private var comboBox: ComboBox<String> by singleAssign()

    private var errorText: Text by singleAssign()

    override val root = form {
        fieldset("Server") {
            field("Name") {
                textfield(model.server) {
                    promptText = "my-server"
                    required()
                }
                comboBox = combobox(model.server) {
                    prefWidth = 100.0
                }
            }
        }
        buttonbar {
            button("Connect", ButtonBar.ButtonData.OK_DONE) {
                isDefaultButton = true
                enableWhen(model.valid)
                asyncAction {
                    model.commit()
                    disabled(this@buttonbar) {
                        try {
                            val server = controller.client.servers.get(model.item.server)
                            main.loadEntity(server.id.toString())
                            close()
                        } catch (e: Exception) {
                            logger.error(e)
                            errorText.text = controller.failMessage(e)
                        }
                    }
                }
            }
            button(messages["bar.cancel"], ButtonBar.ButtonData.CANCEL_CLOSE) {
                isCancelButton = true
                action {
                    model.rollback()
                    close()
                }
            }
        }
        errorText = text().addClass(Styles.errorText)
        addClass(Styles.dialog)
    }

    init {
        title = messages["title"]
    }

    override fun onDock() {
        super.onDock()
        launch {
            try {
                val list = controller.client.servers.list()
                comboBox.items = list.map { it.name.get() }.asObservable()
                comboBox.isDisable = false
            } catch (e: MCSManException) {
                comboBox.isDisable = true
                logger.warning(e)
            }
        }
    }
}
