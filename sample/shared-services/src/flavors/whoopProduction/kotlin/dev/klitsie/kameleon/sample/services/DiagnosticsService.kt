package dev.klitsie.kameleon.sample.services

object DiagnosticsService {
    fun getDiagnostics(): DiagnosticsInfo {
        return DiagnosticsInfo(
            environment = "PRODUCTION",
            logLevel = "ERROR",
            isSandboxMode = false,
            activeFeatures = listOf(
                "⚡ Whoop Enterprise Live Engine",
                "🔒 SSO / SAML Hardened Auth",
                "📈 99.999% SLA Failover",
                "🛡 Real-time DDoS Mitigation"
            ),
            mockTokens = emptyList(),
            telemetryEndpoint = "https://telemetry.whoop.io/v1/metrics",
            combinationName = "whoopProduction"
        )
    }

    fun isFeatureAvailable(featureKey: String): Boolean = featureKey != "experimental"
}
