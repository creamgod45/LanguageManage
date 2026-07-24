package cg.creamgod45.localization.ui

import cg.creamgod45.CoroutineScopeHolder
import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.*
import cg.creamgod45.settings.AiProviderCredentialStore
import cg.creamgod45.settings.LanguageManagerSettings
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.find.FindManager
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import java.awt.LayoutManager2
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class LocalizationManagerPanel(
    private val project: Project,
) : JPanel(BorderLayout()),
    Disposable {
    private val scope = CoroutineScopeHolder.getInstance(project).createScope("LocalizationManagerPanel")
    private val repository = LocalizationFrontendRepository(project)
    private val schemeBox = ComboBox<LanguageSchemeDto>()
    private val searchField = JBTextField()
    private val searchMode = ComboBox(SearchMode.entries.toTypedArray())
    private val localeBox = ComboBox<String>()
    private val rowFilterBox = ComboBox(TranslationRowFilter.entries.toTypedArray())
    private val entryModel = EntryTableModel()
    private val entryTable =
        RowHighlightTable(entryModel) { modelColumn ->
            message("tooltip.usage.locations").takeIf { modelColumn == entryModel.columnCount - 1 }
        }
    private val issueModel = IssueTableModel()
    private val issueTable = JBTable(issueModel)
    private val usageLocationModel = UsageLocationTableModel()
    private val usageLocationTable = JBTable(usageLocationModel)
    private val tabs = JTabbedPane()
    private val previousPageButton = JButton(message("button.previous"))
    private val nextPageButton = JButton(message("button.next"))
    private val pageLabel = JBLabel()
    private val usagePreviousPageButton = JButton(message("button.previous"))
    private val usageNextPageButton = JButton(message("button.next"))
    private val usagePageLabel = JBLabel()
    private val usageLocationWarning = JBLabel()
    private val status = JBLabel(message("status.initial"))
    private val loadProgressState = MutableStateFlow(LoadProgressDto())
    private var current = LocalizationStateDto()
    private var updatingSchemes = false
    private var currentPage = 0
    private var currentUsagePage = 0
    private var currentUsageLocations = UsageLocationPageDto()
    private var usageLocationTarget: Pair<String, String>? = null
    private var usageLocationLoadJob: Job? = null
    private var updatingEntryTable = false
    private var schemeLoadJob: Job? = null
    private var schemeLoadIndicator: ProgressIndicator? = null
    private var schemeLoadTaskId = 0L
    private var schemeLoadPending = false
    private var schemeLoadObservedBusy = false
    private var nextOperationId = 0L
    private val runningOperations = mutableSetOf<Long>()

    companion object {
        private const val PAGE_SIZE = 100
        private const val USAGE_LOCATIONS_TAB_INDEX = 2
        private const val ISSUE_REPORT_URL = "https://github.com/creamgod45/LanguageManage/issues/new"
    }

    init {
        border = JBUI.Borders.empty(6)
        add(createHeader(), BorderLayout.NORTH)
        add(createTabs(), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        wireEvents()
        scope.launch { repository.state.collect { state -> withContext(Dispatchers.EDT) { render(state) } } }
        scope.launch {
            repository.loadProgress.collect { progress ->
                loadProgressState.value = progress
                withContext(Dispatchers.EDT) {
                    if (progress.schemeId == current.activeSchemeId || progress.stage == LoadProgressStage.IDLE) refreshStatus()
                }
            }
        }
    }

    private fun createHeader() =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            val schemeRow =
                ResponsiveGridPanel(JBUI.scale(5), JBUI.scale(4)).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                    add(JBLabel(message("label.scheme")))
                    schemeBox.preferredSize = Dimension(220, schemeBox.preferredSize.height)
                    add(schemeBox)
                    add(schemeCreationDropdown())
                    add(button(message("button.scheme.delete"), ::deleteScheme))
                    add(button(message("button.scheme.settings"), ::editSchemeSettings))
                    add(
                        button(message("button.reload")) {
                            val scheme = activeScheme() ?: return@button showError(message("error.no.active.scheme"))
                            runSchemeLoad(scheme.id, message("progress.scheme.reloading", scheme.name)) { repository.reload(scheme.id) }
                        },
                    )
                    add(
                        button(message("button.repair.normalize")) {
                            previewAndApply(ChangePreviewRequestDto(normalizeAll = true), message("summary.repair.normalize"))
                        },
                    )
                    add(ActionLink(message("action.report.issue")) { BrowserUtil.browse(ISSUE_REPORT_URL) }.apply { setExternalLinkIcon() })
                }
            val searchRow =
                ResponsiveGridPanel(JBUI.scale(5), JBUI.scale(4)).apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                    add(JBLabel(message("label.search")))
                    searchField.columns = 25
                    add(searchField)
                    searchMode.renderer =
                        object : DefaultListCellRenderer() {
                            override fun getListCellRendererComponent(
                                list: JList<*>?,
                                value: Any?,
                                index: Int,
                                selected: Boolean,
                                focus: Boolean,
                            ) = super.getListCellRendererComponent(
                                list,
                                when (value) {
                                    SearchMode.EXACT -> message("search.mode.exact")
                                    else -> message("search.mode.fuzzy")
                                },
                                index,
                                selected,
                                focus,
                            )
                        }
                    add(searchMode)
                    add(JBLabel(message("label.language")))
                    localeBox.preferredSize = Dimension(110, localeBox.preferredSize.height)
                    add(localeBox)
                    rowFilterBox.renderer =
                        object : DefaultListCellRenderer() {
                            override fun getListCellRendererComponent(
                                list: JList<*>?,
                                value: Any?,
                                index: Int,
                                selected: Boolean,
                                focus: Boolean,
                            ) = super.getListCellRendererComponent(
                                list,
                                message((value as? TranslationRowFilter ?: TranslationRowFilter.ALL).messageKey()),
                                index,
                                selected,
                                focus,
                            )
                        }
                    add(JBLabel(message("label.translation.filter")))
                    rowFilterBox.preferredSize =
                        Dimension(190, rowFilterBox.preferredSize.height)
                    add(rowFilterBox)
                    add(actionDropdown())
                }
            add(schemeRow)
            add(Box.createVerticalStrut(JBUI.scale(4)))
            add(searchRow)
        }

    private fun createTabs() =
        tabs.apply {
            entryTable.autoCreateRowSorter = true
            entryTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
            entryTable.cellSelectionEnabled = true
            entryTable.rowSelectionAllowed = true
            entryTable.columnSelectionAllowed = true
            entryTable.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            installClipboardActions(entryTable, allowPaste = true)
            val translationPanel =
                JPanel(BorderLayout()).apply {
                    add(JBScrollPane(entryTable), BorderLayout.CENTER)
                    add(
                        JPanel(FlowLayout(FlowLayout.RIGHT, 8, 3)).apply {
                            add(JBLabel(message("pagination.limit", PAGE_SIZE)))
                            add(previousPageButton)
                            add(pageLabel)
                            add(nextPageButton)
                        },
                        BorderLayout.SOUTH,
                    )
                }
            addTab(message("tab.translations"), translationPanel)
            issueTable.autoCreateRowSorter = true
            issueTable.cellSelectionEnabled = true
            issueTable.rowSelectionAllowed = true
            issueTable.columnSelectionAllowed = true
            issueTable.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            installClipboardActions(issueTable, allowPaste = false)
            val issuesPanel =
                JPanel(BorderLayout()).apply {
                    add(JBScrollPane(issueTable), BorderLayout.CENTER)
                    add(
                        JPanel(FlowLayout(FlowLayout.RIGHT, 8, 3)).apply {
                            add(button(message("button.issues.selected"), ::handleSelectedIssues))
                            add(button(message("button.issues.all"), ::handleAllRepairableIssues))
                        },
                        BorderLayout.SOUTH,
                    )
                }
            addTab(message("tab.issues"), issuesPanel)
            issueTable.columnModel.getColumn(issueModel.columnCount - 1).apply {
                preferredWidth = 80
                cellRenderer = IssueActionButtonRenderer()
                cellEditor = IssueActionButtonEditor(issueTable) { issue -> handleIssue(issue) }
            }
            usageLocationTable.autoCreateRowSorter = true
            usageLocationTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
            usageLocationTable.cellSelectionEnabled = true
            usageLocationTable.rowSelectionAllowed = true
            usageLocationTable.columnSelectionAllowed = true
            usageLocationTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
            installClipboardActions(usageLocationTable, allowPaste = false)
            usageLocationTable.addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        if (event.clickCount == 2 && SwingUtilities.isLeftMouseButton(event)) openSelectedUsageLocation()
                    }
                },
            )
            val usageLocationsPanel =
                JPanel(BorderLayout()).apply {
                    add(JBScrollPane(usageLocationTable), BorderLayout.CENTER)
                    add(
                        JPanel(BorderLayout()).apply {
                            add(usageLocationWarning, BorderLayout.WEST)
                            add(
                                JPanel(FlowLayout(FlowLayout.RIGHT, 8, 3)).apply {
                                    add(button(message("button.usage.location.open"), ::openSelectedUsageLocation))
                                    add(JBLabel(message("pagination.limit", PAGE_SIZE)))
                                    add(usagePreviousPageButton)
                                    add(usagePageLabel)
                                    add(usageNextPageButton)
                                },
                                BorderLayout.EAST,
                            )
                        },
                        BorderLayout.SOUTH,
                    )
                }
            addTab(message("tab.usage.locations"), usageLocationsPanel)
            setEnabledAt(USAGE_LOCATIONS_TAB_INDEX, false)
        }

    private fun wireEvents() {
        schemeBox.addActionListener {
            if (!updatingSchemes) {
                (schemeBox.selectedItem as? LanguageSchemeDto)
                    ?.takeIf {
                        it.id !=
                            current.activeSchemeId
                    }?.let { scheme ->
                        runSchemeLoad(scheme.id, message("progress.scheme.loading", scheme.name)) { repository.activateScheme(scheme.id) }
                    }
            }
        }
        searchMode.addActionListener { applyFilter() }
        localeBox.addActionListener { applyFilter() }
        rowFilterBox.addActionListener { applyFilter() }
        entryTable.selectionModel.addListSelectionListener { event ->
            if (!event.valueIsAdjusting && !updatingEntryTable) {
                val selected = selectedRows()
                if (selected.isNotEmpty() && selected.none { (it.namespace to it.key) == usageLocationTarget }) clearUsageLocationTarget()
            }
        }
        entryTable.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(event: MouseEvent) {
                    if (event.clickCount != 2 || !SwingUtilities.isLeftMouseButton(event)) return
                    val viewRow = entryTable.rowAtPoint(event.point)
                    val viewColumn = entryTable.columnAtPoint(event.point)
                    if (viewRow < 0 || viewColumn < 0 || entryTable.convertColumnIndexToModel(viewColumn) != entryModel.columnCount - 1) return
                    val row = entryModel.items.getOrNull(entryTable.convertRowIndexToModel(viewRow)) ?: return
                    usageLocationTarget = row.namespace to row.key
                    currentUsagePage = 0
                    tabs.setEnabledAt(USAGE_LOCATIONS_TAB_INDEX, true)
                    loadUsageLocationPage()
                    tabs.selectedIndex = USAGE_LOCATIONS_TAB_INDEX
                }
            },
        )
        searchField.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(e: DocumentEvent?) = applyFilter()

                override fun removeUpdate(e: DocumentEvent?) = applyFilter()

                override fun changedUpdate(e: DocumentEvent?) = applyFilter()
            },
        )
        previousPageButton.addActionListener {
            if (currentPage > 0) {
                currentPage--
                applyFilter(resetPage = false)
            }
        }
        nextPageButton.addActionListener {
            currentPage++
            applyFilter(resetPage = false)
        }
        usagePreviousPageButton.addActionListener {
            if (currentUsagePage > 0) {
                currentUsagePage--
                loadUsageLocationPage()
            }
        }
        usageNextPageButton.addActionListener {
            currentUsagePage++
            loadUsageLocationPage()
        }
    }

    private fun render(state: LocalizationStateDto) {
        if (current.activeSchemeId != state.activeSchemeId || (state.busy && !current.busy)) clearUsageLocationTarget()
        current = state
        if (state.busy && schemeLoadPending) schemeLoadObservedBusy = true
        if (!state.busy && schemeLoadObservedBusy) schemeLoadPending = false
        updatingSchemes = true
        schemeBox.model = DefaultComboBoxModel(state.schemes.toTypedArray())
        schemeBox.renderer =
            object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    selected: Boolean,
                    focus: Boolean,
                ) = super.getListCellRendererComponent(
                    list,
                    (value as? LanguageSchemeDto)?.name ?: value,
                    index,
                    selected,
                    focus,
                )
            }
        schemeBox.selectedItem = state.schemes.firstOrNull { it.id == state.activeSchemeId }
        updatingSchemes = false
        val allLocales = message("filter.locale.all")
        val locales =
            listOf(allLocales) +
                state.entries
                    .map { it.locale }
                    .distinct()
                    .sorted()
        val previousLocale = localeBox.selectedItem?.toString()
        localeBox.model = DefaultComboBoxModel(locales.toTypedArray())
        localeBox.selectedItem =
            previousLocale?.takeIf { it in locales } ?: allLocales
        val displayedIssues = displayedIssues(state.issues)
        issueModel.items = displayedIssues
        applyFilter()
        renderUsageLocationTable()
        refreshStatus(displayedIssues)
    }

    private fun refreshStatus(issues: List<LanguageIssueDto> = displayedIssues(current.issues)) {
        val errors = issues.count { it.severity == IssueSeverity.ERROR }
        val progress = loadProgressState.value.takeIf { it.schemeId == current.activeSchemeId }
        status.text =
            current.errorMessage
                ?: when {
                    current.busy -> progress?.let(::progressStatusText) ?: message("status.loading")
                    runningOperations.isNotEmpty() -> message("status.processing")
                    schemeLoadPending -> message("status.loading")
                    current.activeSchemeId == null -> message("status.no.scheme")
                    else -> message("status.summary", current.entries.size, issues.size, errors)
                }
    }

    private fun progressStatusText(progress: LoadProgressDto): String {
        val stage = progressStageText(progress.stage)
        return if (progress.totalSteps > 0) {
            message("status.progress", stage, progress.completedSteps, progress.totalSteps, progress.detail)
        } else {
            message("status.progress.indeterminate", stage, progress.detail)
        }
    }

    private fun progressStageText(stage: LoadProgressStage): String =
        message(
            when (stage) {
                LoadProgressStage.IDLE -> "progress.stage.idle"
                LoadProgressStage.PLANNING -> "progress.stage.planning"
                LoadProgressStage.CACHE -> "progress.stage.cache"
                LoadProgressStage.PARSING -> "progress.stage.parsing"
                LoadProgressStage.BUILDING_TABLE -> "progress.stage.building.table"
                LoadProgressStage.SCANNING_USAGE -> "progress.stage.scanning.usage"
                LoadProgressStage.ANALYZING -> "progress.stage.analyzing"
                LoadProgressStage.WRITING_CACHE -> "progress.stage.writing.cache"
                LoadProgressStage.COMPLETED -> "progress.stage.completed"
            },
        )

    private fun applyFilter(resetPage: Boolean = true) {
        if (resetPage) currentPage = 0
        val locale = localeBox.selectedItem?.toString()?.takeUnless { it == message("filter.locale.all") }
        val matches =
            EntrySearch.filter(
                current.entries,
                searchField.text,
                searchMode.selectedItem as? SearchMode ?: SearchMode.FUZZY,
                locale,
            )
        val matchingKeys = matches.map { it.namespace to it.key }.toSet()
        val locales =
            current.entries
                .map { it.locale }
                .distinct()
                .sorted()
        val joinedRows =
            EntrySearch.filterRows(
                EntrySearch.join(current.entries.filter { (it.namespace to it.key) in matchingKeys }),
                locales.toSet(),
                rowFilterBox.selectedItem as? TranslationRowFilter ?: TranslationRowFilter.ALL,
            )
        val page = EntrySearch.paginate(joinedRows, currentPage, PAGE_SIZE)
        currentPage = page.page
        updatingEntryTable = true
        try {
            entryModel.setData(page.rows, locales)
        } finally {
            updatingEntryTable = false
        }
        pageLabel.text = message("pagination.page", currentPage + 1, page.pageCount, page.totalRows)
        previousPageButton.isEnabled = currentPage > 0
        nextPageButton.isEnabled = currentPage + 1 < page.pageCount
        for (index in 0 until entryTable.columnModel.columnCount) {
            entryTable.columnModel.getColumn(index).preferredWidth =
                when (index) {
                    0 -> 130
                    1 -> 220
                    entryTable.columnModel.columnCount - 1 -> 90
                    else -> 260
                }
        }
    }

    private fun loadUsageLocationPage() {
        val target = usageLocationTarget
        val schemeId = current.activeSchemeId
        val entryIds = current.entries.filter { (it.namespace to it.key) == target }.map { it.id }
        if (target == null || schemeId == null || entryIds.isEmpty()) {
            clearUsageLocationTarget()
            return
        }
        usageLocationLoadJob?.cancel()
        currentUsageLocations = UsageLocationPageDto()
        renderUsageLocationTable()
        val requestedPage = currentUsagePage
        usageLocationLoadJob =
            scope.launch {
                try {
                    val result = repository.usageLocations(schemeId, entryIds, requestedPage, PAGE_SIZE)
                    withContext(Dispatchers.EDT) {
                        if (usageLocationTarget != target || current.activeSchemeId != schemeId) return@withContext
                        currentUsageLocations = result
                        currentUsagePage = result.page
                        renderUsageLocationTable()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    withContext(Dispatchers.EDT) { showError(error.message ?: message("error.action.failed")) }
                }
            }
    }

    private fun renderUsageLocationTable() {
        val target = usageLocationTarget
        usageLocationModel.items =
            if (target == null) {
                emptyList()
            } else {
                currentUsageLocations.items.map { location ->
                    UsageLocationRow(
                        location.entryId,
                        target.first,
                        target.second,
                        location.filePath,
                        location.offset,
                        location.line,
                        location.column,
                        location.occurrenceCount,
                    )
                }
            }
        usagePageLabel.text =
            message(
                "pagination.page",
                currentUsageLocations.page + 1,
                currentUsageLocations.pageCount,
                currentUsageLocations.totalItems,
            )
        usagePreviousPageButton.isEnabled = currentUsagePage > 0
        usageNextPageButton.isEnabled = currentUsagePage + 1 < currentUsageLocations.pageCount
        usageLocationWarning.text = if (currentUsageLocations.truncated) message("usage.locations.truncated") else ""
        listOf(130, 220, 520, 70, 70, 90).forEachIndexed { index, width ->
            usageLocationTable.columnModel.getColumn(index).preferredWidth = width
        }
    }

    private fun clearUsageLocationTarget() {
        usageLocationLoadJob?.cancel()
        usageLocationLoadJob = null
        usageLocationTarget = null
        currentUsagePage = 0
        currentUsageLocations = UsageLocationPageDto()
        renderUsageLocationTable()
        if (tabs.tabCount > USAGE_LOCATIONS_TAB_INDEX) tabs.setEnabledAt(USAGE_LOCATIONS_TAB_INDEX, false)
    }

    private fun openSelectedUsageLocation() {
        if (usageLocationTable.selectedRowCount != 1) return showError(message("error.select.usage.location"))
        val modelRow = usageLocationTable.convertRowIndexToModel(usageLocationTable.selectedRow)
        val location = usageLocationModel.items.getOrNull(modelRow) ?: return
        if (location.line <= 0 || location.column <= 0) {
            val schemeId = current.activeSchemeId ?: return showError(message("error.no.active.scheme"))
            runAction {
                val resolved = repository.resolveUsageLocation(schemeId, location.entryId, location.filePath, location.offset)
                withContext(Dispatchers.EDT) {
                    currentUsageLocations =
                        currentUsageLocations.copy(
                            items =
                                currentUsageLocations.items.map {
                                    if (it.entryId == resolved.entryId && it.filePath == resolved.filePath && it.offset == resolved.offset) resolved else it
                                },
                        )
                    renderUsageLocationTable()
                    navigateToUsageLocation(resolved.filePath, resolved.line, resolved.column)
                }
            }
            return
        }
        navigateToUsageLocation(location.filePath, location.line, location.column)
    }

    private fun navigateToUsageLocation(
        filePath: String,
        line: Int,
        column: Int,
    ) {
        val file =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.replace('\\', '/'))
                ?: return showError(message("error.file.not.found", filePath))
        OpenFileDescriptor(project, file, line - 1, column - 1).navigate(true)
    }

    private fun createScheme() {
        val descriptor =
            FileChooserDescriptor(true, false, false, false, false, true).withTitle(message("chooser.language.files")).withFileFilter {
                it.extension?.lowercase() in
                    setOf("json", "yaml", "yml", "php", "properties")
            }
        FileChooserFactory.getInstance().createFileChooser(descriptor, project, this).choose(project).takeIf { it.isNotEmpty() }?.let { files ->
            val name =
                Messages
                    .showInputDialog(
                        project,
                        message("dialog.scheme.name.prompt"),
                        message("dialog.scheme.add.title"),
                        null,
                    )?.trim()
                    .orEmpty()
            if (name.isNotEmpty()) {
                runAction {
                    repository.createScheme(
                        name,
                        files.map { it.path },
                        LanguageManagerSettings.getInstance().defaultUsageSettings(project.basePath),
                    )
                }
            }
        }
    }

    private fun createSchemeFromFolder() {
        val descriptor =
            FileChooserDescriptor(false, true, false, false, false, true)
                .withTitle(message("chooser.language.folder"))
        val folders =
            FileChooserFactory
                .getInstance()
                .createFileChooser(descriptor, project, this)
                .choose(project)
                .map { it.path }
                .distinct()
        if (folders.isEmpty()) return
        val defaultSettings = LanguageManagerSettings.getInstance().defaultUsageSettings(project.basePath)
        runAction {
            val discovery = repository.discoverLanguageFiles(folders, defaultSettings)
            val selection =
                withContext(Dispatchers.EDT) {
                    if (discovery.files.isEmpty()) {
                        Messages.showInfoMessage(project, message("folder.discovery.none"), message("folder.discovery.none.title"))
                        null
                    } else {
                        FolderSchemeDialog(project, discovery) { folderPaths, completed ->
                            scope.launch {
                                val result = runCatching { repository.discoverLanguageFiles(folderPaths, defaultSettings) }
                                withContext(Dispatchers.EDT) { completed(result) }
                            }
                        }.let { dialog ->
                            if (dialog.showAndGet()) dialog.selection() else null
                        }
                    }
                }
            if (selection != null) {
                repository.createScheme(
                    selection.name,
                    selection.files,
                    defaultSettings,
                )
            }
        }
    }

    private fun deleteScheme() {
        val scheme =
            current.schemes.firstOrNull { it.id == current.activeSchemeId } ?: return
        confirm(message("confirm.scheme.delete", scheme.name)) { runAction { repository.deleteScheme(scheme.id) } }
    }

    private fun editSchemeSettings() {
        val scheme = activeScheme() ?: return showError(message("error.no.active.scheme"))
        val dialog = SchemeUsageSettingsDialog(project, scheme)
        if (dialog.showAndGet()) runAction { repository.updateSchemeUsageSettings(scheme.id, dialog.result()) }
    }

    private fun exportSchemeSettings() {
        if (current.schemes.isEmpty()) return showError(message("scheme.transfer.export.empty"))
        val descriptor =
            FileSaverDescriptor(
                message("scheme.transfer.export.title"),
                message("scheme.transfer.export.description"),
                "json",
            )
        val base = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val target =
            FileChooserFactory
                .getInstance()
                .createSaveFileDialog(descriptor, project)
                .save(base, "language-manager-schemes.json") ?: return
        runAction {
            val content = repository.exportSchemeSettings()
            withContext(Dispatchers.IO) { SchemeSettingsLocalFileAccess.write(target.file.toPath(), content) }
            withContext(Dispatchers.EDT) {
                Messages.showInfoMessage(
                    project,
                    message("scheme.transfer.export.success", current.schemes.size),
                    message("scheme.transfer.export.title"),
                )
            }
        }
    }

    private fun importSchemeSettings() {
        val descriptor =
            FileChooserDescriptor(true, false, false, false, false, false)
                .withTitle(message("scheme.transfer.import.title"))
                .withFileFilter { it.extension?.equals("json", true) == true }
        FileChooserFactory.getInstance().createFileChooser(descriptor, project, this).choose(project).singleOrNull()?.let { source ->
            runAction {
                val content = withContext(Dispatchers.IO) { SchemeSettingsLocalFileAccess.read(source) }
                val preview = repository.previewSchemeSettingsImport(content)
                val accepted = withContext(Dispatchers.EDT) { SchemeImportPreviewDialog(project, preview).showAndGet() }
                if (!accepted) return@runAction
                repository.importSchemeSettings(content)
                withContext(Dispatchers.EDT) {
                    Messages.showInfoMessage(
                        project,
                        message("scheme.transfer.import.success", preview.schemes.size),
                        message("scheme.transfer.import.title"),
                    )
                }
            }
        }
    }

    private fun addEntry() {
        val scheme = activeScheme() ?: return
        showEntryDialog(null, scheme)
    }

    private fun addLocaleVersion() {
        val scheme = activeScheme() ?: return showError(message("error.no.active.scheme"))
        val locales =
            current.entries
                .map { it.locale }
                .filter(String::isNotBlank)
                .distinct()
                .sorted()
        if (locales.isEmpty()) return showError(message("error.locale.version.no.source"))
        val sourceLocale = ComboBox(locales.toTypedArray())
        val targetLocale = LocaleCodeField().apply { toolTipText = message("field.locale.version.autocomplete.help") }
        val targetLocaleNote = JBTextField().apply { emptyText.text = message("field.locale.version.note.example") }
        val panel =
            JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                add(JBLabel(message("field.locale.version.source")))
                add(sourceLocale)
                add(Box.createVerticalStrut(6))
                add(
                    JPanel(GridLayout(1, 2, JBUI.scale(8), 0)).apply {
                        add(
                            JPanel(BorderLayout(0, JBUI.scale(4))).apply {
                                add(JBLabel(message("field.locale.version.target")), BorderLayout.NORTH)
                                add(targetLocale, BorderLayout.CENTER)
                            },
                        )
                        add(
                            JPanel(BorderLayout(0, JBUI.scale(4))).apply {
                                add(JBLabel(message("field.locale.version.note")), BorderLayout.NORTH)
                                add(targetLocaleNote, BorderLayout.CENTER)
                            },
                        )
                    },
                )
                add(Box.createVerticalStrut(4))
                add(JBLabel(message("field.locale.version.autocomplete.help")))
            }
        if (JOptionPane.showConfirmDialog(
                this,
                panel,
                message("dialog.locale.version.title"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE,
            ) != JOptionPane.OK_OPTION
        ) {
            return
        }
        val request = LocaleVersionRequestDto(sourceLocale.selectedItem.toString(), targetLocale.localeCode, targetLocaleNote.text.trim())
        runAction {
            val preview = repository.previewLocaleVersion(scheme.id, request)
            val accepted =
                withContext(Dispatchers.EDT) {
                    val disposable = Disposer.newDisposable("Language Manager locale version preview")
                    try {
                        ChangePreviewDialog(
                            project,
                            preview,
                            message("summary.locale.version", request.sourceLocale, request.targetLocale, preview.files.size),
                            disposable,
                        ).showAndGet()
                    } finally {
                        Disposer.dispose(disposable)
                    }
                }
            if (accepted) {
                repository.createLocaleVersion(
                    scheme.id,
                    request,
                    preview.files.associate { it.filePath to it.beforeSha256 },
                )
            }
        }
    }

    private fun editEntry() {
        val row = selectedRows().singleOrNull() ?: return showError(message("error.select.translation.key"))
        val scheme = activeScheme() ?: return
        showEntryDialog(row, scheme)
    }

    private fun showEntryDialog(
        row: JoinedTranslationRow?,
        scheme: LanguageSchemeDto,
    ) {
        val dialog = MultiLanguageEntryDialog(project, scheme, current.entries, row)
        if (dialog.showAndGet()) runAction { repository.saveAll(scheme.id, dialog.mutations()) }
    }

    private fun deleteSelected() {
        val deletion = EntrySearch.deletionFor(selectedRows())
        if (deletion.rowCount == 0) return showError(message("error.select.entry"))
        confirm(message("confirm.entries.delete", deletion.rowCount)) {
            runAction { repository.delete(activeId(), deletion.entryIds) }
        }
    }

    private fun renameKey() {
        val row =
            selectedRows().singleOrNull() ?: return showError(message("error.select.rename.source"))
        val dialog = RenameKeyDialog(project, row.key)
        if (!dialog.showAndGet()) return
        val request =
            RenameKeyRequestDto(
                namespace = row.namespace,
                oldKey = row.key,
                newKey = dialog.newKey,
                syncUsageLocations = dialog.syncUsageLocations,
            )
        if (!request.syncUsageLocations) {
            runAction { repository.rename(activeId(), row.namespace, row.key, request.newKey) }
            return
        }
        runAction {
            val schemeId = activeId()
            val preview = repository.previewRename(schemeId, request)
            if (preview.files.isEmpty()) return@runAction
            val editedFiles =
                withContext(Dispatchers.EDT) {
                    val disposable = Disposer.newDisposable("Language Manager editable rename preview")
                    try {
                        val previewDialog =
                            ChangePreviewDialog(
                                project,
                                preview,
                                message("summary.rename.sync", row.key, request.newKey),
                                disposable,
                                editableAfterEnabled = true,
                            )
                        if (previewDialog.showAndGet()) previewDialog.editedFiles() else null
                    } finally {
                        Disposer.dispose(disposable)
                    }
                } ?: return@runAction
            repository.applyPreviewedRename(
                schemeId,
                request,
                editedFiles,
                preview.files.associate { it.filePath to it.beforeSha256 },
            )
        }
    }

    private fun copyKeysToLocaleValues() {
        val rows = selectedRows()
        if (rows.isEmpty()) return showError(message("error.select.entry"))
        val locales = entryModel.locales()
        if (locales.isEmpty()) return showError(message("error.translation.targets.none"))
        val dialog = TargetLocaleDialog(project, locales, rows.size)
        if (!dialog.showAndGet()) return
        val locale = dialog.locale
        val mutations =
            rows.map { row ->
                mutationFor(row, locale, row.key)
                    ?: return showError(message("error.locale.file.not.found", locale))
            }
        confirm(message("confirm.copy.key.to.locale", rows.size, locale)) {
            previewAndApplyMutations(mutations, message("summary.copy.key.to.locale", rows.size, locale))
        }
    }

    private fun translateSelectedWithAi() {
        val scheme = activeScheme() ?: return showError(message("error.no.active.scheme"))
        val rows = selectedRows()
        if (rows.isEmpty()) return showError(message("error.select.entry"))
        if (rows.size > 100) return showError(message("error.ai.batch.limit", 100))
        val settings = LanguageManagerSettings.getInstance()
        val token = AiProviderCredentialStore.getToken()
        if (settings.aiEndpoint.isBlank() || settings.aiModel.isBlank() || token.isBlank()) {
            return showError(message("error.ai.settings.required"))
        }
        val locales = entryModel.locales()
        if (locales.isEmpty()) return showError(message("error.ai.locales.required"))
        val dialog = AiTranslationRequestDialog(project, locales, rows, scheme.localeNotes)
        if (!dialog.showAndGet()) return
        if (rows.size == 1) {
            NotificationGroupManager
                .getInstance()
                .getNotificationGroup("LanguageManager")
                .createNotification(message("notification.ai.single.record"), NotificationType.INFORMATION)
                .notify(project)
        }
        val items =
            rows.mapIndexed { index, row ->
                AiTranslationItemDto("item$index", row.namespace, row.key, dialog.sourceValues[index])
            }
        val initialRequests =
            dialog.targetLocales.associateWith { targetLocale ->
                AiTranslationRequestDto(
                    settings.aiProvider,
                    settings.aiEndpoint,
                    settings.aiModel,
                    token,
                    dialog.sourceIdentifier,
                    targetLocale,
                    items,
                    temperature = settings.aiTemperature.toDoubleOrNull(),
                    sourceLocaleNote = dialog.sourceLocale?.let(scheme.localeNotes::get).orEmpty(),
                    targetLocaleNote = scheme.localeNotes[targetLocale].orEmpty(),
                )
            }
        runAction {
            var requests = initialRequests
            translationLoop@ while (true) {
                val suggestions = requests.mapValues { (_, request) -> repository.translateWithAi(request).suggestions }
                val values =
                    withContext(Dispatchers.EDT) {
                        AiTranslationReviewDialog(project, rows, suggestions).takeIf { it.showAndGet() }?.values()
                    } ?: return@runAction
                val reviewed =
                    values.mapValues { (_, localeValues) ->
                        rows.indices.map { index -> AiTranslationSuggestionDto("item$index", localeValues.getValue("item$index")) }
                    }
                val mutations =
                    dialog.targetLocales.flatMap { targetLocale ->
                        rows.mapIndexed { index, row ->
                            mutationFor(row, targetLocale, values.getValue(targetLocale).getValue("item$index"))
                                ?: error(message("error.locale.file.not.found", targetLocale))
                        }
                    }
                val schemeId = scheme.id
                val preview = repository.previewEntryMutations(schemeId, mutations)
                if (preview.files.isEmpty()) return@runAction
                previewLoop@ while (true) {
                    val decision =
                        withContext(Dispatchers.EDT) {
                            val disposable = Disposer.newDisposable("Language Manager AI translation preview")
                            try {
                                ChangePreviewDialog(
                                    project,
                                    preview,
                                    message("summary.ai.translation.multi", mutations.size, dialog.targetLocales.joinToString(", ")),
                                    disposable,
                                    aiFeedbackEnabled = true,
                                ).also { it.show() }.decision
                            } finally {
                                Disposer.dispose(disposable)
                            }
                        }
                    when (decision) {
                        ChangePreviewDecision.APPLY -> {
                            repository.applyPreviewedEntryMutations(
                                schemeId,
                                mutations,
                                preview.files.associate { it.filePath to it.beforeSha256 },
                            )
                            return@runAction
                        }

                        ChangePreviewDecision.AI_FEEDBACK -> {
                            val feedback =
                                withContext(Dispatchers.EDT) {
                                    AiTranslationFeedbackDialog(project).takeIf { it.showAndGet() }?.value
                                } ?: continue@previewLoop
                            requests =
                                initialRequests.mapValues { (locale, request) ->
                                    request.copy(previousSuggestions = reviewed.getValue(locale), userFeedback = feedback)
                                }
                            continue@translationLoop
                        }

                        ChangePreviewDecision.CANCEL -> {
                            return@runAction
                        }
                    }
                }
            }
        }
    }

    private fun mutationFor(
        row: JoinedTranslationRow,
        locale: String,
        value: String,
    ): EntryMutationDto? {
        val existing = row.translations.firstOrNull { it.locale == locale }
        val target =
            TranslationEditorSupport
                .targets(activeScheme() ?: return null, current.entries)
                .firstOrNull { it.locale == locale && it.namespace == row.namespace }
                ?: return null
        return EntryMutationDto(existing?.id, existing?.filePath ?: target.filePath, locale, row.namespace, row.key, value)
    }

    private fun previewAndApplyMutations(
        mutations: List<EntryMutationDto>,
        summary: String,
    ) {
        runAction { previewAndApplyMutationsInAction(mutations, summary) }
    }

    private suspend fun previewAndApplyMutationsInAction(
        mutations: List<EntryMutationDto>,
        summary: String,
    ) {
        val schemeId = activeId()
        val preview = repository.previewEntryMutations(schemeId, mutations)
        if (preview.files.isEmpty()) return
        val accepted =
            withContext(Dispatchers.EDT) {
                val disposable = Disposer.newDisposable("Language Manager entry mutation preview")
                try {
                    ChangePreviewDialog(project, preview, summary, disposable).showAndGet()
                } finally {
                    Disposer.dispose(disposable)
                }
            }
        if (accepted) {
            repository.applyPreviewedEntryMutations(
                schemeId,
                mutations,
                preview.files.associate { it.filePath to it.beforeSha256 },
            )
        }
    }

    private fun findSelectedKeyInProject() {
        val row = selectedRows().singleOrNull() ?: return showError(message("error.select.translation.cell"))
        openFindInFiles(EntrySearch.findInFilesQuery(row), regex = false)
    }

    private fun findSelectedKeyWithUsageRegex() {
        val row = selectedRows().singleOrNull() ?: return showError(message("error.select.translation.cell"))
        val scheme = activeScheme() ?: return showError(message("error.no.active.scheme"))
        val query =
            EntrySearch.usageRegexFindInFilesQuery(row, scheme.usageScanSettings.regexPatterns)
                ?: return showError(message("error.find.usage.regex.unavailable"))
        openFindInFiles(query, regex = true)
    }

    private fun openFindInFiles(
        query: String,
        regex: Boolean,
    ) {
        val model =
            FindManager.getInstance(project).findInProjectModel.clone().apply {
                isReplaceState = false
                isRegularExpressions = regex
                stringToFind = query
                isMultipleFiles = true
            }
        val dataContext = DataManager.getInstance().getDataContext(entryTable)
        FindInProjectManager.getInstance(project).findInProject(dataContext, model)
    }

    private fun handleIssue(issue: LanguageIssueDto) {
        when (issue.code) {
            "MISSING_VALUE" -> {
                entryFor(issue)?.let { entry ->
                    previewAndApply(
                        ChangePreviewRequestDto(repairEntryIds = listOf(entry.id)),
                        message("summary.repair.empty", entry.locale, entry.key),
                    )
                }
                    ?: showError(message("error.issue.entry.not.found"))
            }

            "UNUSED_KEY" -> {
                entryFor(issue)?.let { entry ->
                    previewAndApply(
                        ChangePreviewRequestDto(deleteEntryIds = listOf(entry.id)),
                        message("summary.delete.unused", entry.locale, entry.namespace, entry.key),
                    )
                } ?: showError(message("error.issue.entry.not.found.short"))
            }

            "PARSE_ERROR", "READ_ERROR" -> {
                openIssueFile(issue)
            }

            else -> {
                locateIssue(issue)
            }
        }
    }

    private fun handleSelectedIssues() {
        val issues = selectedIssues()
        if (issues.isEmpty()) return showError(message("error.select.issue"))
        handleIssuesBulk(issues)
    }

    private fun handleAllRepairableIssues() {
        val issues = displayedIssues(current.issues).filter { it.repairable || it.code == "UNUSED_KEY" }
        if (issues.isEmpty()) return showError(message("error.no.bulk.issues"))
        handleIssuesBulk(issues)
    }

    private fun handleIssuesBulk(issues: List<LanguageIssueDto>) {
        val missingIds =
            issues
                .filter { it.code == "MISSING_VALUE" }
                .mapNotNull(::entryFor)
                .map { it.id }
                .distinct()
        val unusedIds =
            issues
                .filter { it.code == "UNUSED_KEY" }
                .mapNotNull(::entryFor)
                .map { it.id }
                .distinct()
        val manualCount = issues.size - issues.count { it.code == "MISSING_VALUE" || it.code == "UNUSED_KEY" }
        if (missingIds.isEmpty() && unusedIds.isEmpty()) return showError(message("error.manual.only"))
        val summary =
            buildString {
                append(message("summary.bulk.repair", missingIds.size))
                if (unusedIds.isNotEmpty()) append(message("summary.bulk.delete", unusedIds.size))
                if (manualCount > 0) append(message("summary.bulk.manual", manualCount))
            }
        previewAndApply(ChangePreviewRequestDto(repairEntryIds = missingIds, deleteEntryIds = unusedIds), summary)
    }

    private fun displayedIssues(issues: List<LanguageIssueDto>): List<LanguageIssueDto> {
        val settings = LanguageManagerSettings.getInstance()
        return visibleIssues(issues, settings.ignoreDuplicateValueIssues, settings.ignoreUnusedKeyIssues)
    }

    private fun previewAndApply(
        request: ChangePreviewRequestDto,
        summary: String,
    ) {
        val schemeId = current.activeSchemeId ?: return showError(message("error.no.active.scheme"))
        runAction {
            val preview = repository.previewChanges(schemeId, request)
            if (preview.files.isEmpty()) {
                withContext(
                    Dispatchers.EDT,
                ) { Messages.showInfoMessage(project, message("info.no.changes"), message("info.no.changes.title")) }
                return@runAction
            }
            val accepted =
                withContext(Dispatchers.EDT) {
                    val disposable = Disposer.newDisposable("Language Manager change preview")
                    try {
                        ChangePreviewDialog(project, preview, summary, disposable).showAndGet()
                    } finally {
                        Disposer.dispose(disposable)
                    }
                }
            if (accepted) {
                repository.applyPreviewedChanges(schemeId, request, preview.files.associate { it.filePath to it.beforeSha256 })
            }
        }
    }

    private fun entryFor(issue: LanguageIssueDto): LanguageEntryDto? =
        current.entries.firstOrNull {
            it.filePath == issue.filePath && it.key == issue.key
        }

    private fun selectedIssues(): List<LanguageIssueDto> =
        issueTable.selectedRows.toList().mapNotNull { row ->
            issueModel.items.getOrNull(issueTable.convertRowIndexToModel(row))
        }

    private fun locateIssue(issue: LanguageIssueDto) {
        if (issue.key.isBlank()) return openIssueFile(issue)
        localeBox.selectedItem = message("filter.locale.all")
        searchMode.selectedItem = SearchMode.EXACT
        searchField.text = issue.key
        tabs.selectedIndex = 0
    }

    private fun openIssueFile(issue: LanguageIssueDto) {
        if (issue.filePath.isBlank()) return showError(message("error.issue.no.file"))
        val file =
            LocalFileSystem.getInstance().refreshAndFindFileByPath(issue.filePath.replace('\\', '/'))
                ?: return showError(message("error.file.not.found", issue.filePath))
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun installClipboardActions(
        table: JTable,
        allowPaste: Boolean,
    ) {
        table.inputMap.put(KeyStroke.getKeyStroke("ctrl C"), "languageManager.copyCells")
        table.actionMap.put(
            "languageManager.copyCells",
            object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent?) = copySelectedCells(table)
            },
        )
        if (allowPaste) {
            table.inputMap.put(KeyStroke.getKeyStroke("ctrl V"), "languageManager.pasteCell")
            table.actionMap.put(
                "languageManager.pasteCell",
                object : AbstractAction() {
                    override fun actionPerformed(event: ActionEvent?) = pasteIntoSelectedTranslationCell()
                },
            )
        }
    }

    private fun copySelectedCells(table: JTable) {
        val viewRows = table.selectedRows.sorted()
        val viewColumns = table.selectedColumns.sorted()
        if (viewRows.isEmpty() || viewColumns.isEmpty()) return
        val text =
            if (viewRows.size == 1 && viewColumns.size == 1) {
                table.getValueAt(viewRows.first(), viewColumns.first())?.toString().orEmpty()
            } else {
                viewRows.joinToString("\n") { row ->
                    viewColumns.joinToString("\t") { column -> clipboardEscape(table.getValueAt(row, column)?.toString().orEmpty()) }
                }
            }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        status.text = message("status.copied", viewRows.size * viewColumns.size)
    }

    private fun clipboardEscape(value: String): String =
        if (value.any { it == '\t' || it == '\n' || it == '\r' || it == '"' }) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }

    private fun pasteIntoSelectedTranslationCell() {
        if (entryTable.selectedRowCount != 1 || entryTable.selectedColumnCount != 1) {
            return showError(message("error.paste.single.cell"))
        }
        val modelRow = entryTable.convertRowIndexToModel(entryTable.selectedRow)
        val modelColumn = entryTable.convertColumnIndexToModel(entryTable.selectedColumn)
        val row = entryModel.items.getOrNull(modelRow) ?: return
        val locale = entryModel.localeAt(modelColumn) ?: return showError(message("error.paste.value.only"))
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) return showError(message("error.clipboard.no.text"))
        val value = clipboard.getData(DataFlavor.stringFlavor) as? String ?: return showError(message("error.clipboard.no.text"))
        val existing = row.translations.firstOrNull { it.locale == locale }
        val targetFile =
            existing?.filePath
                ?: current.entries.firstOrNull { it.locale == locale && it.namespace == row.namespace }?.filePath
                ?: current.entries.firstOrNull { it.locale == locale }?.filePath
                ?: return showError(message("error.locale.file.not.found", locale))
        val mutation = EntryMutationDto(existing?.id, targetFile, locale, row.namespace, row.key, value)
        runAction { repository.save(activeId(), mutation) }
    }

    private fun selectedRows(): List<JoinedTranslationRow> =
        entryTable.selectedRows.toList().mapNotNull { row -> entryModel.items.getOrNull(entryTable.convertRowIndexToModel(row)) }

    private fun activeScheme() = current.schemes.firstOrNull { it.id == current.activeSchemeId }

    private fun activeId() = current.activeSchemeId ?: error(message("error.no.active.scheme"))

    private fun button(
        text: String,
        action: () -> Unit,
    ) = JButton(text).apply { addActionListener { action() } }

    private fun actionDropdown(): JButton {
        val menu =
            JPopupMenu().apply {
                listOf(
                    message("action.add") to ::addEntry,
                    message("action.locale.version.add") to ::addLocaleVersion,
                    message("action.edit") to ::editEntry,
                    message("action.delete.bulk") to ::deleteSelected,
                    message("action.rename") to ::renameKey,
                    message("action.copy.key.to.locale") to ::copyKeysToLocaleValues,
                    message("action.ai.translate") to ::translateSelectedWithAi,
                    message("action.find.in.ide") to ::findSelectedKeyInProject,
                    message("action.find.in.ide.usage.regex") to ::findSelectedKeyWithUsageRegex,
                ).forEach { (label, action) -> add(JMenuItem(label).apply { addActionListener { action() } }) }
            }
        return JButton(message("action.dropdown")).apply {
            toolTipText = message("action.dropdown.tooltip")
            addActionListener { menu.show(this, 0, height) }
        }
    }

    private fun schemeCreationDropdown(): JButton {
        val menu =
            JPopupMenu().apply {
                add(JMenuItem(message("action.scheme.by.files")).apply { addActionListener { createScheme() } })
                add(JMenuItem(message("action.scheme.by.folder")).apply { addActionListener { createSchemeFromFolder() } })
                addSeparator()
                add(JMenuItem(message("action.scheme.import.settings")).apply { addActionListener { importSchemeSettings() } })
                add(JMenuItem(message("action.scheme.export.settings")).apply { addActionListener { exportSchemeSettings() } })
            }
        return JButton(message("button.scheme.add.dropdown")).apply {
            addActionListener { menu.show(this, 0, height) }
        }
    }

    private fun confirm(
        text: String,
        action: () -> Unit,
    ) {
        if (Messages.showYesNoDialog(project, text, message("dialog.confirm.title"), Messages.getQuestionIcon()) ==
            Messages.YES
        ) {
            action()
        }
    }

    private fun showError(text: String) {
        Messages.showErrorDialog(project, text.take(500), message("dialog.confirm.title"))
    }

    private fun runAction(action: suspend () -> Unit) {
        val operationId = ++nextOperationId
        runningOperations += operationId
        refreshStatus()
        scope.launch {
            try {
                action()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.EDT) {
                    showError(e.message ?: message("error.action.failed"))
                    status.text =
                        message("error.action.failed")
                }
            } finally {
                withContext(NonCancellable + Dispatchers.EDT) {
                    runningOperations -= operationId
                    refreshStatus()
                }
            }
        }
    }

    private fun runSchemeLoad(
        schemeId: String,
        title: String,
        action: suspend () -> Unit,
    ) {
        val taskId = ++schemeLoadTaskId
        schemeLoadIndicator?.cancel()
        schemeLoadIndicator = null
        schemeLoadJob?.cancel(CancellationException("Superseded by a newer scheme load"))
        schemeLoadJob = null
        schemeLoadPending = true
        schemeLoadObservedBusy = false
        refreshStatus()
        object : Task.Backgroundable(project, title, true) {
            override fun run(indicator: ProgressIndicator) {
                if (taskId != schemeLoadTaskId) {
                    indicator.cancel()
                    return
                }
                schemeLoadIndicator = indicator
                val job =
                    scope.launch(start = CoroutineStart.LAZY) {
                        try {
                            coroutineScope {
                                val reportingJob =
                                    launch {
                                        loadProgressState
                                            .filter { it.schemeId == schemeId }
                                            .collect { progress -> updateProgressIndicator(indicator, progress) }
                                    }
                                val cancellationJob =
                                    launch {
                                        while (isActive) {
                                            indicator.checkCanceled()
                                            delay(100)
                                        }
                                    }
                                try {
                                    action()
                                } finally {
                                    reportingJob.cancelAndJoin()
                                    cancellationJob.cancelAndJoin()
                                }
                            }
                        } catch (error: ProcessCanceledException) {
                            throw CancellationException("Scheme load cancelled", error)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            withContext(Dispatchers.EDT) {
                                showError(error.message ?: message("error.action.failed"))
                                status.text = message("error.action.failed")
                            }
                        } finally {
                            withContext(NonCancellable + Dispatchers.EDT) {
                                if (taskId == schemeLoadTaskId) {
                                    schemeLoadJob = null
                                    schemeLoadIndicator = null
                                    schemeLoadPending = false
                                    schemeLoadObservedBusy = false
                                    refreshStatus()
                                }
                            }
                        }
                    }
                if (taskId != schemeLoadTaskId) {
                    job.cancel(CancellationException("Superseded before task start"))
                    indicator.cancel()
                    return
                }
                schemeLoadJob = job
                job.start()
                runBlocking { job.join() }
                indicator.checkCanceled()
            }

            override fun onCancel() {
                if (taskId == schemeLoadTaskId) {
                    schemeLoadJob?.cancel(CancellationException("Cancelled from the progress indicator"))
                }
            }
        }.queue()
    }

    private fun updateProgressIndicator(
        indicator: ProgressIndicator,
        progress: LoadProgressDto,
    ) {
        indicator.text = progressStageText(progress.stage)
        indicator.text2 = progressStatusText(progress)
        indicator.isIndeterminate = progress.totalSteps <= 0
        if (progress.totalSteps > 0) {
            indicator.fraction = progress.completedSteps.toDouble().div(progress.totalSteps).coerceIn(0.0, 1.0)
        }
    }

    override fun dispose() {
        schemeLoadIndicator?.cancel()
        scope.cancel()
    }
}

