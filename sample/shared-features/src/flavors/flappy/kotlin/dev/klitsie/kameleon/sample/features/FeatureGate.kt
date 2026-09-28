package dev.klitsie.kameleon.sample.features

object FeatureGate {
    val tierName: String = "Flappy Starter Plan"
    val canExportData: Boolean = false
    val canAccessBetaFeatures: Boolean = false
    val allowsCustomIntegrations: Boolean = false
    val maxCloudStorageGb: Int = 5
    val unlockedCapabilities: List<String> = listOf(
        "🪽 Lightweight runtime engine",
        "👥 Open Community support",
        "📄 Basic Markdown report export"
    )
}
