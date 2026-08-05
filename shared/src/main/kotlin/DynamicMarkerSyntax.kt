package cg.creamgod45.localization

data class DynamicMarkerMatch(
    val startOffset: Int,
    val endOffsetExclusive: Int,
    val groups: List<DynamicSourceGroupDto>,
)

object DynamicMarkerSyntax {
    private const val MAX_MARKER_CHARS = 8_192
    private val marker = Regex("@languageManager\\(\\s*method\\s*:\\s*dynamic\\s*,([^\\r\\n)]{1,$MAX_MARKER_CHARS})\\)")
    private val property = Regex("(?:^|,)\\s*([A-Za-z][A-Za-z0-9_-]{0,63})\\s*:")
    private val commentPrefixes = listOf("//", "#", "/*", "*", "<!--", "{{--")

    fun findAll(content: String): List<DynamicMarkerMatch> =
        marker.findAll(content).mapNotNull { match ->
            val lineStart = content.lastIndexOf('\n', match.range.first - 1).let { if (it < 0) 0 else it + 1 }
            val beforeMarker = content.substring(lineStart, match.range.first)
            if (commentPrefixes.none(beforeMarker::contains)) return@mapNotNull null
            val groups = parseGroups(match.groupValues[1])
            if (groups.isEmpty()) return@mapNotNull null
            DynamicMarkerMatch(match.range.first, match.range.last + 1, groups)
        }.toList()

    fun findAt(
        content: String,
        startOffset: Int,
        endOffsetExclusive: Int = startOffset,
    ): DynamicMarkerMatch? {
        val start = startOffset.coerceIn(0, content.length)
        val end = endOffsetExclusive.coerceIn(start, content.length)
        return findAll(content).firstOrNull { marker ->
            if (start == end) start in marker.startOffset until marker.endOffsetExclusive
            else start < marker.endOffsetExclusive && end > marker.startOffset
        }
    }

    fun render(groups: List<DynamicSourceGroupDto>): String =
        "@languageManager(method: dynamic, ${groups.joinToString(", ") { "${it.name}: ${it.keys.joinToString(",")}" }})"

    private fun parseGroups(body: String): List<DynamicSourceGroupDto> {
        val properties = property.findAll(body).toList()
        return properties.mapIndexedNotNull { index, match ->
            val start = match.range.last + 1
            val end = properties.getOrNull(index + 1)?.range?.first ?: body.length
            val keys =
                body.substring(start, end).trim().trimEnd(',')
                    .split(',')
                    .map(String::trim)
                    .filter { it.length in 1..256 && it.none(Char::isISOControl) }
                    .distinct()
            keys.takeIf { it.isNotEmpty() }?.let { DynamicSourceGroupDto(match.groupValues[1], it) }
        }
    }
}
