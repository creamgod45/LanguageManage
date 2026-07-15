package cg.creamgod45.toolWindow

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.ui.LocalizationManagerPanel
import cg.creamgod45.settings.LanguageManagerSettingsConfigurable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class LanguageManagerToolWindowFactory :
    ToolWindowFactory,
    DumbAware {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        replaceContent(project, toolWindow)
    }

    companion object {
        private const val TOOL_WINDOW_ID = "Language Manager"
        internal const val PLUGIN_SETTINGS_ID = "cg.creamgod45.localization.LanguageManagerSettings"
        internal val PLUGIN_SETTINGS_CLASS = LanguageManagerSettingsConfigurable::class.java

        fun refreshOpenToolWindows() {
            ProjectManager
                .getInstance()
                .openProjects
                .filterNot(Project::isDisposed)
                .forEach { project ->
                    ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.let { replaceContent(project, it) }
                }
        }

        private fun replaceContent(
            project: Project,
            toolWindow: ToolWindow,
        ) {
            toolWindow.contentManager.removeAllContents(true)
            val panel = LocalizationManagerPanel(project)
            toolWindow.title = message("app.title")
            toolWindow.stripeTitle = message("app.title")
            toolWindow.setAdditionalGearActions(settingsActions(project))
            val content = ContentFactory.getInstance().createContent(panel, message("toolwindow.content.title"), false)
            content.setDisposer(panel)
            toolWindow.contentManager.addContent(content)
        }

        private fun settingsActions(project: Project) =
            DefaultActionGroup().apply {
                add(openSettingsAction(project, message("action.settings.plugin")))
            }

        private fun openSettingsAction(
            project: Project,
            title: String,
        ) = object : DumbAwareAction(title) {
            override fun actionPerformed(event: AnActionEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, PLUGIN_SETTINGS_CLASS)
            }
        }
    }
}
