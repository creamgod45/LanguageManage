package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.RegexPatternUi
import cg.creamgod45.RegexPresetUi
import cg.creamgod45.localization.DEFAULT_USAGE_EXCLUDED_DIRECTORIES
import cg.creamgod45.localization.DEFAULT_USAGE_REGEX_PATTERNS
import cg.creamgod45.localization.HARD_MAX_ENTRIES_PER_FILE
import cg.creamgod45.localization.HARD_MAX_ENTRIES_PER_SCHEME
import cg.creamgod45.localization.HARD_MAX_LANGUAGE_FILE_KB
import cg.creamgod45.localization.HARD_MAX_LANGUAGE_SCHEME_MB
import cg.creamgod45.localization.LanguageSchemeDto
import cg.creamgod45.localization.MAX_LOCALE_NOTE_CHARS
import cg.creamgod45.localization.MAX_SCHEME_NAME_LENGTH
import cg.creamgod45.localization.UsageScanSettingsDto
import cg.creamgod45.localization.normalizeSchemeName
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.nio.file.Path
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel
import javax.swing.table.DefaultTableModel

internal data class SchemeSettingsResult(
    val name: String,
    val usageSettings: UsageScanSettingsDto,
    val localeNotes: Map<String, String>,
)

internal class SchemeUsageSettingsDialog(
    private val dialogProject: Project,
    private val scheme: LanguageSchemeDto,
    locales: Collection<String>,
) : DialogWrapper(dialogProject) {
    private val schemeNameField = JBTextField(scheme.name)
    private val filesArea =
        JBTextArea(scheme.files.joinToString("\n"), 4, 48).apply {
            isEditable = false
            lineWrap = false
        }
    private val basePathField = JBTextField(scheme.usageScanSettings.basePath)
    private val regexModel = DefaultListModel<String>().apply { replaceWith(scheme.usageScanSettings.regexPatterns) }
    private val exclusionModel = DefaultListModel<String>().apply { replaceWith(scheme.usageScanSettings.excludedDirectories) }
    private val maxLanguageFileKbSpinner =
        JSpinner(SpinnerNumberModel(scheme.usageScanSettings.maxLanguageFileKb, 1, HARD_MAX_LANGUAGE_FILE_KB, 128))
    private val maxLanguageSchemeMbSpinner =
        JSpinner(SpinnerNumberModel(scheme.usageScanSettings.maxLanguageSchemeMb, 1, HARD_MAX_LANGUAGE_SCHEME_MB, 1))
    private val maxEntriesPerFileSpinner =
        JSpinner(SpinnerNumberModel(scheme.usageScanSettings.maxEntriesPerFile, 1, HARD_MAX_ENTRIES_PER_FILE, 1_000))
    private val maxEntriesPerSchemeSpinner =
        JSpinner(SpinnerNumberModel(scheme.usageScanSettings.maxEntriesPerScheme, 1, HARD_MAX_ENTRIES_PER_SCHEME, 5_000))
    private val localeNotesModel =
        object : DefaultTableModel(
            arrayOf<Any>(message("settings.locale.notes.column.locale"), message("settings.locale.notes.column.note")),
            0,
        ) {
            override fun isCellEditable(
                row: Int,
                column: Int,
            ): Boolean = column == 1
        }.apply { localeNoteRows(locales, scheme.localeNotes).forEach { (locale, note) -> addRow(arrayOf<Any>(locale, note)) } }
    private val localeNotesTable =
        JBTable(localeNotesModel).apply {
            putClientProperty("terminateEditOnFocusLost", true)
            emptyText.text = message("settings.locale.notes.empty")
            columnModel.getColumn(0).preferredWidth = JBUI.scale(140)
            columnModel.getColumn(1).preferredWidth = JBUI.scale(480)
        }

    init {
        title = message("dialog.scheme.settings.title", scheme.name)
        init()
    }

    fun result(): SchemeSettingsResult =
        SchemeSettingsResult(
            name = normalizeSchemeName(schemeNameField.text)!!,
            usageSettings =
                UsageScanSettingsDto(
                    basePath = basePathField.text.trim(),
                    regexPatterns = regexModel.values(),
                    excludedDirectories = exclusionModel.values(),
                    maxLanguageFileKb = maxLanguageFileKbSpinner.value as Int,
                    maxLanguageSchemeMb = maxLanguageSchemeMbSpinner.value as Int,
                    maxEntriesPerFile = maxEntriesPerFileSpinner.value as Int,
                    maxEntriesPerScheme = maxEntriesPerSchemeSpinner.value as Int,
                ),
            localeNotes = editedLocaleNotes(localeNoteValues()),
        )

    override fun createCenterPanel(): JComponent {
        val basePathPanel =
            JPanel(BorderLayout(JBUI.scale(5), 0)).apply {
                add(basePathField, BorderLayout.CENTER)
                add(JButton(message("settings.browse")).apply { addActionListener { chooseBasePath() } }, BorderLayout.EAST)
            }
        val panel =
            FormBuilder
                .createFormBuilder()
                .addLabeledComponent(message("settings.scheme.name"), schemeNameField)
                .addTooltip(message("settings.scheme.name.help"))
                .addLabeledComponent(
                    message("settings.scheme.files"),
                    JBScrollPane(filesArea).apply {
                        preferredSize = Dimension(JBUI.scale(620), JBUI.scale(90))
                    },
                ).addLabeledComponent(message("settings.usage.base.path"), basePathPanel)
                .addTooltip(message("settings.usage.base.path.help"))
                .addComponent(javax.swing.JLabel(message("settings.load.limits.title")))
                .addLabeledComponent(message("settings.load.max.file.kb"), maxLanguageFileKbSpinner)
                .addLabeledComponent(message("settings.load.max.scheme.mb"), maxLanguageSchemeMbSpinner)
                .addLabeledComponent(message("settings.load.max.entries.file"), maxEntriesPerFileSpinner)
                .addLabeledComponent(message("settings.load.max.entries.scheme"), maxEntriesPerSchemeSpinner)
                .addTooltip(message("settings.load.limits.help"))
                .addLabeledComponent(
                    message("settings.usage.regex"),
                    listEditor(
                        regexModel,
                        message("settings.regex.add.prompt"),
                        message("settings.regex.edit.prompt"),
                        DEFAULT_USAGE_REGEX_PATTERNS,
                        regexInput = true,
                    ),
                ).addComponent(RegexPatternUi.helpComponent())
                .addLabeledComponent(
                    message("settings.usage.exclusions"),
                    listEditor(
                        exclusionModel,
                        message("settings.exclusion.add.prompt"),
                        message("settings.exclusion.edit.prompt"),
                        DEFAULT_USAGE_EXCLUDED_DIRECTORIES,
                        bulkInput = true,
                        pathPicker = true,
                    ),
                ).addTooltip(message("settings.usage.exclusions.help"))
                .addLabeledComponent(
                    message("settings.locale.notes"),
                    JBScrollPane(localeNotesTable).apply {
                        preferredSize = Dimension(JBUI.scale(620), JBUI.scale(140))
                    },
                ).addTooltip(message("settings.locale.notes.help", MAX_LOCALE_NOTE_CHARS))
                .panel
        return JBScrollPane(panel).apply {
            border = JBUI.Borders.empty()
            preferredSize = Dimension(JBUI.scale(780), JBUI.scale(620))
        }
    }

    override fun doValidate(): ValidationInfo? {
        if (normalizeSchemeName(schemeNameField.text) == null) {
            return ValidationInfo(message("error.scheme.name.length", MAX_SCHEME_NAME_LENGTH), schemeNameField)
        }
        localeNoteValues().firstOrNull { (_, note) -> !isValidLocaleNote(note) }?.let { (locale, _) ->
            return ValidationInfo(message("settings.locale.notes.invalid", locale, MAX_LOCALE_NOTE_CHARS), localeNotesTable)
        }
        return if ((maxEntriesPerFileSpinner.value as Int) > (maxEntriesPerSchemeSpinner.value as Int)) {
            ValidationInfo(message("settings.load.entries.order"), maxEntriesPerFileSpinner)
        } else {
            null
        }
    }

    override fun getPreferredFocusedComponent(): JComponent = schemeNameField

    private fun localeNoteValues(): List<Pair<String, String>> {
        if (localeNotesTable.isEditing) localeNotesTable.cellEditor?.stopCellEditing()
        return (0 until localeNotesModel.rowCount).map { row ->
            localeNotesModel.getValueAt(row, 0).toString() to localeNotesModel.getValueAt(row, 1)?.toString().orEmpty()
        }
    }

    private fun chooseBasePath() {
        val descriptor =
            FileChooserDescriptor(false, true, false, false, false, false)
                .withTitle(message("settings.usage.base.path.choose"))
        FileChooserFactory
            .getInstance()
            .createFileChooser(descriptor, dialogProject, null)
            .choose(dialogProject)
            .firstOrNull()
            ?.let { basePathField.text = it.path }
    }

    private fun listEditor(
        model: DefaultListModel<String>,
        addPrompt: String,
        editPrompt: String,
        defaultValues: List<String>,
        regexInput: Boolean = false,
        bulkInput: Boolean = false,
        pathPicker: Boolean = false,
    ): JComponent {
        val list = JBList(model).apply { visibleRowCount = 5 }
        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBScrollPane(list).apply { preferredSize = Dimension(JBUI.scale(620), JBUI.scale(110)) }, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEADING, JBUI.scale(4), 0)).apply {
                    add(
                        JButton(message("settings.list.add")).apply {
                            addActionListener {
                                requestListValue(addPrompt, null, regexInput)
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                    ?.let { value ->
                                        if (value !in model.values()) model.addElement(value)
                                    }
                            }
                        },
                    )
                    add(
                        JButton(message("settings.list.edit")).apply {
                            addActionListener {
                                val index = list.selectedIndex
                                if (index < 0) return@addActionListener
                                requestListValue(editPrompt, model[index], regexInput)
                                    ?.trim()
                                    ?.takeIf(String::isNotEmpty)
                                    ?.let { model[index] = it }
                            }
                        },
                    )
                    add(
                        JButton(message("settings.list.remove")).apply {
                            addActionListener { list.selectedIndices.sortedDescending().forEach(model::remove) }
                        },
                    )
                    add(
                        JButton(message("settings.list.restore.defaults")).apply {
                            addActionListener { model.replaceWith(defaultValues) }
                        },
                    )
                    if (bulkInput) {
                        add(
                            JButton(message("settings.list.add.bulk")).apply {
                                addActionListener {
                                    requestBulkListValues()?.let { values ->
                                        val existing = model.values().toMutableSet()
                                        values.filter(existing::add).forEach(model::addElement)
                                    }
                                }
                            },
                        )
                    }
                    if (pathPicker) {
                        add(
                            JButton(message("settings.exclusion.choose.paths")).apply {
                                addActionListener { chooseExclusionPaths(model) }
                            },
                        )
                    }
                    if (regexInput) {
                        add(
                            RegexPresetUi.button { patterns ->
                                patterns.filterNot { it in model.values() }.forEach(model::addElement)
                            },
                        )
                    }
                },
                BorderLayout.SOUTH,
            )
        }
    }

    private fun chooseExclusionPaths(model: DefaultListModel<String>) {
        val configuredRoot = basePathField.text.trim().ifBlank { dialogProject.basePath.orEmpty() }
        val root = runCatching { Path.of(configuredRoot).toAbsolutePath().normalize() }.getOrNull()
        if (root == null) {
            Messages.showErrorDialog(dialogProject, message("settings.exclusion.choose.base.required"), title)
            return
        }
        val descriptor =
            FileChooserDescriptor(true, true, false, false, false, true)
                .withTitle(message("settings.exclusion.choose.title"))
                .withDescription(message("settings.exclusion.choose.description"))
        val selected = FileChooserFactory.getInstance().createFileChooser(descriptor, dialogProject, null).choose(dialogProject)
        val existing = model.values().toMutableSet()
        var skipped = 0
        selected.forEach { virtualFile ->
            val path = runCatching { Path.of(virtualFile.path).toAbsolutePath().normalize() }.getOrNull()
            val relative =
                path
                    ?.takeIf { it != root && it.startsWith(root) }
                    ?.let { root.relativize(it).joinToString("/") { part -> part.toString() } }
                    ?.takeIf(String::isNotBlank)
            if (relative == null) {
                skipped++
            } else if (existing.add(relative)) {
                model.addElement(relative)
            }
        }
        if (skipped > 0) {
            Messages.showWarningDialog(
                dialogProject,
                message("settings.exclusion.choose.skipped", skipped),
                message("settings.exclusion.choose.title"),
            )
        }
    }

    private fun requestBulkListValues(): List<String>? {
        val dialog = BulkExclusionDialog(dialogProject)
        return if (dialog.showAndGet()) parseBulkListValues(dialog.input) else null
    }

    private fun requestListValue(
        prompt: String,
        initialValue: String?,
        regexInput: Boolean,
    ): String? =
        if (regexInput) {
            RegexPatternUi.showInputDialog(dialogProject, title, prompt, initialValue)
        } else {
            Messages.showInputDialog(dialogProject, prompt, title, null, initialValue, null)
        }
}

