package com.handtruth.mc.mcsman.client.module

import com.handtruth.mc.mcsman.protocol.ExtensionPaket
import com.handtruth.mc.paket.PaketTransmitter
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext

interface ModulePaketTransmitter : PaketTransmitter, CoroutineScope {
    val module: PaketModule
    suspend fun disconnect()
}

inline val ModulePaketTransmitter.client get() = module.controller.client

internal class ModulePaketTransmitterImpl(
    parent: PaketTransmitter,
    override val module: PaketModule,
    override val coroutineContext: CoroutineContext
) : ModulePaketTransmitter, PaketTransmitter by parent {
    override suspend fun disconnect() {
        client.send(ExtensionPaket.Disconnect(module.id))
    }

    override fun close() {}
}
