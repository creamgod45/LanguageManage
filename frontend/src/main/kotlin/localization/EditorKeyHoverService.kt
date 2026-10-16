package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.EditorKeyMatchDto
import cg.creamgod45.localization.EditorKeyScanRequestDto
import cg.creamgod45.localization.EditorKeyScanResultDto
import cg.creamgod45.localization.EditorKeyScanStatus
import cg.creamgod45.localization.EntrySearch
import cg.creamgod45.localization.JoinedTranslationRow
import cg.creamgod45.localization.LanguageSchemeDto
import cg.creamgod45.localization.LocalizationManagerRpcApi
import cg.creamgod45.localization.LocalizationStateDto
import cg.creamgod45.localization.MAX_EDITOR_KEY_SCAN_CHARS
import cg.creamgod45.settings.LanguageManagerSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.platform.project.projectId
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.awt.RelativePoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Cursor
import java.awt.MouseInfo
import java.awt.Point
import java.awt.Rectangle
import java.util.WeakHashMap
import javax.swing.SwingUtilities
import javax.swing.Timer

internal class EditorKeyHoverStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.getService(EditorKeyHoverService::class.java)
    }
}

/**
 * Experimental editor key hover (issue #38): underlines a usage-regex key under the mouse in writable editors and, after
 * the configured dwell time, shows its translations with shortcuts to the translation table and the edit dialog.
 *
 * Everything here runs on the EDT. Matches come from the backend ([LocalizationManagerRpcApi.scanEditorKeys]) and are
 * requested only when the mouse moves over an editor whose cached scan is stale, so there is no polling.
 */
@Service(Service.Level.PROJECT)
internal class EditorKeyHoverService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) : Disposable {
    private data class ScanCache(
        val stamp: Long,
        val stateVersion: Long,
        val result: EditorKeyScanResultDto,
    )

    private data class Hover(
        val editor: Editor,
        val match: EditorKeyMatchDto,
        val stamp: Long,
        val highlighter: RangeHighlighter,
    )

    private data class MousePosition(
        val editor: Editor,
        val offset: Int,
        val overText: Boolean,
        val stamp: Long,
    )

    @Volatile
    private var state = LocalizationStateDto()
    private var stateVersion = 0L
    private val scans = WeakHashMap<Document, ScanCache>()
    private val scansInFlight = WeakHashMap<Document, Boolean>()
    private var lastMouse: MousePosition? = null
    private var hover: Hover? = null
    private var popup: JBPopup? = null
    private var popupMatch: Pair<Editor, EditorKeyMatchDto>? = null
    private val dwellTimer = Timer(LanguageManagerSettings.editorKeyHoverDelayMs()) { showPopupForHover() }.apply { isRepeats = false }
    private val closeTimer = Timer(CLOSE_DELAY_MS) { closePopupIfMouseLeft() }.apply { isRepeats = false }

    init {
        val multicaster = EditorFactory.getInstance().eventMulticaster
        multicaster.addEditorMouseMotionListener(
            object : EditorMouseMotionListener {
                override fun mouseMoved(event: EditorMouseEvent) = onMouseMoved(event)
            },
            this,
        )
        multicaster.addEditorMouseListener(
            object : EditorMouseListener {
                override fun mouseExited(event: EditorMouseEvent) {
                    if (event.editor.project != project) return
                    lastMouse = null
                    clearHover()
                    if (popup != null) closeTimer.restart()
                }

                override fun mousePressed(event: EditorMouseEvent) {
                    if (event.editor.project == project) clearHover()
                }
            },
            this,
        )
        coroutineScope.launch {
            LocalizationFrontendRepository(project).state.collectLatest { latest ->
                withContext(Dispatchers.EDT) {
                    val schemeChanged = latest.activeSchemeId != state.activeSchemeId
                    state = latest
                    stateVersion++
                    scans.clear()
                    if (schemeChanged || latest.activeSchemeId == null) {
                        clearHover()
                        cancelPopup()
                    }
                }
            }
        }
    }

    private fun onMouseMoved(event: EditorMouseEvent) {
        val editor = event.editor
        if (editor.project != project) return
        if (!LanguageManagerSettings.editorKeyHoverEnabled() || !isEligible(editor) || event.area != EditorMouseEventArea.EDITING_AREA) {
            lastMouse = null
            clearHover()
            return
        }
        val document = editor.document
        val position = MousePosition(editor, event.offset, event.isOverText, document.modificationStamp)
        lastMouse = position
        val scan = scans[document]
        if (scan == null || scan.stamp != position.stamp || scan.stateVersion != stateVersion) {
            clearHover()
            requestScan(editor)
            return
        }
        updateHover(position, scan.result)
    }

