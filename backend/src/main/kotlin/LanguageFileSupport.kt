package cg.creamgod45

import cg.creamgod45.localization.IssueSeverity
import cg.creamgod45.localization.LanguageIssueDto
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage
import kotlinx.serialization.json.*
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

internal data class ParsedLanguageFile(
    val path: Path,
    val locale: String,
    val namespace: String,
    val values: LinkedHashMap<String, String>,
    val structuredValueKeys: MutableSet<String> = linkedSetOf(),
    val keyPaths: MutableMap<String, List<String>> = linkedMapOf(),
    val issues: MutableList<LanguageIssueDto> = mutableListOf(),
)

internal object SafeLanguageFileAccess {
    private const val MAX_FILE_BYTES = 10L * 1024 * 1024
    private val extensions = setOf("json", "yaml", "yml", "php")

    fun validate(raw: String): Path {
        require(raw.isNotBlank() && raw.length <= 4096) { backendMessage("path.empty") }
        require(raw.none { it == '\u0000' || (it.code < 32 && it != '\t') }) { backendMessage("path.control") }
        val lower = raw.lowercase()
        require(!lower.contains("://") && !lower.startsWith("ldap:") && !lower.startsWith("file:")) {
            backendMessage("path.uri")
        }
        require(!lower.startsWith("\\\\.\\") && !lower.contains("globalroot")) { backendMessage("path.device") }
        val path = Paths.get(raw).toAbsolutePath().normalize()
        require(path.extension.lowercase() in extensions) { backendMessage("path.extension") }
        require(Files.isRegularFile(path)) { backendMessage("path.not.file", path) }
        require(Files.size(path) <= MAX_FILE_BYTES) { backendMessage("path.too.large", path) }
        return path.toRealPath()
    }

    fun read(path: Path): String = Files.newBufferedReader(path, StandardCharsets.UTF_8).use { it.readText() }

    fun atomicWrite(path: Path, content: String) {
        require(content.toByteArray(StandardCharsets.UTF_8).size <= MAX_FILE_BYTES)
        val temp = Files.createTempFile(path.parent, ".language-manager-", ".tmp")
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8)
            try {
                Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}

internal object LanguageFileCodec {
    private val json = Json { prettyPrint = true }

    fun parse(path: Path, schemeId: String): ParsedLanguageFile {
        val locale = if (path.extension.equals("php", true)) path.parent?.fileName?.toString().orEmpty() else path.nameWithoutExtension
        val namespace = if (path.extension.equals("php", true)) path.nameWithoutExtension else ""
        return try {
            val text = SafeLanguageFileAccess.read(path)
            val structuredKeys = linkedSetOf<String>()
            val keyPaths = linkedMapOf<String, List<String>>()
            val values = when (path.extension.lowercase()) {
                "json" -> parseJson(text, structuredKeys, keyPaths)
                "yaml", "yml" -> parseYaml(text, schemeId, path)
                "php" -> PhpArrayParser(text).parse()
                else -> error(backendMessage("format.unsupported"))
            }
            ParsedLanguageFile(path, locale, namespace, values, structuredKeys, keyPaths)
        } catch (e: Exception) {
            ParsedLanguageFile(path, locale, namespace, linkedMapOf(), issues = mutableListOf(
                LanguageIssueDto(schemeId, path.toString(), severity = IssueSeverity.ERROR,
                    code = "PARSE_ERROR", message = "${path.fileName}: ${e.message ?: e.javaClass.simpleName}")
            ))
        }
    }

    fun write(document: ParsedLanguageFile) {
        SafeLanguageFileAccess.atomicWrite(document.path, render(document))
    }

    fun render(document: ParsedLanguageFile): String = when (document.path.extension.lowercase()) {
            "json" -> writeJson(document.values, document.structuredValueKeys, document.keyPaths)
            "yaml", "yml" -> writeYaml(document.values)
            "php" -> writePhp(document.values)
            else -> error(backendMessage("format.unsupported"))
        }

    private fun parseJson(
        text: String,
        structuredKeys: MutableSet<String>,
        keyPaths: MutableMap<String, List<String>>,
    ): LinkedHashMap<String, String> {
        val root = json.parseToJsonElement(text)
        require(root is JsonObject) { backendMessage("json.root.object") }
        return linkedMapOf<String, String>().also { flattenJson(root, emptyList(), it, structuredKeys, keyPaths) }
    }

