package com.handtruth.mc.mcsman.server.module

import com.handtruth.kommon.singleAssign
import com.handtruth.mc.mcsman.common.access.GlobalPermissions
import com.handtruth.mc.mcsman.common.access.ServerPermissions
import com.handtruth.mc.mcsman.common.event.catch
import com.handtruth.mc.mcsman.common.model.AgentTypes
import com.handtruth.mc.mcsman.event.GlobalPermissionEvent
import com.handtruth.mc.mcsman.event.LoginMethodEvent
import com.handtruth.mc.mcsman.event.MCSManLifeEvent
import com.handtruth.mc.mcsman.protocol.mcsman.ChangePasswordPaket
import com.handtruth.mc.mcsman.protocol.mcsman.GetPKeyPaket
import com.handtruth.mc.mcsman.protocol.mcsman.MCSManPaketID
import com.handtruth.mc.mcsman.protocol.mcsman.id
import com.handtruth.mc.mcsman.server.ConfigMCSManException
import com.handtruth.mc.mcsman.server.access.Accesses
import com.handtruth.mc.mcsman.server.access.AccessesFunctionality
import com.handtruth.mc.mcsman.server.access.Permissions
import com.handtruth.mc.mcsman.server.actor.Actors
import com.handtruth.mc.mcsman.server.actor.ActorsFunctionality
import com.handtruth.mc.mcsman.server.actor.User
import com.handtruth.mc.mcsman.server.event.Events
import com.handtruth.mc.mcsman.server.event.MCSManVetoes
import com.handtruth.mc.mcsman.server.model.GlobalPermissionTable
import com.handtruth.mc.mcsman.server.model.LoginMethodTable
import com.handtruth.mc.mcsman.server.model.UserTable
import com.handtruth.mc.mcsman.server.model.tables
import com.handtruth.mc.mcsman.server.server.ServersFunctionality
import com.handtruth.mc.mcsman.server.service.ServicesFunctionality
import com.handtruth.mc.mcsman.server.session.Argon2PasswordHashStrategy
import com.handtruth.mc.mcsman.server.session.NonePasswordHashStrategy
import com.handtruth.mc.mcsman.server.session.SessionsFunctionality
import com.handtruth.mc.mcsman.server.session.getAgent
import com.handtruth.mc.mcsman.server.util.PasswordHashStrategy
import com.handtruth.mc.mcsman.server.util.suspendTransaction
import com.handtruth.mc.nbt.NBTBinaryCodec
import com.handtruth.mc.nbt.NBTBinaryConfig
import com.handtruth.mc.nbt.NBTSerialFormat
import com.handtruth.mc.nbt.plus
import com.handtruth.mc.nbt.tags.CompoundTag
import com.handtruth.mc.paket.Paket
import com.handtruth.mc.paket.peek
import com.handtruth.mc.paket.replyAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.io.asOutput
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.core.context.loadKoinModules
import org.koin.core.get
import org.koin.core.inject
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant

@MCSManModule(after = [], before = [])
object MCSMan : ConfigurableModule<MCSManConfig>(configDeserializer = MCSManConfig.serializer()) {

    private val db: Database by inject()
    private val events: Events by inject()
    private val users: Actors.Users by inject()
    private val accesses: Accesses by inject()

    private var _keysInfo: KeysInfoImpl by singleAssign()
    val keysInfo: KeysInfo get() = _keysInfo

    interface KeysInfo {
        val expiryDate: Instant
        val publicKey: PublicKey
    }

    private class KeysInfoImpl(
        override val expiryDate: Instant,
        override val publicKey: PublicKey,
        val privateKey: PrivateKey
    ) : KeysInfo {
        constructor(expiryDate: Long, publicKey: PublicKey, privateKey: PrivateKey) : this(
            Instant.ofEpochSecond(expiryDate), publicKey, privateKey
        )
    }

    @Serializable
    private class Keystore(
        val version: Int,
        val expiryDate: Long,
        val publicKey: ByteArray,
        val privateKey: ByteArray
    )

    private val kbt = NBTSerialFormat() + NBTBinaryCodec(NBTBinaryConfig.KBT)

