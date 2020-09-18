package com.handtruth.mc.mcsman.client.module

import com.handtruth.mc.mcsman.client.PaketMCSManClient

abstract class PaketModuleSpecification(name: String = "") {
    private fun createName(): String {
        val className = this::class.simpleName!!
        return (if (className.endsWith("module", ignoreCase = true))
            className.dropLast(6)
        else
            className).toLowerCase()
    }

    val name: String = if (name.isEmpty()) createName() else name

    protected open fun initialize(client: PaketMCSManClient) {}
    internal fun invokeInitialize(client: PaketMCSManClient) = initialize(client)

    protected abstract fun onConnection(ts: ModulePaketTransmitter): ModuleConnection
    internal fun invokeOnConnection(ts: ModulePaketTransmitter) = onConnection(ts)
}
