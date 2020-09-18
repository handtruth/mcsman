package com.handtruth.mc.mcsman.server.event

import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.common.access.ServerPermissions
import com.handtruth.mc.mcsman.event.*
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.access.Accesses
import com.handtruth.mc.mcsman.server.actor.Group
import com.handtruth.mc.mcsman.server.actor.User
import com.handtruth.mc.mcsman.server.model.GroupMemberTable
import com.handtruth.mc.mcsman.server.model.GroupTable
import com.handtruth.mc.mcsman.server.session.Sessions
import com.handtruth.mc.mcsman.server.session.actorName
import com.handtruth.mc.mcsman.server.session.getAgent
import com.handtruth.mc.mcsman.server.util.suspendTransaction
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.select
import org.koin.core.KoinComponent
import org.koin.core.get

fun MCSManVetoes(component: KoinComponent) {
    val db: Database = component.get()
    val events: Events = component.get()
    val accesses: Accesses = component.get()
    val sessions: Sessions = component.get()

    events.veto<Event> {
        Veto.allowing { accesses.global.isAllowed(GlobalPermissions.event) }
    }

    events.veto<ActorEvent> {
        val actor = getAgent().actorName
        Veto.allowing { it.actor == actor }
    }

    events.veto<ServerEvent> {
        Veto.allowing { accesses.server.isAllowed(it.server, ServerPermissions.event) }
    }

    events.veto<UserEvent> {
        val actor = getAgent().actorName
        Veto.allowing { actor == it.user }
    }

    events.veto<GroupEvent> {
        val agent = getAgent()
        val actor = agent.represent
        Veto.allowing {
            actor is Group && actor.name == it.group || actor is User && @OptIn(TransactionContext::class)
            (suspendTransaction(db) {
        !(GroupMemberTable innerJoin GroupTable).select {
            (GroupTable.name eq it.group) or (GroupTable.owner eq actor.id)
        }.empty()
    })
        }
    }

    events.veto<SessionEvent> {
        Veto.allowing {
            val session = sessions.getOrNull(it.session) ?: return@allowing false
            session.agent === getAgent()
        }
    }

    events.veto<PermissionSubjectEvent> {
        val agent = getAgent()
        val actor = agent.represent
        Veto.allowing {
            actor is User && actor.name == it.userSubject || actor is Group && actor.name == it.groupSubject
        }
    }
}
