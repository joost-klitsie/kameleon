package dev.klitsie.kameleon.sample.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.klitsie.kameleon.sample.app_title
import dev.klitsie.kameleon.sample.branding.*
import dev.klitsie.kameleon.sample.features.FeatureConfig
import dev.klitsie.kameleon.sample.features.FeatureGate
import dev.klitsie.kameleon.sample.greeting
import dev.klitsie.kameleon.sample.services.DiagnosticsService
import dev.klitsie.kameleon.sample.services.ServiceConfig
import dev.klitsie.kameleon.sample.translations.TranslationConfig
import dev.klitsie.kameleon.shared.TestClass
import org.jetbrains.compose.resources.stringResource
import dev.klitsie.kameleon.sample.Res as TranslationsRes
import dev.klitsie.kameleon.sample.branding.Res as BrandingRes

@Composable
fun App() {
    val diagnostics = DiagnosticsService.getDiagnostics()
    val brandPrimary = Color(BrandTheme.primaryColorHex)
    val brandAccent = Color(BrandTheme.accentColorHex)

    MaterialTheme {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Header / Hero Section
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = brandPrimary.copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, brandPrimary.copy(alpha = 0.4f)),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(TranslationsRes.string.app_title),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = brandPrimary,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(brandPrimary)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = BrandTheme.badge,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                )
                            }
                        }

                        Text(
                            text = "${stringResource(BrandingRes.string.brand_headline)} • ${stringResource(BrandingRes.string.brand_motto)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Text(
                            text = stringResource(TranslationsRes.string.greeting),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                // Dimension Subset Matrix Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                            alpha = 0.4f,
                        ),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "📐 Multi-Module Flavor Dimensions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Kameleon allows each library module to activate only a subset of flavor dimensions while the application module resolves the full matrix.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        ModuleDimensionRow(
                            moduleName = ":sample:shared-services",
                            dimensionSpec = "2 dimensions combined: 'brand' + 'environment' (whoopStaging | whoopProduction | flappyStaging | flappyProduction)",
                            activeValue = "${diagnostics.combinationName} (${diagnostics.environment})",
                        )
                        ModuleDimensionRow(
                            moduleName = ":sample:shared-branding",
                            dimensionSpec = "1 dimension subset: 'brand' (whoop | flappy)",
                            activeValue = BrandTheme.brandName,
                        )
                        ModuleDimensionRow(
                            moduleName = ":sample:shared-features",
                            dimensionSpec = "1 dimension subset: 'brand' (whoop | flappy)",
                            activeValue = FeatureGate.tierName,
                        )
                        ModuleDimensionRow(
                            moduleName = ":sample:shared-translations",
                            dimensionSpec = "1 dimension subset: 'environment' (staging | production)",
                            activeValue = TestClass().value,
                        )
                    }
                }

                // Flavored Classes & Code Feature Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "🧩 Flavored Code & Classes (Coding Feature)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Source files swapped per flavor across single-dimension and multi-dimension modules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider()

                        // Feature Gate details
                        InfoRow(label = "Feature Tier", value = FeatureGate.tierName)
                        InfoRow(label = "Cloud Storage Quota", value = "${FeatureGate.maxCloudStorageGb} GB")
                        InfoRow(
                            label = "Export Capabilities",
                            value = if (FeatureGate.canExportData) "Unlocked (CSV / JSON / PDF)" else "Basic only",
                        )
                        InfoRow(
                            label = "Beta Features Access",
                            value = if (FeatureGate.canAccessBetaFeatures) "Enabled" else "Restricted",
                        )

                        Text(
                            text = "Unlocked Capabilities:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            for (cap in FeatureGate.unlockedCapabilities) {
                                Text(
                                    text = "• $cap",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // Diagnostics details (2 dimensions combined)
                        InfoRow(label = "Active 2D Combination", value = diagnostics.combinationName)
                        InfoRow(
                            label = "Environment & Log Level",
                            value = "${diagnostics.environment} (${diagnostics.logLevel})",
                        )
                        InfoRow(
                            label = "Sandbox Mode Active",
                            value = if (diagnostics.isSandboxMode) "Yes (Mock services)" else "No (Production live)",
                        )
                        InfoRow(label = "Telemetry Ingestion", value = diagnostics.telemetryEndpoint)

                        if (diagnostics.mockTokens.isNotEmpty()) {
                            InfoRow(label = "Mock Dev Tokens", value = diagnostics.mockTokens.joinToString(", "))
                        }

                        // Translations flavored test class
                        InfoRow(label = "TestClass Flavor Output", value = TestClass().value)
                    }
                }

                // Build Config Feature Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "⚙️ Generated BuildConfig (BuildConfig Feature)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Type-safe BuildConfig constants generated per module with custom packages and class names.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider()

                        // BrandConfig
                        SectionHeader(title = "BrandConfig (dev.klitsie.kameleon.sample.branding.BrandConfig)")
                        ConfigKeyValueRow(key = "BRAND_NAME", value = BrandConfig.BRAND_NAME)
                        ConfigKeyValueRow(key = "BRAND_CODE", value = BrandConfig.BRAND_CODE)
                        ConfigKeyValueRow(key = "SUPPORT_EMAIL", value = BrandConfig.SUPPORT_EMAIL)
                        ConfigKeyValueRow(key = "MAX_PROJECTS", value = "${BrandConfig.MAX_PROJECTS}")
                        ConfigKeyValueRow(key = "FLAVOR", value = BrandConfig.FLAVOR)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // ServiceConfig
                        SectionHeader(title = "ServiceConfig (dev.klitsie.kameleon.sample.services.ServiceConfig)")
                        ConfigKeyValueRow(key = "API_ENDPOINT", value = ServiceConfig.API_ENDPOINT)
                        ConfigKeyValueRow(key = "ENABLE_MOCK_DATA", value = "${ServiceConfig.ENABLE_MOCK_DATA}")
                        ConfigKeyValueRow(key = "REQUEST_TIMEOUT_MS", value = "${ServiceConfig.REQUEST_TIMEOUT_MS} ms")
                        ConfigKeyValueRow(key = "CACHE_TTL_SECONDS", value = "${ServiceConfig.CACHE_TTL_SECONDS} s")
                        ConfigKeyValueRow(key = "FLAVOR", value = ServiceConfig.FLAVOR)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // FeatureConfig
                        SectionHeader(title = "FeatureConfig (dev.klitsie.kameleon.sample.features.FeatureConfig)")
                        ConfigKeyValueRow(key = "TIER_LEVEL", value = FeatureConfig.TIER_LEVEL)
                        ConfigKeyValueRow(key = "BILLING_PLAN", value = FeatureConfig.BILLING_PLAN)
                        ConfigKeyValueRow(key = "MAX_CONCURRENT_USERS", value = "${FeatureConfig.MAX_CONCURRENT_USERS}")
                        ConfigKeyValueRow(
                            key = "ENABLE_EXPERIMENTAL_AI",
                            value = "${FeatureConfig.ENABLE_EXPERIMENTAL_AI}",
                        )
                        ConfigKeyValueRow(key = "FLAVOR", value = FeatureConfig.FLAVOR)

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // TranslationConfig
                        SectionHeader(title = "TranslationConfig (dev.klitsie.kameleon.sample.translations.TranslationConfig)")
                        ConfigKeyValueRow(key = "DEFAULT_LOCALE", value = TranslationConfig.DEFAULT_LOCALE)
                        ConfigKeyValueRow(
                            key = "SUPPORTED_LOCALES_COUNT",
                            value = "${TranslationConfig.SUPPORTED_LOCALES_COUNT}",
                        )
                        ConfigKeyValueRow(
                            key = "ENABLE_TRANSLATIONS_LOGGING",
                            value = "${TranslationConfig.ENABLE_TRANSLATIONS_LOGGING}",
                        )
                        ConfigKeyValueRow(key = "FLAVOR", value = TranslationConfig.FLAVOR)
                    }
                }

                // Merged Resources Demonstration Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "🗂 Compose Resources Merging",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Strings and assets overlaid automatically from flavor directories (e.g. src/flavors/{flavor}/composeResources).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        HorizontalDivider()

                        InfoRow(
                            label = "Translations app_title",
                            value = stringResource(TranslationsRes.string.app_title),
                        )
                        InfoRow(
                            label = "Translations greeting",
                            value = stringResource(TranslationsRes.string.greeting),
                        )
                        InfoRow(
                            label = "Branding brand_headline",
                            value = stringResource(BrandingRes.string.brand_headline),
                        )
                        InfoRow(
                            label = "Branding brand_badge_text",
                            value = stringResource(BrandingRes.string.brand_badge_text),
                        )
                        InfoRow(
                            label = "Branding brand_motto",
                            value = stringResource(BrandingRes.string.brand_motto),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleDimensionRow(moduleName: String, dimensionSpec: String, activeValue: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = moduleName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = activeValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = dimensionSpec,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1.2f),
        )
    }
}

@Composable
private fun ConfigKeyValueRow(key: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
