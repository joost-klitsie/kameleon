package dev.klitsie.kameleon.idea

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class KameleonSyncCoordinator(
    private val project: Project,
    coroutineScope: CoroutineScope,
) {
    val activeSyncCount = MutableStateFlow(0)
    val isAwaitingVariantSwitch = MutableStateFlow(false)

    init {
        coroutineScope.launch {
            // wait for first sync to complete
            activeSyncCount.first { it > 0 }
            activeSyncCount
                .debounce(300.milliseconds)
                .filter { it == 0 }
                .collect { onAllSyncsSettled() }
        }
    }

    fun onSyncStarted() {
        activeSyncCount.update { it + 1 }
    }

    fun onSyncEnded() {
        activeSyncCount.update { (it - 1).coerceAtLeast(0) }
    }

    fun onVariantSwitchStarted() {
        isAwaitingVariantSwitch.update { true }
    }

    suspend fun awaitSyncSettled() {
        withTimeoutOrNull(2.seconds) {
            activeSyncCount.first { it > 0 }
        }
        activeSyncCount.first { it == 0 }
        awaitSmartMode(project)
    }

    suspend fun awaitSmartMode(project: Project) {
        val dumbService = DumbService.getInstance(project)
        if (!dumbService.isDumb) return

        suspendCancellableCoroutine { continuation ->
            dumbService.runWhenSmart {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        }
    }

    private fun onAllSyncsSettled() {
        val wasVariantSwitch = isAwaitingVariantSwitch.getAndUpdate { false }

        if (wasVariantSwitch) {
            // Internal variant switch cascade completed across all modules; do not trigger reload
            return
        }

        // External sync completed (git checkout, user sync, build file edit)
        DumbService.getInstance(project).runWhenSmart {
            KameleonSyncManager.getInstance(project).reloadFlavors()
        }
    }

    fun reset() {
        activeSyncCount.update { 0 }
        isAwaitingVariantSwitch.update { false }
    }

    companion object {
        fun getInstance(project: Project): KameleonSyncCoordinator =
            project.getService(KameleonSyncCoordinator::class.java)
    }
}
