package dev.klitsie.kameleon.sample.features

object FeatureGate {
    val tierName: String = "Whoop Elite Plan"
    val canExportData: Boolean = true
    val canAccessBetaFeatures: Boolean = true
    val allowsCustomIntegrations: Boolean = true
    val maxCloudStorageGb: Int = 500
    val unlockedCapabilities: List<String> = listOf(
        "⚡ Ultra-low latency data sync",
        "🔒 Enterprise SSO & SAML 2.0",
        "📊 Advanced telemetry analytics",
        "🛠 Automated CI/CD webhooks",
        "🌐 Dedicated edge caching"
    )
}
