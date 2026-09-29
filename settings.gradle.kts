@file:Suppress("UnstableApiUsage")

rootProject.name = "kameleon"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

includeBuild("kameleon-gradle-plugin")
includeBuild("kameleon-idea-plugin")

include(":sample:shared-translations")
include(":sample:shared-branding")
include(":sample:shared-services")
include(":sample:shared-features")
include(":sample:shared-app")
include(":sample:desktopApp")
include(":sample:androidApp")
include(":sample:androidApp2")
include(":sample:androidApp3")
include(":sample:webApp")
