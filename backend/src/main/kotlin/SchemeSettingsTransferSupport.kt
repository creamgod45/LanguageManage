package cg.creamgod45

import cg.creamgod45.localization.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.file.Path
import cg.creamgod45.LanguageManagerBackendBundle.message as backendMessage

internal data class ResolvedImportedScheme(
    val name: String,
    val files: List<String>,
    val usageScanSettings: UsageScanSettingsDto,
    val localeNotes: Map<String, String>,
    val dynamicSourceRules: List<DynamicSourceRuleDto>,
)

internal object SchemeSettingsTransferSupport {
    private const val FORMAT_VERSION = 3
    private val SUPPORTED_FORMAT_VERSIONS = 1..FORMAT_VERSION
    private const val MAX_CONTENT_LENGTH = 1_000_000
    private const val MAX_SCHEMES = 100
    private const val MAX_FILES = 2_000
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = false
        }

    fun export(
        schemes: List<LanguageSchemeDto>,
        projectBasePath: String?,
    ): String {
        require(schemes.isNotEmpty()) { backendMessage("scheme.transfer.export.empty") }
        val root =
            projectBasePath
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?.toAbsolutePath()
                ?.normalize()
        val portable =
            schemes.map { scheme ->
                PortableLanguageSchemeDto(
                    name = scheme.name,
                    files = scheme.files.map { portablePath(it, root) },
                    usageScanSettings =
                        scheme.usageScanSettings.copy(
                            basePath =
                                scheme.usageScanSettings.basePath
                                    .takeIf(String::isNotBlank)
                                    ?.let { portablePath(it, root) }
                                    .orEmpty(),
                        ),
                    localeNotes = LocaleMetadataSupport.normalizeNotes(scheme.localeNotes),
                    dynamicSourceRules =
                        scheme.dynamicSourceRules.map { rule ->
                            rule.copy(filePath = portablePath(rule.filePath, root))
                        },
                )
            }
        return json.encodeToString(SchemeSettingsTransferDto(FORMAT_VERSION, portable)) + "\n"
    }

    fun preview(
        content: String,
        projectBasePath: String?,
    ): SchemeImportPreviewDto {
        val transfer = decode(content)
        val root = requireProjectRoot(projectBasePath)
        return SchemeImportPreviewDto(
            formatVersion = transfer.formatVersion,
            basePath = root.toString(),
            schemes =
                transfer.schemes.map { scheme ->
                    SchemeImportItemPreviewDto(
                        name = scheme.name,
                        files = scheme.files.map { configured -> inspect(configured, root) },
                        dynamicSourceFiles =
                            scheme.dynamicSourceRules
                                .map { it.filePath }
                                .distinct()
                                .map { configured -> inspectSource(configured, root) },
                    )
                },
        )
    }

    fun resolve(
        content: String,
        projectBasePath: String?,
    ): List<ResolvedImportedScheme> {
        val transfer = decode(content)
        val root = requireProjectRoot(projectBasePath)
        return transfer.schemes.map { scheme ->
            val files =
                scheme.files
                    .map { configured ->
                        val resolved = resolveConfiguredPath(configured, root)
                        SafeLanguageFileAccess.validate(resolved.toString()).toString()
                    }.distinct()
            require(files.isNotEmpty()) { backendMessage("scheme.files.required") }
            val basePath =
                scheme.usageScanSettings.basePath
                    .takeIf(String::isNotBlank)
                    ?.let { configured ->
                        SafeLanguageFileAccess.validateDirectory(resolveConfiguredPath(configured, root).toString()).toString()
                    }.orEmpty()
            val dynamicRoot = basePath.takeIf(String::isNotBlank)?.let(Path::of) ?: root
            val dynamicRules =
                scheme.dynamicSourceRules.map { rule ->
                    rule.copy(filePath = resolveConfiguredPath(rule.filePath, root).toString())
                }.let { DynamicSourceSupport.normalizeRules(it, dynamicRoot) }
            ResolvedImportedScheme(
                name = scheme.name.trim(),
                files = files,
                usageScanSettings = UsageScanSupport.normalize(scheme.usageScanSettings.copy(basePath = basePath)),
                localeNotes = LocaleMetadataSupport.normalizeNotes(scheme.localeNotes),
                dynamicSourceRules = dynamicRules,
            )
        }
    }

    private fun decode(content: String): SchemeSettingsTransferDto {
        require(content.length in 1..MAX_CONTENT_LENGTH && content.none { it == '\u0000' }) {
            backendMessage("scheme.transfer.content.invalid")
        }
        val transfer =
            runCatching { json.decodeFromString<SchemeSettingsTransferDto>(content) }
                .getOrElse { throw IllegalArgumentException(backendMessage("scheme.transfer.json.invalid", safeDetail(it))) }
        require(transfer.formatVersion in SUPPORTED_FORMAT_VERSIONS) { backendMessage("scheme.transfer.version.invalid", transfer.formatVersion) }
        require(transfer.schemes.size in 1..MAX_SCHEMES) { backendMessage("scheme.transfer.scheme.count", MAX_SCHEMES) }
        require(transfer.schemes.sumOf { it.files.size } <= MAX_FILES) { backendMessage("scheme.transfer.file.count", MAX_FILES) }
        transfer.schemes.forEach { scheme ->
            require(scheme.name.trim().length in 1..80 && scheme.name.none(Char::isISOControl)) { backendMessage("scheme.name.length") }
            require(scheme.files.isNotEmpty()) { backendMessage("scheme.files.required") }
            LocaleMetadataSupport.normalizeNotes(scheme.localeNotes)
            require(scheme.dynamicSourceRules.size <= MAX_DYNAMIC_SOURCE_RULES)
        }
        return transfer
    }

    private fun inspect(
        configured: String,
        root: Path,
    ): SchemeImportFilePreviewDto {
        val resolved =
            runCatching { resolveConfiguredPath(configured, root) }.getOrElse { error ->
                return SchemeImportFilePreviewDto(configured, "", false, false, safeDetail(error))
            }
        return runCatching {
            val safePath = SafeLanguageFileAccess.validate(resolved.toString())
            val parsed = LanguageFileCodec.parse(safePath, "scheme-import-preview")
            val errors = parsed.issues.filter { it.severity == IssueSeverity.ERROR }
            SchemeImportFilePreviewDto(
                configuredPath = configured,
                resolvedPath = safePath.toString(),
                available = true,
                recognized = errors.isEmpty(),
                detail = errors.joinToString("; ") { it.message }.take(500),
            )
        }.getOrElse { error ->
            SchemeImportFilePreviewDto(configured, resolved.toString(), false, false, safeDetail(error))
        }
    }

    private fun inspectSource(
        configured: String,
        root: Path,
    ): SchemeImportFilePreviewDto {
        val resolved =
            runCatching { resolveConfiguredPath(configured, root) }.getOrElse { error ->
                return SchemeImportFilePreviewDto(configured, "", false, false, safeDetail(error))
            }
        return runCatching {
            require(java.nio.file.Files.isRegularFile(resolved))
            SchemeImportFilePreviewDto(configured, resolved.toRealPath().toString(), true, true)
        }.getOrElse { error ->
            SchemeImportFilePreviewDto(configured, resolved.toString(), false, false, safeDetail(error))
        }
    }

    private fun resolveConfiguredPath(
        configured: String,
        root: Path,
    ): Path {
        require(configured.isNotBlank() && configured.length <= 4096 && configured.none(Char::isISOControl)) {
            backendMessage("path.empty")
        }
        val lower = configured.lowercase()
        require("://" !in lower && !lower.startsWith("ldap:") && !lower.startsWith("file:")) { backendMessage("path.uri") }
        val rawPath = Path.of(configured)
        if (rawPath.isAbsolute) return rawPath.toAbsolutePath().normalize()
        val resolved = root.resolve(rawPath).normalize()
        require(resolved.startsWith(root)) { backendMessage("usage.exclusion.invalid", configured) }
        return resolved
    }

    private fun portablePath(
        raw: String,
        root: Path?,
    ): String {
        val path = runCatching { Path.of(raw).toAbsolutePath().normalize() }.getOrNull() ?: return raw
        return if (root != null && path.startsWith(root)) {
            root.relativize(path).joinToString("/") { it.toString() }.ifBlank { "." }
        } else {
            path.toString().replace('\\', '/')
        }
    }

    private fun requireProjectRoot(projectBasePath: String?): Path {
        require(!projectBasePath.isNullOrBlank()) { backendMessage("scheme.transfer.project.root.required") }
        return SafeLanguageFileAccess.validateDirectory(projectBasePath).toAbsolutePath().normalize()
    }

    private fun safeDetail(error: Throwable): String =
        (error.message ?: error.javaClass.simpleName)
            .replace(Regex("[\u0000-\u001F\u007F-\u009F]"), "?")
            .take(500)
}
