package dev.klitsie.kameleon.targets

import dev.klitsie.kameleon.VariantCombination
import dev.klitsie.kameleon.VariantMatrix
import dev.klitsie.kameleon.dsl.KameleonExtension
import dev.klitsie.kameleon.resolveActiveVariant
import dev.klitsie.kameleon.resolveFieldsForVariant
import dev.klitsie.kameleon.tasks.GenerateKameleonConfigTask
import dev.klitsie.kameleon.tasks.MergeComposeResourcesTask
import org.gradle.api.Project
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.Framework
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import java.io.File

class KotlinMultiplatformTarget(
    private val project: Project,
    private val extension: KameleonExtension,
    private val mergeTaskProvider: TaskProvider<MergeComposeResourcesTask>? = null
) : TargetAdapter {
    constructor(project: Project) : this(
        project,
        project.extensions.getByType(KameleonExtension::class.java),
        if (project.tasks.names.contains("mergeKameleonResources")) {
            project.tasks.named("mergeKameleonResources", MergeComposeResourcesTask::class.java)
        } else null
    )

    override fun configure() {
        configureSourceSets()
        configureVariantTasks()
    }

    private fun configureSourceSets() {
        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlinExt = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
            if (kotlinExt != null) {
                project.afterEvaluate {
                    val activeVariant = resolveActiveVariant(project, extension)
                    val variants = VariantMatrix.calculateVariants(extension.getOrderedDimensions())
                    val combination = variants.find { it.name.equals(activeVariant, ignoreCase = true) }
                    val flavorDirsToAttach = mutableListOf<String>()
                    if (combination != null) {
                        flavorDirsToAttach.addAll(combination.flavorNames)
                        if (!combination.flavorNames.contains(combination.name)) {
                            flavorDirsToAttach.add(combination.name)
                        }
                    } else {
                        flavorDirsToAttach.add(activeVariant)
                    }

                    for (flavorName in flavorDirsToAttach.distinct()) {
                        val flavorDir = project.file("src/flavors/$flavorName")
                        if (flavorDir.exists()) {
                            val commonKotlin = File(flavorDir, "kotlin")
                            if (commonKotlin.exists()) {
                                kotlinExt.sourceSets.findByName("commonMain")?.kotlin?.srcDir(commonKotlin)
                            }
                            val androidMain = File(flavorDir, "androidMain")
                            if (androidMain.exists()) {
                                kotlinExt.sourceSets.findByName("androidMain")?.kotlin?.srcDir(androidMain)
                            }
                            val desktopMain = File(flavorDir, "desktopMain")
                            if (desktopMain.exists()) {
                                kotlinExt.sourceSets.findByName("jvmMain")?.kotlin?.srcDir(desktopMain)
                                kotlinExt.sourceSets.findByName("desktopMain")?.kotlin?.srcDir(desktopMain)
                            }
                            val iosMain = File(flavorDir, "iosMain")
                            if (iosMain.exists()) {
                                kotlinExt.sourceSets.findByName("iosMain")?.kotlin?.srcDir(iosMain)
                            }
                            val wasmJsMain = File(flavorDir, "wasmJsMain")
                            if (wasmJsMain.exists()) {
                                kotlinExt.sourceSets.findByName("wasmJsMain")?.kotlin?.srcDir(wasmJsMain)
                            }
                            val jsMain = File(flavorDir, "jsMain")
                            if (jsMain.exists()) {
                                kotlinExt.sourceSets.findByName("jsMain")?.kotlin?.srcDir(jsMain)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun configureVariantTasks() {
        project.afterEvaluate {
            val kotlinExt = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
            val hasKmp = project.plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
            val hasKotlinJvm = project.plugins.hasPlugin("org.jetbrains.kotlin.jvm")
            if (kotlinExt == null && !hasKmp && !hasKotlinJvm) {
                return@afterEvaluate
            }

            val hasJvmOrDesktop = kotlinExt?.targets?.any { it.platformType.name == "jvm" } == true ||
                hasKotlinJvm
            val hasIos = kotlinExt?.targets?.any { it.platformType.name == "native" && it.name.startsWith("ios", ignoreCase = true) } == true
            val hasWasmJs = kotlinExt?.targets?.any { it.name == "wasmJs" || it.platformType.name == "wasm" } == true
            val hasJs = kotlinExt?.targets?.any { it.name == "js" || it.platformType.name == "js" } == true

            val variants = VariantMatrix.calculateVariants(extension.getOrderedDimensions())

            val resolvedMergeTask = mergeTaskProvider
                ?: if (project.tasks.names.contains("mergeKameleonResources")) {
                    project.tasks.named("mergeKameleonResources", MergeComposeResourcesTask::class.java)
                } else null

            for (variant in variants) {
                val capVariant = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                // Build Config Task for this variant
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
                        outputDir.convention(
                            project.layout.buildDirectory.dir("generated/kameleon/buildconfig/${variant.name}")
                        )
                    }
                }

                // Desktop (JVM) Tasks
                if (hasJvmOrDesktop) {
                    val runTaskName = "run$capVariant"
                    if (project.tasks.findByName(runTaskName) == null) {
                        val desktopRunTask = project.tasks.findByName("run")
                        if (desktopRunTask != null) {
                            project.tasks.register(runTaskName) {
                                group = "compose desktop"
                                description = "Runs the Compose Desktop application with the '${variant.name}' variant (debug/development)."
                                dependsOn(desktopRunTask)
                                doFirst {
                                    setKameleonFlavorSystemProperties(extension, variant)
                                }
                            }
                        }
                    }

                    val runReleaseTaskName = "run${capVariant}Release"
                    if (project.tasks.findByName(runReleaseTaskName) == null) {
                        val desktopRunReleaseTask = project.tasks.findByName("runRelease")
                            ?: project.tasks.findByName("run")
                        if (desktopRunReleaseTask != null) {
                            project.tasks.register(runReleaseTaskName) {
                                group = "compose desktop"
                                description = "Runs the Compose Desktop application with the '${variant.name}' variant (release)."
                                dependsOn(desktopRunReleaseTask)
                                doFirst {
                                    setKameleonFlavorSystemProperties(extension, variant)
                                }
                            }
                        }
                    }

                    val packageTaskName = "package${capVariant}Distribution"
                    if (project.tasks.findByName(packageTaskName) == null) {
                        val desktopPackageTask = project.tasks.findByName("packageDistributionForCurrentOS")
                            ?: project.tasks.findByName("packageUberJarForCurrentOS")
                            ?: project.tasks.findByName("package")
                        if (desktopPackageTask != null) {
                            project.tasks.register(packageTaskName) {
                                group = "compose desktop"
                                description = "Packages the Compose Desktop distribution for variant '${variant.name}'."
                                dependsOn(desktopPackageTask)
                                doFirst {
                                    setKameleonFlavorSystemProperties(extension, variant)
                                }
                            }
                        }
                    }

                    val packageReleaseTaskName = "package${capVariant}ReleaseDistribution"
                    if (project.tasks.findByName(packageReleaseTaskName) == null) {
                        val desktopPackageReleaseTask = project.tasks.findByName("packageReleaseDistributionForCurrentOS")
                            ?: project.tasks.findByName("packageReleaseUberJarForCurrentOS")
                            ?: project.tasks.findByName("packageDistributionForCurrentOS")
                            ?: project.tasks.findByName("package")
                        if (desktopPackageReleaseTask != null) {
                            project.tasks.register(packageReleaseTaskName) {
                                group = "compose desktop"
                                description = "Packages the Compose Desktop release distribution for variant '${variant.name}'."
                                dependsOn(desktopPackageReleaseTask)
                                doFirst {
                                    setKameleonFlavorSystemProperties(extension, variant)
                                }
                            }
                        }
                    }
                }

                // iOS Tasks
                if (hasIos) {
                    val iosTaskName = "assemble${capVariant}IosFramework"
                    val iosDebugTaskName = "assemble${capVariant}DebugIosFramework"
                    val iosReleaseTaskName = "assemble${capVariant}ReleaseIosFramework"

                    val assembleVariantFramework = if (project.tasks.findByName(iosTaskName) != null) {
                        project.tasks.named(iosTaskName)
                    } else {
                        project.tasks.register(iosTaskName) {
                            group = "kameleon ios"
                            description = "Assembles all iOS frameworks for variant '${variant.name}'."
                            resolvedMergeTask?.let { dependsOn(it) }
                        }
                    }

                    val assembleDebugFramework = if (project.tasks.findByName(iosDebugTaskName) != null) {
                        project.tasks.named(iosDebugTaskName)
                    } else {
                        project.tasks.register(iosDebugTaskName) {
                            group = "kameleon ios"
                            description = "Assembles the Debug iOS framework for variant '${variant.name}'."
                            resolvedMergeTask?.let { dependsOn(it) }
                        }
                    }

                    val assembleReleaseFramework = if (project.tasks.findByName(iosReleaseTaskName) != null) {
                        project.tasks.named(iosReleaseTaskName)
                    } else {
                        project.tasks.register(iosReleaseTaskName) {
                            group = "kameleon ios"
                            description = "Assembles the Release iOS framework for variant '${variant.name}'."
                            resolvedMergeTask?.let { dependsOn(it) }
                        }
                    }

                    val kotlin = project.extensions.findByType(KotlinMultiplatformExtension::class.java)
                    kotlin?.targets?.withType(KotlinNativeTarget::class.java)?.configureEach {
                        if (konanTarget.family.isAppleFamily) {
                            binaries.withType(Framework::class.java).configureEach {
                                assembleVariantFramework.configure {
                                    dependsOn(linkTaskProvider)
                                }
                                if (buildType.name.equals("DEBUG", ignoreCase = true)) {
                                    assembleDebugFramework.configure {
                                        dependsOn(linkTaskProvider)
                                    }
                                } else if (buildType.name.equals("RELEASE", ignoreCase = true)) {
                                    assembleReleaseFramework.configure {
                                        dependsOn(linkTaskProvider)
                                    }
                                }
                            }
                        }
                    }
                }

                // Web Tasks (WasmJS & JS)
                if (hasWasmJs) {
                    val wasmRunTask = "runWasmJs$capVariant"
                    if (project.tasks.findByName(wasmRunTask) == null) {
                        project.tasks.register(wasmRunTask) {
                            group = "kameleon web"
                            description = "Runs the WasmJS development server for variant '${variant.name}'."
                            project.tasks.findByName("wasmJsBrowserDevelopmentRun")?.let { dependsOn(it) }
                            doFirst {
                                setKameleonFlavorSystemProperties(extension, variant)
                            }
                        }
                    }

                    val wasmRunReleaseTask = "runWasmJs${capVariant}Release"
                    if (project.tasks.findByName(wasmRunReleaseTask) == null) {
                        project.tasks.register(wasmRunReleaseTask) {
                            group = "kameleon web"
                            description = "Runs the WasmJS production/release server for variant '${variant.name}'."
                            (project.tasks.findByName("wasmJsBrowserProductionRun")
                                ?: project.tasks.findByName("wasmJsBrowserDevelopmentRun"))?.let { dependsOn(it) }
                            doFirst {
                                setKameleonFlavorSystemProperties(extension, variant)
                            }
                        }
                    }

                    val wasmDistTask = "wasmJs${capVariant}BrowserDistribution"
                    if (project.tasks.findByName(wasmDistTask) == null) {
                        project.tasks.register<Copy>(wasmDistTask) {
                            group = "kameleon web"
                            description = "Executes WasmJS browser distribution for variant '${variant.name}'."
                            project.tasks.findByName("wasmJsBrowserDistribution")?.let { dependsOn(it) }
                            from(project.layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
                            into(project.layout.buildDirectory.dir("dist/wasmJs/${variant.name}"))
                        }
                    }
                }

                if (hasJs) {
                    val jsRunTask = "runJs$capVariant"
                    if (project.tasks.findByName(jsRunTask) == null) {
                        project.tasks.register(jsRunTask) {
                            group = "kameleon web"
                            description = "Runs the JS development server for variant '${variant.name}'."
                            project.tasks.findByName("jsBrowserDevelopmentRun")?.let { dependsOn(it) }
                            doFirst {
                                setKameleonFlavorSystemProperties(extension, variant)
                            }
                        }
                    }

                    val jsRunReleaseTask = "runJs${capVariant}Release"
                    if (project.tasks.findByName(jsRunReleaseTask) == null) {
                        project.tasks.register(jsRunReleaseTask) {
                            group = "kameleon web"
                            description = "Runs the JS production/release server for variant '${variant.name}'."
                            (project.tasks.findByName("jsBrowserProductionRun")
                                ?: project.tasks.findByName("jsBrowserDevelopmentRun"))?.let { dependsOn(it) }
                            doFirst {
                                setKameleonFlavorSystemProperties(extension, variant)
                            }
                        }
                    }

                    val jsDistTask = "js${capVariant}BrowserDistribution"
                    if (project.tasks.findByName(jsDistTask) == null) {
                        project.tasks.register<Copy>(jsDistTask) {
                            group = "kameleon web"
                            description = "Executes JS browser distribution for variant '${variant.name}'."
                            project.tasks.findByName("jsBrowserDistribution")?.let { dependsOn(it) }
                            from(project.layout.buildDirectory.dir("dist/js/productionExecutable"))
                            into(project.layout.buildDirectory.dir("dist/js/${variant.name}"))
                        }
                    }
                }
            }

            val activeVariant = resolveActiveVariant(project, extension)
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
                    val combination = variants.find { it.name.equals(activeVariant, ignoreCase = true) }
                    flavorParts.set(combination?.flavorNames ?: emptyList())
                    fields.set(project.provider {
                        resolveFieldsForVariant(
                            extension,
                            combination ?: VariantCombination(activeVariant, emptyList())
                        )
                    })
                    outputDir.convention(
                        project.layout.buildDirectory.dir("generated/kameleon/buildconfig/$activeVariant")
                    )
                }
            }

            if (project.tasks.findByName("generateKameleonConfig") == null) {
                project.tasks.register("generateKameleonConfig") {
                    group = "kameleon"
                    description = "Generates type-safe KameleonConfig for the active variant ('$activeVariant')."
                    dependsOn(activeGenTask)
                }
            }

            // Wire into Kotlin Multiplatform source sets
            if (kotlinExt != null) {
                kotlinExt.sourceSets.findByName("commonMain")?.kotlin?.srcDir(activeGenTask.map { it.outputDir })
            }

            if (hasIos) {
                project.tasks.findByName("embedAndSignAppleFrameworkForXcode")?.let { embedTask ->
                    embedTask.doFirst {
                        val xcodeFlavor = System.getenv("FLAVOR")
                            ?: System.getenv("SCHEME")
                            ?: System.getenv("CONFIGURATION")
                        if (!xcodeFlavor.isNullOrBlank()) {
                            System.setProperty("kameleon.flavor", xcodeFlavor.lowercase())
                        }
                    }
                }
            }
        }
    }

    private fun setKameleonFlavorSystemProperties(extension: KameleonExtension, variant: VariantCombination) {
        System.setProperty("kameleon.flavor", variant.name)
        val orderedDims = extension.getOrderedDimensions().filter { it.flavors.isNotEmpty() }
        if (orderedDims.size == variant.flavorNames.size) {
            for (i in orderedDims.indices) {
                System.setProperty("kameleon.flavor.${orderedDims[i].name}", variant.flavorNames[i])
            }
        }
    }
}
