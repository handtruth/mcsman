package com.handtruth.mc.mcsman.client.gui.model

import com.handtruth.mc.mcsman.client.gui.util.forget
import com.handtruth.mc.mcsman.client.gui.util.forward
import com.handtruth.mc.mcsman.client.gui.util.load
import com.handtruth.mc.mcsman.client.gui.util.set
import javafx.beans.property.*
import tornadofx.ItemViewModel
import tornadofx.getValue
import tornadofx.setValue
import java.net.URI
import java.net.URISyntaxException
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.component3

enum class Protocols {
    Paket, SPaket, HTTP, HTTPS;

    companion object {
        private val lowercase = values().associateBy { it.name.toLowerCase() } +
                mapOf("mcsman" to Paket, "mcsmans" to SPaket)

        val names: Set<String> get() = lowercase.keys

        operator fun get(name: String) = lowercase[name.toLowerCase()]
    }
}

class LoginModel {
    val urlProperty = SimpleStringProperty(this, "url", "")
    var url: String by urlProperty

    val usernameProperty = SimpleStringProperty(this, "username", "")
    var username: String by usernameProperty

    val passwordProperty = SimpleStringProperty(this, "password", "")
    var password: String by passwordProperty
}

class LoginViewModel : ItemViewModel<LoginModel>(LoginModel()) {
    val url = bind(LoginModel::urlProperty)
    val username = bind(LoginModel::username)
    val password = bind(LoginModel::password)

    private val protocolString = SimpleStringProperty("")
    val protocol = SimpleObjectProperty(Protocols.Paket)
    val port = SimpleStringProperty("")
    val host = SimpleStringProperty("")
    val controller = SimpleStringProperty("")
    val entity = SimpleStringProperty("")
    val saveForm = SimpleBooleanProperty(this, "saveForm", false)
    val savePassword = SimpleBooleanProperty(this, "savePassword", false)

    private val emptyFields = listOf("", "", "", "", "", "")

    private fun backward(address: String?): List<String> = run {
        try {
            address ?: return@backward emptyFields
            val uri = URI(address)
            val scheme = (uri.scheme ?: return@backward emptyFields).toLowerCase()
            val port = uri.port.let {
                if (it == -1) when (scheme) {
                    "http" -> 80
                    "https" -> 443
                    "pakets", "mcsmans" -> 1338
                    else -> 1337
                } else {
                    it
                }
            }
            uri.userInfo?.let { userInfo ->
                val auth = userInfo.split(':', limit = 2)
                username.value = auth[0]
                password.value = auth.getOrNull(1) ?: return@let
            }
            val path = (uri.path ?: "/").split('/', limit = 3)
            val controller = path.getOrElse(1) { "" }
            val entity = path.getOrElse(2) { "" }
            listOf(scheme, uri.host.orEmpty(), port.toString(), controller, entity)
        } catch (e: URISyntaxException) {
            emptyFields
        }
    }

    init {
        saveForm.addListener { _, _, value -> if (!value) savePassword.value = false }

        protocol forward {
            (it ?: Protocols.Paket).name.toLowerCase()
        } backward {
            Protocols[it!!]
        } bind2way protocolString

        arrayOf<Property<String?>>(
            protocolString, host, port, controller, entity
        ) forward { (protocol, host, port, controller, entity) ->
            port!!
            val portStr = when (protocol) {
                "http" -> if (port != "80") ":$port" else ""
                "https" -> if (port != "443") ":$port" else ""
                "mcsmans", "pakets" -> if (port != "1338") ":$port" else ""
                else -> if (port != "1337") ":$port" else ""
            }
            val uri = buildString {
                append(protocol).append("://").append(host).append(portStr)
                if (!controller.isNullOrBlank()) {
                    append('/').append(controller)
                    if (!entity.isNullOrBlank())
                        append('/').append(entity)
                }
            }
            listOf(uri)
        } backward { backward(it.first()) } bind2way arrayOf(url)

        preferences {
            load(url as StringProperty)
            load(saveForm as BooleanProperty)
            load(savePassword as BooleanProperty)
            load(username as StringProperty)
            load(password as StringProperty)
        }

        val data = backward(url.value)
        protocolString.value = data[0]
        protocol.value = Protocols[data[0]]
        host.value = data[1]
        port.value = data[2]
        controller.value = data[3]
        entity.value = data[4]
    }

    override fun onCommit() {
        super.onCommit()
        preferences {
            if (saveForm.value) {
                set(url as ReadOnlyStringProperty)
                set(saveForm as ReadOnlyBooleanProperty)
                set(username as ReadOnlyStringProperty)
                set(savePassword as ReadOnlyBooleanProperty)
                if (savePassword.value)
                    set(password as ReadOnlyStringProperty)
                else
                    forget(password as ReadOnlyStringProperty)
            } else {
                forget(url as ReadOnlyStringProperty)
                forget(saveForm as ReadOnlyBooleanProperty)
                forget(username as ReadOnlyStringProperty)
                forget(savePassword as ReadOnlyBooleanProperty)
                forget(password as ReadOnlyStringProperty)
            }
        }
    }
}
