package cg.creamgod45

import cg.creamgod45.localization.FolderDiscoveryDto
import cg.creamgod45.localization.IssueSeverity
import cg.creamgod45.localization.LanguageFileCandidateDto
import cg.creamgod45.localization.LanguageIssueDto
import kotlinx.serialization.json.*
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal data class ParsedLanguageFile(
    val path: Path,
    val locale: String,
    val namespace: String,
    val values: LinkedHashMap<String, String>,
    val structuredValueKeys: MutableSet<String> = linkedSetOf(),
    val keyPaths: MutableMap<String, List<String>> = linkedMapOf(),
    val issues: MutableList<LanguageIssueDto> = mutableListOf(),
)

private val PROPERTIES_LOCALE_SUFFIX = Regex("^(.+)_([a-z]{2,3}(?:_[A-Z]{2})?)$")

private fun propertiesIdentity(path: Path): Pair<String, String> {
    val stem = path.nameWithoutExtension
    val match = PROPERTIES_LOCALE_SUFFIX.matchEntire(stem)
    return if (match == null) stem to "en" else match.groupValues[1] to match.groupValues[2]
}

internal object SafeLanguageFileAccess {
    private const val MAX_FILE_BYTES = 10L * 1024 * 1024
    private val extensions = setOf("json", "yaml", "yml", "php", "properties")

    private fun safeNormalizedPath(raw: String): Path {
        require(raw.isNotBlank() && raw.length <= 4096) { backendMessage("path.empty") }
        require(raw.none { it == '\u0000' || (it.code < 32 && it != '\t') }) { backendMessage("path.control") }
        val lower = raw.lowercase()
        require(!lower.contains("://") && !lower.startsWith("ldap:") && !lower.startsWith("file:")) {
            backendMessage("path.uri")
        }
        require(!lower.startsWith("\\\\.\\") && !lower.contains("globalroot")) { backendMessage("path.device") }
        return Paths.get(raw).toAbsolutePath().normalize()
    }

    fun validate(raw: String): Path {
        val path = safeNormalizedPath(raw)
        require(path.extension.lowercase() in extensions) { backendMessage("path.extension") }
        require(Files.isRegularFile(path)) { backendMessage("path.not.file", path) }
        require(Files.size(path) <= MAX_FILE_BYTES) { backendMessage("path.too.large", path) }
        return path.toRealPath()
    }

    fun validateDirectory(raw: String): Path {
        val path = safeNormalizedPath(raw)
        require(Files.isDirectory(path)) { backendMessage("path.not.directory", path) }
        return path.toRealPath()
    }

    fun read(path: Path): String = Files.newBufferedReader(path, StandardCharsets.UTF_8).use { it.readText() }

