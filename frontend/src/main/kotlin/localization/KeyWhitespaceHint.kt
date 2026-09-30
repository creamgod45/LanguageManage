package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import com.intellij.icons.AllIcons
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import javax.swing.event.DocumentEvent
import javax.swing.text.JTextComponent

/**
 * Keys are saved exactly as typed because surrounding spaces may be intentional, so `" app.name"` and `app.name`
 * are different keys. This hint makes such spaces visible while typing instead of silently removing them.
 */
internal object KeyWhitespace {
    /** Includes IME full-width spaces (U+3000), which are easy to type by accident and hard to see. */
    fun hasSurroundingWhitespace(text: String): Boolean = text.isNotBlank() && text != text.trim()
}

internal class KeyWhitespaceHint(
    private val field: JTextComponent,
) : JBLabel() {
    init {
        icon = AllIcons.General.Warning
        setAllowAutoWrapping(true)
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
        field.document.addDocumentListener(
            object : DocumentAdapter() {
                override fun textChanged(event: DocumentEvent) = refresh()
            },
        )
        refresh()
    }

    private fun refresh() {
        val raw = field.text
        isVisible = KeyWhitespace.hasSurroundingWhitespace(raw)
        text = if (isVisible) message("hint.key.surrounding.whitespace") else ""
    }
}
