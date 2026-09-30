package cg.creamgod45.localization.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import javax.swing.JComponent

/**
 * Native JetBrains action popup with checkbox toggles that stays open after each click, so several options can be
 * switched in one go (used by Visible Columns and by the analysis naming-format exclusions).
 */
internal object KeepOpenTogglePopup {
    class Toggle(
        val text: String,
        val isSelected: () -> Boolean,
        val setSelected: (Boolean) -> Unit,
    )

    class Command(
        val text: String,
        val perform: () -> Unit,
    )

    /** Toggles first, then a separator and the commands (for example "Show All"); every item keeps the popup open. */
    fun group(
        toggles: List<Toggle>,
        commands: List<Command> = emptyList(),
    ): DefaultActionGroup =
        DefaultActionGroup().apply {
            toggles.forEach { toggle ->
                add(
                    object : DumbAwareToggleAction(toggle.text) {
                        init {
                            templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Always
                        }

                        override fun getActionUpdateThread() = ActionUpdateThread.EDT

                        override fun isSelected(event: AnActionEvent) = toggle.isSelected()

                        override fun setSelected(
                            event: AnActionEvent,
                            state: Boolean,
                        ) = toggle.setSelected(state)
                    },
                )
            }
            if (commands.isNotEmpty()) addSeparator()
            commands.forEach { command ->
                add(
                    object : DumbAwareAction(command.text) {
                        init {
                            templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Always
                        }

                        override fun getActionUpdateThread() = ActionUpdateThread.EDT

                        override fun actionPerformed(event: AnActionEvent) = command.perform()
                    },
                )
            }
        }

    fun show(
        anchor: JComponent,
        title: String?,
        toggles: List<Toggle>,
        commands: List<Command> = emptyList(),
    ) {
        JBPopupFactory
            .getInstance()
            .createActionGroupPopup(
                title,
                group(toggles, commands),
                DataManager.getInstance().getDataContext(anchor),
                JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
                true,
            ).showUnderneathOf(anchor)
    }
}