private data class FolderSchemeSelection(
    val name: String,
    val files: List<String>,
)

private class FolderSchemeDialog(
    private val project: Project,
    initialDiscovery: FolderDiscoveryDto,
    private val discoverFolders: (List<String>, (Result<FolderDiscoveryDto>) -> Unit) -> Unit,
) : DialogWrapper(project, true) {
    private val folderPaths = initialDiscovery.folderPaths.toMutableList()
    private var truncated = initialDiscovery.truncated
    private var loading = false
    private val schemeName = JBTextField(suggestSchemeName(initialDiscovery.folderPaths))
    private val model = FolderCandidateTableModel(initialDiscovery.files)
    private val table = JBTable(model)
    private val summaryLabel = JBLabel()
    private val addFolderButton = JButton(message("folder.dialog.add"))

    init {
        title = message("folder.discovery.title")
        setOKButtonText(message("folder.dialog.create"))
        setCancelButtonText(message("button.cancel"))
        init()
        schemeName.document.addDocumentListener(
            object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent?) = updateOkAction()

                override fun removeUpdate(event: DocumentEvent?) = updateOkAction()

                override fun changedUpdate(event: DocumentEvent?) = updateOkAction()
            },
        )
        model.addTableModelListener { updateOkAction() }
        addFolderButton.addActionListener { addFolders() }
        updateSummary()
        updateOkAction()
    }

    fun selection() =
        FolderSchemeSelection(
            schemeName.text.trim(),
            model.rows.filter { it.selected && it.candidate.recognized }.map { it.candidate.filePath },
        )

    private fun updateOkAction() {
        isOKActionEnabled = !loading && schemeName.text.trim().isNotEmpty() && model.rows.any { it.selected && it.candidate.recognized }
    }

    private fun addFolders() {
        val descriptor =
            FileChooserDescriptor(false, true, false, false, false, true)
                .withTitle(message("chooser.language.folder"))
        val additions =
            FileChooserFactory
                .getInstance()
                .createFileChooser(descriptor, project, table)
                .choose(project)
                .map { it.path }
                .filterNot { it in folderPaths }
                .distinct()
        if (additions.isEmpty()) return
        setLoading(true)
        discoverFolders((folderPaths + additions).distinct()) { result ->
            if (!isShowing) return@discoverFolders
            result
                .onSuccess { discovery ->
                    folderPaths.clear()
                    folderPaths += discovery.folderPaths
                    truncated = discovery.truncated
                    model.replaceCandidates(discovery.files)
                    updateSummary()
                }.onFailure { error ->
                    Messages.showErrorDialog(
                        project,
                        error.message?.take(500) ?: message("error.action.failed"),
                        message("folder.discovery.add.failed.title"),
                    )
                }
            setLoading(false)
        }
    }

    private fun setLoading(value: Boolean) {
        loading = value
        addFolderButton.isEnabled = !value
        addFolderButton.text = message(if (value) "folder.dialog.add.loading" else "folder.dialog.add")
        updateOkAction()
    }

    private fun updateSummary() {
        val recognized = model.rows.count { it.candidate.recognized }
        summaryLabel.text =
            buildString {
                append(message("folder.discovery.folder.count", folderPaths.size))
                append(' ')
                append(message("folder.discovery.summary", recognized, model.rows.size))
                if (truncated) append(message("folder.discovery.truncated"))
            }
    }

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout(6, 6)).apply {
            preferredSize = Dimension(1050, 600)
            add(
                JPanel(BorderLayout(6, 4)).apply {
                    add(
                        JPanel(BorderLayout(6, 0)).apply {
                            add(JBLabel(message("folder.discovery.name")), BorderLayout.WEST)
                            add(schemeName, BorderLayout.CENTER)
                            add(addFolderButton, BorderLayout.EAST)
                        },
                        BorderLayout.NORTH,
                    )
                    add(summaryLabel, BorderLayout.SOUTH)
                },
                BorderLayout.NORTH,
            )
            table.autoCreateRowSorter = true
            table.autoResizeMode = JTable.AUTO_RESIZE_OFF
            table.columnModel.getColumn(0).preferredWidth = 70
            table.columnModel.getColumn(1).preferredWidth = 430
            table.columnModel.getColumn(2).preferredWidth = 70
            table.columnModel.getColumn(3).preferredWidth = 100
            table.columnModel.getColumn(4).preferredWidth = 120
            table.columnModel.getColumn(5).preferredWidth = 80
            table.columnModel.getColumn(6).preferredWidth = 320
            add(JBScrollPane(table), BorderLayout.CENTER)
        }

    override fun getPreferredFocusedComponent(): JComponent = schemeName

    private fun suggestSchemeName(paths: List<String>): String {
        val folders = paths.map(Path::of)
        if (folders.size == 1) {
            return folders
                .first()
                .fileName
                ?.toString()
                .orEmpty()
        }
        val commonParent = folders.mapNotNull { it.parent }.distinct().singleOrNull()
        return commonParent?.fileName?.toString() ?: folders
            .firstOrNull()
            ?.fileName
            ?.toString()
            .orEmpty()
    }
}

