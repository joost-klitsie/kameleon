package dev.klitsie.kameleon

import dev.klitsie.kameleon.dsl.HoconEntry
import dev.klitsie.kameleon.dsl.HoconValue
import dev.klitsie.kameleon.dsl.KameleonExtension
import dev.klitsie.kameleon.tasks.GenerateHoconConfigTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateHoconConfigTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `hoconConfig block populates entries using literals and providers`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply(KameleonPlugin::class.java)
        val extension = project.extensions.getByType(KameleonExtension::class.java)

        extension.flavor("local") {
            hoconConfig {
                fileName.set("custom.conf")

                putString("server.host", "127.0.0.1", "SERVER_HOST")
                putString("server.domain", project.provider { "example.com" })

                putInteger("server.port", 8080, "SERVER_PORT")
                putInteger("server.timeout", project.provider { 30 })

                putInt("server.port2", 8081, "SERVER_PORT2")
                putInt("server.timeout2", project.provider { 40 })

                putBoolean("server.debug", true)
                putBoolean("server.ssl", project.provider { false })

                putRaw("server.allowedHosts", "[\"localhost\"]")
                putRaw("server.cors", project.provider { "[\"*\"]" }, "CORS_ORIGIN")
            }
        }

        val flavor = extension.dimension("environment").flavors.getByName("local")
        assertEquals("custom.conf", flavor.hoconConfig.fileName.get())

        val entries = flavor.hoconConfig.entries.get()
        assertEquals(10, entries.size)

        assertEquals(HoconEntry("server.host", HoconValue.StringValue("127.0.0.1"), "SERVER_HOST"), entries[0])
        assertEquals(HoconEntry("server.domain", HoconValue.StringValue("example.com"), null), entries[1])
        assertEquals(HoconEntry("server.port", HoconValue.IntegerValue(8080), "SERVER_PORT"), entries[2])
        assertEquals(HoconEntry("server.timeout", HoconValue.IntegerValue(30), null), entries[3])
        assertEquals(HoconEntry("server.port2", HoconValue.IntegerValue(8081), "SERVER_PORT2"), entries[4])
        assertEquals(HoconEntry("server.timeout2", HoconValue.IntegerValue(40), null), entries[5])
        assertEquals(HoconEntry("server.debug", HoconValue.BooleanValue(true), null), entries[6])
        assertEquals(HoconEntry("server.ssl", HoconValue.BooleanValue(false), null), entries[7])
        assertEquals(HoconEntry("server.allowedHosts", HoconValue.RawValue("[\"localhost\"]"), null), entries[8])
        assertEquals(HoconEntry("server.cors", HoconValue.RawValue("[\"*\"]"), "CORS_ORIGIN"), entries[9])
    }

    @Test
    fun `generate hocon config task writes formatted conf file`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("generateHocon", GenerateHoconConfigTask::class.java).get()

        val outputDir = File(tempDir, "build/generated/kameleon/hocon/local")
        task.fileName.set("kameleon.conf")
        task.outputDirectory.set(outputDir)

        task.entries.add(HoconEntry("server.host", HoconValue.StringValue("127.0.0.1"), "SERVER_HOST"))
        task.entries.add(HoconEntry("server.port", HoconValue.IntegerValue(8080), "SERVER_PORT"))
        task.entries.add(HoconEntry("server.debug", HoconValue.BooleanValue(true), null))
        task.entries.add(HoconEntry("server.allowedHosts", HoconValue.RawValue("[\"localhost\", \"127.0.0.1\"]"), null))

        task.generate()

        val generatedConf = File(outputDir, "kameleon.conf")
        assertTrue(generatedConf.exists(), "kameleon.conf must exist")

        val content = generatedConf.readText()
        val expected = """
            server.host = "127.0.0.1"
            server.host = ${'$'}{?SERVER_HOST}
            server.port = 8080
            server.port = ${'$'}{?SERVER_PORT}
            server.debug = true
            server.allowedHosts = ["localhost", "127.0.0.1"]

        """.trimIndent()

        assertEquals(expected, content)
    }
}
