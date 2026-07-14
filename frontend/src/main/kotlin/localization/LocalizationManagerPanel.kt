package cg.creamgod45.localization.ui

import cg.creamgod45.CoroutineScopeHolder
import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.*
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.DiffRequestPanel
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.find.FindManager
import com.intellij.find.findInProject.FindInProjectManager
import com.intellij.ide.BrowserUtil
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.*
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

internal class LocalizationManagerPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val scope = CoroutineScopeHolder.getInstance(project).createScope("LocalizationManagerPanel")
    private val repository = LocalizationFrontendRepository(project)
    private val schemeBox = ComboBox<LanguageSchemeDto>()
    private val searchField = JBTextField()
    private val searchMode = ComboBox(SearchMode.entries.toTypedArray())
    private val localeBox = ComboBox<String>()
    private val entryModel = EntryTableModel()
    private val entryTable = JBTable(entryModel)
    private val issueModel = IssueTableModel()
    private val issueTable = JBTable(issueModel)
    private val tabs = JTabbedPane()
    private val previousPageButton = JButton(message("button.previous"))
    private val nextPageButton = JButton(message("button.next"))
    private val pageLabel = JBLabel()
    private val status = JBLabel(message("status.initial"))
    private var current = LocalizationStateDto()
    private var updatingSchemes = false
    private var currentPage = 0

    companion object {
        private const val PAGE_SIZE = 100
        private const val ISSUE_REPORT_URL = "https://github.com/creamgod45/LanguageManage/issues/new"
    }

    init {
        border = JBUI.Borders.empty(6)
        add(createHeader(), BorderLayout.NORTH)
        add(createTabs(), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
        wireEvents()
        scope.launch { repository.state.collect { state -> withContext(Dispatchers.EDT) { render(state) } } }
    }

    private fun createHeader() = JPanel(BorderLayout(6, 6)).apply {
        val schemeRow = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            add(JBLabel(message("label.scheme"))); schemeBox.preferredSize = Dimension(220, schemeBox.preferredSize.height); add(schemeBox)
            add(schemeCreationDropdown()); add(button(message("button.scheme.delete"), ::deleteScheme)); add(button(message("button.reload")) { runAction { repository.reload(activeId()) } })
            add(button(message("button.repair.normalize")) { previewAndApply(ChangePreviewRequestDto(normalizeAll = true), message("summary.repair.normalize")) })
            add(ActionLink(message("action.report.issue")) { BrowserUtil.browse(ISSUE_REPORT_URL) }.apply { setExternalLinkIcon() })
        }
        val searchRow = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            add(JBLabel(message("label.search"))); searchField.columns = 25; add(searchField)
            searchMode.renderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean) =
                    super.getListCellRendererComponent(list, when (value) { SearchMode.EXACT -> message("search.mode.exact"); else -> message("search.mode.fuzzy") }, index, selected, focus)
            }
            add(searchMode)
            add(JBLabel(message("label.language"))); localeBox.preferredSize = Dimension(110, localeBox.preferredSize.height); add(localeBox)
            add(actionDropdown())
        }
        add(schemeRow, BorderLayout.NORTH); add(searchRow, BorderLayout.SOUTH)
    }

    private fun createTabs() = tabs.apply {
        entryTable.autoCreateRowSorter = true
        entryTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        entryTable.cellSelectionEnabled = true
        entryTable.rowSelectionAllowed = true
        entryTable.columnSelectionAllowed = true
        entryTable.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        installClipboardActions(entryTable, allowPaste = true)
        val translationPanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(entryTable), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 3)).apply {
                add(JBLabel(message("pagination.limit", PAGE_SIZE)))
                add(previousPageButton)
                add(pageLabel)
                add(nextPageButton)
            }, BorderLayout.SOUTH)
        }
        addTab(message("tab.translations"), translationPanel)
        issueTable.autoCreateRowSorter = true
        issueTable.cellSelectionEnabled = true
        issueTable.rowSelectionAllowed = true
        issueTable.columnSelectionAllowed = true
        issueTable.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        installClipboardActions(issueTable, allowPaste = false)
        val issuesPanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(issueTable), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 3)).apply {
                add(button(message("button.issues.selected"), ::handleSelectedIssues))
                add(button(message("button.issues.all"), ::handleAllRepairableIssues))
            }, BorderLayout.SOUTH)
        }
        addTab(message("tab.issues"), issuesPanel)
        issueTable.columnModel.getColumn(issueModel.columnCount - 1).apply {
            preferredWidth = 80
            cellRenderer = IssueActionButtonRenderer()
            cellEditor = IssueActionButtonEditor(issueTable) { issue -> handleIssue(issue) }
        }
    }

    private fun wireEvents() {
        schemeBox.addActionListener { if (!updatingSchemes) (schemeBox.selectedItem as? LanguageSchemeDto)?.takeIf { it.id != current.activeSchemeId }?.let { scheme -> runAction { repository.activateScheme(scheme.id) } } }
        searchMode.addActionListener { applyFilter() }; localeBox.addActionListener { applyFilter() }
        searchField.document.addDocumentListener(object : DocumentListener { override fun insertUpdate(e: DocumentEvent?) = applyFilter(); override fun removeUpdate(e: DocumentEvent?) = applyFilter(); override fun changedUpdate(e: DocumentEvent?) = applyFilter() })
        previousPageButton.addActionListener { if (currentPage > 0) { currentPage--; applyFilter(resetPage = false) } }
        nextPageButton.addActionListener { currentPage++; applyFilter(resetPage = false) }
    }

    private fun render(state: LocalizationStateDto) {
        current = state
        updatingSchemes = true
        schemeBox.model = DefaultComboBoxModel(state.schemes.toTypedArray())
        schemeBox.renderer = object : DefaultListCellRenderer() { override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean) = super.getListCellRendererComponent(list, (value as? LanguageSchemeDto)?.name ?: value, index, selected, focus) }
        schemeBox.selectedItem = state.schemes.firstOrNull { it.id == state.activeSchemeId }
        updatingSchemes = false
        val allLocales = message("filter.locale.all")
        val locales = listOf(allLocales) + state.entries.map { it.locale }.distinct().sorted()
        val previousLocale = localeBox.selectedItem?.toString()
        localeBox.model = DefaultComboBoxModel(locales.toTypedArray()); localeBox.selectedItem = previousLocale?.takeIf { it in locales } ?: allLocales
        issueModel.items = state.issues
        applyFilter()
        val errors = state.issues.count { it.severity == IssueSeverity.ERROR }
        status.text = state.errorMessage ?: when { state.busy -> message("status.loading"); state.activeSchemeId == null -> message("status.no.scheme"); else -> message("status.summary", state.entries.size, state.issues.size, errors) }
    }

    private fun applyFilter(resetPage: Boolean = true) {
        if (resetPage) currentPage = 0
        val locale = localeBox.selectedItem?.toString()?.takeUnless { it == message("filter.locale.all") }
        val matches = EntrySearch.filter(current.entries, searchField.text, searchMode.selectedItem as? SearchMode ?: SearchMode.FUZZY, locale)
        val matchingKeys = matches.map { it.namespace to it.key }.toSet()
        val joinedRows = EntrySearch.join(current.entries.filter { (it.namespace to it.key) in matchingKeys })
        val page = EntrySearch.paginate(joinedRows, currentPage, PAGE_SIZE)
        currentPage = page.page
        val locales = current.entries.map { it.locale }.distinct().sorted()
        entryModel.setData(page.rows, locales)
        pageLabel.text = message("pagination.page", currentPage + 1, page.pageCount, page.totalRows)
        previousPageButton.isEnabled = currentPage > 0
        nextPageButton.isEnabled = currentPage + 1 < page.pageCount
        for (index in 0 until entryTable.columnModel.columnCount) {
            entryTable.columnModel.getColumn(index).preferredWidth = when (index) {
                0 -> 130
                1 -> 220
                entryTable.columnModel.columnCount - 1 -> 90
                else -> 260
            }
        }
    }

    private fun createScheme() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, true).withTitle(message("chooser.language.files")).withFileFilter { it.extension?.lowercase() in setOf("json", "yaml", "yml", "php") }
        FileChooserFactory.getInstance().createFileChooser(descriptor, project, this).choose(project).takeIf { it.isNotEmpty() }?.let { files ->
            val name = Messages.showInputDialog(project, message("dialog.scheme.name.prompt"), message("dialog.scheme.add.title"), null)?.trim().orEmpty()
            if (name.isNotEmpty()) runAction { repository.createScheme(name, files.map { it.path }) }
        }
    }

    private fun createSchemeFromFolder() {
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle(message("chooser.language.folder"))
        val folder = FileChooserFactory.getInstance().createFileChooser(descriptor, project, this)
            .choose(project).singleOrNull() ?: return
        runAction {
            val discovery = repository.discoverLanguageFiles(folder.path)
            val selection = withContext(Dispatchers.EDT) {
                if (discovery.files.isEmpty()) {
                    Messages.showInfoMessage(project, message("folder.discovery.none"), message("folder.discovery.none.title"))
                    null
                } else {
                    FolderSchemeDialog(project, discovery).let { dialog ->
                        if (dialog.showAndGet()) dialog.selection() else null
                    }
                }
            }
            if (selection != null) repository.createScheme(selection.name, selection.files)
        }
    }
    private fun deleteScheme() { val scheme = current.schemes.firstOrNull { it.id == current.activeSchemeId } ?: return; confirm(message("confirm.scheme.delete", scheme.name)) { runAction { repository.deleteScheme(scheme.id) } } }
    private fun addEntry() { val scheme = activeScheme() ?: return; showEntryDialog(null, scheme) }
    private fun editEntry() {
        val row = selectedRows().singleOrNull() ?: return showError(message("error.select.translation.key"))
        val choices = row.translations.map { "${it.locale} — ${it.filePath}" }
        val selected = if (choices.size == 1) choices.first() else JOptionPane.showInputDialog(
            this, message("dialog.edit.language.prompt"), message("dialog.edit.translation.title"), JOptionPane.PLAIN_MESSAGE, null, choices.toTypedArray(), choices.first()
        ) as? String ?: return
        val entry = row.translations[choices.indexOf(selected)]
        showEntryDialog(entry, activeScheme() ?: return)
    }
    private fun showEntryDialog(entry: LanguageEntryDto?, scheme: LanguageSchemeDto) {
        val file = ComboBox(scheme.files.toTypedArray()); file.selectedItem = entry?.filePath ?: scheme.files.first()
        val locale = JBTextField(); val namespace = JBTextField(); val key = JBTextField(entry?.key.orEmpty()); val value = JBTextArea(entry?.value.orEmpty(), 6, 40).apply { lineWrap = true }
        locale.isEditable = false; namespace.isEditable = false
        fun updateMetadata() {
            val path = Path.of(file.selectedItem.toString())
            val known = current.entries.firstOrNull { it.filePath == path.toString() }
            locale.text = known?.locale ?: if (path.fileName.toString().substringAfterLast('.', "").equals("php", true)) path.parent?.fileName?.toString().orEmpty() else path.fileName.toString().substringBeforeLast('.')
            namespace.text = known?.namespace ?: if (path.fileName.toString().endsWith(".php", true)) path.fileName.toString().substringBeforeLast('.') else ""
        }
        file.addActionListener { updateMetadata() }; updateMetadata()
        val panel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); listOf(message("field.file") to file, message("field.language") to locale, message("field.namespace") to namespace, message("field.key") to key, message("field.value") to JBScrollPane(value)).forEach { (label, component) -> add(JBLabel(label)); add(component as java.awt.Component); add(Box.createVerticalStrut(4)) } }
        if (JOptionPane.showConfirmDialog(this, panel, if (entry == null) message("dialog.add.translation.title") else message("dialog.edit.translation.title"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            runAction { repository.save(scheme.id, EntryMutationDto(entry?.id, file.selectedItem.toString(), locale.text, namespace.text, key.text, value.text)) }
        }
    }
    private fun deleteSelected() { val entries = selectedEntries(); if (entries.isEmpty()) return showError(message("error.select.entry")); confirm(message("confirm.entries.delete", entries.size)) { runAction { repository.delete(activeId(), entries.map { it.id }) } } }
    private fun renameKey() { val row = selectedRows().singleOrNull() ?: return showError(message("error.select.rename.source")); val value = Messages.showInputDialog(project, message("dialog.rename.prompt", row.key), message("dialog.rename.title"), null, row.key, null)?.trim() ?: return; runAction { repository.rename(activeId(), row.key, value) } }

    private fun findSelectedKeyInProject() {
        val row = selectedRows().singleOrNull() ?: return showError(message("error.select.translation.cell"))
        val fullKey = listOf(row.namespace, row.key).filter { it.isNotBlank() }.joinToString(".")
        val model = FindManager.getInstance(project).findInProjectModel.clone().apply {
            stringToFind = fullKey
            isReplaceState = false
            isRegularExpressions = false
            isMultipleFiles = true
        }
        val dataContext = DataManager.getInstance().getDataContext(entryTable)
        FindInProjectManager.getInstance(project).findInProject(dataContext, model)
    }

    private fun handleIssue(issue: LanguageIssueDto) {
        when (issue.code) {
            "MISSING_VALUE" -> entryFor(issue)?.let { entry ->
                previewAndApply(ChangePreviewRequestDto(repairEntryIds = listOf(entry.id)), message("summary.repair.empty", entry.locale, entry.key))
            }
                ?: showError(message("error.issue.entry.not.found"))
            "UNUSED_KEY" -> entryFor(issue)?.let { entry ->
                previewAndApply(ChangePreviewRequestDto(deleteEntryIds = listOf(entry.id)), message("summary.delete.unused", entry.locale, entry.namespace, entry.key))
            } ?: showError(message("error.issue.entry.not.found.short"))
            "PARSE_ERROR", "READ_ERROR" -> openIssueFile(issue)
            else -> locateIssue(issue)
        }
    }

    private fun handleSelectedIssues() {
        val issues = selectedIssues()
        if (issues.isEmpty()) return showError(message("error.select.issue"))
        handleIssuesBulk(issues)
    }

    private fun handleAllRepairableIssues() {
        val issues = current.issues.filter { it.repairable || it.code == "UNUSED_KEY" }
        if (issues.isEmpty()) return showError(message("error.no.bulk.issues"))
        handleIssuesBulk(issues)
    }

    private fun handleIssuesBulk(issues: List<LanguageIssueDto>) {
        val missingIds = issues.filter { it.code == "MISSING_VALUE" }.mapNotNull(::entryFor).map { it.id }.distinct()
        val unusedIds = issues.filter { it.code == "UNUSED_KEY" }.mapNotNull(::entryFor).map { it.id }.distinct()
        val manualCount = issues.size - issues.count { it.code == "MISSING_VALUE" || it.code == "UNUSED_KEY" }
        if (missingIds.isEmpty() && unusedIds.isEmpty()) return showError(message("error.manual.only"))
        val summary = buildString {
            append(message("summary.bulk.repair", missingIds.size))
            if (unusedIds.isNotEmpty()) append(message("summary.bulk.delete", unusedIds.size))
            if (manualCount > 0) append(message("summary.bulk.manual", manualCount))
        }
        previewAndApply(ChangePreviewRequestDto(repairEntryIds = missingIds, deleteEntryIds = unusedIds), summary)
    }

    private fun previewAndApply(request: ChangePreviewRequestDto, summary: String) {
        val schemeId = current.activeSchemeId ?: return showError(message("error.no.active.scheme"))
        runAction {
            val preview = repository.previewChanges(schemeId, request)
            if (preview.files.isEmpty()) {
                withContext(Dispatchers.EDT) { Messages.showInfoMessage(project, message("info.no.changes"), message("info.no.changes.title")) }
                return@runAction
            }
            val accepted = withContext(Dispatchers.EDT) {
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

    private fun entryFor(issue: LanguageIssueDto): LanguageEntryDto? = current.entries.firstOrNull {
        it.filePath == issue.filePath && it.key == issue.key
    }

    private fun selectedIssues(): List<LanguageIssueDto> = issueTable.selectedRows.toList().mapNotNull { row ->
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
        val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(issue.filePath.replace('\\', '/'))
            ?: return showError(message("error.file.not.found", issue.filePath))
        FileEditorManager.getInstance(project).openFile(file, true)
    }

    private fun installClipboardActions(table: JTable, allowPaste: Boolean) {
        table.inputMap.put(KeyStroke.getKeyStroke("ctrl C"), "languageManager.copyCells")
        table.actionMap.put("languageManager.copyCells", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent?) = copySelectedCells(table)
        })
        if (allowPaste) {
            table.inputMap.put(KeyStroke.getKeyStroke("ctrl V"), "languageManager.pasteCell")
            table.actionMap.put("languageManager.pasteCell", object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent?) = pasteIntoSelectedTranslationCell()
            })
        }
    }

    private fun copySelectedCells(table: JTable) {
        val viewRows = table.selectedRows.sorted()
        val viewColumns = table.selectedColumns.sorted()
        if (viewRows.isEmpty() || viewColumns.isEmpty()) return
        val text = if (viewRows.size == 1 && viewColumns.size == 1) {
            table.getValueAt(viewRows.first(), viewColumns.first())?.toString().orEmpty()
        } else {
            viewRows.joinToString("\n") { row ->
                viewColumns.joinToString("\t") { column -> clipboardEscape(table.getValueAt(row, column)?.toString().orEmpty()) }
            }
        }
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        status.text = message("status.copied", viewRows.size * viewColumns.size)
    }

    private fun clipboardEscape(value: String): String = if (value.any { it == '\t' || it == '\n' || it == '\r' || it == '"' }) {
        "\"${value.replace("\"", "\"\"")}\""
    } else value

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
        val targetFile = existing?.filePath
            ?: current.entries.firstOrNull { it.locale == locale && it.namespace == row.namespace }?.filePath
            ?: current.entries.firstOrNull { it.locale == locale }?.filePath
            ?: return showError(message("error.locale.file.not.found", locale))
        val mutation = EntryMutationDto(existing?.id, targetFile, locale, row.namespace, row.key, value)
        runAction { repository.save(activeId(), mutation) }
    }

    private fun selectedRows(): List<JoinedTranslationRow> = entryTable.selectedRows.toList().mapNotNull { row -> entryModel.items.getOrNull(entryTable.convertRowIndexToModel(row)) }
    private fun selectedEntries(): List<LanguageEntryDto> = selectedRows().flatMap { it.translations }.distinctBy { it.id }
    private fun activeScheme() = current.schemes.firstOrNull { it.id == current.activeSchemeId }
    private fun activeId() = current.activeSchemeId ?: error(message("error.no.active.scheme"))
    private fun button(text: String, action: () -> Unit) = JButton(text).apply { addActionListener { action() } }
    private fun actionDropdown(): JButton {
        val menu = JPopupMenu().apply {
            listOf(
                message("action.add") to ::addEntry,
                message("action.edit") to ::editEntry,
                message("action.delete.bulk") to ::deleteSelected,
                message("action.rename") to ::renameKey,
                message("action.find.in.ide") to ::findSelectedKeyInProject,
            ).forEach { (label, action) -> add(JMenuItem(label).apply { addActionListener { action() } }) }
        }
        return JButton(message("action.dropdown")).apply {
            toolTipText = message("action.dropdown.tooltip")
            addActionListener { menu.show(this, 0, height) }
        }
    }
    private fun schemeCreationDropdown(): JButton {
        val menu = JPopupMenu().apply {
            add(JMenuItem(message("action.scheme.by.files")).apply { addActionListener { createScheme() } })
            add(JMenuItem(message("action.scheme.by.folder")).apply { addActionListener { createSchemeFromFolder() } })
        }
        return JButton(message("button.scheme.add.dropdown")).apply {
            addActionListener { menu.show(this, 0, height) }
        }
    }
    private fun confirm(text: String, action: () -> Unit) { if (Messages.showYesNoDialog(project, text, message("dialog.confirm.title"), Messages.getQuestionIcon()) == Messages.YES) action() }
    private fun showError(text: String) { Messages.showErrorDialog(project, text.take(500), message("dialog.confirm.title")) }
    private fun runAction(action: suspend () -> Unit) {
        status.text = message("status.processing")
        scope.launch {
            try { action() }
            catch (e: Exception) {
                if (e is CancellationException) throw e
                withContext(Dispatchers.EDT) { showError(e.message ?: message("error.action.failed")); status.text = message("error.action.failed") }
            }
        }
    }
    override fun dispose() { scope.cancel() }
}