private data class FolderCandidateRow(
    val candidate: LanguageFileCandidateDto,
    var selected: Boolean,
)

private class FolderCandidateTableModel(
    candidates: List<LanguageFileCandidateDto>,
) : AbstractTableModel() {
    val rows = candidates.map { FolderCandidateRow(it, it.recognized) }.toMutableList()

    fun replaceCandidates(candidates: List<LanguageFileCandidateDto>) {
        val previousSelection = rows.associate { it.candidate.filePath to it.selected }
        rows.clear()
        rows +=
            candidates.map { candidate ->
                FolderCandidateRow(candidate, previousSelection[candidate.filePath] ?: candidate.recognized)
            }
        fireTableDataChanged()
    }

    override fun getRowCount() = rows.size

    override fun getColumnCount() = 7

    override fun getColumnName(column: Int) =
        when (column) {
            0 -> message("folder.table.select")
            1 -> message("folder.table.file")
            2 -> message("folder.table.format")
            3 -> message("folder.table.locale")
            4 -> message("folder.table.namespace")
            5 -> message("folder.table.entries")
            else -> message("folder.table.result")
        }

    override fun getColumnClass(columnIndex: Int): Class<*> =
        when (columnIndex) {
            0 -> Boolean::class.javaObjectType
            5 -> Int::class.javaObjectType
            else -> String::class.java
        }

    override fun isCellEditable(
        rowIndex: Int,
        columnIndex: Int,
    ) = columnIndex == 0 && rows[rowIndex].candidate.recognized

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any =
        rows[rowIndex].let { row ->
            when (columnIndex) {
                0 -> {
                    row.selected
                }

                1 -> {
                    row.candidate.filePath
                }

                2 -> {
                    row.candidate.format
                }

                3 -> {
                    row.candidate.locale
                }

                4 -> {
                    row.candidate.namespace
                }

                5 -> {
                    row.candidate.entryCount
                }

                else -> {
                    if (row.candidate.recognized) {
                        message("folder.result.recognized")
                    } else {
                        message("folder.result.failed", row.candidate.errorMessage.orEmpty())
                    }
                }
            }
        }

    override fun setValueAt(
        value: Any?,
        rowIndex: Int,
        columnIndex: Int,
    ) {
        if (!isCellEditable(rowIndex, columnIndex)) return
        rows[rowIndex].selected = value == true
        fireTableCellUpdated(rowIndex, columnIndex)
    }
}

