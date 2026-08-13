package com.example.personalvault.util

/**
 * Shared pastel palette. Used both for folder colors and for the app's background
 * accent-color picker in Settings, so the two stay visually consistent. Refined to the
 * "Warm Security" design system's lower-saturation, consistent-lightness pastel set —
 * three of these (peach, mint, lavender) are the exact values from that design; the rest
 * were matched to the same lightness/saturation family so all eight mix well together.
 */
val PastelPalette = listOf(
    "#FFAFA3", "#FFD3B6", "#FFF0A8", "#A8E6CF",
    "#A0E7E5", "#A8D8EA", "#D4A5FF", "#FFC4E1"
)

/** A slightly deeper shade of each [PastelPalette] color, used for the small "folder tab"
 *  accent patch in the top corner of folder cards. */
val PastelPaletteDark = listOf(
    "#FF8A7A", "#FFBB93", "#F0DC6E", "#79DCB6",
    "#6ED4D1", "#7EC0DC", "#B76EFF", "#FF97C9"
)
