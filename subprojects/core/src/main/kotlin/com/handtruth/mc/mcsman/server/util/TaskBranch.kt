package com.handtruth.mc.mcsman.server.util

import com.handtruth.kommon.Log
import com.handtruth.kommon.LogLevel
import kotlinx.coroutines.*
import org.koin.core.KoinComponent
import org.koin.core.get
import org.koin.core.parameter.parametersOf
import kotlin.coroutines.CoroutineContext

interface TaskBranch : CoroutineScope, Loggable, KoinComponent {

    fun fork(
        part: String,
        lvl: LogLevel = LogLevel.Error,
        dispatcher: CoroutineDispatcher = Dispatchers.Main
    ): CoroutineContext {
        val parentName = coroutineContext[CoroutineName]!!.name
        val tag = "$parentName/$part"
        val log: Log = get { parametersOf(tag) }
        val handler = defaultExceptionHandler(log, lvl)
        val name = CoroutineName(tag)
        val job = Job(coroutineContext[Job]!!)
        return handler + name + log + job + dispatcher
    }

    fun provideContext(name: String): CoroutineContext = fork(name)

}