/** Keeps cell selection semantics while letting the active Look & Feel paint selected rows. */
internal class RowHighlightTable(
    model: javax.swing.table.TableModel,
    private val tooltipForModelColumn: (Int) -> String? = { null },
) : JBTable(model) {
    override fun isCellSelected(
        row: Int,
        column: Int,
    ): Boolean = super.isCellSelected(row, column) || selectionModel.isSelectedIndex(row)

    override fun getToolTipText(event: MouseEvent): String? {
        val viewColumn = columnAtPoint(event.point)
        return if (viewColumn >= 0) tooltipForModelColumn(convertColumnIndexToModel(viewColumn)) else null
    }
}

internal class ResponsiveGridPanel(
    horizontalGap: Int,
    verticalGap: Int,
) : JPanel(
        ResponsiveGridLayout(horizontalGap, verticalGap),
    ) {
    private var lastWidth = -1

    override fun setBounds(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
    ) {
        val widthChanged = width != lastWidth
        super.setBounds(x, y, width, height)
        if (widthChanged) {
            lastWidth = width
            revalidate()
            parent?.revalidate()
        }
    }
}

internal class ResponsiveGridLayout(
    private val horizontalGap: Int,
    private val verticalGap: Int,
) : LayoutManager2 {
    override fun addLayoutComponent(
        component: Component,
        constraints: Any?,
    ) = Unit

    override fun addLayoutComponent(
        name: String?,
        component: Component?,
    ) = Unit

    override fun removeLayoutComponent(component: Component?) = Unit

    override fun invalidateLayout(target: Container?) = Unit

    override fun getLayoutAlignmentX(target: Container?) = 0f

    override fun getLayoutAlignmentY(target: Container?) = 0f

    override fun maximumLayoutSize(target: Container?) = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)

    override fun preferredLayoutSize(parent: Container): Dimension = measure(parent, useMinimumSize = false, applyBounds = false)

    override fun minimumLayoutSize(parent: Container): Dimension = measure(parent, useMinimumSize = true, applyBounds = false)

    override fun layoutContainer(parent: Container) {
        measure(parent, useMinimumSize = false, applyBounds = true)
    }

    private fun measure(
        parent: Container,
        useMinimumSize: Boolean,
        applyBounds: Boolean,
    ): Dimension {
        val insets = parent.insets
        val availableWidth =
            (parent.width.takeIf { it > 0 } ?: JBUI.scale(900))
                .minus(insets.left + insets.right)
                .coerceAtLeast(1)
        var x = insets.left
        var y = insets.top
        var rowHeight = 0
        var usedWidth = 0

        parent.components.filter(Component::isVisible).forEach { component ->
            val requested = if (useMinimumSize) component.minimumSize else component.preferredSize
            val cellWidth = requested.width.coerceAtMost(availableWidth)
            if (x > insets.left && x + cellWidth > insets.left + availableWidth) {
                x = insets.left
                y += rowHeight + verticalGap
                rowHeight = 0
            }
            if (applyBounds) component.setBounds(x, y, cellWidth, requested.height)
            x += cellWidth + horizontalGap
            rowHeight = maxOf(rowHeight, requested.height)
            usedWidth = maxOf(usedWidth, x - horizontalGap - insets.left)
        }

        return Dimension(
            usedWidth + insets.left + insets.right,
            y + rowHeight + insets.bottom,
        )
    }
}

