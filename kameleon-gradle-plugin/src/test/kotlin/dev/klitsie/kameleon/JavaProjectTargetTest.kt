package dev.klitsie.kameleon

import dev.klitsie.kameleon.dsl.BuildConfigValue
import dev.klitsie.kameleon.dsl.KameleonExtension
import dev.klitsie.kameleon.tasks.GenerateKameleonConfigTask
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JavaProjectTargetTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `generate kameleon config task outputs formatted java class when language is java`() {
        val project = ProjectBuilder.builder().withProjectDir(tempDir).build()
        val task = project.tasks.register("generateJavaConfig", GenerateKameleonConfigTask::class.java).get()

        val outputDir = File(tempDir, "build/generated/kameleon/buildconfig/staging")
        task.packageName.set("com.example.javaapp")
        task.className.set("AppConfig")
        task.variantName.set("staging")
        task.flavorParts.set(listOf("staging"))
        task.language.set("java")
        task.outputDir.set(outputDir)

        task.fields.put("baseUrl", BuildConfigValue.StringValue("https://api.staging.com"))
        task.fields.put("enableAnalytics", BuildConfigValue.BooleanValue(false))
        task.fields.put("timeoutSeconds", BuildConfigValue.IntValue(30))
        task.fields.put("cacheSize", BuildConfigValue.LongValue(1024L))

        task.generate()

        val generatedFile = File(outputDir, "com/example/javaapp/AppConfig.java")
        assertTrue(generatedFile.exists(), "Generated Java file should exist at ${generatedFile.path}")

        val content = generatedFile.readText()
        assertTrue(content.contains("package com.example.javaapp;"))
        assertTrue(content.contains("public final class AppConfig {"))
        assertTrue(content.contains("private AppConfig() {}"))
        assertTrue(content.contains("public static final String VARIANT = \"staging\";"))
        assertTrue(content.contains("public static final String FLAVOR = \"staging\";"))
        assertTrue(content.contains("public static final String BASE_URL = \"https://api.staging.com\";"))
        assertTrue(content.contains("public static final boolean ENABLE_ANALYTICS = false;"))
        assertTrue(content.contains("public static final int TIMEOUT_SECONDS = 30;"))
        assertTrue(content.contains("public static final long CACHE_SIZE = 1024L;"))
    }

    @Test
    fun `pure java project enables kameleon DSL, dynamic source roots and java BuildConfig generation`() {
        val projDir = File(tempDir, "proj-pure-java").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projDir).build()

        project.pluginManager.apply("java")
        project.pluginManager.apply("dev.klitsie.kameleon")

        val flavorJavaDir = File(projDir, "src/free/java").apply { mkdirs() }
        val flavorResDir = File(projDir, "src/free/resources").apply { mkdirs() }

        val extension = project.extensions.getByType(KameleonExtension::class.java)
        extension.defaultBuildConfig {
            packageName.set("com.example.javaapp")
            className.set("JavaConfig")
            "apiKey" to "default_key"
        }

        extension.dimension("tier") {
            flavor("free") {
                buildConfig {
                    "apiKey" to "free_key"
                    "isPaid" to false
                }
            }
            flavor("paid") {
                buildConfig {
                    "apiKey" to "paid_key"
                    "isPaid" to true
                }
            }
        }

        project.extensions.extraProperties.set("kameleon.flavor.tier", "free")

        val evaluateMethod = project.javaClass.getMethod("evaluate")
        evaluateMethod.invoke(project)

        val javaBuildConfigTask = project.tasks.findByName("generateJavaBuildConfig")
        assertNotNull(javaBuildConfigTask, "generateJavaBuildConfig task should be registered on pure Java project")

        val kameleonConfigTask = project.tasks.findByName("generateKameleonConfig")
        assertNotNull(kameleonConfigTask, "generateKameleonConfig task should be registered on pure Java project")

        val freeConfigTask = project.tasks.findByName("generateKameleonFreeConfig") as? GenerateKameleonConfigTask
        assertNotNull(freeConfigTask, "generateKameleonFreeConfig task should be registered")
        assertEquals("java", freeConfigTask.language.get())

        freeConfigTask.generate()

        val outputDir = freeConfigTask.outputDir.get().asFile
        val generatedJava = File(outputDir, "com/example/javaapp/JavaConfig.java")
        assertTrue(generatedJava.exists(), "JavaConfig.java should be generated")
        val content = generatedJava.readText()
        assertTrue(content.contains("public static final String API_KEY = \"free_key\";"))
        assertTrue(content.contains("public static final boolean IS_PAID = false;"))

        val javaExt = project.extensions.getByType(JavaPluginExtension::class.java)
        val mainSourceSet = javaExt.sourceSets.getByName("main")

        val javaSrcDirs = mainSourceSet.java.srcDirs
        assertTrue(javaSrcDirs.contains(flavorJavaDir), "main java srcDirs should include src/free/java")
        assertTrue(javaSrcDirs.any { it.path.contains("generated") && it.path.contains("buildconfig") }, "main java srcDirs should include generated BuildConfig output dir")

        val resSrcDirs = mainSourceSet.resources.srcDirs
        assertTrue(resSrcDirs.contains(flavorResDir), "main resources srcDirs should include src/free/resources")
    }

    @Test
    fun `kmp project with jvm target does NOT trigger JavaProjectTarget`() {
        val projDir = File(tempDir, "proj-kmp-jvm").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projDir).build()

        project.pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        project.pluginManager.apply("dev.klitsie.kameleon")

        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        kotlin.jvm()

        val extension = project.extensions.getByType(KameleonExtension::class.java)
        extension.dimension("mode") {
            flavor("demo")
            flavor("full")
        }

        val evaluateMethod = project.javaClass.getMethod("evaluate")
        evaluateMethod.invoke(project)

        // Mutually exclusive: JavaProjectTarget must NOT attach when KMP is present
        assertNull(project.tasks.findByName("generateJavaBuildConfig"), "generateJavaBuildConfig must not be registered on KMP project")

        // KMP config generation task should exist and produce Kotlin object
        val demoConfigTask = project.tasks.findByName("generateKameleonDemoConfig") as? GenerateKameleonConfigTask
        assertNotNull(demoConfigTask, "generateKameleonDemoConfig should be registered by KMP target")
        assertFalse(demoConfigTask.language.orNull == "java", "KMP config task should not be configured as Java")
    }

    @Test
    fun `android application project does NOT trigger JavaProjectTarget`() {
        val projDir = File(tempDir, "proj-android-app").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projDir).build()

        project.pluginManager.apply("com.android.application")
        project.pluginManager.apply("dev.klitsie.kameleon")

        val androidApp = project.extensions.getByType(com.android.build.api.dsl.ApplicationExtension::class.java)
        androidApp.namespace = "com.example.androidapp"
        androidApp.compileSdk = 34

        val evaluateMethod = project.javaClass.getMethod("evaluate")
        evaluateMethod.invoke(project)

        assertNull(project.tasks.findByName("generateJavaBuildConfig"), "generateJavaBuildConfig must not be registered on Android application project")
    }

    @Test
    fun `android library project does NOT trigger JavaProjectTarget`() {
        val projDir = File(tempDir, "proj-android-lib").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projDir).build()

        project.pluginManager.apply("com.android.library")
        project.pluginManager.apply("dev.klitsie.kameleon")

        val androidLib = project.extensions.getByType(com.android.build.api.dsl.LibraryExtension::class.java)
        androidLib.namespace = "com.example.androidlib"
        androidLib.compileSdk = 34

        val evaluateMethod = project.javaClass.getMethod("evaluate")
        evaluateMethod.invoke(project)

        assertNull(project.tasks.findByName("generateJavaBuildConfig"), "generateJavaBuildConfig must not be registered on Android library project")
    }

    @Test
    fun `kotlin jvm project does NOT trigger JavaProjectTarget`() {
        val projDir = File(tempDir, "proj-kotlin-jvm").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projDir).build()

        project.pluginManager.apply("org.jetbrains.kotlin.jvm")
        project.pluginManager.apply("dev.klitsie.kameleon")

        val evaluateMethod = project.javaClass.getMethod("evaluate")
        evaluateMethod.invoke(project)

        assertNull(project.tasks.findByName("generateJavaBuildConfig"), "generateJavaBuildConfig must not be registered on Kotlin JVM project")
    }

    @Test
    fun `java-library and application projects trigger JavaProjectTarget with reversed plugin application order`() {
        val projDirLib = File(tempDir, "proj-java-lib").apply { mkdirs() }
        val projectLib = ProjectBuilder.builder().withProjectDir(projDirLib).build()

        // Apply kameleon first, then java-library
        projectLib.pluginManager.apply("dev.klitsie.kameleon")
        projectLib.pluginManager.apply("java-library")

        val flavorJavaDir = File(projDirLib, "src/flavors/pro/java").apply { mkdirs() }
        val flavorResDir = File(projDirLib, "src/flavors/pro/resources").apply { mkdirs() }

        val extLib = projectLib.extensions.getByType(KameleonExtension::class.java)
        extLib.dimension("edition") {
            flavor("pro")
            flavor("community")
        }
        projectLib.extensions.extraProperties.set("kameleon.flavor.edition", "pro")

        val evaluateLib = projectLib.javaClass.getMethod("evaluate")
        evaluateLib.invoke(projectLib)

        assertNotNull(projectLib.tasks.findByName("generateJavaBuildConfig"), "generateJavaBuildConfig should be registered on java-library project")
        val mainSourceSet = projectLib.extensions.getByType(JavaPluginExtension::class.java).sourceSets.getByName("main")
        assertTrue(mainSourceSet.java.srcDirs.contains(flavorJavaDir), "src/flavors/pro/java should be included in java source dirs")
        assertTrue(mainSourceSet.resources.srcDirs.contains(flavorResDir), "src/flavors/pro/resources should be included in resources source dirs")

        // Application plugin
        val projDirApp = File(tempDir, "proj-java-app").apply { mkdirs() }
        val projectApp = ProjectBuilder.builder().withProjectDir(projDirApp).build()
        projectApp.pluginManager.apply("dev.klitsie.kameleon")
        projectApp.pluginManager.apply("application")

        val evaluateApp = projectApp.javaClass.getMethod("evaluate")
        evaluateApp.invoke(projectApp)

        assertNotNull(projectApp.tasks.findByName("generateJavaBuildConfig"), "generateJavaBuildConfig should be registered on application project")
    }

    @Test
    fun `tooling model builder exposes schema correctly for Java target`() {
        val projDir = File(tempDir, "proj-java-tooling").apply { mkdirs() }
        val project = ProjectBuilder.builder().withProjectDir(projDir).build()

        project.pluginManager.apply("java")
        project.pluginManager.apply("dev.klitsie.kameleon")

        val extension = project.extensions.getByType(KameleonExtension::class.java)
        extension.dimension("environment") {
            flavor("staging")
            flavor("production")
        }
        extension.defaultVariant.set("staging")

        val evaluateMethod = project.javaClass.getMethod("evaluate")
        evaluateMethod.invoke(project)

        val modelBuilder = dev.klitsie.kameleon.tooling.KameleonModelBuilder(extension)
        assertTrue(modelBuilder.canBuild(dev.klitsie.kameleon.tooling.KameleonToolingModel::class.java.name))

        val model = modelBuilder.buildAll(dev.klitsie.kameleon.tooling.KameleonToolingModel::class.java.name, project) as dev.klitsie.kameleon.tooling.KameleonToolingModel
        assertEquals(project.path, model.projectPath)
        assertEquals(listOf("staging", "production"), model.dimensions["environment"])
        assertEquals(listOf("staging", "production"), model.availableVariants)
        assertEquals("staging", model.defaultVariant)
    }
}
