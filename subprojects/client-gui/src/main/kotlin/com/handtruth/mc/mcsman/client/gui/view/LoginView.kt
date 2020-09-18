package com.handtruth.mc.mcsman.client.gui.view

import com.handtruth.mc.mcsman.client.gui.Styles
import com.handtruth.mc.mcsman.client.gui.model.LoginViewModel
import com.handtruth.mc.mcsman.client.gui.model.Protocols
import com.handtruth.mc.mcsman.client.gui.util.CoroutineView
import com.handtruth.mc.mcsman.client.gui.util.MCSManControllerView
import com.handtruth.mc.mcsman.client.gui.util.disabled
import javafx.geometry.Pos
import javafx.scene.control.Alert
import javafx.scene.control.ButtonBar
import javafx.scene.control.ButtonType
import javafx.scene.control.Label
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import tornadofx.*
import java.net.URI
import java.net.URISyntaxException

class LoginView : CoroutineView("login") {

    private val viewModel: LoginViewModel by inject()

    private var errorText: Label by singleAssign()

    override val root = vbox(spacing = 5, alignment = Pos.CENTER) {
        label("Minecraft Server Manager") {
            style {
                fontSize = 25.px
            }
        }
        separator()
        form {
            fieldset("URL") {
                textfield(viewModel.url) {
                    validator {
                        try {
                            it.isNullOrBlank() && return@validator error("empty URL")
                            val uri = URI(it!!)
                            val scheme = uri.scheme
                            val userInfo = uri.userInfo
                            when {
                                scheme.isNullOrBlank() -> error("protocol must be defined")
                                scheme.toLowerCase() !in Protocols.names -> error("unknown protocol")
                                uri.host.isNullOrBlank() -> error("host name must be defined")
                                userInfo != null && ':' in userInfo -> warning("it is secure to use password field")
                                else -> null
                            }
                        } catch (e: URISyntaxException) {
                            error(e.message)
                        }
                    }
                }
            }
            fieldset("Advanced") {
                field("Protocol") {
                    combobox(viewModel.protocol, Protocols.values().toList())
                }
                hbox(5) {
                    field("Socket") {
                        vbox {
                            textfield(viewModel.host)
                            label("host") {
                                addClass(Styles.fieldNotice)
                            }
                        }
                        label(":") {
                            addClass(Styles.delimiter)
                        }
                        vbox {
                            textfield(viewModel.port) {
                                maxWidth = 60.0
                            }
                            label("port") {
                                addClass(Styles.fieldNotice)
                            }
                        }
                    }
                }
                hbox(5) {
                    field("Unit") {
                        vbox {
                            textfield(viewModel.controller)
                            label("controller") {
                                addClass(Styles.fieldNotice)
                            }
                        }
                        label("/") {
                            addClass(Styles.delimiter)
                        }
                        vbox {
                            textfield(viewModel.entity) {
                                enableWhen(viewModel.controller.isNotEmpty)
                            }
                            label("entity") {
                                addClass(Styles.fieldNotice)
                            }
                        }
                    }
                }
            }
            separator()
            fieldset("Credentials") {
                field("Username") {
                    textfield(viewModel.username) {
                        required()
                    }
                }
                field("Password") {
                    passwordfield(viewModel.password) {
                        required()
                    }
                }
            }
            hbox(5) {
                checkbox("save form", viewModel.saveForm)
                checkbox("save password", viewModel.savePassword) {
                    enableWhen(viewModel.saveForm)
                }
            }
        }
        separator()
        buttonbar {
            button("Connect", ButtonBar.ButtonData.APPLY) {
                isDefaultButton = true
                enableWhen(viewModel.valid)
                asyncAction {
                    disabled(this@buttonbar) {
                        check(viewModel.commit())
                        val item = viewModel.item
                        try {
                            val uri = URI(item.url)
                            val path = uri.path.split('/')
                            val viewClass = when (path.size) {
                                0, 1 -> MCSManView::class
                                else -> {
                                    val name = path[1]
                                    val it = controller.controllers.find { it.name == name }
                                        ?: kotlin.error("unknown controller")
                                    it.viewClass
                                }
                            }
                            controller.connect(uri, item.username, item.password)
                            val view = find(viewClass)
                            view.openWindow(escapeClosesWindow = false, owner = null)
                            close()
                            path.getOrNull(2)?.let {
                                withContext(NonCancellable) {
                                    yield()
                                    view as MCSManControllerView
                                    logger.info { "Open Entity: $it" }
                                    view.loadEntity(it)
                                }
                            }
                        } catch (e: Exception) {
                            logger.error(e)
                            alert(
                                Alert.AlertType.ERROR, "What a Terrible Failure!", e.message, ButtonType.OK,
                                owner = this@LoginView.currentWindow, title = "Connection Error"
                            )
                        }
                    }
                }
            }
            button("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE) {
                isCancelButton = true
                action {
                    close()
                }
            }
        }
        errorText = label().addClass(Styles.errorText)
        addClass(Styles.dialog)
    }
}
