plugins {
    application
    kotlin("plugin.serialization")
    id("kotlinx-atomicfu")
}

application {
    mainClassName = "com.handtruth.mc.mcsman.server.MCSManCore"
}

distributions {
    main {
        contents {
            exclude {
                // exclude("com.kosprov.jargon2", "jargon2-native-ri-binaries-generic")
                it.name.startsWith("jargon2-native-ri-binaries-generic")
            }
        }
    }
}

dependencies {
    fun koin(name: String) = "org.koin:koin-$name"
    fun handtruth(name: String) = "com.handtruth.$name"
    fun kommon(name: String) = handtruth("kommon:kommon-$name")
    fun mctools(name: String) = handtruth("mc:tools-$name")
    fun jetbrains(name: String) = "org.jetbrains.$name"
    fun kotlinx(name: String) = jetbrains("kotlinx:kotlinx-$name")
    fun exposed(name: String) = jetbrains("exposed:exposed-$name")
    fun ktor(name: String) = "io.ktor:ktor-$name"

    api(project(":mcsman-protocol"))
    api(project(":mcsman-event"))
    api(project(":mcsman-event-table-base"))
    api(kommon("log"))
    api(kommon("delegates"))
    api(kommon("concurrent"))
    api(koin("core"))
    api(kotlinx("serialization-runtime"))
    api(kotlinx("coroutines-core"))
    api(kotlinx("collections-immutable"))

    api(exposed("core"))
    api(exposed("dao"))
    implementation(exposed("jdbc"))
    api(exposed("java-time"))

    implementation(kotlin("reflect"))
    val atomicfuVersion: String by project
    implementation("org.jetbrains.kotlinx:atomicfu:$atomicfuVersion")

    compileOnly("com.google.auto.service:auto-service-annotations")
    kapt("com.google.auto.service:auto-service")
    kapt(project(":mcsman-compiler-event-table"))

    implementation(koin("logger-slf4j"))
    implementation(ktor("network"))
    implementation("com.handtruth:docker-client:0.0.1")
    implementation(mctools("nbt"))
    implementation("com.charleskorn.kaml:kaml")
    implementation("net.devrieze:xmlutil")
    implementation("net.devrieze:xmlutil-serialization")
    implementation("com.zaxxer:HikariCP")
    implementation("org.reflections:reflections")
    implementation("ch.qos.logback:logback-classic")
    implementation(koin("logger-slf4j"))
    val jargon2Version = "1.1.1"
    implementation("com.kosprov.jargon2:jargon2-api:$jargon2Version")
    runtimeOnly("com.kosprov.jargon2:jargon2-native-ri-backend:$jargon2Version")

    // Drivers
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("mysql:mysql-connector-java")
    runtimeOnly("org.xerial:sqlite-jdbc")
}

tasks {
    val definitions by creating(WriteProperties::class) {
        outputFile = file("$buildDir/resources/main/META-INF/application.properties")
        comment = "Paket Gradle Plugin Properties"
        properties(
            "group" to "com.handtruth.mc",
            "version" to version,
            "name" to project.name
        )
    }
    getByName("processResources") {
        dependsOn(definitions)
    }
}
