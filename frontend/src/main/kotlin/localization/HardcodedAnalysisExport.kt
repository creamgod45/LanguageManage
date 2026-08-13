package cg.creamgod45.localization.ui

import cg.creamgod45.localization.HardcodedTextCandidateDto
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

internal object HardcodedAnalysisExport {
    private const val MAX_EXPORT_BYTES = 100L * 1024 * 1024

    fun csv(items: List<HardcodedTextCandidateDto>): String =
        buildString {
            append("Value,File path,Line,Col\r\n")
            items.forEach { item ->
                append(csvCell(item.text)).append(',')
                append(csvCell(item.filePath)).append(',')
                append(item.line).append(',').append(item.column).append("\r\n")
            }
        }

    fun write(
        path: Path,
        content: String,
    ) {
        val target = path.toAbsolutePath().normalize()
        require(target.fileName.toString().endsWith(".csv", true))
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_EXPORT_BYTES)
        target.parent?.let(Files::createDirectories)
        val temp = Files.createTempFile(target.parent, ".language-manager-analysis-", ".tmp")
        try {
            Files.write(temp, bytes)
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
}
