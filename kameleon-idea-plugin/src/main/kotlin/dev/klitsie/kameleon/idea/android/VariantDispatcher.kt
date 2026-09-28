package dev.klitsie.kameleon.idea.android

import com.android.tools.idea.gradle.project.entities.GradleAndroidModelEntity
import com.android.tools.idea.gradle.project.entities.modifyGradleAndroidModelEntity
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.project.sync.GradleSyncInvoker
import com.android.tools.idea.model.AndroidModel
import com.google.wireless.android.sdk.stats.GradleSyncStats
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.storage.MutableEntityStorage
import dev.klitsie.kameleon.idea.KameleonStateService
import dev.klitsie.kameleon.idea.KameleonSyncCoordinator
import dev.klitsie.kameleon.idea.KameleonVariantMapper
import dev.klitsie.kameleon.idea.ProjectFlavorSchema
import dev.klitsie.kameleon.idea.buildGradleFlavorArguments
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.gradle.util.GradleConstants

class VariantDispatcher(private val project: Project) {

    private val coordinator get() = KameleonSyncCoordinator.getInstance(project)
    private val log = Logger.getInstance(VariantDispatcher::class.java)

    suspend fun dispatchDimensionChange(
        dimension: String,
        flavor: String,
        schema: ProjectFlavorSchema,
        activeFlavors: Map<String, String>,
    ): Int {
        return dispatch(schema, activeFlavors, targetDimension = dimension, targetFlavor = flavor)
    }

    suspend fun dispatchAlignment(
        schema: ProjectFlavorSchema,
        activeFlavors: Map<String, String>,
    ): Int {
        return dispatch(schema, activeFlavors, targetDimension = null, targetFlavor = null)
    }

    private suspend fun dispatch(
        schema: ProjectFlavorSchema,
        activeFlavors: Map<String, String>,
        targetDimension: String?,
        targetFlavor: String?,
    ): Int {
        val modulesToUpdate = mutableListOf<Pair<Module, String>>()
        val modules = getDeduplicatedAndroidModules()
        for (module in modules) {
            try {
                val modulePath = getModuleProjectPath(module)
                val moduleDims = getModuleDeclaredDimensions(module, modulePath, schema)

                if (targetDimension != null) {
                    // Only update if module specifically declares the modified dimension
                    if (moduleDims == null || !moduleDims.contains(targetDimension)) {
                        continue
                    }
                } else {
                    // Alignment: only update if module specifically declares dimensions in schema
                    if (moduleDims == null || moduleDims.isEmpty() || !moduleDims.any { it in schema.dimensions }) {
                        continue
                    }
                }

                // Strict Guard 1: Filter Non-Variant / KMP Source-Set Modules
                val availableVariants = getModuleAvailableVariants(module)
                if (availableVariants.isEmpty()) {
                    continue
                }

                val currentAgpVariant = getAgpSelectedVariant(module)
                if (currentAgpVariant == null || availableVariants.none {
                        it.equals(
                            currentAgpVariant,
                            ignoreCase = true,
                        )
                    }) {
                    continue
                }

                // Determine relevant dimensions for this module
                val relevantDims = schema.dimensions.keys.filter { it in moduleDims }

                val flavorList = if (relevantDims.isNotEmpty()) {
                    relevantDims.mapNotNull { activeFlavors[it] ?: schema.dimensions[it]?.firstOrNull() }
                } else {
                    listOf(targetFlavor ?: activeFlavors["default"] ?: "default")
                }

                val fallbackFlavor = targetFlavor ?: "default"
                val compositeFlavor =
                    if (flavorList.isEmpty()) fallbackFlavor else KameleonVariantMapper.buildVariantName(flavorList)
                val buildType = if (currentAgpVariant.endsWith("Release", ignoreCase = true)) "Release" else "Debug"
                val expectedVariant = "${compositeFlavor.replaceFirstChar { it.lowercase() }}$buildType"

                // Strict Guard 2: Module-Specific Variant Resolution & Existence Check
                val matchedVariant = matchVariantName(expectedVariant, compositeFlavor, buildType, availableVariants)
                if (matchedVariant == null) {
                    continue
                }

                // Strict Guard 3: Active Selection Equality (Hard NO-OP)
                if (currentAgpVariant.equals(matchedVariant, ignoreCase = true)) {
                    continue
                }

                modulesToUpdate.add(module to matchedVariant)
            } catch (t: Throwable) {
                log.error("Failed to determine AGP variant update for module ${module.name}", t)
            }
        }

        if (modulesToUpdate.isEmpty()) {
            return 0
        }

        val updatedCount = batchSetAgpSelectedVariants(modulesToUpdate.toMap())

        if (updatedCount > 0) {
            triggerSingleProjectSync()
        }
        return updatedCount
    }

