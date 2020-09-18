package com.handtruth.mc.mcsman.server.session

import com.handtruth.kommon.Log
import com.handtruth.mc.mcsman.NotExistsMCSManException
import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.server.AgentCheck
import com.handtruth.mc.mcsman.server.MCSManCore
import com.handtruth.mc.mcsman.server.TransactionContext
import com.handtruth.mc.mcsman.server.access.Accesses
import com.handtruth.mc.mcsman.server.util.Loggable
import com.handtruth.mc.mcsman.server.util.suspendTransaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.sql.Database
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

open class Sessions : CoroutineScope, Loggable, KoinComponent {
    private val db: Database by inject()
    private val accesses: Accesses by inject()

    private val _sessions: MutableMap<Int, Session> = ConcurrentHashMap(1)
    val sessions: Map<Int, Session> get() = _sessions

    private val mutex = Mutex()

    internal suspend fun putSession(session: Session) {
        mutex.withLock {
            _sessions[session.id] = session
        }
    }

    internal suspend fun removeSession(session: Session) {
        mutex.withLock {
            _sessions.remove(session.id)
        }
    }

    final override val coroutineContext = MCSManCore.fork("session")
    final override val log = coroutineContext[Log]!!

    private suspend inline fun checkPermission() {
        val agent = getAgent()
        @OptIn(TransactionContext::class)
        suspendTransaction(db) {
            accesses.global.checkAllowed(agent, GlobalPermissions.session, "session")
        }
    }

    @AgentCheck
    suspend fun list(): MutableList<Int> {
        checkPermission()
        return sessions.keys.toMutableList()
    }

    @AgentCheck
    suspend fun getOrNull(id: Int): Session? {
        checkPermission()
        return sessions[id]
    }

    @AgentCheck
    suspend fun get(id: Int): Session = getOrNull(id) ?: throw NotExistsMCSManException("session #$id does not exist")
}
