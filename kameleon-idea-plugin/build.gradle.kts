plugins {
    id("java")
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.intellijPlatform)
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdea("2026.2")
        bundledPlugin("com.intellij.gradle")
        bundledPlugin("com.intellij.java")
        plugin("org.jetbrains.android", "262.10968.63")
    }
    testImplementation(kotlin("test"))
}

intellijPlatform {
    pluginConfiguration {
        id = "dev.klitsie.kameleon.switcher"
        name = "Kameleon Variant Switcher"
        version = "1.0.0"
        vendor {
            name = "Joost Klitsie"
        }
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }
        }
    }
    publishing {
        token = providers.environmentVariable("INTELLIJ_PLATFORM_PUBLISH_TOKEN")
    }
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }
}
