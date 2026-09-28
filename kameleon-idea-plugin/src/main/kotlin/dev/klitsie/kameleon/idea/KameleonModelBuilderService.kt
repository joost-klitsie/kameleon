package dev.klitsie.kameleon.idea

import dev.klitsie.kameleon.tooling.DefaultKameleonToolingModel
import dev.klitsie.kameleon.tooling.KameleonToolingModel
import org.gradle.api.Project
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService

class KameleonModelBuilderService : ModelBuilderService {

    override fun canBuild(modelName: String): Boolean {
        return modelName == KameleonToolingModel::class.java.name
    }

    override fun buildAll(modelName: String, project: Project): Any? {
        val extension = project.extensions.findByName("kameleon") ?: return null
        return try {
            val getOrderedDimensionsMethod =
                extension.javaClass.methods.firstOrNull { it.name == "getOrderedDimensions" }
            val dimensionsList = getOrderedDimensionsMethod?.invoke(extension) as? List<*> ?: emptyList<Any>()
            val dimMap = mutableMapOf<String, List<String>>()
            for (dim in dimensionsList) {
                if (dim == null) continue
                val getNameMethod = dim.javaClass.methods.firstOrNull { it.name == "getName" }
                val dimName = getNameMethod?.invoke(dim)?.toString() ?: continue
                val getOrderedFlavorsMethod = dim.javaClass.methods.firstOrNull { it.name == "getOrderedFlavors" }
                val flavorsList = getOrderedFlavorsMethod?.invoke(dim) as? List<*> ?: emptyList<Any>()
                val fNames = flavorsList.mapNotNull { f ->
                    f?.javaClass?.methods?.firstOrNull { it.name == "getName" }?.invoke(f)?.toString()
                }
                if (fNames.isNotEmpty()) {
                    dimMap[dimName] = fNames
                }
            }

            if (dimMap.isEmpty()) {
                val getFlavorsMethod = extension.javaClass.methods.firstOrNull { it.name == "getFlavors" }
                val flavorsContainer = getFlavorsMethod?.invoke(extension) as? Iterable<*>
                val fallbackFlavors = flavorsContainer?.mapNotNull { f ->
                    f?.javaClass?.methods?.firstOrNull { it.name == "getName" }?.invoke(f)?.toString()
                } ?: emptyList()
                if (fallbackFlavors.isNotEmpty()) {
                    dimMap["default"] = fallbackFlavors
                }
            }

            val getDefaultVariantMethod = extension.javaClass.methods.firstOrNull { it.name == "getDefaultVariant" }
            val defaultVariantProp = getDefaultVariantMethod?.invoke(extension)
            val defaultVariant = (defaultVariantProp?.javaClass?.methods?.firstOrNull { it.name == "getOrElse" }
                ?.invoke(defaultVariantProp, "staging")
                ?: defaultVariantProp?.javaClass?.methods?.firstOrNull { it.name == "getOrNull" }
                    ?.invoke(defaultVariantProp)
                ?: "staging").toString()

            val availableVariants = if (dimMap.isNotEmpty()) {
                var product = listOf(listOf<String>())
                for (fList in dimMap.values) {
                    product = product.flatMap { existing -> fList.map { existing + it } }
                }
                product.map { list ->
                    if (list.isEmpty()) "default"
                    else list.first() + list.drop(1)
                        .joinToString("") { it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase() else c.toString() } }
                }
            } else emptyList()

            DefaultKameleonToolingModel(
                projectPath = project.path,
                dimensions = dimMap,
                availableVariants = availableVariants,
                defaultVariant = defaultVariant,
            )
        } catch (_: Throwable) {
            null
        }
    }
}
