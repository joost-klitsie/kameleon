package dev.klitsie.kameleon

import com.android.build.api.dsl.ApplicationExtension
import dev.klitsie.kameleon.dsl.BuildConfigValue
import dev.klitsie.kameleon.dsl.KameleonExtension
import dev.klitsie.kameleon.targets.AndroidProjectTarget
import dev.klitsie.kameleon.targets.ComposeResourcesTarget
import dev.klitsie.kameleon.targets.JavaProjectTarget
import dev.klitsie.kameleon.targets.KotlinMultiplatformTarget
import dev.klitsie.kameleon.tooling.KameleonModelBuilder
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import javax.inject.Inject

class KameleonPlugin @Inject constructor(
    private val registry: ToolingModelBuilderRegistry
) : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("kameleon", KameleonExtension::class.java)

        registry.register(KameleonModelBuilder(extension))

        configureConventions(project, extension)
        configureAttributeSchema(project, extension)

        val composeTarget = ComposeResourcesTarget(project, extension)
        val mergeTask = composeTarget.configure()

        AndroidProjectTarget(project, extension).configure()
        KotlinMultiplatformTarget(project, extension, mergeTask).configure()
        JavaProjectTarget(project, extension).configure()
    }

    private fun configureConventions(project: Project, extension: KameleonExtension) {
        val defaultPackageProvider = project.provider {
            try {
                val androidApp = project.extensions.findByType(ApplicationExtension::class.java)
                val androidNamespace = androidApp?.namespace

                if (!androidNamespace.isNullOrBlank()) {
                    androidNamespace
                } else {
                    "dev.klitsie.kameleon"
                }
            } catch (_: Throwable) {
                "dev.klitsie.kameleon"
            }
        }
        extension.defaultBuildConfig.packageName.convention(defaultPackageProvider)
    }

    private fun configureAttributeSchema(project: Project, extension: KameleonExtension) {
        project.dependencies.attributesSchema {
            attribute(FlavorAttribute.FLAVOR_ATTRIBUTE) {
                compatibilityRules.add(CaseInsensitiveCompatibilityRule::class.java)
                disambiguationRules.add(FlavorDisambiguationRule::class.java) {
                    params(extension.activeVariant.getOrElse("default"))
                }
            }
        }
    }
}

internal fun resolveFieldsForVariant(
    extension: KameleonExtension,
    variant: VariantCombination
): Map<String, BuildConfigValue> {
    val resolved = mutableMapOf<String, BuildConfigValue>()

    // 1. Root defaultBuildConfig
    resolved.putAll(extension.defaultBuildConfig.fields.get())

    // 2. Dimension overrides
    for (dim in extension.getOrderedDimensions()) {
        resolved.putAll(dim.buildConfig.fields.get())
    }

    // 3. Flavor overrides (in order of constituent flavors)
    for (flavorName in variant.flavorNames) {
        for (dim in extension.getOrderedDimensions()) {
            val flavor = dim.flavors.findByName(flavorName)
            if (flavor != null) {
                resolved.putAll(flavor.buildConfig.fields.get())
            }
        }
    }

    // 4. Variant-level combination flavor override (if a flavor matches full variant name)
    for (dim in extension.getOrderedDimensions()) {
        val variantFlavor = dim.flavors.findByName(variant.name)
        if (variantFlavor != null) {
            resolved.putAll(variantFlavor.buildConfig.fields.get())
        }
    }

    return resolved
}

