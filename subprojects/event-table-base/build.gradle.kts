dependencies {
    fun exposed(name: String) = "org.jetbrains.exposed:exposed-$name"
    fun kotlinx(name: String) = "org.jetbrains.kotlinx:kotlinx-$name"

    api(project(":mcsman-base"))

    api(exposed("core"))
    api(exposed("java-time"))
}