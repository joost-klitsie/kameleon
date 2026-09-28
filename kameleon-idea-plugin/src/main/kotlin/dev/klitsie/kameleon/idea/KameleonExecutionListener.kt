package dev.klitsie.kameleon.idea

import com.intellij.execution.ExecutionListener
import com.intellij.execution.runners.ExecutionEnvironment
import org.jetbrains.plugins.gradle.service.execution.GradleRunConfiguration

class KameleonExecutionListener : ExecutionListener {
    override fun processStarting(executorId: String, env: ExecutionEnvironment) {
        val project = env.project
        val runProfile = env.runProfile
        if (runProfile is GradleRunConfiguration) {
            val state = KameleonStateService.getInstance(project)
            val flags = buildGradleFlavorArguments(state.getActiveSelections())
            if (flags.isNotEmpty()) {
                val currentParams = runProfile.settings.scriptParameters ?: ""
                runProfile.settings.scriptParameters = injectGradleFlavorArguments(currentParams, flags)
            }
        }
    }
}
