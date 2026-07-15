package cg.creamgod45

import cg.creamgod45.LanguageManagerBundle.message
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import javax.swing.JComponent

internal object RegexPatternUi {
    fun helpComponent(): JComponent =
        JBTextArea(message("settings.usage.regex.help.full"), 5, 64).apply {
            isEditable = false
            isFocusable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            border = JBUI.Borders.empty(2, 0, 6, 0)
        }

    fun showInputDialog(
        project: Project,
        title: String,
        prompt: String,
        initialValue: String? = null,
    ): String? {
        val dialog = RegexInputDialog(project, title, prompt, initialValue.orEmpty())
        return if (dialog.showAndGet()) dialog.value() else null
    }

    private class RegexInputDialog(
        project: Project,
        dialogTitle: String,
        private val prompt: String,
        initialValue: String,
    ) : DialogWrapper(project) {
        private val field =
            JBTextField(initialValue).apply {
                emptyText.text = message("settings.regex.placeholder")
            }

        init {
            title = dialogTitle
            init()
        }

        fun value(): String = field.text.trim()

        override fun getPreferredFocusedComponent(): JComponent = field

        override fun createCenterPanel(): JComponent =
            FormBuilder
                .createFormBuilder()
                .addLabeledComponent(prompt, field)
                .addComponent(helpComponent())
                .panel
                .apply { preferredSize = Dimension(JBUI.scale(660), preferredSize.height) }
    }
}