private class BulkExclusionDialog(
    project: Project,
) : DialogWrapper(project) {
    private val inputArea =
        JBTextArea(10, 48).apply {
            lineWrap = true
            wrapStyleWord = true
            emptyText.text = message("settings.exclusion.bulk.placeholder")
        }

    val input: String get() = inputArea.text

    init {
        title = message("settings.exclusion.bulk.title")
        setOKButtonText(message("settings.list.add.bulk"))
        init()
    }

    override fun createCenterPanel(): JComponent =
        JBScrollPane(inputArea).apply {
            preferredSize = Dimension(JBUI.scale(560), JBUI.scale(220))
        }
}

/** Rows for the language-notes table: every loaded locale plus any locale that already has a note, so stale notes can be cleared. */
internal fun localeNoteRows(
    locales: Collection<String>,
    notes: Map<String, String>,
): List<Pair<String, String>> = (locales + notes.keys).distinct().sorted().map { it to notes[it].orEmpty() }

/** Trimmed notes keyed by locale; blank notes are dropped, which removes them from the scheme. */
internal fun editedLocaleNotes(rows: List<Pair<String, String>>): Map<String, String> =
    rows.associate { (locale, note) -> locale to note.trim() }.filterValues(String::isNotBlank)

internal fun isValidLocaleNote(note: String): Boolean = note.trim().let { it.length <= MAX_LOCALE_NOTE_CHARS && it.none(Char::isISOControl) }

internal fun parseBulkListValues(input: String): List<String> =
    input
        .split(Regex("[,\\r\\n]+"))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

private fun DefaultListModel<String>.values(): List<String> = (0 until size()).map(::getElementAt)

private fun DefaultListModel<String>.replaceWith(values: List<String>) {
    clear()
    values.forEach(::addElement)
}
