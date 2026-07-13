package cg.creamgod45.toolWindow

import cg.creamgod45.localization.ui.LocalizationManagerPanel
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class LanguageManagerToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = LocalizationManagerPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "語言方案", false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}
