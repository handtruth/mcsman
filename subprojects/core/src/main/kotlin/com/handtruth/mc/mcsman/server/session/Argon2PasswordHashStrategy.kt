package com.handtruth.mc.mcsman.server.session

import com.handtruth.mc.mcsman.server.module.MCSManConfig
import com.handtruth.mc.mcsman.server.util.PasswordHashStrategy
import com.kosprov.jargon2.api.Jargon2.*

class Argon2PasswordHashStrategy(config: MCSManConfig.PasswordsConfig.Argon2Config) :
    PasswordHashStrategy {
    private val hasher: Hasher = jargon2Hasher()
        .type(config.type)
        .memoryCost(config.memoryCost)
        .timeCost(config.timeCost)
        .parallelism(config.parallelism)
        .saltLength(config.saltLength)
        .hashLength(config.hashLength)

    private val verifier = jargon2Verifier()

    override fun hash(password: String): String {
        return toByteArray(password).encoding(Charsets.UTF_8).use {
            hasher.password(it).encodedHash()
        }
    }

    override fun verify(data: String, password: String): Boolean {
        return toByteArray(password).encoding(Charsets.UTF_8).use { verifier.hash(data).password(it).verifyEncoded() }
    }

    override fun toString(): String = "HashStrategy(argon2)"
}
