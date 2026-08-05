package cg.creamgod45.localization.ui

import cg.creamgod45.localization.DynamicSourceRuleDto

internal fun dynamicSourceOffset(
    text: CharSequence,
    line: Int,
    column: Int,
): Int? {
    if (line < 1 || column < 1) return null
    var currentLine = 1
    var lineStart = 0
    var index = 0
    while (currentLine < line && index < text.length) {
        when (text[index]) {
            '\r' -> {
                index += if (index + 1 < text.length && text[index + 1] == '\n') 2 else 1
                currentLine++
                lineStart = index
            }
            '\n' -> {
                index++
                currentLine++
                lineStart = index
            }
            else -> index++
        }
    }
    if (currentLine != line) return null
    var lineEnd = lineStart
    while (lineEnd < text.length && text[lineEnd] != '\r' && text[lineEnd] != '\n') lineEnd++
    val offset = lineStart + column - 1
    return offset.takeIf { it <= lineEnd }
}

internal fun dynamicSourceKeyCount(rule: DynamicSourceRuleDto): Int =
    rule.groups.flatMap { it.keys }.distinct().size

internal fun dynamicSourceTooltipDetails(rule: DynamicSourceRuleDto): String {
    val groups = rule.groups.joinToString("\n") { group ->
        "${group.name}: ${group.keys.distinct().joinToString(", ")}"
    }
    return groups.take(MAX_DYNAMIC_INLAY_TOOLTIP_CHARS)
}

private const val MAX_DYNAMIC_INLAY_TOOLTIP_CHARS = 2_000
