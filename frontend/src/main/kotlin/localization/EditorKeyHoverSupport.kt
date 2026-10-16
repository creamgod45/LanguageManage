package cg.creamgod45.localization.ui

import cg.creamgod45.localization.EditorKeyMatchDto
import cg.creamgod45.localization.EntrySearch
import cg.creamgod45.localization.JoinedTranslationRow
import cg.creamgod45.localization.LanguageEntryDto
import java.awt.Dimension
import java.awt.Rectangle

/** Pure rules of the experimental editor key hover (issue #38), kept apart from Swing so they can be unit tested. */
internal object EditorKeyHoverSupport {
    /** Upper bounds of the hover popup before the screen-relative limits apply (unscaled pixels). */
    const val MAX_POPUP_WIDTH = 560
    const val MAX_POPUP_HEIGHT = 420
    const val MIN_POPUP_WIDTH = 280
    const val MAX_SCREEN_WIDTH_FRACTION = 0.6
    const val MAX_SCREEN_HEIGHT_FRACTION = 0.5

    /** Rows listed in one popup; a capture that resolves to more rows shows the first ones and a "more" hint. */
    const val MAX_POPUP_ROWS = 20

    /** Characters of one translation shown in the popup; the full value is in the translation table and edit dialog. */
    const val MAX_DISPLAY_VALUE_CHARS = 2_000

    /**
     * Only editors the user can type in get the hover: the main editor of a writable file on disk, never a viewer, a
     * read-only document (the editor tab shows a lock), a diff, console, preview or in-memory editor. The backend still
     * accepts only regular local files; this check only keeps the frontend from asking for files it would reject.
     */
    fun isEligibleEditor(
        isMainEditor: Boolean,
        isViewer: Boolean,
        documentWritable: Boolean,
        fileWritable: Boolean,
        physicalFile: Boolean,
        hasActiveScheme: Boolean,
    ): Boolean = hasActiveScheme && isMainEditor && !isViewer && documentWritable && fileWritable && physicalFile

    /** The innermost match containing [offset] (end exclusive), or `null`. */
    fun matchAt(
        matches: List<EditorKeyMatchDto>,
        offset: Int,
    ): EditorKeyMatchDto? =
        matches
            .filter { offset >= it.startOffset && offset < it.endOffsetExclusive }
            .minByOrNull { it.endOffsetExclusive - it.startOffset }

    /** The translation rows a match resolves to, read from the latest state so edits show without a rescan. */
    fun rowsFor(
        match: EditorKeyMatchDto,
        entries: List<LanguageEntryDto>,
    ): List<JoinedTranslationRow> {
        val targets = match.targets.mapTo(hashSetOf()) { it.namespace to it.key }
        if (targets.isEmpty()) return emptyList()
        return EntrySearch.join(entries.filter { (it.namespace to it.key) in targets })
    }

    fun locales(entries: List<LanguageEntryDto>): List<String> = entries.map(LanguageEntryDto::locale).distinct().sorted()

    /** Translations of [row] in [locales] order; `null` marks a language without this key. */
    fun translations(
        row: JoinedTranslationRow,
        locales: List<String>,
    ): List<Pair<String, String?>> =
        locales.map { locale ->
            val values = row.translations.filter { it.locale == locale }
            locale to if (values.isEmpty()) null else values.joinToString(" | ") { it.value }
        }

    fun displayValue(
        value: String,
        maxChars: Int = MAX_DISPLAY_VALUE_CHARS,
    ): String = if (value.length <= maxChars) value else value.take(maxChars) + "…"

    /** "Create translation" prefill for an unresolved match. */
    fun createDraft(match: EditorKeyMatchDto): TranslationEntryDraft =
        TranslationEntryDraft(
            namespace = match.suggestedNamespace,
            key = match.suggestedKey.ifEmpty { match.candidate },
            valuesByFilePath = emptyMap(),
        )

    /**
     * Popup size for content that would like [preferred]: never wider than [MAX_POPUP_WIDTH] or 60% of the screen,
     * never taller than [MAX_POPUP_HEIGHT] or half of the screen, and at least [MIN_POPUP_WIDTH] wide when the screen
     * allows. Content beyond the limits scrolls. [scale] converts the unscaled limits to screen pixels (HiDPI).
     */
    fun popupSize(
        preferred: Dimension,
        screen: Rectangle,
        scale: (Int) -> Int = { it },
    ): Dimension {
        val maxWidth = minOf(scale(MAX_POPUP_WIDTH), (screen.width * MAX_SCREEN_WIDTH_FRACTION).toInt()).coerceAtLeast(1)
        val maxHeight = minOf(scale(MAX_POPUP_HEIGHT), (screen.height * MAX_SCREEN_HEIGHT_FRACTION).toInt()).coerceAtLeast(1)
        val minWidth = minOf(scale(MIN_POPUP_WIDTH), maxWidth)
        return Dimension(preferred.width.coerceIn(minWidth, maxWidth), preferred.height.coerceIn(1, maxHeight))
    }
}
