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
        namespace = "dev.klitsie.kameleon.sample"
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
        packageName.set("dev.klitsie.kameleon.sample.translations")
        className.set("TranslationConfig")

        "supportedLocalesCount" to 3
        "defaultLocale" to "en"
        "enableTranslationsLogging" to false
    }

    dimension("environment") {
        flavor("staging") {
            buildConfig {
                "supportedLocalesCount" to 3
                "defaultLocale" to "en-staging"
                "enableTranslationsLogging" to true
            }
        }
        flavor("production") {
            buildConfig {
                "supportedLocalesCount" to 3
                "defaultLocale" to "en"
                "enableTranslationsLogging" to false
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "dev.klitsie.kameleon.sample"
    generateResClass = auto
}
