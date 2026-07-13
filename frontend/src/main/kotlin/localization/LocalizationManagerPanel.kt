package cg.creamgod45.localization.ui

import cg.creamgod45.CoroutineScopeHolder
import cg.creamgod45.localization.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
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
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.table.AbstractTableModel

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
    private val previousPageButton = JButton("上一頁")
    private val nextPageButton = JButton("下一頁")
    private val pageLabel = JBLabel()
    private val status = JBLabel("請建立方案並明確選擇語言檔案")
    private var current = LocalizationStateDto()
    private var updatingSchemes = false
    private var currentPage = 0

    companion object {
        private const val PAGE_SIZE = 100
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
            add(JBLabel("方案")); schemeBox.preferredSize = Dimension(220, schemeBox.preferredSize.height); add(schemeBox)
            add(button("新增方案", ::createScheme)); add(button("刪除方案", ::deleteScheme)); add(button("重新讀取") { runAction { repository.reload(activeId()) } })
            add(button("修復/正規化") { confirm("將空值補為 key，並用標準格式重寫可解析檔案。要繼續嗎？") { runAction { repository.repair(activeId()) } } })
        }
        val searchRow = JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
            add(JBLabel("搜尋")); searchField.columns = 25; add(searchField); add(searchMode)
            add(JBLabel("語言")); localeBox.preferredSize = Dimension(110, localeBox.preferredSize.height); add(localeBox)
            add(button("新增", ::addEntry)); add(button("編輯", ::editEntry)); add(button("批量刪除", ::deleteSelected)); add(button("Key 改名", ::renameKey))
        }
        add(schemeRow, BorderLayout.NORTH); add(searchRow, BorderLayout.SOUTH)
    }

    private fun createTabs() = JTabbedPane().apply {
        entryTable.autoCreateRowSorter = true
        entryTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        entryTable.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        val translationPanel = JPanel(BorderLayout()).apply {
            add(JBScrollPane(entryTable), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 3)).apply {
                add(JBLabel("每頁最多 $PAGE_SIZE 筆"))
                add(previousPageButton)
                add(pageLabel)
                add(nextPageButton)
            }, BorderLayout.SOUTH)
        }
        addTab("翻譯表", translationPanel)
        issueTable.autoCreateRowSorter = true
        addTab("問題與建議", JBScrollPane(issueTable))
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
        val locales = listOf("全部") + state.entries.map { it.locale }.distinct().sorted()
        val previousLocale = localeBox.selectedItem?.toString()
        localeBox.model = DefaultComboBoxModel(locales.toTypedArray()); localeBox.selectedItem = previousLocale?.takeIf { it in locales } ?: "全部"
        issueModel.items = state.issues
        applyFilter()
        val errors = state.issues.count { it.severity == IssueSeverity.ERROR }
        status.text = state.errorMessage ?: when { state.busy -> "讀取中…"; state.activeSchemeId == null -> "尚未建立方案；插件不會自動選擇任何檔案"; else -> "${state.entries.size} 筆翻譯，${state.issues.size} 項建議，其中 $errors 項錯誤" }
    }

    private fun applyFilter(resetPage: Boolean = true) {
        if (resetPage) currentPage = 0
        val locale = localeBox.selectedItem?.toString()?.takeUnless { it == "全部" }
        val matches = EntrySearch.filter(current.entries, searchField.text, searchMode.selectedItem as? SearchMode ?: SearchMode.FUZZY, locale)
        val matchingKeys = matches.map { it.namespace to it.key }.toSet()
        val joinedRows = EntrySearch.join(current.entries.filter { (it.namespace to it.key) in matchingKeys })
        val page = EntrySearch.paginate(joinedRows, currentPage, PAGE_SIZE)
        currentPage = page.page
        val locales = current.entries.map { it.locale }.distinct().sorted()
        entryModel.setData(page.rows, locales)
        pageLabel.text = "第 ${currentPage + 1} / ${page.pageCount} 頁，共 ${page.totalRows} 筆"
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
        val descriptor = FileChooserDescriptor(true, false, false, false, false, true).withTitle("選擇語言檔案（JSON / YAML / PHP）").withFileFilter { it.extension?.lowercase() in setOf("json", "yaml", "yml", "php") }
        FileChooserFactory.getInstance().createFileChooser(descriptor, project, this).choose(project).takeIf { it.isNotEmpty() }?.let { files ->
            val name = Messages.showInputDialog(project, "方案名稱", "新增語言管理方案", null)?.trim().orEmpty()
            if (name.isNotEmpty()) runAction { repository.createScheme(name, files.map { it.path }) }
        }
    }
    private fun deleteScheme() { val scheme = current.schemes.firstOrNull { it.id == current.activeSchemeId } ?: return; confirm("刪除方案「${scheme.name}」？語言檔案本身不會被刪除。") { runAction { repository.deleteScheme(scheme.id) } } }
    private fun addEntry() { val scheme = activeScheme() ?: return; showEntryDialog(null, scheme) }
    private fun editEntry() {
        val row = selectedRows().singleOrNull() ?: return showError("請選擇一個翻譯鍵進行編輯")
        val choices = row.translations.map { "${it.locale} — ${it.filePath}" }
        val selected = if (choices.size == 1) choices.first() else JOptionPane.showInputDialog(
            this, "選擇要編輯的語言", "編輯翻譯", JOptionPane.PLAIN_MESSAGE, null, choices.toTypedArray(), choices.first()
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
        val panel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); listOf("檔案" to file, "語言" to locale, "Namespace" to namespace, "Key" to key, "Value" to JBScrollPane(value)).forEach { (label, component) -> add(JBLabel(label)); add(component as java.awt.Component); add(Box.createVerticalStrut(4)) } }
        if (JOptionPane.showConfirmDialog(this, panel, if (entry == null) "新增翻譯" else "編輯翻譯", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            runAction { repository.save(scheme.id, EntryMutationDto(entry?.id, file.selectedItem.toString(), locale.text, namespace.text, key.text, value.text)) }
        }
    }
    private fun deleteSelected() { val entries = selectedEntries(); if (entries.isEmpty()) return showError("請至少選擇一筆資料"); confirm("確定刪除 ${entries.size} 筆翻譯？此操作會寫回語言檔案。") { runAction { repository.delete(activeId(), entries.map { it.id }) } } }
    private fun renameKey() { val row = selectedRows().singleOrNull() ?: return showError("請選擇一個翻譯鍵作為改名來源"); val value = Messages.showInputDialog(project, "將所有語言中的 key「${row.key}」改為：", "Key 改名", null, row.key, null)?.trim() ?: return; runAction { repository.rename(activeId(), row.key, value) } }

    private fun selectedRows(): List<JoinedTranslationRow> = entryTable.selectedRows.toList().mapNotNull { row -> entryModel.items.getOrNull(entryTable.convertRowIndexToModel(row)) }
    private fun selectedEntries(): List<LanguageEntryDto> = selectedRows().flatMap { it.translations }.distinctBy { it.id }
    private fun activeScheme() = current.schemes.firstOrNull { it.id == current.activeSchemeId }
    private fun activeId() = current.activeSchemeId ?: error("請先建立或選擇方案")
    private fun button(text: String, action: () -> Unit) = JButton(text).apply { addActionListener { action() } }
    private fun confirm(message: String, action: () -> Unit) { if (Messages.showYesNoDialog(project, message, "Language Manager", Messages.getQuestionIcon()) == Messages.YES) action() }
    private fun showError(message: String) { Messages.showErrorDialog(project, message.take(500), "Language Manager") }
    private fun runAction(action: suspend () -> Unit) { scope.launch { try { action() } catch (e: Exception) { if (e is CancellationException) throw e; withContext(Dispatchers.EDT) { showError(e.message ?: "操作失敗") } } } }
    override fun dispose() { scope.cancel() }
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

    override fun getRowCount() = items.size
    override fun getColumnCount() = locales.size + 3
    override fun getColumnName(column: Int) = when {
        column == 0 -> "Namespace"
        column == 1 -> "Key"
        column == columnCount - 1 -> "使用次數"
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
    private val columns = arrayOf("等級", "類型", "Key", "訊息", "檔案")
    override fun getRowCount() = items.size; override fun getColumnCount() = columns.size; override fun getColumnName(column: Int) = columns[column]
    override fun getValueAt(row: Int, col: Int): Any = items[row].let { when (col) { 0 -> it.severity; 1 -> it.code; 2 -> it.key; 3 -> it.message; else -> it.filePath } }
}
