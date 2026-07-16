package cg.creamgod45

import cg.creamgod45.localization.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

@Service(Service.Level.PROJECT)
class LocalizationManagerService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {
    companion object {
        private const val CACHE_FORMAT_VERSION = 5
        private const val MAX_PERSISTED_STATE_BYTES = 10L * 1024 * 1024
        private const val MAX_DISK_CACHE_ENTRIES = 25_000
        private const val MAX_ESTIMATED_CACHE_CHARS = 5_000_000L
        private val LOG = Logger.getInstance(LocalizationManagerService::class.java)

        fun getInstance(project: Project): LocalizationManagerService = project.getService(LocalizationManagerService::class.java)
    }

    @Serializable
    private data class SchemeStore(
        val schemes: List<LanguageSchemeDto> = emptyList(),
        val activeSchemeId: String? = null,
    )

    @Serializable
    private data class CacheStore(
        val formatVersion: Int = 0,
        val fingerprints: Map<String, Long>,
        val entries: List<LanguageEntryDto>,
        val issues: List<LanguageIssueDto>,
    )

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }
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

    suspend fun createScheme(
        name: String,
        rawFiles: List<String>,
        rawUsageSettings: UsageScanSettingsDto,
    ) = mutex.withLock {
        LOG.info("Creating localization scheme '$name' with ${rawFiles.size} explicitly selected files")
        require(name.trim().length in 1..80) { backendMessage("scheme.name.length") }
        require(rawFiles.isNotEmpty()) { backendMessage("scheme.files.required") }
        val files = rawFiles.map(SafeLanguageFileAccess::validate).distinct().map(Path::toString)
        val scheme =
            LanguageSchemeDto(
                UUID.randomUUID().toString(),
                sanitizeText(name, 80),
                files,
                System.currentTimeMillis(),
                UsageScanSupport.normalize(rawUsageSettings),
            )
        val schemes = mutableState.value.schemes + scheme
        mutableState.value = mutableState.value.copy(schemes = schemes, activeSchemeId = scheme.id, errorMessage = null)
        persistSchemes()
        loadScheme(scheme, true)
    }

    suspend fun deleteScheme(id: String) =
        mutex.withLock {
            val schemes = mutableState.value.schemes.filterNot { it.id == id }
            val active = if (mutableState.value.activeSchemeId == id) schemes.firstOrNull()?.id else mutableState.value.activeSchemeId
            Files.deleteIfExists(cacheFile(id))
            mutableState.value =
                mutableState.value.copy(
                    schemes = schemes,
                    activeSchemeId = active,
                    entries = emptyList(),
                    issues = emptyList(),
                    errorMessage = null,
                )
            persistSchemes()
            active?.let { next -> schemes.firstOrNull { it.id == next }?.let { loadScheme(it, false) } }
        }

    suspend fun activateScheme(id: String) =
        mutex.withLock {
            val scheme = mutableState.value.schemes.firstOrNull { it.id == id } ?: error(backendMessage("scheme.not.found"))
            mutableState.value =
                mutableState.value.copy(activeSchemeId = id, entries = emptyList(), issues = emptyList(), errorMessage = null)
            persistSchemes()
            loadScheme(scheme, false)
        }

    suspend fun updateSchemeUsageSettings(
        id: String,
        rawSettings: UsageScanSettingsDto,
    ) = mutex.withLock {
        val scheme = requireScheme(id)
        val settings = UsageScanSupport.normalize(rawSettings)
        val updated = scheme.copy(usageScanSettings = settings, updatedAtEpochMs = System.currentTimeMillis())
        val schemes = mutableState.value.schemes.map { if (it.id == id) updated else it }
        mutableState.value = mutableState.value.copy(schemes = schemes, errorMessage = null)
        Files.deleteIfExists(cacheFile(id))
        persistSchemes()
        if (mutableState.value.activeSchemeId == id) {
            coroutineScope.launch(Dispatchers.IO + CoroutineName("Language Manager usage settings reload")) {
                mutex.withLock {
                    mutableState.value.schemes
                        .firstOrNull { it.id == id }
                        ?.let { loadScheme(it, true) }
                }
            }
        }
    }

    suspend fun reload(
        id: String,
        force: Boolean,
    ) = mutex.withLock {
        val scheme = requireScheme(id)
        loadScheme(scheme, force)
    }

    fun discoverLanguageFiles(
        folderPaths: List<String>,
        rawSettings: UsageScanSettingsDto,
    ): FolderDiscoveryDto = LanguageFolderDiscovery.discover(folderPaths, UsageScanSupport.normalize(rawSettings))

    suspend fun exportSchemeSettings(): String =
        mutex.withLock {
            SchemeSettingsTransferSupport.export(mutableState.value.schemes, project.basePath)
        }

    fun previewSchemeSettingsImport(content: String): SchemeImportPreviewDto =
        SchemeSettingsTransferSupport.preview(content, project.basePath)

    suspend fun importSchemeSettings(content: String) =
        mutex.withLock {
            val imported = SchemeSettingsTransferSupport.resolve(content, project.basePath)
            val now = System.currentTimeMillis()
            val newSchemes =
                imported.mapIndexed { index, scheme ->
                    LanguageSchemeDto(
                        id = UUID.randomUUID().toString(),
                        name = sanitizeText(scheme.name, 80),
                        files = scheme.files,
                        updatedAtEpochMs = now + index,
                        usageScanSettings = scheme.usageScanSettings,
                    )
                }
            val schemes = mutableState.value.schemes + newSchemes
            val active = newSchemes.last().id
            mutableState.value = mutableState.value.copy(schemes = schemes, activeSchemeId = active, errorMessage = null)
            persistSchemes()
            loadScheme(newSchemes.last(), true)
        }

    suspend fun saveEntry(
        schemeId: String,
        mutation: EntryMutationDto,
    ) = saveEntries(schemeId, listOf(mutation))

    suspend fun saveEntries(
        schemeId: String,
        mutations: List<EntryMutationDto>,
    ) = mutex.withLock {
        val scheme = requireScheme(schemeId)
        require(mutations.isNotEmpty()) { backendMessage("entry.mutations.required") }
        mutations.forEach { validateMutation(scheme, it) }
        val documents = parseDocuments(scheme)
        if (documents.any {
                it.issues.any { issue ->
                    issue.severity == IssueSeverity.ERROR
                }
            }
        ) {
            error(backendMessage("edit.fix.parse.first"))
        }
        val changedDocuments =
            EntryMutationSupport.apply(documents, mutableState.value.entries, mutations) { value ->
                sanitizeText(value, 100_000)
            }
        val originalContents = changedDocuments.associate { it.path to SafeLanguageFileAccess.read(it.path) }
        val written = mutableListOf<Path>()
        try {
            changedDocuments.forEach { document ->
                SafeLanguageFileAccess.atomicWrite(document.path, LanguageFileCodec.render(document))
                written.add(document.path)
            }
        } catch (error: Exception) {
            written.asReversed().forEach { path ->
                runCatching { SafeLanguageFileAccess.atomicWrite(path, originalContents.getValue(path)) }
                    .exceptionOrNull()
                    ?.let(error::addSuppressed)
            }
            throw error
        }
        loadScheme(scheme, true)
    }

    suspend fun previewEntryMutations(
        schemeId: String,
        mutations: List<EntryMutationDto>,
    ): ChangePreviewDto = mutex.withLock { buildEntryMutationPreview(requireScheme(schemeId), mutations) }

    suspend fun applyPreviewedEntryMutations(
        schemeId: String,
        mutations: List<EntryMutationDto>,
        expectedBeforeHashes: Map<String, String>,
    ) = mutex.withLock {
        val scheme = requireScheme(schemeId)
        val preview = buildEntryMutationPreview(scheme, mutations)
        require(preview.files.associate { it.filePath to it.beforeSha256 } == expectedBeforeHashes) { backendMessage("preview.changed") }
        val written = mutableListOf<FileChangePreviewDto>()
        try {
            preview.files.forEach { change ->
                SafeLanguageFileAccess.atomicWrite(Path.of(change.filePath), change.afterContent)
                written += change
            }
        } catch (error: Exception) {
            written.asReversed().forEach { change ->
                runCatching { SafeLanguageFileAccess.atomicWrite(Path.of(change.filePath), change.beforeContent) }
                    .exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
        loadScheme(scheme, true)
    }

    private fun buildEntryMutationPreview(
        scheme: LanguageSchemeDto,
        mutations: List<EntryMutationDto>,
    ): ChangePreviewDto {
        require(mutations.isNotEmpty()) { backendMessage("entry.mutations.required") }
        mutations.forEach { validateMutation(scheme, it) }
        val documents = parseDocuments(scheme)
        require(documents.none { document -> document.issues.any { it.severity == IssueSeverity.ERROR } }) {
            backendMessage("edit.fix.parse.first")
        }
        val changed = EntryMutationSupport.apply(documents, mutableState.value.entries, mutations) { sanitizeText(it, 100_000) }
        return ChangePreviewDto(changed.mapNotNull { document ->
            val before = SafeLanguageFileAccess.read(document.path)
            val after = LanguageFileCodec.render(document)
            if (before == after) null else FileChangePreviewDto(document.path.toString(), before, after, contentSha256(before))
        })
    }

    suspend fun deleteEntries(
        schemeId: String,
        ids: List<String>,
    ) = mutex.withLock {
        require(ids.isNotEmpty()) { backendMessage("entry.selection.required") }
        val scheme = requireScheme(schemeId)
        val selected = mutableState.value.entries.filter { it.id in ids }
        val documents = parseDocuments(scheme)
        selected.groupBy { it.filePath }.forEach { (path, entries) ->
            val document = documents.firstOrNull { it.path.toString() == path } ?: return@forEach
            entries.forEach {
                document.values.remove(it.key)
                document.structuredValueKeys.remove(it.key)
                document.keyPaths.remove(it.key)
            }
            LanguageFileCodec.write(document)
        }
        loadScheme(scheme, true)
    }

    suspend fun renameKey(
        schemeId: String,
        oldKey: String,
        newKeyRaw: String,
    ) = mutex.withLock {
        val scheme = requireScheme(schemeId)
        val newKey = validateKey(newKeyRaw)
        val documents = parseDocuments(scheme)
        var changed = false
        documents.forEach { document ->
            if (oldKey in document.values) {
                require(
                    newKey !in document.values || newKey == oldKey,
                ) { backendMessage("entry.key.exists.file", document.path.fileName, newKey) }
                val rebuilt = linkedMapOf<String, String>()
                document.values.forEach { (key, value) -> rebuilt[if (key == oldKey) newKey else key] = value }
                if (oldKey in document.structuredValueKeys) {
                    document.structuredValueKeys.remove(oldKey)
                    document.structuredValueKeys += newKey
                }
                document.keyPaths.remove(oldKey)?.let { oldPath ->
                    document.keyPaths[newKey] = if (oldPath.size == 1) listOf(newKey) else newKey.split('.').filter(String::isNotBlank)
                }
                document.values.clear()
                document.values.putAll(rebuilt)
                LanguageFileCodec.write(document)
                changed = true
            }
        }
        require(changed) { backendMessage("entry.key.not.found", oldKey) }
        loadScheme(scheme, true)
    }

    suspend fun repair(schemeId: String) =
        mutex.withLock {
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

    suspend fun repairEntries(
        schemeId: String,
        ids: List<String>,
    ) = mutex.withLock {
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

    suspend fun previewLocaleVersion(
        schemeId: String,
        request: LocaleVersionRequestDto,
    ): ChangePreviewDto =
        mutex.withLock {
            buildLocaleVersionPreview(requireScheme(schemeId), request)
        }

    suspend fun createLocaleVersion(
        schemeId: String,
        request: LocaleVersionRequestDto,
        expectedTargetHashes: Map<String, String>,
    ) = mutex.withLock {
        val scheme = requireScheme(schemeId)
        val preview = buildLocaleVersionPreview(scheme, request)
        val actualHashes = preview.files.associate { it.filePath to it.beforeSha256 }
        require(actualHashes == expectedTargetHashes) { backendMessage("preview.changed") }
        preview.files.forEach { change ->
            require(!Files.exists(Path.of(change.filePath))) { backendMessage("locale.version.target.exists", change.filePath) }
        }

        val previousState = mutableState.value
        val createdFiles = mutableListOf<Path>()
        try {
            preview.files.forEach { change ->
                val path = Path.of(change.filePath).toAbsolutePath().normalize()
                path.parent.createDirectories()
                Files.createFile(path)
                createdFiles.add(path)
                SafeLanguageFileAccess.atomicWrite(path, change.afterContent)
            }
            val updatedScheme =
                scheme.copy(
                    files = (scheme.files + createdFiles.map(Path::toString)).distinct(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
            mutableState.value =
                previousState.copy(
                    schemes = previousState.schemes.map { if (it.id == scheme.id) updatedScheme else it },
                    activeSchemeId = scheme.id,
                    errorMessage = null,
                )
            persistSchemes()
            loadScheme(updatedScheme, true)
        } catch (error: Exception) {
            createdFiles.asReversed().forEach { path -> runCatching { Files.deleteIfExists(path) } }
            mutableState.value = previousState
            runCatching { persistSchemes() }
            throw error
        }
    }

    suspend fun previewChanges(
        schemeId: String,
        request: ChangePreviewRequestDto,
    ): ChangePreviewDto =
        mutex.withLock {
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

    private fun buildChangePreview(
        scheme: LanguageSchemeDto,
        request: ChangePreviewRequestDto,
    ): ChangePreviewDto {
        require(request.normalizeAll || request.repairEntryIds.isNotEmpty() || request.deleteEntryIds.isNotEmpty()) {
            backendMessage("preview.no.request")
        }
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

        return ChangePreviewDto(
            documents.filter { it.path.toString() in affectedPaths }.mapNotNull { document ->
                val before = SafeLanguageFileAccess.read(document.path)
                val after = LanguageFileCodec.render(document)
                if (before == after) null else FileChangePreviewDto(document.path.toString(), before, after, contentSha256(before))
            },
        )
    }

    private fun buildLocaleVersionPreview(
        scheme: LanguageSchemeDto,
        request: LocaleVersionRequestDto,
    ): ChangePreviewDto {
        val targets = LanguageLocaleVersionSupport.buildTargets(parseDocuments(scheme), request.sourceLocale, request.targetLocale)
        val emptyHash = contentSha256("")
        return ChangePreviewDto(
            targets.map { target ->
                FileChangePreviewDto(
                    filePath = target.path.toString(),
                    beforeContent = "",
                    afterContent = target.content,
                    beforeSha256 = emptyHash,
                )
            },
        )
    }

    private fun contentSha256(content: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(content.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun loadScheme(
        scheme: LanguageSchemeDto,
        force: Boolean,
    ) {
        mutableState.value = mutableState.value.copy(busy = true, errorMessage = null)
        try {
            val fingerprints = fingerprints(scheme)
            if (!force) {
                readCache(scheme.id)
                    ?.takeIf {
                        it.formatVersion == CACHE_FORMAT_VERSION &&
                            it.fingerprints == fingerprints &&
                            cacheFitsLimits(scheme, it)
                    }?.let { cache ->
                    LOG.info("Loaded localization scheme '${scheme.name}' from cache (${cache.entries.size} entries)")
                    mutableState.value =
                        mutableState.value.copy(activeSchemeId = scheme.id, entries = cache.entries, issues = cache.issues, busy = false)
                    return
                }
            }
            val documents = parseDocuments(scheme)
            val entriesWithoutUsage =
                documents.flatMap { document ->
                    document.values.map { (key, value) ->
                        val namespace = document.namespace
                        LanguageEntryDto(
                            entryId(document.path, key),
                            scheme.id,
                            document.path.toString(),
                            document.locale,
                            namespace,
                            key,
                            value,
                        )
                    }
                }
            // Publish parsed rows before the optional project-wide usage scan so
            // large/remote projects never leave the tool window looking idle.
            mutableState.value =
                mutableState.value.copy(
                    activeSchemeId = scheme.id,
                    entries = entriesWithoutUsage,
                    issues = documents.flatMap { it.issues },
                    busy = true,
                )
            val usageRoot =
                if (scheme.usageScanSettings.basePath.isBlank()) {
                    project.basePath
                        ?.let(Path::of)
                        ?.toAbsolutePath()
                        ?.normalize()
                } else {
                    SafeLanguageFileAccess.validateDirectory(scheme.usageScanSettings.basePath)
                }
            val usages =
                usageRoot
                    ?.let {
                        UsageScanSupport.counts(it, entriesWithoutUsage, scheme.files, scheme.usageScanSettings)
                    }.orEmpty()
            val entries = entriesWithoutUsage.map { it.copy(usageCount = usages[it.id] ?: 0) }
            val issues = documents.flatMap { it.issues } + LocalizationAnalysis.analyze(scheme.id, entries)
            val cache = CacheStore(CACHE_FORMAT_VERSION, fingerprints, entries, issues)
            persistCacheIfSafe(scheme.id, cache)
            mutableState.value = mutableState.value.copy(activeSchemeId = scheme.id, entries = entries, issues = issues, busy = false)
            LOG.info("Loaded localization scheme '${scheme.name}': ${entries.size} entries, ${issues.size} issues")
        } catch (e: Exception) {
            mutableState.value = mutableState.value.copy(busy = false, errorMessage = safeMessage(e))
            LOG.warn("Failed to load localization scheme '${scheme.name}'", e)
        }
    }

    private fun parseDocuments(scheme: LanguageSchemeDto): List<ParsedLanguageFile> {
        val budget = LanguageLoadBudget(scheme.usageScanSettings)
        return scheme.files.map { raw ->
            try {
                val path = SafeLanguageFileAccess.validate(raw)
                budget.acceptFile(path)
                val document = LanguageFileCodec.parse(path, scheme.id, scheme.usageScanSettings.maxEntriesPerFile)
                budget.acceptEntries(path, document.values.size)
                document
            } catch (
                e: Exception,
            ) {
                ParsedLanguageFile(
                    Path.of(raw),
                    "",
                    "",
                    linkedMapOf(),
                    issues =
                        mutableListOf(
                            LanguageIssueDto(scheme.id, raw, severity = IssueSeverity.ERROR, code = "READ_ERROR", message = safeMessage(e)),
                        ),
                )
            }
        }
    }

    private fun cacheFitsLimits(
        scheme: LanguageSchemeDto,
        cache: CacheStore,
    ): Boolean {
        return runCatching {
            val budget = LanguageLoadBudget(scheme.usageScanSettings)
            scheme.files.forEach { raw -> budget.acceptFile(SafeLanguageFileAccess.validate(raw)) }
            cache.entries
                .groupingBy { it.filePath }
                .eachCount()
                .forEach { (filePath, count) -> budget.acceptEntries(Path.of(filePath), count) }
            true
        }.getOrDefault(false)
    }

    private fun validateMutation(
        scheme: LanguageSchemeDto,
        mutation: EntryMutationDto,
    ) {
        require(
            mutation.filePath in scheme.files ||
                runCatching { SafeLanguageFileAccess.validate(mutation.filePath).toString() in scheme.files }.getOrDefault(false),
        ) { backendMessage("entry.outside.scheme") }
        validateKey(mutation.key)
        require(mutation.locale.matches(Regex("[A-Za-z0-9_-]{1,32}"))) { backendMessage("locale.invalid") }
        require(
            mutation.namespace.isEmpty() || mutation.namespace.matches(Regex("[A-Za-z0-9_.-]{1,128}")),
        ) { backendMessage("namespace.invalid") }
        sanitizeText(mutation.value, 100_000)
    }

    private fun validateKey(raw: String): String = TranslationInputValidation.key(raw)

    private fun sanitizeText(
        raw: String,
        max: Int,
    ): String {
        require(raw.length <= max) { backendMessage("input.too.long") }
        require(
            raw.none {
                it ==
                    '\u0000' ||
                    (it.code < 32 && it !in "\n\r\t")
            },
        ) { backendMessage("input.control") }
        return raw.trim()
    }

    private fun requireScheme(id: String) =
        mutableState.value.schemes.firstOrNull { it.id == id } ?: error(backendMessage("scheme.not.found"))

    private fun fingerprints(scheme: LanguageSchemeDto) =
        scheme.files.associateWith { raw ->
            runCatching {
                Files.getLastModifiedTime(SafeLanguageFileAccess.validate(raw)).toMillis() xor
                    Files.size(SafeLanguageFileAccess.validate(raw))
            }.getOrDefault(-1L)
        }

    private fun entryId(
        path: Path,
        key: String,
    ) = MessageDigest.getInstance("SHA-256").digest("$path\u0000$key".toByteArray()).take(16).joinToString("") {
        "%02x".format(it)
    }

    private fun cacheFile(id: String) = storageDir.resolve("cache-$id.json")

    private fun readCache(id: String): CacheStore? =
        runCatching {
            json.decodeFromString<CacheStore>(readBoundedState(cacheFile(id)))
        }.getOrNull()

    private fun readSchemeStore(): SchemeStore =
        if (schemeFile.exists()) json.decodeFromString(readBoundedState(schemeFile)) else SchemeStore()

    private fun readBoundedState(path: Path): String {
        require(Files.size(path) <= MAX_PERSISTED_STATE_BYTES) { backendMessage("load.persisted.state.too.large") }
        return Files.readString(path)
    }

    private fun persistCacheIfSafe(
        schemeId: String,
        cache: CacheStore,
    ) {
        val path = cacheFile(schemeId)
        val estimatedChars =
            cache.entries.sumOf { entry ->
                entry.filePath.length.toLong() + entry.locale.length + entry.namespace.length + entry.key.length + entry.value.length + 160
            } + cache.issues.sumOf { issue -> issue.filePath.length.toLong() + issue.key.length + issue.message.length + 120 }
        if (cache.entries.size > MAX_DISK_CACHE_ENTRIES || estimatedChars > MAX_ESTIMATED_CACHE_CHARS) {
            Files.deleteIfExists(path)
            LOG.info("Skipped oversized disk cache for scheme '$schemeId' (${cache.entries.size} entries)")
            return
        }
        writeJson(path, json.encodeToString(cache))
    }

    private fun persistSchemes() =
        writeJson(schemeFile, json.encodeToString(SchemeStore(mutableState.value.schemes, mutableState.value.activeSchemeId)))

    private fun writeJson(
        path: Path,
        content: String,
    ) {
        path.parent.createDirectories()
        SafeLanguageFileAccess.atomicWrite(path, content)
    }

    private fun safeMessage(error: Throwable) =
        (error.message ?: error.javaClass.simpleName).replace(Regex("[\u0000-\u0008\u000B\u000C\u000E-\u001F]"), "?").take(500)
}
