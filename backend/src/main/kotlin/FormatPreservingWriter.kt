package cg.creamgod45

import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.path.extension

/**
 * Writes value-only edits back into the original file text, so comments, blank lines, quoting, indentation, key order
 * and every untouched entry stay byte-for-byte identical. Only the edited values are replaced.
 *
 * It returns null whenever a safe in-place edit is not possible (keys added, removed or renamed, structured or
 * non-string values, or a value whose location cannot be determined); callers then fall back to a full render.
 * Every patch is re-parsed and must yield exactly the expected values before it is used.
 */
internal object FormatPreservingWriter {
    private class Span(
        val start: Int,
        val end: Int,
        val isString: Boolean = true,
    )

    fun patch(
        original: String,
        document: ParsedLanguageFile,
    ): String? {
        val path = document.path
        val before = LanguageFileCodec.parseText(path, "format-preserving", original, Int.MAX_VALUE)
        if (before.issues.isNotEmpty()) return null
        if (before.values.keys != document.values.keys) return null
        if (before.structuredValueKeys != document.structuredValueKeys || before.jsonArrayPaths != document.jsonArrayPaths) return null
        val changedKeys = document.values.keys.filter { before.values[it] != document.values[it] }
        if (changedKeys.isEmpty()) return original
        if (changedKeys.any { it in document.structuredValueKeys }) return null
        if (changedKeys.any { key -> before.keyPaths[key] != document.keyPaths[key] && key in before.keyPaths }) return null

        val format = path.extension.lowercase()
        val spans = runCatching { spans(format, original) }.getOrNull() ?: return null
        val replacements =
            changedKeys.map { key ->
                val span = spans[key] ?: return null
                if (!span.isString) return null
                span to encode(format, document.values.getValue(key))
            }
        val ordered = replacements.sortedByDescending { it.first.start }
        if (ordered.zipWithNext().any { (later, earlier) -> earlier.first.end > later.first.start }) return null
        val patched =
            StringBuilder(original)
                .apply { ordered.forEach { (span, text) -> replace(span.start, span.end, text) } }
                .toString()

        val after = LanguageFileCodec.parseText(path, "format-preserving", patched, Int.MAX_VALUE)
        return patched.takeIf { after.issues.isEmpty() && after.values == document.values }
    }

    private fun encode(
        format: String,
        value: String,
    ): String =
        when (format) {
            "json" -> JsonPrimitive(value).toString()
            "yaml", "yml" -> LanguageFileCodec.yamlValue(value)
            "php" -> "'" + LanguageFileCodec.phpEscape(value) + "'"
            "properties" -> LanguageFileCodec.escapeProperty(value, key = false)
            else -> error("unsupported")
        }

    private fun spans(
        format: String,
        text: String,
    ): Map<String, Span>? =
        when (format) {
            "json" -> {
                JsonValueScanner(text).scan()
            }

            "yaml", "yml" -> {
                yamlSpans(text)
            }

            "php" -> {
                PhpArrayParser(text, Int.MAX_VALUE).let { parser ->
                    parser.parse()
                    parser.valueSpans.mapValues {
                        Span(
                            it.value.first,
                            it.value.last + 1,
                        )
                    }
                }
            }

            "properties" -> {
                propertiesSpans(text)
            }

            else -> {
                null
            }
        }

