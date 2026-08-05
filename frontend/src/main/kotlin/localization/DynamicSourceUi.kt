package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.DynamicSourceGroupDto
import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.MAX_DYNAMIC_SOURCE_KEYS_PER_GROUP
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal class DynamicGroupsEditor(
    private val project: Project,
    entries: List<LanguageEntryDto>,
    initial: List<DynamicSourceGroupDto>,
    conversionLabel: String? = null,
    onConvert: (() -> Unit)? = null,
) : JPanel(BorderLayout()) {
    private val suggestions =
        entries.flatMap { entry ->
            listOf(entry.key, if (entry.namespace.isBlank()) entry.key else "${entry.namespace}.${entry.key}")
        }.distinct().sorted()
    private val groupsPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val groupRows = mutableListOf<GroupRow>()

    init {
        border = JBUI.Borders.empty(4)
        add(groupsPanel, BorderLayout.CENTER)
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
                add(JButton(message("dynamic.group.add")).apply { addActionListener { addGroup() } })
                if (conversionLabel != null && onConvert != null) {
                    add(JButton(conversionLabel).apply { addActionListener { onConvert() } })
                }
            },
            BorderLayout.SOUTH,
        )
        initial.ifEmpty { listOf(DynamicSourceGroupDto()) }.forEach(::addGroup)
    }

    fun groups(): List<DynamicSourceGroupDto> = groupRows.map { it.value() }

    fun validationInfo(): ValidationInfo? {
        if (groupRows.isEmpty()) return ValidationInfo(message("dynamic.validation.group.required"), this)
        groupRows.forEach { row ->
            if (!row.name.text.trim().matches(Regex("[A-Za-z][A-Za-z0-9_-]{0,63}"))) {
                return ValidationInfo(message("dynamic.validation.group.name"), row.name)
            }
            val populatedKeys = row.keys.filter { it.text.isNotBlank() }
            if (populatedKeys.isEmpty()) return ValidationInfo(message("dynamic.validation.key.required"), row.panel)
            if (populatedKeys.size > MAX_DYNAMIC_SOURCE_KEYS_PER_GROUP) {
                return ValidationInfo(message("dynamic.validation.key.limit", MAX_DYNAMIC_SOURCE_KEYS_PER_GROUP), row.panel)
            }
            populatedKeys.forEach { field ->
                val key = field.text.trim()
                if (key.length > 256 || key.any(Char::isISOControl)) {
                    return ValidationInfo(message("dynamic.validation.key.invalid"), field)
                }
            }
        }
        return null
    }

    private fun addGroup(initial: DynamicSourceGroupDto = DynamicSourceGroupDto()) {
        lateinit var row: GroupRow
        row = GroupRow(initial) {
            groupRows.remove(row)
            groupsPanel.remove(row.panel)
            groupsPanel.revalidate()
            groupsPanel.repaint()
        }
        groupRows += row
        groupsPanel.add(row.panel)
        groupsPanel.revalidate()
        groupsPanel.repaint()
    }

    private inner class GroupRow(
        initial: DynamicSourceGroupDto,
        remove: () -> Unit,
    ) {
        val name = JBTextField(initial.name.ifBlank { "enum" }, 10)
        val keys = mutableListOf<DynamicKeyField>()
        private val keysPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
        val panel =
            JPanel(BorderLayout(6, 4)).apply {
                border = JBUI.Borders.compound(JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground()), JBUI.Borders.empty(5))
                add(
                    JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).apply {
                        add(JBLabel(message("dynamic.group.name")))
                        add(this@GroupRow.name)
                        add(JButton(message("dynamic.key.add")).apply { addActionListener { addKey("") } })
                        add(JButton(message("dynamic.key.bulk.add")).apply { addActionListener { addKeysInBulk() } })
                        add(JButton(message("button.remove")).apply { addActionListener { remove() } })
                    },
                    BorderLayout.NORTH,
                )
                add(keysPanel, BorderLayout.CENTER)
            }

        init {
            initial.keys.ifEmpty { listOf("") }.forEach(::addKey)
        }

        fun value() = DynamicSourceGroupDto(name.text.trim(), keys.map { it.text.trim() }.filter(String::isNotBlank).distinct())

        private fun addKey(value: String) {
            lateinit var field: DynamicKeyField
            val row = JPanel(BorderLayout(4, 0))
            field = DynamicKeyField(value, suggestions)
            row.add(field, BorderLayout.CENTER)
            row.add(JButton(message("button.remove")).apply {
                addActionListener {
                    keys.remove(field)
                    keysPanel.remove(row)
                    keysPanel.revalidate()
                    keysPanel.repaint()
                }
            }, BorderLayout.EAST)
            keys += field
            keysPanel.add(row)
            keysPanel.revalidate()
        }

        private fun addKeysInBulk() {
            val dialog = BulkDynamicKeysDialog(project)
            if (!dialog.showAndGet()) return
            val existing = keys.map { it.text.trim() }.toSet()
            dialog.keys().filterNot(existing::contains).forEach(::addKey)
        }
    }
}

