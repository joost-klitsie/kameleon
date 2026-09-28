package dev.klitsie.kameleon.sample.services

object DiagnosticsService {
    fun getDiagnostics(): DiagnosticsInfo {
        return DiagnosticsInfo(
            environment = "STAGING",
            logLevel = "DEBUG_FLAPPY",
            isSandboxMode = true,
            activeFeatures = listOf(
                "🪽 Flappy Test Sandbox",
                "🪶 Featherweight Mock Cache",
                "📝 Console Trace Logger",
                "🧪 Community Beta Testing"
            ),
            mockTokens = listOf("flappy-stg-test-token"),
            telemetryEndpoint = "https://staging-telemetry.flappy.dev/traces",
            combinationName = "flappyStaging"
        )
    }

    fun isFeatureAvailable(featureKey: String): Boolean = true
}
