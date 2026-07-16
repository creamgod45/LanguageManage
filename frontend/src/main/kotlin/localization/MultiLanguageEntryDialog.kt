package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.EntryMutationDto
import cg.creamgod45.localization.JoinedTranslationRow
import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.LanguageSchemeDto
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.nio.file.Path
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

internal data class TranslationEditorTarget(
    val filePath: String,
    val locale: String,
    val namespace: String,
)

internal object TranslationEditorSupport {
    private val propertiesLocaleSuffix = Regex("^(.+)_([a-z]{2,3}(?:_[A-Z]{2})?)$")

    fun targets(
        scheme: LanguageSchemeDto,
        entries: List<LanguageEntryDto>,
    ): List<TranslationEditorTarget> {
        val known = entries.groupBy(LanguageEntryDto::filePath).mapValues { it.value.first() }
        return scheme.files
            .map { filePath ->
                known[filePath]?.let { entry ->
                    TranslationEditorTarget(filePath, entry.locale, entry.namespace)
                } ?: inferTarget(filePath)
            }.distinctBy(TranslationEditorTarget::filePath)
            .sortedWith(compareBy<TranslationEditorTarget> { it.namespace }.thenBy { it.locale }.thenBy { it.filePath })
    }

    private fun inferTarget(filePath: String): TranslationEditorTarget {
        val path = Path.of(filePath)
        return when (path.extension.lowercase()) {
            "php" -> {
                TranslationEditorTarget(
                    filePath,
                    path.parent
                        ?.fileName
                        ?.toString()
                        .orEmpty(),
                    path.nameWithoutExtension,
                )
            }

            "properties" -> {
                val match = propertiesLocaleSuffix.matchEntire(path.nameWithoutExtension)
                if (match == null) {
                    TranslationEditorTarget(filePath, "en", path.nameWithoutExtension)
                } else {
                    TranslationEditorTarget(filePath, match.groupValues[2], match.groupValues[1])
                }
            }

            else -> {
                TranslationEditorTarget(filePath, path.nameWithoutExtension, "")
            }
        }
    }
}