private class EntryTableModel : AbstractTableModel() {
    var items: List<JoinedTranslationRow> = emptyList()
        private set
    private var locales: List<String> = emptyList()

    fun setData(
        rows: List<JoinedTranslationRow>,
        locales: List<String>,
    ) {
        val structureChanged = this.locales != locales
        items = rows
        this.locales = locales
        if (structureChanged) fireTableStructureChanged() else fireTableDataChanged()
    }

    fun localeAt(column: Int): String? = locales.getOrNull(column - 2)

    fun locales(): List<String> = locales.toList()

    override fun getRowCount() = items.size

    override fun getColumnCount() = locales.size + 3

    override fun getColumnName(column: Int) =
        when {
            column == 0 -> message("table.namespace")
            column == 1 -> message("table.key")
            column == columnCount - 1 -> message("table.usage")
            else -> locales[column - 2]
        }

    override fun getValueAt(
        row: Int,
        col: Int,
    ): Any =
        items[row].let { item ->
            when {
                col == 0 -> item.namespace
                col == 1 -> item.key
                col == columnCount - 1 -> item.usageCount
                else -> item.values(locales[col - 2])
            }
        }

    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex ==
            columnCount - 1
        ) {
            Int::class.javaObjectType
        } else {
            String::class.java
        }
}

