package cg.creamgod45

import cg.creamgod45.localization.UsageLocationDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UsageLocationPagingSupportTest {
    @Test
    fun `returns only one page and merges locale copies of the same source occurrence`() {
        val locations =
            buildList {
                repeat(12) { locale ->
                    repeat(205) { index ->
                        add(
                            UsageLocationDto(
                                entryId = "locale-$locale",
                                filePath = "src/File${index / 50}.kt",
                                offset = index * 10,
                                sourceModifiedAtEpochMs = 1,
                                line = if (locale == 3) index + 1 else 0,
                                column = if (locale == 3) 4 else 0,
                                occurrenceCount = 1,
                            ),
                        )
                    }
                }
            }

        val result =
            UsageLocationPagingSupport.page(
                locations,
                (0 until 12).mapTo(hashSetOf()) { "locale-$it" },
                requestedPage = 1,
                pageSize = 100,
                truncated = true,
            )

        assertEquals(205, result.totalItems)
        assertEquals(3, result.pageCount)
        assertEquals(100, result.items.size)
        assertTrue(result.items.all { it.line > 0 })
        assertTrue(result.truncated)
    }

    @Test
    fun `clamps an out of range page`() {
        val result =
            UsageLocationPagingSupport.page(
                listOf(UsageLocationDto("entry", "source.kt", 2, 1, occurrenceCount = 1)),
                setOf("entry"),
                requestedPage = 99,
                pageSize = 100,
                truncated = false,
            )

        assertEquals(0, result.page)
        assertEquals(1, result.items.size)
    }
}
