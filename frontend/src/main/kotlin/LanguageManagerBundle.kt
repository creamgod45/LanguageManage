package cg.creamgod45

import cg.creamgod45.settings.DisplayLanguage
import cg.creamgod45.settings.LanguageManagerSettings
import com.intellij.AbstractBundle
import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.LanguageManagerFrontendBundle"

internal object LanguageManagerBundle : DynamicBundle(BUNDLE) {
    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String =
        messageForLanguage(LanguageManagerSettings.currentLanguage(), key, *params)

    internal fun messageForLanguage(language: DisplayLanguage, key: String, vararg params: Any): String {
        val locale = language.locale ?: return getMessage(key, *params)
        val bundle = DynamicBundle.getResourceBundle(javaClass.classLoader, BUNDLE, locale)
        return AbstractBundle.message(bundle, key, *params)
    }
}
