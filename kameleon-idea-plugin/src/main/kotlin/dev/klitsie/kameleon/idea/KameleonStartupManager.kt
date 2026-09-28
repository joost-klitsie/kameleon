package dev.klitsie.kameleon.idea

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
class KameleonStartupManager(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {

    val coordinator = KameleonSyncCoordinator.getInstance(project)

    fun onStartup() {
        coroutineScope.launch {
            coordinator.awaitSyncSettled()
            delay(1.seconds)
            reconcileVariantsWithProperties(isInitialStartup = true)
        }
    }

    fun reconcileVariantsWithProperties(isInitialStartup: Boolean = false) {
        KameleonSyncManager.getInstance(project).reconcileVariantsWithProperties(isInitialStartup)
    }

    companion object {
        fun getInstance(project: Project): KameleonStartupManager = project.service()

        fun isSyncInProgress(project: Project): Boolean {
            try {
                val syncStateClass = Class.forName("com.android.tools.idea.gradle.project.sync.GradleSyncState")
                val getInstanceMethod = syncStateClass.getMethod("getInstance", Project::class.java)
                val syncState = getInstanceMethod.invoke(null, project)
                if (syncState != null) {
                    val isSyncInProgressMethod = syncStateClass.methods.firstOrNull {
                        it.name == "isSyncInProgress" && it.parameterCount == 0
                    }
                    val inProgress = isSyncInProgressMethod?.invoke(syncState) as? Boolean
                    if (inProgress == true) {
                        return true
                    }
                }
            } catch (_: Throwable) {
                // Ignore and proceed to fallback
            }

            try {
                val extUtilClass = Class.forName("com.intellij.openapi.externalSystem.util.ExternalSystemUtil")
                val isInitialImportMethod = extUtilClass.methods.firstOrNull {
                    it.name == "isInitialImportInProgress" && it.parameterCount == 1 && it.parameterTypes[0] == Project::class.java
                }
                val inProgress = isInitialImportMethod?.invoke(null, project) as? Boolean
                if (inProgress == true) {
                    return true
                }
            } catch (_: Throwable) {
                // Ignore fallback failures
            }

            return false
        }
    }
}
