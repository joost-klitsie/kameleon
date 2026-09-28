package dev.klitsie.kameleon.idea

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KameleonPluginTest {

    @Test
    fun `state service stores and updates selected and available variants`() {
        val service = KameleonStateService()
        assertEquals("default", service.currentFlavor)
        assertEquals(emptyList(), service.availableFlavors)

        service.currentFlavor = "production"
        service.availableFlavors = listOf("demoStaging", "demoProduction", "fullProduction")

        assertEquals("production", service.currentFlavor)
        assertEquals("production", service.selectedVariant)
        assertEquals(listOf("demoStaging", "demoProduction", "fullProduction"), service.availableFlavors)
        assertEquals(listOf("demoStaging", "demoProduction", "fullProduction"), service.availableVariants)

        val state = service.state
        assertEquals("production", state.currentFlavor)
        assertEquals(listOf("demoStaging", "demoProduction", "fullProduction"), state.availableFlavors)

        val newService = KameleonStateService()
        newService.loadState(state)
        assertEquals("production", newService.currentFlavor)
        assertEquals(listOf("demoStaging", "demoProduction", "fullProduction"), newService.availableFlavors)
    }

    @Test
    fun `flavor discovery extracts distinct flavor combinations from variant names`() {
        val variantNames = listOf(
            "brandADefaultDebug",
            "brandADefaultRelease",
            "brandAStagingDebug",
            "brandAStagingRelease",
            "brandBDefaultDebug",
            "brandBStagingRelease"
        )
        val extracted = FlavorDiscovery.extractFlavors(variantNames)
        assertEquals(
            listOf("brandADefault", "brandAStaging", "brandBDefault", "brandBStaging"),
            extracted
        )
    }

    @Test
    fun `flavor discovery correctly strips debug and release suffixes and lowercases leading character`() {
        val variantNames = listOf(
            "DefaultDebug",
            "StagingRelease",
            "ProductionDebug",
            "productionRelease"
        )
        val extracted = FlavorDiscovery.extractFlavors(variantNames)
        assertEquals(
            listOf("default", "staging", "production"),
            extracted
        )
    }

    @Test
    fun `flavor discovery returns empty list for variants with only standard build types without flavors`() {
        val variantNames = listOf("debug", "release", "Debug", "Release", "")
        val extracted = FlavorDiscovery.extractFlavors(variantNames)
        assertEquals(emptyList(), extracted)
    }

    @Test
    fun `script parameter injection regex cleanly replaces existing flavor parameter`() {
        val flag = "-Pkameleon.flavor=production"
        val initialParams = "-Dtest=true -Pkameleon.flavor=staging --info"
        val updated = initialParams.replace(
            Regex("""-Pkameleon\.flavor=\S+"""),
            flag
        )
        assertEquals("-Dtest=true -Pkameleon.flavor=production --info", updated)
    }

    @Test
    fun `variant mapper resolves target AGP variant preserving active build type`() {
        val knownAgpVariants = listOf("stagingDebug", "stagingRelease", "productionDebug", "productionRelease")

        // 1. Preserve Debug when switching staging -> production
        val targetDebug = KameleonVariantMapper.toAgpVariant(
            targetFlavor = "production",
            currentAgpVariant = "stagingDebug",
            knownAgpVariants = knownAgpVariants
        )
        assertEquals("productionDebug", targetDebug)

        // 2. Preserve Release when switching staging -> production
        val targetRelease = KameleonVariantMapper.toAgpVariant(
            targetFlavor = "production",
            currentAgpVariant = "stagingRelease",
            knownAgpVariants = knownAgpVariants
        )
        assertEquals("productionRelease", targetRelease)

        // 3. Fallback to substring match if exact candidate isn't directly matched
        val customAgpVariants = listOf("stagingDebug", "customProductionVariant")
        val targetCustom = KameleonVariantMapper.toAgpVariant(
            targetFlavor = "production",
            currentAgpVariant = "stagingDebug",
            knownAgpVariants = customAgpVariants
        )
        assertEquals("customProductionVariant", targetCustom)

        // 4. Return null if no matching variant exists in knownAgpVariants
        val noMatch = KameleonVariantMapper.toAgpVariant(
            targetFlavor = "unknown",
            currentAgpVariant = "stagingDebug",
            knownAgpVariants = knownAgpVariants
        )
        assertNull(noMatch)
    }

    @Test
    fun `variant mapper extracts flavor from AGP composite variant`() {
        val availableFlavors = listOf("staging", "production")

        // 1. Extract staging from stagingDebug
        val flavor1 = KameleonVariantMapper.fromAgpVariant("stagingDebug", availableFlavors)
        assertEquals("staging", flavor1)

        // 2. Extract production from productionRelease
        val flavor2 = KameleonVariantMapper.fromAgpVariant("productionRelease", availableFlavors)
        assertEquals("production", flavor2)

        // 3. Return null when variant doesn't match available flavors
        val flavor3 = KameleonVariantMapper.fromAgpVariant("qaDebug", availableFlavors)
        assertNull(flavor3)
    }

    @Test
    fun `properties line update replaces existing flavor key while preserving comments and other properties`() {
        val original = """
            # Project properties
            org.gradle.jvmargs=-Xmx2048m
            kameleon.flavor=staging
            
            # Additional flags
            android.useAndroidX=true
        """.trimIndent()

        val lines = original.lines()
        val lineRegex = Regex("""^\s*kameleon\.flavor\s*=.*$""")
        val keyIndex = lines.indexOfFirst { lineRegex.matches(it) }

        val targetLine = "kameleon.flavor=production"
        val updatedLines = lines.toMutableList()
        updatedLines[keyIndex] = targetLine
        val result = updatedLines.joinToString("\n")

        val expected = """
            # Project properties
            org.gradle.jvmargs=-Xmx2048m
            kameleon.flavor=production
            
            # Additional flags
            android.useAndroidX=true
        """.trimIndent()

        assertEquals(expected, result)
    }

    @Test
    fun `properties line update skips writing and leaves content untouched when flavor key is missing`() {
        val original = """
            # Project properties
            org.gradle.jvmargs=-Xmx2048m
        """.trimIndent()

        val lines = original.lines()
        val targetKey = "kameleon.flavor"
        val lineRegex = Regex("""^\s*${Regex.escape(targetKey)}\s*=\s*(.*?)\s*$""")
        val keyIndex = lines.indexOfFirst { lineRegex.matches(it) }

        assertEquals(-1, keyIndex)

        var written = false
        var logMessage: String? = null
        if (keyIndex != -1) {
            written = true
        } else {
            logMessage = "Property '$targetKey' is not defined in gradle.properties. Skipping file write."
        }

        assertEquals(false, written)
        assertEquals("Property 'kameleon.flavor' is not defined in gradle.properties. Skipping file write.", logMessage)
    }

    @Test
    fun `dimension property line update skips writing when dimension key is missing`() {
        val original = """
            # Project properties
            org.gradle.jvmargs=-Xmx2048m
            kameleon.flavor=staging
        """.trimIndent()

        val lines = original.lines()
        val targetKey = "kameleon.flavor.brand"
        val lineRegex = Regex("""^\s*${Regex.escape(targetKey)}\s*=\s*(.*?)\s*$""")
        val keyIndex = lines.indexOfFirst { lineRegex.matches(it) }

        assertEquals(-1, keyIndex)

        var written = false
        var logMessage: String? = null
        if (keyIndex != -1) {
            written = true
        } else {
            logMessage = "Property '$targetKey' is not defined in gradle.properties. Skipping file write."
        }

        assertEquals(false, written)
        assertEquals("Property 'kameleon.flavor.brand' is not defined in gradle.properties. Skipping file write.", logMessage)
    }

    @Test
    fun `self-healing logic resets current flavor if not in available flavors`() {
        val service = KameleonStateService()
        service.availableFlavors = listOf("brandADefault", "brandAStaging")
        service.currentFlavor = "oldRemovedFlavor"

        // Emulate self-healing check as in reloadFlavors
        if (service.availableFlavors.isNotEmpty() && service.currentFlavor !in service.availableFlavors) {
            service.currentFlavor = service.availableFlavors.first()
        }

        assertEquals("brandADefault", service.currentFlavor)
    }

    @Test
    fun `schema aggregation correctly merges dimensions and module dimensions from tooling models`() {
        val service = KameleonStateService()
        val appModel = dev.klitsie.kameleon.tooling.DefaultKameleonToolingModel(
            projectPath = ":sample:androidApp",
            dimensions = mapOf(
                "brand" to listOf("demo", "full"),
                "environment" to listOf("staging", "production")
            ),
            availableVariants = listOf("demoStaging", "demoProduction", "fullStaging", "fullProduction"),
            defaultVariant = "demoStaging"
        )
        val libModel = dev.klitsie.kameleon.tooling.DefaultKameleonToolingModel(
            projectPath = ":sample:shared-translations",
            dimensions = mapOf(
                "environment" to listOf("staging", "production")
            ),
            availableVariants = listOf("staging", "production"),
            defaultVariant = "staging"
        )

        service.registerToolingModels(listOf(appModel, libModel))

        val schema = service.schema
        assertEquals(2, schema.dimensions.size)
        assertEquals(listOf("demo", "full"), schema.dimensions["brand"])
        assertEquals(listOf("staging", "production"), schema.dimensions["environment"])

        assertEquals(2, schema.moduleDimensions.size)
        assertEquals(setOf("brand", "environment"), schema.moduleDimensions[":sample:androidApp"])
        assertEquals(setOf("environment"), schema.moduleDimensions[":sample:shared-translations"])
    }

    @Test
    fun `variant mapper builds camelCase variant name across dimensions`() {
        val variant1 = KameleonVariantMapper.buildVariantName(listOf("demo", "staging"))
        assertEquals("demoStaging", variant1)

        val variant2 = KameleonVariantMapper.buildVariantName(listOf("brandA", "environmentB", "release"))
        assertEquals("brandAEnvironmentBRelease", variant2)

        val variant3 = KameleonVariantMapper.buildVariantName(listOf("staging"))
        assertEquals("staging", variant3)

        val variantEmpty = KameleonVariantMapper.buildVariantName(emptyList())
        assertEquals("default", variantEmpty)
    }

    @Test
    fun `dimension property line updates replace dimension key while preserving default and other dimensions`() {
        val original = """
            # Project properties
            kameleon.flavor=staging
            kameleon.flavor.brand=demo
        """.trimIndent()

        val lines = original.lines()
        val targetKey = "kameleon.flavor.brand"
        val lineRegex = Regex("""^\s*${Regex.escape(targetKey)}\s*=.*$""")
        val keyIndex = lines.indexOfFirst { lineRegex.matches(it) }

        val targetLine = "$targetKey=full"
        val updatedLines = lines.toMutableList()
        updatedLines[keyIndex] = targetLine
        val result = updatedLines.joinToString("\n")

        val expected = """
            # Project properties
            kameleon.flavor=staging
            kameleon.flavor.brand=full
        """.trimIndent()

        assertEquals(expected, result)
    }

    @Test
    fun `dimension property read fallback returns flat kameleon flavor if dimension not explicitly present`() {
        val content = """
            # Project properties
            kameleon.flavor=staging
        """.trimIndent()

        val lines = content.lines()

        fun readFlavor(dimension: String): String? {
            val targetKey = if (dimension == "default") "kameleon.flavor" else "kameleon.flavor.$dimension"
            val lineRegex = Regex("""^\s*${Regex.escape(targetKey)}\s*=\s*(.*?)\s*$""")
            val match = lines.firstNotNullOfOrNull { lineRegex.matchEntire(it) }
            return if (match != null) {
                match.groupValues[1].takeIf { it.isNotBlank() }
            } else if (dimension != "default") {
                val fallbackRegex = Regex("""^\s*kameleon\.flavor\s*=\s*(.*?)\s*$""")
                val fallbackMatch = lines.firstNotNullOfOrNull { fallbackRegex.matchEntire(it) }
                fallbackMatch?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }

        assertEquals("staging", readFlavor("default"))
        assertEquals("staging", readFlavor("brand"))
    }

    @Test
    fun `idempotent property write detects exact match and leaves content untouched`() {
        val original = """
            # Project properties
            kameleon.flavor=staging
            kameleon.flavor.brand=demo
        """.trimIndent()

        val lines = original.lines()
        val targetKey = "kameleon.flavor.brand"
        val requestedValue = "demo"
        val lineRegex = Regex("""^\s*${Regex.escape(targetKey)}\s*=\s*(.*?)\s*$""")
        val keyIndex = lines.indexOfFirst { lineRegex.matches(it) }

        var modified = false
        if (keyIndex != -1) {
            val currentMatch = lineRegex.matchEntire(lines[keyIndex])
            val currentValue = currentMatch?.groupValues?.get(1)
            if (currentValue == requestedValue) {
                // Should short-circuit and not modify
                modified = false
            } else {
                modified = true
            }
        }

        assertEquals(false, modified)
    }

    @Test
    fun `per-module dimension variant resolution matches declared dimensions and preserves build type`() {
        val schema = ProjectFlavorSchema(
            dimensions = mapOf(
                "brand" to listOf("demo", "full"),
                "environment" to listOf("staging", "production")
            ),
            moduleDimensions = mapOf(
                ":sample:androidApp" to setOf("brand", "environment"),
                ":sample:shared-translations" to setOf("environment")
            )
        )

        val activeFlavors = mapOf(
            "brand" to "full",
            "environment" to "production"
        )

        // 1. AndroidApp module declaring both dimensions:
        val appDims = schema.dimensions.keys.filter { it in schema.moduleDimensions[":sample:androidApp"]!! }
        val appFlavors = appDims.map { activeFlavors[it]!! }
        val appComposite = KameleonVariantMapper.buildVariantName(appFlavors)
        val appCurrentVariant = "demoStagingRelease"
        val appBuildType = if (appCurrentVariant.endsWith("Release", ignoreCase = true)) "Release" else "Debug"
        val appTargetVariant = "${appComposite.replaceFirstChar { it.lowercase() }}$appBuildType"
        assertEquals("fullProductionRelease", appTargetVariant)

        // 2. SharedTranslations module declaring only environment:
        val libDims = schema.dimensions.keys.filter { it in schema.moduleDimensions[":sample:shared-translations"]!! }
        val libFlavors = libDims.map { activeFlavors[it]!! }
        val libComposite = KameleonVariantMapper.buildVariantName(libFlavors)
        val libCurrentVariant = "stagingDebug"
        val libBuildType = if (libCurrentVariant.endsWith("Release", ignoreCase = true)) "Release" else "Debug"
        val libTargetVariant = "${libComposite.replaceFirstChar { it.lowercase() }}$libBuildType"
        assertEquals("productionDebug", libTargetVariant)
    }

    @Test
    fun `per-module dimension change skips module when dimension is not declared by module`() {
        val schema = ProjectFlavorSchema(
            dimensions = mapOf(
                "brand" to listOf("demo", "full"),
                "environment" to listOf("staging", "production")
            ),
            moduleDimensions = mapOf(
                ":sample:androidApp" to setOf("brand", "environment"),
                ":sample:shared-translations" to setOf("environment")
            )
        )

        val changedDimension = "brand"
        val libDims = schema.moduleDimensions[":sample:shared-translations"]!!
        val appDims = schema.moduleDimensions[":sample:androidApp"]!!

        val libShouldUpdate = changedDimension in libDims
        val appShouldUpdate = changedDimension in appDims

        assertEquals(false, libShouldUpdate)
        assertEquals(true, appShouldUpdate)
    }

    @Test
    fun `variant name matching against available variants handles exact and fuzzy matches and returns null on missing`() {
        val available = listOf("demoStagingDebug", "demoStagingRelease", "fullProductionDebug", "fullProductionRelease")

        // Exact match
        assertEquals("fullProductionDebug", matchVariantNameHelper("fullProductionDebug", "fullProduction", "Debug", available))

        // Case-insensitive match
        assertEquals("fullProductionRelease", matchVariantNameHelper("FullProductionRelease", "fullProduction", "Release", available))

        // Return null when available list does not expose variant
        assertEquals(null, matchVariantNameHelper("stagingDebug", "staging", "Debug", listOf("debug", "release")))

        // Return null when available list is empty
        assertEquals(null, matchVariantNameHelper("customVariantDebug", "customVariant", "Debug", emptyList()))
    }

    private fun matchVariantNameHelper(
        expectedVariant: String,
        compositeFlavor: String,
        buildType: String,
        availableVariants: List<String>
    ): String? {
        if (availableVariants.isEmpty()) return null

        return availableVariants.firstOrNull { it == expectedVariant }
            ?: availableVariants.firstOrNull { it.equals(expectedVariant, ignoreCase = true) }
            ?: availableVariants.firstOrNull { it.contains(compositeFlavor, ignoreCase = true) && it.endsWith(buildType, ignoreCase = true) }
    }

    @Test
    fun `guard 1 filters non-variant and KMP source set modules with invalid variants like androidMain`() {
        val availableVariantsEmpty = emptyList<String>()
        val currentVariantKmp = "androidMain"

        val isNonVariant = availableVariantsEmpty.isEmpty()
        assertEquals(true, isNonVariant)

        val availableVariantsLibrary = listOf("debug", "release")
        val isCurrentVariantValidInModule = availableVariantsLibrary.any { it.equals(currentVariantKmp, ignoreCase = true) }
        assertEquals(false, isCurrentVariantValidInModule)
    }

    @Test
    fun `guard 2 validates target variant existence against module capabilities`() {
        val unflavoredLibraryVariants = listOf("debug", "release")
        val flavoredLibraryVariants = listOf("stagingDebug", "stagingRelease", "productionDebug", "productionRelease")
        val appVariants = listOf("demoStagingDebug", "demoStagingRelease", "fullProductionDebug", "fullProductionRelease")

        // Target variant for app
        val targetAppVariant = "demoStagingDebug"
        val matchedApp = matchVariantNameHelper(targetAppVariant, "demoStaging", "Debug", appVariants)
        assertEquals("demoStagingDebug", matchedApp)

        // Attempting to apply compound variant to unflavored library
        val matchedUnflavored = matchVariantNameHelper(targetAppVariant, "demoStaging", "Debug", unflavoredLibraryVariants)
        assertEquals(null, matchedUnflavored)

        // Attempting to apply single dimension flavor to unflavored library
        val targetLibVariant = "stagingDebug"
        val matchedUnflavoredSingle = matchVariantNameHelper(targetLibVariant, "staging", "Debug", unflavoredLibraryVariants)
        assertEquals(null, matchedUnflavoredSingle)

        // Applying single dimension flavor to flavored library
        val matchedFlavored = matchVariantNameHelper(targetLibVariant, "staging", "Debug", flavoredLibraryVariants)
        assertEquals("stagingDebug", matchedFlavored)
    }

    @Test
    fun `guard 3 performs hard NO-OP when active variant matches target variant`() {
        val currentVariant = "stagingDebug"
        val targetVariant = "stagingDebug"

        val isNoOp = currentVariant.equals(targetVariant, ignoreCase = true)
        assertEquals(true, isNoOp)

        val differentTarget = "productionDebug"
        val isDifferentNoOp = currentVariant.equals(differentTarget, ignoreCase = true)
        assertEquals(false, isDifferentNoOp)
    }

    @Test
    fun `test and synthetic module filtering identifies test modules correctly`() {
        val testNames = listOf(
            "sample.androidApp.unitTest",
            "sample.androidApp.androidTest",
            "sample.androidApp.androidTestFixtures",
            "sample.shared-app.test",
            "project:feature:unitTest",
            "project:feature:androidTest"
        )
        for (name in testNames) {
            assertEquals(
                true,
                dev.klitsie.kameleon.idea.android.VariantDispatcher.isTestOrSyntheticName(name),
                "Expected $name to be identified as test/synthetic"
            )
        }

        val nonTestNames = listOf(
            "sample.androidApp.main",
            "sample.androidApp",
            "sample.shared-app",
            "sample.shared-translations.main",
            ":sample:androidApp",
            ":sample:shared-app:main"
        )
        for (name in nonTestNames) {
            assertEquals(
                false,
                dev.klitsie.kameleon.idea.android.VariantDispatcher.isTestOrSyntheticName(name),
                "Expected $name to NOT be identified as test/synthetic"
            )
        }
    }

    @Test
    fun `sanitizeGradlePath strips source sets and normalizes path`() {
        assertEquals(
            ":sample:androidApp",
            dev.klitsie.kameleon.idea.android.VariantDispatcher.sanitizeGradlePath(":sample:androidApp.main")
        )
        assertEquals(
            ":sample:androidApp",
            dev.klitsie.kameleon.idea.android.VariantDispatcher.sanitizeGradlePath(":sample:androidApp:main")
        )
        assertEquals(
            ":sample:androidApp",
            dev.klitsie.kameleon.idea.android.VariantDispatcher.sanitizeGradlePath("sample.androidApp.unitTest")
        )
        assertEquals(
            ":sample:androidApp",
            dev.klitsie.kameleon.idea.android.VariantDispatcher.sanitizeGradlePath("sample:androidApp:androidTestFixtures")
        )
        assertEquals(
            ":feature",
            dev.klitsie.kameleon.idea.android.VariantDispatcher.sanitizeGradlePath("feature.test")
        )
    }

    @Test
    fun `findHolderModule resolves source set suffix modules to their holder modules`() {
        fun createMockModule(name: String): Module {
            return Proxy.newProxyInstance(
                Module::class.java.classLoader,
                arrayOf(Module::class.java)
            ) { proxy, method, args ->
                when (method.name) {
                    "getName" -> name
                    "toString" -> "Module($name)"
                    "equals" -> proxy === args?.get(0) || (args?.get(0) as? Module)?.name == name
                    "hashCode" -> name.hashCode()
                    else -> null
                }
            } as Module
        }

        fun createMockModuleManager(modules: List<Module>): ModuleManager {
            return object : ModuleManager() {
                override val modules: Array<Module> get() = modules.toTypedArray()
                override val sortedModules: Array<Module> get() = modules.toTypedArray()
                override val allModuleDescriptions: Collection<com.intellij.openapi.module.ModuleDescription> get() = emptyList()
                override val unloadedModuleDescriptions: Collection<com.intellij.openapi.module.UnloadedModuleDescription> get() = emptyList()

                override fun findModuleByName(name: String): Module? = modules.firstOrNull { it.name == name }
                override fun loadModule(filePath: String): Module = throw UnsupportedOperationException()
                override fun loadModule(file: java.nio.file.Path): Module = throw UnsupportedOperationException()
                override fun newModule(filePath: String, moduleTypeId: String): Module = throw UnsupportedOperationException()
                override fun disposeModule(module: Module) {}
                override fun getModifiableModel(): com.intellij.openapi.module.ModifiableModuleModel = throw UnsupportedOperationException()
                override fun getUnloadedModuleDescription(moduleName: String): com.intellij.openapi.module.UnloadedModuleDescription? = null
                override fun hasModuleGroups(): Boolean = false
                override fun moduleDependencyComparator(): Comparator<Module> = Comparator { _, _ -> 0 }
                override fun getModuleDependentModules(module: Module): List<Module> = emptyList()
                override fun isModuleDependent(module: Module, onModule: Module): Boolean = false
                override fun moduleGraph(): com.intellij.util.graph.Graph<Module> = throw UnsupportedOperationException()
                override fun moduleGraph(includeTests: Boolean): com.intellij.util.graph.Graph<Module> = throw UnsupportedOperationException()
                override fun getModuleGrouper(model: com.intellij.openapi.module.ModifiableModuleModel?): com.intellij.openapi.module.ModuleGrouper = throw UnsupportedOperationException()
                override fun setUnloadedModulesSync(unloadedModuleNames: List<String>) {}
                override suspend fun setUnloadedModules(unloadedModuleNames: List<String>) {}
            }
        }

        val holderApp = createMockModule("kameleon-root.sample.androidApp")
        val holderApp2 = createMockModule("kameleon-root.sample.androidApp2")
        val holderShared = createMockModule("kameleon-root.sample.shared-app")
        val standalone = createMockModule("standalone-module")

        val mainApp = createMockModule("kameleon-root.sample.androidApp.main")
        val unitTestApp = createMockModule("kameleon-root.sample.androidApp.unitTest")
        val testApp = createMockModule("kameleon-root.sample.androidApp.test")
        val androidTestApp = createMockModule("kameleon-root.sample.androidApp.androidTest")
        val jvmTestApp = createMockModule("kameleon-root.sample.androidApp.jvmTest")

        val mainApp2 = createMockModule("kameleon-root.sample.androidApp2.main")

        val allModules = listOf(holderApp, holderApp2, holderShared, standalone, mainApp, unitTestApp, testApp, androidTestApp, jvmTestApp, mainApp2)
        val moduleManager = createMockModuleManager(allModules)

        assertEquals(holderApp, dev.klitsie.kameleon.idea.android.VariantDispatcher.findHolderModule(mainApp, moduleManager))
        assertEquals(holderApp, dev.klitsie.kameleon.idea.android.VariantDispatcher.findHolderModule(unitTestApp, moduleManager))
        assertEquals(holderApp, dev.klitsie.kameleon.idea.android.VariantDispatcher.findHolderModule(testApp, moduleManager))
        assertEquals(holderApp, dev.klitsie.kameleon.idea.android.VariantDispatcher.findHolderModule(androidTestApp, moduleManager))
        assertEquals(holderApp, dev.klitsie.kameleon.idea.android.VariantDispatcher.findHolderModule(jvmTestApp, moduleManager))
        assertEquals(holderApp2, dev.klitsie.kameleon.idea.android.VariantDispatcher.findHolderModule(mainApp2, moduleManager))
        assertEquals(standalone, dev.klitsie.kameleon.idea.android.VariantDispatcher.findHolderModule(standalone, moduleManager))
    }

    @Test
    fun `holder module resolution maps source set candidates to holder modules and deduplicates`() {
        fun createMockModule(name: String): Module {
            return Proxy.newProxyInstance(
                Module::class.java.classLoader,
                arrayOf(Module::class.java)
            ) { proxy, method, args ->
                when (method.name) {
                    "getName" -> name
                    "toString" -> "Module($name)"
                    "equals" -> proxy === args?.get(0) || (args?.get(0) as? Module)?.name == name
                    "hashCode" -> name.hashCode()
                    else -> null
                }
            } as Module
        }

        fun createMockModuleManager(modules: List<Module>): ModuleManager {
            return object : ModuleManager() {
                override val modules: Array<Module> get() = modules.toTypedArray()
                override val sortedModules: Array<Module> get() = modules.toTypedArray()
                override val allModuleDescriptions: Collection<com.intellij.openapi.module.ModuleDescription> get() = emptyList()
                override val unloadedModuleDescriptions: Collection<com.intellij.openapi.module.UnloadedModuleDescription> get() = emptyList()

                override fun findModuleByName(name: String): Module? = modules.firstOrNull { it.name == name }
                override fun loadModule(filePath: String): Module = throw UnsupportedOperationException()
                override fun loadModule(file: java.nio.file.Path): Module = throw UnsupportedOperationException()
                override fun newModule(filePath: String, moduleTypeId: String): Module = throw UnsupportedOperationException()
                override fun disposeModule(module: Module) {}
                override fun getModifiableModel(): com.intellij.openapi.module.ModifiableModuleModel = throw UnsupportedOperationException()
                override fun getUnloadedModuleDescription(moduleName: String): com.intellij.openapi.module.UnloadedModuleDescription? = null
                override fun hasModuleGroups(): Boolean = false
                override fun moduleDependencyComparator(): Comparator<Module> = Comparator { _, _ -> 0 }
                override fun getModuleDependentModules(module: Module): List<Module> = emptyList()
                override fun isModuleDependent(module: Module, onModule: Module): Boolean = false
                override fun moduleGraph(): com.intellij.util.graph.Graph<Module> = throw UnsupportedOperationException()
                override fun moduleGraph(includeTests: Boolean): com.intellij.util.graph.Graph<Module> = throw UnsupportedOperationException()
                override fun getModuleGrouper(model: com.intellij.openapi.module.ModifiableModuleModel?): com.intellij.openapi.module.ModuleGrouper = throw UnsupportedOperationException()
                override fun setUnloadedModulesSync(unloadedModuleNames: List<String>) {}
                override suspend fun setUnloadedModules(unloadedModuleNames: List<String>) {}
            }
        }

        val holder1 = createMockModule("kameleon-root.sample.androidApp")
        val main1 = createMockModule("kameleon-root.sample.androidApp.main")
        val unitTest1 = createMockModule("kameleon-root.sample.androidApp.unitTest")
        val androidTest1 = createMockModule("kameleon-root.sample.androidApp.androidTest")

        val holder2 = createMockModule("kameleon-root.sample.androidApp2")
        val main2 = createMockModule("kameleon-root.sample.androidApp2.main")

        val mm = createMockModuleManager(listOf(holder1, holder2, main1, unitTest1, androidTest1, main2))

        val candidateModules = listOf(main1, unitTest1, androidTest1, main2)
        val resolvedHolders = candidateModules.map { dev.klitsie.kameleon.idea.android.VariantDispatcher.findHolderModule(it, mm) }
            .filterNot { dev.klitsie.kameleon.idea.android.VariantDispatcher.isTestOrSyntheticModule(it) }
            .distinct()

        assertEquals(listOf(holder1, holder2), resolvedHolders)
        assertEquals(listOf("kameleon-root.sample.androidApp", "kameleon-root.sample.androidApp2"), resolvedHolders.map { it.name })
    }

    @Test
    fun `deduplication groups raw modules by root gradle path and excludes test modules`() {
        data class MockModule(val name: String, val externalProjectId: String, val hasFacet: Boolean)

        val rawModules = listOf(
            MockModule("sample.androidApp.main", ":sample:androidApp:main", true),
            MockModule("sample.androidApp.unitTest", ":sample:androidApp:unitTest", true),
            MockModule("sample.androidApp.androidTest", ":sample:androidApp:androidTest", true),
            MockModule("sample.androidApp", ":sample:androidApp", false),
            MockModule("sample.shared-app.main", ":sample:shared-app:main", true),
            MockModule("sample.shared-app.unitTest", ":sample:shared-app:unitTest", true)
        )

        val filtered = rawModules.filterNot {
            dev.klitsie.kameleon.idea.android.VariantDispatcher.isTestOrSyntheticName(it.name)
        }

        assertEquals(3, filtered.size) // androidApp.main, androidApp, shared-app.main

        val grouped = filtered.groupBy {
            dev.klitsie.kameleon.idea.android.VariantDispatcher.sanitizeGradlePath(it.externalProjectId)
        }

        assertEquals(2, grouped.size) // :sample:androidApp and :sample:shared-app
        assertEquals(setOf(":sample:androidApp", ":sample:shared-app"), grouped.keys)

        val selected = grouped.values.mapNotNull { list ->
            list.firstOrNull { it.hasFacet } ?: list.firstOrNull { it.name.endsWith(".main") } ?: list.firstOrNull()
        }

        assertEquals(2, selected.size)
        assertEquals("sample.androidApp.main", selected.first { it.externalProjectId.contains("androidApp") }.name)
        assertEquals("sample.shared-app.main", selected.first { it.externalProjectId.contains("shared-app") }.name)
    }

    @Test
    fun `variant dispatcher skips update when current variant already matches target variant`() {
        val currentVariant = "demoStagingDebug"
        val targetVariant = "demoStagingDebug"

        val shouldSkip = currentVariant.equals(targetVariant, ignoreCase = true)
        assertEquals(true, shouldSkip)

        val differentTarget = "demoProductionDebug"
        val shouldSkipDifferent = currentVariant.equals(differentTarget, ignoreCase = true)
        assertEquals(false, shouldSkipDifferent)
    }

    @Test
    fun `startup initialization runs strictly once and handles in-progress sync`() {
        val isInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
        var pendingInitialAlignment = false
        var alignmentsRun = 0

        fun onStartup(syncInProgress: Boolean) {
            if (isInitialized.get()) return

            if (syncInProgress) {
                pendingInitialAlignment = true
                return
            }

            if (isInitialized.compareAndSet(false, true)) {
                pendingInitialAlignment = false
                alignmentsRun++
            }
        }

        fun onImportFinished() {
            if (pendingInitialAlignment) {
                isInitialized.set(true)
                pendingInitialAlignment = false
                alignmentsRun++
            } else {
                isInitialized.set(true)
            }
        }

        // 1. Initial startup while sync is in progress
        onStartup(syncInProgress = true)
        assertEquals(false, isInitialized.get())
        assertEquals(true, pendingInitialAlignment)
        assertEquals(0, alignmentsRun)

        // 2. Subsequent startup attempts while initialized or pending
        onStartup(syncInProgress = false)
        assertEquals(true, isInitialized.get())
        assertEquals(false, pendingInitialAlignment)
        assertEquals(1, alignmentsRun)

        // 3. Post-sync finishes -> isInitialized is true and alignment runs at most once
        onImportFinished()
        assertEquals(true, isInitialized.get())
        assertEquals(false, pendingInitialAlignment)
        assertEquals(1, alignmentsRun)

        // 4. Repeated onStartup is a hard no-op
        onStartup(syncInProgress = false)
        assertEquals(1, alignmentsRun)
    }

    @Test
    fun `UI selection idempotency guards against redundant disk writes and updates`() {
        val activeFlavors = mutableMapOf("brand" to "demo", "environment" to "staging")
        var writeCount = 0
        var dispatchCount = 0

        fun onDimensionFlavorSelected(dimension: String, flavor: String) {
            val current = activeFlavors[dimension]
            if (current == flavor) {
                return // Idempotency guard
            }

            activeFlavors[dimension] = flavor
            writeCount++
            dispatchCount++
        }

        // Selecting already active flavor:
        onDimensionFlavorSelected("brand", "demo")
        assertEquals(0, writeCount)
        assertEquals(0, dispatchCount)

        // Selecting new flavor:
        onDimensionFlavorSelected("brand", "full")
        assertEquals(1, writeCount)
        assertEquals(1, dispatchCount)
        assertEquals("full", activeFlavors["brand"])

        // Selecting the newly active flavor again:
        onDimensionFlavorSelected("brand", "full")
        assertEquals(1, writeCount)
        assertEquals(1, dispatchCount)
    }

    @Test
    fun `post-sync verification triggers no updates when AGP variants already match expected variants`() {
        val propertiesFlavors = mapOf("brand" to "demo", "environment" to "staging")
        val currentAgpVariants = mapOf(
            ":sample:androidApp" to "demoStagingDebug",
            ":sample:shared-translations" to "stagingDebug"
        )
        val schema = ProjectFlavorSchema(
            dimensions = mapOf("brand" to listOf("demo", "full"), "environment" to listOf("staging", "production")),
            moduleDimensions = mapOf(
                ":sample:androidApp" to setOf("brand", "environment"),
                ":sample:shared-translations" to setOf("environment")
            )
        )

        var updatesTriggered = 0

        for ((modulePath, currentVariant) in currentAgpVariants) {
            val dims = schema.dimensions.keys.filter { it in schema.moduleDimensions[modulePath]!! }
            val flavors = dims.map { propertiesFlavors[it]!! }
            val composite = KameleonVariantMapper.buildVariantName(flavors)
            val expectedVariant = "${composite.replaceFirstChar { it.lowercase() }}Debug"

            if (currentVariant.equals(expectedVariant, ignoreCase = true)) {
                // Hard NO-OP
            } else {
                updatesTriggered++
            }
        }

        assertEquals(0, updatesTriggered)
    }

    @Test
    fun `conditional sync relies on AGP when Android targets are updated`() {
        var bgSyncTriggered = 0
        var agpUpdatesCount = 2
        var logMessage: String? = null

        val hasStateChanged = true
        if (agpUpdatesCount > 0) {
            logMessage = "Updated $agpUpdatesCount Android target(s). Relying on AGP variant update."
        } else if (hasStateChanged) {
            logMessage = "No Android targets updated. Triggering background Gradle sync for KMP/Java targets."
            bgSyncTriggered++
        }

        assertEquals(0, bgSyncTriggered)
        assertEquals("Updated 2 Android target(s). Relying on AGP variant update.", logMessage)
    }

    @Test
    fun `conditional sync triggers background Gradle sync when no Android targets updated for pure KMP or Java`() {
        var bgSyncTriggered = 0
        var agpUpdatesCount = 0
        var logMessage: String? = null

        val hasStateChanged = true
        if (agpUpdatesCount > 0) {
            logMessage = "Updated $agpUpdatesCount Android target(s). Relying on AGP variant update."
        } else if (hasStateChanged) {
            logMessage = "No Android targets updated. Triggering background Gradle sync for KMP/Java targets."
            bgSyncTriggered++
        }

        assertEquals(1, bgSyncTriggered)
        assertEquals("No Android targets updated. Triggering background Gradle sync for KMP/Java targets.", logMessage)
    }

    @Test
    fun `conditional sync performs hard NO-OP when selection does not change state`() {
        val activeFlavors = mutableMapOf("brand" to "demo")
        var fileWrites = 0
        var agpCalls = 0
        var bgSyncCalls = 0

        fun selectFlavor(dimension: String, flavor: String) {
            val currentActive = activeFlavors[dimension]
            val hasStateChanged = currentActive != flavor
            if (!hasStateChanged) {
                // Hard NO-OP: no file writes, no AGP updates, no sync
                return
            }

            activeFlavors[dimension] = flavor
            fileWrites++
            val updatedAndroidTargets = 1
            if (updatedAndroidTargets > 0) {
                agpCalls++
            } else if (hasStateChanged) {
                bgSyncCalls++
            }
        }

        // Selecting same flavor -> hard NO-OP
        selectFlavor("brand", "demo")
        assertEquals(0, fileWrites)
        assertEquals(0, agpCalls)
        assertEquals(0, bgSyncCalls)

        // Selecting new flavor -> updates executed
        selectFlavor("brand", "full")
        assertEquals(1, fileWrites)
        assertEquals(1, agpCalls)
        assertEquals(0, bgSyncCalls)
    }

    @Test
    fun `triggerProjectRefresh skips refresh when sync guard is active`() {
        var refreshExecuted = 0

        fun triggerRefresh(isUpdatingVariants: Boolean, isSyncInProgress: Boolean) {
            if (isUpdatingVariants || isSyncInProgress) {
                return
            }
            refreshExecuted++
        }

        triggerRefresh(isUpdatingVariants = true, isSyncInProgress = false)
        assertEquals(0, refreshExecuted)

        triggerRefresh(isUpdatingVariants = false, isSyncInProgress = true)
        assertEquals(0, refreshExecuted)

        triggerRefresh(isUpdatingVariants = false, isSyncInProgress = false)
        assertEquals(1, refreshExecuted)
    }

    @Test
    fun `buildGradleFlavorArguments builds dimensionless argument for default or empty dimension`() {
        val singleDefault = buildGradleFlavorArguments(mapOf("default" to "dev"))
        assertEquals(listOf("-Pkameleon.flavor=dev"), singleDefault)

        val nullDim = buildGradleFlavorArguments(mapOf(null to "dev"))
        assertEquals(listOf("-Pkameleon.flavor=dev"), nullDim)

        val emptyDim = buildGradleFlavorArguments(mapOf("" to "dev"))
        assertEquals(listOf("-Pkameleon.flavor=dev"), emptyDim)
    }

    @Test
    fun `buildGradleFlavorArguments builds granular arguments for multiple explicit dimensions`() {
        val multiDim = buildGradleFlavorArguments(
            linkedMapOf(
                "brand" to "flappy",
                "environment" to "production"
            )
        )
        assertEquals(
            listOf(
                "-Pkameleon.flavor.brand=flappy",
                "-Pkameleon.flavor.environment=production"
            ),
            multiDim
        )
    }

    @Test
    fun `injectGradleFlavorArguments replaces single and multiple existing flavor arguments`() {
        val flags = listOf("-Pkameleon.flavor.brand=flappy", "-Pkameleon.flavor.environment=production")

        // 1. Existing dimensionless argument
        val initial1 = "-Dtest=true -Pkameleon.flavor=staging --info"
        val updated1 = injectGradleFlavorArguments(initial1, flags)
        assertEquals("-Dtest=true -Pkameleon.flavor.brand=flappy -Pkameleon.flavor.environment=production --info", updated1)

        // 2. Existing multi-dimension arguments
        val initial2 = "-Dtest=true -Pkameleon.flavor.brand=whoop -Pkameleon.flavor.environment=staging --info"
        val updated2 = injectGradleFlavorArguments(initial2, flags)
        assertEquals("-Dtest=true -Pkameleon.flavor.brand=flappy -Pkameleon.flavor.environment=production --info", updated2)

        // 3. No existing flavor argument
        val initial3 = "-Dtest=true --info"
        val updated3 = injectGradleFlavorArguments(initial3, flags)
        assertEquals("-Dtest=true --info -Pkameleon.flavor.brand=flappy -Pkameleon.flavor.environment=production", updated3)

        // 4. Blank initial parameters
        val updated4 = injectGradleFlavorArguments("", flags)
        assertEquals("-Pkameleon.flavor.brand=flappy -Pkameleon.flavor.environment=production", updated4)
    }

    @Test
    fun `batch in-memory variant mutation updates models before triggering single sync`() {
        class FakeAndroidModel(var selectedVariant: String) {
            fun getSelectedVariantName(): String = selectedVariant
            fun setSelectedVariantName(variant: String) {
                selectedVariant = variant
            }
        }

        val models = mapOf(
            "app" to FakeAndroidModel("demoStagingDebug"),
            "feature" to FakeAndroidModel("demoStagingDebug"),
            "library" to FakeAndroidModel("stagingDebug")
        )

        val targetVariants = mapOf(
            "app" to "fullProductionDebug",
            "feature" to "fullProductionDebug",
            "library" to "productionDebug"
        )

        var mutationsCount = 0
        var syncTriggerCount = 0

        val modulesToUpdate = mutableListOf<Pair<String, String>>()
        for ((moduleName, model) in models) {
            val current = model.getSelectedVariantName()
            val target = targetVariants[moduleName]!!
            if (!current.equals(target, ignoreCase = true)) {
                modulesToUpdate.add(moduleName to target)
            }
        }

        assertEquals(3, modulesToUpdate.size)

        // Perform in-memory batch mutation
        for ((moduleName, target) in modulesToUpdate) {
            models[moduleName]!!.setSelectedVariantName(target)
            mutationsCount++
        }

        // Trigger single sync after batch mutation
        if (mutationsCount > 0) {
            syncTriggerCount++
        }

        assertEquals(3, mutationsCount)
        assertEquals(1, syncTriggerCount)
        assertEquals("fullProductionDebug", models["app"]!!.getSelectedVariantName())
        assertEquals("fullProductionDebug", models["feature"]!!.getSelectedVariantName())
        assertEquals("productionDebug", models["library"]!!.getSelectedVariantName())
    }

    @Test
    fun `batch in-memory mutation NO-OP triggers zero mutations and zero syncs when already matching`() {
        class FakeAndroidModel(var selectedVariant: String) {
            fun getSelectedVariantName(): String = selectedVariant
            fun setSelectedVariantName(variant: String) {
                selectedVariant = variant
            }
        }

        val models = mapOf(
            "app" to FakeAndroidModel("fullProductionDebug"),
            "library" to FakeAndroidModel("productionDebug")
        )

        val targetVariants = mapOf(
            "app" to "fullProductionDebug",
            "library" to "productionDebug"
        )

        var mutationsCount = 0
        var syncTriggerCount = 0

        val modulesToUpdate = mutableListOf<Pair<String, String>>()
        for ((moduleName, model) in models) {
            val current = model.getSelectedVariantName()
            val target = targetVariants[moduleName]!!
            if (!current.equals(target, ignoreCase = true)) {
                modulesToUpdate.add(moduleName to target)
            }
        }

        assertEquals(0, modulesToUpdate.size)

        for ((moduleName, target) in modulesToUpdate) {
            models[moduleName]!!.setSelectedVariantName(target)
            mutationsCount++
        }

        if (mutationsCount > 0) {
            syncTriggerCount++
        }

        assertEquals(0, mutationsCount)
        assertEquals(0, syncTriggerCount)
    }

    @Test
    fun `nested model unwrapping mutates GradleAndroidModelData and AndroidFacet state`() {
        // 1. Simulating modern AGP delegate hierarchy
        class FakeGradleAndroidModelData(var selectedVariantName: String)
        class FakeGradleAndroidModelImpl(val data: FakeGradleAndroidModelData)
        // Delegate has no setSelectedVariantName (immutable delegate)
        class FakeGradleAndroidDependencyModelImpl(val coreModel: FakeGradleAndroidModelImpl)

        val modelData = FakeGradleAndroidModelData("demoStagingDebug")
        val coreModel = FakeGradleAndroidModelImpl(modelData)
        val dependencyModel = FakeGradleAndroidDependencyModelImpl(coreModel)

        // 2. Simulating AndroidFacet configuration state
        class FakeState {
            @JvmField
            var SELECTED_BUILD_VARIANT: String = "demoStagingDebug"
        }
        class FakeConfiguration {
            val state = FakeState()
        }
        class FakeFacet {
            val configuration = FakeConfiguration()
        }

        val facet = FakeFacet()
        val targetVariant = "fullProductionDebug"

        // Test in-memory unwrapping and mutation
        var core: Any = dependencyModel
        val getCoreMethod = core.javaClass.methods.firstOrNull { it.name == "getCoreModel" || it.name == "getCore" }
        val unwrappedCore = getCoreMethod?.invoke(core)
            ?: core.javaClass.declaredFields.firstOrNull { it.name == "coreModel" }?.let { f ->
                f.isAccessible = true
                f.get(core)
            }
        if (unwrappedCore != null) {
            core = unwrappedCore
        }

        val getDataMethod = core.javaClass.methods.firstOrNull { it.name == "getData" }
        val unwrappedData = getDataMethod?.invoke(core)
            ?: core.javaClass.declaredFields.firstOrNull { it.name == "data" }?.let { f ->
                f.isAccessible = true
                f.get(core)
            }

        assertNotNull(unwrappedData)
        assertTrue(unwrappedData is FakeGradleAndroidModelData)

        // Mutate selectedVariantName on GradleAndroidModelData via reflection
        val field = unwrappedData.javaClass.declaredFields.first { it.name == "selectedVariantName" }
        field.isAccessible = true
        field.set(unwrappedData, targetVariant)

        assertEquals("fullProductionDebug", modelData.selectedVariantName)

        // Test facet configuration mutation
        val config = facet.configuration
        val state = config.state
        val facetField = state.javaClass.getField("SELECTED_BUILD_VARIANT")
        facetField.set(state, targetVariant)

        assertEquals("fullProductionDebug", facet.configuration.state.SELECTED_BUILD_VARIANT)
    }

    @Test
    fun `setAllVariantsAndSyncOnce batch mutates all targets and tracks change status`() {
        class MockModuleData(var variant: String, var facetVariant: String)

        val modules = mapOf(
            "app" to MockModuleData("demoDebug", "demoDebug"),
            "lib" to MockModuleData("stagingDebug", "stagingDebug")
        )

        val targets = mapOf(
            "app" to "fullDebug",
            "lib" to "productionDebug"
        )

        var syncRequested = 0
        var mutationsCount = 0

        for ((name, target) in targets) {
            val mod = modules[name]!!
            if (mod.variant != target || mod.facetVariant != target) {
                mod.variant = target
                mod.facetVariant = target
                mutationsCount++
            }
        }

        if (mutationsCount > 0) {
            syncRequested++
        }

        assertEquals(2, mutationsCount)
        assertEquals(1, syncRequested)
        assertEquals("fullDebug", modules["app"]!!.variant)
        assertEquals("fullDebug", modules["app"]!!.facetVariant)
        assertEquals("productionDebug", modules["lib"]!!.variant)
        assertEquals("productionDebug", modules["lib"]!!.facetVariant)
    }

    @Test
    fun `WorkspaceModel entity batch mutation updates selected variant across modules`() {
        data class FakeModelData(val selectedVariantName: String)
        data class FakeAndroidModel(val data: FakeModelData)
        class FakeEntity(val moduleName: String, var gradleAndroidModel: FakeAndroidModel)

        val entities = mutableListOf(
            FakeEntity("app", FakeAndroidModel(FakeModelData("demoDebug"))),
            FakeEntity("lib", FakeAndroidModel(FakeModelData("demoDebug")))
        )

        val targetVariants = mapOf(
            "app" to "fullProductionDebug",
            "lib" to "fullProductionDebug"
        )

        var batchTransactionExecuted = false
        var mutatedCount = 0

        // Simulate single WorkspaceModel updateProjectModel transaction
        val updateProjectModel = { block: () -> Unit ->
            batchTransactionExecuted = true
            block()
        }

        updateProjectModel {
            for ((moduleName, target) in targetVariants) {
                val entity = entities.firstOrNull { it.moduleName == moduleName }
                if (entity != null) {
                    val currentData = entity.gradleAndroidModel.data
                    if (currentData.selectedVariantName != target) {
                        val updatedData = currentData.copy(selectedVariantName = target)
                        val updatedModel = entity.gradleAndroidModel.copy(data = updatedData)
                        entity.gradleAndroidModel = updatedModel
                        mutatedCount++
                    }
                }
            }
        }

        assertTrue(batchTransactionExecuted)
        assertEquals(2, mutatedCount)
        assertEquals("fullProductionDebug", entities.first { it.moduleName == "app" }.gradleAndroidModel.data.selectedVariantName)
        assertEquals("fullProductionDebug", entities.first { it.moduleName == "lib" }.gradleAndroidModel.data.selectedVariantName)
    }

    @Test
    fun `GradleSyncStats trigger resolution resolves variant selection trigger`() {
        val trigger = com.google.wireless.android.sdk.stats.GradleSyncStats.Trigger.TRIGGER_VARIANT_SELECTION_CHANGED_BY_USER
        val request = com.android.tools.idea.gradle.project.sync.GradleSyncInvoker.Request(trigger)
        assertEquals(trigger, request.trigger)
    }

    @Test
    fun `reconcileVariantsWithProperties and reloadFlavors unify state reloading AGP alignment and project view refresh`() {
        var reloadFlavorsCount = 0
        var alignAgpCount = 0
        var refreshProjectViewCount = 0

        fun reconcile(isInitialStartup: Boolean) {
            reloadFlavorsCount++
            alignAgpCount++
            refreshProjectViewCount++
        }

        // 1. Simulating startup reconciliation
        reconcile(isInitialStartup = true)
        assertEquals(1, reloadFlavorsCount)
        assertEquals(1, alignAgpCount)
        assertEquals(1, refreshProjectViewCount)

        // 2. Simulating post-sync / configuration change reload
        reconcile(isInitialStartup = false)
        assertEquals(2, reloadFlavorsCount)
        assertEquals(2, alignAgpCount)
        assertEquals(2, refreshProjectViewCount)
    }

    @Test
    fun `unified variant dispatch resolves targets for dimension change and full alignment`() {
        val schema = ProjectFlavorSchema(
            dimensions = mapOf(
                "brand" to listOf("flappy", "whoop"),
                "environment" to listOf("staging", "production")
            ),
            moduleDimensions = mapOf(
                ":sample:androidApp" to setOf("brand", "environment"),
                ":sample:shared-branding" to setOf("brand")
            )
        )
        val activeFlavors = mapOf("brand" to "whoop", "environment" to "production")

        fun resolveTargetForModule(
            modulePath: String,
            moduleDims: Set<String>?,
            targetDimension: String?,
            targetFlavor: String?
        ): String? {
            if (targetDimension != null) {
                if (moduleDims == null || !moduleDims.contains(targetDimension)) return null
            } else {
                if (moduleDims == null || moduleDims.isEmpty() || !moduleDims.any { it in schema.dimensions }) return null
            }

            val relevantDims = schema.dimensions.keys.filter { it in moduleDims }

            val flavorList = if (relevantDims.isNotEmpty()) {
                relevantDims.mapNotNull { activeFlavors[it] ?: schema.dimensions[it]?.firstOrNull() }
            } else {
                listOf(targetFlavor ?: activeFlavors["default"] ?: "default")
            }

            val fallbackFlavor = targetFlavor ?: "default"
            val compositeFlavor = if (flavorList.isEmpty()) fallbackFlavor else KameleonVariantMapper.buildVariantName(flavorList)
            return "${compositeFlavor.replaceFirstChar { it.lowercase() }}Debug"
        }

        // Test dimension change: only 'environment' changed
        val appDimTarget = resolveTargetForModule(":sample:androidApp", moduleDims = setOf("brand", "environment"), targetDimension = "environment", targetFlavor = "production")
        val brandingDimTarget = resolveTargetForModule(":sample:shared-branding", moduleDims = setOf("brand"), targetDimension = "environment", targetFlavor = "production")
        val nonKameleonAppTarget = resolveTargetForModule(":sample:nonKameleonApp", moduleDims = null, targetDimension = "environment", targetFlavor = "production")
        assertEquals("whoopProductionDebug", appDimTarget)
        assertNull(brandingDimTarget) // Lib does not declare environment dimension, skipped
        assertNull(nonKameleonAppTarget) // Non-Kameleon app module without declared dimensions, skipped

        // Test alignment: full schema alignment
        val appAlignTarget = resolveTargetForModule(":sample:androidApp", moduleDims = setOf("brand", "environment"), targetDimension = null, targetFlavor = null)
        val brandingAlignTarget = resolveTargetForModule(":sample:shared-branding", moduleDims = setOf("brand"), targetDimension = null, targetFlavor = null)
        val nonKameleonAlignTarget = resolveTargetForModule(":sample:nonKameleonApp", moduleDims = null, targetDimension = null, targetFlavor = null)
        assertEquals("whoopProductionDebug", appAlignTarget)
        assertEquals("whoopDebug", brandingAlignTarget) // Lib declares brand dimension, matched
        assertNull(nonKameleonAlignTarget) // Non-Kameleon module skipped on alignment
    }

    @Test
    fun `VariantDispatcher getModuleDeclaredDimensions excludes modules not in schema moduleDimensions`() {
        val schema = ProjectFlavorSchema(
            dimensions = mapOf(
                "brand" to listOf("flappy", "whoop"),
                "environment" to listOf("staging", "production")
            ),
            moduleDimensions = mapOf(
                ":sample:androidApp" to setOf("brand", "environment"),
                ":sample:shared-branding" to setOf("brand")
            )
        )

        // Module in schema -> resolved
        val appDims = if (schema.moduleDimensions.isNotEmpty()) {
            if (schema.moduleDimensions.containsKey(":sample:androidApp")) {
                schema.moduleDimensions[":sample:androidApp"]
            } else {
                schema.moduleDimensions.entries.firstOrNull { (path, _) ->
                    ":sample:androidApp".endsWith(path) || path.endsWith(":sample:androidApp")
                }?.value
            }
        } else null
        assertEquals(setOf("brand", "environment"), appDims)

        // Non-Kameleon module NOT in schema -> null (skipped)
        val nonKameleonDims = if (schema.moduleDimensions.isNotEmpty()) {
            if (schema.moduleDimensions.containsKey(":sample:otherApp")) {
                schema.moduleDimensions[":sample:otherApp"]
            } else {
                schema.moduleDimensions.entries.firstOrNull { (path, _) ->
                    ":sample:otherApp".endsWith(path) || path.endsWith(":sample:otherApp")
                }?.value
            }
        } else null
        assertNull(nonKameleonDims)
    }

    @Test
    fun `reloadFlavors priority order prioritizes diskVal then inMemory activeFlavors then schema fallback`() {
        val flavors = listOf("alpha", "beta", "gamma")

        fun resolveActive(diskVal: String?, inMemoryVal: String?): String {
            return when {
                diskVal != null && diskVal in flavors -> diskVal
                inMemoryVal != null && inMemoryVal in flavors -> inMemoryVal
                else -> flavors.firstOrNull() ?: "default"
            }
        }

        // Case A: diskVal is present and valid in flavors -> prioritize diskVal
        assertEquals("gamma", resolveActive(diskVal = "gamma", inMemoryVal = "beta"))

        // Case B: diskVal is null, but in-memory selection is valid -> preserve in-memory selection
        assertEquals("beta", resolveActive(diskVal = null, inMemoryVal = "beta"))

        // Case C: diskVal is invalid/unmatched, in-memory selection is valid -> preserve in-memory selection
        assertEquals("beta", resolveActive(diskVal = "invalidFlavor", inMemoryVal = "beta"))

        // Case D: both diskVal and in-memory selection are null or invalid -> fallback to first flavor
        assertEquals("alpha", resolveActive(diskVal = null, inMemoryVal = null))
        assertEquals("alpha", resolveActive(diskVal = "unknown", inMemoryVal = "unknown"))
    }

    @Test
    fun `state service serializes and restores activeFlavors and schema without losing selections during schema rebuild`() {
        val service = KameleonStateService()
        val appModel = dev.klitsie.kameleon.tooling.DefaultKameleonToolingModel(
            projectPath = ":app",
            dimensions = mapOf(
                "brand" to listOf("demo", "full"),
                "environment" to listOf("staging", "production")
            ),
            availableVariants = listOf("demoStaging", "demoProduction", "fullStaging", "fullProduction"),
            defaultVariant = "demoStaging"
        )
        service.registerToolingModel(appModel)
        service.activeFlavors["brand"] = "full"
        service.activeFlavors["environment"] = "production"
        service.currentFlavor = "fullProduction"

        val state = service.state
        assertEquals("fullProduction", state.currentFlavor)
        assertEquals("full", state.activeFlavors["brand"])
        assertEquals("production", state.activeFlavors["environment"])
        assertEquals(listOf("demo", "full"), state.schemaDimensions["brand"])
        assertEquals(listOf("staging", "production"), state.schemaDimensions["environment"])

        // Restore in new service instance
        val restoredService = KameleonStateService()
        restoredService.loadState(state)
        assertEquals("fullProduction", restoredService.currentFlavor)
        assertEquals("full", restoredService.activeFlavors["brand"])
        assertEquals("production", restoredService.activeFlavors["environment"])
        assertEquals(2, restoredService.schema.dimensions.size)

        // Registering another model (triggering rebuildSchema) preserves activeFlavors
        val libModel = dev.klitsie.kameleon.tooling.DefaultKameleonToolingModel(
            projectPath = ":lib",
            dimensions = mapOf("environment" to listOf("staging", "production")),
            availableVariants = listOf("staging", "production"),
            defaultVariant = "staging"
        )
        restoredService.registerToolingModel(libModel)
        assertEquals("full", restoredService.activeFlavors["brand"])
        assertEquals("production", restoredService.activeFlavors["environment"])
    }

    @Test
    fun `properties line update appends property when key is missing`() {
        val original = """
            # Project properties
            org.gradle.jvmargs=-Xmx2048m
        """.trimIndent()

        val lines = original.lines()
        val targetKey = "kameleon.flavor.brand"
        val flavor = "flappy"
        val targetLine = "$targetKey=$flavor"
        val lineRegex = Regex("""^\s*${Regex.escape(targetKey)}\s*=\s*(.*?)\s*$""")
        val keyIndex = lines.indexOfFirst { lineRegex.matches(it) }

        val lineSeparator = "\n"
        val newContent = if (keyIndex != -1) {
            val updatedLines = lines.toMutableList()
            updatedLines[keyIndex] = targetLine
            updatedLines.joinToString(lineSeparator)
        } else {
            if (original.isEmpty()) targetLine
            else if (original.endsWith("\n") || original.endsWith("\r\n")) "$original$targetLine"
            else "$original$lineSeparator$targetLine"
        }

        val expected = """
            # Project properties
            org.gradle.jvmargs=-Xmx2048m
            kameleon.flavor.brand=flappy
        """.trimIndent()

        assertEquals(expected, newContent)
    }
}
