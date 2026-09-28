package dev.klitsie.kameleon.idea

import com.intellij.facet.Facet
import com.intellij.facet.FacetTypeId
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project

object FlavorDiscovery {

    private val log = Logger.getInstance(FlavorDiscovery::class.java)

    /**
     * Discovers available Kameleon flavors for the given project.
     * Uses the KameleonToolingModel schema from KameleonStateService first,
     * falling back to AndroidFacet inspection if the tooling model is absent.
     */
    fun discoverFlavors(project: Project): List<String> {
        val stateService = project.getService(KameleonStateService::class.java)
        if (stateService != null && stateService.schema.dimensions.isNotEmpty()) {
            if (stateService.availableFlavors.isNotEmpty()) {
                return stateService.availableFlavors
            }
            if (stateService.schema.dimensions.size == 1) {
                return stateService.schema.dimensions.values.first()
            }
            var product = listOf(listOf<String>())
            for (fList in stateService.schema.dimensions.values) {
                product = product.flatMap { existing -> fList.map { existing + it } }
            }
            return product.map { KameleonVariantMapper.buildVariantName(it) }.distinct()
        }

        val facets = getAndroidFacets(project)
        if (facets.isEmpty()) {
            log.warn("No Android facets found in project '${project.name}'")
            return emptyList()
        }

        val primaryFacet = findPrimaryAppFacet(facets) ?: facets.first()
        val variantNames = getVariantNamesFromFacet(primaryFacet)
        if (variantNames.isEmpty()) {
            log.warn("No variant names discovered from Android facet on module '${primaryFacet.module.name}'")
            return emptyList()
        }

        val flavors = extractFlavors(variantNames)
        log.info("Discovered flavors for project '${project.name}': $flavors (from variants: $variantNames)")
        return flavors
    }

    /**
     * Extracts distinct flavor combinations from variant names by stripping
     * standard build-type suffixes ("Debug", "Release") and lowercasing the leading character.
     * E.g. ["brandADefaultDebug", "brandAStagingRelease"] -> ["brandADefault", "brandAStaging"]
     */
    fun extractFlavors(variantNames: Collection<String>): List<String> {
        return variantNames.mapNotNull { variantName ->
            var flavor = variantName.trim()
            if (flavor.endsWith("Debug", ignoreCase = true)) {
                flavor = flavor.substring(0, flavor.length - "Debug".length)
            } else if (flavor.endsWith("Release", ignoreCase = true)) {
                flavor = flavor.substring(0, flavor.length - "Release".length)
            }
            val trimmed = flavor.replaceFirstChar { it.lowercase() }
            if (trimmed.isNotBlank()) trimmed else null
        }.distinct()
    }

    @Suppress("UNCHECKED_CAST")
    private fun getAndroidFacets(project: Project): List<Facet<*>> {
        return try {
            val androidFacetClass = Class.forName("org.jetbrains.android.facet.AndroidFacet")
            val idField = androidFacetClass.getField("ID")
            val facetTypeId = idField.get(null) as? FacetTypeId<Facet<*>>
            if (facetTypeId != null) {
                ProjectFacetManager.getInstance(project).getFacets(facetTypeId)
            } else {
                emptyList()
            }
        } catch (_: ClassNotFoundException) {
            emptyList()
        } catch (t: Throwable) {
            log.error("Failed to retrieve Android facets from ProjectFacetManager", t)
            emptyList()
        }
    }

    private fun findPrimaryAppFacet(facets: List<Facet<*>>): Facet<*>? {
        // Exclude test modules
        val nonTestFacets = facets.filterNot {
            val name = it.module.name
            name.contains("androidTest", ignoreCase = true) || name.contains("unitTest", ignoreCase = true)
        }
        val candidates = if (nonTestFacets.isNotEmpty()) nonTestFacets else facets

        // Check if any facet module name indicates the main application module
        return candidates.firstOrNull {
            val name = it.module.name
            it.name.endsWith(".main") && (name.contains("app", ignoreCase = true) || name.contains(
                "android",
                ignoreCase = true,
            ))
        } ?: candidates.firstOrNull {
            val name = it.module.name
            name.equals("app", ignoreCase = true) || name.endsWith(":app") || name.endsWith(".app")
        } ?: candidates.firstOrNull {
            it.module.name.contains("app", ignoreCase = true)
        } ?: candidates.firstOrNull {
            isAppFacet(it)
        } ?: candidates.firstOrNull()
    }

