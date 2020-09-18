plugins {
    kotlin("plugin.serialization")
}

dependencies {
    fun jetbrains(name: String) = "org.jetbrains.$name"
    fun kotlinx(name: String) = jetbrains("kotlinx:kotlinx-$name")
    fun handtruth(name: String) = "com.handtruth.$name"

    api(project(":mcsman-base"))
    api(project(":mcsman-event"))
    api(handtruth("mc:tools-chat"))
    api(handtruth("kommon:kommon-concurrent"))
}
