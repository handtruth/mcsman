dependencies {
    fun kotlinx(name: String) = "org.jetbrains.kotlinx:kotlinx-$name"

    implementation(project(":mcsman-compiler-base"))

    implementation(kotlin("reflect"))

    implementation(project(":mcsman-protocol"))

    implementation(kotlinx("serialization-runtime"))

    compileOnly("com.google.auto.service:auto-service-annotations")
    kapt("com.google.auto.service:auto-service")
}
