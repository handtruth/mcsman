package com.handtruth.mc.mcsman.client.module

import com.handtruth.mc.mcsman.client.PaketMCSManClient
import com.handtruth.mc.mcsman.protocol.ExtensionPaket
import com.handtruth.mc.mcsman.protocol.ModulePaket
import com.handtruth.mc.paket.nest
import com.handtruth.mc.paket.peek
import com.handtruth.mc.paket.receiveAll
import com.handtruth.mc.paket.split
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class PaketModules internal constructor(
    override val client: PaketMCSManClient,
    specifications: List<PaketModuleSpecification>
) : Modules {
    internal val specifications: Map<String, PaketModuleSpecification> = specifications.associateBy { it.name }
    private val modules: ConcurrentMap<Int, PaketModule> = ConcurrentHashMap(1)

    internal val tss = client.async(start = CoroutineStart.LAZY) {
        val modules = list()
        val tss = client.extTs.split(modules.size + 1) {
            val head = it.peek(ExtensionPaket.Header)
            when (head.type) {
                ExtensionPaket.Types.Operate -> head.module
                ExtensionPaket.Types.Disconnect -> modules.size
            }
        }
        client.launch {
            tss.last().receiveAll {
                val head = peek(ExtensionPaket.Header)
                @Suppress("BlockingMethodInNonBlockingContext")
                this@PaketModules.modules[head.module]!!.connection.get().onDisconnect()
            }
        }
        MutableList(modules.size) {
            tss[it] nest ExtensionPaket.Source(it)
        }
    }

    override fun get(id: Int): PaketModule = modules.getOrPut(id) { PaketModule(this, id) }

    override suspend fun get(name: String): PaketModule {
        val paket = client.request(ModulePaket.GetIdRequest(name)) { peek(ModulePaket.GetIdResponse) }
        return get(paket.module).apply { internalName = paket.name }
    }

    override suspend fun list(): List<PaketModule> {
        val paket = client.request(ModulePaket.ListRequest) { peek(ModulePaket.ListResponse) }
        return paket.ids.zip(paket.names) { id, name -> get(id).apply { internalName = name } }
    }
}
