plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    id("dev.klitsie.kameleon")
}

kotlin {
    jvm()
    android {
        namespace = "dev.klitsie.kameleon.sample.services"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
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
        }
    }
}
// KMP library module with Android, WasmJs, iOS and Jvm targets
kameleon {
    defaultBuildConfig {
        packageName.set("dev.klitsie.kameleon.sample.services")
        className.set("ServiceConfig")

        "apiEndpoint" to "https://api.kameleon.dev"
        "enableMockData" to false
        "requestTimeoutMs" to 10000
        "analyticsEnabled" to true
        "cacheTtlSeconds" to 3600L
    }

    dimension("brand") {
        flavor("whoop") {
            buildConfig {
                "requestTimeoutMs" to 20000
            }
        }
        flavor("flappy") {
            buildConfig {
                "requestTimeoutMs" to 5000
            }
        }
    }

    dimension("environment") {
        flavor("staging") {
            buildConfig {
                "apiEndpoint" to "https://staging-api.kameleon.dev/v1"
                "enableMockData" to true
                "analyticsEnabled" to false
                "cacheTtlSeconds" to 60L
            }
        }
        flavor("production") {
            buildConfig {
                "apiEndpoint" to "https://prod-api.kameleon.dev/v1"
                "enableMockData" to false
                "analyticsEnabled" to true
                "cacheTtlSeconds" to 86400L
            }
        }
    }
}
