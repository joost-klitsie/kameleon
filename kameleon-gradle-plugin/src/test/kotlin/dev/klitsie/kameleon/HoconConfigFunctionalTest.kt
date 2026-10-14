package dev.klitsie.kameleon

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HoconConfigFunctionalTest {

    @TempDir
    lateinit var testProjectDir: File

    @Test
    fun `hoconConfig generates conf on classpath, supports configuration cache, up-to-date checks and local properties invalidation`() {
        File(testProjectDir, "settings.gradle.kts").writeText(
            """
            rootProject.name = "functional-test-app"
            """.trimIndent()
        )

        val localPropsFile = File(testProjectDir, "local.properties")
        localPropsFile.writeText(
            """
            server.host=127.0.0.1
            """.trimIndent()
        )

        val buildFile = File(testProjectDir, "build.gradle.kts")
        buildFile.writeText(
            """
            plugins {
                `java`
                id("dev.klitsie.kameleon")
            }

            kameleon {
                flavor("local") {
                    hoconConfig {
                        fileName.set("kameleon.conf")

                        putString(
                            path = "server.host",
                            value = localProperty("server.host", "localhost"),
                            environmentOverride = "SERVER_HOST"
                        )

                        putInteger(
                            path = "server.port",
                            value = 8080,
                            environmentOverride = "SERVER_PORT"
                        )

                        putBoolean(
                            path = "server.debug",
                            value = true,
                            environmentOverride = null
                        )

                        putRaw(
                            path = "server.allowedHosts",
                            rawValue = "[\"localhost\", \"127.0.0.1\"]",
                            environmentOverride = null
                        )
                    }
                }
            }
            """.trimIndent()
        )

        // 1. Initial build: verify kameleon.conf is generated on classpath
        val result1 = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("processResources", "--configuration-cache")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result1.task(":generateKameleonLocalHoconConfig")?.outcome)
        assertEquals(TaskOutcome.SUCCESS, result1.task(":processResources")?.outcome)

        val generatedResource = File(testProjectDir, "build/resources/main/kameleon.conf")
        assertTrue(generatedResource.exists(), "kameleon.conf should be packaged on the classpath root")
        val content1 = generatedResource.readText()
        assertTrue(content1.contains("server.host = \"127.0.0.1\""))
        assertTrue(content1.contains("server.host = \${?SERVER_HOST}"))
        assertTrue(content1.contains("server.port = 8080"))
        assertTrue(content1.contains("server.port = \${?SERVER_PORT}"))
        assertTrue(content1.contains("server.debug = true"))
        assertTrue(content1.contains("server.allowedHosts = [\"localhost\", \"127.0.0.1\"]"))

        // 2. Second build without changes: verify UP-TO-DATE and CC reused
        val result2 = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("processResources", "--configuration-cache")
            .build()

        assertEquals(TaskOutcome.UP_TO_DATE, result2.task(":generateKameleonLocalHoconConfig")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, result2.task(":processResources")?.outcome)

        // 3. Update local.properties: verify configuration cache invalidation and regeneration
        localPropsFile.writeText(
            """
            server.host=192.168.1.100
            """.trimIndent()
        )

        val result3 = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withPluginClasspath()
            .withArguments("processResources", "--configuration-cache")
            .build()

        assertEquals(TaskOutcome.SUCCESS, result3.task(":generateKameleonLocalHoconConfig")?.outcome)

        val content3 = generatedResource.readText()
        assertTrue(content3.contains("server.host = \"192.168.1.100\""))
        assertTrue(content3.contains("server.host = \${?SERVER_HOST}"))
    }
}
