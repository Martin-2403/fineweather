object FineWeatherTheme {
    val nunitoFamily = FontFamily(
        Font(R.font.nunito_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.nunito_italic, FontWeight.Normal, FontStyle.Italic)
    )

    val typography = Typography(
        h1 = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp
        ),
        h2 = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp
        ),
        subtitle1 = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        ),
        body1 = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp
        ),
        body2 = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp
        ),
        button = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        ),
        caption = TextStyle(
            fontFamily = nunitoFamily,
            fontSize = 12.sp
        ),
        overline = TextStyle(
            fontFamily = nunitoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
        )
    )

    val colors = lightColorScheme(
        primary = Color(0xFF6200EE),    // Purple 700
        onPrimary = Color(0xFFFFFFFF),  // White
        primaryContainer = Color(0xFF3700B3),    // Purple 800
        onPrimaryContainer = Color(0xFFFFFFFF),  // White
        secondary = Color(0xFF03DAC6),    // Teal 600
        onSecondary = Color(0xFF000000),  // Black
        secondaryContainer = Color(0xFF018786),    // Teal 700
        onSecondaryContainer = Color(0xFFFFFFFF),  // White
        background = Color(247, 249, 250),  // Your current background
        onBackground = Color(0xFF000000),  // Black
        surface = Color(247, 249, 250),    // Your current surface
        onSurface = Color(0xFF000000),    // Black
        error = Color(0xFFB00020),        // Red
        onError = Color(0xFFFFFFFF),      // White
        errorContainer = Color(0xFFFFDAD4),
        onErrorContainer = Color(0xFF000000)
    )

    @Composable
    fun FineWeatherTheme(
        darkTheme: Boolean = isSystemInDarkTheme(),
        content: @Composable () -> Unit
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme else colors,
            typography = typography,
            content = content
        )
    }
}