    private fun createKeyPair(): KeysInfoImpl {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(4096)
        val keys = keyGen.genKeyPair()
        val date = Instant.now()
            .plusSeconds(config.authority.expiryDate.seconds)
        val keystore = Keystore(3, date.epochSecond, keys.public.encoded, keys.private.encoded)
        config.authority.location.outputStream().asOutput().use {
            kbt.write(it, kbt.toNBT(Keystore.serializer(), keystore) as CompoundTag)
        }
        return KeysInfoImpl(date, keys.public, keys.private)
    }

    override fun initialize() {
        val config = config // will stuck without this line, idk why...

        PasswordHashStrategy["none"] = NonePasswordHashStrategy()
        PasswordHashStrategy["argon2"] = Argon2PasswordHashStrategy(config.passwords.argon2)

        run {
            _keysInfo = if (config.authority.location.exists()) {
                try {
                    val keystore =
                        kbt.load(
                            Keystore.serializer(),
                            config.authority.location.inputStream().readBytes()
                        )
                    if (keystore.expiryDate < Instant.now().epochSecond) {
                        createKeyPair()
                    } else {
                        val factory = KeyFactory.getInstance("RSA")
                        val forPublic = X509EncodedKeySpec(keystore.publicKey)
                        val public = factory.generatePublic(forPublic)
                        val forPrivate = PKCS8EncodedKeySpec(keystore.privateKey)
                        val private = factory.generatePrivate(forPrivate)
                        KeysInfoImpl(keystore.expiryDate, public, private)
                    }
                } catch (e: Exception) {
                    log.error(e) { "failed to read MCSMan keys" }
                    createKeyPair()
                }
            } else {
                createKeyPair()
            }
        }

        val koin = module {
            single(named<PasswordHashStrategy>()) { config.passwords.defaultAlgorithm }
            single {
                val algorithm: String = get(named<PasswordHashStrategy>())
                PasswordHashStrategy[algorithm]
                    ?: throw ConfigMCSManException("hash algorithm \"$algorithm\" not implemented")
            }
        }
        loadKoinModules(koin)

        events.register<MCSManLifeEvent>()
        ActorsFunctionality().initialize()
        AccessesFunctionality().initialize()
        ServersFunctionality().apply {
            initialize()
            launch { listen() }
        }
        ServicesFunctionality().initialize()
        SessionsFunctionality()
        permissions()
        MCSManVetoes(this)
        val (hasNotRoot, hasNotSystem, hasNotDocker) = transaction(db) {
            SchemaUtils.create(*(tables + events.tables.toList()).toTypedArray())
            val table = UserTable
            Triple(
                table.select { table.name eq "root" }.empty(),
                table.select { table.name eq "system" }.empty(),
                table.select { table.name eq "docker" }.empty()
            )
        }
        blocking {
            if (hasNotRoot)
                users.create("root", "Administrator")
            if (hasNotSystem)
                users.create("system", "System Service")
            if (hasNotDocker)
                users.create("docker", "Docker Actor")
        }
        GlobalPermissionTable.let { table ->
            val answer = transaction(db) {
                (table innerJoin UserTable).selectAll().toList()
                (table innerJoin UserTable).select {
                    (table.permission eq GlobalPermissions.admin) and (UserTable.name eq "root")
                }.limit(1).firstOrNull()
            }
            if (answer == null) blocking {
                log.info { "not found root admin privileges, creating new..." }
                events.raise(
                    GlobalPermissionEvent(
                        GlobalPermissions.admin, true, "system", userSubject = "root"
                    )
                )
            } else if (!answer[table.allowed]) blocking {
                log.warning { "root admin privileges negated! Grant privileges again..." }
                events.raise(
                    GlobalPermissionEvent(
                        GlobalPermissions.admin, false, "system", userSubject = "root", direction = false
                    )
                )
                events.raise(
                    GlobalPermissionEvent(GlobalPermissions.admin, true, "system", userSubject = "root")
                )
            }
        }
        log.info { "main module initialized" }
    }

    override fun shutdown() {
        log.info { "main module stopped" }
    }

