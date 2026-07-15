package cg.creamgod45

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

private const val BACKEND_BUNDLE = "messages.LanguageManagerBackendBundle"

internal object LanguageManagerBackendBundle : DynamicBundle(BACKEND_BUNDLE) {
    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = BACKEND_BUNDLE) key: String,
        vararg params: Any,
    ): String = getMessage(key, *params)
}
