package dev.klitsie.kameleon.idea

object KameleonVariantMapper {

    /**
     * Resolves a Kameleon flavor to an exact AGP variant name.
     * Takes the current active AGP variant (e.g. "stagingDebug") to extract
     * the existing build type suffix, then produces the target variant (e.g. "productionDebug").
     */
    fun toAgpVariant(
        targetFlavor: String,
        currentAgpVariant: String,
        knownAgpVariants: Collection<String>,
    ): String? {
        // Fallback: If no current variant, guess "Debug"
        val buildType = if (currentAgpVariant.endsWith("Release", ignoreCase = true)) "Release" else "Debug"

        // Construct potential target name: lowercase flavor + capitalized buildType
        val candidate = "${targetFlavor.replaceFirstChar { it.lowercase() }}$buildType"

        // Verify this exact variant exists in AGP's registered variant list
        return knownAgpVariants.firstOrNull { it.equals(candidate, ignoreCase = true) }
            ?: knownAgpVariants.firstOrNull { it.contains(targetFlavor, ignoreCase = true) }
    }

    /**
     * Extracts the matching Kameleon flavor from a composite AGP variant string.
     * Example: "stagingDebug" with available flavors ["staging", "production"] -> "staging"
     */
    fun fromAgpVariant(
        agpVariantName: String,
        availableFlavors: Collection<String>,
    ): String? {
        return availableFlavors.firstOrNull { flavor ->
            agpVariantName.startsWith(flavor, ignoreCase = true) ||
                    agpVariantName.contains(flavor, ignoreCase = true)
        }
    }

    /**
     * Composes a camelCase variant name from a list of constituent flavor names.
     * E.g. ["brandA", "staging"] -> "brandAStaging"
     */
    fun buildVariantName(flavorNames: List<String>): String {
        if (flavorNames.isEmpty()) return "default"
        val first = flavorNames.first()
        val rest = flavorNames.drop(1).joinToString("") { name ->
            name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
        return first + rest
    }
}
