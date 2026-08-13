package cg.creamgod45.localization.ui

internal fun retainedTranslationPage(
    currentPage: Int,
    previousSchemeId: String?,
    nextSchemeId: String?,
): Int = if (previousSchemeId == nextSchemeId) currentPage.coerceAtLeast(0) else 0
