package cg.creamgod45

import cg.creamgod45.localization.MAX_LOCALE_NOTE_CHARS
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal object LocaleMetadataSupport {
    private val localePattern = Regex("[A-Za-z][A-Za-z0-9_-]{0,31}")

    fun normalizeNote(note: String): String {
        val normalized = note.trim()
        require(normalized.length <= MAX_LOCALE_NOTE_CHARS && normalized.none(Char::isISOControl)) {
            backendMessage("locale.note.invalid", MAX_LOCALE_NOTE_CHARS)
        }
        return normalized
    }

    fun normalizeNotes(notes: Map<String, String>): Map<String, String> {
        require(notes.size <= 256) { backendMessage("locale.notes.count.invalid") }
        return notes.entries.associate { (locale, note) ->
            require(locale.matches(localePattern)) { backendMessage("locale.invalid") }
            locale to normalizeNote(note)
        }.filterValues(String::isNotBlank)
    }
}
