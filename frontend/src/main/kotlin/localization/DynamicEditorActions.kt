package cg.creamgod45.localization.ui

import cg.creamgod45.CoroutineScopeHolder
import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.DynamicMarkerConversionRequestDto
import cg.creamgod45.localization.DynamicSourceGroupDto
import cg.creamgod45.localization.DynamicMarkerMatch
import cg.creamgod45.localization.DynamicMarkerSyntax
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import kotlinx.coroutines.launch

class SearchSelectedTranslationGroup : DefaultActionGroup(), DumbAware {
    override fun update(event: AnActionEvent) {
        event.presentation.text = message("action.editor.search.group")
        event.presentation.isEnabledAndVisible = selectedContext(event) != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

class SearchSelectedTranslationExactAction : SelectedTextAction() {
    override val titleKey = "action.editor.search.exact"
    override fun perform(context: EditorSelectionContext) =
        openToolWindow(context) { LocalizationUiCommand.SearchTranslations(context.text, true) }
}

class SearchSelectedTranslationFuzzyAction : SelectedTextAction() {
    override val titleKey = "action.editor.search.fuzzy"
    override fun perform(context: EditorSelectionContext) =
        openToolWindow(context) { LocalizationUiCommand.SearchTranslations(context.text, false) }
}

class DynamicSourceActionGroup : DefaultActionGroup(), DumbAware {
    override fun update(event: AnActionEvent) {
        event.presentation.text = message("action.editor.dynamic.group")
        event.presentation.isEnabledAndVisible = dynamicSourceContext(event) != null || markerContext(event) != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

class CreateDynamicMarkerAction : EditorPositionAction() {
    override val titleKey = "action.editor.dynamic.marker"

    override fun perform(context: EditorSelectionContext) {
        val draft = context.toDraft()
        val dialog =
            DynamicMarkerDialog(
                context.project,
                context.filePath,
                draft.line,
                draft.column,
                LocalizationActionContext.getInstance(context.project).snapshot().entries,
                listOf(DynamicSourceGroupDto("enum", splitSelectedKeys(draft.selectedText))),
            )
        if (!dialog.showAndGet()) return
        val marker = markerText(context.filePath, dialog.groups())
        val lineStart = context.editor.document.getLineStartOffset((draft.line - 1).coerceAtLeast(0))
        WriteCommandAction.runWriteCommandAction(context.project, message(titleKey), null, Runnable {
            context.editor.document.insertString(lineStart, marker)
        })
    }
}

class EditDynamicMarkerAction : DumbAwareAction() {
    override fun update(event: AnActionEvent) {
        event.presentation.text = message("action.editor.dynamic.edit")
        event.presentation.isEnabledAndVisible = markerContext(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val context = markerContext(event) ?: return
        val position = context.editor.offsetToLogicalPosition(context.marker.startOffset)
        val state = LocalizationActionContext.getInstance(context.project).snapshot()
        val scheme = state.schemes.firstOrNull { it.id == state.activeSchemeId }
        val entries = state.entries
        val dialog =
            DynamicMarkerDialog(
                context.project,
                context.filePath,
                position.line + 1,
                position.column + 1,
                entries,
                context.marker.groups,
                allowConversion = scheme != null,
            )
        if (!dialog.showAndGet()) return
        if (dialog.isConversionRequested()) {
            val activeScheme = scheme ?: return
            FileDocumentManager.getInstance().saveDocument(context.editor.document)
            val request =
                DynamicMarkerConversionRequestDto(
                    filePath = context.filePath,
                    markerStartOffset = context.marker.startOffset,
                    markerEndOffsetExclusive = context.marker.endOffsetExclusive,
                    expectedMarker = context.editor.document.getText(
                        com.intellij.openapi.util.TextRange(context.marker.startOffset, context.marker.endOffsetExclusive),
                    ),
                    line = position.line + 1,
                    column = position.column + 1,
                    groups = dialog.groups(),
                )
            CoroutineScopeHolder.getInstance(context.project).getPluginScope().launch {
                runCatching {
                    val ruleId = LocalizationFrontendRepository(context.project).convertDynamicMarkerToRule(activeScheme.id, request)
                    openToolWindow(context.project, LocalizationUiCommand.FocusDynamicSource(ruleId))
                    notifyConversion(context.project, message("dynamic.convert.to.rule.success"))
                }.onFailure { error ->
                    notifyConversion(context.project, error.message ?: message("error.action.failed"), true)
                }
            }
            return
        }
        WriteCommandAction.runWriteCommandAction(context.project, message("action.editor.dynamic.edit"), null, Runnable {
            context.editor.document.replaceString(
                context.marker.startOffset,
                context.marker.endOffsetExclusive,
                DynamicMarkerSyntax.render(dialog.groups()),
            )
        })
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

class AddNonInvasiveDynamicSourceAction : EditorPositionAction() {
    override val titleKey = "action.editor.dynamic.noninvasive"
    override fun perform(context: EditorSelectionContext) = openToolWindow(context) { context.toDraft() }
}

abstract class EditorPositionAction : DumbAwareAction() {
    protected abstract val titleKey: String
    protected abstract fun perform(context: EditorSelectionContext)

    override fun update(event: AnActionEvent) {
        event.presentation.text = message(titleKey)
        event.presentation.isEnabled = dynamicSourceContext(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        dynamicSourceContext(event)?.let(::perform)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

abstract class SelectedTextAction : DumbAwareAction() {
    protected abstract val titleKey: String
    protected abstract fun perform(context: EditorSelectionContext)

    override fun update(event: AnActionEvent) {
        event.presentation.text = message(titleKey)
        event.presentation.isEnabled = selectedContext(event) != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        selectedContext(event)?.let(::perform)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

data class EditorSelectionContext(
    val project: com.intellij.openapi.project.Project,
    val editor: Editor,
    val filePath: String,
    val text: String,
) {
    internal fun toDraft(): LocalizationUiCommand.AddDynamicSource {
        val position = editor.offsetToLogicalPosition(editorContextOffset(editor))
        return LocalizationUiCommand.AddDynamicSource(filePath, position.line + 1, position.column + 1, text)
    }
}

internal fun editorContextOffset(editor: Editor): Int =
    editorContextOffset(
        editor.selectionModel.hasSelection(),
        editor.selectionModel.selectionStart,
        editor.caretModel.offset,
    )

internal fun editorContextOffset(
    hasSelection: Boolean,
    selectionStart: Int,
    caretOffset: Int,
): Int = if (hasSelection) selectionStart else caretOffset

private fun selectedContext(event: AnActionEvent): EditorSelectionContext? {
    val project = event.project ?: return null
    if (!LocalizationActionContext.getInstance(project).hasActiveScheme()) return null
    val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
    val text = editor.selectionModel.selectedText?.takeIf(String::isNotBlank) ?: return null
    val filePath = event.getData(CommonDataKeys.VIRTUAL_FILE)?.path?.takeIf(String::isNotBlank) ?: return null
    return EditorSelectionContext(project, editor, filePath, text)
}

private fun dynamicSourceContext(event: AnActionEvent): EditorSelectionContext? {
    val project = event.project ?: return null
    if (!LocalizationActionContext.getInstance(project).hasActiveScheme()) return null
    val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
    val filePath = event.getData(CommonDataKeys.VIRTUAL_FILE)?.path?.takeIf(String::isNotBlank) ?: return null
    return EditorSelectionContext(project, editor, filePath, editor.selectionModel.selectedText.orEmpty().trim())
}

private data class MarkerEditorContext(
    val project: com.intellij.openapi.project.Project,
    val editor: Editor,
    val filePath: String,
    val marker: DynamicMarkerMatch,
)

private fun markerContext(event: AnActionEvent): MarkerEditorContext? {
    val project = event.project ?: return null
    val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
    val filePath = event.getData(CommonDataKeys.VIRTUAL_FILE)?.path ?: return null
    val selection = editor.selectionModel
    val start = if (selection.hasSelection()) selection.selectionStart else editor.caretModel.offset
    val end = if (selection.hasSelection()) selection.selectionEnd else start
    val marker = DynamicMarkerSyntax.findAt(editor.document.text, start, end) ?: return null
    return MarkerEditorContext(project, editor, filePath, marker)
}

private fun openToolWindow(
    context: EditorSelectionContext,
    command: () -> LocalizationUiCommand,
) {
    ToolWindowManager.getInstance(context.project).getToolWindow("Language Manager")?.activate {
        LocalizationActionContext.getInstance(context.project).submit(command())
    }
}

private fun openToolWindow(
    project: com.intellij.openapi.project.Project,
    command: LocalizationUiCommand,
) {
    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
        ToolWindowManager.getInstance(project).getToolWindow("Language Manager")?.activate {
            LocalizationActionContext.getInstance(project).submit(command)
        }
    }
}

private fun notifyConversion(
    project: com.intellij.openapi.project.Project,
    text: String,
    error: Boolean = false,
) {
    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
        com.intellij.notification.NotificationGroupManager.getInstance()
            .getNotificationGroup("LanguageManager")
            .createNotification(
                text.take(500),
                if (error) com.intellij.notification.NotificationType.ERROR else com.intellij.notification.NotificationType.INFORMATION,
            ).notify(project)
    }
}

private class DynamicMarkerDialog(
    project: com.intellij.openapi.project.Project,
    private val filePath: String,
    private val line: Int,
    private val column: Int,
    entries: List<cg.creamgod45.localization.LanguageEntryDto>,
    initialGroups: List<DynamicSourceGroupDto>,
    allowConversion: Boolean = false,
) : DialogWrapper(project) {
    private var conversionRequested = false
    private val editor = DynamicGroupsEditor(
        project,
        entries,
        initialGroups,
        if (allowConversion) message("dynamic.convert.to.rule") else null,
        if (allowConversion) ::requestConversion else null,
    )

    init {
        title = message("dialog.dynamic.marker.title")
        init()
    }

    fun groups() = editor.groups()

    fun isConversionRequested() = conversionRequested

    private fun requestConversion() {
        val validation = editor.validationInfo()
        if (validation != null) {
            setErrorText(validation.message, validation.component)
            return
        }
        conversionRequested = true
        close(OK_EXIT_CODE)
    }

    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent(message("dynamic.file"), JBLabel(filePath))
            .addLabeledComponent(message("dynamic.line.column"), JBLabel("$line:$column"))
            .addComponent(
                JBScrollPane(editor).apply {
                    preferredSize = java.awt.Dimension(com.intellij.util.ui.JBUI.scale(720), com.intellij.util.ui.JBUI.scale(420))
                    verticalScrollBar.unitIncrement = com.intellij.util.ui.JBUI.scale(18)
                    horizontalScrollBar.unitIncrement = com.intellij.util.ui.JBUI.scale(18)
                },
            )
            .panel

    override fun doValidate(): ValidationInfo? = editor.validationInfo()
}

internal fun splitSelectedKeys(text: String): List<String> =
    text.split(Regex("[,\\s]+"))
        .map { it.trim().trim('\'', '"') }
        .filter { it.isNotBlank() }
        .distinct()
        .ifEmpty { listOf(text.trim()) }

private fun markerText(
    filePath: String,
    groups: List<DynamicSourceGroupDto>,
): String {
    val marker = DynamicMarkerSyntax.render(groups)
    val lower = filePath.lowercase()
    return when {
        lower.endsWith(".blade.php") -> "{{-- $marker --}}\n"
        lower.endsWith(".html") || lower.endsWith(".xml") -> "<!-- $marker -->\n"
        lower.endsWith(".py") || lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".sh") -> "# $marker\n"
        else -> "// $marker\n"
    }
}
