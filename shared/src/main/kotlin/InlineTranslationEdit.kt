package cg.creamgod45.localization

sealed interface InlineTranslationEditResult {
    data object NoChange : InlineTranslationEditResult

    data class MissingLocaleFile(
        val locale: String,
    ) : InlineTranslationEditResult

    data class Save(
        val mutation: EntryMutationDto,
    ) : InlineTranslationEditResult
}

object InlineTranslationEdit {
    /**
     * A translation cell can be edited in place when it maps to at most one entry. Long and multi-line values are edited
     * in place too; only a cell that joins several same-locale values is ambiguous and needs Edit Selected.
     */
    fun isEditable(
        row: JoinedTranslationRow,
        locale: String,
    ): Boolean = row.translations.count { it.locale == locale } <= 1

    fun resolve(
        row: JoinedTranslationRow,
        locale: String,
        newValue: String,
        schemeEntries: List<LanguageEntryDto>,
    ): InlineTranslationEditResult {
        val existing = row.translations.firstOrNull { it.locale == locale }
        if (existing == null && newValue.isEmpty()) return InlineTranslationEditResult.NoChange
        if (existing != null && existing.value == newValue) return InlineTranslationEditResult.NoChange
        val targetFile =
            targetFile(row, locale, schemeEntries)
                ?: return InlineTranslationEditResult.MissingLocaleFile(locale)
        return InlineTranslationEditResult.Save(
            EntryMutationDto(existing?.id?.takeIf(String::isNotBlank), targetFile, locale, row.namespace, row.key, newValue),
        )
    }

    fun targetFile(
        row: JoinedTranslationRow,
        locale: String,
        schemeEntries: List<LanguageEntryDto>,
    ): String? =
        row.translations.firstOrNull { it.locale == locale }?.filePath
            ?: schemeEntries.firstOrNull { it.locale == locale && it.namespace == row.namespace }?.filePath
            ?: schemeEntries.firstOrNull { it.locale == locale }?.filePath
}
