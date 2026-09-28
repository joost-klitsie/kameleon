package dev.klitsie.kameleon

import dev.klitsie.kameleon.dsl.BuildConfigValue
import dev.klitsie.kameleon.dsl.KameleonExtension
import dev.klitsie.kameleon.tasks.GenerateKameleonConfigTask
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.assertThrows
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GenerateKameleonConfigTaskTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `build config block supports multiple assignment syntaxes and types`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val extension = project.extensions.create("kameleon", KameleonExtension::class.java)

        extension.defaultBuildConfig {
            packageName.set("com.example.app")
            className.set("AppConfig")

            // Infix to syntax
            "baseUrl" to "https://api.production.com"
            "enableAnalytics" to true

            // Operator set syntax
            this["timeoutSeconds"] = 30
            this["maxFileSize"] = 1048576L

            // Generic put
            put("genericString", "custom")
            put("genericBool", false)
            put("genericInt", 42)
            put("genericLong", 999L)

            // Direct typed putters
            putString("directString", "str")
            putBoolean("directBool", true)
            putInt("directInt", 100)
            putLong("directLong", 200L)
        }

        val fields = extension.defaultBuildConfig.fields.get()
        assertEquals(BuildConfigValue.StringValue("https://api.production.com"), fields["baseUrl"])
        assertEquals(BuildConfigValue.BooleanValue(true), fields["enableAnalytics"])
        assertEquals(BuildConfigValue.IntValue(30), fields["timeoutSeconds"])
        assertEquals(BuildConfigValue.LongValue(1048576L), fields["maxFileSize"])
        assertEquals(BuildConfigValue.StringValue("custom"), fields["genericString"])
        assertEquals(BuildConfigValue.BooleanValue(false), fields["genericBool"])
        assertEquals(BuildConfigValue.IntValue(42), fields["genericInt"])
        assertEquals(BuildConfigValue.LongValue(999L), fields["genericLong"])
        assertEquals(BuildConfigValue.StringValue("str"), fields["directString"])
        assertEquals(BuildConfigValue.BooleanValue(true), fields["directBool"])
        assertEquals(BuildConfigValue.IntValue(100), fields["directInt"])
        assertEquals(BuildConfigValue.LongValue(200L), fields["directLong"])
    }

    @Test
    fun `build config block throws IllegalArgumentException on unsupported types`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val extension = project.extensions.create("kameleon", KameleonExtension::class.java)

        assertThrows<IllegalArgumentException> {
            extension.defaultBuildConfig {
                put("unsupportedDouble", 3.14)
            }
        }

        assertThrows<IllegalArgumentException> {
            extension.defaultBuildConfig {
                "unsupportedList" to listOf("a", "b")
            }
        }
    }

    @Test
    fun `generate kameleon config task outputs formatted kotlin object`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("generateConfig", GenerateKameleonConfigTask::class.java).get()

        val outputDir = File(tempDir, "build/generated/kameleon/buildconfig/staging")
        task.packageName.set("com.example.app")
        task.className.set("AppConfig")
        task.variantName.set("staging")
        task.flavorParts.set(listOf("staging"))
        task.outputDir.set(outputDir)

        task.fields.put("baseUrl", BuildConfigValue.StringValue("https://api.staging.com"))
        task.fields.put("enableAnalytics", BuildConfigValue.BooleanValue(false))
        task.fields.put("timeoutSeconds", BuildConfigValue.IntValue(30))
        task.fields.put("cacheSize", BuildConfigValue.LongValue(1024L))

        task.generate()

        val generatedFile = File(outputDir, "com/example/app/AppConfig.kt")
        assertTrue(generatedFile.exists(), "Generated file should exist at ${generatedFile.path}")

        val content = generatedFile.readText()
        assertTrue(content.contains("package com.example.app"))
        assertTrue(content.contains("public object AppConfig {"))
        assertTrue(content.contains("public const val VARIANT: String = \"staging\""))
        assertTrue(content.contains("public const val FLAVOR: String = \"staging\""))
        assertTrue(content.contains("public const val BASE_URL: String = \"https://api.staging.com\""))
        assertTrue(content.contains("public const val ENABLE_ANALYTICS: Boolean = false"))
        assertTrue(content.contains("public const val TIMEOUT_SECONDS: Int = 30"))
        assertTrue(content.contains("public const val CACHE_SIZE: Long = 1024L"))
    }

    @Test
    fun `screaming snake case formatter converts various naming conventions`() {
        assertEquals("BASE_URL", GenerateKameleonConfigTask.toScreamingSnakeCase("baseUrl"))
        assertEquals("ENABLE_ANALYTICS", GenerateKameleonConfigTask.toScreamingSnakeCase("enableAnalytics"))
        assertEquals("TIMEOUT_SECONDS", GenerateKameleonConfigTask.toScreamingSnakeCase("timeoutSeconds"))
        assertEquals("API_KEY", GenerateKameleonConfigTask.toScreamingSnakeCase("apiKey"))
        assertEquals("API_KEY", GenerateKameleonConfigTask.toScreamingSnakeCase("APIKey"))
        assertEquals("CUSTOM_FIELD_NAME", GenerateKameleonConfigTask.toScreamingSnakeCase("custom-field-name"))
        assertEquals("ALREADY_SNAKE", GenerateKameleonConfigTask.toScreamingSnakeCase("ALREADY_SNAKE"))
        assertEquals("SIMPLE", GenerateKameleonConfigTask.toScreamingSnakeCase("simple"))
    }

    @Test
    fun `full dsl and multi-variant resolution priority`() {
        val projDir = File(tempDir, "proj-multivariant").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projDir).build()
        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("dev.klitsie.kameleon")

        val kotlin = project.extensions.findByType(org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java)!!
        kotlin.jvm()

        val extension = project.extensions.findByType(KameleonExtension::class.java)!!

        // Configure using target consumer DSL
        extension.defaultBuildConfig {
            packageName.set("com.example.app")
            className.set("AppConfig")

            "baseUrl" to "https://api.production.com"
            "enableAnalytics" to true
            this["timeoutSeconds"] = 30
        }

        extension.dimension("brand") {
            flavor("free")
            flavor("paid")
        }

        extension.dimension("environment") {
            flavor("staging") {
                buildConfig {
                    "baseUrl" to "https://api.staging.com"
                    "enableAnalytics" to false
                }
            }
            flavor("production")
        }

        val evaluateMethod = project.javaClass.getMethod("evaluate")
        evaluateMethod.invoke(project)

        // Check task generation
        val stagingTask = project.tasks.findByName("generateKameleonFreeStagingConfig") as? GenerateKameleonConfigTask
        assertTrue(stagingTask != null)
        stagingTask.generate()

        val outputDir = stagingTask.outputDir.get().asFile
        val generatedFile = File(outputDir, "com/example/app/AppConfig.kt")
        assertTrue(generatedFile.exists())

        val content = generatedFile.readText()
        assertTrue(content.contains("public const val VARIANT: String = \"freeStaging\""))
        assertTrue(content.contains("public const val FLAVOR: String = \"free\""))
        // Overridden by staging flavor
        assertTrue(content.contains("public const val BASE_URL: String = \"https://api.staging.com\""))
        assertTrue(content.contains("public const val ENABLE_ANALYTICS: Boolean = false"))
        // Inherited from defaultBuildConfig
        assertTrue(content.contains("public const val TIMEOUT_SECONDS: Int = 30"))
    }

    @Test
    fun `default package name resolution falls back to default and respects android namespace and explicit overrides`() {
        val projDir = File(tempDir, "proj-pkg-res").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projDir).build()
        project.pluginManager.apply("dev.klitsie.kameleon")

        val extension = project.extensions.findByType(KameleonExtension::class.java)!!

        // 1. Standalone default fallback
        assertEquals("dev.klitsie.kameleon", extension.defaultBuildConfig.packageName.get())

        // 2. Explicit override wins
        extension.defaultBuildConfig.packageName.set("com.custom.pkg")
        assertEquals("com.custom.pkg", extension.defaultBuildConfig.packageName.get())
    }

    @Test
    fun `default package name automatically resolves from AGP ApplicationExtension namespace lazily`() {
        val projDir = File(tempDir, "proj-android-pkg").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projDir).build()

        project.pluginManager.apply("com.android.application")
        project.pluginManager.apply("dev.klitsie.kameleon")

        val androidApp = project.extensions.getByType(com.android.build.api.dsl.ApplicationExtension::class.java)
        androidApp.namespace = "com.example.agpapp"

        val extension = project.extensions.findByType(KameleonExtension::class.java)!!

        // Resolves lazily to AGP namespace
        assertEquals("com.example.agpapp", extension.defaultBuildConfig.packageName.get())

        // Explicit override still wins over AGP namespace
        extension.defaultBuildConfig.packageName.set("com.custom.override")
        assertEquals("com.custom.override", extension.defaultBuildConfig.packageName.get())
    }
}