    /** Physical lines with their start offsets, without the line terminator. */
    private fun lines(text: String): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        var start = 0
        while (start <= text.length) {
            var end = start
            while (end < text.length && text[end] != '\n' && text[end] != '\r') end++
            result += start to text.substring(start, end)
            if (end >= text.length) break
            start = if (text[end] == '\r' && end + 1 < text.length && text[end + 1] == '\n') end + 2 else end + 1
        }
        return result
    }

    private fun yamlSpans(text: String): Map<String, Span> {
        val spans = linkedMapOf<String, Span>()
        val parents = mutableListOf<Pair<Int, String>>()
        lines(text).forEach { (offset, raw) ->
            if (raw.isBlank() || raw.trimStart().startsWith('#')) return@forEach
            val indent = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val line = raw.trim()
            val colon = LanguageFileCodec.findYamlColon(line)
            if (colon <= 0) return@forEach
            val key = LanguageFileCodec.unquote(line.substring(0, colon).trim())
            while (parents.isNotEmpty() && parents.last().first >= indent) parents.removeLast()
            val afterColon = line.substring(colon + 1)
            val rest = afterColon.trim()
            if (rest.isEmpty()) {
                parents += indent to key
                return@forEach
            }
            val full = (parents.map { it.second } + key).joinToString(".")
            val valueText = LanguageFileCodec.stripYamlComment(rest).trimEnd()
            val start = offset + indent + colon + 1 + (afterColon.length - afterColon.trimStart().length)
            spans[full] = Span(start, start + valueText.length)
        }
        return spans
    }

    private fun propertiesSpans(text: String): Map<String, Span> {
        val spans = linkedMapOf<String, Span>()
        val physical = lines(text)
        var index = 0
        while (index < physical.size) {
            val (offset, raw) = physical[index]
            // A logical line continues while it ends with an odd number of backslashes.
            var last = index
            while (last < physical.size - 1 && physical[last].second.takeLastWhile { it == '\\' }.length % 2 == 1) last++
            val leading = raw.length - raw.trimStart().length
            val line = raw.trimStart()
            if (line.isNotEmpty() && !line.startsWith('#') && !line.startsWith('!')) {
                val (rawKey, rawValue) = LanguageFileCodec.splitProperty(line)
                val key = decode(rawKey)
                // Only values that start on the key's own physical line can be located safely.
                val valueStartInLine = if (rawValue.isEmpty()) line.trimEnd().length else line.indexOf(rawValue, rawKey.length)
                if (key != null && valueStartInLine >= 0 && valueStartInLine <= line.length) {
                    val lastLine = physical[last]
                    spans[key] = Span(offset + leading + valueStartInLine, lastLine.first + lastLine.second.length)
                }
            }
            index = last + 1
        }
        return spans
    }

    private fun decode(rawKey: String): String? =
        runCatching {
            LanguageFileCodec
                .parseText(
                    java.nio.file.Path
                        .of("key.properties"),
                    "format-preserving",
                    "$rawKey=\n",
                ).values.keys
                .single()
        }.getOrNull()

    /** Locates JSON values by the same dotted path the parser uses (object keys and array indexes). */
    private class JsonValueScanner(
        private val text: String,
    ) {
        private var index = 0
        private val spans = linkedMapOf<String, Span>()

        fun scan(): Map<String, Span> {
            skipWhitespace()
            value(emptyList())
            return spans
        }

        private fun value(path: List<String>) {
            skipWhitespace()
            when (text[index]) {
                '{' -> {
                    index++
                    skipWhitespace()
                    if (text[index] == '}') {
                        index++
                        return
                    }
                    while (true) {
                        skipWhitespace()
                        val key = string()
                        skipWhitespace()
                        check(text[index++] == ':')
                        value(path + key)
                        skipWhitespace()
                        if (text[index++] == '}') return
                    }
                }

                '[' -> {
                    index++
                    skipWhitespace()
                    if (text[index] == ']') {
                        index++
                        return
                    }
                    var position = 0
                    while (true) {
                        value(path + (position++).toString())
                        skipWhitespace()
                        if (text[index++] == ']') return
                    }
                }

                '"' -> {
                    val start = index
                    string()
                    spans[path.joinToString(".")] = Span(start, index)
                }

                else -> {
                    val start = index
                    while (index < text.length && text[index] !in ",}] \t\r\n") index++
                    spans[path.joinToString(".")] = Span(start, index, isString = false)
                }
            }
        }

        private fun string(): String {
            check(text[index] == '"')
            val start = index
            index++
            while (text[index] != '"') index += if (text[index] == '\\') 2 else 1
            index++
            return kotlinx.serialization.json.Json
                .parseToJsonElement(text.substring(start, index))
                .let { (it as JsonPrimitive).content }
        }

        private fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }
    }
}
