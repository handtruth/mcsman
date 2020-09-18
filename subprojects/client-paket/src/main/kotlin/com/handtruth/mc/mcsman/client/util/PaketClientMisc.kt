package com.handtruth.mc.mcsman.client.util

import com.handtruth.mc.mcsman.client.MCSManClient
import com.handtruth.mc.mcsman.client.actor.Actor
import com.handtruth.mc.mcsman.common.model.AgentTypes

internal fun MCSManClient.subject(type: AgentTypes, actor: Int): Actor? =
    if (actor == 0) null else when (type) {
        AgentTypes.User -> actors.users.get(actor)
        AgentTypes.Group -> actors.groups.get(actor)
        AgentTypes.Service -> services.get(actor)
    }