private data class FolderSchemeSelection(val name: String, val files: List<String>)

private class FolderSchemeDialog(
    project: Project,
    private val discovery: FolderDiscoveryDto,
) : DialogWrapper(project, true) {
    private val schemeName = JBTextField(Path.of(discovery.folderPath).fileName?.toString().orEmpty())
    private val model = FolderCandidateTableModel(discovery.files)
    private val table = JBTable(model)

    init {
        title = message("folder.discovery.title")
        setOKButtonText(message("folder.dialog.create"))
        setCancelButtonText(message("button.cancel"))
        init()
        schemeName.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent?) = updateOkAction()
            override fun removeUpdate(event: DocumentEvent?) = updateOkAction()
            override fun changedUpdate(event: DocumentEvent?) = updateOkAction()
        })
        model.addTableModelListener { updateOkAction() }
        updateOkAction()
    }

    fun selection() = FolderSchemeSelection(
        schemeName.text.trim(),
        model.rows.filter { it.selected && it.candidate.recognized }.map { it.candidate.filePath },
    )

    private fun updateOkAction() {
        isOKActionEnabled = schemeName.text.trim().isNotEmpty() && model.rows.any { it.selected && it.candidate.recognized }
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(6, 6)).apply {
        preferredSize = Dimension(1050, 600)
        val recognized = discovery.files.count { it.recognized }
        add(JPanel(BorderLayout(6, 4)).apply {
            add(JPanel(BorderLayout(6, 0)).apply {
                add(JBLabel(message("folder.discovery.name")), BorderLayout.WEST)
                add(schemeName, BorderLayout.CENTER)
            }, BorderLayout.NORTH)
            add(JBLabel(buildString {
                append(message("folder.discovery.summary", recognized, discovery.files.size))
                if (discovery.truncated) append(message("folder.discovery.truncated"))
            }), BorderLayout.SOUTH)
        }, BorderLayout.NORTH)
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
}

