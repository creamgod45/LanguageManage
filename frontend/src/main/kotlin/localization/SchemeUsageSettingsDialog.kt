package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.DEFAULT_USAGE_EXCLUDED_DIRECTORIES
import cg.creamgod45.localization.DEFAULT_USAGE_REGEX_PATTERNS
import cg.creamgod45.localization.LanguageSchemeDto
import cg.creamgod45.localization.UsageScanSettingsDto
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal class SchemeUsageSettingsDialog(
    private val dialogProject: Project,
    private val scheme: LanguageSchemeDto,
) : DialogWrapper(dialogProject) {
    private val filesArea =
        JBTextArea(scheme.files.joinToString("\n"), 4, 48).apply {
            isEditable = false
            lineWrap = false
        }
    private val basePathField = JBTextField(scheme.usageScanSettings.basePath)
    private val regexModel = DefaultListModel<String>().apply { replaceWith(scheme.usageScanSettings.regexPatterns) }
    private val exclusionModel = DefaultListModel<String>().apply { replaceWith(scheme.usageScanSettings.excludedDirectories) }

    init {
        title = message("dialog.scheme.settings.title", scheme.name)
        init()
    }

    fun result(): UsageScanSettingsDto =
        UsageScanSettingsDto(
            basePath = basePathField.text.trim(),
            regexPatterns = regexModel.values(),
            excludedDirectories = exclusionModel.values(),
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
                .addLabeledComponent(
                    message("settings.scheme.files"),
                    JBScrollPane(filesArea).apply {
                        preferredSize = Dimension(JBUI.scale(620), JBUI.scale(90))
                    },
                ).addLabeledComponent(message("settings.usage.base.path"), basePathPanel)
                .addTooltip(message("settings.usage.base.path.help"))
                .addLabeledComponent(
                    message("settings.usage.regex"),
                    listEditor(
                        regexModel,
                        message("settings.regex.add.prompt"),
                        message("settings.regex.edit.prompt"),
                        DEFAULT_USAGE_REGEX_PATTERNS,
                    ),
                ).addTooltip(message("settings.usage.regex.help"))
                .addLabeledComponent(
                    message("settings.usage.exclusions"),
                    listEditor(
                        exclusionModel,
                        message("settings.exclusion.add.prompt"),
                        message("settings.exclusion.edit.prompt"),
                        DEFAULT_USAGE_EXCLUDED_DIRECTORIES,
                    ),
                ).addTooltip(message("settings.usage.exclusions.help"))
                .panel
        panel.preferredSize = Dimension(JBUI.scale(760), JBUI.scale(500))
        return panel
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
    ): JComponent {
        val list = JBList(model).apply { visibleRowCount = 5 }
        return JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(JBScrollPane(list).apply { preferredSize = Dimension(JBUI.scale(620), JBUI.scale(110)) }, BorderLayout.CENTER)
            add(
                JPanel(FlowLayout(FlowLayout.LEADING, JBUI.scale(4), 0)).apply {
                    add(
                        JButton(message("settings.list.add")).apply {
                            addActionListener {
                                Messages
                                    .showInputDialog(dialogProject, addPrompt, title, null)
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
                                Messages
                                    .showInputDialog(dialogProject, editPrompt, title, null, model[index], null)
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
                },
                BorderLayout.SOUTH,
            )
        }
    }
}

private fun DefaultListModel<String>.values(): List<String> = (0 until size()).map(::getElementAt)

private fun DefaultListModel<String>.replaceWith(values: List<String>) {
    clear()
    values.forEach(::addElement)
}
