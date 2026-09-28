plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    id("dev.klitsie.kameleon")
}

kotlin {
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "webApp.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(project(":sample:shared-app"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
        }
    }
}

kameleon {
    defaultBuildConfig {
        packageName.set("dev.klitsie.kameleon.sample.web")
        className.set("WebConfig")
        "targetPlatform" to "WasmJs Web"
    }
    dimension("environment") {
        flavor("staging") {
            buildConfig {
                "targetPlatform" to "WasmJs Web (Staging)"
            }
        }
        flavor("production") {
            buildConfig {
                "targetPlatform" to "WasmJs Web (Production)"
            }
        }
    }
}
