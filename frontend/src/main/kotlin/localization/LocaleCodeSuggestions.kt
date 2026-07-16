package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import java.util.Locale

internal data class LocaleCodeSuggestion(
    val code: String,
    val displayName: String,
) {
    val label: String get() = "$code — $displayName"

    override fun toString(): String = label
}

internal object LocaleCodeCatalog {
    private val recommendedTags =
        listOf(
            "en-US", "en-GB", "es-ES", "es-MX", "es-419", "fr-FR", "fr-CA", "pt-BR", "pt-PT",
            "zh-CN", "zh-TW", "zh-Hans", "zh-Hant", "sr-Cyrl", "sr-Latn", "ja-JP", "ko-KR", "th-TH",
        )

    val suggestions: List<LocaleCodeSuggestion> by lazy {
        (recommendedTags + Locale.getISOLanguages().toList())
            .distinctBy { it.lowercase() }
            .map { code ->
                val locale = Locale.forLanguageTag(code)
                LocaleCodeSuggestion(code, locale.getDisplayName(locale).ifBlank { code })
            }
    }

    fun matching(query: String): List<LocaleCodeSuggestion> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return suggestions
        return suggestions.filter { suggestion ->
            suggestion.code.lowercase().contains(needle) || suggestion.displayName.lowercase().contains(needle)
        }
    }

    fun extractCode(editorText: String): String = editorText.substringBefore(" — ").trim()
}

internal class LocaleCodeField : TextFieldWithBrowseButton() {
    init {
        addActionListener { showSuggestions() }
    }

    val localeCode: String get() = LocaleCodeCatalog.extractCode(text)

    private fun showSuggestions() {
        val matches = LocaleCodeCatalog.matching(text).ifEmpty { LocaleCodeCatalog.suggestions }
        JBPopupFactory
            .getInstance()
            .createPopupChooserBuilder(matches)
            .setTitle(message("field.locale.version.suggestions"))
            .setItemChosenCallback { suggestion: LocaleCodeSuggestion ->
                this@LocaleCodeField.text = suggestion.code
                textField.caretPosition = this@LocaleCodeField.text.length
            }.createPopup()
            .showUnderneathOf(this)
    }
}
