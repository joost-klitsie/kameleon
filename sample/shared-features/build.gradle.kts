plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    id("dev.klitsie.kameleon")
}

kotlin {
    jvm()
    android {
        namespace = "dev.klitsie.kameleon.sample.features"
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

kameleon {
    defaultBuildConfig {
        packageName.set("dev.klitsie.kameleon.sample.features")
        className.set("FeatureConfig")

        "maxConcurrentUsers" to 1
        "tierLevel" to "Basic"
        "enableExperimentalAi" to false
        "billingPlan" to "STANDARD"
    }

    dimension("brand") {
        flavor("whoop") {
            buildConfig {
                "maxConcurrentUsers" to 500
                "tierLevel" to "Enterprise"
                "enableExperimentalAi" to true
                "billingPlan" to "CUSTOM_CONTRACT"
            }
        }
        flavor("flappy") {
            buildConfig {
                "maxConcurrentUsers" to 10
                "tierLevel" to "Community Starter"
                "enableExperimentalAi" to false
                "billingPlan" to "COMMUNITY_FREE"
            }
        }
    }
}
