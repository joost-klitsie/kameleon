plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("dev.klitsie.kameleon")
}

kotlin {
    jvm()
    android {
        namespace = "dev.klitsie.kameleon.sample.branding"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        androidResources.enable = true
    }
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
    }
    js {
        browser()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
        }
    }
}

kameleon {
    defaultBuildConfig {
        packageName.set("dev.klitsie.kameleon.sample.branding")
        className.set("BrandConfig")

        "brandName" to "Generic Brand"
        "brandCode" to "GEN"
        "supportEmail" to "support@example.com"
        "maxProjects" to 10
    }

    dimension("brand") {
        flavor("whoop") {
            buildConfig {
                "brandName" to "Whoop Enterprise"
                "brandCode" to "WHOOP"
                "supportEmail" to "enterprise@whoop.io"
                "maxProjects" to 1000
            }
        }
        flavor("flappy") {
            buildConfig {
                "brandName" to "Flappy Community"
                "brandCode" to "FLAP"
                "supportEmail" to "hello@flappy.dev"
                "maxProjects" to 25
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.klitsie.kameleon.sample.branding"
    generateResClass = auto
}
