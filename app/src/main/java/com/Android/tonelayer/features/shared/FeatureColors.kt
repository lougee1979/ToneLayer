// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.tonelayer.features.shared

import androidx.compose.ui.graphics.Color

data class FeatureColors(
    val primary: Color,
    val secondary: Color,
    val surface: Color,
    val soft: Color,
    val outline: Color
)

val ToneLayerBlue = FeatureColors(
    primary   = Color(0xFF1D4ED8),   // royal blue
    secondary = Color(0xFF059669),   // emerald green
    surface   = Color(0xFFEFF6FF),   // light blue surface
    soft      = Color(0xFFECFDF5),   // light green soft
    outline   = Color(0xFF93C5FD)    // sky blue outline
)

val NeutralGray = FeatureColors(
    primary = Color(0xFF52525B),
    secondary = Color(0xFF71717A),
    surface = Color(0xFFF4F4F5),
    soft = Color(0xFFE4E4E7),
    outline = Color(0xFFD4D4D8)
)
