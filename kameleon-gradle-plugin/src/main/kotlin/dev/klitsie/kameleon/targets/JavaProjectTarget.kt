package dev.klitsie.kameleon.targets

import dev.klitsie.kameleon.VariantCombination
import dev.klitsie.kameleon.VariantMatrix
import dev.klitsie.kameleon.dsl.KameleonExtension
import dev.klitsie.kameleon.resolveActiveVariant
import dev.klitsie.kameleon.resolveFieldsForVariant
import dev.klitsie.kameleon.resolveHoconEntriesForVariant
import dev.klitsie.kameleon.resolveHoconFileNameForVariant
import dev.klitsie.kameleon.tasks.GenerateHoconConfigTask
import dev.klitsie.kameleon.tasks.GenerateKameleonConfigTask
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import java.io.File

class JavaProjectTarget(
    private val project: Project,
    private val extension: KameleonExtension
) : TargetAdapter {

    override fun configure() {
        project.plugins.withId("java") {
            project.afterEvaluate {
                val hasKmp = project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
                val hasKotlinJvm = project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")
                val hasAgp = project.plugins.hasPlugin("com.android.base") ||
                    project.plugins.hasPlugin("com.android.application") ||
                    project.plugins.hasPlugin("com.android.library")

                if (!hasKmp && !hasKotlinJvm && !hasAgp) {
                    configureTarget()
                }
            }
        }
    }

    private fun configureTarget() {
        val javaExt = project.extensions.findByType(JavaPluginExtension::class.java) ?: return
        val mainSourceSet = javaExt.sourceSets.findByName("main") ?: return

        val activeVariant = resolveActiveVariant(project, extension)
        val variants = VariantMatrix.calculateVariants(extension.getOrderedDimensions())
        val combination = variants.find { it.name.equals(activeVariant, ignoreCase = true) }
        val activeFlavors = mutableListOf<String>()
        if (combination != null) {
            activeFlavors.addAll(combination.flavorNames)
            if (!combination.flavorNames.contains(combination.name)) {
                activeFlavors.add(combination.name)
            }
        } else {
            activeFlavors.add(activeVariant)
        }

        for (flavor in activeFlavors.distinct()) {
            val flavorJava = project.file("src/$flavor/java")
            if (flavorJava.exists()) {
                mainSourceSet.java.srcDir(flavorJava)
            }
            val flavorResources = project.file("src/$flavor/resources")
            if (flavorResources.exists()) {
                mainSourceSet.resources.srcDir(flavorResources)
            }

            val flavorsDir = project.file("src/flavors/$flavor")
            if (flavorsDir.exists()) {
                val fJava = File(flavorsDir, "java")
                if (fJava.exists()) {
                    mainSourceSet.java.srcDir(fJava)
                }
                val fResources = File(flavorsDir, "resources")
                if (fResources.exists()) {
                    mainSourceSet.resources.srcDir(fResources)
                }
            }
        }

        // Configure variant BuildConfig tasks
        for (variant in variants) {
            val capVariant = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val configTaskName = "generateKameleon${capVariant}Config"
            if (project.tasks.findByName(configTaskName) == null) {
                project.tasks.register(configTaskName, GenerateKameleonConfigTask::class.java) {
                    group = "kameleon"
                    description = "Generates type-safe KameleonConfig for the '${variant.name}' variant."
                    packageName.set(extension.defaultBuildConfig.packageName)
                    className.set(extension.defaultBuildConfig.className)
                    variantName.set(variant.name)
                    flavorParts.set(variant.flavorNames)
                    fields.set(project.provider { resolveFieldsForVariant(extension, variant) })
                    language.set("java")
                    outputDir.convention(
                        project.layout.buildDirectory.dir("generated/kameleon/buildconfig/${variant.name}")
                    )
                }
            }

            val hoconTaskName = "generateKameleon${capVariant}HoconConfig"
            if (project.tasks.findByName(hoconTaskName) == null) {
                project.tasks.register(hoconTaskName, GenerateHoconConfigTask::class.java) {
                    group = "kameleon"
                    description = "Generates HOCON config for the '${variant.name}' variant."
                    fileName.set(project.provider { resolveHoconFileNameForVariant(extension, variant) })
                    entries.set(project.provider { resolveHoconEntriesForVariant(extension, variant) })
                    outputDirectory.convention(
                        project.layout.buildDirectory.dir("generated/kameleon/hocon/${variant.name}")
                    )
                }
            }
        }

        // Active variant task
        val activeCapVariant = activeVariant.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val activeConfigTaskName = "generateKameleon${activeCapVariant}Config"
        val activeGenTask = if (project.tasks.findByName(activeConfigTaskName) != null) {
            project.tasks.named(activeConfigTaskName, GenerateKameleonConfigTask::class.java)
        } else {
            project.tasks.register(activeConfigTaskName, GenerateKameleonConfigTask::class.java) {
                group = "kameleon"
                description = "Generates type-safe KameleonConfig for the '$activeVariant' variant."
                packageName.set(extension.defaultBuildConfig.packageName)
                className.set(extension.defaultBuildConfig.className)
                variantName.set(activeVariant)
                val comb = variants.find { it.name.equals(activeVariant, ignoreCase = true) }
                flavorParts.set(comb?.flavorNames ?: emptyList())
                fields.set(project.provider {
                    resolveFieldsForVariant(
                        extension,
                        comb ?: VariantCombination(activeVariant, emptyList())
                    )
                })
                language.set("java")
                outputDir.convention(
                    project.layout.buildDirectory.dir("generated/kameleon/buildconfig/$activeVariant")
                )
            }
        }

        val activeHoconTaskName = "generateKameleon${activeCapVariant}HoconConfig"
        val activeHoconGenTask = if (project.tasks.findByName(activeHoconTaskName) != null) {
            project.tasks.named(activeHoconTaskName, GenerateHoconConfigTask::class.java)
        } else {
            project.tasks.register(activeHoconTaskName, GenerateHoconConfigTask::class.java) {
                group = "kameleon"
                description = "Generates HOCON config for the '$activeVariant' variant."
                val comb = variants.find { it.name.equals(activeVariant, ignoreCase = true) }
                    ?: VariantCombination(activeVariant, emptyList())
                fileName.set(project.provider { resolveHoconFileNameForVariant(extension, comb) })
                entries.set(project.provider { resolveHoconEntriesForVariant(extension, comb) })
                outputDirectory.convention(
                    project.layout.buildDirectory.dir("generated/kameleon/hocon/$activeVariant")
                )
            }
        }

        if (project.tasks.findByName("generateJavaBuildConfig") == null) {
            project.tasks.register("generateJavaBuildConfig") {
                group = "kameleon"
                description = "Generates type-safe Java BuildConfig for the active variant ('$activeVariant')."
                dependsOn(activeGenTask)
            }
        }

        if (project.tasks.findByName("generateKameleonConfig") == null) {
            project.tasks.register("generateKameleonConfig") {
                group = "kameleon"
                description = "Generates type-safe KameleonConfig for the active variant ('$activeVariant')."
                dependsOn(activeGenTask)
            }
        }

        if (project.tasks.findByName("generateKameleonHoconConfig") == null) {
            project.tasks.register("generateKameleonHoconConfig") {
                group = "kameleon"
                description = "Generates HOCON config for the active variant ('$activeVariant')."
                dependsOn(activeHoconGenTask)
            }
        }

        mainSourceSet.java.srcDir(activeGenTask.map { it.outputDir })
        mainSourceSet.resources.srcDir(activeHoconGenTask.map { it.outputDirectory })
    }
}
