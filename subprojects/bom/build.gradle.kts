plugins {
    `java-platform`
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("bom") {
            from(components["javaPlatform"])
        }
    }
}

val mcsmanLibs: List<String> by rootProject.extra

dependencies {
    constraints {
        for (lib in mcsmanLibs) {
            if (lib != "module-minecraft-server")
                api(project(":mcsman-$lib"))
        }
    }
}
