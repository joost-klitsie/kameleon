plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("dev.klitsie.kameleon")
}

kotlin {
    jvm()

    sourceSets {
        jvmMain.dependencies {
            implementation(project(":sample:shared-app"))
            implementation(compose.desktop.currentOs)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
        }
    }
}

kameleon {
    defaultBuildConfig {
        packageName.set("dev.klitsie.kameleon.sample")
        className.set("AppConfig")

        "baseUrl" to "https://api.example.com"
        "enableAnalytics" to true
        this["timeoutSeconds"] = 30
    }

    dimension("brand") {
        flavors {
            register("whoop")
            register("flappy")
        }
    }
    dimension("environment") {
        flavor("staging") {
            buildConfig {
                "baseUrl" to "https://api.staging.example.com"
                "enableAnalytics" to false
            }
        }
        flavor("production")
    }
}

compose.desktop {
    application {
        mainClass = "MainKt"
    }
}
