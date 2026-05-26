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
    primary = Color(0xFF6D4AC8),
    secondary = Color(0xFF059669),
    surface = Color(0xFFF4F0FF),
    soft = Color(0xFFECFDF5),
    outline = Color(0xFFA7F3D0)
)

val ClarityGreen = FeatureColors(
    primary = Color(0xFF059669),
    secondary = Color(0xFF6D4AC8),
    surface = Color(0xFFECFDF5),
    soft = Color(0xFFF4F0FF),
    outline = Color(0xFFC4B5FD)
)

val NeutralGray = FeatureColors(
    primary = Color(0xFF52525B),
    secondary = Color(0xFF71717A),
    surface = Color(0xFFF4F4F5),
    soft = Color(0xFFE4E4E7),
    outline = Color(0xFFD4D4D8)
)
