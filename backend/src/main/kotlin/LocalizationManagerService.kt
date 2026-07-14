package cg.creamgod45

import cg.creamgod45.localization.*
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class LocalizationManagerService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    companion object {
        private const val CACHE_FORMAT_VERSION = 3
        private val LOG = Logger.getInstance(LocalizationManagerService::class.java)
        fun getInstance(project: Project): LocalizationManagerService = project.getService(LocalizationManagerService::class.java)
    }

    @Serializable
    private data class SchemeStore(val schemes: List<LanguageSchemeDto> = emptyList(), val activeSchemeId: String? = null)

    @Serializable
    private data class CacheStore(
        val formatVersion: Int = 0,
        val fingerprints: Map<String, Long>,
        val entries: List<LanguageEntryDto>,
        val issues: List<LanguageIssueDto>,
    )

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val storageDir: Path = Path.of(project.basePath ?: System.getProperty("java.io.tmpdir"), ".idea", "language-manager")
    private val schemeFile = storageDir.resolve("schemes.json")
    private val mutableState = MutableStateFlow(LocalizationStateDto())
    val state: StateFlow<LocalizationStateDto> = mutableState.asStateFlow()

    init {
        coroutineScope.launch(Dispatchers.IO + CoroutineName("Language Manager initialization")) {
            mutex.withLock {
                runCatching {
                    storageDir.createDirectories()
                    val store = readSchemeStore()
                    mutableState.value = LocalizationStateDto(schemes = store.schemes, activeSchemeId = store.activeSchemeId)
                    store.activeSchemeId?.let { id -> store.schemes.firstOrNull { it.id == id }?.let { loadScheme(it, false) } }
                }.onFailure {
                    mutableState.value = mutableState.value.copy(busy = false, errorMessage = safeMessage(it))
                    LOG.warn("Failed to initialize Language Manager", it)
                }
            }
        }
    }

    suspend fun createScheme(name: String, rawFiles: List<String>) = mutex.withLock {
        LOG.info("Creating localization scheme '$name' with ${rawFiles.size} explicitly selected files")
        require(name.trim().length in 1..80) { backendMessage("scheme.name.length") }
        require(rawFiles.isNotEmpty()) { backendMessage("scheme.files.required") }
        val files = rawFiles.map(SafeLanguageFileAccess::validate).distinct().map(Path::toString)
        val scheme = LanguageSchemeDto(UUID.randomUUID().toString(), sanitizeText(name, 80), files, System.currentTimeMillis())
        val schemes = mutableState.value.schemes + scheme
        mutableState.value = mutableState.value.copy(schemes = schemes, activeSchemeId = scheme.id, errorMessage = null)
        persistSchemes()
        loadScheme(scheme, true)
    }

    suspend fun deleteScheme(id: String) = mutex.withLock {
        val schemes = mutableState.value.schemes.filterNot { it.id == id }
        val active = if (mutableState.value.activeSchemeId == id) schemes.firstOrNull()?.id else mutableState.value.activeSchemeId
        Files.deleteIfExists(cacheFile(id))
        mutableState.value = mutableState.value.copy(schemes = schemes, activeSchemeId = active, entries = emptyList(), issues = emptyList(), errorMessage = null)
        persistSchemes()
        active?.let { next -> schemes.firstOrNull { it.id == next }?.let { loadScheme(it, false) } }
    }

    suspend fun activateScheme(id: String) = mutex.withLock {
        val scheme = mutableState.value.schemes.firstOrNull { it.id == id } ?: error(backendMessage("scheme.not.found"))
        mutableState.value = mutableState.value.copy(activeSchemeId = id, entries = emptyList(), issues = emptyList(), errorMessage = null)
        persistSchemes()
        loadScheme(scheme, false)
    }

    suspend fun reload(id: String, force: Boolean) = mutex.withLock {
        val scheme = requireScheme(id)
        loadScheme(scheme, force)
    }

    fun discoverLanguageFiles(folderPath: String): FolderDiscoveryDto = LanguageFolderDiscovery.discover(folderPath)

    suspend fun saveEntry(schemeId: String, mutation: EntryMutationDto) = mutex.withLock {
        val scheme = requireScheme(schemeId)
        validateMutation(scheme, mutation)
        val targetPath = SafeLanguageFileAccess.validate(mutation.filePath)
        val documents = parseDocuments(scheme)
        if (documents.any { it.issues.any { issue -> issue.severity == IssueSeverity.ERROR } }) error(backendMessage("edit.fix.parse.first"))
        var structuredValue = false
        var originalKey: String? = null
        var originalKeyPath: List<String>? = null
        mutation.id?.let { id ->
            val original = mutableState.value.entries.firstOrNull { it.id == id } ?: error(backendMessage("entry.not.found"))
            originalKey = original.key
            documents.firstOrNull { it.path.toString() == original.filePath }?.let { originalDocument ->
                structuredValue = original.key in originalDocument.structuredValueKeys
                originalKeyPath = originalDocument.keyPaths.remove(original.key)
                originalDocument.values.remove(original.key)
                originalDocument.structuredValueKeys.remove(original.key)
            }
        }
        val document = documents.firstOrNull { it.path == targetPath } ?: error(backendMessage("entry.target.outside"))
        if (mutation.id == null && mutation.key in document.values) error(backendMessage("entry.key.exists", mutation.key))
        document.values[mutation.key] = sanitizeText(mutation.value, 100_000)
        if (structuredValue) document.structuredValueKeys += mutation.key
        document.keyPaths[mutation.key] = when {
            originalKeyPath != null && mutation.key == originalKey -> originalKeyPath
            originalKeyPath?.size == 1 -> listOf(mutation.key)
            mutation.key.any(Char::isWhitespace) -> listOf(mutation.key)
            else -> mutation.key.split('.').filter(String::isNotBlank)
        }
        documents.filter { it.path == targetPath || mutation.id != null && it.values !== document.values }.forEach(LanguageFileCodec::write)
        loadScheme(scheme, true)
    }

    suspend fun deleteEntries(schemeId: String, ids: List<String>) = mutex.withLock {
        require(ids.isNotEmpty()) { backendMessage("entry.selection.required") }
        val scheme = requireScheme(schemeId)
        val selected = mutableState.value.entries.filter { it.id in ids }
        val documents = parseDocuments(scheme)
        selected.groupBy { it.filePath }.forEach { (path, entries) ->
            val document = documents.firstOrNull { it.path.toString() == path } ?: return@forEach
            entries.forEach { document.values.remove(it.key); document.structuredValueKeys.remove(it.key); document.keyPaths.remove(it.key) }
            LanguageFileCodec.write(document)
        }
        loadScheme(scheme, true)
    }

    suspend fun renameKey(schemeId: String, oldKey: String, newKeyRaw: String) = mutex.withLock {
        val scheme = requireScheme(schemeId)
        val newKey = validateKey(newKeyRaw)
        val documents = parseDocuments(scheme)
        var changed = false
        documents.forEach { document ->
            if (oldKey in document.values) {
                require(newKey !in document.values || newKey == oldKey) { backendMessage("entry.key.exists.file", document.path.fileName, newKey) }
                val rebuilt = linkedMapOf<String, String>()
                document.values.forEach { (key, value) -> rebuilt[if (key == oldKey) newKey else key] = value }
                if (oldKey in document.structuredValueKeys) {
                    document.structuredValueKeys.remove(oldKey)
                    document.structuredValueKeys += newKey
                }
                document.keyPaths.remove(oldKey)?.let { oldPath ->
                    document.keyPaths[newKey] = if (oldPath.size == 1) listOf(newKey) else newKey.split('.').filter(String::isNotBlank)
                }
                document.values.clear(); document.values.putAll(rebuilt)
                LanguageFileCodec.write(document); changed = true
            }
        }
        require(changed) { backendMessage("entry.key.not.found", oldKey) }
        loadScheme(scheme, true)
    }

    suspend fun repair(schemeId: String) = mutex.withLock {
        val scheme = requireScheme(schemeId)
        val documents = parseDocuments(scheme)
        val errors = documents.flatMap { it.issues }.filter { it.severity == IssueSeverity.ERROR }
        require(errors.isEmpty()) { backendMessage("repair.syntax") }
        documents.forEach { document ->
            document.values.replaceAll { key, value -> sanitizeText(value.ifBlank { key }, 100_000) }
            LanguageFileCodec.write(document)
        }
        loadScheme(scheme, true)
    }

    suspend fun repairEntries(schemeId: String, ids: List<String>) = mutex.withLock {
        require(ids.isNotEmpty()) { backendMessage("repair.selection.required") }
        val scheme = requireScheme(schemeId)
        val selected = mutableState.value.entries.filter { it.id in ids && it.value.isBlank() }
        require(selected.isNotEmpty()) { backendMessage("repair.no.empty.values") }
        val documents = parseDocuments(scheme)
        val errors = documents.flatMap { it.issues }.filter { it.severity == IssueSeverity.ERROR }
        require(errors.isEmpty()) { backendMessage("repair.parse.blocked") }
        selected.groupBy { it.filePath }.forEach { (path, entries) ->
            val document = documents.firstOrNull { it.path.toString() == path } ?: return@forEach
            entries.forEach { entry -> if (document.values[entry.key].isNullOrBlank()) document.values[entry.key] = entry.key }
            LanguageFileCodec.write(document)
        }
        loadScheme(scheme, true)
    }

    suspend fun previewChanges(schemeId: String, request: ChangePreviewRequestDto): ChangePreviewDto = mutex.withLock {
        buildChangePreview(requireScheme(schemeId), request)
    }

    suspend fun applyPreviewedChanges(
        schemeId: String,
        request: ChangePreviewRequestDto,
        expectedBeforeHashes: Map<String, String>,
    ) = mutex.withLock {
        val scheme = requireScheme(schemeId)
        val preview = buildChangePreview(scheme, request)
        val actualHashes = preview.files.associate { it.filePath to it.beforeSha256 }
        require(actualHashes == expectedBeforeHashes) { backendMessage("preview.changed") }
        preview.files.forEach { change ->
            val path = SafeLanguageFileAccess.validate(change.filePath)
            require(contentSha256(SafeLanguageFileAccess.read(path)) == change.beforeSha256) {
                backendMessage("preview.changed.file", path.fileName)
            }
        }
        preview.files.forEach { change ->
            SafeLanguageFileAccess.atomicWrite(SafeLanguageFileAccess.validate(change.filePath), change.afterContent)
        }
        loadScheme(scheme, true)
    }

    private fun buildChangePreview(scheme: LanguageSchemeDto, request: ChangePreviewRequestDto): ChangePreviewDto {
        require(request.normalizeAll || request.repairEntryIds.isNotEmpty() || request.deleteEntryIds.isNotEmpty()) { backendMessage("preview.no.request") }
        val documents = parseDocuments(scheme)
        val errors = documents.flatMap { it.issues }.filter { it.severity == IssueSeverity.ERROR }
        require(errors.isEmpty()) { backendMessage("preview.parse.blocked") }
        val affectedPaths = linkedSetOf<String>()

        if (request.normalizeAll) {
            documents.forEach { document ->
                document.values.replaceAll { key, value -> sanitizeText(value.ifBlank { key }, 100_000) }
                affectedPaths += document.path.toString()
            }
        } else {
            val repairs = mutableState.value.entries.filter { it.id in request.repairEntryIds && it.value.isBlank() }
            val deletions = mutableState.value.entries.filter { it.id in request.deleteEntryIds }
            require(repairs.isNotEmpty() || deletions.isNotEmpty()) { backendMessage("preview.no.safe.changes") }
            repairs.groupBy { it.filePath }.forEach { (path, entries) ->
                val document = documents.firstOrNull { it.path.toString() == path } ?: return@forEach
                entries.forEach { entry -> if (document.values[entry.key].isNullOrBlank()) document.values[entry.key] = entry.key }
                affectedPaths += path
            }
            deletions.groupBy { it.filePath }.forEach { (path, entries) ->
                val document = documents.firstOrNull { it.path.toString() == path } ?: return@forEach
                entries.forEach { entry ->
                    document.values.remove(entry.key)
                    document.structuredValueKeys.remove(entry.key)
                    document.keyPaths.remove(entry.key)
                }
                affectedPaths += path
            }
        }

        return ChangePreviewDto(documents.filter { it.path.toString() in affectedPaths }.mapNotNull { document ->
            val before = SafeLanguageFileAccess.read(document.path)
            val after = LanguageFileCodec.render(document)
            if (before == after) null else FileChangePreviewDto(document.path.toString(), before, after, contentSha256(before))
        })
    }

    private fun contentSha256(content: String): String = MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private fun loadScheme(scheme: LanguageSchemeDto, force: Boolean) {
        mutableState.value = mutableState.value.copy(busy = true, errorMessage = null)
        try {
            val fingerprints = fingerprints(scheme)
            if (!force) readCache(scheme.id)?.takeIf { it.formatVersion == CACHE_FORMAT_VERSION && it.fingerprints == fingerprints }?.let { cache ->
                LOG.info("Loaded localization scheme '${scheme.name}' from cache (${cache.entries.size} entries)")
                mutableState.value = mutableState.value.copy(activeSchemeId = scheme.id, entries = cache.entries, issues = cache.issues, busy = false)
                return
            }
            val documents = parseDocuments(scheme)
            val entriesWithoutUsage = documents.flatMap { document ->
                document.values.map { (key, value) ->
                    val namespace = document.namespace
                    LanguageEntryDto(entryId(document.path, key), scheme.id, document.path.toString(), document.locale, namespace, key, value)
                }
            }
            // Publish parsed rows before the optional project-wide usage scan so
            // large/remote projects never leave the tool window looking idle.
            mutableState.value = mutableState.value.copy(
                activeSchemeId = scheme.id,
                entries = entriesWithoutUsage,
                issues = documents.flatMap { it.issues },
                busy = true,
            )
            val usages = usageCounts(entriesWithoutUsage, scheme.files)
            val entries = entriesWithoutUsage.map { it.copy(usageCount = usages[it.id] ?: 0) }
            val issues = documents.flatMap { it.issues } + LocalizationAnalysis.analyze(scheme.id, entries)
            val cache = CacheStore(CACHE_FORMAT_VERSION, fingerprints, entries, issues)
            writeJson(cacheFile(scheme.id), json.encodeToString(cache))
            mutableState.value = mutableState.value.copy(activeSchemeId = scheme.id, entries = entries, issues = issues, busy = false)
            LOG.info("Loaded localization scheme '${scheme.name}': ${entries.size} entries, ${issues.size} issues")
        } catch (e: Exception) {
            mutableState.value = mutableState.value.copy(busy = false, errorMessage = safeMessage(e))
            LOG.warn("Failed to load localization scheme '${scheme.name}'", e)
        }
    }

    private fun parseDocuments(scheme: LanguageSchemeDto): List<ParsedLanguageFile> = scheme.files.map { raw ->
        try { LanguageFileCodec.parse(SafeLanguageFileAccess.validate(raw), scheme.id) }
        catch (e: Exception) { ParsedLanguageFile(Path.of(raw), "", "", linkedMapOf(), issues = mutableListOf(LanguageIssueDto(scheme.id, raw, severity = IssueSeverity.ERROR, code = "READ_ERROR", message = safeMessage(e)))) }
    }

    private fun usageCounts(entries: List<LanguageEntryDto>, languageFiles: List<String>): Map<String, Int> {
        val root = project.basePath?.let(Path::of) ?: return emptyMap()
        val counts = entries.associate { it.id to 0 }.toMutableMap()
        val needleOwners = mutableMapOf<String, MutableSet<String>>()
        entries.forEach { entry ->
            setOf(entry.key, if (entry.namespace.isBlank()) entry.key else "${entry.namespace}.${entry.key}").forEach { needle ->
                needleOwners.getOrPut(needle) { linkedSetOf() } += entry.id
            }
        }
        val ignoredDirectories = setOf(".git", ".idea", ".gradle", "build", "dist", "vendor", "node_modules", "storage", "cache", "coverage")
        val sourceExtensions = setOf("php", "js", "ts", "tsx", "jsx", "vue", "java", "kt", "kts", "py", "rb", "go", "rs", "html", "twig")
        val quoted = Regex("[\\\"']([^\\\"'\\r\\n]{1,256})[\\\"']")
        val token = Regex("[A-Za-z0-9_.-]{2,256}")
        var visitedFiles = 0
        runCatching {
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult =
                    if (dir != root && dir.fileName.toString().lowercase() in ignoredDirectories) FileVisitResult.SKIP_SUBTREE else FileVisitResult.CONTINUE

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (visitedFiles >= 2000) return FileVisitResult.TERMINATE
                    val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
                    if (extension !in sourceExtensions || attrs.size() > 512_000 || file.toString() in languageFiles) return FileVisitResult.CONTINUE
                    visitedFiles++
                    runCatching {
                        Files.newBufferedReader(file, StandardCharsets.UTF_8).useLines { lines ->
                            lines.forEach { line ->
                                val candidates = linkedSetOf<String>()
                                quoted.findAll(line).forEach { candidates += it.groupValues[1] }
                                token.findAll(line).forEach { candidates += it.value }
                                val matchedIds = candidates.flatMapTo(linkedSetOf()) { needleOwners[it].orEmpty() }
                                matchedIds.forEach { id -> counts[id] = counts.getValue(id) + 1 }
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        }.onFailure { LOG.debug("Usage scan stopped early", it) }
        LOG.info("Usage scan checked $visitedFiles source files for ${entries.size} localization entries")
        return counts
    }

    private fun validateMutation(scheme: LanguageSchemeDto, mutation: EntryMutationDto) {
        require(mutation.filePath in scheme.files || runCatching { SafeLanguageFileAccess.validate(mutation.filePath).toString() in scheme.files }.getOrDefault(false)) { backendMessage("entry.outside.scheme") }
        validateKey(mutation.key)
        require(mutation.locale.matches(Regex("[A-Za-z0-9_-]{1,32}"))) { backendMessage("locale.invalid") }
        require(mutation.namespace.isEmpty() || mutation.namespace.matches(Regex("[A-Za-z0-9_.-]{1,128}"))) { backendMessage("namespace.invalid") }
        sanitizeText(mutation.value, 100_000)
    }
    private fun validateKey(raw: String): String = sanitizeText(raw, 256).also { require(it.matches(Regex("[A-Za-z0-9_.-]{1,256}"))) { backendMessage("key.invalid") } }
    private fun sanitizeText(raw: String, max: Int): String { require(raw.length <= max) { backendMessage("input.too.long") }; require(raw.none { it == '\u0000' || (it.code < 32 && it !in "\n\r\t") }) { backendMessage("input.control") }; return raw.trim() }
    private fun requireScheme(id: String) = mutableState.value.schemes.firstOrNull { it.id == id } ?: error(backendMessage("scheme.not.found"))
    private fun fingerprints(scheme: LanguageSchemeDto) = scheme.files.associateWith { raw -> runCatching { Files.getLastModifiedTime(SafeLanguageFileAccess.validate(raw)).toMillis() xor Files.size(SafeLanguageFileAccess.validate(raw)) }.getOrDefault(-1L) }
    private fun entryId(path: Path, key: String) = MessageDigest.getInstance("SHA-256").digest("$path\u0000$key".toByteArray()).take(16).joinToString("") { "%02x".format(it) }
    private fun cacheFile(id: String) = storageDir.resolve("cache-$id.json")
    private fun readCache(id: String): CacheStore? = runCatching { json.decodeFromString<CacheStore>(Files.readString(cacheFile(id))) }.getOrNull()
    private fun readSchemeStore(): SchemeStore = if (schemeFile.exists()) json.decodeFromString(Files.readString(schemeFile)) else SchemeStore()
    private fun persistSchemes() = writeJson(schemeFile, json.encodeToString(SchemeStore(mutableState.value.schemes, mutableState.value.activeSchemeId)))
    private fun writeJson(path: Path, content: String) { path.parent.createDirectories(); SafeLanguageFileAccess.atomicWrite(path, content) }
    private fun safeMessage(error: Throwable) = (error.message ?: error.javaClass.simpleName).replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F]"), "?").take(500)
}
