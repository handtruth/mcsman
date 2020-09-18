plugins {
    kotlin("plugin.serialization")
}

dependencies {
    fun jetbrains(name: String) = "org.jetbrains.$name"
    fun kotlinx(name: String) = jetbrains("kotlinx:kotlinx-$name")
    fun handtruth(name: String) = "com.handtruth.$name"
    fun kommon(name: String) = handtruth("kommon:kommon-$name")

    implementation(kotlin("reflect"))
    api(kotlinx("serialization-runtime"))
    api(kotlinx("coroutines-core"))
    api(kommon("state"))
}
