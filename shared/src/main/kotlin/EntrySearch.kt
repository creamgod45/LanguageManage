package cg.creamgod45.localization

object EntrySearch {
    fun filter(entries: List<LanguageEntryDto>, query: String, mode: SearchMode, locale: String?): List<LanguageEntryDto> {
        val normalized = query.trim()
        return entries.filter { entry ->
            (locale.isNullOrBlank() || entry.locale == locale) && when {
                normalized.isEmpty() -> true
                mode == SearchMode.EXACT -> listOf(entry.key, entry.value, entry.namespace, "${entry.namespace}.${entry.key}").any { it.equals(normalized, true) }
                else -> listOf(entry.key, entry.value, entry.namespace, entry.locale, entry.filePath).any { it.contains(normalized, true) }
            }
        }
    }

    fun join(entries: List<LanguageEntryDto>): List<JoinedTranslationRow> = entries
        .groupBy { it.namespace to it.key }
        .map { (identity, translations) ->
            JoinedTranslationRow(identity.first, identity.second, translations.sortedBy { it.locale })
        }
        .sortedWith(compareBy<JoinedTranslationRow> { it.namespace }.thenBy { it.key })

    fun paginate(rows: List<JoinedTranslationRow>, requestedPage: Int, pageSize: Int = 100): JoinedTranslationPage {
        require(pageSize in 1..100) { "每頁筆數必須介於 1 到 100" }
        val pageCount = maxOf(1, (rows.size + pageSize - 1) / pageSize)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        return JoinedTranslationPage(rows.drop(page * pageSize).take(pageSize), page, pageCount, rows.size)
    }
}

data class JoinedTranslationRow(
    val namespace: String,
    val key: String,
    val translations: List<LanguageEntryDto>,
) {
    fun values(locale: String): String = translations.filter { it.locale == locale }.joinToString(" | ") { it.value }
    val usageCount: Int get() = translations.maxOfOrNull { it.usageCount } ?: 0
}

data class JoinedTranslationPage(
    val rows: List<JoinedTranslationRow>,
    val page: Int,
    val pageCount: Int,
    val totalRows: Int,
)
