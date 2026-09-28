package dev.klitsie.kameleon.targets

import dev.klitsie.kameleon.dsl.KameleonExtension
import dev.klitsie.kameleon.resolveActiveVariant
import dev.klitsie.kameleon.resolveOverlayDirsForVariantName
import dev.klitsie.kameleon.tasks.MergeComposeResourcesTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.jetbrains.compose.ComposeExtension
import org.jetbrains.compose.resources.ResourcesExtension

class ComposeResourcesTarget(
    private val project: Project,
    private val extension: KameleonExtension
) {
    constructor(project: Project) : this(
        project,
        project.extensions.getByType(KameleonExtension::class.java)
    )

    fun configure(): TaskProvider<MergeComposeResourcesTask> {
        extension.baseResourceDir.convention(
            project.layout.projectDirectory.dir("src/commonMain/composeResources")
        )

        val mergeTask = project.tasks.register<MergeComposeResourcesTask>("mergeKameleonResources") {
            baseDir.set(
                project.provider {
                    val base = extension.baseResourceDir.orNull?.asFile
                    if (base != null && base.exists()) extension.baseResourceDir.get() else null
                }
            )
            overlayDirs.set(
                project.provider {
                    val active = resolveActiveVariant(project, extension)
                    resolveOverlayDirsForVariantName(project, extension, active)
                }
            )
            outputDir.convention(
                project.layout.buildDirectory.dir("generated/kameleon/composeResources")
            )
        }

        project.plugins.withId("org.jetbrains.compose") {
            project.afterEvaluate {
                val composeExtension = project.extensions.findByType(ComposeExtension::class.java)
                val resourcesExtension = composeExtension?.extensions?.findByType(ResourcesExtension::class.java)
                resourcesExtension?.customDirectory(
                    sourceSetName = "commonMain",
                    directoryProvider = mergeTask.flatMap { it.outputDir }
                )
            }
        }

        return mergeTask
    }
}
