package dev.klitsie.kameleon.sample.services

object DiagnosticsService {
    fun getDiagnostics(): DiagnosticsInfo {
        return DiagnosticsInfo(
            environment = "STAGING",
            logLevel = "VERBOSE_WHOOP",
            isSandboxMode = true,
            activeFeatures = listOf(
                "⚡ Whoop Hyper-Speed Sync",
                "🛠 Staging Mock Cluster",
                "🔍 Debug Profiler Pro",
                "🚀 Experimental Turbo Cache"
            ),
            mockTokens = listOf("whoop-stg-token-999", "admin-debug-sandbox"),
            telemetryEndpoint = "https://staging-telemetry.whoop.io/traces",
            combinationName = "whoopStaging"
        )
    }

    fun isFeatureAvailable(featureKey: String): Boolean = true
}