private class IssueTableModel : AbstractTableModel() {
    var items: List<LanguageIssueDto> = emptyList()
        set(value) {
            field = value
            fireTableDataChanged()
        }
    private val columns get() =
        arrayOf(
            message("table.issue.severity"),
            message("table.issue.type"),
            message("table.key"),
            message("table.issue.message"),
            message("table.issue.file"),
            message("table.issue.action"),
        )

    override fun getRowCount() = items.size

    override fun getColumnCount() = 6

    override fun getColumnName(column: Int) = columns[column]

    override fun getValueAt(
        row: Int,
        col: Int,
    ): Any =
        items[row].let {
            when (col) {
                0 -> severityText(it.severity)
                1 -> issueTypeText(it.code)
                2 -> it.key
                3 -> it.message
                4 -> it.filePath
                else -> message("action.process")
            }
        }

    override fun isCellEditable(
        rowIndex: Int,
        columnIndex: Int,
    ): Boolean = columnIndex == columnCount - 1
}

private data class UsageLocationRow(
    val entryId: String,
    val namespace: String,
    val key: String,
    val filePath: String,
    val offset: Int,
    val line: Int,
    val column: Int,
    val occurrenceCount: Int,
)

private class UsageLocationTableModel : AbstractTableModel() {
    var items: List<UsageLocationRow> = emptyList()
        set(value) {
            field = value
            fireTableDataChanged()
        }