private enum class BulkKeySeparator(val messageKey: String) {
    LINES("dynamic.key.bulk.separator.lines"),
    COMMA("dynamic.key.bulk.separator.comma"),
    SEMICOLON("dynamic.key.bulk.separator.semicolon"),
    CUSTOM("dynamic.key.bulk.separator.custom"),
}

private class BulkDynamicKeysDialog(
    project: Project,
) : DialogWrapper(project) {
    private val input = JBTextArea(12, 58).apply { lineWrap = false }
    private val separator = ComboBox(BulkKeySeparator.entries.toTypedArray())
    private val customSeparator = JBTextField(8).apply { isEnabled = false }

    init {
        title = message("dynamic.key.bulk.title")
        separator.renderer =
            object : javax.swing.DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: javax.swing.JList<*>?,
                    value: Any?,
                    index: Int,
                    selected: Boolean,
                    focus: Boolean,
                ) = super.getListCellRendererComponent(
                    list,
                    message((value as? BulkKeySeparator ?: BulkKeySeparator.LINES).messageKey),
                    index,
                    selected,
                    focus,
                )
            }
        separator.addActionListener { customSeparator.isEnabled = separator.selectedItem == BulkKeySeparator.CUSTOM }
        init()
    }

    fun keys(): List<String> {
        val selected = separator.selectedItem as? BulkKeySeparator ?: BulkKeySeparator.LINES
        val delimiter =
            when (selected) {
                BulkKeySeparator.LINES -> null
                BulkKeySeparator.COMMA -> ","
                BulkKeySeparator.SEMICOLON -> ";"
                BulkKeySeparator.CUSTOM -> customSeparator.text
            }
        return splitBulkDynamicKeys(input.text, delimiter)
    }

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout(0, 6)).apply {
            add(JBLabel(message("dynamic.key.bulk.help")), BorderLayout.NORTH)
            add(JBScrollPane(input).apply { preferredSize = java.awt.Dimension(JBUI.scale(650), JBUI.scale(260)) }, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply {
                    add(JBLabel(message("dynamic.key.bulk.separator")))
                    add(separator)
                    add(JBLabel(message("dynamic.key.bulk.custom")))
                    add(customSeparator)
                },
                BorderLayout.SOUTH,
            )
        }

    override fun doValidate(): ValidationInfo? = when {
        input.text.isBlank() -> ValidationInfo(message("dynamic.key.bulk.validation.empty"), input)
        separator.selectedItem == BulkKeySeparator.CUSTOM && customSeparator.text.isEmpty() ->
            ValidationInfo(message("dynamic.key.bulk.validation.separator"), customSeparator)
        customSeparator.text.length > 16 || customSeparator.text.any(Char::isISOControl) ->
            ValidationInfo(message("dynamic.key.bulk.validation.separator"), customSeparator)
        keys().any { it.length > 256 || it.any(Char::isISOControl) } ->
            ValidationInfo(message("dynamic.validation.key.invalid"), input)
        else -> null
    }
}

internal fun splitBulkDynamicKeys(
    text: String,
    literalSeparator: String?,
): List<String> =
    (if (literalSeparator == null) text.split(Regex("\\R")) else text.split(literalSeparator))
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

private class DynamicKeyField(
    initial: String,
    private val suggestions: List<String>,
) : TextFieldWithBrowseButton() {
    init {
        text = initial
        toolTipText = message("dynamic.key.autocomplete.help")
        addActionListener { showSuggestions() }
    }

    private fun showSuggestions() {
        val needle = text.trim().lowercase()
        val matches = suggestions.filter { needle.isBlank() || it.lowercase().contains(needle) }.take(200)
        if (matches.isEmpty()) return
        JBPopupFactory.getInstance().createPopupChooserBuilder(matches)
            .setTitle(message("dynamic.key.autocomplete.title"))
            .setItemChosenCallback { selected: String ->
                text = selected
                textField.caretPosition = selected.length
            }.createPopup()
            .showUnderneathOf(this)
    }
}
