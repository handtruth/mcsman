package com.handtruth.mc.mcsman.server.util

import com.handtruth.kommon.Log
import org.koin.core.logger.Level
import org.koin.core.logger.Logger
import org.koin.core.logger.MESSAGE

internal class KoinLogger(private val log: Log) : Logger() {
    override fun log(level: Level, msg: MESSAGE) {
        when (level) {
            Level.DEBUG -> log.debug { msg }
            Level.INFO -> log.info { msg }
            Level.ERROR -> log.error { msg }
            Level.NONE -> {}
        }
    }
}
