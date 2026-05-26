// Copyright (c) 2026 Alden Lougee. All rights reserved.
// Proprietary and confidential. Unauthorized copying, modification,
// distribution, or derivative use is prohibited.

package com.Android.tonelayer.features.shared

import androidx.compose.ui.graphics.Color

data class FeatureColors(
    val primary: Color,
    val surface: Color,
    val soft: Color,
    val outline: Color
)

val ToneLayerBlue = FeatureColors(
    primary = Color(0xFF2563EB),
    surface = Color(0xFFEFF6FF),
    soft = Color(0xFFDBEAFE),
    outline = Color(0xFF93C5FD)
)

val ClarityGreen = FeatureColors(
    primary = Color(0xFF059669),
    surface = Color(0xFFECFDF5),
    soft = Color(0xFFD1FAE5),
    outline = Color(0xFF6EE7B7)
)

val NeutralGray = FeatureColors(
    primary = Color(0xFF52525B),
    surface = Color(0xFFF4F4F5),
    soft = Color(0xFFE4E4E7),
    outline = Color(0xFFD4D4D8)
)
