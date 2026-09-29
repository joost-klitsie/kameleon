package dev.klitsie.kameleon.idea

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.components.service
import javax.swing.JComponent

class KameleonVariantAction : ComboBoxAction() {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }

        val state = project.service<KameleonStateService>()
        e.presentation.isEnabledAndVisible = true
        val displayText = if (state.schema.dimensions.size > 1) {
            val activeList = state.schema.dimensions.keys.mapNotNull { dim ->
                state.activeFlavors[dim] ?: state.schema.dimensions[dim]?.firstOrNull()
            }
            if (activeList.isNotEmpty()) {
                "Variant: ${KameleonVariantMapper.buildVariantName(activeList)}"
            } else {
                "Variant: ${state.currentFlavor.ifEmpty { "default" }}"
            }
        } else {
            "Variant: ${state.currentFlavor.ifEmpty { "default" }}"
        }
        e.presentation.text = displayText
    }

    override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup {
        val group = DefaultActionGroup()
        val project = CommonDataKeys.PROJECT.getData(dataContext) ?: return group
        val state = project.service<KameleonStateService>()
        val schema = state.schema

        if (schema.dimensions.size <= 1) {
            // Single dimension (or "default") -> flat list directly without submenu nesting
            val flavors = if (schema.dimensions.size == 1) {
                val (dimName, flavorList) = schema.dimensions.entries.first()
                flavorList.sorted().map { flavor -> dimName to flavor }
            } else {
                val list = if (state.availableFlavors.isNotEmpty()) state.availableFlavors.sorted() else listOf("default")
                list.map { "default" to it }
            }

            for ((dimName, flavor) in flavors) {
                group.add(FlavorToggleAction(dimName, flavor, state))
            }
        } else {
            // Multi-dimension -> submenus for each dimension
            for ((dimName, flavors) in schema.dimensions) {
                val formattedDimName =
                    dimName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                val subGroup = DefaultActionGroup("Dimension: $formattedDimName", true)
                for (flavor in flavors.sorted()) {
                    subGroup.add(FlavorToggleAction(dimName, flavor, state))
                }
                group.add(subGroup)
            }
        }

        return group
    }

    private class FlavorToggleAction(
        private val dimension: String,
        private val flavor: String,
        private val state: KameleonStateService,
    ) : ToggleAction(flavor) {

        override fun getActionUpdateThread(): ActionUpdateThread {
            return ActionUpdateThread.BGT
        }

        override fun isSelected(e: AnActionEvent): Boolean {
            val active = state.activeFlavors[dimension]
            return if (active != null) {
                active == flavor
            } else {
                state.currentFlavor == flavor
            }
        }

        override fun setSelected(e: AnActionEvent, selected: Boolean) {
            if (selected) {
                val currentActive = state.activeFlavors[dimension] ?: state.currentFlavor
                if (currentActive == flavor) {
                    return
                }
                state.onDimensionFlavorSelected(dimension, flavor)
            }
        }
    }

}
