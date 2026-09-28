package dev.klitsie.kameleon.sample.services

object DiagnosticsService {
    fun getDiagnostics(): DiagnosticsInfo {
        return DiagnosticsInfo(
            environment = "PRODUCTION",
            logLevel = "WARN",
            isSandboxMode = false,
            activeFeatures = listOf(
                "🪽 Flappy Lightweight Core",
                "👥 Community Cloud Telemetry",
                "🚀 Global Edge CDN Distribution",
                "🔒 Standard TLS Encryption"
            ),
            mockTokens = emptyList(),
            telemetryEndpoint = "https://telemetry.flappy.dev/v1/metrics",
            combinationName = "flappyProduction"
        )
    }

    fun isFeatureAvailable(featureKey: String): Boolean = featureKey != "experimental"
}
