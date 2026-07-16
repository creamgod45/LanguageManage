package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.AiTranslationSuggestionDto
import cg.creamgod45.localization.JoinedTranslationRow
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

internal class AiTranslationRequestDialog(
    project: Project,
    locales: List<String>,
) : DialogWrapper(project) {
    private val source = ComboBox(locales.toTypedArray())
    private val target = ComboBox(locales.toTypedArray())

    init {
        title = message("dialog.ai.translation.title")
        if (locales.size > 1) target.selectedIndex = 1
        init()
    }

    val sourceLocale: String get() = source.selectedItem?.toString().orEmpty()
    val targetLocale: String get() = target.selectedItem?.toString().orEmpty()

    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addComponent(javax.swing.JLabel(message("dialog.ai.translation.help")))
            .addLabeledComponent(message("dialog.ai.source.locale"), source)
            .addLabeledComponent(message("dialog.ai.target.locale"), target)
            .panel

    override fun doValidate(): ValidationInfo? =
        if (sourceLocale == targetLocale) ValidationInfo(message("error.ai.locales.same"), target) else null
}

internal class TargetLocaleDialog(
    project: Project,
    locales: List<String>,
    private val rowCount: Int,
) : DialogWrapper(project) {
    private val target = ComboBox(locales.toTypedArray())
    init { title = message("dialog.copy.key.locale.title"); init() }
    val locale: String get() = target.selectedItem?.toString().orEmpty()
    override fun createCenterPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addComponent(javax.swing.JLabel(message("dialog.copy.key.locale.prompt", rowCount)))
            .addLabeledComponent(message("dialog.ai.target.locale"), target)
            .panel
}

internal class AiTranslationReviewDialog(
    project: Project,
    rows: List<JoinedTranslationRow>,
    suggestions: List<AiTranslationSuggestionDto>,
) : DialogWrapper(project) {
    private val model = AiReviewTableModel(rows, suggestions)

    init {
        title = message("dialog.ai.review.title")
        setOKButtonText(message("action.apply"))
        init()
    }

    fun values(): Map<String, String> = model.values()

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(javax.swing.JLabel(message("dialog.ai.review.help")), BorderLayout.NORTH)
            add(JBScrollPane(JBTable(model).apply { autoResizeMode = JBTable.AUTO_RESIZE_LAST_COLUMN }), BorderLayout.CENTER)
            preferredSize = Dimension(JBUI.scale(900), JBUI.scale(480))
        }
}

internal class AiTranslationFeedbackDialog(project: Project) : DialogWrapper(project) {
    private val feedback = JBTextArea(6, 60).apply { lineWrap = true; wrapStyleWord = true }
    init { title = message("dialog.ai.feedback.title"); init() }
    val value: String get() = feedback.text.trim()
    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, JBUI.scale(6))).apply {
        add(javax.swing.JLabel(message("dialog.ai.feedback.help")), BorderLayout.NORTH)
        add(JBScrollPane(feedback), BorderLayout.CENTER)
        preferredSize = Dimension(JBUI.scale(700), JBUI.scale(220))
    }
    override fun doValidate(): ValidationInfo? =
        if (value.isBlank()) ValidationInfo(message("error.ai.feedback.required"), feedback) else null
}

private class AiReviewTableModel(
    rows: List<JoinedTranslationRow>,
    suggestions: List<AiTranslationSuggestionDto>,
) : AbstractTableModel() {
    private val items = rows.mapIndexed { index, row -> ReviewItem("item$index", row.namespace, row.key, suggestions.first { it.id == "item$index" }.translatedValue) }
    override fun getRowCount() = items.size
    override fun getColumnCount() = 3
    override fun getColumnName(column: Int) = message(arrayOf("column.namespace", "column.key", "column.value")[column])
    override fun getValueAt(row: Int, column: Int): Any = when (column) { 0 -> items[row].namespace; 1 -> items[row].key; else -> items[row].value }
    override fun isCellEditable(row: Int, column: Int) = column == 2
    override fun setValueAt(value: Any?, row: Int, column: Int) { if (column == 2) { items[row].value = value?.toString().orEmpty(); fireTableCellUpdated(row, column) } }
    fun values() = items.associate { it.id to it.value }
    private data class ReviewItem(val id: String, val namespace: String, val key: String, var value: String)
}