private data class FolderCandidateRow(val candidate: LanguageFileCandidateDto, var selected: Boolean)

private class FolderCandidateTableModel(candidates: List<LanguageFileCandidateDto>) : AbstractTableModel() {
    val rows = candidates.map { FolderCandidateRow(it, it.recognized) }.toMutableList()

    override fun getRowCount() = rows.size
    override fun getColumnCount() = 7
    override fun getColumnName(column: Int) = when (column) {
        0 -> message("folder.table.select")
        1 -> message("folder.table.file")
        2 -> message("folder.table.format")
        3 -> message("folder.table.locale")
        4 -> message("folder.table.namespace")
        5 -> message("folder.table.entries")
        else -> message("folder.table.result")
    }

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        0 -> Boolean::class.javaObjectType
        5 -> Int::class.javaObjectType
        else -> String::class.java
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int) = columnIndex == 0 && rows[rowIndex].candidate.recognized

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = rows[rowIndex].let { row ->
        when (columnIndex) {
            0 -> row.selected
            1 -> row.candidate.filePath
            2 -> row.candidate.format
            3 -> row.candidate.locale
            4 -> row.candidate.namespace
            5 -> row.candidate.entryCount
            else -> if (row.candidate.recognized) message("folder.result.recognized")
            else message("folder.result.failed", row.candidate.errorMessage.orEmpty())
        }
    }

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        if (!isCellEditable(rowIndex, columnIndex)) return
        rows[rowIndex].selected = value == true
        fireTableCellUpdated(rowIndex, columnIndex)
    }
}