    fun getDeduplicatedAndroidModules(): List<Module> {
        val moduleManager = ModuleManager.getInstance(project)
        val candidateModules = getAndroidModules()
        val holderModules = candidateModules.map { module ->
            findHolderModule(module, moduleManager)
        }
        return holderModules
            .filterNot { isTestOrSyntheticModule(it) }
            .distinct()
    }

    fun findHolderModule(
        module: Module,
        moduleManager: ModuleManager = ModuleManager.getInstance(project),
    ): Module {
        return Companion.findHolderModule(module, moduleManager)
    }

    fun getAndroidModules(): List<Module> {
        val facetManager = ProjectFacetManager.getInstance(project)
        val modules = facetManager.getModulesWithFacet(AndroidFacet.ID)
        if (modules.isNotEmpty()) {
            return modules.distinct()
        }
        return ModuleManager.getInstance(project).modules.filter { hasAndroidFacet(it) }.distinct()
    }

    fun hasAndroidFacet(module: Module): Boolean {
        return getAndroidFacet(module) != null
    }

    fun getAndroidFacet(module: Module): AndroidFacet? = AndroidFacet.getInstance(module)

    fun findAndroidModuleWithFacet(preferredModule: Module): Module? {
        if (hasAndroidFacet(preferredModule)) return preferredModule
        val moduleManager = ModuleManager.getInstance(project)
        val directMain = moduleManager.findModuleByName("${preferredModule.name}.main")
        if (directMain != null && hasAndroidFacet(directMain)) {
            return directMain
        }
        val moduleName = preferredModule.name
        val baseName = moduleName.substringBeforeLast(".")
        val candidate = moduleManager.findModuleByName("$baseName.main")
        if (candidate != null && hasAndroidFacet(candidate)) {
            return candidate
        }
        return moduleManager.modules.firstOrNull {
            (it.name == "${preferredModule.name}.main" || it.name.startsWith("${preferredModule.name}.")) && hasAndroidFacet(
                it,
            )
        } ?: moduleManager.modules.firstOrNull {
            it.name.startsWith(baseName) && hasAndroidFacet(it)
        }
    }

    fun isAppModule(module: Module): Boolean {
        val targetModule = findAndroidModuleWithFacet(module) ?: module
        val facet = getAndroidFacet(targetModule)
        if (facet != null) {
            return facet.configuration.isAppProject
        }
        return GradleAndroidModel.get(targetModule)?.androidProject?.projectType?.name?.contains(
            "APP",
            ignoreCase = true,
        ) == true
    }

    fun getModuleDeclaredDimensions(
        module: Module,
        modulePath: String,
        schema: ProjectFlavorSchema,
    ): Set<String>? {
        if (schema.moduleDimensions.isNotEmpty()) {
            if (schema.moduleDimensions.containsKey(modulePath)) {
                return schema.moduleDimensions[modulePath]
            }
            val match = schema.moduleDimensions.entries.firstOrNull { (path, _) ->
                modulePath == path ||
                    modulePath.endsWith(path) ||
                    path.endsWith(modulePath) ||
                    module.name.contains(path.removePrefix(":").replace(':', '.'))
            }
            if (match != null) {
                return match.value
            }
            // Strict check: if schema.moduleDimensions is populated, modules absent from it
            // do not have the Kameleon Gradle plugin applied and must not be flavored.
            return null
        }

        val targetModule = findAndroidModuleWithFacet(module) ?: module
        val facet = getAndroidFacet(targetModule)
        val model = facet?.let { GradleAndroidModel.get(it) } ?: GradleAndroidModel.get(targetModule)
        return model?.productFlavorNamesByFlavorDimension?.keys
            ?: model?.androidProject?.flavorDimensions?.toSet()
            ?: run {
                if (schema.dimensions.size == 1 && schema.dimensions.containsKey("default")) {
                    setOf("default")
                } else {
                    null
                }
            }
    }

