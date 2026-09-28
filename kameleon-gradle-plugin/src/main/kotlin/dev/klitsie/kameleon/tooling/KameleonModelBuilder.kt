package dev.klitsie.kameleon.tooling

import dev.klitsie.kameleon.VariantMatrix
import dev.klitsie.kameleon.dsl.KameleonExtension
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder

class KameleonModelBuilder(private val extension: KameleonExtension) : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean {
        return modelName == KameleonToolingModel::class.java.name
    }

    override fun buildAll(modelName: String, project: Project): Any {
        val orderedDimensions = extension.getOrderedDimensions()
        val dimMap = mutableMapOf<String, List<String>>()
        for (dim in orderedDimensions) {
            val flavors = dim.getOrderedFlavors().map { it.name }
            if (flavors.isNotEmpty()) {
                dimMap[dim.name] = flavors
            }
        }

        // If no dimensions are explicitly configured but flavors exist, create an implicit "default" dimension
        if (dimMap.isEmpty()) {
            val fallbackFlavors = extension.flavors.map { it.name }
            if (fallbackFlavors.isNotEmpty()) {
                dimMap["default"] = fallbackFlavors
            }
        }

        val variants = VariantMatrix.calculateVariants(orderedDimensions)
        val availableVariants = variants.map { it.name }.distinct()
        val defaultVariant = extension.defaultVariant.getOrElse("staging")
        return DefaultKameleonToolingModel(
            projectPath = project.path,
            dimensions = dimMap,
            availableVariants = availableVariants,
            defaultVariant = defaultVariant
        )
    }
}
