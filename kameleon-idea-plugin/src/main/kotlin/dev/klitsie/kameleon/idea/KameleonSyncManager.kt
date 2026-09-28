package dev.klitsie.kameleon.idea

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.Project
import dev.klitsie.kameleon.idea.android.KameleonAgpBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.plugins.gradle.util.GradleConstants

@Service(Service.Level.PROJECT)
class KameleonSyncManager(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {

    private val log = Logger.getInstance(KameleonSyncManager::class.java)

    private val coordinator get() = KameleonSyncCoordinator.getInstance(project)

    fun reloadFlavors() {
        reconcileVariantsWithProperties(isInitialStartup = false)
    }

    fun reconcileVariantsWithProperties(isInitialStartup: Boolean = false) {
        coroutineScope.launch {
            try {
                val stateService = KameleonStateService.getInstance(project)
                stateService.reloadFlavors()
                alignAgpWithProperties()
            } catch (t: Throwable) {
                log.error("Failed during reconcileVariantsWithProperties (isInitialStartup=$isInitialStartup)", t)
            }
        }
    }

    suspend fun alignAgpWithProperties(): Int {
        return try {
            val agpBridge = project.service<KameleonAgpBridge>()
            val updated = agpBridge.alignAgpWithProperties()
            updated
        } catch (_: NoClassDefFoundError) {
            // Safe fallback when running without Android plugin
            0
        } catch (t: Throwable) {
            log.error("Failed to dispatch AGP variant alignment", t)
            0
        }
    }

    fun triggerProjectRefresh(project: Project = this.project) {
        if (coordinator.isAwaitingVariantSwitch.value || KameleonStartupManager.isSyncInProgress(project)) {
            return
        }
        coordinator.onVariantSwitchStarted()

        try {
            val state = KameleonStateService.getInstance(project)
            val flavorArgs = buildGradleFlavorArguments(state.getActiveSelections())
            val importSpec = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
                .use(ProgressExecutionMode.IN_BACKGROUND_ASYNC)

            if (flavorArgs.isNotEmpty()) {
                importSpec.withArguments(flavorArgs.joinToString(" "))
            }

            ExternalSystemUtil.refreshProjects(importSpec)
        } catch (t: Throwable) {
            log.error("Failed to trigger project refresh", t)
        }
    }

    companion object {
        fun getInstance(project: Project): KameleonSyncManager = project.service()
    }
}
