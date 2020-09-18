@file:Suppress("FunctionName")

package com.handtruth.mc.mcsman.server.session

import com.handtruth.mc.mcsman.InternalMCSManApi
import com.handtruth.mc.mcsman.server.actor.Actor
import com.handtruth.mc.mcsman.server.actor.Group
import com.handtruth.mc.mcsman.server.actor.SuperActor
import com.handtruth.mc.mcsman.server.actor.User
import com.handtruth.mc.mcsman.server.service.Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

interface Agent : CoroutineContext.Element {
    val represent: Actor

    companion object Key : CoroutineContext.Key<Agent>

    override val key get() = Key
}

interface UpgradableAgent : Agent {
    val isPrivileged: Boolean
}

interface SuperpowerAgent : Agent

interface UserAgent : UpgradableAgent {
    override val represent: User
}

@InternalMCSManApi
interface GroupAgent : UpgradableAgent {
    override val represent: Group
}

interface ServiceAgent : SuperpowerAgent {
    override val represent: Service
}

object SuperAgent : SuperpowerAgent {
    override val represent = SuperActor
}

internal class FakeUserAgent(override val represent: User, override val isPrivileged: Boolean) : UserAgent

internal class FakeGroupAgent(override val represent: Group, override val isPrivileged: Boolean) : GroupAgent
internal class FakeServiceAgent(override val represent: Service) : ServiceAgent

fun Agent(actor: User, isPrivileged: Boolean = false): UserAgent = FakeUserAgent(actor, isPrivileged)
fun Agent(actor: Service): ServiceAgent = FakeServiceAgent(actor)

val Agent.isAdmin: Boolean get() = this is UpgradableAgent && isPrivileged || this is SuperpowerAgent

val Agent.actorName
    get() = when (this) {
        is UserAgent -> represent.name
        else -> "system"
    }

suspend inline fun <R> sudo(agent: Agent, noinline block: suspend CoroutineScope.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return withContext(agent, block)
}

suspend inline fun <R> sudo(noinline block: suspend CoroutineScope.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return sudo(SuperAgent, block)
}

suspend inline fun getAgent(): Agent = coroutineContext[Agent] ?: SuperAgent

suspend inline fun getActorName(): String = getAgent().actorName

fun Agent.upgrade(): Agent {
    @OptIn(InternalMCSManApi::class)
    return when (this) {
        is UserAgent -> FakeUserAgent(represent, true)
        is GroupAgent -> FakeGroupAgent(represent, true)
        is UpgradableAgent -> object : UpgradableAgent by this {
            override val isPrivileged get() = true
        }
        else -> this
    }
}

suspend inline fun <R> privileged(noinline block: suspend CoroutineScope.() -> R): R {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    return withContext(getAgent().upgrade(), block)
}
