plugins {
    kotlin("plugin.serialization")
}

dependencies {
    fun kotlinx(name: String) = "org.jetbrains.kotlinx:kotlinx-$name"

    api(kotlinx("serialization-runtime"))
    api(project(":mcsman-base"))

    kapt(project(":mcsman-compiler-event"))
}
