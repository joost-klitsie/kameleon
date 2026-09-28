package dev.klitsie.kameleon.idea.android

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import dev.klitsie.kameleon.idea.KameleonStateService
import dev.klitsie.kameleon.idea.ProjectFlavorSchema

@Service(Service.Level.PROJECT)
class KameleonAgpBridge(private val project: Project) {

    val dispatcher = VariantDispatcher(project)

    suspend fun onDimensionChanged(
        dimension: String,
        flavor: String,
        schema: ProjectFlavorSchema,
        activeFlavors: Map<String, String>,
    ): Int {
        return dispatcher.dispatchDimensionChange(dimension, flavor, schema, activeFlavors)
    }

    suspend fun alignAgpWithProperties(): Int {
        val state = project.service<KameleonStateService>()
        return dispatcher.dispatchAlignment(state.schema, state.activeFlavors)
    }

    companion object {
        fun getInstance(project: Project): KameleonAgpBridge = project.service()
    }
}
