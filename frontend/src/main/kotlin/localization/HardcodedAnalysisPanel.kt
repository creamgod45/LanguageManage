package cg.creamgod45.localization.ui

import cg.creamgod45.CoroutineScopeHolder
import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.HardcodedAnalysisProgressDto
import cg.creamgod45.localization.HardcodedAnalysisResultDto
import cg.creamgod45.localization.HardcodedAnalysisStage
import cg.creamgod45.localization.HardcodedTextCandidateDto
import cg.creamgod45.localization.HardcodedTextConfidence
import cg.creamgod45.localization.LanguageSchemeDto
import cg.creamgod45.localization.LocalizationStateDto
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JCheckBoxMenuItem
import javax.swing.JComboBox
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JPopupMenu
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel

internal class HardcodedAnalysisPanel(
    private val project: Project,
) : JPanel(BorderLayout()), Disposable {
    companion object { private const val PAGE_SIZE = 100 }

    private enum class ConfidenceFilter { ALL, HIGH, MEDIUM, LOW }

    private val scope = CoroutineScopeHolder.getInstance(project).createScope("HardcodedAnalysisPanel")
    private val repository = LocalizationFrontendRepository(project)
    private val schemeLabel = JBLabel()
    private val search = JBTextField()
    private val confidence = JComboBox(ConfidenceFilter.entries.toTypedArray())
    private val excludedPatterns = linkedSetOf<AnalysisValuePatternFilter>()
    private val patternFilterButton = JButton(patternFilterButtonText())
    private val refresh = JButton(message("analysis.action.refresh"))
    private val export = JButton(message("analysis.action.export.count", 0)).apply { isEnabled = false }
    private val stop = JButton(message("analysis.action.stop")).apply { isEnabled = false }
    private val progress = JProgressBar(0, 100).apply { isStringPainted = true; isVisible = false }
    private val status = JBLabel(message("analysis.status.ready"))
    private val pageLabel = JBLabel()
    private val previous = JButton(message("button.previous"))
    private val next = JButton(message("button.next"))
    private val model = CandidateTableModel()
    private val table = JBTable(model)
    private val statistics = linkedMapOf<String, JBLabel>()
    private var state = LocalizationStateDto()
    private var result: HardcodedAnalysisResultDto? = null
    private var currentPage = 0
    private var selected = false
    private var runningIndicator: ProgressIndicator? = null
    private var runningSchemeId: String? = null
    private var analysisRunning = false

    init {
        border = JBUI.Borders.empty(6)
        add(header(), BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(footer(), BorderLayout.SOUTH)
        table.autoCreateRowSorter = true
        table.autoResizeMode = JBTable.AUTO_RESIZE_OFF
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && SwingUtilities.isLeftMouseButton(event)) openSelected()
            }
        })
        wireEvents()
        scope.launch { repository.state.collect { withContext(Dispatchers.EDT) { updateState(it) } } }
        scope.launch { repository.hardcodedAnalysisProgress.collect { value -> withContext(Dispatchers.EDT) { updateProgress(value) } } }
        renderPage()
    }

    fun onSelected() {
        selected = true
        if (state.busy) {
            status.text = message("analysis.status.waiting.scheme")
            return
        }
        val scheme = activeScheme() ?: return updateReadyState()
        if (!analysisRunning) startAnalysis(force = true)
    }

    fun onDeselected() {
        selected = false
    }

    private fun header() =
        JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 8, 3)).apply {
                    add(JBLabel(message("analysis.type")))
                    add(JBLabel(message("analysis.type.hardcoded")))
                    add(JBLabel(message("label.scheme")))
                    add(schemeLabel)
                    add(refresh)
                    add(export)
                    add(stop)
                    add(JBLabel(message("label.search")))
                    search.columns = 18
                    add(search)
                    add(JBLabel(message("analysis.confidence")))
                    confidence.renderer = AnalysisConfidenceRenderer()
                    add(confidence)
                    add(patternFilterButton)
                },
            )
            add(
                JPanel(GridLayout(3, 3, JBUI.scale(8), JBUI.scale(4))).apply {
                    listOf(
                        "scanned" to "analysis.stat.scanned",
                        "cached" to "analysis.stat.cached",
                        "skipped" to "analysis.stat.skipped",
                        "matched" to "analysis.stat.matched",
                        "locations" to "analysis.stat.locations",
                        "unique" to "analysis.stat.unique",
                        "high" to "analysis.stat.high",
                        "medium" to "analysis.stat.medium",
                        "low" to "analysis.stat.low",
                    ).forEach { (id, key) ->
                        add(JBLabel(message(key, 0)).also { statistics[id] = it })
                    }
                },
            )
            add(progress)
        }

    private fun footer() =
        JPanel(BorderLayout()).apply {
            add(status, BorderLayout.WEST)
            add(
                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 3)).apply {
                    add(JButton(message("analysis.action.open")).apply { addActionListener { openSelected() } })
                    add(JBLabel(message("pagination.limit", PAGE_SIZE)))
                    add(previous)
                    add(pageLabel)
                    add(next)
                },
                BorderLayout.EAST,
            )
        }

    private fun wireEvents() {
        refresh.addActionListener { startAnalysis(force = true) }
        export.addActionListener { exportFilteredResults() }
        stop.addActionListener { runningIndicator?.cancel() }
        previous.addActionListener { if (currentPage > 0) { currentPage--; renderPage() } }
        next.addActionListener { currentPage++; renderPage() }
        confidence.addActionListener { currentPage = 0; renderPage() }
        patternFilterButton.addActionListener { showPatternFilterMenu() }
        search.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = changed()
            override fun removeUpdate(e: DocumentEvent?) = changed()
            override fun changedUpdate(e: DocumentEvent?) = changed()
            private fun changed() { currentPage = 0; renderPage() }
        })
    }

    private fun updateState(nextState: LocalizationStateDto) {
        val changed = state.activeSchemeId != nextState.activeSchemeId
        val becameReady = state.busy && !nextState.busy
        state = nextState
        schemeLabel.text = activeScheme()?.name ?: message("analysis.scheme.none")
        if (changed) {
            result = null
            export.isEnabled = false
            currentPage = 0
            renderStatistics(null)
            renderPage()
            if (selected && !analysisRunning) onSelected()
        }
        if (selected && becameReady && !analysisRunning) onSelected()
        refresh.isEnabled = activeScheme() != null && !state.busy && !analysisRunning
    }

    private fun startAnalysis(force: Boolean) {
        val scheme = activeScheme() ?: return updateReadyState()
        if (analysisRunning) return
        analysisRunning = true
        runningSchemeId = scheme.id
        refresh.isEnabled = false
        export.isEnabled = false
        stop.isEnabled = true
        progress.isVisible = true
        progress.isIndeterminate = true
        val task =
            object : Task.Backgroundable(project, message("analysis.task.title"), true) {
                override fun run(indicator: ProgressIndicator) {
                    runningIndicator = indicator
                    val value =
                        runBlocking {
                            coroutineScope {
                                val deferred = async { repository.analyzeHardcodedText(scheme.id, force) }
                                while (deferred.isActive) {
                                    if (indicator.isCanceled) {
                                        deferred.cancelAndJoin()
                                        throw ProcessCanceledException()
                                    }
                                    delay(100)
                                }
                                deferred.await()
                            }
                        }
                    ApplicationManager.getApplication().invokeLater { finish(value) }
                }

                override fun onThrowable(error: Throwable) {
                    if (error !is ProcessCanceledException) notifyError(error)
                }

                override fun onFinished() {
                    ApplicationManager.getApplication().invokeLater {
                        runningIndicator = null
                        runningSchemeId = null
                        analysisRunning = false
                        stop.isEnabled = false
                        refresh.isEnabled = activeScheme() != null
                        progress.isVisible = false
                        renderPage()
                    }
                }
            }
        task.queue()
    }

    private fun finish(value: HardcodedAnalysisResultDto) {
        if (state.activeSchemeId != value.schemeId) return
        result = value
        export.isEnabled = value.items.isNotEmpty()
        currentPage = 0
        renderStatistics(value)
        renderPage()
        status.text = if (value.truncated) message("analysis.status.truncated") else message("analysis.status.completed")
    }

    private fun updateProgress(value: HardcodedAnalysisProgressDto) {
        if (value.schemeId != runningSchemeId) return
        val total = value.totalFiles
        progress.isIndeterminate = total <= 0
        progress.value = if (total > 0) (value.processedFiles * 100 / total).coerceIn(0, 100) else 0
        progress.string =
            when (value.stage) {
                HardcodedAnalysisStage.DISCOVERING -> message("analysis.progress.discovering", value.discoveredFiles)
                HardcodedAnalysisStage.SCANNING -> message("analysis.progress.scanning", value.processedFiles, total, value.cachedFiles, value.currentPath)
                HardcodedAnalysisStage.CANCELLED -> message("analysis.status.cancelled")
                HardcodedAnalysisStage.FAILED -> message("analysis.status.failed")
                else -> message("analysis.status.ready")
            }
        status.text = progress.string
    }

    private fun renderStatistics(value: HardcodedAnalysisResultDto?) {
        val s = value?.statistics
        statistics["scanned"]?.text = message("analysis.stat.scanned", s?.scannedFiles ?: 0)
        statistics["cached"]?.text = message("analysis.stat.cached", s?.cachedFiles ?: 0)
        statistics["skipped"]?.text = message("analysis.stat.skipped", s?.skippedFiles ?: 0)
        statistics["matched"]?.text = message("analysis.stat.matched", s?.matchedFiles ?: 0)
        statistics["locations"]?.text = message("analysis.stat.locations", s?.candidateLocations ?: 0)
        statistics["unique"]?.text = message("analysis.stat.unique", s?.uniqueTexts ?: 0)
        statistics["high"]?.text = message("analysis.stat.high", s?.highConfidence ?: 0)
        statistics["medium"]?.text = message("analysis.stat.medium", s?.mediumConfidence ?: 0)
        statistics["low"]?.text = message("analysis.stat.low", s?.lowConfidence ?: 0)
    }

    private fun renderPage() {
        val filtered = filteredItems()
        export.text = message("analysis.action.export.count", filtered.size)
        export.isEnabled = filtered.isNotEmpty() && !analysisRunning
        val pages = maxOf(1, (filtered.size + PAGE_SIZE - 1) / PAGE_SIZE)
        currentPage = currentPage.coerceIn(0, pages - 1)
        model.items = filtered.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE)
        pageLabel.text = message("pagination.page", currentPage + 1, pages, filtered.size)
        previous.isEnabled = currentPage > 0
        next.isEnabled = currentPage + 1 < pages
    }

    private fun filteredItems(): List<HardcodedTextCandidateDto> {
        val selectedConfidence = confidence.selectedItem as? ConfidenceFilter ?: ConfidenceFilter.ALL
        return filterAnalysisItems(
            result?.items.orEmpty(),
            search.text,
            selectedConfidence.takeUnless { it == ConfidenceFilter.ALL }?.let { HardcodedTextConfidence.valueOf(it.name) },
            excludedPatterns,
        )
    }

    private fun showPatternFilterMenu() {
        val menu = JPopupMenu()
        AnalysisValuePatternFilter.entries.forEach { filter ->
            menu.add(
                JCheckBoxMenuItem(message("analysis.exclude.pattern.${filter.name.lowercase()}"), filter in excludedPatterns).apply {
                    addActionListener {
                        if (isSelected) excludedPatterns += filter else excludedPatterns -= filter
                        patternFilterButton.text = patternFilterButtonText()
                        currentPage = 0
                        renderPage()
                    }
                },
            )
        }
        menu.addSeparator()
        menu.add(message("analysis.exclude.pattern.clear")).addActionListener {
            excludedPatterns.clear()
            patternFilterButton.text = patternFilterButtonText()
            currentPage = 0
            renderPage()
        }
        menu.show(patternFilterButton, 0, patternFilterButton.height)
    }

    private fun patternFilterButtonText(): String = message("analysis.exclude.patterns", excludedPatterns.size)

    private fun openSelected() {
        val row = table.selectedRow.takeIf { it >= 0 } ?: return
        val item = model.items.getOrNull(table.convertRowIndexToModel(row)) ?: return
        val file = runCatching { LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(item.filePath)) }.getOrNull() ?: return
        OpenFileDescriptor(project, file, item.line - 1, item.column - 1).navigate(true)
    }

    private fun exportFilteredResults() {
        val items = filteredItems()
        if (items.isEmpty()) return
        val descriptor =
            FileSaverDescriptor(
                message("analysis.export.title"),
                message("analysis.export.description", items.size),
                "csv",
            )
        val base = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val target =
            FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
                .save(base, "language-manager-untranslated-locations.csv") ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    HardcodedAnalysisExport.write(target.file.toPath(), HardcodedAnalysisExport.csv(items))
                }
                withContext(Dispatchers.EDT) {
                    Messages.showInfoMessage(project, message("analysis.export.success", items.size), message("analysis.export.title"))
                }
            } catch (error: Exception) {
                withContext(Dispatchers.EDT) { notifyError(error) }
            }
        }
    }

    private fun activeScheme(): LanguageSchemeDto? = state.schemes.firstOrNull { it.id == state.activeSchemeId }

    private fun updateReadyState() {
        schemeLabel.text = message("analysis.scheme.none")
        status.text = message("analysis.status.no.scheme")
        refresh.isEnabled = false
    }

    private fun notifyError(error: Throwable) {
        NotificationGroupManager.getInstance().getNotificationGroup("Language Manager Notifications")
            .createNotification(message("analysis.status.failed"), error.message.orEmpty().take(500), NotificationType.ERROR)
            .notify(project)
    }

    override fun dispose() {
        runningIndicator?.cancel()
        scope.cancel()
    }

    private inner class AnalysisConfidenceRenderer : javax.swing.DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: javax.swing.JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean) =
            super.getListCellRendererComponent(list, message("analysis.confidence.${(value as? ConfidenceFilter ?: ConfidenceFilter.ALL).name.lowercase()}"), index, selected, focus)
    }

    private class CandidateTableModel : AbstractTableModel() {
        var items: List<HardcodedTextCandidateDto> = emptyList()
            set(value) { field = value; fireTableDataChanged() }
        override fun getRowCount() = items.size
        override fun getColumnCount() = 4
        override fun getColumnName(column: Int) = message(arrayOf("analysis.column.text", "analysis.column.file", "analysis.column.line", "analysis.column.column")[column])
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val item = items[rowIndex]
            return when (columnIndex) {
                0 -> item.text
                1 -> item.filePath
                2 -> item.line
                else -> item.column
            }
        }
    }
}
