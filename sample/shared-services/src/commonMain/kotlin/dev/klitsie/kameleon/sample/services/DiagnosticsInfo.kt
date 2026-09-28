package dev.klitsie.kameleon.sample.services

data class DiagnosticsInfo(
    val environment: String,
    val logLevel: String,
    val isSandboxMode: Boolean,
    val activeFeatures: List<String>,
    val mockTokens: List<String>,
    val telemetryEndpoint: String,
    val combinationName: String
)
