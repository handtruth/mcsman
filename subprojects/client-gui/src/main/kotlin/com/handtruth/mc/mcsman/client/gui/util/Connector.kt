package com.handtruth.mc.mcsman.client.gui.util

import com.handtruth.mc.mcsman.client.MCSManClient
import com.handtruth.mc.mcsman.client.connect
import com.handtruth.mc.mcsman.client.gui.AppController
import com.handtruth.mc.mcsman.client.gui.model.Protocols
import io.ktor.network.sockets.*
import java.net.URI

interface Connector {
    suspend fun connect(uri: URI, username: String, password: String): MCSManClient
}

class PaketConnector(private val controller: AppController) : Connector {
    override suspend fun connect(uri: URI, username: String, password: String): MCSManClient {
        val protocol = Protocols[uri.scheme!!]
        val port = uri.port.let {
            if (it == -1) when (protocol) {
                Protocols.Paket -> 1337
                Protocols.SPaket -> 1338
                else -> error("unreachable")
            } else it
        }
        val rawSocket = aSocket(controller.selector).tcp().connect(uri.host, port)
        val socket = if (protocol == Protocols.Paket)
            rawSocket
        else
            controller.tls(rawSocket)
        val client = MCSManClient.connect(socket, controller.coroutineContext)
        try {
            client.authorizeByPassword(username, password)
            return client
        } catch (thr: Throwable) {
            client.close()
            throw thr
        }
    }
}

class HTTPConnector(private val controller: AppController) : Connector {
    override suspend fun connect(uri: URI, username: String, password: String): MCSManClient {
        error("HTTP protocol is in development, consider to use \"paket\"")
    }
}