    fun atomicWrite(
        path: Path,
        content: String,
    ) {
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

internal object LanguageFolderDiscovery {
    private const val MAX_FILES = 500
    private const val MAX_DEPTH = 16
    private val extensions = setOf("json", "yaml", "yml", "php", "properties")
    private val ignoredDirectories =
        setOf(".git", ".idea", ".gradle", "build", "dist", "vendor", "node_modules", "storage", "cache", "coverage")

    fun discover(rawFolder: String): FolderDiscoveryDto = discover(listOf(rawFolder))

    fun discover(rawFolders: List<String>): FolderDiscoveryDto {
        require(rawFolders.isNotEmpty()) { backendMessage("folder.selection.required") }
        val roots = rawFolders.map(SafeLanguageFileAccess::validateDirectory).distinct()
        val candidates = linkedMapOf<String, LanguageFileCandidateDto>()
        var truncated = false
        roots.forEach { root ->
            if (truncated) return@forEach
            Files.walkFileTree(
                root,
                emptySet(),
                MAX_DEPTH,
                object : SimpleFileVisitor<Path>() {
                    override fun preVisitDirectory(
                        dir: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult =
                        if (dir != root &&
                            dir.fileName.toString().lowercase() in ignoredDirectories
                        ) {
                            FileVisitResult.SKIP_SUBTREE
                        } else {
                            FileVisitResult.CONTINUE
                        }

                    override fun visitFile(
                        file: Path,
                        attrs: BasicFileAttributes,
                    ): FileVisitResult {
                        val extension = file.extension.lowercase()
                        if (extension !in extensions) return FileVisitResult.CONTINUE
                        val normalizedPath = file.toAbsolutePath().normalize().toString()
                        if (normalizedPath in candidates) return FileVisitResult.CONTINUE
                        if (candidates.size >= MAX_FILES) {
                            truncated = true
                            return FileVisitResult.TERMINATE
                        }
                        candidates[normalizedPath] = inspect(file, extension)
                        return FileVisitResult.CONTINUE
                    }
                },
            )
        }
        return FolderDiscoveryDto(
            folderPath = roots.first().toString(),
            files = candidates.values.sortedBy { it.filePath.lowercase() },
            truncated = truncated,
            folderPaths = roots.map(Path::toString),
        )
    }

    private fun inspect(
        file: Path,
        extension: String,
    ): LanguageFileCandidateDto =
        try {
            val safePath = SafeLanguageFileAccess.validate(file.toString())
            val parsed = LanguageFileCodec.parse(safePath, "folder-discovery")
            val errors = parsed.issues.filter { it.severity == IssueSeverity.ERROR }
            LanguageFileCandidateDto(
                filePath = safePath.toString(),
                format = extension.uppercase(),
                locale = parsed.locale,
                namespace = parsed.namespace,
                entryCount = parsed.values.size,
                recognized = errors.isEmpty(),
                errorMessage = errors.joinToString("; ") { it.message }.take(500).ifBlank { null },
            )
        } catch (error: Exception) {
            LanguageFileCandidateDto(
                filePath = file.toAbsolutePath().normalize().toString(),
                format = extension.uppercase(),
                recognized = false,
                errorMessage = (error.message ?: error.javaClass.simpleName).take(500),
            )
        }
}

internal data class LocaleVersionTarget(
    val path: Path,
    val content: String,
)

internal object LanguageLocaleVersionSupport {
    private val localePattern = Regex("[A-Za-z][A-Za-z0-9_-]{0,31}")
    private val windowsDevices =
        buildSet {
            addAll(listOf("CON", "PRN", "AUX", "NUL"))
            (1..9).forEach { index ->
                add("COM$index")
                add("LPT$index")
            }
        }

    fun buildTargets(
        documents: List<ParsedLanguageFile>,
        sourceLocale: String,
        targetLocale: String,
    ): List<LocaleVersionTarget> {
        require(sourceLocale.matches(localePattern)) { backendMessage("locale.invalid") }
        require(targetLocale.matches(localePattern) && targetLocale.uppercase() !in windowsDevices) { backendMessage("locale.invalid") }
        require(!sourceLocale.equals(targetLocale, ignoreCase = true)) { backendMessage("locale.version.same") }
        require(
            documents.none { it.locale.equals(targetLocale, ignoreCase = true) },
        ) { backendMessage("locale.version.exists", targetLocale) }
        val sources = documents.filter { it.locale == sourceLocale }
        require(sources.isNotEmpty()) { backendMessage("locale.version.source.missing", sourceLocale) }
        require(sources.none { document -> document.issues.any { it.severity == IssueSeverity.ERROR } }) {
            backendMessage("locale.version.parse.blocked")
        }

        val targets =
            sources.map { source ->
                val targetPath =
                    when (source.path.extension.lowercase()) {
                        "php" -> {
                            val localeRoot = source.path.parent?.parent ?: error(backendMessage("locale.version.path.invalid"))
                            localeRoot
                                .resolve(targetLocale)
                                .resolve(source.path.fileName)
                                .toAbsolutePath()
                                .normalize()
                        }

                        "json", "yaml", "yml" -> {
                            source.path
                                .resolveSibling("$targetLocale.${source.path.extension.lowercase()}")
                                .toAbsolutePath()
                                .normalize()
                        }

                        "properties" -> {
                            source.path
                                .resolveSibling(propertiesTargetName(source.path, targetLocale))
                                .toAbsolutePath()
                                .normalize()
                        }

                        else -> {
                            error(backendMessage("format.unsupported"))
                        }
                    }
                require(!Files.exists(targetPath)) { backendMessage("locale.version.target.exists", targetPath) }
                val values =
                    linkedMapOf<String, String>().apply {
                        source.values.forEach { (key, value) -> put(key, if (key in source.structuredValueKeys) value else "") }
                    }
                val targetDocument =
                    ParsedLanguageFile(
                        path = targetPath,
                        locale = targetLocale,
                        namespace = source.namespace,
                        values = values,
                        structuredValueKeys = source.structuredValueKeys.toMutableSet(),
                        keyPaths = source.keyPaths.toMutableMap(),
                    )
                LocaleVersionTarget(targetPath, LanguageFileCodec.render(targetDocument))
            }
        require(targets.map { it.path }.distinct().size == targets.size) { backendMessage("locale.version.path.conflict") }
        return targets
    }

    private fun propertiesTargetName(
        path: Path,
        targetLocale: String,
    ): String {
        val namespace = propertiesIdentity(path).first
        return "${namespace}_$targetLocale.properties"
    }
}

internal object LanguageFileCodec {
    private val json = Json { prettyPrint = true }

    fun parse(
        path: Path,
        schemeId: String,
    ): ParsedLanguageFile {
        val (locale, namespace) =
            when (path.extension.lowercase()) {
                "php" -> {
                    path.parent
                        ?.fileName
                        ?.toString()
                        .orEmpty() to path.nameWithoutExtension
                }

                "properties" -> {
                    propertiesIdentity(path).let { (bundle, bundleLocale) -> bundleLocale to bundle }
                }

                else -> {
                    path.nameWithoutExtension to ""
                }
            }
        return try {
            val text = SafeLanguageFileAccess.read(path)
            val structuredKeys = linkedSetOf<String>()
            val keyPaths = linkedMapOf<String, List<String>>()
            val values =
                when (path.extension.lowercase()) {
                    "json" -> parseJson(text, structuredKeys, keyPaths)
                    "yaml", "yml" -> parseYaml(text, schemeId, path)
                    "php" -> PhpArrayParser(text).parse()
                    "properties" -> parseProperties(text, keyPaths)
                    else -> error(backendMessage("format.unsupported"))
                }
            ParsedLanguageFile(path, locale, namespace, values, structuredKeys, keyPaths)
        } catch (e: Exception) {
            ParsedLanguageFile(
                path,
                locale,
                namespace,
                linkedMapOf(),
                issues =
                    mutableListOf(
                        LanguageIssueDto(
                            schemeId,
                            path.toString(),
                            severity = IssueSeverity.ERROR,
                            code = "PARSE_ERROR",
                            message = "${path.fileName}: ${e.message ?: e.javaClass.simpleName}",
                        ),
                    ),
            )
        }
    }

    fun write(document: ParsedLanguageFile) {
        SafeLanguageFileAccess.atomicWrite(document.path, render(document))
    }

    fun render(document: ParsedLanguageFile): String =
        when (document.path.extension.lowercase()) {
            "json" -> writeJson(document.values, document.structuredValueKeys, document.keyPaths)
            "yaml", "yml" -> writeYaml(document.values)
            "php" -> writePhp(document.values)
            "properties" -> writeProperties(document.values)
            else -> error(backendMessage("format.unsupported"))
        }

    private fun parseProperties(
        text: String,
        keyPaths: MutableMap<String, List<String>>,
    ): LinkedHashMap<String, String> {
        val out = linkedMapOf<String, String>()
        propertiesLogicalLines(text).forEach { (lineNumber, rawLine) ->
            val line = rawLine.trimStart()
            if (line.isEmpty() || line.startsWith('#') || line.startsWith('!')) return@forEach
            val (rawKey, rawValue) = splitProperty(line)
            val key = decodePropertyEscapes(rawKey, lineNumber)
            val value = decodePropertyEscapes(rawValue, lineNumber)
            require(key.isNotBlank()) { backendMessage("properties.empty.key", lineNumber) }
            require(key !in out) { backendMessage("properties.duplicate.key", lineNumber, key) }
            out[key] = value
            keyPaths[key] = listOf(key)
        }
        return out
    }

    private fun propertiesLogicalLines(text: String): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        var pending: StringBuilder? = null
        var startLine = 0
        text.lineSequence().forEachIndexed { index, physicalLine ->
            val lineNumber = index + 1
            val continuing = pending != null
            val current =
                pending ?: StringBuilder().also {
                    pending = it
                    startLine = lineNumber
                }
            val part = if (continuing) physicalLine.trimStart() else physicalLine
            current.append(part)
            val trailingSlashes = current.reversed().takeWhile { it == '\\' }.count()
            if (trailingSlashes % 2 == 1) {
                current.setLength(current.length - 1)
            } else {
                result += startLine to current.toString()
                pending = null
            }
        }
        pending?.let { result += startLine to it.toString() }
        return result
    }

    private fun splitProperty(line: String): Pair<String, String> {
        var escaped = false
        var separator = -1
        var whitespaceSeparator = false
        for (index in line.indices) {
            val char = line[index]
            if (escaped) {
                escaped = false
            } else {
                when {
                    char == '\\' -> {
                        escaped = true
                    }

                    char == '=' || char == ':' -> {
                        separator = index
                        break
                    }

                    char.isWhitespace() -> {
                        separator = index
                        whitespaceSeparator = true
                        break
                    }
                }
            }
        }
        if (separator < 0) return line to ""
        var valueStart = separator
        if (whitespaceSeparator) {
            while (valueStart < line.length && line[valueStart].isWhitespace()) valueStart++
            if (valueStart < line.length && line[valueStart] in charArrayOf('=', ':')) valueStart++
        } else {
            valueStart++
        }
        while (valueStart < line.length && line[valueStart].isWhitespace()) valueStart++
        return line.substring(0, separator) to line.substring(valueStart)
    }

    private fun decodePropertyEscapes(
        value: String,
        lineNumber: Int,
    ): String =
        buildString {
            var index = 0
            while (index < value.length) {
                val char = value[index++]
                if (char != '\\') {
                    append(char)
                    continue
                }
                require(index < value.length) { backendMessage("properties.dangling.escape", lineNumber) }
                when (val escaped = value[index++]) {
                    't' -> {
                        append('\t')
                    }

                    'n' -> {
                        append('\n')
                    }

                    'r' -> {
                        append('\r')
                    }

                    'f' -> {
                        append('\u000C')
                    }

                    'u' -> {
                        require(index + 4 <= value.length) { backendMessage("properties.unicode.escape", lineNumber) }
                        val hex = value.substring(index, index + 4)
                        require(hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                            backendMessage("properties.unicode.escape", lineNumber)
                        }
                        append(hex.toInt(16).toChar())
                        index += 4
                    }

                    else -> {
                        append(escaped)
                    }
                }
            }
        }

    private fun writeProperties(values: Map<String, String>): String =
        buildString {
            values.forEach { (key, value) ->
                append(escapeProperty(key, key = true)).append('=').append(escapeProperty(value, key = false)).append('\n')
            }
        }

    private fun escapeProperty(
        value: String,
        key: Boolean,
    ): String =
        buildString {
            value.forEachIndexed { index, char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\u000C' -> append("\\f")
                    ' ' -> if (key || index == 0) append("\\ ") else append(char)
                    '=', ':' -> if (key) append('\\').append(char) else append(char)
                    '#', '!' -> if (key && index == 0) append('\\').append(char) else append(char)
                    else -> append(char)
                }
            }
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
            is JsonObject -> {
                element.forEach { (key, child) -> flattenJson(child, path + key, out, structuredKeys, keyPaths) }
            }

            is JsonPrimitive -> {
                require(element.isString || element.booleanOrNull != null || element.doubleOrNull != null) {
                    backendMessage("json.null.unsupported", displayKey)
                }.also {
                    require(displayKey !in out) { backendMessage("json.path.conflict", displayKey) }
                    out[displayKey] = element.content
                    keyPaths[displayKey] = path
                }
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
        val root =
            tree(values, keyPaths) { key, value ->
                if (key in structuredKeys) {
                    json.parseToJsonElement(value).also { require(it is JsonArray) { backendMessage("json.array.invalid", key) } }
                } else {
                    value
                }
            }

        fun convert(value: Any): JsonElement =
            when (value) {
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

    private fun writeYaml(values: Map<String, String>): String =
        buildString {
            fun appendMap(
                map: Map<String, Any>,
                depth: Int,
            ) {
                map.forEach { (key, value) ->
                    append("  ".repeat(depth)).append(yamlKey(key)).append(':')
                    if (value is Map<*, *>) {
                        append('\n')
                        @Suppress("UNCHECKED_CAST")
                        appendMap(value as Map<String, Any>, depth + 1)
                    } else {
                        append(' ').append(yamlValue(value.toString())).append('\n')
                    }
                }
            }
            appendMap(tree(values), 0)
        }

    private fun writePhp(values: Map<String, String>): String =
        buildString {
            append("<?php\n\nreturn [\n")

            fun appendMap(
                map: Map<String, Any>,
                depth: Int,
            ) {
                map.forEach { (key, value) ->
                    append("    ".repeat(depth)).append('\'').append(phpEscape(key)).append("' => ")
                    if (value is Map<*, *>) {
                        append("[\n")
                        @Suppress("UNCHECKED_CAST")
                        appendMap(value as Map<String, Any>, depth + 1)
                        append("    ".repeat(depth)).append("],\n")
                    } else {
                        append('\'').append(phpEscape(value.toString())).append("',\n")
                    }
                }
            }
            appendMap(tree(values), 1)
            append("];\n")
        }

    private fun parseYaml(
        text: String,
        schemeId: String,
        path: Path,
    ): LinkedHashMap<String, String> {
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
            if (rest.isEmpty()) {
                parents += indent to key
            } else {
                val full = (parents.map { it.second } + key).joinToString(".")
                require(full !in out) { backendMessage("yaml.duplicate.key", index + 1, full) }
                out[full] = unquote(stripYamlComment(rest))
            }
        }
        return out
    }

    private fun findYamlColon(line: String): Int {
        var quote: Char? = null
        line.forEachIndexed { i, c ->
            if (c == '\'' ||
                c == '"'
            ) {
                quote =
                    if (quote == c) {
                        null
                    } else if (quote == null) {
                        c
                    } else {
                        quote
                    }
            }
            ; if (c == ':' && quote == null) return i
        }
        return -1
    }

    private fun stripYamlComment(value: String): String {
        var quote: Char? = null
        value.forEachIndexed { i, c ->
            if (c == '\'' ||
                c == '"'
            ) {
                quote =
                    if (quote ==
                        c
                    ) {
                        null
                    } else if (quote ==
                        null
                    ) {
                        c
                    } else {
                        quote
                    }
            }
            ; if (c == '#' && quote == null && i > 0 &&
                value[i - 1].isWhitespace()
            ) {
                return value.substring(0, i).trim()
            }
        }
        return value
    }

    private fun unquote(value: String): String =
        if (value.length >= 2 && value.first() == value.last() &&
            value.first() in charArrayOf('\'', '"')
        ) {
            if (value.first() == '"') {
                value
                    .substring(1, value.lastIndex)
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            } else {
                value.substring(1, value.lastIndex).replace("''", "'")
            }
        } else {
            value
        }

    private fun yamlKey(value: String) = if (value.matches(Regex("[A-Za-z0-9_.-]+"))) value else yamlValue(value)

    private fun yamlValue(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""

    private fun phpEscape(value: String) =
        value
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
}

internal class PhpArrayParser(
    private val source: String,
) {
    companion object {
        private val STRICT_TYPES_DECLARE = Regex("declare\\s*\\(\\s*strict_types\\s*=\\s*1\\s*\\)\\s*;")
    }

    private var index = 0
    private val out = linkedMapOf<String, String>()

    fun parse(): LinkedHashMap<String, String> {
        if (source.startsWith('\uFEFF')) index++
        skipTrivia()
        if (peek("<?php")) index += 5
        skipTrivia()
        if (peekWord("declare")) {
            val declaration = STRICT_TYPES_DECLARE.find(source, index)
            require(declaration != null && declaration.range.first == index) { backendMessage("php.declare.strict.only") }
            index = declaration.range.last + 1
            skipTrivia()
        }
        expectWord("return")
        skipTrivia()
        parseMap("")
        skipTrivia()
        if (index < source.length && source[index] == ';') index++
        skipTrivia()
        require(index == source.length) { backendMessage("php.trailing.code") }
        return out
    }

    private fun parseMap(prefix: String) {
        val closing =
            when {
                peek("[") -> {
                    index++
                    ']'
                }

                peekWord("array") -> {
                    expectWord("array")
                    skipTrivia()
                    expect('(')
                    ')'
                }

                else -> {
                    error(backendMessage("php.return.array.only"))
                }
            }
        skipTrivia()
        while (index < source.length && source[index] != closing) {
            val key = parseString()
            skipTrivia()
            require(peek("=>")) { backendMessage("php.missing.arrow") }
            index += 2
            skipTrivia()
            val full = if (prefix.isEmpty()) key else "$prefix.$key"
            if (peek("[") || peekWord("array")) {
                parseMap(full)
            } else {
                val value = parseScalar()
                require(full !in out) { backendMessage("php.duplicate.key", full) }
                out[full] = value
            }
            skipTrivia()
            if (index < source.length &&
                source[index] == ','
            ) {
                index++
                skipTrivia()
            } else if (source[index] !=
                closing
            ) {
                error(backendMessage("php.missing.comma"))
            }
        }
        expect(closing)
    }

    private fun parseScalar(): String =
        if (index < source.length && source[index] in charArrayOf('\'', '"')) {
            parseString()
        } else {
            val start = index
            while (index < source.length && source[index] !in charArrayOf(',', ']', ')')) index++
            source.substring(start, index).trim().also {
                require(it.matches(Regex("-?[0-9.]+|true|false", RegexOption.IGNORE_CASE))) { backendMessage("php.scalar.only") }
            }
        }

    private fun parseString(): String {
        require(index < source.length && source[index] in charArrayOf('\'', '"')) { backendMessage("php.quoted.key.value") }
        val quote = source[index++]
        val result = StringBuilder()
        while (index < source.length) {
            val c = source[index++]
            if (c == quote) return result.toString()
            if (c ==
                '\\'
            ) {
                require(index < source.length)
                val next = source[index++]
                result.append(
                    when (next) {
                        'n' -> '\n'
                        'r' -> '\r'
                        't' -> '\t'
                        else -> next
                    },
                )
            } else {
                result.append(c)
            }
        }
        error(backendMessage("php.unclosed.string"))
    }

    private fun skipTrivia() {
        while (index <
            source.length
        ) {
            when {
                source[index].isWhitespace() -> {
                    index++
                }

                peek("//") || peek("#") -> {
                    while (index < source.length &&
                        source[index] != '\n'
                    ) {
                        index++
                    }
                }

                peek("/*") -> {
                    val end = source.indexOf("*/", index + 2)
                    require(end >= 0) { backendMessage("php.unclosed.comment") }
                    index =
                        end + 2
                }

                else -> {
                    return
                }
            }
        }
    }

    private fun expect(c: Char) {
        require(index < source.length && source[index] == c) { backendMessage("parser.expected.character", c) }
        index++
    }

    private fun expectWord(word: String) {
        require(peekWord(word)) { backendMessage("parser.expected.word", word) }
        index += word.length
    }

    private fun peek(value: String) = source.regionMatches(index, value, 0, value.length)

    private fun peekWord(value: String) =
        source.regionMatches(index, value, 0, value.length, ignoreCase = true) &&
            (index + value.length >= source.length || !source[index + value.length].isLetterOrDigit())
}
