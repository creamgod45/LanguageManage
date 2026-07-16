package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.AiTranslationSuggestionDto
import cg.creamgod45.localization.JoinedTranslationRow
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

internal const val AI_KEY_SOURCE = "source-key"

internal class AiTranslationRequestDialog(
    project: Project,
    private val locales: List<String>,
    private val rows: List<JoinedTranslationRow>,
    private val localeNotes: Map<String, String> = emptyMap(),
) : DialogWrapper(project) {
    private val sourceChoices = listOf(AiSourceChoice(null)) + locales.map { AiSourceChoice(it, localeNotes[it].orEmpty()) }
    private val source = ComboBox(sourceChoices.toTypedArray())
    private val targetModel = DefaultListModel<String>()
    private val target =
        JBList(targetModel).apply {
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            visibleRowCount = 5
            cellRenderer =
                object : javax.swing.DefaultListCellRenderer() {
                    override fun getListCellRendererComponent(
                        list: javax.swing.JList<*>?,
                        value: Any?,
                        index: Int,
                        isSelected: Boolean,
                        cellHasFocus: Boolean,
                    ) = super.getListCellRendererComponent(
                        list,
                        value?.toString()?.let { locale -> localeLabel(locale, localeNotes[locale].orEmpty()) },
                        index,
                        isSelected,
                        cellHasFocus,
                    )
                }
        }
    private val previewModel = AiSourcePreviewTableModel(rows)

    init {
        title = message("dialog.ai.translation.title")
        source.selectedIndex = sourceChoices.indexOfFirst { it.locale?.equals("en", true) == true }.takeIf { it >= 0 } ?: 0
        source.addActionListener { refreshTargetsAndPreview(false) }
        refreshTargetsAndPreview(true)
        init()
    }

    val sourceLocale: String? get() = (source.selectedItem as? AiSourceChoice)?.locale
    val sourceIdentifier: String get() = sourceLocale ?: AI_KEY_SOURCE
    val targetLocales: List<String> get() = target.selectedValuesList
    val sourceValues: List<String> get() = previewModel.values()

    override fun createCenterPanel(): JComponent =
        FormBuilder
            .createFormBuilder()
            .addComponent(JLabel(message("dialog.ai.translation.help.multi")))
            .addLabeledComponent(message("dialog.ai.source.locale"), source)
            .addLabeledComponent(message("dialog.ai.target.locales"), JBScrollPane(target))
            .addComponent(JLabel(message("dialog.ai.source.preview")))
            .addComponent(JBScrollPane(JBTable(previewModel).apply { autoResizeMode = JBTable.AUTO_RESIZE_LAST_COLUMN }))
            .panel
            .apply { preferredSize = Dimension(JBUI.scale(900), JBUI.scale(520)) }

    override fun doValidate(): ValidationInfo? {
        if (targetLocales.isEmpty()) return ValidationInfo(message("error.ai.target.required"), target)
        val missingIndex = sourceValues.indexOfFirst(String::isBlank)
        if (missingIndex < 0) return null
        return ValidationInfo(
            message("error.ai.source.missing", rows[missingIndex].key, sourceLocale ?: message("dialog.ai.source.key")),
            source,
        )
    }

    private fun refreshTargetsAndPreview(initial: Boolean) {
        val selected = target.selectedValuesList.toSet()
        val sourceLocale = sourceLocale
        targetModel.clear()
        locales
            .distinct()
            .sorted()
            .filterNot { it == sourceLocale }
            .forEach(targetModel::addElement)
        val preferred =
            if (initial) {
                (0 until targetModel.size()).filter { index ->
                    val locale = targetModel[index]
                    rows.any { row -> row.translations.none { it.locale == locale } || sourceValue(row, locale).isNullOrBlank() }
                }
            } else {
                (0 until targetModel.size()).filter { targetModel[it] in selected }
            }
        val indices = preferred.ifEmpty { if (targetModel.size() > 0) listOf(0) else emptyList() }
        target.selectedIndices = indices.toIntArray()
        previewModel.sourceLocale = sourceLocale
    }
}

internal class TargetLocaleDialog(
    project: Project,
    locales: List<String>,
    private val rowCount: Int,
) : DialogWrapper(project) {
    private val target = ComboBox(locales.toTypedArray())

    init {
        title = message("dialog.copy.key.locale.title")
        init()
    }

    val locale: String get() = target.selectedItem?.toString().orEmpty()

    override fun createCenterPanel(): JComponent =
        FormBuilder
            .createFormBuilder()
            .addComponent(javax.swing.JLabel(message("dialog.copy.key.locale.prompt", rowCount)))
            .addLabeledComponent(message("dialog.ai.target.locale"), target)
            .panel
}

