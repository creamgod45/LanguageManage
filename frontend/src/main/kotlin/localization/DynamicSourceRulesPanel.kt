package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.DynamicSourceGroupDto
import cg.creamgod45.localization.DynamicSourceRuleDto
import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.LanguageSchemeDto
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.util.UUID
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

internal class DynamicSourceRulesPanel(
    private val project: Project,
    private val save: (List<DynamicSourceRuleDto>) -> Unit,
    private val reportError: (String) -> Unit,
    private val convertToMarker: (DynamicSourceRuleDto, List<DynamicSourceRuleDto>) -> Unit,
    private val navigateToMarker: (DynamicSourceRuleDto) -> Unit,
) : JPanel(BorderLayout()) {
    private val cards = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val scrollPane = JBScrollPane(cards).apply {
        verticalScrollBar.unitIncrement = JBUI.scale(18)
        horizontalScrollBar.unitIncrement = JBUI.scale(18)
    }
    private val rows = mutableListOf<RuleRow>()
    private var entries: List<LanguageEntryDto> = emptyList()
    private var schemeId: String? = null
    private var renderedRules: List<DynamicSourceRuleDto> = emptyList()
    private var pendingFocusRuleId: String? = null

    init {
        add(
            JPanel(FlowLayout(FlowLayout.LEFT, 6, 3)).apply {
                add(JBLabel(message("dynamic.wizard.help")))
                add(JButton(message("dynamic.rule.add")).apply { addActionListener { addRule() } })
                add(JButton(message("dynamic.save")).apply {
                    addActionListener {
                        validationMessage()?.let(reportError) ?: save(rows.map { it.value() })
                    }
                })
            },
            BorderLayout.NORTH,
        )
        add(scrollPane, BorderLayout.CENTER)
    }

    fun render(
        scheme: LanguageSchemeDto?,
        currentEntries: List<LanguageEntryDto>,
    ) {
        entries = currentEntries
        if (scheme?.id == schemeId && scheme?.dynamicSourceRules.orEmpty() == renderedRules) return
        schemeId = scheme?.id
        renderedRules = scheme?.dynamicSourceRules.orEmpty()
        rows.clear()
        cards.removeAll()
        renderedRules.forEach(::addRule)
        cards.revalidate()
        cards.repaint()
        pendingFocusRuleId?.let(::focusRule)
    }

    fun addDraft(command: LocalizationUiCommand.AddDynamicSource) {
        addRule(
            DynamicSourceRuleDto(
                id = UUID.randomUUID().toString(),
                filePath = command.filePath,
                line = command.line,
                column = command.column,
                groups = listOf(DynamicSourceGroupDto("enum", splitSelectedKeys(command.selectedText))),
            ),
        )
    }

    fun focusRule(ruleId: String) {
        pendingFocusRuleId = ruleId
        val row = rows.firstOrNull { it.id == ruleId } ?: return
        javax.swing.SwingUtilities.invokeLater {
            cards.scrollRectToVisible(row.panel.bounds)
            row.panel.requestFocusInWindow()
            pendingFocusRuleId = null
        }
    }

    private fun addRule(initial: DynamicSourceRuleDto? = null) {
        lateinit var row: RuleRow
        row = RuleRow(initial ?: DynamicSourceRuleDto(UUID.randomUUID().toString(), "", 1, 1, groups = listOf(DynamicSourceGroupDto()))) {
            rows.remove(row)
            cards.remove(row.panel)
            cards.revalidate()
            cards.repaint()
        }
        rows += row
        cards.add(row.panel)
        cards.revalidate()
        cards.repaint()
        javax.swing.SwingUtilities.invokeLater { row.path.requestFocusInWindow() }
    }

    private fun validationMessage(): String? {
        if (schemeId == null) return message("error.no.active.scheme")
        rows.forEach { row ->
            if (row.path.text.isBlank()) return message("dynamic.validation.file.required")
            row.groups.validationInfo()?.let { return it.message }
        }
        return null
    }

    private inner class RuleRow(
        initial: DynamicSourceRuleDto,
        remove: () -> Unit,
    ) {
        val id = initial.id
        val path = JBTextField(initial.filePath, 42)
        private val line = JSpinner(SpinnerNumberModel(initial.line.coerceAtLeast(1), 1, 10_000_000, 1))
        private val column = JSpinner(SpinnerNumberModel(initial.column.coerceAtLeast(1), 1, 10_000_000, 1))
        val groups: DynamicGroupsEditor
        val panel: JPanel

        init {
            groups = DynamicGroupsEditor(
                project,
                entries,
                initial.groups,
                message("dynamic.convert.to.marker"),
            ) {
                validationMessage()?.let(reportError) ?: convertToMarker(value(), rows.map { it.value() })
            }
            panel = JPanel(BorderLayout(6, 5)).apply {
                border = JBUI.Borders.compound(JBUI.Borders.customLine(com.intellij.ui.JBColor.border()), JBUI.Borders.empty(6))
                add(
                    JPanel().apply {
                        layout = BoxLayout(this, BoxLayout.Y_AXIS)
                        add(
                            JPanel(FlowLayout(FlowLayout.LEFT, 5, 0)).apply {
                                alignmentX = LEFT_ALIGNMENT
                                add(JBLabel(message("dynamic.file")))
                                add(path)
                                add(JBLabel(message("dynamic.line")))
                                add(line)
                                add(JBLabel(message("dynamic.column")))
                                add(column)
                            },
                        )
                        add(
                            JPanel(FlowLayout(FlowLayout.LEFT, 5, 3)).apply {
                                alignmentX = LEFT_ALIGNMENT
                                add(JButton(message("dynamic.navigate.to.marker")).apply {
                                    addActionListener { navigateToMarker(value()) }
                                })
                                add(JButton(message("button.remove")).apply { addActionListener { remove() } })
                            },
                        )
                    },
                    BorderLayout.NORTH,
                )
                add(groups, BorderLayout.CENTER)
            }
        }

        fun value() =
            DynamicSourceRuleDto(
                id = id,
                filePath = path.text.trim(),
                line = line.value as Int,
                column = column.value as Int,
                groups = groups.groups(),
            )
    }
}
