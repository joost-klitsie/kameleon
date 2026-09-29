package dev.klitsie.kameleon.idea

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import dev.klitsie.kameleon.idea.android.VariantDispatcher
import dev.klitsie.kameleon.tooling.DefaultKameleonToolingModel
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KameleonPluginTest {

    // region KameleonStateService Tests

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
    fun `state service active selections resolves schema dimensions and fallback`() {
        val service = KameleonStateService()

        // 1. Default fallback when empty
        assertEquals(mapOf<String?, String>("default" to "default"), service.getActiveSelections())

        // 2. Fallback when only currentFlavor is set
        service.currentFlavor = "staging"
        assertEquals(mapOf<String?, String>("default" to "staging"), service.getActiveSelections())

        // 3. Fallback when only flat activeFlavors is set
        service.activeFlavors["default"] = "production"
        assertEquals(mapOf<String?, String>("default" to "production"), service.getActiveSelections())

        // 4. Schema dimensions resolution
        val model = DefaultKameleonToolingModel(
            projectPath = ":app",
            dimensions = mapOf(
                "brand" to listOf("demo", "full"),
                "environment" to listOf("staging", "production")
            ),
            availableVariants = listOf("demoStaging", "demoProduction", "fullStaging", "fullProduction"),
            defaultVariant = "demoStaging"
        )
        service.registerToolingModel(model)

        // Dimension active values default to first flavor if not explicitly selected
        assertEquals(mapOf<String?, String>("brand" to "demo", "environment" to "staging"), service.getActiveSelections())

        // Explicit dimension selection
        service.activeFlavors["brand"] = "full"
        service.activeFlavors["environment"] = "production"
        assertEquals(mapOf<String?, String>("brand" to "full", "environment" to "production"), service.getActiveSelections())
    }

    @Test
    fun `schema aggregation correctly merges dimensions and module dimensions from tooling models`() {
        val service = KameleonStateService()
        val appModel = DefaultKameleonToolingModel(
            projectPath = ":sample:androidApp",
            dimensions = mapOf(
                "brand" to listOf("demo", "full"),
                "environment" to listOf("staging", "production")
            ),
            availableVariants = listOf("demoStaging", "demoProduction", "fullStaging", "fullProduction"),
            defaultVariant = "demoStaging"
        )
        val libModel = DefaultKameleonToolingModel(
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
    fun `schema aggregation preserves dimension occurrence order from model`() {
        val service = KameleonStateService()
        val appModel = DefaultKameleonToolingModel(
            projectPath = ":sample:androidApp",
            dimensions = linkedMapOf(
                "target" to listOf("phone", "tablet"),
                "environment" to listOf("staging", "production")
            ),
            availableVariants = listOf("phoneStaging", "phoneProduction", "tabletStaging", "tabletProduction"),
            defaultVariant = "phoneStaging"
        )

        service.registerToolingModel(appModel)

        val schema = service.schema
        assertEquals(listOf("target", "environment"), schema.dimensions.keys.toList())
    }

    @Test
    fun `state service serializes and restores activeFlavors and schema without losing selections during schema rebuild`() {
        val service = KameleonStateService()
        val appModel = DefaultKameleonToolingModel(
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
        val libModel = DefaultKameleonToolingModel(
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
    fun `state service discovered models staging queues and consumes models by root path`() {
        val rootPath = "/path/to/project"
        val model = DefaultKameleonToolingModel(
            projectPath = ":app",
            dimensions = mapOf("brand" to listOf("demo", "full")),
            availableVariants = listOf("demo", "full"),
            defaultVariant = "demo"
        )
        val expected = listOf(model)

        KameleonStateService.registerDiscoveredModel(rootPath, model)
        val consumed = KameleonStateService.consumeDiscoveredModels(rootPath)
        assertEquals(expected, consumed)

        // Consuming again returns empty list since pending models are removed
        val consumedAgain = KameleonStateService.consumeDiscoveredModels(rootPath)
        assertTrue(consumedAgain.isEmpty())
    }

    // endregion

    // region FlavorDiscovery Tests

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
        assertTrue(extracted.isEmpty())
    }

    // endregion

    // region KameleonVariantMapper Tests

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

    // endregion

    // region GradleArguments Tests

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

    // endregion

    // region VariantDispatcher Tests

    @Test
    fun `test and synthetic module filtering identifies test modules correctly`() {
        val testNames = listOf(
            "sample.androidApp.unitTest",
            "sample.androidApp.androidTest",
            "sample.androidApp.androidTestFixtures",
            "sample.shared-app.test",
            "project:feature:unitTest",
            "project:feature:androidTest",
            "sample.androidApp.jvmTest",
            ":sample:feature:jvmTest"
        )
        for (name in testNames) {
            assertTrue(
                VariantDispatcher.isTestOrSyntheticName(name),
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
            assertTrue(
                !VariantDispatcher.isTestOrSyntheticName(name),
                "Expected $name to NOT be identified as test/synthetic"
            )
        }
    }

    @Test
    fun `sanitizeGradlePath strips source sets and normalizes path`() {
        assertEquals(
            ":sample:androidApp",
            VariantDispatcher.sanitizeGradlePath(":sample:androidApp.main")
        )
        assertEquals(
            ":sample:androidApp",
            VariantDispatcher.sanitizeGradlePath(":sample:androidApp:main")
        )
        assertEquals(
            ":sample:androidApp",
            VariantDispatcher.sanitizeGradlePath("sample.androidApp.unitTest")
        )
        assertEquals(
            ":sample:androidApp",
            VariantDispatcher.sanitizeGradlePath("sample:androidApp:androidTestFixtures")
        )
        assertEquals(
            ":feature",
            VariantDispatcher.sanitizeGradlePath("feature.test")
        )
        assertEquals(
            ":sample:androidApp",
            VariantDispatcher.sanitizeGradlePath("sample:androidApp:jvmTest")
        )
    }

    @Test
    fun `findHolderModule resolves source set suffix modules to their holder modules`() {
        val holderApp = createMockModule("kameleon.sample.androidApp")
        val holderApp2 = createMockModule("kameleon.sample.androidApp2")
        val holderShared = createMockModule("kameleon.sample.shared-app")
        val standalone = createMockModule("standalone-module")

        val mainApp = createMockModule("kameleon.sample.androidApp.main")
        val unitTestApp = createMockModule("kameleon.sample.androidApp.unitTest")
        val testApp = createMockModule("kameleon.sample.androidApp.test")
        val androidTestApp = createMockModule("kameleon.sample.androidApp.androidTest")
        val jvmTestApp = createMockModule("kameleon.sample.androidApp.jvmTest")

        val mainApp2 = createMockModule("kameleon.sample.androidApp2.main")

        val allModules = listOf(holderApp, holderApp2, holderShared, standalone, mainApp, unitTestApp, testApp, androidTestApp, jvmTestApp, mainApp2)
        val moduleManager = createMockModuleManager(allModules)

        assertEquals(holderApp, VariantDispatcher.findHolderModule(mainApp, moduleManager))
        assertEquals(holderApp, VariantDispatcher.findHolderModule(unitTestApp, moduleManager))
        assertEquals(holderApp, VariantDispatcher.findHolderModule(testApp, moduleManager))
        assertEquals(holderApp, VariantDispatcher.findHolderModule(androidTestApp, moduleManager))
        assertEquals(holderApp, VariantDispatcher.findHolderModule(jvmTestApp, moduleManager))
        assertEquals(holderApp2, VariantDispatcher.findHolderModule(mainApp2, moduleManager))
        assertEquals(standalone, VariantDispatcher.findHolderModule(standalone, moduleManager))
    }

    @Test
    fun `holder module resolution maps source set candidates to holder modules and deduplicates`() {
        val holder1 = createMockModule("kameleon.sample.androidApp")
        val main1 = createMockModule("kameleon.sample.androidApp.main")
        val unitTest1 = createMockModule("kameleon.sample.androidApp.unitTest")
        val androidTest1 = createMockModule("kameleon.sample.androidApp.androidTest")

        val holder2 = createMockModule("kameleon.sample.androidApp2")
        val main2 = createMockModule("kameleon.sample.androidApp2.main")

        val mm = createMockModuleManager(listOf(holder1, holder2, main1, unitTest1, androidTest1, main2))

        val candidateModules = listOf(main1, unitTest1, androidTest1, main2)
        val resolvedHolders = candidateModules.map { VariantDispatcher.findHolderModule(it, mm) }
            .filterNot { VariantDispatcher.isTestOrSyntheticModule(it) }
            .distinct()

        assertEquals(listOf(holder1, holder2), resolvedHolders)
        assertEquals(listOf("kameleon.sample.androidApp", "kameleon.sample.androidApp2"), resolvedHolders.map { it.name })
    }

    @Test
    fun `variant name matching against available variants handles exact and fuzzy matches and returns null on missing`() {
        val dispatcher = VariantDispatcher(createMockProject())
        val available = listOf("demoStagingDebug", "demoStagingRelease", "fullProductionDebug", "fullProductionRelease")

        // Exact match
        assertEquals("fullProductionDebug", dispatcher.matchVariantName("fullProductionDebug", "fullProduction", "Debug", available))

        // Case-insensitive match
        assertEquals("fullProductionRelease", dispatcher.matchVariantName("FullProductionRelease", "fullProduction", "Release", available))

        // Substring and build-type match
        assertEquals("fullProductionDebug", dispatcher.matchVariantName("customFullProductionDebug", "fullProduction", "Debug", available))

        // Return null when available list does not expose variant
        assertNull(dispatcher.matchVariantName("stagingDebug", "staging", "Debug", listOf("debug", "release")))

        // Return null when available list is empty
        assertNull(dispatcher.matchVariantName("customVariantDebug", "customVariant", "Debug", emptyList()))
    }

    @Test
    fun `VariantDispatcher getModuleDeclaredDimensions resolves dimensions from schema and excludes unconfigured modules`() {
        val dispatcher = VariantDispatcher(createMockProject())
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

        val appModule = createMockModule("sample.androidApp")
        val brandingModule = createMockModule("sample.shared-branding")
        val nonKameleonModule = createMockModule("sample.otherApp")

        // Module in schema -> resolved declared dimensions
        val appDims = dispatcher.getModuleDeclaredDimensions(appModule, ":sample:androidApp", schema)
        assertEquals(setOf("brand", "environment"), appDims)

        val brandingDims = dispatcher.getModuleDeclaredDimensions(brandingModule, ":sample:shared-branding", schema)
        assertEquals(setOf("brand"), brandingDims)

        // Non-Kameleon module NOT in schema -> null (skipped)
        val nonKameleonDims = dispatcher.getModuleDeclaredDimensions(nonKameleonModule, ":sample:otherApp", schema)
        assertNull(nonKameleonDims)
    }

    // endregion

    // region Test Helpers

    private fun createMockProject(): Project {
        return Proxy.newProxyInstance(
            Project::class.java.classLoader,
            arrayOf(Project::class.java)
        ) { _, _, _ -> null } as Project
    }

    private fun createMockModule(name: String): Module {
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

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION", "UnstableApiUsage", "NonExtendableApiUsage")
    private fun createMockModuleManager(modules: List<Module>): ModuleManager {
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

    // endregion
}
