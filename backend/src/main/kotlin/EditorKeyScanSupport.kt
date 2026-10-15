package cg.creamgod45

import cg.creamgod45.localization.EditorKeyMatchDto
import cg.creamgod45.localization.EditorKeyScanResultDto
import cg.creamgod45.localization.EditorKeyScanStatus
import cg.creamgod45.localization.EditorKeyTargetDto
import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.MAX_EDITOR_KEY_MATCHES

/**
 * Finds usage-regex key captures in one editor's text for the experimental editor key hover (issue #38) and resolves
 * each capture to the scheme's translation rows with the same rules as the usage scan.
 */
internal object EditorKeyScanSupport {
    /** A user regex that backtracks for longer than this stops the scan; matches found so far are kept. */
    const val SCAN_TIME_BUDGET_MS = 1_500L

    fun scan(
        text: CharSequence,
        patterns: List<Regex>,
        entries: List<LanguageEntryDto>,
        maxMatches: Int = MAX_EDITOR_KEY_MATCHES,
        timeBudgetMs: Long = SCAN_TIME_BUDGET_MS,
        nanoTime: () -> Long = System::nanoTime,
    ): EditorKeyScanResultDto {
        val deadline = nanoTime() + timeBudgetMs * 1_000_000
        val guarded = DeadlineCharSequence(text, deadline, nanoTime)
        val occurrences = linkedMapOf<IntRange, CandidateOccurrence>()
        var truncated = false
        try {
            patterns.forEach { pattern ->
                for (match in pattern.findAll(guarded)) {
                    val occurrence = UsageScanSupport.candidateOf(match) ?: continue
                    if (occurrence.range.isEmpty()) continue
                    if (occurrences.size >= maxMatches && occurrence.range !in occurrences) {
                        truncated = true
                        break
                    }
                    occurrences.putIfAbsent(occurrence.range, occurrence)
                }
            }
        } catch (_: ScanDeadlineExceeded) {
            truncated = true
        }
        val keyIndex = UsageKeyIndex(entries)
        val entriesById = entries.associateBy(LanguageEntryDto::id)
        val namespaces = entries.mapTo(linkedSetOf(), LanguageEntryDto::namespace).toList()
        val matches =
            occurrences.values
                .sortedWith(compareBy<CandidateOccurrence> { it.range.first }.thenBy { it.range.last })
                .map { occurrence ->
                    val targets =
                        keyIndex
                            .owners(occurrence.candidate)
                            .mapNotNull(entriesById::get)
                            .map { EditorKeyTargetDto(it.namespace, it.key) }
                            .distinct()
                            .sortedWith(compareBy<EditorKeyTargetDto> { it.namespace }.thenBy { it.key })
                    val (suggestedNamespace, suggestedKey) =
                        if (targets.isEmpty()) suggestTarget(occurrence.candidate, namespaces) else "" to ""
                    EditorKeyMatchDto(
                        startOffset = occurrence.range.first,
                        endOffsetExclusive = occurrence.range.last + 1,
                        candidate = occurrence.candidate,
                        targets = targets,
                        suggestedNamespace = suggestedNamespace,
                        suggestedKey = suggestedKey,
                    )
                }
        return EditorKeyScanResultDto(EditorKeyScanStatus.OK, matches, truncated)
    }

    /**
     * Splits an unresolved capture into the namespace and key that "Create translation" prefills: the longest known
     * namespace written as `namespace.` or `namespace/with/slashes.` is taken off the front; otherwise the whole capture
     * is the key in the root namespace. A `package::` prefix is dropped like the usage scan does.
     */
    fun suggestTarget(
        candidate: String,
        namespaces: Collection<String>,
    ): Pair<String, String> {
        val bare = if ("::" in candidate) candidate.substringAfter("::") else candidate
        namespaces
            .filter(String::isNotBlank)
            .sortedByDescending(String::length)
            .forEach { namespace ->
                listOf("$namespace.", "${namespace.replace('.', '/')}.").forEach { prefix ->
                    if (bare.length > prefix.length && bare.startsWith(prefix)) return namespace to bare.substring(prefix.length)
                }
            }
        return "" to bare
    }

    private class ScanDeadlineExceeded : RuntimeException(null, null, false, false)

    /**
     * Lets a catastrophically backtracking user regex give up. The JDK matcher reads input through [get]; sub-sequences
     * stay guarded too, so no read path skips the deadline.
     */
    private class DeadlineCharSequence(
        private val text: CharSequence,
        private val deadline: Long,
        private val nanoTime: () -> Long,
    ) : CharSequence {
        private var reads = 0

        override val length: Int get() = text.length

        override fun get(index: Int): Char {
            if (++reads and CHECK_INTERVAL_MASK == 0 && nanoTime() - deadline > 0) throw ScanDeadlineExceeded()
            return text[index]
        }

        override fun subSequence(
            startIndex: Int,
            endIndex: Int,
        ): CharSequence = DeadlineCharSequence(text.subSequence(startIndex, endIndex), deadline, nanoTime)

        override fun toString(): String = text.toString()
    }

    private const val CHECK_INTERVAL_MASK = 0xFFF
}
