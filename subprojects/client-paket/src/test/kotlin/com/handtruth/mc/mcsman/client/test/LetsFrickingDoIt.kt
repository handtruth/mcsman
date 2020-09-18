package com.handtruth.mc.mcsman.client.test

import com.handtruth.mc.mcsman.client.PaketMCSManClient
import com.handtruth.mc.mcsman.client.sudo
import com.handtruth.mc.paket.PaketTransmitter
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking

class LetsFrickingDoIt {

    @KtorExperimentalAPI
    //@Test
    fun lol() {
        runBlocking {
            val context = coroutineContext + Dispatchers.IO + Job(coroutineContext[Job])
            val selector = ActorSelectorManager(Dispatchers.IO)
            val sock = aSocket(selector).tcp()
                .connect("localhost", 1337)
            println("sock created")
            PaketTransmitter(sock.openReadChannel(), sock.openWriteChannel()).use { ts ->
                println("transmitter created")
                val client = PaketMCSManClient(context, ts, emptyList())
                println("client created")
                client.handshake("localhost", 1337u)
                println("handshake sent")
                client.authorizeByPassword("root", "lolkeklol")
                println("authorized")
                val session = client.sessions.current.get()
                println("session")
                val sessionInfo = session.inspect()
                println("me: $sessionInfo")
                client.sudo {
                    println("sudo in")
                    //val server = client.servers.create("vanilla", ImageName("handtruth/mcscon:vanilla"))
                    //println("server created: ${server.inspect()}")
                    println(client.modules.get("mcsman").inspect())
                }
                println("sudo out")
            }
        }
    }

}