internal class AiTranslationReviewDialog(
    project: Project,
    rows: List<JoinedTranslationRow>,
    suggestions: Map<String, List<AiTranslationSuggestionDto>>,
) : DialogWrapper(project) {
    private val model = AiReviewTableModel(rows, suggestions)

    init {
        title = message("dialog.ai.review.title")
        setOKButtonText(message("action.apply"))
        init()
    }

    fun values(): Map<String, Map<String, String>> = model.values()

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(javax.swing.JLabel(message("dialog.ai.review.help")), BorderLayout.NORTH)
            add(JBScrollPane(JBTable(model).apply { autoResizeMode = JBTable.AUTO_RESIZE_LAST_COLUMN }), BorderLayout.CENTER)
            preferredSize = Dimension(JBUI.scale(900), JBUI.scale(480))
        }
}

internal class AiTranslationFeedbackDialog(
    project: Project,
) : DialogWrapper(project) {
    private val feedback =
        JBTextArea(6, 60).apply {
            lineWrap = true
            wrapStyleWord = true
        }

    init {
        title = message("dialog.ai.feedback.title")
        setCancelButtonText(message("button.back"))
        init()
    }

    val value: String get() = feedback.text.trim()

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout(0, JBUI.scale(6))).apply {
            add(javax.swing.JLabel(message("dialog.ai.feedback.help")), BorderLayout.NORTH)
            add(JBScrollPane(feedback), BorderLayout.CENTER)
            preferredSize = Dimension(JBUI.scale(700), JBUI.scale(220))
        }

    override fun doValidate(): ValidationInfo? =
        if (value.isBlank()) ValidationInfo(message("error.ai.feedback.required"), feedback) else null
}

private class AiReviewTableModel(
    rows: List<JoinedTranslationRow>,
    suggestions: Map<String, List<AiTranslationSuggestionDto>>,
) : AbstractTableModel() {
    private val locales = suggestions.keys.toList()
    private val items =
        rows.mapIndexed { index, row ->
            ReviewItem(
                "item$index",
                row.namespace,
                row.key,
                locales
                    .associateWith { locale ->
                        suggestions.getValue(locale).first { it.id == "item$index" }.translatedValue
                    }.toMutableMap(),
            )
        }

    override fun getRowCount() = items.size

    override fun getColumnCount() = 2 + locales.size

    override fun getColumnName(column: Int) = if (column < 2) aiMetadataColumnName(column) else locales[column - 2]

    override fun getValueAt(
        row: Int,
        column: Int,
    ): Any =
        when (column) {
            0 -> {
                items[row].namespace
            }

            1 -> {
                items[row].key
            }

            else -> {
                items[row].values.getValue(
                    locales[
                        column -
                            2,
                    ],
                )
            }
        }

    override fun isCellEditable(
        row: Int,
        column: Int,
    ) = column >= 2

    override fun setValueAt(
        value: Any?,
        row: Int,
        column: Int,
    ) {
        if (column >= 2) {
            items[row].values[locales[column - 2]] = value?.toString().orEmpty()
            fireTableCellUpdated(row, column)
        }
    }

    fun values(): Map<String, Map<String, String>> =
        locales.associateWith { locale -> items.associate { it.id to it.values.getValue(locale) } }

    private data class ReviewItem(
        val id: String,
        val namespace: String,
        val key: String,
        val values: MutableMap<String, String>,
    )
}

internal fun sourceValue(
    row: JoinedTranslationRow,
    locale: String?,
): String? = if (locale == null) row.key else row.translations.firstOrNull { it.locale == locale }?.value

internal fun aiMetadataColumnName(column: Int): String =
    when (column) {
        0 -> message("table.namespace")
        1 -> message("table.key")
        else -> error("Unsupported AI translation metadata column: $column")
    }

private data class AiSourceChoice(
    val locale: String?,
    val note: String = "",
) {
    override fun toString(): String = locale?.let { localeLabel(it, note) } ?: message("dialog.ai.source.key")
}

private fun localeLabel(locale: String, note: String): String = if (note.isBlank()) locale else "$locale — $note"

private class AiSourcePreviewTableModel(
    private val rows: List<JoinedTranslationRow>,
) : AbstractTableModel() {
    private val values = MutableList(rows.size) { rows[it].key }
    var sourceLocale: String? = null
        set(value) {
            field = value
            rows.indices.forEach { index -> values[index] = sourceValue(rows[index], value).orEmpty() }
            fireTableDataChanged()
        }

    override fun getRowCount() = rows.size

    override fun getColumnCount() = 3

    override fun getColumnName(column: Int) = if (column < 2) aiMetadataColumnName(column) else message("dialog.ai.source.text")

    override fun getValueAt(
        row: Int,
        column: Int,
    ): Any =
        when (column) {
            0 -> rows[row].namespace
            1 -> rows[row].key
            else -> values[row]
        }

    override fun isCellEditable(
        row: Int,
        column: Int,
    ) = column == 2

    override fun setValueAt(
        value: Any?,
        row: Int,
        column: Int,
    ) {
        if (column == 2) {
            values[row] = value?.toString().orEmpty()
            fireTableCellUpdated(row, column)
        }
    }

    fun values(): List<String> = values.toList()
}
