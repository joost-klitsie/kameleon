package dev.klitsie.kameleon.idea

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import dev.klitsie.kameleon.tooling.KameleonToolingModel
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

class KameleonProjectResolverExtension : AbstractProjectResolverExtension() {

    override fun getExtraProjectModelClasses(): Set<Class<*>> {
        return setOf(KameleonToolingModel::class.java)
    }

    override fun getToolingExtensionsClasses(): Set<Class<*>> {
        return setOf(KameleonModelBuilderService::class.java, KameleonToolingModel::class.java)
    }

    override fun getExtraCommandLineArgs(): List<String> {
        val proj = resolverCtx.externalSystemTaskId.findProject()
        if (proj != null) {
            val state = KameleonStateService.getInstance(proj)
            return buildGradleFlavorArguments(state.getActiveSelections())
        }
        return emptyList()
    }

    override fun populateModuleExtraModels(gradleModule: IdeaModule, ideModule: DataNode<ModuleData>) {
        val model = resolverCtx.getProjectModel(gradleModule, KameleonToolingModel::class.java)
        if (model != null) {
            val rootPath = resolverCtx.externalProjectPath
            KameleonStateService.registerDiscoveredModel(rootPath, model)
        }
        super.populateModuleExtraModels(gradleModule, ideModule)
    }
}