    private fun flattenJson(
        element: JsonElement,
        path: List<String>,
        out: LinkedHashMap<String, String>,
        structuredKeys: MutableSet<String>,
        keyPaths: MutableMap<String, List<String>>,
    ) {
        val displayKey = path.joinToString(".")
        when (element) {
            is JsonObject -> element.forEach { (key, child) -> flattenJson(child, path + key, out, structuredKeys, keyPaths) }
            is JsonPrimitive -> require(element.isString || element.booleanOrNull != null || element.doubleOrNull != null) { backendMessage("json.null.unsupported", displayKey) }.also {
                require(displayKey !in out) { backendMessage("json.path.conflict", displayKey) }
                out[displayKey] = element.content
                keyPaths[displayKey] = path
            }
            is JsonArray -> {
                require(displayKey !in out) { backendMessage("json.path.conflict", displayKey) }
                structuredKeys += displayKey
                out[displayKey] = json.encodeToString(JsonElement.serializer(), element)
                keyPaths[displayKey] = path
            }
        }
    }

    private fun writeJson(
        values: Map<String, String>,
        structuredKeys: Set<String>,
        keyPaths: Map<String, List<String>>,
    ): String {
        val root = tree(values, keyPaths) { key, value ->
            if (key in structuredKeys) {
                json.parseToJsonElement(value).also { require(it is JsonArray) { backendMessage("json.array.invalid", key) } }
            } else value
        }
        fun convert(value: Any): JsonElement = when (value) {
            is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to convert(it.value!!) })
            is JsonElement -> value
            else -> JsonPrimitive(value.toString())
        }
        return json.encodeToString(JsonElement.serializer(), convert(root)) + "\n"
    }

    private fun tree(
        values: Map<String, String>,
        keyPaths: Map<String, List<String>> = emptyMap(),
        leaf: (String, String) -> Any = { _, value -> value },
    ): LinkedHashMap<String, Any> {
        val root = linkedMapOf<String, Any>()
        values.forEach { (flatKey, value) ->
            var cursor = root
            val parts = keyPaths[flatKey] ?: flatKey.split('.').filter(String::isNotBlank)
            require(parts.isNotEmpty()) { backendMessage("key.empty") }
            parts.dropLast(1).forEach { part ->
                @Suppress("UNCHECKED_CAST")
                cursor = cursor.getOrPut(part) { linkedMapOf<String, Any>() } as? LinkedHashMap<String, Any>
                    ?: error(backendMessage("key.hierarchy.conflict", flatKey))
            }
            cursor[parts.last()] = leaf(flatKey, value)
        }
        return root
    }

    private fun writeYaml(values: Map<String, String>): String = buildString {
        fun appendMap(map: Map<String, Any>, depth: Int) {
            map.forEach { (key, value) ->
                append("  ".repeat(depth)).append(yamlKey(key)).append(':')
                if (value is Map<*, *>) {
                    append('\n')
                    @Suppress("UNCHECKED_CAST") appendMap(value as Map<String, Any>, depth + 1)
                } else append(' ').append(yamlValue(value.toString())).append('\n')
            }
        }
        appendMap(tree(values), 0)
    }

    private fun writePhp(values: Map<String, String>): String = buildString {
        append("<?php\n\nreturn [\n")
        fun appendMap(map: Map<String, Any>, depth: Int) {
            map.forEach { (key, value) ->
                append("    ".repeat(depth)).append('\'').append(phpEscape(key)).append("' => ")
                if (value is Map<*, *>) {
                    append("[\n")
                    @Suppress("UNCHECKED_CAST") appendMap(value as Map<String, Any>, depth + 1)
                    append("    ".repeat(depth)).append("],\n")
                } else append('\'').append(phpEscape(value.toString())).append("',\n")
            }
        }
        appendMap(tree(values), 1)
        append("];\n")
    }

    private fun parseYaml(text: String, schemeId: String, path: Path): LinkedHashMap<String, String> {
        val out = linkedMapOf<String, String>()
        val parents = mutableListOf<Pair<Int, String>>()
        text.lineSequence().forEachIndexed { index, raw ->
            if (raw.isBlank() || raw.trimStart().startsWith('#')) return@forEachIndexed
            require(!raw.contains('\t')) { backendMessage("yaml.tabs", index + 1) }
            val indent = raw.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
            val line = raw.trim()
            val colon = findYamlColon(line)
            require(colon > 0) { backendMessage("yaml.missing.colon", index + 1) }
            val key = unquote(line.substring(0, colon).trim())
            require(key.isNotBlank()) { backendMessage("yaml.empty.key", index + 1) }
            while (parents.isNotEmpty() && parents.last().first >= indent) parents.removeLast()
            val rest = line.substring(colon + 1).trim()
            if (rest.isEmpty()) parents += indent to key
            else {
                val full = (parents.map { it.second } + key).joinToString(".")
                require(full !in out) { backendMessage("yaml.duplicate.key", index + 1, full) }
                out[full] = unquote(stripYamlComment(rest))
            }
        }
        return out
    }

    private fun findYamlColon(line: String): Int {
        var quote: Char? = null
        line.forEachIndexed { i, c -> if (c == '\'' || c == '"') quote = if (quote == c) null else if (quote == null) c else quote; if (c == ':' && quote == null) return i }
        return -1
    }
    private fun stripYamlComment(value: String): String {
        var quote: Char? = null
        value.forEachIndexed { i, c -> if (c == '\'' || c == '"') quote = if (quote == c) null else if (quote == null) c else quote; if (c == '#' && quote == null && i > 0 && value[i - 1].isWhitespace()) return value.substring(0, i).trim() }
        return value
    }
    private fun unquote(value: String): String = if (value.length >= 2 && value.first() == value.last() && value.first() in charArrayOf('\'', '"')) {
        if (value.first() == '"') value.substring(1, value.lastIndex).replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
        else value.substring(1, value.lastIndex).replace("''", "'")
    } else value
    private fun yamlKey(value: String) = if (value.matches(Regex("[A-Za-z0-9_.-]+"))) value else yamlValue(value)
    private fun yamlValue(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""
    private fun phpEscape(value: String) = value.replace("\\", "\\\\").replace("'", "\\'").replace("\r", "\\r").replace("\n", "\\n")
}

