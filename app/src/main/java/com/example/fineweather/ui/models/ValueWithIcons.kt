package com.example.fineweather.ui.models

import androidx.compose.ui.graphics.Color

internal data class IconStack(
    val resId: Int,
    val count: Int,
    val tint: Color,
    val contentDescription: String,
)

internal data class ValueWithIcons(
    val text: String,
    val icons: IconStack?,
)
