package dev.klitsie.kameleon.dsl

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class KameleonDimension @Inject constructor(
    val name: String,
    private val objects: ObjectFactory
) {
    val flavors: NamedDomainObjectContainer<KameleonFlavor> =
        objects.domainObjectContainer(KameleonFlavor::class.java)

    val buildConfig: BuildConfigBlock = objects.newInstance(BuildConfigBlock::class.java)

    private val orderedFlavorNames = mutableListOf<String>()

    init {
        flavors.whenObjectAdded {
            if (!orderedFlavorNames.contains(name)) {
                orderedFlavorNames.add(name)
            }
        }
    }

    fun buildConfig(action: Action<BuildConfigBlock>) {
        action.execute(buildConfig)
    }

    // Flat shortcut: flavor("staging") { ... }
    fun flavor(name: String, action: Action<KameleonFlavor>? = null): KameleonFlavor {
        if (!orderedFlavorNames.contains(name)) {
            orderedFlavorNames.add(name)
        }
        val existing = flavors.findByName(name)
        val f = existing ?: flavors.create(name)
        action?.execute(f)
        return f
    }

    fun flavors(action: Action<NamedDomainObjectContainer<KameleonFlavor>>) {
        action.execute(flavors)
    }

    fun flavors(vararg flavorNames: String) {
        for (flavorName in flavorNames) {
            if (!orderedFlavorNames.contains(flavorName)) {
                orderedFlavorNames.add(flavorName)
            }
            flavors.maybeCreate(flavorName)
        }
    }

    fun register(vararg flavorNames: String) {
        flavors(*flavorNames)
    }

    fun register(name: String, action: Action<KameleonFlavor>) {
        flavor(name, action)
    }

    fun getOrderedFlavors(): List<KameleonFlavor> {
        val allFlavors = flavors.associateBy { it.name }
        val result = mutableListOf<KameleonFlavor>()
        for (fName in orderedFlavorNames) {
            allFlavors[fName]?.let { result.add(it) }
        }
        for (f in flavors) {
            if (!result.contains(f)) {
                result.add(f)
            }
        }
        return result
    }
}
