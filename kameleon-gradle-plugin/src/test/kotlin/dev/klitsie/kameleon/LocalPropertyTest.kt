package dev.klitsie.kameleon

import dev.klitsie.kameleon.dsl.KameleonExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalPropertyTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `localProperty reads existing key from existing local properties file`() {
        val localPropsFile = File(tempDir, "local.properties")
        localPropsFile.writeText(
            """
            server.host=10.0.0.1
            server.port=9090
            """.trimIndent()
        )

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply(KameleonPlugin::class.java)
        val extension = project.extensions.getByType(KameleonExtension::class.java)

        val hostProvider = extension.localProperty("server.host")
        assertTrue(hostProvider.isPresent)
        assertEquals("10.0.0.1", hostProvider.get())

        val hostWithDefault = extension.localProperty("server.host", "127.0.0.1")
        assertEquals("10.0.0.1", hostWithDefault.get())

        val portProvider = extension.localProperty("server.port")
        assertEquals("9090", portProvider.get())
    }

    @Test
    fun `localProperty handles missing key in existing local properties file`() {
        val localPropsFile = File(tempDir, "local.properties")
        localPropsFile.writeText("server.host=10.0.0.1\n")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply(KameleonPlugin::class.java)
        val extension = project.extensions.getByType(KameleonExtension::class.java)

        val missingProvider = extension.localProperty("missing.key")
        assertFalse(missingProvider.isPresent)
        assertNull(missingProvider.orNull)

        val missingWithDefault = extension.localProperty("missing.key", "fallback_value")
        assertTrue(missingWithDefault.isPresent)
        assertEquals("fallback_value", missingWithDefault.get())
    }

    @Test
    fun `localProperty handles missing local properties file gracefully without failing`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply(KameleonPlugin::class.java)
        val extension = project.extensions.getByType(KameleonExtension::class.java)

        val missingFileProvider = extension.localProperty("any.key")
        assertFalse(missingFileProvider.isPresent)
        assertNull(missingFileProvider.orNull)

        val missingFileWithDefault = extension.localProperty("any.key", "default_val")
        assertTrue(missingFileWithDefault.isPresent)
        assertEquals("default_val", missingFileWithDefault.get())
    }

    @Test
    fun `localProperty is accessible inside flavor and hoconConfig blocks`() {
        val localPropsFile = File(tempDir, "local.properties")
        localPropsFile.writeText("api.url=https://api.test.com\n")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.plugins.apply(KameleonPlugin::class.java)
        val extension = project.extensions.getByType(KameleonExtension::class.java)

        var flavorPropValue: String? = null
        var hoconPropValue: String? = null

        extension.flavor("staging") {
            val flavorProp = localProperty("api.url")
            flavorPropValue = flavorProp.get()

            hoconConfig {
                val hoconProp = localProperty("api.url", "fallback")
                hoconPropValue = hoconProp.get()
            }
        }

        assertEquals("https://api.test.com", flavorPropValue)
        assertEquals("https://api.test.com", hoconPropValue)
    }

    @Test
    fun `localProperty resolves from root project when subproject has no local properties`() {
        val rootPropsFile = File(tempDir, "local.properties")
        rootPropsFile.writeText("root.secret=root_secret_value\n")
        File(tempDir, "settings.gradle.kts").writeText("rootProject.name = \"test-root\"\n")

        val subprojectDir = File(tempDir, "subproject").apply { mkdirs() }
        val rootProject = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val subproject = ProjectBuilder.builder().withParent(rootProject).withProjectDir(subprojectDir).build()
        subproject.plugins.apply(KameleonPlugin::class.java)

        val extension = subproject.extensions.getByType(KameleonExtension::class.java)

        val rootProp = extension.localProperty("root.secret")
        assertTrue(rootProp.isPresent)
        assertEquals("root_secret_value", rootProp.get())
    }

    @Test
    fun `localProperty allows subproject to override root project local properties`() {
        val rootPropsFile = File(tempDir, "local.properties")
        rootPropsFile.writeText(
            """
            shared.key=from_root
            root.only=root_val
            """.trimIndent()
        )
        File(tempDir, "settings.gradle.kts").writeText("rootProject.name = \"test-root\"\n")

        val subprojectDir = File(tempDir, "subproject").apply { mkdirs() }
        val subPropsFile = File(subprojectDir, "local.properties")
        subPropsFile.writeText(
            """
            shared.key=from_subproject
            subproject.only=sub_val
            """.trimIndent()
        )

        val rootProject = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val subproject = ProjectBuilder.builder().withParent(rootProject).withProjectDir(subprojectDir).build()
        subproject.plugins.apply(KameleonPlugin::class.java)

        val extension = subproject.extensions.getByType(KameleonExtension::class.java)

        val sharedProp = extension.localProperty("shared.key")
        assertEquals("from_subproject", sharedProp.get())

        val rootOnlyProp = extension.localProperty("root.only")
        assertEquals("root_val", rootOnlyProp.get())

        val subOnlyProp = extension.localProperty("subproject.only")
        assertEquals("sub_val", subOnlyProp.get())
    }

    @Test
    fun `createLocalPropertyProvider resolves with custom projectDirectory and rootProjectDirectory providers`() {
        val projectDir = File(tempDir, "proj").apply { mkdirs() }
        File(projectDir, "local.properties").writeText("key1=val1_project\n")

        val rootDir = File(tempDir, "root").apply { mkdirs() }
        File(rootDir, "local.properties").writeText("key1=val1_root\nkey2=val2_root\n")

        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val projectDirectoryProvider = project.layout.dir(project.provider { projectDir })
        val rootProjectDirectoryProvider = project.layout.dir(project.provider { rootDir })

        val key1Provider = dev.klitsie.kameleon.dsl.createLocalPropertyProvider(
            project.providers,
            projectDirectoryProvider,
            rootProjectDirectoryProvider,
            "key1"
        )
        assertEquals("val1_project", key1Provider.get())

        val key2Provider = dev.klitsie.kameleon.dsl.createLocalPropertyProvider(
            project.providers,
            projectDirectoryProvider,
            rootProjectDirectoryProvider,
            "key2"
        )
        assertEquals("val2_root", key2Provider.get())
    }
}
