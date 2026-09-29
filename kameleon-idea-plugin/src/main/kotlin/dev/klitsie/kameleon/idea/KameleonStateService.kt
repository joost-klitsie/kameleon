package dev.klitsie.kameleon.idea

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic
import dev.klitsie.kameleon.tooling.KameleonToolingModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.Serializable
import java.util.concurrent.ConcurrentHashMap

data class ProjectFlavorSchema(
    val dimensions: Map<String, List<String>> = emptyMap(), // dimension -> list of flavors
    val moduleDimensions: Map<String, Set<String>> = emptyMap(), // moduleProjectPath -> set of dimensions it defines
) : Serializable

@Service(Service.Level.PROJECT)
@State(
    name = "KameleonStateService",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class KameleonStateService(
    private val project: Project? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) :
    PersistentStateComponent<KameleonStateService.State> {

    private val log = Logger.getInstance(KameleonStateService::class.java)

    data class State(
        var currentFlavor: String = "default",
        var availableFlavors: List<String> = emptyList(),
        var activeFlavors: MutableMap<String, String> = mutableMapOf(),
        var schemaDimensions: MutableMap<String, List<String>> = mutableMapOf(),
        var schemaModuleDimensions: MutableMap<String, Set<String>> = mutableMapOf(),
    )

    private var myState = State()

    var schema: ProjectFlavorSchema = ProjectFlavorSchema()
        private set

    val activeFlavors: MutableMap<String, String>
        get() = myState.activeFlavors

    fun getActiveSelections(): Map<String?, String> {
        if (schema.dimensions.isNotEmpty()) {
            val selections = mutableMapOf<String?, String>()
            for ((dim, flavors) in schema.dimensions) {
                val active = activeFlavors[dim] ?: flavors.firstOrNull() ?: "default"
                selections[dim] = active
            }
            return selections
        }
        if (activeFlavors.isNotEmpty()) {
            return activeFlavors.toMap()
        }
        return mapOf("default" to currentFlavor.ifBlank { "default" })
    }

    private val toolingModels = java.util.Collections.synchronizedMap(LinkedHashMap<String, KameleonToolingModel>())

    override fun getState(): State {
        myState.schemaDimensions = LinkedHashMap(schema.dimensions)
        myState.schemaModuleDimensions = LinkedHashMap(schema.moduleDimensions)
        return myState
    }

    override fun loadState(state: State) {
        myState = state
        schema = ProjectFlavorSchema(
            dimensions = LinkedHashMap(state.schemaDimensions),
            moduleDimensions = LinkedHashMap(state.schemaModuleDimensions),
        )
    }

    var currentFlavor: String
        get() = myState.currentFlavor
        set(value) {
            myState.currentFlavor = value
        }

    var availableFlavors: List<String>
        get() = myState.availableFlavors
        set(value) {
            myState.availableFlavors = value
        }

    var selectedVariant: String
        get() = currentFlavor
        set(value) {
            currentFlavor = value
        }

    var availableVariants: List<String>
        get() = availableFlavors
        set(value) {
            availableFlavors = value
        }

    fun registerToolingModel(model: KameleonToolingModel) {
        toolingModels[model.projectPath] = model
        rebuildSchema()
    }

    fun registerToolingModels(models: Collection<KameleonToolingModel>) {
        for (model in models) {
            toolingModels[model.projectPath] = model
        }
        rebuildSchema()
    }

    private fun rebuildSchema() {
        val allDims = linkedMapOf<String, MutableList<String>>()
        val modDims = linkedMapOf<String, Set<String>>()

        val models = synchronized(toolingModels) { toolingModels.values.toList() }
        for (model in models) {
            modDims[model.projectPath] = model.dimensions.keys
            for ((dim, flavors) in model.dimensions) {
                val list = allDims.computeIfAbsent(dim) { mutableListOf() }
                for (f in flavors) {
                    if (!list.contains(f)) {
                        list.add(f)
                    }
                }
            }
        }

        schema = ProjectFlavorSchema(allDims, modDims)
    }

    fun onDimensionFlavorSelected(dimension: String, flavor: String) {
        val proj = project ?: return
        if (KameleonStartupManager.isSyncInProgress(proj)) {
            notifySyncInProgress(proj)
            return
        }
        val currentActive = activeFlavors[dimension]
        val hasStateChanged = currentActive != flavor
        if (!hasStateChanged) {
            // Idempotency: flavor is already active for this dimension, return immediately without touching disk, AGP, or Gradle sync
            return
        }

        activeFlavors[dimension] = flavor

        if (schema.dimensions.size > 1) {
            val composite = schema.dimensions.keys.mapNotNull { activeFlavors[it] }
            currentFlavor = KameleonVariantMapper.buildVariantName(composite)
        } else {
            currentFlavor = flavor
        }

        // 1. Write to gradle.properties
        try {
            GradlePropertiesSync(proj).updateFlavorOnDisk(dimension, flavor)
        } catch (t: Throwable) {
            log.error("Failed to update gradle.properties for dimension '$dimension' with flavor '$flavor'", t)
        }

        // 2. Dispatch AGP variant updates to relevant Android modules
        coroutineScope.launch {
            val updatedAndroidTargets = try {
                val agpBridge = proj.service<dev.klitsie.kameleon.idea.android.KameleonAgpBridge>()
                val updated = agpBridge.onDimensionChanged(dimension, flavor, schema, activeFlavors)
                updated
            } catch (_: NoClassDefFoundError) {
                // Safe fallback when running without Android plugin
                0
            } catch (t: Throwable) {
                log.error("Failed to dispatch AGP variant update", t)
                0
            }

            // 3. Conditional Sync Dispatch Logic
            if (updatedAndroidTargets > 0) {
                // AGP's BuildVariantUpdater handles the IDE model update/sync internally
                log.info("Updated $updatedAndroidTargets Android target(s). Relying on AGP variant update.")
            } else {
                // No Android target consumed the switch (pure KMP, pure Java, or non-Android project).z
                // Trigger a background Gradle refresh so IntelliJ re-indexes updated source roots/properties.
                log.info("No Android targets updated. Triggering background Gradle sync for KMP/Java targets.")
                KameleonSyncManager.getInstance(proj).triggerProjectRefresh()
            }

            notifyListeners()
        }
    }

    fun reloadFlavors(): List<String> {
        val proj = project ?: return availableFlavors

        val rootPath = proj.basePath ?: ""
        val models = consumeDiscoveredModels(rootPath)
        if (models.isNotEmpty()) {
            registerToolingModels(models)
        }

        if (toolingModels.isNotEmpty()) {
            rebuildSchema()
        }

        if (schema.dimensions.isNotEmpty()) {
            val sync = GradlePropertiesSync(proj)
            for ((dim, flavors) in schema.dimensions) {
                val diskVal = sync.readFlavorFromDisk(dim)
                val inMemoryVal = activeFlavors[dim]
                val active = when {
                    diskVal != null && diskVal in flavors -> diskVal
                    inMemoryVal != null && inMemoryVal in flavors -> inMemoryVal
                    else -> flavors.firstOrNull() ?: "default"
                }
                activeFlavors[dim] = active
            }

            if (schema.dimensions.size > 1) {
                val composite = schema.dimensions.keys.mapNotNull { activeFlavors[it] }
                currentFlavor = KameleonVariantMapper.buildVariantName(composite)
            } else {
                currentFlavor = activeFlavors.values.firstOrNull() ?: "default"
            }

            val allVariants = mutableListOf<String>()
            for (model in toolingModels.values) {
                allVariants.addAll(model.availableVariants)
            }
            if (allVariants.isEmpty()) {
                var product = listOf(listOf<String>())
                for (fList in schema.dimensions.values) {
                    product = product.flatMap { existing -> fList.map { existing + it } }
                }
                availableFlavors = product.map { KameleonVariantMapper.buildVariantName(it) }.distinct()
            } else {
                availableFlavors = allVariants.distinct()
            }
        } else {
            val sync = GradlePropertiesSync(proj)
            val diskVal = sync.readFlavorFromDisk("default")
            val discovered = FlavorDiscovery.discoverFlavors(proj)
            if (discovered.isNotEmpty()) {
                availableFlavors = discovered
            }
            val inMemoryVal = activeFlavors["default"] ?: currentFlavor.takeIf { it.isNotBlank() }
            val active = when {
                diskVal != null && (availableFlavors.isEmpty() || diskVal in availableFlavors) -> diskVal
                inMemoryVal != null && (availableFlavors.isEmpty() || inMemoryVal in availableFlavors) -> inMemoryVal
                else -> availableFlavors.firstOrNull() ?: "default"
            }
            currentFlavor = active
            activeFlavors["default"] = active
        }

        notifyListeners()
        return availableFlavors
    }

    fun notifyListeners() {
        val proj = project ?: return
        try {
            proj.messageBus.syncPublisher(TOPIC).onFlavorsUpdated(availableFlavors, currentFlavor)
        } catch (t: Throwable) {
            log.error("Failed to notify flavor listeners", t)
        }
    }

    private fun notifySyncInProgress(project: Project) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Kameleon Notifications")
            .createNotification(
                title = "Variant switch not possible",
                content = "A Gradle sync is currently in progress. Please wait for it to complete.",
                type = NotificationType.WARNING,
            )
            .notify(project)
    }

    fun interface KameleonFlavorListener {
        fun onFlavorsUpdated(availableFlavors: List<String>, currentFlavor: String)
    }

    companion object {
        val TOPIC = Topic.create("Kameleon Flavor Changes", KameleonFlavorListener::class.java)

        private val pendingModels = ConcurrentHashMap<String, MutableMap<String, KameleonToolingModel>>()

        fun registerDiscoveredModel(rootProjectPath: String, model: KameleonToolingModel) {
            val map = pendingModels.computeIfAbsent(rootProjectPath) { ConcurrentHashMap() }
            map[model.projectPath] = model
        }

        fun consumeDiscoveredModels(rootProjectPath: String): List<KameleonToolingModel> {
            return pendingModels.remove(rootProjectPath)?.values?.toList() ?: emptyList()
        }

        fun getInstance(project: Project): KameleonStateService = project.service()
    }
}