private class EntryTableModel : AbstractTableModel() {
    var items: List<JoinedTranslationRow> = emptyList()
        private set
    private var locales: List<String> = emptyList()

    fun setData(rows: List<JoinedTranslationRow>, locales: List<String>) {
        val structureChanged = this.locales != locales
        items = rows
        this.locales = locales
        if (structureChanged) fireTableStructureChanged() else fireTableDataChanged()
    }

    fun localeAt(column: Int): String? = locales.getOrNull(column - 2)

    override fun getRowCount() = items.size
    override fun getColumnCount() = locales.size + 3
    override fun getColumnName(column: Int) = when {
        column == 0 -> message("table.namespace")
        column == 1 -> message("table.key")
        column == columnCount - 1 -> message("table.usage")
        else -> locales[column - 2]
    }
    override fun getValueAt(row: Int, col: Int): Any = items[row].let { item -> when {
        col == 0 -> item.namespace
        col == 1 -> item.key
        col == columnCount - 1 -> item.usageCount
        else -> item.values(locales[col - 2])
    } }
    override fun getColumnClass(columnIndex: Int): Class<*> = if (columnIndex == columnCount - 1) Int::class.javaObjectType else String::class.java
}
private class IssueTableModel : AbstractTableModel() {
    var items: List<LanguageIssueDto> = emptyList(); set(value) { field = value; fireTableDataChanged() }
    private val columns get() = arrayOf(message("table.issue.severity"), message("table.issue.type"), message("table.key"), message("table.issue.message"), message("table.issue.file"), message("table.issue.action"))
    override fun getRowCount() = items.size; override fun getColumnCount() = 6; override fun getColumnName(column: Int) = columns[column]
    override fun getValueAt(row: Int, col: Int): Any = items[row].let { when (col) { 0 -> severityText(it.severity); 1 -> issueTypeText(it.code); 2 -> it.key; 3 -> it.message; 4 -> it.filePath; else -> message("action.process") } }
    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = columnIndex == columnCount - 1
}

