package cg.creamgod45.settings

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.toolWindow.LanguageManagerToolWindowFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent

class LanguageManagerSettingsConfigurable : Configurable {
    private var languageBox: ComboBox<DisplayLanguage>? = null

    override fun getDisplayName(): String = message("settings.display.name")

    override fun createComponent(): JComponent {
        languageBox = ComboBox(DisplayLanguage.entries.toTypedArray()).apply {
            renderer = SimpleListCellRenderer.create { label, value, _ ->
                label.text = message(value.messageKey())
            }
        }
        reset()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(message("settings.language.label"), languageBox!!)
            .addComponentFillVertically(javax.swing.JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean =
        languageBox?.selectedItem != LanguageManagerSettings.getInstance().displayLanguage

    override fun apply() {
        val selected = languageBox?.selectedItem as? DisplayLanguage ?: DisplayLanguage.AUTO
        if (selected == LanguageManagerSettings.getInstance().displayLanguage) return
        LanguageManagerSettings.getInstance().displayLanguage = selected
        LanguageManagerToolWindowFactory.refreshOpenToolWindows()
    }

    override fun reset() {
        languageBox?.selectedItem = LanguageManagerSettings.getInstance().displayLanguage
    }

    override fun disposeUIResources() {
        languageBox = null
    }
}

private fun DisplayLanguage.messageKey(): String = when (this) {
    DisplayLanguage.AUTO -> "settings.language.auto"
    DisplayLanguage.ENGLISH -> "settings.language.english"
    DisplayLanguage.TRADITIONAL_CHINESE -> "settings.language.traditional.chinese"
    DisplayLanguage.SIMPLIFIED_CHINESE -> "settings.language.simplified.chinese"
    DisplayLanguage.JAPANESE -> "settings.language.japanese"
    DisplayLanguage.KOREAN -> "settings.language.korean"
}
