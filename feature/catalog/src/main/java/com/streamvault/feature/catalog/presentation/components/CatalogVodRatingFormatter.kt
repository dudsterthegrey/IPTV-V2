package com.streamvault.feature.catalog.presentation.components

import java.util.Locale

fun formatVodRatingLabel(rating: Float): String {
    // Ratings are out of 10 (IMDb once matched); guessing a 5-point scale turned a 3/10 into "3/5".
    val normalizedRating = rating.coerceIn(0f, 10f)
    val valueText = formatVodRatingValue(normalizedRating)
    return "\u2605 $valueText/10"
}

private fun formatVodRatingValue(rating: Float): String {
    val formatted = String.format(Locale.US, "%.1f", rating)
    return formatted.removeSuffix(".0")
}