    fun getRootGradlePath(module: Module): String {
        try {
            val extId = ExternalSystemApiUtil.getExternalProjectId(module)
            if (!extId.isNullOrBlank()) {
                return sanitizeGradlePath(extId)
            }
        } catch (_: Throwable) {
        }

        try {
            val extProp = ExternalSystemModulePropertyManager.getInstance(module)
            val linkedId = extProp.getLinkedProjectId()
            if (!linkedId.isNullOrBlank()) {
                return sanitizeGradlePath(linkedId)
            }
        } catch (_: Throwable) {
        }

        val name = module.name
        return sanitizeGradlePath(name.replace('.', ':'))
    }

    fun getModuleProjectPath(module: Module): String {
        return getRootGradlePath(module)
    }

    fun getAndroidModel(module: Module): AndroidModel? {
        val targetModule = findAndroidModuleWithFacet(module) ?: module
        val facet = getAndroidFacet(targetModule)
        return facet?.let { AndroidModel.get(it) } ?: AndroidModel.get(targetModule) ?: GradleAndroidModel.get(
            targetModule,
        )
    }

    suspend fun batchSetAgpSelectedVariants(targetVariants: Map<Module, String>): Int = withContext(Dispatchers.EDT) {
        if (targetVariants.isEmpty()) return@withContext 0
        val modifiedModules = mutableSetOf<Module>()

        // 1. Batch Workspace Model Updates
        try {
            runWriteAction {
                WorkspaceModel.getInstance(project)
                    .updateProjectModel("Batch switch variants") { storage: MutableEntityStorage ->
                        for ((module, targetVariant) in targetVariants) {
                            try {
                                val targetModule = findAndroidModuleWithFacet(module) ?: module
                                val candidateNames = setOf(
                                    targetModule.name,
                                    module.name,
                                    targetModule.name.removeSuffix(".main"),
                                    module.name.removeSuffix(".main"),
                                )

                                val moduleEntity = candidateNames.firstNotNullOfOrNull { name ->
                                    storage.resolve(ModuleId(name))
                                } ?: storage.entities(ModuleEntity::class.java)
                                    .firstOrNull { it.name in candidateNames }

                                val androidEntity = if (moduleEntity != null) {
                                    storage.entities(GradleAndroidModelEntity::class.java)
                                        .firstOrNull { it.module == moduleEntity }
                                } else {
                                    storage.entities(GradleAndroidModelEntity::class.java)
                                        .firstOrNull { it.module.name in candidateNames }
                                }

                                if (androidEntity != null) {
                                    val currentData = androidEntity.gradleAndroidModel.data
                                    if (currentData.selectedVariantName != targetVariant) {
                                        val updatedData = currentData.copy(selectedVariantName = targetVariant)
                                        val updatedModel = androidEntity.gradleAndroidModel.copy(data = updatedData)
                                        storage.modifyGradleAndroidModelEntity(androidEntity) {
                                            this.gradleAndroidModel = updatedModel
                                        }
                                        modifiedModules.add(module)
                                    }
                                }
                            } catch (t: Throwable) {
                                log.error("Failed to update WorkspaceModel entity for module ${module.name}", t)
                            }
                        }
                    }
            }
        } catch (t: Throwable) {
            log.error("Failed to execute WorkspaceModel updateProjectModel transaction", t)
        }

        // 2. Synchronize Facet Persistent State
        for ((module, targetVariant) in targetVariants) {
            try {
                val targetModule = findAndroidModuleWithFacet(module) ?: module
                val facet = getAndroidFacet(targetModule) ?: getAndroidFacet(module)
                if (facet != null) {
                    val currentState = facet.configuration.state.SELECTED_BUILD_VARIANT
                    if (currentState != targetVariant) {
                        facet.configuration.state.SELECTED_BUILD_VARIANT = targetVariant
                        modifiedModules.add(module)
                    }
                }
            } catch (t: Throwable) {
                log.error("Failed to mutate AndroidFacet configuration state for module ${module.name}", t)
            }
        }

        return@withContext modifiedModules.size
    }

