package cg.creamgod45

import cg.creamgod45.localization.UsageLocationDto
import cg.creamgod45.localization.UsageLocationPageDto

internal object UsageLocationPagingSupport {
    fun page(
        locations: List<UsageLocationDto>,
        allowedEntryIds: Set<String>,
        requestedPage: Int,
        pageSize: Int,
        truncated: Boolean,
    ): UsageLocationPageDto {
        val uniqueLocations = linkedMapOf<Pair<String, Int>, UsageLocationDto>()
        locations.forEach { location ->
            if (location.entryId !in allowedEntryIds) return@forEach
            val identity = location.filePath to location.offset
            val existing = uniqueLocations[identity]
            if (existing == null ||
                (existing.line <= 0 && location.line > 0) ||
                (existing.line == location.line && location.occurrenceCount > existing.occurrenceCount)
            ) {
                uniqueLocations[identity] = location
            }
        }
        val grouped = uniqueLocations.values.sortedWith(compareBy(UsageLocationDto::filePath, UsageLocationDto::offset))
        val pageCount = maxOf(1, (grouped.size + pageSize - 1) / pageSize)
        val page = requestedPage.coerceIn(0, pageCount - 1)
        val from = (page * pageSize).coerceAtMost(grouped.size)
        return UsageLocationPageDto(
            items = grouped.subList(from, (from + pageSize).coerceAtMost(grouped.size)),
            page = page,
            pageCount = pageCount,
            totalItems = grouped.size,
            truncated = truncated,
        )
    }
}
