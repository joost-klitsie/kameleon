package dev.klitsie.kameleon.dsl

import org.gradle.api.Action
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

open class KameleonFlavor @Inject constructor(
    val name: String,
    objects: ObjectFactory,
    private val providers: ProviderFactory,
    private val projectDirectory: Provider<Directory>,
    private val rootProjectDirectory: Provider<Directory>,
) {
    var applicationIdSuffix: String? = null
    val buildConfig: BuildConfigBlock = objects.newInstance(BuildConfigBlock::class.java)
    val hoconConfig: HoconConfigBlock =
        objects.newInstance(HoconConfigBlock::class.java, projectDirectory, rootProjectDirectory)

    fun buildConfig(action: Action<BuildConfigBlock>) {
        action.execute(buildConfig)
    }

    fun hoconConfig(action: Action<HoconConfigBlock>) {
        action.execute(hoconConfig)
    }

    fun localProperty(key: String): Provider<String> =
        createLocalPropertyProvider(providers, projectDirectory, rootProjectDirectory, key)

    fun localProperty(key: String, defaultValue: String): Provider<String> =
        localProperty(key).orElse(defaultValue)
}
