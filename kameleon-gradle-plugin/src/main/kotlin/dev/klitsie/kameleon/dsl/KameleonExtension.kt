package dev.klitsie.kameleon.dsl

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import javax.inject.Inject

open class KameleonExtension @Inject constructor(
    objects: ObjectFactory,
    private val providers: ProviderFactory
) {
    fun resolveFlavorForDimension(dim: String): Provider<String> {
        return providers.gradleProperty("kameleon.flavor.$dim")
            .orElse(providers.gradleProperty("kameleon.flavor"))
    }

    fun resolveDefaultFlavor(): Provider<String> {
        return providers.gradleProperty("kameleon.flavor")
    }
    val defaultVariant: Property<String> = objects.property(String::class.java).convention(
        providers.provider {
            val variants = dev.klitsie.kameleon.VariantMatrix.calculateVariants(getOrderedDimensions())
            variants.firstOrNull()?.name ?: "default"
        }
    )
    val activeVariant: Property<String> = objects.property(String::class.java).convention(
        providers.gradleProperty("kameleon.flavor")
            .orElse(defaultVariant)
    )

    // Backward compatibility aliases
    val defaultFlavor: Property<String>
        get() = defaultVariant

    val activeFlavor: Property<String>
        get() = activeVariant

    val defaultBuildConfig: DefaultBuildConfigBlock = objects.newInstance(DefaultBuildConfigBlock::class.java)

    fun defaultBuildConfig(action: Action<DefaultBuildConfigBlock>) {
        action.execute(defaultBuildConfig)
    }

    val baseResourceDir: DirectoryProperty = objects.directoryProperty()

    val dimensions: NamedDomainObjectContainer<KameleonDimension> =
        objects.domainObjectContainer(KameleonDimension::class.java)

    private val orderedDimensionNames = mutableListOf<String>()

    init {
        dimensions.whenObjectAdded {
            if (!orderedDimensionNames.contains(name)) {
                orderedDimensionNames.add(name)
            }
        }
    }

    // Flat shortcut: dimension("environment") { ... }
    fun dimension(name: String, action: Action<KameleonDimension>? = null): KameleonDimension {
        if (!orderedDimensionNames.contains(name)) {
            orderedDimensionNames.add(name)
        }
        val existing = dimensions.findByName(name)
        val dim = existing ?: dimensions.create(name)
        action?.execute(dim)
        return dim
    }

    fun dimension(name: String, vararg flavorNames: String): KameleonDimension {
        val dim = dimension(name)
        dim.flavors(*flavorNames)
        return dim
    }

    fun dimensions(action: Action<NamedDomainObjectContainer<KameleonDimension>>) {
        action.execute(dimensions)
    }

    fun getOrderedDimensions(): List<KameleonDimension> {
        val allDims = dimensions.associateBy { it.name }
        val result = mutableListOf<KameleonDimension>()
        for (dName in orderedDimensionNames) {
            allDims[dName]?.let { result.add(it) }
        }
        for (d in dimensions) {
            if (!result.contains(d)) {
                result.add(d)
            }
        }
        return result
    }

    // Flat flavor backwards compatibility
    val flavors: NamedDomainObjectContainer<KameleonFlavor>
        get() = dimension("environment").flavors

    fun flavors(action: Action<NamedDomainObjectContainer<KameleonFlavor>>) {
        action.execute(flavors)
    }

    fun flavors(vararg flavorNames: String) {
        val env = dimension("environment")
        for (name in flavorNames) {
            env.flavor(name)
        }
    }
}
