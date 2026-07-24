package cg.creamgod45

import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.LocalizationStateDto
import cg.creamgod45.localization.UsageLocationDto
import cg.creamgod45.localization.UsageScanSettingsDto
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.openjdk.jol.info.GraphLayout

class LocalizationMemoryFootprintTest {
    private val temp = Files.createTempDirectory("language-manager-memory-test")

    @AfterTest
    fun cleanup() {
        temp.toFile().deleteRecursively()
    }

    @Test
    fun `twelve real language files keep usage locations out of frontend state`() {
        val locales = listOf("en", "zh_TW", "zh_CN", "ja", "ko", "es", "th", "fr", "de", "it", "pt_BR", "id")
        val keysPerLocale = 8_000
        val languageFiles =
            locales.map { locale ->
                temp.resolve("lang/$locale.json").apply {
                    parent.createDirectories()
                    writeText(
                        buildString {
                            append("{\n")
                            repeat(keysPerLocale) { index ->
                                if (index > 0) append(",\n")
                                append("  \"key.$index\": \"Translation value $index for locale $locale with realistic text\"")
                            }
                            append("\n}\n")
                        },
                    )
                }
            }
        val entries =
            languageFiles.flatMap { path ->
                val parsed = LanguageFileCodec.parse(path, "memory-scheme")
                parsed.values.map { (key, value) ->
                    LanguageEntryDto(
                        id = "${parsed.locale}:$key",
                        schemeId = "memory-scheme",
                        filePath = path.toString(),
                        locale = parsed.locale,
                        namespace = parsed.namespace,
                        key = key,
                        value = value,
                    )
                }
            }
        temp.resolve("src/Usages.php").apply {
            parent.createDirectories()
            writeText(
                buildString {
                    repeat(keysPerLocale) { index -> append("tr(\"key.$index\");\n") }
                },
            )
        }
        val scan =
            UsageScanSupport.scan(
                root = temp,
                entries = entries,
                languageFiles = languageFiles.map { it.toString() },
                settings =
                    UsageScanSettingsDto(
                        basePath = temp.toString(),
                        regexPatterns = listOf("""tr\("(?<key>[^"]+)"\)"""),
                        excludedDirectories = emptyList(),
                    ),
            )
        val countedEntries = entries.map { it.copy(usageCount = scan.counts[it.id] ?: 0) }
        val optimizedFrontendState = LocalizationStateDto(entries = countedEntries)
        val legacyFrontendState = LegacyFrontendState(optimizedFrontendState, scan.locations)
        val optimizedFrontendBytes = GraphLayout.parseInstance(optimizedFrontendState).totalSize()
        val legacyFrontendBytes = GraphLayout.parseInstance(legacyFrontendState).totalSize()
        val backendRetainedBytes = GraphLayout.parseInstance(BackendRetainedState(countedEntries, scan.locations)).totalSize()
        val savedFrontendBytes = legacyFrontendBytes - optimizedFrontendBytes
        val optimizedRpcBytes = Json.encodeToString(optimizedFrontendState).toByteArray().size
        val legacyRpcBytes = Json.encodeToString(legacyFrontendState).toByteArray().size
        val savedRpcBytes = legacyRpcBytes - optimizedRpcBytes

        assertEquals(locales.size * keysPerLocale, countedEntries.size)
        assertEquals(locales.size * keysPerLocale, scan.locations.size)
        assertTrue(savedFrontendBytes > 0)
        assertTrue(optimizedFrontendBytes < legacyFrontendBytes)
        assertTrue(savedRpcBytes > 0)

        val report =
            """
            locales=${locales.size}
            keys_per_locale=$keysPerLocale
            entries=${countedEntries.size}
            usage_locations=${scan.locations.size}
            language_file_bytes=${languageFiles.sumOf(Files::size)}
            optimized_frontend_retained_bytes=$optimizedFrontendBytes
            legacy_frontend_retained_bytes=$legacyFrontendBytes
            frontend_retained_bytes_saved=$savedFrontendBytes
            optimized_rpc_payload_bytes=$optimizedRpcBytes
            legacy_rpc_payload_bytes=$legacyRpcBytes
            rpc_payload_bytes_saved=$savedRpcBytes
            backend_retained_bytes=$backendRetainedBytes
            """.trimIndent()
        val reportFile =
            PathForTest.projectBuildDirectory()
                .resolve("reports/language-manager-memory/12-language.properties")
                .apply {
                    parent.createDirectories()
                    writeText(report + "\n")
                }
        println("LANGUAGE_MANAGER_MEMORY_REPORT=$reportFile")
        println(report)
    }

    @Serializable
    private data class LegacyFrontendState(
        val state: LocalizationStateDto,
        val usageLocations: List<UsageLocationDto>,
    )

    private data class BackendRetainedState(
        val entries: List<LanguageEntryDto>,
        val usageLocations: List<UsageLocationDto>,
    )
}

private object PathForTest {
    fun projectBuildDirectory() =
        java.nio.file.Path
            .of(System.getProperty("user.dir"))
            .resolve("build")
}