    private fun isAppFacet(facet: Facet<*>): Boolean {
        return try {
            val config = getFacetConfiguration(facet) ?: return false
            val isAppMethod = config.javaClass.methods.firstOrNull {
                it.name == "isApp" || it.name == "isAppType" || it.name == "isApplicationType"
            }
            isAppMethod != null && isAppMethod.invoke(config) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun getFacetConfiguration(facet: Facet<*>): Any {
        return try {
            val method = facet.javaClass.methods.firstOrNull { it.name == "getConfiguration" && it.parameterCount == 0 }
            method?.invoke(facet) ?: facet.configuration
        } catch (_: Throwable) {
            facet.configuration
        }
    }

    private fun getVariantNamesFromFacet(facet: Facet<*>): List<String> {
        val model = getAndroidModel(facet)
        if (model == null) {
            log.warn("AndroidFacet configuration model is not ready or null for module '${facet.module.name}'")
            return emptyList()
        }

        return getVariantNamesFromModel(model)
    }

    private fun getAndroidModel(facet: Facet<*>): Any? {
        // 1. Inspect facet.configuration.model
        try {
            val config = getFacetConfiguration(facet)
            if (config != null) {
                val getModelMethod = config.javaClass.methods.firstOrNull {
                    it.name == "getModel" || it.name == "getAndroidModel" || it.name == "model"
                }
                val model = getModelMethod?.invoke(config)
                if (model != null) {
                    return model
                }
            }
        } catch (t: Throwable) {
            log.error("Failed to get model from facet configuration", t)
        }

        // 2. Try GradleAndroidModel.get(facet) or GradleAndroidModel.get(module)
        try {
            val gradleAndroidModelClass =
                Class.forName("com.android.tools.idea.gradle.project.model.GradleAndroidModel")
            val getMethodWithFacet = gradleAndroidModelClass.methods.firstOrNull {
                it.name == "get" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(facet.javaClass)
            }
            if (getMethodWithFacet != null) {
                val model = getMethodWithFacet.invoke(null, facet)
                if (model != null) return model
            }

            val getMethodWithModule = gradleAndroidModelClass.methods.firstOrNull {
                it.name == "get" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(Module::class.java)
            }
            if (getMethodWithModule != null) {
                val model = getMethodWithModule.invoke(null, facet.module)
                if (model != null) return model
            }
        } catch (_: Throwable) {
            // ClassNotFoundException or method invocation issue
        }

        // 3. Try AndroidModel.get(facet) or AndroidModel.get(module)
        try {
            val androidModelClass = Class.forName("com.android.tools.idea.model.AndroidModel")
            val getMethodWithFacet = androidModelClass.methods.firstOrNull {
                it.name == "get" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(facet.javaClass)
            }
            if (getMethodWithFacet != null) {
                val model = getMethodWithFacet.invoke(null, facet)
                if (model != null) return model
            }

            val getMethodWithModule = androidModelClass.methods.firstOrNull {
                it.name == "get" && it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(Module::class.java)
            }
            if (getMethodWithModule != null) {
                val model = getMethodWithModule.invoke(null, facet.module)
                if (model != null) return model
            }
        } catch (_: Throwable) {
            // ClassNotFoundException or method invocation issue
        }

        return null
    }

    private fun getVariantNamesFromModel(model: Any): List<String> {
        val queryMethodNames = listOf(
            "getFilteredVariantNames",
            "getAllVariantNames",
            "getVariantNames",
            "filteredVariantNames",
            "allVariantNames",
            "variantNames",
        )

        for (methodName in queryMethodNames) {
            try {
                val method = model.javaClass.methods.firstOrNull {
                    it.name == methodName && it.parameterCount == 0
                }
                if (method != null) {
                    val result = method.invoke(model)
                    if (result != null) {
                        val names = extractStringList(result)
                        if (names.isNotEmpty()) {
                            return names
                        }
                    }
                }
            } catch (t: Throwable) {
                log.error("Failed to invoke '$methodName' on model ${model.javaClass.name}", t)
            }
        }

        // Fallback: try getVariants() or variants property
        try {
            val variantsMethod = model.javaClass.methods.firstOrNull {
                (it.name == "getVariants" || it.name == "variants") && it.parameterCount == 0
            }
            if (variantsMethod != null) {
                val result = variantsMethod.invoke(model)
                if (result != null) {
                    val names = extractVariantNamesFromObjects(result)
                    if (names.isNotEmpty()) {
                        return names
                    }
                }
            }
        } catch (t: Throwable) {
            log.error("Failed to extract variants from model via getVariants", t)
        }

        return emptyList()
    }

    private fun extractStringList(result: Any): List<String> {
        return when (result) {
            is Collection<*> -> result.mapNotNull { it?.toString() }
            is Array<*> -> result.mapNotNull { it?.toString() }
            is Iterable<*> -> result.mapNotNull { it?.toString() }
            is Sequence<*> -> result.mapNotNull { it?.toString() }.toList()
            is String -> listOf(result)
            else -> emptyList()
        }
    }

    private fun extractVariantNamesFromObjects(result: Any): List<String> {
        val items = when (result) {
            is Collection<*> -> result
            is Array<*> -> result.toList()
            is Iterable<*> -> result.toList()
            is Sequence<*> -> result.toList()
            else -> listOf(result)
        }

        return items.filterNotNull().mapNotNull { item ->
            if (item is String) {
                item
            } else {
                try {
                    val getNameMethod = item.javaClass.methods.firstOrNull {
                        it.name == "getName" && it.parameterCount == 0
                    }
                    getNameMethod?.invoke(item)?.toString() ?: item.toString()
                } catch (_: Throwable) {
                    item.toString()
                }
            }
        }
    }
}
