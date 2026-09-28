package dev.klitsie.kameleon.dsl

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class KameleonFlavor @Inject constructor(
    val name: String,
    objects: ObjectFactory
) {
    var applicationIdSuffix: String? = null
    val buildConfig: BuildConfigBlock = objects.newInstance(BuildConfigBlock::class.java)

    fun buildConfig(action: Action<BuildConfigBlock>) {
        action.execute(buildConfig)
    }
}
