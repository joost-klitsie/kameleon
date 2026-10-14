package dev.klitsie.kameleon.dsl

import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import java.io.StringReader
import java.util.Properties

internal fun createLocalPropertyProvider(
    providers: ProviderFactory,
    projectDirectory: Provider<Directory>,
    rootProjectDirectory: Provider<Directory>,
    key: String
): Provider<String> {
    val projectLocalPropertiesFile = projectDirectory.map { it.file("local.properties") }
    val rootLocalPropertiesFile = rootProjectDirectory.map { it.file("local.properties") }
    val projectProperty = providers.fileContents(projectLocalPropertiesFile).asText.map { text ->
        val properties = Properties()
        StringReader(text).use { reader ->
            properties.load(reader)
        }
        properties.getProperty(key)
    }
    val rootProperty = providers.fileContents(rootLocalPropertiesFile).asText.map { text ->
        val properties = Properties()
        StringReader(text).use { reader ->
            properties.load(reader)
        }
        properties.getProperty(key)
    }
    return projectProperty.orElse(rootProperty)
}
