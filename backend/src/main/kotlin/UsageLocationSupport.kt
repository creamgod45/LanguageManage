package cg.creamgod45

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal object UsageLocationSupport {
    /** Resolves a UTF-16 character offset lazily without loading the entire source file. */
    fun sourceLineColumn(
        path: Path,
        offset: Int,
    ): Pair<Int, Int>? {
        if (offset < 0) return null
        var remaining = offset
        var line = 1
        var column = 1
        val buffer = CharArray(DEFAULT_BUFFER_SIZE)
        Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            while (remaining > 0) {
                val count = reader.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) return null
                repeat(count) { index ->
                    if (buffer[index] == '\n') {
                        line++
                        column = 1
                    } else {
                        column++
                    }
                }
                remaining -= count
            }
        }
        return line to column
    }
}
