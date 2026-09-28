package dev.klitsie.kameleon.idea

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class KameleonStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        project.getService(KameleonSyncListener::class.java).onStartup()
        KameleonStartupManager.getInstance(project).onStartup()
    }
}