internal class MultiLanguageEntryDialog(
    project: Project,
    scheme: LanguageSchemeDto,
    private val entries: List<LanguageEntryDto>,
    private val row: JoinedTranslationRow?,
) : DialogWrapper(project) {
    private val allTargets = TranslationEditorSupport.targets(scheme, entries)
    private val namespaces =
        (if (row == null) allTargets.map(TranslationEditorTarget::namespace).distinct() else listOf(row.namespace))
            .ifEmpty { listOf("") }
    private val namespaceBox =
        ComboBox(namespaces.toTypedArray()).apply {
            renderer = namespaceRenderer()
            isEnabled = row == null && namespaces.size > 1
        }
    private val keyField = JBTextField(row?.key.orEmpty()).apply { isEditable = row == null }
    private val editorPanel = JPanel(GridBagLayout())
    private val editors = linkedMapOf<TranslationEditorTarget, JBTextArea>()
    private val draftValues = mutableMapOf<String, String>()

    init {
        title = message(if (row == null) "dialog.add.translation.title" else "dialog.edit.translation.title")
        namespaceBox.selectedItem = row?.namespace ?: namespaces.first()
        namespaceBox.addActionListener { rebuildEditors() }
        rebuildEditors()
        init()
    }

    fun mutations(): List<EntryMutationDto> {
        saveDraftValues()
        val key = keyField.text.trim()
        return editors.mapNotNull { (target, editor) ->
            val existing =
                row?.translations?.firstOrNull { it.filePath == target.filePath }
                    ?: row?.translations?.firstOrNull { it.locale == target.locale && it.namespace == target.namespace }
            if (row != null && existing == null && editor.text.isEmpty()) return@mapNotNull null
            EntryMutationDto(
                id = existing?.id?.takeIf(String::isNotBlank),
                filePath = target.filePath,
                locale = target.locale,
                namespace = target.namespace,
                key = key,
                value = editor.text,
            )
        }
    }

    override fun createCenterPanel(): JComponent {
        val header =
            FormBuilder
                .createFormBuilder()
                .addComponent(JBLabel(message("dialog.translation.all.help")))
                .addLabeledComponent(message("field.namespace"), namespaceBox)
                .addLabeledComponent(message("field.key"), keyField)
                .panel
        val scrollPane =
            JBScrollPane(editorPanel).apply {
                border = JBUI.Borders.emptyTop(8)
                verticalScrollBar.unitIncrement = JBUI.scale(16)
                horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            }
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
            add(header, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            preferredSize = Dimension(JBUI.scale(800), JBUI.scale(560))
            minimumSize = Dimension(JBUI.scale(500), JBUI.scale(360))
        }
    }

    override fun doValidate(): ValidationInfo? =
        when {
            keyField.text.trim().isEmpty() -> ValidationInfo(message("error.translation.key.required"), keyField)
            editors.isEmpty() -> ValidationInfo(message("error.translation.targets.none"), namespaceBox)
            else -> null
        }

    private fun rebuildEditors() {
        saveDraftValues()
        editors.clear()
        editorPanel.removeAll()
        val namespace = namespaceBox.selectedItem?.toString().orEmpty()
        val selectedTargets =
            allTargets
                .filter { it.namespace == namespace }
                .groupBy(TranslationEditorTarget::locale)
                .map { (locale, targets) ->
                    targets.firstOrNull { target -> row?.translations?.any { it.filePath == target.filePath } == true }
                        ?: targets.minBy { it.filePath }.copy(locale = locale)
                }.sortedBy(TranslationEditorTarget::locale)

        selectedTargets.forEachIndexed { index, target ->
            val existing =
                row?.translations?.firstOrNull { it.filePath == target.filePath }
                    ?: row?.translations?.firstOrNull { it.locale == target.locale && it.namespace == target.namespace }
            val editor =
                JBTextArea(draftValues[target.filePath] ?: existing?.value.orEmpty(), 3, 50).apply {
                    lineWrap = true
                    wrapStyleWord = true
                }
            editors[target] = editor
            editorPanel.add(
                translationSection(target, editor),
                GridBagConstraints().apply {
                    gridx = 0
                    gridy = index
                    weightx = 1.0
                    fill = GridBagConstraints.HORIZONTAL
                    anchor = GridBagConstraints.NORTH
                    insets = JBUI.insetsBottom(8)
                },
            )
        }
        editorPanel.add(
            JPanel(),
            GridBagConstraints().apply {
                gridx = 0
                gridy = selectedTargets.size
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
            },
        )
        editorPanel.revalidate()
        editorPanel.repaint()
    }

    private fun translationSection(
        target: TranslationEditorTarget,
        editor: JBTextArea,
    ): JComponent {
        val height = JBUI.scale(72)
        val valueScroll =
            JBScrollPane(editor).apply {
                preferredSize = Dimension(JBUI.scale(620), height)
                minimumSize = Dimension(JBUI.scale(280), height)
            }
        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            border = JBUI.Borders.compound(JBUI.Borders.customLine(JBColor.border()), JBUI.Borders.empty(8))
            add(JBLabel(message("dialog.translation.locale.section", target.locale)), BorderLayout.NORTH)
            add(valueScroll, BorderLayout.CENTER)
            add(
                JBLabel(target.filePath).apply {
                    toolTipText = target.filePath
                    foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                },
                BorderLayout.SOUTH,
            )
            preferredSize = Dimension(JBUI.scale(700), JBUI.scale(118))
            minimumSize = Dimension(JBUI.scale(320), JBUI.scale(108))
        }
    }

    private fun saveDraftValues() {
        editors.forEach { (target, editor) -> draftValues[target.filePath] = editor.text }
    }

    private fun namespaceRenderer(): DefaultListCellRenderer =
        object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                selected: Boolean,
                focus: Boolean,
            ) = super.getListCellRendererComponent(
                list,
                value?.toString()?.ifBlank { message("field.namespace.root") },
                index,
                selected,
                focus,
            )
        }
}