private fun severityText(severity: IssueSeverity): String = when (severity) {
    IssueSeverity.INFO -> message("issue.severity.info")
    IssueSeverity.WARNING -> message("issue.severity.warning")
    IssueSeverity.ERROR -> message("issue.severity.error")
}

private fun issueTypeText(code: String): String = when (code) {
    "MISSING_VALUE" -> message("issue.type.MISSING_VALUE")
    "DUPLICATE_KEY" -> message("issue.type.DUPLICATE_KEY")
    "DUPLICATE_VALUE" -> message("issue.type.DUPLICATE_VALUE")
    "MISSING_TRANSLATION" -> message("issue.type.MISSING_TRANSLATION")
    "UNUSED_KEY" -> message("issue.type.UNUSED_KEY")
    "PARSE_ERROR" -> message("issue.type.PARSE_ERROR")
    "READ_ERROR" -> message("issue.type.READ_ERROR")
    else -> code
}

private class IssueActionButtonRenderer : JButton(message("action.process")), TableCellRenderer {
    override fun getTableCellRendererComponent(
        table: JTable?, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
    ): java.awt.Component = this
}

private class IssueActionButtonEditor(
    private val table: JTable,
    private val action: (LanguageIssueDto) -> Unit,
) : AbstractCellEditor(), TableCellEditor {
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
        table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int,
    ): java.awt.Component {
        val model = table.model as IssueTableModel
        issue = model.items.getOrNull(table.convertRowIndexToModel(row))
        return button
    }
}

