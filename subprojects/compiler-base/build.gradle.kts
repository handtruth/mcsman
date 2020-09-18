dependencies {
    fun kotlinx(name: String) = "org.jetbrains.kotlinx:kotlinx-$name"
    fun kotlinpoet(name: String = "") = "com.squareup:kotlinpoet${if (name.isEmpty()) "" else "-"}$name"

    api(kotlinx("metadata-jvm"))

    api("com.handtruth.kommon:kommon-log")
    api("com.hendraanggrian:kotlinpoet-ktx")
    api(kotlinpoet())
    api(kotlinpoet("metadata"))
    api(kotlinpoet("metadata-specs"))
    api(kotlinpoet("classinspector-elements"))
}
