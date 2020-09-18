import com.handtruth.mc.mcsman.gradle.MCSPackExtension

plugins {
    id("com.handtruth.mc.mcsman.bundle")
}

group = "com.example"
version = "0.0.1"

mcspack {
    artifact(type = MCSPackExtension.Types.Client, source = project(":example-client"))
    artifact(type = MCSPackExtension.Types.Common, source = project(":example-common"))
}

dependencies {
    implementation("com.handtruth.mc:mcsman-module-minecraft-server")
    mcsmanBundle("com.handtruth.mc:mcsman-module-minecraft-server")
    api(project(":example-common"))
}