private class ChangePreviewDialog(
    private val project: Project,
    private val preview: ChangePreviewDto,
    private val summary: String,
    disposable: Disposable,
) : DialogWrapper(project, true) {
    private val fileSelector = ComboBox(preview.files.toTypedArray())
    private val diffPanel: DiffRequestPanel = DiffManager.getInstance().createRequestPanel(project, disposable, null)

    init {
        title = message("diff.title")
        setOKButtonText(message("diff.apply"))
        setCancelButtonText(message("button.cancel"))
        fileSelector.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean) =
                super.getListCellRendererComponent(list, (value as? FileChangePreviewDto)?.filePath ?: value, index, selected, focus)
        }
        fileSelector.addActionListener { updateDiff() }
        updateDiff()
        init()
    }

    private fun updateDiff() {
        val change = fileSelector.selectedItem as? FileChangePreviewDto ?: return
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(change.filePath)
        val factory = DiffContentFactory.getInstance()
        diffPanel.setRequest(SimpleDiffRequest(
            change.filePath,
            factory.create(project, change.beforeContent, fileType),
            factory.create(project, change.afterContent, fileType),
            message("diff.before"),
            message("diff.after"),
        ))
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(6, 6)).apply {
        preferredSize = Dimension(1050, 680)
        add(JPanel(BorderLayout(6, 4)).apply {
            add(JBLabel(message("diff.header", summary, preview.files.size)), BorderLayout.NORTH)
            add(fileSelector, BorderLayout.CENTER)
        }, BorderLayout.NORTH)
        add(diffPanel.component, BorderLayout.CENTER)
    }

    override fun getPreferredFocusedComponent(): JComponent? = diffPanel.preferredFocusedComponent
}
