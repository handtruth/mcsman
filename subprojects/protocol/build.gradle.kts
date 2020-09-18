plugins {
    kotlin("plugin.serialization")
}

dependencies {
    fun kotlinx(name: String) = "org.jetbrains.kotlinx:kotlinx-$name"
    fun handtruth(name: String) = "com.handtruth.$name"
    fun mctools(name: String) = handtruth("mc:tools-$name")

    api(project(":mcsman-base"))
    implementation(mctools("nbt"))
    api(mctools("chat"))
    api(mctools("paket"))

    implementation(kotlin("reflect"))

    api("com.soywiz.korlibs.klock:klock")
}
