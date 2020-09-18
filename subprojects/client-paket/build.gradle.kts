dependencies {
    fun ktor(name: String) = "io.ktor:ktor-$name"
    fun handtruth(name: String) = "com.handtruth.$name"

    api(project(":mcsman-client"))
    implementation(project(":mcsman-protocol"))
    implementation(kotlin("reflect"))
    implementation(ktor("network-tls"))
}
