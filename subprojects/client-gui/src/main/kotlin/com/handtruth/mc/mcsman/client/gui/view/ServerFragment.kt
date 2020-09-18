package com.handtruth.mc.mcsman.client.gui.view

import com.handtruth.kommon.TaggedLog
import com.handtruth.mc.mcsman.client.gui.Styles
import com.handtruth.mc.mcsman.client.gui.model.ServerScope
import com.handtruth.mc.mcsman.client.gui.util.ErrorDispatcher
import com.handtruth.mc.mcsman.client.gui.util.disabled
import com.handtruth.mc.mcsman.client.server.ServerInfo
import com.handtruth.mc.mcsman.common.model.ExecutableStatus
import com.handtruth.mc.mcsman.util.forever
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.scene.control.ButtonBase
import kotlinx.coroutines.*
import tornadofx.*
import kotlin.coroutines.CoroutineContext

class ServerFragment : Fragment(), CoroutineScope {

    private val status = SimpleObjectProperty<ExecutableStatus?>(null)
    private val listening = SimpleBooleanProperty(false)

    override val scope = super.scope as ServerScope

    private val parent = find<ServersView>(scope.parent)
    private inline val client get() = parent.controller.client

    private val logger = parent.logFactory.log("#${scope.server.id}")

    private val info = SimpleObjectProperty<ServerInfo?>(null)

    override var coroutineContext: CoroutineContext = ErrorDispatcher
        private set

    override fun onDock() {
        super.onDock()
        val tag = (logger as TaggedLog).tag
        coroutineContext = parent.coroutineContext + CoroutineName(tag) + Job(parent.coroutineContext[Job]) + logger
        launch {
            val initial = scope.server.inspect()
            status.value = initial.status
            info.value = initial
        }
    }

    override fun onUndock() {
        super.onUndock()
        cancel()
        coroutineContext = ErrorDispatcher
        info.value = null
        status.value = null
    }

    private fun ButtonBase.asyncAction(block: suspend CoroutineScope.() -> Unit) {
        action {
            launch(start = CoroutineStart.UNDISPATCHED, block = block)
        }
    }

    private var appendable: Appendable by singleAssign()

    override val root = borderpane {
        top = buttonbar {
            button("Start") {
                removeWhen(status.isEqualTo(ExecutableStatus.Running) or status.isEqualTo(ExecutableStatus.Paused))
                disableWhen(status.isNull)
                asyncAction {
                    scope.server.start()
                }
            }
            val shutdownClosure = status.isNull or status.isEqualTo(ExecutableStatus.Stopped)
            button("Stop") {
                removeWhen(this@ServerFragment.parent.dangerMode or shutdownClosure)
                asyncAction {
                    scope.server.stop()
                }
            }
            button("Pause") {
                removeWhen(status.isEqualTo(ExecutableStatus.Paused))
                disableWhen(status.booleanBinding { it == null || it == ExecutableStatus.Stopped })
                asyncAction {
                    scope.server.pause()
                }
            }
            button("Resume") {
                removeWhen(status.isNotEqualTo(ExecutableStatus.Paused))
                asyncAction {
                    scope.server.resume()
                }
            }
            button("Kill") {
                disableWhen(this@ServerFragment.parent.dangerMode.not() or shutdownClosure)
                asyncAction {
                    scope.server.kill()
                }
            }
            button("Listen") {
                disableWhen(listening)
                asyncAction {
                    val channelA = scope.server.subscribeOutput()
                    val channelB = scope.server.subscribeErrors()
                    try {
                        forever {
                            val line = kotlinx.coroutines.selects.select<String> {
                                channelA.onReceive { it }
                                channelB.onReceive { it }
                            }
                            appendable.append(line).append('\n')
                        }
                    } finally {
                        channelA.cancel()
                        channelB.cancel()
                    }
                }
            }
            button("Mute") {
                disableWhen(listening.not())
            }
        }
        center = splitpane {
            textarea {
                appendable = object : Appendable {
                    override fun append(c: Char): Appendable {
                        appendText(c.toString())
                        return this
                    }

                    override fun append(csq: CharSequence?): java.lang.Appendable {
                        csq ?: return this
                        appendText(csq.toString())
                        return this
                    }

                    override fun append(csq: CharSequence?, start: Int, end: Int): java.lang.Appendable {
                        csq ?: return this
                        appendText(csq.substring(start, end))
                        return this
                    }
                }
                addClass(Styles.terminal)
            }
            scrollpane {
                form {
                    fieldset("Server Info") {
                        field("ID") {
                            textfield(info.stringBinding { it?.id?.toString() ?: "" }) {
                                disableWhen(info.isNull)
                            }
                        }
                        field("Name") {
                            textfield(info.stringBinding { it?.name ?: "" }) {
                                disableWhen(info.isNull)
                            }
                        }
                        field("Status") {
                            textfield(info.stringBinding { it?.status?.toString() ?: "" }) {
                                disableWhen(info.isNull)
                            }
                        }
                        field("Description") {
                            textfield(info.stringBinding { it?.description?.toString() ?: "" }) {
                                disableWhen(info.isNull)
                            }
                        }
                        field("Image Name") {
                            textfield(info.stringBinding { it?.image?.toString() ?: "" }) {
                                disableWhen(info.isNull)
                            }
                        }
                        field("Image ID") {
                            textfield(info.stringBinding { it?.imageId ?: "" }) {
                                disableWhen(info.isNull)
                            }
                        }
                        button("Refresh") {
                            asyncAction {
                                disabled(this@button) {
                                    val data = scope.server.inspect()
                                    info.value = data
                                    status.value = data.status
                                }
                            }
                        }
                    }
                }
            }
            setDividerPosition(0, 0.7)
        }
        bottom = textfield {

        }
    }
}