    private fun updateHover(
        position: MousePosition,
        result: EditorKeyScanResultDto,
    ) {
        val match =
            if (position.overText && result.status == EditorKeyScanStatus.OK) {
                EditorKeyHoverSupport.matchAt(result.matches, position.offset)
            } else {
                null
            }
        val current = hover
        if (match == null) {
            clearHover()
            if (popup != null) closeTimer.restart()
            return
        }
        if (current != null && current.editor === position.editor && current.match == match && current.stamp == position.stamp) return
        clearHover()
        val editor = position.editor
        if (match.endOffsetExclusive > editor.document.textLength) return
        val highlighter =
            editor.markupModel.addRangeHighlighter(
                CodeInsightColors.HYPERLINK_ATTRIBUTES,
                match.startOffset,
                match.endOffsetExclusive,
                HighlighterLayer.HYPERLINK,
                HighlighterTargetArea.EXACT_RANGE,
            )
        (editor as? EditorEx)?.setCustomCursor(this, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        hover = Hover(editor, match, position.stamp, highlighter)
        if (popupMatch?.let { it.first === editor && it.second == match } == true) {
            closeTimer.stop()
            return
        }
        dwellTimer.initialDelay = LanguageManagerSettings.editorKeyHoverDelayMs()
        dwellTimer.restart()
    }

    private fun clearHover() {
        dwellTimer.stop()
        val current = hover ?: return
        hover = null
        current.editor.markupModel.removeHighlighter(current.highlighter)
        (current.editor as? EditorEx)?.setCustomCursor(this, null)
    }

    private fun isEligible(editor: Editor): Boolean {
        val file = FileDocumentManager.getInstance().getFile(editor.document)
        return EditorKeyHoverSupport.isEligibleEditor(
            isMainEditor = editor.editorKind == EditorKind.MAIN_EDITOR,
            isViewer = editor.isViewer,
            documentWritable = editor.document.isWritable,
            fileWritable = file?.isWritable == true,
            // In remote development the frontend's files are not in the local file system, so only in-memory files are skipped.
            physicalFile = file != null && file !is LightVirtualFile,
            hasActiveScheme = state.activeSchemeId != null,
        )
    }

    private fun requestScan(editor: Editor) {
        val document = editor.document
        if (scansInFlight[document] == true) return
        val schemeId = state.activeSchemeId ?: return
        val filePath = FileDocumentManager.getInstance().getFile(document)?.path ?: return
        val stamp = document.modificationStamp
        val version = stateVersion
        if (document.textLength > MAX_EDITOR_KEY_SCAN_CHARS) {
            scans[document] = ScanCache(stamp, version, EditorKeyScanResultDto(EditorKeyScanStatus.UNSUPPORTED))
            return
        }
        val text = document.immutableCharSequence
        scansInFlight[document] = true
        coroutineScope.launch {
            val result =
                try {
                    LocalizationManagerRpcApi
                        .getInstance()
                        .scanEditorKeys(project.projectId(), schemeId, EditorKeyScanRequestDto(filePath, text.toString()))
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    LOG.debug("Editor key scan failed for '$filePath'", error)
                    EditorKeyScanResultDto(EditorKeyScanStatus.UNSUPPORTED)
                }
            withContext(Dispatchers.EDT) {
                scansInFlight.remove(document)
                if (document.modificationStamp == stamp && stateVersion == version && state.activeSchemeId == schemeId) {
                    scans[document] = ScanCache(stamp, version, result)
                }
                // The mouse may be resting on a key; re-evaluate it now instead of waiting for the next move.
                val mouse = lastMouse ?: return@withContext
                if (mouse.editor.document !== document || !LanguageManagerSettings.editorKeyHoverEnabled()) return@withContext
                val latest = scans[document]
                if (latest != null && latest.stamp == mouse.stamp && latest.stateVersion == stateVersion) {
                    updateHover(mouse, latest.result)
                } else if (mouse.stamp != stamp || stateVersion != version) {
                    requestScan(mouse.editor)
                }
            }
        }
    }

    private fun showPopupForHover() {
        val current = hover ?: return
        val editor = current.editor
        if (editor.isDisposed || !editor.contentComponent.isShowing || editor.document.modificationStamp != current.stamp) return
        if (state.busy && state.entries.isEmpty()) return
        cancelPopup()
        val start = editor.offsetToXY(current.match.startOffset)
        val anchor = RelativePoint(editor.contentComponent, Point(start.x, start.y + editor.lineHeight))
        val created = EditorKeyHoverPopup.create(current.match, state.entries, anchor, popupActions())
        val listener =
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) = cancelPopup()
            }
        editor.document.addDocumentListener(listener, created)
        editor.scrollingModel.addVisibleAreaListener({ cancelPopup() }, created)
        popup = created
        popupMatch = editor to current.match
        created.addListener(
            object : com.intellij.openapi.ui.popup.JBPopupListener {
                override fun onClosed(event: com.intellij.openapi.ui.popup.LightweightWindowEvent) {
                    if (popup === created) {
                        popup = null
                        popupMatch = null
                        closeTimer.stop()
                    }
                }
            },
        )
        created.show(anchor)
    }

    private fun closePopupIfMouseLeft() {
        val shown = popup ?: return
        val match = popupMatch
        if (match != null && hover?.let { it.editor === match.first && it.match == match.second } == true) return
        val pointer = MouseInfo.getPointerInfo()?.location
        val content = shown.content
        if (pointer != null && content.isShowing) {
            val bounds = Rectangle(content.locationOnScreen, content.size)
            bounds.grow(POPUP_HOVER_MARGIN, POPUP_HOVER_MARGIN)
            if (bounds.contains(pointer)) return
        }
        shown.cancel()
    }

    private fun cancelPopup() {
        closeTimer.stop()
        popup?.cancel()
        popup = null
        popupMatch = null
    }

    private fun popupActions() =
        object : EditorKeyHoverActions {
            override fun openInTable(row: JoinedTranslationRow) {
                ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)?.activate {
                    LocalizationActionContext
                        .getInstance(project)
                        .submit(LocalizationUiCommand.FocusTranslation(row.namespace, row.key))
                }
            }

            override fun edit(row: JoinedTranslationRow) {
                SwingUtilities.invokeLater { showEntryDialog(row.namespace, row.key, null) }
            }

            override fun create(match: EditorKeyMatchDto) {
                SwingUtilities.invokeLater { showEntryDialog(null, null, EditorKeyHoverSupport.createDraft(match)) }
            }
        }

    /**
     * The same add/edit dialog and save call as the translation table. A key that was deleted meanwhile opens as a new
     * translation, and a rejected save reopens the dialog with what the user typed.
     */
    private fun showEntryDialog(
        namespace: String?,
        key: String?,
        draft: TranslationEntryDraft?,
    ) {
        val latest = state
        val scheme: LanguageSchemeDto =
            latest.schemes.firstOrNull { it.id == latest.activeSchemeId }
                ?: return Messages.showErrorDialog(project, message("error.no.active.scheme"), message("dialog.confirm.title"))
        val row =
            if (namespace != null && key != null) {
                EntrySearch.join(latest.entries.filter { it.namespace == namespace && it.key == key }).firstOrNull()
            } else {
                null
            }
        val effectiveDraft = draft ?: if (row == null && key != null) TranslationEntryDraft(namespace.orEmpty(), key, emptyMap()) else null
        val dialog = MultiLanguageEntryDialog(project, scheme, latest.entries, row, effectiveDraft)
        if (!dialog.showAndGet()) return
        val mutations = dialog.mutations()
        val submitted = dialog.draft()
        coroutineScope.launch {
            try {
                LocalizationFrontendRepository(project).saveAll(scheme.id, mutations)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                withContext(Dispatchers.EDT) {
                    Messages.showErrorDialog(project, safeErrorText(error), message("dialog.confirm.title"))
                    if (state.activeSchemeId == scheme.id) {
                        if (row == null) showEntryDialog(null, null, submitted) else showEntryDialog(row.namespace, row.key, submitted)
                    }
                }
            }
        }
    }

    override fun dispose() {
        dwellTimer.stop()
        closeTimer.stop()
        clearHover()
        cancelPopup()
    }

    private companion object {
        const val TOOL_WINDOW_ID = "Language Manager"
        const val CLOSE_DELAY_MS = 350
        const val POPUP_HOVER_MARGIN = 8
        val LOG = Logger.getInstance(EditorKeyHoverService::class.java)

        fun safeErrorText(error: Throwable): String =
            (error.message ?: message("error.action.failed"))
                .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "?")
                .take(500)
    }
}
