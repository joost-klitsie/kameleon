package dev.klitsie.kameleon.targets

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.Variant
import dev.klitsie.kameleon.FlavorAttribute
import dev.klitsie.kameleon.VariantCombination
import dev.klitsie.kameleon.VariantMatrix
import dev.klitsie.kameleon.dsl.KameleonExtension
import dev.klitsie.kameleon.resolveFieldsForVariant
import dev.klitsie.kameleon.resolveHoconEntriesForVariant
import dev.klitsie.kameleon.resolveHoconFileNameForVariant
import dev.klitsie.kameleon.resolveOverlayDirsForVariantName
import dev.klitsie.kameleon.tasks.GenerateHoconConfigTask
import dev.klitsie.kameleon.tasks.GenerateKameleonConfigTask
import dev.klitsie.kameleon.tasks.MergeComposeResourcesTask
import org.gradle.api.GradleException
import org.gradle.api.Project

class AndroidProjectTarget(
    private val project: Project,
    private val extension: KameleonExtension
) : TargetAdapter {

    override fun configure() {
        project.plugins.withId("com.android.application") {
            configureApplication()
        }

        project.plugins.withId("com.android.library") {
            configureLibrary()
        }
    }

    private fun configureApplication() {
        configureAndroidVariantConsumer(project)
        val androidComponents = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        val androidDsl = project.extensions.getByType(ApplicationExtension::class.java)

        androidComponents.finalizeDsl {
            val agpProductFlavors = androidDsl.productFlavors
            val agpHasFlavors = agpProductFlavors.isNotEmpty()
            val kameleonFlavors = extension.getOrderedDimensions().flatMap { it.getOrderedFlavors() }

            if (agpHasFlavors) {
                val agpFlavorNames = agpProductFlavors.map { it.name }.toSet()
                for (kFlavor in kameleonFlavors) {
                    if (!agpFlavorNames.contains(kFlavor.name)) {
                        throw GradleException(
                            """
                            [kameleon] Invalid flavor configuration in project '${project.name}':
                            Flavor '${kFlavor.name}' was configured in 'kameleon { ... }' but is not defined in 'android { productFlavors { ... } }'.
                            In an Android application project, AGP defines the flavor matrix as the source of truth, and kameleon can only decorate existing flavors.
                            """.trimIndent()
                        )
                    }
                }

                androidDsl.flavorDimensions.forEach { dimName ->
                    if (extension.dimensions.findByName(dimName) == null) {
                        extension.dimension(dimName)
                    }
                }

                agpProductFlavors.all {
                    val agpFlavor = this
                    val dimName = agpFlavor.dimension ?: return@all
                    val targetDim = extension.dimensions.findByName(dimName) ?: extension.dimension(dimName)

                    val existingKFlavor = targetDim.flavors.findByName(agpFlavor.name)
                    if (existingKFlavor == null) {
                        targetDim.flavor(agpFlavor.name) {
                            applicationIdSuffix = agpFlavor.applicationIdSuffix
                        }
                    } else {
                        if (existingKFlavor.applicationIdSuffix != null && agpFlavor.applicationIdSuffix == null) {
                            agpFlavor.applicationIdSuffix = existingKFlavor.applicationIdSuffix
                        }
                    }
                }
            } else if (kameleonFlavors.isNotEmpty()) {
                extension.getOrderedDimensions().forEach { dim ->
                    if (!androidDsl.flavorDimensions.contains(dim.name)) {
                        androidDsl.flavorDimensions.add(dim.name)
                    }
                    dim.getOrderedFlavors().forEach { flavor ->
                        if (androidDsl.productFlavors.findByName(flavor.name) == null) {
                            androidDsl.productFlavors.register(flavor.name) {
                                dimension = dim.name
                                if (flavor.applicationIdSuffix != null) {
                                    applicationIdSuffix = flavor.applicationIdSuffix
                                }
                            }
                        }
                    }
                }
            }
        }

        androidComponents.onVariants { variant ->
            registerVariantResourceMerge(variant)
        }
    }

    private fun configureLibrary() {
        configureAndroidVariantConsumer(project)
        val androidComponents = project.extensions.findByType(LibraryAndroidComponentsExtension::class.java)
        val androidDsl = project.extensions.findByType(LibraryExtension::class.java)
        if (androidComponents != null && androidDsl != null) {
            androidComponents.finalizeDsl {
                val agpProductFlavors = androidDsl.productFlavors
                val agpHasFlavors = agpProductFlavors.isNotEmpty()
                val kameleonFlavors = extension.getOrderedDimensions().flatMap { it.getOrderedFlavors() }

                if (agpHasFlavors) {
                    val agpFlavorNames = agpProductFlavors.map { it.name }.toSet()
                    for (kFlavor in kameleonFlavors) {
                        if (!agpFlavorNames.contains(kFlavor.name)) {
                            throw GradleException(
                                """
                                [kameleon] Invalid flavor configuration in project '${project.name}':
                                Flavor '${kFlavor.name}' was configured in 'kameleon { ... }' but is not defined in 'android { productFlavors { ... } }'.
                                In an Android library project, AGP defines the flavor matrix as the source of truth, and kameleon can only decorate existing flavors.
                                """.trimIndent()
                            )
                        }
                    }

                    androidDsl.flavorDimensions.forEach { dimName ->
                        if (extension.dimensions.findByName(dimName) == null) {
                            extension.dimension(dimName)
                        }
                    }
                    agpProductFlavors.all {
                        val agpFlavor = this
                        val dimName = agpFlavor.dimension ?: return@all
                        val targetDim = extension.dimensions.findByName(dimName) ?: extension.dimension(dimName)

                        if (targetDim.flavors.findByName(agpFlavor.name) == null) {
                            targetDim.flavor(agpFlavor.name)
                        }
                    }
                } else if (kameleonFlavors.isNotEmpty()) {
                    extension.getOrderedDimensions().forEach { dim ->
                        if (!androidDsl.flavorDimensions.contains(dim.name)) {
                            androidDsl.flavorDimensions.add(dim.name)
                        }
                        dim.getOrderedFlavors().forEach { flavor ->
                            if (androidDsl.productFlavors.findByName(flavor.name) == null) {
                                androidDsl.productFlavors.register(flavor.name) {
                                    dimension = dim.name
                                }
                            }
                        }
                    }
                }
            }

            androidComponents.onVariants { variant ->
                registerVariantResourceMerge(variant)
            }
        }
    }

    private fun registerVariantResourceMerge(variant: Variant) {
        val variantName = variant.flavorName ?: ""
        if (variantName.isNotEmpty()) {
            val capVariant = variantName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            val taskName = "mergeKameleon${capVariant}Resources"
            val variantMergeTask = if (project.tasks.findByName(taskName) != null) {
                project.tasks.named(taskName, MergeComposeResourcesTask::class.java)
            } else {
                project.tasks.register(taskName, MergeComposeResourcesTask::class.java) {
                    baseDir.set(
                        project.provider {
                            val base = extension.baseResourceDir.orNull?.asFile
                            if (base != null && base.exists()) extension.baseResourceDir.get() else null
                        }
                    )
                    overlayDirs.set(
                        project.provider {
                            resolveOverlayDirsForVariantName(project, extension, variantName)
                        }
                    )
                    outputDir.convention(project.layout.buildDirectory.dir("generated/kameleon/composeResources/$variantName"))
                }
            }
            variant.sources.res?.addGeneratedSourceDirectory(
                variantMergeTask,
                MergeComposeResourcesTask::outputDir
            )

            val hoconTaskName = "generateKameleon${capVariant}HoconConfig"
            val variantHoconTask = if (project.tasks.findByName(hoconTaskName) != null) {
                project.tasks.named(hoconTaskName, GenerateHoconConfigTask::class.java)
            } else {
                project.tasks.register(hoconTaskName, GenerateHoconConfigTask::class.java) {
                    val variants = VariantMatrix.calculateVariants(extension.getOrderedDimensions())
                    val comb = variants.find { it.name.equals(variantName, ignoreCase = true) }
                        ?: VariantCombination(variantName, listOf(variantName))
                    fileName.set(project.provider { resolveHoconFileNameForVariant(extension, comb) })
                    entries.set(project.provider { resolveHoconEntriesForVariant(extension, comb) })
                    outputDirectory.convention(project.layout.buildDirectory.dir("generated/kameleon/hocon/$variantName"))
                }
            }
            variant.sources.resources?.addGeneratedSourceDirectory(
                variantHoconTask,
                GenerateHoconConfigTask::outputDirectory
            )

            val configTaskName = "generateKameleon${capVariant}Config"
            val variantConfigTask = if (project.tasks.findByName(configTaskName) != null) {
                project.tasks.named(configTaskName, GenerateKameleonConfigTask::class.java)
            } else {
                project.tasks.register(configTaskName, GenerateKameleonConfigTask::class.java) {
                    group = "kameleon"
                    description = "Generates type-safe KameleonConfig for the '$variantName' variant."
                    packageName.set(extension.defaultBuildConfig.packageName)
                    className.set(extension.defaultBuildConfig.className)
                    this.variantName.set(variantName)
                    val variants = VariantMatrix.calculateVariants(extension.getOrderedDimensions())
                    val comb = variants.find { it.name.equals(variantName, ignoreCase = true) }
                        ?: VariantCombination(variantName, listOf(variantName))
                    flavorParts.set(comb.flavorNames)
                    fields.set(project.provider { resolveFieldsForVariant(extension, comb) })
                    outputDir.convention(
                        project.layout.buildDirectory.dir("generated/kameleon/buildconfig/$variantName")
                    )
                }
            }
            variant.sources.java?.addGeneratedSourceDirectory(
                variantConfigTask,
                GenerateKameleonConfigTask::outputDir
            )
        }
    }

    private fun configureAndroidVariantConsumer(project: Project) {
        val androidComponents = project.extensions.findByType(AndroidComponentsExtension::class.java) ?: return
        androidComponents.onVariants { variant ->
            val flavor = variant.flavorName ?: return@onVariants

            variant.compileConfiguration.attributes.attribute(FlavorAttribute.FLAVOR_ATTRIBUTE, flavor)
            variant.runtimeConfiguration.attributes.attribute(FlavorAttribute.FLAVOR_ATTRIBUTE, flavor)
        }
    }
}
