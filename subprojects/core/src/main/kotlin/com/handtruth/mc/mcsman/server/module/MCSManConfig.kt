@file:UseSerializers(Argon2TypeSerializer::class, FileSerializer::class, JavaDurationSerializer::class)

package com.handtruth.mc.mcsman.server.module

import com.handtruth.mc.mcsman.server.util.Argon2TypeSerializer
import com.handtruth.mc.mcsman.server.util.FileSerializer
import com.handtruth.mc.mcsman.server.util.JavaDurationSerializer
import com.kosprov.jargon2.api.Jargon2
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.io.File
import java.time.Duration

@Serializable
data class MCSManConfig(
    override val enable: Boolean = true,
    val admin: AdminConfig = AdminConfig(),
    val passwords: PasswordsConfig = PasswordsConfig(),
    val authority: AuthorityConfig = AuthorityConfig()
) : ModuleConfig {
    @Serializable
    data class AdminConfig(
        val password: String = ""
    )

    @Serializable
    data class PasswordsConfig(
        val defaultAlgorithm: String = "argon2",
        val argon2: Argon2Config = Argon2Config()
    ) {
        @Serializable
        data class Argon2Config(
            val type: Jargon2.Type = Jargon2.Type.ARGON2i,
            val parallelism: Int = Runtime.getRuntime().availableProcessors() * 2,
            val memoryCost: Int = 65536,
            val timeCost: Int = 3,
            val saltLength: Int = 16,
            val hashLength: Int = 128
        )
    }

    @Serializable
    data class AuthorityConfig(
        val expiryDate: Duration = Duration.ofDays(356),
        val location: File = File("keys.dat")
    )
}
