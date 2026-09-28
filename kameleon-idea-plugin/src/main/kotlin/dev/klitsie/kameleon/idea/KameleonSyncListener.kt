package dev.klitsie.kameleon.idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class KameleonSyncListener(
    private val project: Project,
) : Disposable {

    val coordinator = KameleonSyncCoordinator.getInstance(project)

    fun onStartup() {
        val listener = object : ExternalSystemTaskNotificationListener {
            override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
                if (id.findProject() == project) {
                    coordinator.onSyncStarted()
                }
            }

            override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
                if (id.findProject() == project) {
                    coordinator.onSyncEnded()
                }
            }

            override fun onFailure(projectPath: String, id: ExternalSystemTaskId, e: java.lang.Exception) {
                if (id.findProject() == project) {
                    coordinator.onSyncEnded()
                }
            }

            override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
                if (id.findProject() == project) {
                    coordinator.onSyncEnded()
                }
            }
        }

        // Direct registration onto the engine's notification manager
        ExternalSystemProgressNotificationManager.getInstance()
            .addNotificationListener(listener, this)
    }

    override fun dispose() {}

}
