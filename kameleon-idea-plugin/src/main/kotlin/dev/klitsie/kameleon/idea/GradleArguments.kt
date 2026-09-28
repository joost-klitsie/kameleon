package dev.klitsie.kameleon.idea

/**
 * Builds Gradle command-line flavor property arguments based on active selections.
 *
 * Dimensionless / default setups produce `-Pkameleon.flavor=<flavor>`.
 * Explicit dimensional setups produce `-Pkameleon.flavor.<dimension>=<flavor>`.
 */
fun buildGradleFlavorArguments(activeSelections: Map<String?, String>): List<String> {
    return activeSelections.mapNotNull { (dimension, flavor) ->
        when {
            // Dimensionless / default flavor setup
            dimension.isNullOrBlank() || dimension.equals("default", ignoreCase = true) -> {
                "-Pkameleon.flavor=$flavor"
            }
            // Explicit dimension
            else -> {
                "-Pkameleon.flavor.$dimension=$flavor"
            }
        }
    }
}

/**
 * Injects or replaces Kameleon flavor parameters within a Gradle scriptParameters string.
 */
fun injectGradleFlavorArguments(currentParams: String?, flags: List<String>): String {
    if (flags.isEmpty()) return currentParams ?: ""
    val flagsString = flags.joinToString(" ")
    val params = currentParams ?: ""
    val flavorRegex = Regex("""-Pkameleon\.flavor(?:\.[^\s=]+)?=\S+""")
    if (!flavorRegex.containsMatchIn(params)) {
        return if (params.isBlank()) flagsString else "$params $flagsString"
    }
    var first = true
    val replaced = flavorRegex.replace(params) {
        if (first) {
            first = false
            flagsString
        } else {
            ""
        }
    }
    return replaced.split(Regex("""\s+""")).filter { it.isNotBlank() }.joinToString(" ")
}
