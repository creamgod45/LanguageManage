package cg.creamgod45.toolWindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import cg.creamgod45.CoroutineScopeHolder
import cg.creamgod45.chatApp.ChatAppSample
import cg.creamgod45.chatApp.viewmodel.ChatViewModel
import cg.creamgod45.chatApp.viewmodel.FrontendChatRepositoryModel

class ModularPluginToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun shouldBeAvailable(project: Project) = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        chatApp(project, toolWindow)
    }

    private fun chatApp(project: Project, toolWindow: ToolWindow) {
        val viewModel = ChatViewModel(
            CoroutineScopeHolder.getInstance(project).createScope(ChatViewModel::class.java.simpleName),
            FrontendChatRepositoryModel.getInstance(project)
        )
        Disposer.register(toolWindow.disposable, viewModel)

        val chatPanel = ChatAppSample(viewModel, project)
        val content = ContentFactory.getInstance().createContent(chatPanel, "Chat App", false)
        toolWindow.contentManager.addContent(content)
    }
}