    fun getAgpSelectedVariant(module: Module): String? {
        val targetModule = findAndroidModuleWithFacet(module) ?: module
        return try {
            val androidModel = getAndroidModel(targetModule)
            if (androidModel is GradleAndroidModel) {
                val variantName = androidModel.selectedVariantName
                if (variantName.isNotBlank()) {
                    return variantName
                }
            }

            val facet = getAndroidFacet(targetModule)
            if (facet != null) {
                val variantFromState = facet.configuration.state.SELECTED_BUILD_VARIANT
                if (!variantFromState.isNullOrBlank()) {
                    return variantFromState
                }
            }

            null
        } catch (e: Throwable) {
            log.error("Failed to get variant from AndroidModel for module ${targetModule.name}", e)
            null
        }
    }

    fun getModuleAvailableVariants(module: Module): List<String> {
        val targetModule = findAndroidModuleWithFacet(module) ?: module
        val facet = getAndroidFacet(targetModule)
        val gradleModel = facet?.let { GradleAndroidModel.get(it) } ?: GradleAndroidModel.get(targetModule)
        if (gradleModel != null) {
            return gradleModel.filteredVariantNames.toList()
        }
        return emptyList()
    }

    fun matchVariantName(
        expectedVariant: String,
        compositeFlavor: String,
        buildType: String,
        availableVariants: List<String>,
    ): String? {
        if (availableVariants.isEmpty()) return null

        return availableVariants.firstOrNull { it == expectedVariant }
            ?: availableVariants.firstOrNull { it.equals(expectedVariant, ignoreCase = true) }
            ?: availableVariants.firstOrNull {
                it.contains(compositeFlavor, ignoreCase = true) && it.endsWith(
                    buildType,
                    ignoreCase = true,
                )
            }
    }

    suspend fun triggerSingleProjectSync(): Boolean {
        return try {
            coordinator.onVariantSwitchStarted()
            withContext(Dispatchers.EDT) {
                requestGradleSync()
            }
            true
        } catch (t: Throwable) {
            log.error("Exception during project sync after variant changes", t)
            coordinator.reset()
            false
        }
    }

    fun requestGradleSync(targetProject: Project = this.project) {
        runCatching {
            GradleSyncInvoker.getInstance().requestProjectSync(
                project,
                GradleSyncInvoker.Request(GradleSyncStats.Trigger.TRIGGER_PROJECT_MODIFIED),
            )
        }.onFailure {
            log.warn("GradleSyncInvoker.requestProjectSync failed, attempting fallbackExternalSystemRefresh", it)
            fallbackExternalSystemRefresh(targetProject)
        }
    }