    override fun getRowCount() = items.size

    override fun getColumnCount() = 6

    override fun getColumnName(column: Int): String =
        when (column) {
            0 -> message("table.namespace")
            1 -> message("table.key")
            2 -> message("table.usage.location.file")
            3 -> message("table.usage.location.line")
            4 -> message("table.usage.location.column")
            else -> message("table.usage.location.count")
        }

    override fun getValueAt(
        row: Int,
        column: Int,
    ): Any =
        items[row].let { item ->
            when (column) {
                0 -> item.namespace
                1 -> item.key
                2 -> item.filePath
                3 -> item.line.takeIf { it > 0 }?.toString() ?: message("usage.location.pending")
                4 -> item.column.takeIf { it > 0 }?.toString() ?: message("usage.location.pending")
                else -> item.occurrenceCount
            }
        }

    override fun getColumnClass(columnIndex: Int): Class<*> =
        if (columnIndex == 5) Int::class.javaObjectType else String::class.java
}

private fun severityText(severity: IssueSeverity): String =
    when (severity) {
        IssueSeverity.INFO -> message("issue.severity.info")
        IssueSeverity.WARNING -> message("issue.severity.warning")
        IssueSeverity.ERROR -> message("issue.severity.error")
    }

private fun TranslationRowFilter.messageKey(): String =
    when (this) {
        TranslationRowFilter.ALL -> "filter.translation.all"
        TranslationRowFilter.MISSING_TRANSLATION -> "filter.translation.missing"
        TranslationRowFilter.ZERO_USAGE -> "filter.translation.zero.usage"
    }

