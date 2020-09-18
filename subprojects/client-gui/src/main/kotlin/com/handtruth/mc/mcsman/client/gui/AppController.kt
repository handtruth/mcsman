package com.handtruth.mc.mcsman.client.gui

import com.handtruth.kommon.LogFactory
import com.handtruth.kommon.LogLevel
import com.handtruth.kommon.PrintLog
import com.handtruth.mc.mcsman.AccessDeniedMCSManException
import com.handtruth.mc.mcsman.AlreadyExistsMCSManException
import com.handtruth.mc.mcsman.AlreadyInStateMCSManException
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.client.MCSManClient
import com.handtruth.mc.mcsman.client.UnknownMCSManException
import com.handtruth.mc.mcsman.client.gui.model.Protocols
import com.handtruth.mc.mcsman.client.gui.util.Connector
import com.handtruth.mc.mcsman.client.gui.util.HTTPConnector
import com.handtruth.mc.mcsman.client.gui.util.PaketConnector
import com.handtruth.mc.mcsman.client.gui.view.*
import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.util.*
import javafx.beans.property.SimpleBooleanProperty
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyCodeCombination
import javafx.scene.input.KeyCombination
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.javafx.JavaFx
import kotlinx.coroutines.javafx.asFlow
import tornadofx.Controller
import tornadofx.View
import tornadofx.singleAssign
import java.net.URI

class AppController : Controller(), CoroutineScope {

    private fun getLogLevel(): LogLevel =
        LogLevel.getByName(System.getProperty("com.handtruth.mc.mcsman.verb", "info")) ?: LogLevel.Info

    val logFactory = LogFactory("mcsman", getLogLevel()) { tag, lvl -> PrintLog(tag, lvl) }

    val logger = logFactory.log()

    override val coroutineContext = Job() + CoroutineName("mcsman") + logger + Dispatchers.JavaFx

    var client: MCSManClient by singleAssign()
        private set

    @OptIn(KtorExperimentalAPI::class)
    val selector: SelectorManager by lazy {
        ActorSelectorManager(
            coroutineContext + Job(coroutineContext[Job]) +
                    CoroutineName("mcsman/selector") + Dispatchers.IO
        )
    }

    private val tlsContext = coroutineContext + Job(coroutineContext[Job]) +
            CoroutineName("mcsman/tls") + Dispatchers.IO

    suspend fun tls(socket: Socket): Socket {
        return socket.tls(tlsContext)
    }

    private val connectors: Map<Protocols, Connector>

    init {
        val paketConnector = PaketConnector(this)
        val httpConnector = HTTPConnector(this)
        connectors = mapOf(
            Protocols.Paket to paketConnector, Protocols.SPaket to paketConnector,
            Protocols.HTTP to httpConnector, Protocols.HTTPS to httpConnector
        )
    }

    private fun startSudoTask() = launch {
        sudoPossible.value = client.accesses.global.checkSelf(GlobalPermissions.admin)
        sudoState.collect {
            try {
                if (it)
                    client.enterAdminState()
                else
                    client.leaveAdminState()
            } catch (e: AlreadyInStateMCSManException) {

            }
        }
    }

    suspend fun connect(uri: URI, username: String, password: String): MCSManClient {
        val client = connectors[Protocols[uri.scheme!!]!!]!!.connect(uri, username, password)
        this.client = client
        startSudoTask()
        return client
    }

    val controllers = listOf(
        ServersView.Info, SessionsView.Info, ModulesView.Info, GroupsView.Info,
        EventsView.Info, ServicesView.Info, UsersView.Info, AccessesView.Info
    )

    fun activateShortcuts(view: View) {
        controllers.forEach {
            val combination = KeyCodeCombination(it.keyCode, KeyCombination.CONTROL_DOWN)
            view.accelerators[combination] = {
                it.open(this)
            }
        }
        view.accelerators[KeyCodeCombination(KeyCode.NUMPAD5, KeyCombination.CONTROL_DOWN)] = {
            val mcsmanView = find<MCSManView>()
            if (mcsmanView.isDocked)
                mcsmanView.currentWindow?.requestFocus()
            else
                mcsmanView.openWindow(escapeClosesWindow = false, owner = null)
        }
    }

    val sudoPossible = SimpleBooleanProperty(false)

    val sudo = SimpleBooleanProperty(false)

    val sudoState = sudo.asFlow()

    fun failMessage(e: Exception): String {
        return when (e) {
            is AccessDeniedMCSManException -> "access denied: ${e.message}"
            is AlreadyInStateMCSManException -> "no action required: ${e.message}"
            is AlreadyExistsMCSManException -> "this object already exists: ${e.message}"
            is NotExistsMCSManException -> "no such object: ${e.message}"
            is UnknownMCSManException -> "internal server error: ${e.message}"
            else -> e.message ?: "ERROR"
        }
    }
}
