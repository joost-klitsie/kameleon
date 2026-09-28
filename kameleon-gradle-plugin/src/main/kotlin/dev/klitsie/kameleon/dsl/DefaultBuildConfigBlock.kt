package dev.klitsie.kameleon.dsl

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import javax.inject.Inject

open class DefaultBuildConfigBlock @Inject constructor(objects: ObjectFactory) : BuildConfigBlock(objects) {
    val packageName: Property<String> = objects.property(String::class.java).convention("dev.klitsie.kameleon")
    val className: Property<String> = objects.property(String::class.java).convention("KameleonConfig")
}
