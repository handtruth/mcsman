dependencies {
    fun exposed(name: String) = "org.jetbrains.exposed:exposed-$name"
    fun kotlinx(name: String) = "org.jetbrains.kotlinx:kotlinx-$name"

    implementation(kotlin("reflect"))

    implementation(project(":mcsman-compiler-base"))
    implementation(project(":mcsman-event-table-base"))
    runtimeOnly(project(":mcsman-compiler-event"))

    implementation(exposed("core"))
    implementation(exposed("java-time"))

    implementation(kotlinx("serialization-runtime"))
    implementation("org.redundent:kotlin-xml-builder")

    compileOnly("com.google.auto.service:auto-service-annotations")
    kapt("com.google.auto.service:auto-service")
}
