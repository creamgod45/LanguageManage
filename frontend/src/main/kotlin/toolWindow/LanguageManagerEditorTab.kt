package cg.creamgod45.toolWindow

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.LanguageManagerIcons
import cg.creamgod45.localization.ui.HardcodedAnalysisPanel
import cg.creamgod45.localization.ui.LocalizationManagerPanel
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileTypes.ex.FakeFileType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.testFramework.LightVirtualFile
import java.beans.PropertyChangeListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel

/** Tool window views that can be moved into an editor tab. */
internal enum class LanguageManagerView(
    val titleKey: String,
) {
    TRANSLATIONS("toolwindow.content.title"),
    ANALYSIS("toolwindow.content.analysis"),
}

internal object LanguageManagerEditorFileType : FakeFileType() {
    override fun getName() = "LanguageManagerEditor"

    override fun getDescription() = message("app.title")

    override fun getIcon(): Icon = LanguageManagerIcons.ToolWindow

    override fun isMyFileType(file: VirtualFile) = file is LanguageManagerVirtualFile
}

/** In-memory placeholder file; the editor content is a live panel, not file text. */
internal class LanguageManagerVirtualFile(
    val view: LanguageManagerView,
) : LightVirtualFile(editorTabTitle(view), LanguageManagerEditorFileType, "") {
    init {
        isWritable = false
    }
}

internal fun editorTabTitle(view: LanguageManagerView) = message("editor.tab.title", message("app.title"), message(view.titleKey))

class LanguageManagerFileEditorProvider :
    FileEditorProvider,
    DumbAware {
    override fun accept(
        project: Project,
        file: VirtualFile,
    ) = file is LanguageManagerVirtualFile

    override fun acceptRequiresReadAction() = false

    override fun createEditor(
        project: Project,
        file: VirtualFile,
    ): FileEditor = LanguageManagerFileEditor(project, file as LanguageManagerVirtualFile)

    override fun getEditorTypeId() = EDITOR_TYPE_ID

    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    private companion object {
        const val EDITOR_TYPE_ID = "cg.creamgod45.localization.LanguageManagerEditor"
    }
}

private class LanguageManagerFileEditor(
    project: Project,
    private val file: LanguageManagerVirtualFile,
) : UserDataHolderBase(),
    FileEditor {
    private val analysisPanel = if (file.view == LanguageManagerView.ANALYSIS) HardcodedAnalysisPanel(project) else null
    private val panel: JPanel = analysisPanel ?: LocalizationManagerPanel(project)

    init {
        Disposer.register(this, panel as Disposable)
    }

    override fun getComponent(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = panel

    override fun getName() = message(file.view.titleKey)

    override fun getFile(): VirtualFile = file

    override fun setState(state: FileEditorState) = Unit

    override fun isModified() = false

    override fun isValid() = true

    override fun selectNotify() {
        analysisPanel?.onSelected()
    }

    override fun deselectNotify() {
        analysisPanel?.onDeselected()
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit

    override fun dispose() = Unit
}

/** Opens the given view as an editor tab (reusing an open one) and hides the tool window. */
internal class OpenInEditorTabAction(
    private val toolWindow: ToolWindow,
    private val viewOf: () -> LanguageManagerView,
) : DumbAwareAction(message("action.open.in.editor.tab"), null, AllIcons.Actions.MoveToWindow) {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        openInEditorTab(project, viewOf())
        toolWindow.hide()
    }
}

internal fun openInEditorTab(
    project: Project,
    view: LanguageManagerView,
) {
    val manager = FileEditorManager.getInstance(project)
    val file =
        manager.openFiles.firstOrNull { it is LanguageManagerVirtualFile && it.view == view }
            ?: LanguageManagerVirtualFile(view)
    manager.openFile(file, true)
}
