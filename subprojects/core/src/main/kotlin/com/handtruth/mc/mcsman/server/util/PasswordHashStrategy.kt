package com.handtruth.mc.mcsman.server.util

import org.koin.core.KoinComponent
import org.koin.core.inject
import org.koin.core.qualifier.named

interface PasswordHashStrategy {
    fun hash(password: String): String
    fun verify(data: String, password: String): Boolean

    companion object : MainThreadCOWMap<String, PasswordHashStrategy>(), KoinComponent {
        val default: PasswordHashStrategy by inject()
        val defaultName: String by inject(named<PasswordHashStrategy>())
    }
}
