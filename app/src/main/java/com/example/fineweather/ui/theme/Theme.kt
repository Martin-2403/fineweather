package com.example.fineweather.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = MistBlue,
    secondary = TealBlue,
    tertiary = HazyBlue,
    background = DeepTeal,
    surface = DeepTeal,
    surfaceVariant = TealInk,
    onPrimary = DeepTeal,
    onSecondary = TealInk,
    onTertiary = LightTeal,
    onBackground = HazyBlue,
    onSurface = HazyBlue,
    onSurfaceVariant = HazyBlue,
)

private val LightColorScheme = lightColorScheme(
    primary = DeepTeal,
    secondary = TealInk,
    tertiary = MistBlue,
    background = HazyBlue, //Background color
    surface = HazyBlue, //Background color header
    surfaceVariant = DarkHazyBlue,
    onPrimary = MistBlue,
    onSecondary = TealInk,
    onTertiary = TealInk,
    onBackground = TealInk,
    onSurface = TealInk,
    onSurfaceVariant = TealInk,
)

@Composable
fun FineWeatherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
}