internal fun resolveActiveVariant(project: Project, extension: KameleonExtension): String {
    val orderedDims = extension.getOrderedDimensions().filter { it.flavors.isNotEmpty() }
    val variants = VariantMatrix.calculateVariants(orderedDims)

    if (orderedDims.size > 1) {
        val dimFlavors = orderedDims.mapNotNull { dim ->
            val flavor = project.findProperty("kameleon.flavor.${dim.name}")?.toString()
                ?: project.providers.gradleProperty("kameleon.flavor.${dim.name}").orNull
                ?: System.getProperty("kameleon.flavor.${dim.name}")
            if (!flavor.isNullOrBlank()) flavor else null
        }
        if (dimFlavors.size == orderedDims.size) {
            return VariantMatrix.buildVariantName(dimFlavors)
        }
    }

    if (orderedDims.size == 1) {
        val dimName = orderedDims.first().name
        val dimFlavor = project.findProperty("kameleon.flavor.$dimName")?.toString()
            ?: project.providers.gradleProperty("kameleon.flavor.$dimName").orNull
            ?: System.getProperty("kameleon.flavor.$dimName")
        if (!dimFlavor.isNullOrBlank()) return dimFlavor
    }

    val cli = project.findProperty("kameleon.flavor")?.toString()
        ?: project.providers.gradleProperty("kameleon.flavor").orNull
        ?: System.getProperty("kameleon.flavor")
    if (!cli.isNullOrBlank()) {
        val directMatch = variants.find { it.name.equals(cli, ignoreCase = true) }
        if (directMatch != null) {
            return directMatch.name
        }
        if (orderedDims.isNotEmpty()) {
            val matchingFlavors = orderedDims.mapNotNull { dim ->
                dim.flavors.find { f -> cli.contains(f.name, ignoreCase = true) }?.name
            }
            if (matchingFlavors.size == orderedDims.size) {
                return if (matchingFlavors.size == 1) {
                    matchingFlavors.first()
                } else {
                    VariantMatrix.buildVariantName(matchingFlavors)
                }
            }
        }
        return cli
    }

    val requestedTasks = project.gradle.startParameter.taskNames

    for (taskPath in requestedTasks) {
        val taskName = taskPath.substringAfterLast(':')
        for (variant in variants) {
            val capVariant = variant.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            if (taskName.contains(capVariant, ignoreCase = false) ||
                taskName.endsWith(variant.name, ignoreCase = true) ||
                taskName.contains(variant.name, ignoreCase = true)) {
                return variant.name
            }
        }
    }

    for (taskPath in requestedTasks) {
        val taskName = taskPath.substringAfterLast(':')
        for (dim in extension.dimensions) {
            for (flavor in dim.flavors) {
                val capFlavor = flavor.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                if (taskName.contains(capFlavor, ignoreCase = false) ||
                    taskName.endsWith(flavor.name, ignoreCase = true) ||
                    taskName.contains(flavor.name, ignoreCase = true)) {
                    val matchingVariant = variants.find { it.flavorNames.contains(flavor.name) }
                    if (matchingVariant != null) {
                        return matchingVariant.name
                    }
                    return flavor.name
                }
            }
        }
    }

    val activeVal = extension.activeVariant.orNull
    if (!activeVal.isNullOrBlank() && activeVal != "default") {
        return activeVal
    }
    val defaultVal = extension.defaultVariant.orNull
    if (!defaultVal.isNullOrBlank() && defaultVal != "default") {
        return defaultVal
    }
    return variants.firstOrNull()?.name ?: activeVal ?: extension.defaultVariant.getOrElse("staging")
}

internal fun resolveOverlayDirsForVariantName(project: Project, extension: KameleonExtension, variantName: String): List<org.gradle.api.file.Directory> {
    val variants = VariantMatrix.calculateVariants(extension.getOrderedDimensions())
    val combination = variants.find { it.name.equals(variantName, ignoreCase = true) }
    val flavorNames = combination?.flavorNames ?: listOf(variantName)
    val dirs = mutableListOf<org.gradle.api.file.Directory>()
    for (fName in flavorNames) {
        val dir = project.file("src/flavors/$fName/composeResources")
        if (dir.exists()) {
            dirs.add(project.layout.projectDirectory.dir(dir.relativeTo(project.projectDir).path))
        }
    }
    if (combination != null && !combination.flavorNames.contains(combination.name)) {
        val dir = project.file("src/flavors/${combination.name}/composeResources")
        if (dir.exists()) {
            dirs.add(project.layout.projectDirectory.dir(dir.relativeTo(project.projectDir).path))
        }
    } else if (combination == null) {
        val dir = project.file("src/flavors/$variantName/composeResources")
        if (dir.exists() && !dirs.any { it.asFile == dir }) {
            dirs.add(project.layout.projectDirectory.dir(dir.relativeTo(project.projectDir).path))
        }
    }
    return dirs
}

fun Project.resolveFlavorForDimension(dim: String): Provider<String> {
    return providers.gradleProperty("kameleon.flavor.$dim")
        .orElse(providers.gradleProperty("kameleon.flavor"))
}

fun Project.resolveDefaultFlavor(): Provider<String> {
    return providers.gradleProperty("kameleon.flavor")
}

fun Project.getKameleonFlavor(dimension: String = "default"): Provider<String> {
    return if (dimension.isBlank() || dimension.equals("default", ignoreCase = true)) {
        resolveDefaultFlavor()
    } else {
        resolveFlavorForDimension(dimension)
    }
}
