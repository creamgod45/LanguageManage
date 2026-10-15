package cg.creamgod45.settings

import cg.creamgod45.LanguageManagerBundle.message
import com.intellij.openapi.options.Configurable
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/** "Experimental" child page of the Language Manager settings: features that may still change or be removed. */
class LanguageManagerExperimentalConfigurable : Configurable {
    private var editorKeyHoverBox: JBCheckBox? = null
    private var editorKeyHoverDelaySpinner: JSpinner? = null

    override fun getDisplayName(): String = message("settings.experimental.display.name")

    override fun createComponent(): JComponent {
        val delaySpinner =
            JSpinner(
                SpinnerNumberModel(
                    editorKeyHoverDelaySeconds(DEFAULT_EDITOR_KEY_HOVER_DELAY_MS),
                    MIN_EDITOR_KEY_HOVER_DELAY_MS / 1_000.0,
                    MAX_EDITOR_KEY_HOVER_DELAY_MS / 1_000.0,
                    0.1,
                ),
            ).apply { editor = JSpinner.NumberEditor(this, "0.0") }
        val hoverBox = JBCheckBox(message("settings.experimental.editor.hover")).apply { addActionListener { updateEnabled() } }
        editorKeyHoverBox = hoverBox
        editorKeyHoverDelaySpinner = delaySpinner
        val form =
            FormBuilder
                .createFormBuilder()
                .addComponent(
                    JBLabel(message("settings.experimental.description")).apply {
                        foreground =
                            JBUI.CurrentTheme.ContextHelp.FOREGROUND
                    },
                ).addSeparator()
                .addComponent(JBLabel(message("settings.experimental.editor.hover.title")))
                .addComponent(hoverBox)
                .addTooltip(message("settings.experimental.editor.hover.help"))
                .addLabeledComponent(message("settings.experimental.editor.hover.delay"), delaySpinner)
                .addTooltip(
                    message(
                        "settings.experimental.editor.hover.delay.help",
                        editorKeyHoverDelaySeconds(MIN_EDITOR_KEY_HOVER_DELAY_MS),
                        editorKeyHoverDelaySeconds(MAX_EDITOR_KEY_HOVER_DELAY_MS),
                    ),
                ).addComponentFillVertically(JPanel(), 0)
                .panel
        reset()
        return JPanel(BorderLayout()).apply { add(form, BorderLayout.NORTH) }
    }

    override fun isModified(): Boolean {
        val settings = LanguageManagerSettings.getInstance()
        return editorKeyHoverBox?.isSelected != settings.editorKeyHoverEnabled || selectedDelayMs() != settings.editorKeyHoverDelayMs
    }

    override fun apply() {
        val settings = LanguageManagerSettings.getInstance()
        settings.editorKeyHoverEnabled = editorKeyHoverBox?.isSelected ?: false
        settings.editorKeyHoverDelayMs = selectedDelayMs()
    }

    override fun reset() {
        val settings = LanguageManagerSettings.getInstance()
        editorKeyHoverBox?.isSelected = settings.editorKeyHoverEnabled
        editorKeyHoverDelaySpinner?.value = editorKeyHoverDelaySeconds(settings.editorKeyHoverDelayMs)
        updateEnabled()
    }

    override fun disposeUIResources() {
        editorKeyHoverBox = null
        editorKeyHoverDelaySpinner = null
    }

    private fun selectedDelayMs(): Int =
        editorKeyHoverDelayMsFromSeconds((editorKeyHoverDelaySpinner?.value as? Number)?.toDouble() ?: Double.NaN)

    private fun updateEnabled() {
        editorKeyHoverDelaySpinner?.isEnabled = editorKeyHoverBox?.isSelected == true
    }
}