internal class PhpArrayParser(private val source: String) {
    private var index = 0
    private val out = linkedMapOf<String, String>()

    fun parse(): LinkedHashMap<String, String> {
        skipTrivia()
        if (peek("<?php")) index += 5
        skipTrivia(); expectWord("return"); skipTrivia()
        parseMap("")
        skipTrivia(); if (index < source.length && source[index] == ';') index++
        skipTrivia(); require(index == source.length) { backendMessage("php.trailing.code") }
        return out
    }

    private fun parseMap(prefix: String) {
        val closing = when { peek("[") -> { index++; ']' }; peekWord("array") -> { expectWord("array"); skipTrivia(); expect('('); ')' }; else -> error(backendMessage("php.return.array.only")) }
        skipTrivia()
        while (index < source.length && source[index] != closing) {
            val key = parseString(); skipTrivia(); require(peek("=>")) { backendMessage("php.missing.arrow") }; index += 2; skipTrivia()
            val full = if (prefix.isEmpty()) key else "$prefix.$key"
            if (peek("[") || peekWord("array")) parseMap(full) else {
                val value = parseScalar()
                require(full !in out) { backendMessage("php.duplicate.key", full) }
                out[full] = value
            }
            skipTrivia(); if (index < source.length && source[index] == ',') { index++; skipTrivia() } else if (source[index] != closing) error(backendMessage("php.missing.comma"))
        }
        expect(closing)
    }

    private fun parseScalar(): String = if (index < source.length && source[index] in charArrayOf('\'', '"')) parseString() else {
        val start = index
        while (index < source.length && source[index] !in charArrayOf(',', ']', ')')) index++
        source.substring(start, index).trim().also { require(it.matches(Regex("-?[0-9.]+|true|false", RegexOption.IGNORE_CASE))) { backendMessage("php.scalar.only") } }
    }
    private fun parseString(): String {
        require(index < source.length && source[index] in charArrayOf('\'', '"')) { backendMessage("php.quoted.key.value") }
        val quote = source[index++]; val result = StringBuilder()
        while (index < source.length) {
            val c = source[index++]
            if (c == quote) return result.toString()
            if (c == '\\') { require(index < source.length); val next = source[index++]; result.append(when (next) { 'n' -> '\n'; 'r' -> '\r'; 't' -> '\t'; else -> next }) } else result.append(c)
        }
        error(backendMessage("php.unclosed.string"))
    }
    private fun skipTrivia() { while (index < source.length) { when { source[index].isWhitespace() -> index++; peek("//") || peek("#") -> { while (index < source.length && source[index] != '\n') index++ }; peek("/*") -> { val end = source.indexOf("*/", index + 2); require(end >= 0) { backendMessage("php.unclosed.comment") }; index = end + 2 }; else -> return } } }
    private fun expect(c: Char) { require(index < source.length && source[index] == c) { backendMessage("parser.expected.character", c) }; index++ }
    private fun expectWord(word: String) { require(peekWord(word)) { backendMessage("parser.expected.word", word) }; index += word.length }
    private fun peek(value: String) = source.regionMatches(index, value, 0, value.length)
    private fun peekWord(value: String) = source.regionMatches(index, value, 0, value.length, ignoreCase = true) && (index + value.length >= source.length || !source[index + value.length].isLetterOrDigit())
}
