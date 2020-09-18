plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    id("com.gradle.plugin-publish")
    jacoco
}

group = rootProject.group
version = rootProject.version

val pluginPrefix = "com.handtruth.mc.mcsman"

dependencies {
    implementation(kotlin("gradle-plugin"))
    implementation(kotlin("serialization"))
    implementation("org.redundent:kotlin-xml-builder")
    implementation(project(":mcsman-core"))
}

gradlePlugin {
    plugins {
        create("MCSPackBundlePlugin") {
            id = "$pluginPrefix.bundle"
            displayName = "MCSPackBundlePlugin"
            description = "MCSMan Bundle Dev Tools"
            implementationClass = "com.handtruth.mc.mcsman.gradle.MCSPackBundlePlugin"
        }
        create("MCSPackCommonPlugin") {
            id = "$pluginPrefix.common"
            displayName = "MCSPackCommonPlugin"
            description = "MCSMan Common Dev Libs"
            implementationClass = "com.handtruth.mc.mcsman.gradle.MCSPackCommonPlugin"
        }
        create("MCSPackClientGUIPlugin") {
            id = "$pluginPrefix.client-gui"
            displayName = "MCSPackClientGUIPlugin"
            description = "MCSMan Client GUI Dev Tools"
            implementationClass = "com.handtruth.mc.mcsman.gradle.MCSPackClientGUIPlugin"
        }
        create("MCSPackClientHTTPPlugin") {
            id = "$pluginPrefix.client-http"
            displayName = "MCSPackClientHTTPPlugin"
            description = "MCSMan HTTP Client Dev Tools"
            implementationClass = "com.handtruth.mc.mcsman.gradle.MCSPackClientHTTPPlugin"
        }
        create("MCSPackClientPaketPlugin") {
            id = "$pluginPrefix.client-paket"
            displayName = "MCSPackClientPaketPlugin"
            description = "MCSMan Paket Client Dev Tools"
            implementationClass = "com.handtruth.mc.mcsman.gradle.MCSPackClientPaketPlugin"
        }
        create("MCSPackClientPlugin") {
            id = "$pluginPrefix.client"
            displayName = "MCSPackClientPlugin"
            description = "MCSMan Client Interface Dev Tools"
            implementationClass = "com.handtruth.mc.mcsman.gradle.MCSPackClientPlugin"
        }
        create("MCSPackMCSBotPlugin") {
            id = "$pluginPrefix.mcsbot"
            displayName = "MCSPackMCSBotPlugin"
            description = "MCSMan Discord Bot Extension Dev Tools"
            implementationClass = "com.handtruth.mc.mcsman.gradle.MCSPackMCSBotPlugin"
        }
        create("MCSPackMCSDroidPlugin") {
            id = "$pluginPrefix.mcsdroid"
            displayName = "MCSPackMCSDroidPlugin"
            description = "MCSMan Android Application Dev Tools"
            implementationClass = "com.handtruth.mc.mcsman.gradle.MCSPackMCSDroidPlugin"
        }
        create("MCSPackMCSWebPlugin") {
            id = "$pluginPrefix.mcsweb"
            displayName = "MCSPackMCSWebPlugin"
            description = "MCSMan MCSWeb Service Application Dev Tools"
            implementationClass = "com.handtruth.mc.mcsman.gradle.MCSPackMCSWebPlugin"
        }
        create("MCSPackProtocolPlugin") {
            id = "$pluginPrefix.protocol"
            displayName = "MCSPackProtocolPlugin"
            description = "MCSMan Paket Protocol Dev Tools"
            implementationClass = "com.handtruth.mc.mcsman.gradle.MCSPackProtocolPlugin"
        }
    }
}

pluginBundle {
    website = "http://mc.handtruth.com/"
    vcsUrl = "https://github.com/handtruth/mcsman.git"
}

tasks {
    val definitions by creating(WriteProperties::class) {
        outputFile = file("$buildDir/resources/main/META-INF/mcsman-module-plugin.properties")
        comment = "Paket Gradle Plugin Properties"
        val platformVersion: String by project
        properties(
            "group" to "com.handtruth.mc",
            "version" to version,
            "name" to project.name,
            "handtruthPlatformVersion" to platformVersion
        )
    }
    getByName("processResources") {
        dependsOn(definitions)
    }
}
