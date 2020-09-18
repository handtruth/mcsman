dependencies {
    fun exposed(name: String) = "org.jetbrains.exposed:exposed-$name"
    fun kotlinx(name: String) = "org.jetbrains.kotlinx:kotlinx-$name"

    implementation(kotlin("reflect"))

    implementation(project(":mcsman-core"))
    implementation(project(":mcsman-compiler-base"))
    runtimeOnly(project(":mcsman-compiler-event"))
    runtimeOnly(project(":mcsman-compiler-event-table"))

    implementation(kotlinx("serialization-runtime"))
    implementation("org.redundent:kotlin-xml-builder")

    compileOnly("com.google.auto.service:auto-service-annotations")
    kapt("com.google.auto.service:auto-service")
}
