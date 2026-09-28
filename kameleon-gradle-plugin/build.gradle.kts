plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    alias(libs.plugins.mavenPublish)
}

group = "dev.klitsie.kameleon"
version = "1.0.0"

kotlin {
    jvmToolchain(17)
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
    }
}

gradlePlugin {
    plugins {
        register("kameleon") {
            id = "dev.klitsie.kameleon"
            implementationClass = "dev.klitsie.kameleon.KameleonPlugin"
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    coordinates(group.toString(), "kameleon-gradle-plugin", version.toString())
    pom {
        name.set("kameleon-gradle-plugin")
        description.set("Flavor and variant management toolchain for Kotlin Multiplatform and Compose Multiplatform projects")
        inceptionYear.set("2026")
        url.set("https://github.com/joost-klitsie/kameleon/")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("joost-klitsie")
                name.set("Joost klitsie")
                url.set("https://github.com/joost-klitsie")
                email.set("j.p.klitsie@gmail.com")
                organization.set("Klitsie Development")
                organizationUrl.set("https://klitsie.dev")
            }
        }
        scm {
            url.set("https://github.com/joost-klitsie/kameleon/")
            connection.set("scm:git:git://github.com/joost-klitsie/kameleon.git")
            developerConnection.set("scm:git:ssh://git@github.com/joost-klitsie/kameleon.git")
        }
    }
}

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

dependencies {
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.android.gradlePlugin)

    testImplementation(libs.kotlin.test)
    testImplementation(gradleTestKit())
    testImplementation(libs.kotlin.gradlePlugin)
    testImplementation(libs.compose.gradlePlugin)
    testImplementation(libs.android.gradlePlugin)
}

tasks.test {
    useJUnitPlatform()
}
