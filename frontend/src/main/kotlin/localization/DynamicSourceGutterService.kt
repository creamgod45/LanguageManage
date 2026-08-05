package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.LanguageManagerIcons
import cg.creamgod45.localization.DynamicMarkerSyntax
import cg.creamgod45.localization.LocalizationStateDto
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path
import java.util.IdentityHashMap
import javax.swing.Icon
import javax.swing.Timer

internal class DynamicSourceGutterStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(DynamicSourceGutterService::class.java)
    }
}

@Service(Service.Level.PROJECT)
internal class DynamicSourceGutterService(
    private val project: Project,
    coroutineScope: CoroutineScope,
) : Disposable {
    private var state = LocalizationStateDto()
    private val installed = IdentityHashMap<Editor, List<RangeHighlighter>>()
    private val installedInlays = IdentityHashMap<Editor, List<Inlay<*>>>()
    private val documentRefreshTimer = Timer(250) { refreshAll() }.apply { isRepeats = false }

    init {
        project.messageBus.connect(this).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) = refreshAll()
                override fun selectionChanged(event: FileEditorManagerEvent) = refreshAll()
                override fun fileClosed(source: FileEditorManager, file: com.intellij.openapi.vfs.VirtualFile) = clearClosedEditors()
            },
        )
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        if (!project.isDisposed) documentRefreshTimer.restart()
                    }
                }
            },
            this,
        )
        coroutineScope.launch {
            LocalizationFrontendRepository(project).state.collectLatest { latest ->
                state = latest
                withContext(Dispatchers.EDT) { refreshAll() }
            }
        }
    }

    private fun refreshAll() = editors().forEach(::refresh)

    private fun editors(): List<Editor> =
        FileEditorManager.getInstance(project).allEditors.filterIsInstance<TextEditor>().map { it.editor }

    private fun clearClosedEditors() {
        val open = editors().toSet()
        installed.keys.filterNot(open::contains).toList().forEach { editor ->
            installed.remove(editor)?.forEach(editor.markupModel::removeHighlighter)
            installedInlays.remove(editor)?.forEach(Inlay<*>::dispose)
        }
    }

    private fun refresh(editor: Editor) {
        installed.remove(editor)?.forEach(editor.markupModel::removeHighlighter)
        installedInlays.remove(editor)?.forEach(Inlay<*>::dispose)
        val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return
        val highlighters = mutableListOf<RangeHighlighter>()
        val inlays = mutableListOf<Inlay<*>>()
        DynamicMarkerSyntax.findAll(editor.document.text).forEach { marker ->
            val line = editor.document.getLineNumber(marker.startOffset)
            highlighters += addGutter(editor, line, true, null)
        }
        val scheme = state.schemes.firstOrNull { it.id == state.activeSchemeId }
        scheme?.dynamicSourceRules.orEmpty()
            .filter { samePath(it.filePath, file.path) }
            .forEach { rule ->
                val validOffset = dynamicSourceOffset(editor.document.charsSequence, rule.line, rule.column)
                val line = (rule.line - 1).coerceIn(0, (editor.document.lineCount - 1).coerceAtLeast(0))
                highlighters += addGutter(editor, line, false, rule.id, validOffset != null)
                if (validOffset != null) addRuleInlay(editor, validOffset, rule)?.let(inlays::add)
            }
        installed[editor] = highlighters
        installedInlays[editor] = inlays
    }

    private fun addRuleInlay(
        editor: Editor,
        offset: Int,
        rule: cg.creamgod45.localization.DynamicSourceRuleDto,
    ): Inlay<*>? {
        val factory = PresentationFactory(editor)
        val label = message("dynamic.inlay.label", dynamicSourceKeyCount(rule))
        val tooltip =
            buildString {
                append(message("dynamic.inlay.tooltip.title"))
                append("\n")
                append(message("dynamic.inlay.tooltip.status"))
                append("\n")
                append(message("dynamic.inlay.tooltip.method", rule.method))
                append("\n")
                append(message("dynamic.inlay.tooltip.location", rule.filePath, rule.line, rule.column))
                val details = dynamicSourceTooltipDetails(rule)
                if (details.isNotBlank()) {
                    append("\n")
                    append(message("dynamic.inlay.tooltip.groups", details))
                }
                append("\n")
                append(message("dynamic.inlay.tooltip.action"))
            }
        val clickable =
            factory.reference(factory.withTooltip(tooltip, factory.smallText(label))) {
                focusDynamicRule(rule.id)
            }
        val presentation = factory.inset(clickable, editor.offsetToXY(offset).x.coerceAtLeast(0), 0, 0, 0)
        return editor.inlayModel.addBlockElement(
            offset,
            true,
            true,
            0,
            PresentationRenderer(presentation),
        )
    }

    private fun addGutter(
        editor: Editor,
        line: Int,
        invasive: Boolean,
        ruleId: String?,
        validPosition: Boolean = true,
    ): RangeHighlighter =
        editor.markupModel.addLineHighlighter(line, HighlighterLayer.ADDITIONAL_SYNTAX, null).also { highlighter ->
            highlighter.gutterIconRenderer =
                DynamicSourceGutterRenderer(
                    invasive,
                    ruleId,
                    validPosition,
                    navigate = {
                        if (invasive) {
                            FileDocumentManager.getInstance().getFile(editor.document)?.let { file ->
                                OpenFileDescriptor(project, file, line, 0).navigate(true)
                            }
                        } else if (ruleId != null) {
                            focusDynamicRule(ruleId)
                        }
                    },
                )
        }

    private fun focusDynamicRule(ruleId: String) {
        ToolWindowManager.getInstance(project).getToolWindow("Language Manager")?.activate {
            LocalizationActionContext.getInstance(project).submit(LocalizationUiCommand.FocusDynamicSource(ruleId))
        }
    }

    private fun samePath(
        first: String,
        second: String,
    ): Boolean =
        runCatching {
            Path.of(first).toAbsolutePath().normalize() == Path.of(second).toAbsolutePath().normalize()
        }.getOrDefault(first.equals(second, ignoreCase = true))

    override fun dispose() {
        documentRefreshTimer.stop()
        installed.forEach { (editor, highlighters) -> highlighters.forEach(editor.markupModel::removeHighlighter) }
        installed.clear()
        installedInlays.forEach { (_, inlays) -> inlays.forEach(Inlay<*>::dispose) }
        installedInlays.clear()
    }
}

private class DynamicSourceGutterRenderer(
    private val invasive: Boolean,
    private val ruleId: String?,
    private val validPosition: Boolean,
    private val navigate: () -> Unit,
) : GutterIconRenderer() {
    override fun getIcon(): Icon = LanguageManagerIcons.ToolWindow

    override fun getTooltipText(): String =
        message(
            if (!invasive && !validPosition) "dynamic.gutter.rule.stale.tooltip"
            else if (invasive) "dynamic.gutter.marker.tooltip"
            else "dynamic.gutter.rule.tooltip",
        )

    override fun getClickAction(): AnAction =
        object : DumbAwareAction() {
            override fun actionPerformed(event: AnActionEvent) = navigate()
        }

    override fun isNavigateAction(): Boolean = true

    override fun getAlignment(): Alignment = Alignment.LEFT

    override fun equals(other: Any?): Boolean =
        other is DynamicSourceGutterRenderer &&
            invasive == other.invasive && ruleId == other.ruleId && validPosition == other.validPosition

    override fun hashCode(): Int = 31 * (31 * invasive.hashCode() + ruleId.hashCode()) + validPosition.hashCode()
}
