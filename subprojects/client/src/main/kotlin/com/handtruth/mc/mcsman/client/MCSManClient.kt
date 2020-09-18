package com.handtruth.mc.mcsman.client

import com.handtruth.mc.mcsman.client.access.Accesses
import com.handtruth.mc.mcsman.client.actor.Actors
import com.handtruth.mc.mcsman.client.bundle.Bundles
import com.handtruth.mc.mcsman.client.event.Events
import com.handtruth.mc.mcsman.client.module.Modules
import com.handtruth.mc.mcsman.client.server.Servers
import com.handtruth.mc.mcsman.client.service.Services
import com.handtruth.mc.mcsman.client.session.Sessions
import com.handtruth.mc.mcsman.client.util.Closeable
import com.handtruth.mc.mcsman.common.model.AgentTypes
import kotlinx.coroutines.CoroutineScope
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

interface MCSManClient : CoroutineScope, Closeable {
    suspend fun authorizeByPassword(login: String, password: String, type: AgentTypes = AgentTypes.User)
    suspend fun authorizeByToken(token: String, type: AgentTypes = AgentTypes.User)

    val servers: Servers
    val actors: Actors
    val sessions: Sessions
    val services: Services
    val modules: Modules
    val accesses: Accesses
    val bundles: Bundles
    val events: Events

    suspend fun enterAdminState()
    suspend fun leaveAdminState()

    companion object
}

suspend inline fun <R> MCSManClient.sudo(block: () -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    enterAdminState()
    try {
        return block()
    } finally {
        leaveAdminState()
    }
}
