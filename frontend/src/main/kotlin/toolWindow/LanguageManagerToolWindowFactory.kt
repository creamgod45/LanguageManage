package cg.creamgod45.toolWindow

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.ui.LocalizationManagerPanel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentFactory

class LanguageManagerToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        replaceContent(project, toolWindow)
    }

    companion object {
        private const val TOOL_WINDOW_ID = "Language Manager"

        fun refreshOpenToolWindows() {
            ProjectManager.getInstance().openProjects
                .filterNot(Project::isDisposed)
                .forEach { project ->
                    ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.let { replaceContent(project, it) }
                }
        }

        private fun replaceContent(project: Project, toolWindow: ToolWindow) {
            toolWindow.contentManager.removeAllContents(true)
        val panel = LocalizationManagerPanel(project)
        toolWindow.title = message("app.title")
        toolWindow.stripeTitle = message("app.title")
        val content = ContentFactory.getInstance().createContent(panel, message("toolwindow.content.title"), false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
        }
    }
}
