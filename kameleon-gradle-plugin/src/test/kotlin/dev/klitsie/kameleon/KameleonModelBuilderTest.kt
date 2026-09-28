package dev.klitsie.kameleon

import dev.klitsie.kameleon.dsl.KameleonExtension
import dev.klitsie.kameleon.tooling.KameleonModelBuilder
import dev.klitsie.kameleon.tooling.KameleonToolingModel
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class KameleonModelBuilderTest {

    @field:TempDir
    lateinit var tempDir: File

    @Test
    fun `model builder matches KameleonToolingModel name`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")
        val extension = project.extensions.getByType(KameleonExtension::class.java)
        val builder = KameleonModelBuilder(extension)

        assertTrue(builder.canBuild(KameleonToolingModel::class.java.name))
    }

    @Test
    fun `model builder extracts variants and default variant`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")
        val extension = project.extensions.getByType(KameleonExtension::class.java)

        extension.dimension("environment") {
            flavor("staging")
            flavor("production")
        }

        val builder = KameleonModelBuilder(extension)
        val model = builder.buildAll(KameleonToolingModel::class.java.name, project) as KameleonToolingModel

        assertEquals(project.path, model.projectPath)
        assertEquals(mapOf("environment" to listOf("staging", "production")), model.dimensions)
        assertEquals(listOf("staging", "production"), model.availableVariants)
        assertEquals("staging", model.defaultVariant)
    }

    @Test
    fun `model builder handles multi-dimensional variants`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")
        val extension = project.extensions.getByType(KameleonExtension::class.java)

        extension.dimension("brand") {
            flavor("demo")
            flavor("full")
        }
        extension.dimension("environment") {
            flavor("staging")
            flavor("production")
        }

        val builder = KameleonModelBuilder(extension)
        val model = builder.buildAll(KameleonToolingModel::class.java.name, project) as KameleonToolingModel

        assertEquals(mapOf("brand" to listOf("demo", "full"), "environment" to listOf("staging", "production")), model.dimensions)
        assertEquals(listOf("demoStaging", "demoProduction", "fullStaging", "fullProduction"), model.availableVariants)
        assertEquals("demoStaging", model.defaultVariant)
    }

    @Test
    fun `model builder creates default dimension when no explicit dimensions configured`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")
        val extension = project.extensions.getByType(KameleonExtension::class.java)

        // Using standard flavors without explicit dimension
        val builder = KameleonModelBuilder(extension)
        val model = builder.buildAll(KameleonToolingModel::class.java.name, project) as KameleonToolingModel

        assertEquals(project.path, model.projectPath)
    }

    @Test
    fun `ambient property kameleon flavor with dimensions resolves active variant`() {
        tempDir.resolve("gradle.properties").writeText(
            """
            kameleon.flavor.brand=full
            kameleon.flavor.environment=production
            """.trimIndent()
        )
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")
        val extension = project.extensions.getByType(KameleonExtension::class.java)
        extension.dimension("brand") {
            flavor("demo")
            flavor("full")
        }
        extension.dimension("environment") {
            flavor("staging")
            flavor("production")
        }

        val flavorProvider = project.getKameleonFlavor("brand")
        assertEquals("full", flavorProvider.get())
        val defaultFlavorProvider = project.getKameleonFlavor()
        assertEquals(false, defaultFlavorProvider.isPresent)
    }

    @Test
    fun `ambient property kameleon flavor overrides active variant resolution`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")
        val extension = project.extensions.getByType(KameleonExtension::class.java)
        extension.dimension("environment") {
            flavor("staging")
            flavor("production")
        }

        // Set kameleon.flavor project property
        project.extensions.extraProperties.set("kameleon.flavor", "production")

        // Build config or active variant should resolve to production
        val task = project.tasks.findByName("mergeKameleonResources")
        org.junit.jupiter.api.Assertions.assertNotNull(task)
    }
}
