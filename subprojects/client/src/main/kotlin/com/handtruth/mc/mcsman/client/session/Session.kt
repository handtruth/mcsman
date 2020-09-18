package com.handtruth.mc.mcsman.client.session

import com.handtruth.mc.mcsman.client.actor.Actor
import com.handtruth.mc.mcsman.client.module.Module
import com.handtruth.mc.mcsman.client.server.Server
import com.handtruth.mc.mcsman.client.service.Service
import com.handtruth.mc.mcsman.client.util.Entity
import com.handtruth.mc.mcsman.client.util.EntityInfo
import com.handtruth.mc.mcsman.util.Removable

interface Session : Entity, Removable {
    override val controller: Sessions

    override suspend fun inspect(): SessionInfo
}

data class SessionInfo(
    override val id: Int,
    val privileged: Boolean,
    val actor: Actor?,
    val modules: List<Module>,
    val servers: List<Server>,
    val services: List<Service>,
    val listenEvents: Boolean
) : EntityInfo
