package dev.klitsie.kameleon

import dev.klitsie.kameleon.tasks.MergeComposeResourcesTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MergeComposeResourcesTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `plugin registers extension, dimensions, and merge task`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")

        val extension = project.extensions.findByType(KameleonExtension::class.java)!!
        assertEquals("default", extension.defaultVariant.get())

        extension.flavors("staging", "production")
        assertEquals(setOf("staging", "production"), extension.flavors.names)
        assertTrue(extension.dimensions.names.contains("environment"))

        val task = project.tasks.findByName("mergeKameleonResources") as? MergeComposeResourcesTask
        assertTrue(task != null)
    }

    @Test
    fun `variant matrix computes Cartesian product and names correctly`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val extension = project.extensions.create("kameleon", KameleonExtension::class.java)

        val brandDim = extension.dimensions.maybeCreate("brand")
        brandDim.flavors("demo", "full")

        val envDim = extension.dimensions.maybeCreate("environment")
        envDim.flavors("staging", "production")

        val variants = VariantMatrix.calculateVariants(extension.getOrderedDimensions())
        assertEquals(4, variants.size)
        assertEquals(
            listOf(
                VariantCombination("demoStaging", listOf("demo", "staging")),
                VariantCombination("demoProduction", listOf("demo", "production")),
                VariantCombination("fullStaging", listOf("full", "staging")),
                VariantCombination("fullProduction", listOf("full", "production"))
            ),
            variants
        )
    }

    @Test
    fun `dsl supports dimension block and register helpers`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val extension = project.extensions.create("kameleon", KameleonExtension::class.java)

        extension.dimension("brand") {
            register("demo", "full")
        }
        extension.dimension("environment") {
            register("staging", "prod")
        }

        val variants = VariantMatrix.calculateVariants(extension.getOrderedDimensions())
        assertEquals(4, variants.size)
        assertEquals(
            listOf("demoStaging", "demoProd", "fullStaging", "fullProd"),
            variants.map { it.name }
        )
    }

    @Test
    fun `task merges strings xml with multi-layer overlays and priority`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("mergeResources", MergeComposeResourcesTask::class.java).get()

        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val demoDir = File(tempDir, "demo").apply { mkdirs() }
        val stagingDir = File(tempDir, "staging").apply { mkdirs() }
        val demoStagingDir = File(tempDir, "demoStaging").apply { mkdirs() }
        val outputDir = File(tempDir, "output").apply { mkdirs() }

        // Base strings
        File(baseDir, "values").mkdirs()
        File(baseDir, "values/strings.xml").writeText(
            """
            <resources>
                <string name="app_title">Base Title</string>
                <string name="greeting">Hello Base</string>
                <string name="base_only">Base Value</string>
            </resources>
            """.trimIndent()
        )

        // Demo layer
        File(demoDir, "values").mkdirs()
        File(demoDir, "values/strings.xml").writeText(
            """
            <resources>
                <string name="app_title">Demo Title</string>
                <string name="demo_badge">Demo Mode</string>
            </resources>
            """.trimIndent()
        )

        // Staging layer
        File(stagingDir, "values").mkdirs()
        File(stagingDir, "values/strings.xml").writeText(
            """
            <resources>
                <string name="greeting">Hello Staging</string>
                <string name="server_url">https://staging.api.com</string>
            </resources>
            """.trimIndent()
        )

        // Variant layer (highest priority)
        File(demoStagingDir, "values").mkdirs()
        File(demoStagingDir, "values/strings.xml").writeText(
            """
            <resources>
                <string name="app_title">Demo Staging Title</string>
            </resources>
            """.trimIndent()
        )

        task.baseDir.set(baseDir)
        task.overlayDirs.set(listOf(demoDir, stagingDir, demoStagingDir).map {
            project.layout.projectDirectory.dir(it.absolutePath)
        })
        task.outputDir.set(outputDir)

        task.merge()

        val mergedDefaultXml = File(outputDir, "values/strings.xml").readText()
        assertTrue(mergedDefaultXml.contains("""<string name="app_title">Demo Staging Title</string>"""))
        assertTrue(mergedDefaultXml.contains("""<string name="greeting">Hello Staging</string>"""))
        assertTrue(mergedDefaultXml.contains("""<string name="base_only">Base Value</string>"""))
        assertTrue(mergedDefaultXml.contains("""<string name="demo_badge">Demo Mode</string>"""))
        assertTrue(mergedDefaultXml.contains("""<string name="server_url">https://staging.api.com</string>"""))
    }

    @Test
    fun `task overwrites non-xml assets and cleans stale outputs`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("mergeResources", MergeComposeResourcesTask::class.java).get()

        val baseDir = File(tempDir, "base").apply { mkdirs() }
        val flavorDir = File(tempDir, "flavor").apply { mkdirs() }
        val outputDir = File(tempDir, "output").apply { mkdirs() }

        val baseDrawableDir = File(baseDir, "drawable").apply { mkdirs() }
        File(baseDrawableDir, "icon.png").writeBytes(byteArrayOf(1, 2, 3))

        val flavorDrawableDir = File(flavorDir, "drawable").apply { mkdirs() }
        File(flavorDrawableDir, "icon.png").writeBytes(byteArrayOf(4, 5, 6))

        // Create a stale output file
        val staleFile = File(outputDir, "stale.txt")
        staleFile.writeText("stale content")

        task.baseDir.set(baseDir)
        task.overlayDirs.set(listOf(project.layout.projectDirectory.dir(flavorDir.absolutePath)))
        task.outputDir.set(outputDir)

        task.merge()

        assertFalse(staleFile.exists())
        val outputIcon = File(outputDir, "drawable/icon.png")
        assertTrue(outputIcon.exists())
        assertEquals(listOf<Byte>(4, 5, 6), outputIcon.readBytes().toList())
    }

    @Test
    fun `task matrix generation registers tasks for variant combinations`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("dev.klitsie.kameleon")

        val kotlin = project.extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)!!
        kotlin.jvm()
        kotlin.wasmJs { browser() }

        val extension = project.extensions.findByType(KameleonExtension::class.java)!!
        val brand = extension.dimensions.maybeCreate("brand")
        brand.flavors("demo", "full")
        val env = extension.dimensions.maybeCreate("environment")
        env.flavors("staging", "production")

        project.tasks.register("run")

        // Trigger afterEvaluate actions in test fixture
        val evaluateMethod = project.javaClass.getMethod("evaluate")
        evaluateMethod.invoke(project)

        assertTrue(project.tasks.findByName("runDemoStaging") != null)
        assertTrue(project.tasks.findByName("runDemoStagingRelease") != null)
        assertTrue(project.tasks.findByName("runDemoProduction") != null)
        assertTrue(project.tasks.findByName("runDemoProductionRelease") != null)
        assertTrue(project.tasks.findByName("runFullStaging") != null)
        assertTrue(project.tasks.findByName("runFullStagingRelease") != null)
        assertTrue(project.tasks.findByName("runFullProduction") != null)
        assertTrue(project.tasks.findByName("runFullProductionRelease") != null)
        assertTrue(project.tasks.findByName("runWasmJsDemoStaging") != null)
        assertTrue(project.tasks.findByName("runWasmJsDemoStagingRelease") != null)
        assertTrue(project.tasks.findByName("wasmJsDemoStagingBrowserDistribution") != null)
        assertTrue(project.tasks.findByName("wasmJsFullProductionBrowserDistribution") != null)
    }

    @Test
    fun `variant matrix falls back to single default variant when no dimensions configured`() {
        val variants = VariantMatrix.calculateVariants(emptyList())
        assertEquals(1, variants.size)
        assertEquals("default", variants.first().name)
        assertEquals(emptyList(), variants.first().flavorNames)
    }

    @Test
    fun `flat flavors automatically group under environment dimension`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val extension = project.extensions.create("kameleon", KameleonExtension::class.java)

        extension.flavors("staging", "production")

        assertEquals(1, extension.getOrderedDimensions().size)
        assertEquals("environment", extension.getOrderedDimensions().first().name)
        assertEquals(
            listOf("staging", "production"),
            extension.getOrderedDimensions().first().getOrderedFlavors().map { it.name }
        )
    }
}
