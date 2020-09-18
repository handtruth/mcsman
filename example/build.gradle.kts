allprojects {
    group = "com.example"
    version = "0.0.1"
    repositories {
        mavenLocal()
        maven("https://mvn.handtruth.com")
        maven("https://dl.bintray.com/pdvrieze/maven")
        jcenter()
    }
}
