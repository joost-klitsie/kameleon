package dev.klitsie.kameleon.tooling

import java.io.Serializable

interface KameleonToolingModel : Serializable {
    val projectPath: String
    val dimensions: Map<String, List<String>> // dimension name -> list of flavor names
    val availableVariants: List<String>      // calculated composite variant names
    val defaultVariant: String
}

data class DefaultKameleonToolingModel(
    override val projectPath: String,
    override val dimensions: Map<String, List<String>>,
    override val availableVariants: List<String>,
    override val defaultVariant: String,
) : KameleonToolingModel