private fun issueTypeText(code: String): String =
    when (code) {
        "MISSING_VALUE" -> message("issue.type.MISSING_VALUE")
        "DUPLICATE_KEY" -> message("issue.type.DUPLICATE_KEY")
        "DUPLICATE_VALUE" -> message("issue.type.DUPLICATE_VALUE")
        "MISSING_TRANSLATION" -> message("issue.type.MISSING_TRANSLATION")
        "UNUSED_KEY" -> message("issue.type.UNUSED_KEY")
        "PARSE_ERROR" -> message("issue.type.PARSE_ERROR")
        "READ_ERROR" -> message("issue.type.READ_ERROR")
        else -> code
    }

private class IssueActionButtonRenderer :
    JButton(message("action.process")),
    TableCellRenderer {
    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): java.awt.Component = this
}

private class IssueActionButtonEditor(
    private val table: JTable,
    private val action: (LanguageIssueDto) -> Unit,
) : AbstractCellEditor(),
    TableCellEditor {
    private val button = JButton(message("action.process"))
    private var issue: LanguageIssueDto? = null

    init {
        button.addActionListener {
            val selectedIssue = issue
            fireEditingStopped()
            if (selectedIssue != null) SwingUtilities.invokeLater { action(selectedIssue) }
        }
    }

    override fun getCellEditorValue(): Any = message("action.process")

    override fun getTableCellEditorComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        row: Int,
        column: Int,
    ): java.awt.Component {
        val model = table.model as IssueTableModel
        issue = model.items.getOrNull(table.convertRowIndexToModel(row))
        return button
    }
}

private enum class ChangePreviewDecision { APPLY, AI_FEEDBACK, CANCEL }

private class RenameKeyDialog(
    project: Project,
    private val oldKey: String,
) : DialogWrapper(project, true) {
    private val keyField = JBTextField(oldKey)
    private val syncCheckBox = JBCheckBox(message("dialog.rename.sync.usages"))

    val newKey: String
        get() = keyField.text.trim()
    val syncUsageLocations: Boolean
        get() = syncCheckBox.isSelected

    init {
        title = message("dialog.rename.title")
        init()
    }

    override fun doValidate(): ValidationInfo? =
        if (newKey.isEmpty()) ValidationInfo(message("error.rename.key.required"), keyField) else null

    override fun createCenterPanel(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8)
            add(JBLabel(message("dialog.rename.prompt", oldKey)).apply { alignmentX = Component.LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(6))
            add(
                keyField.apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                },
            )
            add(Box.createVerticalStrut(10))
            add(syncCheckBox.apply { alignmentX = Component.LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(4))
            add(JBLabel(message("dialog.rename.sync.usages.help")).apply { alignmentX = Component.LEFT_ALIGNMENT })
        }

    override fun getPreferredFocusedComponent(): JComponent = keyField
}

private class ChangePreviewDialog(
    private val project: Project,
    private val preview: ChangePreviewDto,
    private val summary: String,
    disposable: Disposable,
    private val aiFeedbackEnabled: Boolean = false,
    private val editableAfterEnabled: Boolean = false,
) : DialogWrapper(project, true) {
    private val fileSelector = ComboBox(preview.files.toTypedArray())
    private val diffPanel: DiffRequestPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)
    private val editabilityHint = JBLabel()
    var decision: ChangePreviewDecision = ChangePreviewDecision.CANCEL
        private set
    private val editedAfterContents = preview.files.associate { it.filePath to it.afterContent }.toMutableMap()
    private var currentAfterDocument: Document? = null
    private var currentAfterPath: String? = null
    private val feedbackAction =
        object : DialogWrapperAction(message("diff.ai.feedback")) {
            override fun doAction(event: ActionEvent) {
                decision = ChangePreviewDecision.AI_FEEDBACK
                close(NEXT_USER_EXIT_CODE)
            }
        }

    init {
        title = message("diff.title")
        setOKButtonText(message("diff.apply"))
        setCancelButtonText(message("button.cancel"))
        fileSelector.renderer =
            object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?,
                    value: Any?,
                    index: Int,
                    selected: Boolean,
                    focus: Boolean,
                ) = super.getListCellRendererComponent(list, (value as? FileChangePreviewDto)?.filePath ?: value, index, selected, focus)
            }
        fileSelector.addActionListener { updateDiff() }
        updateDiff()
        init()
    }

    override fun doOKAction() {
        saveCurrentEdit()
        decision = ChangePreviewDecision.APPLY
        super.doOKAction()
    }

    override fun createActions(): Array<Action> =
        if (aiFeedbackEnabled) arrayOf(okAction, feedbackAction, cancelAction) else super.createActions()

    fun editedFiles(): List<EditedFileContentDto> {
        saveCurrentEdit()
        return preview.files.map { change ->
            EditedFileContentDto(change.filePath, editedAfterContents.getValue(change.filePath))
        }
    }

    private fun saveCurrentEdit() {
        val path = currentAfterPath ?: return
        val document = currentAfterDocument ?: return
        editedAfterContents[path] = document.text
    }

    private fun updateDiff() {
        saveCurrentEdit()
        val change = fileSelector.selectedItem as? FileChangePreviewDto ?: return
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(change.filePath)
        val factory = DiffContentFactory.getInstance()
        val editable = editableAfterEnabled && change.editable
        if (editableAfterEnabled) {
            editabilityHint.text =
                message(
                    if (editable) {
                        "diff.editable.hint.source"
                    } else {
                        "diff.editable.hint.language"
                    },
                )
        }
        val afterContent =
            if (editable) {
                EditorFactory
                    .getInstance()
                    .createDocument(editedAfterContents.getValue(change.filePath))
                    .also {
                        currentAfterDocument = it
                        currentAfterPath = change.filePath
                    }
                    .let { document ->
                        factory.create(project, document, fileType)
                    }
            } else {
                currentAfterDocument = null
                currentAfterPath = null
                factory.create(project, editedAfterContents.getValue(change.filePath), fileType)
            }
        diffPanel.setRequest(
            SimpleDiffRequest(
                change.filePath,
                factory.create(project, change.beforeContent, fileType),
                afterContent,
                message("diff.before"),
                message("diff.after"),
            ),
        )
    }

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout(6, 6)).apply {
            preferredSize = Dimension(1050, 680)
            add(
                JPanel(BorderLayout(6, 4)).apply {
                    add(JBLabel(message("diff.header", summary, preview.files.size)), BorderLayout.NORTH)
                    add(fileSelector, BorderLayout.CENTER)
                    if (editableAfterEnabled) add(editabilityHint, BorderLayout.SOUTH)
                },
                BorderLayout.NORTH,
            )
            add(diffPanel.component, BorderLayout.CENTER)
        }

    override fun getPreferredFocusedComponent(): JComponent? = diffPanel.preferredFocusedComponent
}