    private fun fallbackExternalSystemRefresh(targetProject: Project = this.project) {
        try {
            val state = KameleonStateService.getInstance(targetProject)
            val flavorArgs = buildGradleFlavorArguments(state.getActiveSelections())
            val importSpec = ImportSpecBuilder(targetProject, GradleConstants.SYSTEM_ID)
                .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)

            if (flavorArgs.isNotEmpty()) {
                importSpec.withArguments(flavorArgs.joinToString(" "))
            }

            ExternalSystemUtil.refreshProjects(importSpec)
            log.info("Triggered project sync fallback via ExternalSystemUtil.refreshProjects")
        } catch (t: Throwable) {
            log.warn("ExternalSystemUtil.refreshProjects failed, attempting refreshProject", t)
            try {
                val basePath = targetProject.basePath
                if (basePath != null) {
                    ExternalSystemUtil.refreshProject(
                        targetProject,
                        GradleConstants.SYSTEM_ID,
                        basePath,
                        false,
                        ProgressExecutionMode.IN_BACKGROUND_ASYNC,
                    )
                    log.info("Triggered project sync fallback via ExternalSystemUtil.refreshProject")
                }
            } catch (t2: Throwable) {
                log.error("Failed all fallback sync methods", t2)
            }
        }
    }

    companion object {

        fun findHolderModule(module: Module, moduleManager: ModuleManager): Module {
            val sourceSetSuffixes = listOf(".main", ".unitTest", ".test", ".androidTest", ".jvmTest")
            for (suffix in sourceSetSuffixes) {
                if (module.name.endsWith(suffix)) {
                    val strippedName = module.name.removeSuffix(suffix)
                    val candidate = moduleManager.findModuleByName(strippedName)
                    if (candidate != null) {
                        return candidate
                    }
                }
            }

            // Fallback: check Gradle external project identity
            val externalProjectId = try {
                ExternalSystemApiUtil.getExternalProjectId(module)
            } catch (_: Throwable) {
                null
            }
            if (!externalProjectId.isNullOrBlank()) {
                val delimiterSuffixes = listOf(
                    ":main", ":unitTest", ":test", ":androidTest", ":jvmTest",
                    ".main", ".unitTest", ".test", ".androidTest", ".jvmTest",
                )
                val matchedDelimiter = delimiterSuffixes.firstOrNull { externalProjectId.endsWith(it) }
                if (matchedDelimiter != null) {
                    val owningPath = externalProjectId.removeSuffix(matchedDelimiter)
                    if (owningPath.isNotEmpty()) {
                        val holder = moduleManager.modules.firstOrNull { candidate ->
                            val candidateExtId = try {
                                ExternalSystemApiUtil.getExternalProjectId(candidate)
                            } catch (_: Throwable) {
                                null
                            }
                            val candidateLinkedId = try {
                                ExternalSystemModulePropertyManager.getInstance(candidate).getLinkedProjectId()
                            } catch (_: Throwable) {
                                null
                            }
                            candidateExtId == owningPath || candidateLinkedId == owningPath
                        }
                        if (holder != null) {
                            return holder
                        }
                    }
                }
            }

            return module
        }

        fun isTestOrSyntheticModule(module: Module): Boolean {
            return isTestOrSyntheticName(module.name)
        }

        fun isTestOrSyntheticName(name: String): Boolean {
            return name.contains(".unitTest") ||
                    name.contains(":unitTest") ||
                    name.contains(".androidTest") ||
                    name.contains(":androidTest") ||
                    name.contains(".androidTestFixtures") ||
                    name.contains(":androidTestFixtures") ||
                    name.contains(".test") ||
                    name.contains(":test") ||
                    name.contains(".jvmTest") ||
                    name.contains(":jvmTest")
        }

        fun sanitizeGradlePath(rawPath: String): String {
            val colonPath = if (rawPath.startsWith(":")) {
                rawPath
            } else if (rawPath.contains(':')) {
                ":$rawPath"
            } else {
                ":${rawPath.replace('.', ':')}"
            }
            var sanitized = colonPath
                .removeSuffix(".main")
                .removeSuffix(":main")
                .removeSuffix(".unitTest")
                .removeSuffix(":unitTest")
                .removeSuffix(".androidTest")
                .removeSuffix(":androidTest")
                .removeSuffix(".androidTestFixtures")
                .removeSuffix(":androidTestFixtures")
                .removeSuffix(".test")
                .removeSuffix(":test")
                .removeSuffix(".jvmTest")
                .removeSuffix(":jvmTest")
            if (!sanitized.startsWith(":")) {
                sanitized = ":$sanitized"
            }
            return sanitized
        }
    }
}
