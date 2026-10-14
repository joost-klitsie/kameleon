plugins {
    alias(libs.plugins.kotlinJvm)
    id("dev.klitsie.kameleon")
    application
}

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test)
}

kameleon {
    dimension("environment") {
        defaultBuildConfig {
            putString("marco", "polo!")
        }
        flavors {
            register("staging") {
                hoconConfig {
                    putInteger("ktor.deployment.port", 8080)
                    putString("app.greeting", localProperty("testValue.staging", "greeting is missing from local.properties :("))
                }
            }
            register("production") {
                hoconConfig {
                    putInteger("ktor.deployment.port", 8081, environmentOverride = "PORT")
                    putString("app.greeting", "world (production)", environmentOverride = "APP_GREETING")
                }
            }
        }
    }
}
