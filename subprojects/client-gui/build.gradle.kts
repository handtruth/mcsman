plugins {
    application
}

application {
    mainClassName = "com.handtruth.mc.mcsman.client.gui.MCSManClientApp"
}

dependencies {
    fun handtruth(name: String) = "com.handtruth.$name"
    fun kommon(name: String) = handtruth("kommon:kommon-$name")
    fun kotlinx(name: String) = "org.jetbrains.kotlinx:kotlinx-$name"
    fun ktor(name: String) = "io.ktor:ktor-$name"

    implementation(project(":mcsman-client-paket"))
    implementation(kommon("log"))
    implementation(kotlinx("coroutines-javafx"))
    implementation(ktor("network-tls"))
    implementation("no.tornado:tornadofx")
}
