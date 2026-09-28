plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("dev.klitsie.kameleon")
}

android {
    namespace = "dev.klitsie.kameleon.sample.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "dev.klitsie.kameleon.sample.android"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    flavorDimensions += "brand"
    flavorDimensions += "environment"
    productFlavors {
        create("whoop") {
            dimension = "brand"
        }
        create("flappy") {
            dimension = "brand"
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
        }
        create("production") {
            dimension = "environment"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":sample:shared-app"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.components.resources)
}
