package cg.creamgod45

import cg.creamgod45.settings.DisplayLanguage
import cg.creamgod45.settings.LanguageManagerSettings
import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey
import java.util.ResourceBundle

private const val BUNDLE = "messages.LanguageManagerFrontendBundle"
private val NO_LOCALE_FALLBACK = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_DEFAULT)

internal object LanguageManagerBundle : DynamicBundle(BUNDLE) {
    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): String = messageForLanguage(LanguageManagerSettings.currentLanguage(), key, *params)

    internal fun messageForLanguage(
        language: DisplayLanguage,
        key: String,
        vararg params: Any,
    ): String {
        val locale = language.locale ?: return getMessage(key, *params)
        val bundle = ResourceBundle.getBundle(BUNDLE, locale, javaClass.classLoader, NO_LOCALE_FALLBACK)
        return AbstractBundle.message(bundle, key, *params)
    }
}
