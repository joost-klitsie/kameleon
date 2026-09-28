package dev.klitsie.kameleon

data class VariantCombination(
    val name: String,
    val flavorNames: List<String>
)

object VariantMatrix {
    fun calculateVariants(dimensions: List<KameleonDimension>): List<VariantCombination> {
        val nonEmptyDimensions = dimensions.filter { it.flavors.isNotEmpty() }
        if (nonEmptyDimensions.isEmpty()) {
            return listOf(VariantCombination("default", emptyList()))
        }

        var product = listOf(listOf<String>())
        for (dim in nonEmptyDimensions) {
            val flavorNames = dim.getOrderedFlavors().map { it.name }
            product = product.flatMap { existing ->
                flavorNames.map { flavorName ->
                    existing + flavorName
                }
            }
        }

        return product.map { flavorList ->
            val variantName = buildVariantName(flavorList)
            VariantCombination(variantName, flavorList)
        }
    }

    fun buildVariantName(flavorNames: List<String>): String {
        if (flavorNames.isEmpty()) return "default"
        val first = flavorNames.first()
        val rest = flavorNames.drop(1).joinToString("") { name ->
            name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return first + rest
    }
}
