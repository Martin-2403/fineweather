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
    /** A dash or a loading mark instead of a value, drawn dimmed. */
    val placeholder: Boolean = false,
    /** Read out instead of [text] when the text alone says nothing, like a loading mark. */
    val description: String? = null,
)
