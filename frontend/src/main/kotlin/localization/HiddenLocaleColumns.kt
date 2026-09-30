package cg.creamgod45.localization.ui

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.project.Project

/**
 * Remembers which language columns are hidden in the translation table. Stored per project and per scheme ID, so
 * hiding a column in one scheme never affects another scheme. This is view state only; no language file changes.
 */
internal class HiddenLocaleColumns(
    private val project: Project,
) {
    fun hidden(schemeId: String): Set<String> = properties().getList(key(schemeId)).orEmpty().toSet()

    fun setHidden(
        schemeId: String,
        locale: String,
        hidden: Boolean,
    ) {
        val current = hidden(schemeId)
        store(schemeId, if (hidden) current + locale else current - locale)
    }

    fun showAll(schemeId: String) = store(schemeId, emptySet())

    private fun store(
        schemeId: String,
        locales: Set<String>,
    ) {
        // setList(null) removes a list value; unsetValue only clears plain string values.
        properties().setList(key(schemeId), locales.sorted().ifEmpty { null })
    }

    private fun properties() = PropertiesComponent.getInstance(project)

    private fun key(schemeId: String) = "cg.creamgod45.languageManager.hiddenLocales.$schemeId"
}
