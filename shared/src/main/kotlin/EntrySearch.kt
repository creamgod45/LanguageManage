package cg.creamgod45.localization

object EntrySearch {
    fun findInFilesQuery(row: JoinedTranslationRow): String = row.key

    fun usageRegexFindInFilesQuery(
        row: JoinedTranslationRow,
        patterns: List<String>,
    ): String? =
        patterns
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .mapNotNull { injectLiteralKey(it, row.key) }
            .firstOrNull()

    private fun injectLiteralKey(
        pattern: String,
        key: String,
    ): String? {
        val marker = "(?<key>"
        val groupStart = pattern.indexOf(marker)
        if (groupStart < 0) return null

        var depth = 1
        var inCharacterClass = false
        var escaped = false
        var index = groupStart + marker.length
        while (index < pattern.length) {
            val character = pattern[index]
            when {
                escaped -> {
                    escaped = false
                }

                character == '\\' -> {
                    escaped = true
                }

                character == '[' && !inCharacterClass -> {
                    inCharacterClass = true
                }

                character == ']' && inCharacterClass -> {
                    inCharacterClass = false
                }

                !inCharacterClass && character == '(' -> {
                    depth++
                }

                !inCharacterClass && character == ')' -> {
                    depth--
                    if (depth == 0) break
                }
            }
            index++
        }
        if (depth != 0) return null

        val withKey = pattern.replaceRange(groupStart, index + 1, escapeRegexLiteral(key)).trim()
        val withoutStartAnchor = withKey.removePrefix("^")
        return if (withoutStartAnchor.endsWithUnescapedDollar()) withoutStartAnchor.dropLast(1) else withoutStartAnchor
    }

    private fun String.endsWithUnescapedDollar(): Boolean {
        if (!endsWith('$')) return false
        var precedingBackslashes = 0
        var index = lastIndex - 1
        while (index >= 0 && this[index] == '\\') {
            precedingBackslashes++
            index--
        }
        return precedingBackslashes % 2 == 0
    }

    private fun escapeRegexLiteral(value: String): String =
        buildString(value.length) {
            value.forEach { character ->
                if (character in REGEX_META_CHARACTERS) append('\\')
                append(character)
            }
        }

    fun filter(
        entries: List<LanguageEntryDto>,
        query: String,
        mode: SearchMode,
        locale: String?,
    ): List<LanguageEntryDto> {
        val normalized = query.trim()
        return entries.filter { entry ->
            (locale.isNullOrBlank() || entry.locale == locale) &&
                when {
                    normalized.isEmpty() -> {
                        true
                    }

                    mode == SearchMode.EXACT -> {
                        listOf(entry.key, entry.value, entry.namespace, "${entry.namespace}.${entry.key}").any {
                            it.equals(normalized, true)
                        }
                    }

                    else -> {
                        listOf(entry.key, entry.value, entry.namespace, entry.locale, entry.filePath).any { it.contains(normalized, true) }
                    }
                }
        }
    }

    fun join(entries: List<LanguageEntryDto>): List<JoinedTranslationRow> =
        entries
            .groupBy { it.namespace to it.key }
            .map { (identity, translations) ->
                JoinedTranslationRow(identity.first, identity.second, translations.sortedBy { it.locale })
            }.sortedWith(compareBy<JoinedTranslationRow> { it.namespace }.thenBy { it.key })

    fun filterRows(
        rows: List<JoinedTranslationRow>,
        locales: Set<String>,
        filter: TranslationRowFilter,
    ): List<JoinedTranslationRow> =
        when (filter) {
            TranslationRowFilter.ALL -> {
                rows
            }

            TranslationRowFilter.MISSING_TRANSLATION -> {
                rows.filter { row ->
                    locales.any { locale -> row.translations.none { it.locale == locale && it.value.isNotBlank() } }
                }
            }

            TranslationRowFilter.ZERO_USAGE -> {
                rows.filter { it.usageCount == 0 }
            }
        }

    fun paginate(
        rows: List<JoinedTranslationRow>,
        requestedPage: Int,
        pageSize: Int = 100,
    ): JoinedTranslationPage {
        require(pageSize in 1..100) { "每頁筆數必須介於 1 到 100" }
        val pageCount = maxOf(1, (rows.size + pageSize - 1) / pageSize)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        return JoinedTranslationPage(rows.drop(page * pageSize).take(pageSize), page, pageCount, rows.size)
    }

    fun deletionFor(rows: List<JoinedTranslationRow>): JoinedTranslationDeletion {
        val selectedRows = rows.distinctBy { it.namespace to it.key }
        return JoinedTranslationDeletion(
            rowCount = selectedRows.size,
            entryIds = selectedRows.flatMap { it.translations }.map { it.id }.distinct(),
        )
    }

    private val REGEX_META_CHARACTERS = setOf('\\', '.', '^', '$', '|', '?', '*', '+', '(', ')', '[', ']', '{', '}')
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

data class JoinedTranslationDeletion(
    val rowCount: Int,
    val entryIds: List<String>,
)
