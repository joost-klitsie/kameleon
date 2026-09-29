package dev.klitsie.kameleon

import dev.klitsie.kameleon.dsl.KameleonExtension
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class KameleonFlavorResolutionTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `resolveFlavorForDimension resolves dimension property when present`() {
        File(tempDir, "gradle.properties").writeText(
            """
            kameleon.flavor.brand=flappy
            kameleon.flavor=fallbackFlavor
            """.trimIndent()
        )
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

        val resolved = project.resolveFlavorForDimension("brand")
        assertEquals("flappy", resolved.get())
    }

    @Test
    fun `resolveFlavorForDimension falls back to global kameleon flavor when dimension property is absent`() {
        File(tempDir, "gradle.properties").writeText(
            """
            kameleon.flavor=globalFlavor
            """.trimIndent()
        )
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

        val resolved = project.resolveFlavorForDimension("brand")
        assertEquals("globalFlavor", resolved.get())
    }

    @Test
    fun `resolveDefaultFlavor resolves dimensionless property`() {
        File(tempDir, "gradle.properties").writeText(
            """
            kameleon.flavor=dev
            """.trimIndent()
        )
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()

        val resolved = project.resolveDefaultFlavor()
        assertEquals("dev", resolved.get())
    }

    @Test
    fun `library module declaring only brand dimension resolves from per-dimension property in multi-dimension invocation`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")

        // Multi-dimension properties passed via CLI
        project.extensions.extraProperties.set("kameleon.flavor.brand", "flappy")
        project.extensions.extraProperties.set("kameleon.flavor.environment", "production")

        val extension = project.extensions.getByType(KameleonExtension::class.java)
        // Library module defines ONLY brand dimension
        extension.dimension("brand", "flappy", "whoop")

        val activeVariant = resolveActiveVariant(project, extension)
        assertEquals("flappy", activeVariant)
    }

    @Test
    fun `app module declaring multiple dimensions resolves composite variant from per-dimension properties`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")

        project.extensions.extraProperties.set("kameleon.flavor.brand", "flappy")
        project.extensions.extraProperties.set("kameleon.flavor.environment", "production")

        val extension = project.extensions.getByType(KameleonExtension::class.java)
        extension.dimension("brand", "flappy", "whoop")
        extension.dimension("environment", "staging", "production")

        val activeVariant = resolveActiveVariant(project, extension)
        assertEquals("flappyProduction", activeVariant)
    }

    @Test
    fun `single dimension module falls back to dimensionless kameleon flavor when per-dimension property is not passed`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")

        project.extensions.extraProperties.set("kameleon.flavor", "staging")

        val extension = project.extensions.getByType(KameleonExtension::class.java)
        extension.dimension("environment", "staging", "production")

        val activeVariant = resolveActiveVariant(project, extension)
        assertEquals("staging", activeVariant)
    }

    @Test
    fun `dimension registration preserves order of occurrence`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")

        val extension = project.extensions.getByType(KameleonExtension::class.java)
        extension.dimension("target", "phone", "tablet")
        extension.dimension("environment", "staging", "production")

        val orderedDims = extension.getOrderedDimensions().map { it.name }
        assertEquals(listOf("target", "environment"), orderedDims)

        val variants = VariantMatrix.calculateVariants(extension.getOrderedDimensions())
        assertEquals(listOf("phoneStaging", "phoneProduction", "tabletStaging", "tabletProduction"), variants.map { it.name })
    }
}