    private fun permissions() {
        val global: Permissions.Global = get()
        val server: Permissions.Server = get()

        with(GlobalPermissions) {
            global.allowsAlso(searchGroup, groupList)
            global.allowsAlso(searchUser, userList)
        }

        with(ServerPermissions) {
            server.allowsAlso(info, status, getId)
            server.allowsAlso(manage, start, stop, pause, resume, kill, status)
            server.allowsAlso(log, output, errors)
            server.allowsAlso(io, input, output, errors)
            server.allowsAlso(access, check, permList, grant, revoke)
            server.allowsAlso(
                owner, access, manage, io, log, permList, check, info, getId, upgrade, chDesc, revoke, grant, remove,
                send, input, errors, output, resume, pause, kill, stop, start, status, event
            )
        }

        val config = config

        if (config.admin.password.isNotEmpty()) {
            launch {
                events.catch<MCSManLifeEvent> { it.direction }
                val hasPassword = suspendTransaction(db) {
                    !(LoginMethodTable innerJoin UserTable)
                        .select { (UserTable.name eq "root") and (LoginMethodTable.method eq "password") }.empty()
                }
                if (hasPassword) {
                    log.verbose { "password for root was not created because it already exists" }
                } else {
                    log.info { "creating root password..." }
                    val data = PasswordHashStrategy.default.hash(config.admin.password)
                    log.info { "password created" }
                    events.raise(
                        LoginMethodEvent(
                            "password", PasswordHashStrategy.defaultName,
                            LoginMethodEvent.Data(data), null, "root"
                        )
                    )
                    log.info {
                        "created password for root user, consider remove it from configuration as it doesn't need anymore"
                    }
                }
            }
        }
    }

    suspend fun changePassword(user: User, password: String) {
        val table = LoginMethodTable
        suspendTransaction(db) {
            table.select { (table.user eq user.id) and (table.method eq "password") }.limit(1).firstOrNull()
        }?.let {
            events.raise(
                LoginMethodEvent(
                    "password", it[table.algorithm], LoginMethodEvent.Data(it[table.data]),
                    it[table.expiryDate]?.let { d -> SessionsFunctionality.date2long(d) }, user.name, false
                )
            )
            events.raise(
                LoginMethodEvent(
                    "password", PasswordHashStrategy.defaultName,
                    LoginMethodEvent.Data(PasswordHashStrategy.default.hash(password)), null, user.name
                )
            )
        }
    }

    override fun onConnection(ts: ModulePaketTransmitter) {
        launch(Dispatchers.Default + ts.session.agent) {
            ts.replyAll<Paket> {
                when (id) {
                    MCSManPaketID.GetPKey ->
                        GetPKeyPaket.Response(
                            "RSA4096", keysInfo.expiryDate.epochSecond, keysInfo.publicKey.encoded
                        )
                    MCSManPaketID.ChangePassword -> {
                        try {
                            val paket = peek(ChangePasswordPaket.Request)
                            val agent = getAgent()
                            val actor = agent.represent
                            when {
                                paket.agent != AgentTypes.User ->
                                    ChangePasswordPaket.Response(
                                        ChangePasswordPaket.Response.Codes.Unsupported,
                                        "currently only user password can be changed"
                                    )
                                actor is User && (paket.actor == 0 || paket.actor == actor.id) -> {
                                    if (accesses.global.isAllowed(GlobalPermissions.chSelfPasswd) ||
                                        accesses.global.isAllowed(GlobalPermissions.chPasswd)
                                    ) {
                                        changePassword(actor, paket.password)
                                        ChangePasswordPaket.Response.Success
                                    } else {
                                        ChangePasswordPaket.Response(
                                            ChangePasswordPaket.Response.Codes.AccessDenied, ""
                                        )
                                    }
                                }
                                else -> {
                                    if (accesses.global.isAllowed(GlobalPermissions.chPasswd)) {
                                        val user = users.get(paket.actor)
                                        changePassword(user, paket.password)
                                        ChangePasswordPaket.Response.Success
                                    } else {
                                        ChangePasswordPaket.Response(
                                            ChangePasswordPaket.Response.Codes.AccessDenied, ""
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            ChangePasswordPaket.Response(
                                ChangePasswordPaket.Response.Codes.Unknown,
                                e.message.orEmpty()
                            )
                        }
                    }
                }
            }
        }
    }
}
