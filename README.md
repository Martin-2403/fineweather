# FineWeather

FineWeather is an Android app that compares recent, forecast, and historic temperature averages for any city using Open-Meteo data.

## Features
- Search by city or place with geocoding, localized results, and alternate matches
- Average temperatures for the last 31 days, current month so far, and next 7 or 14 days
- Historical monthly baseline for the current month (1961-1990 or current 30-year normal)
- Status and trend visuals, plus a warning when current-month data is limited
- Favorites for saving places locally and reopening them from a dedicated tab
- Settings for forecast length, historic reference, search language, and divergence method
- Local caching for geocode and weather data, with settings stored in DataStore

## Data sources
- Open-Meteo Forecast API, Archive API, and Geocoding API (no API key required)

## Tech stack
- Kotlin + Jetpack Compose (Material 3)
- MVVM with ViewModel + StateFlow
- Retrofit + Gson
- Room for caching and favorites
- DataStore Preferences for settings
- Coroutines

## Getting started
1. Open the project in Android Studio.
2. Ensure the Android SDK is installed (compileSdk 36, minSdk 27).
3. Run the `app` configuration on an emulator or device.

### CLI builds
- `./gradlew assembleDebug`
- `./gradlew installDebug`

### Tests
- `./gradlew test`
- `./gradlew connectedAndroidTest`

## Settings
- Forecast length: 7 or 14 days
- Historic reference: 1961-1990 baseline or current 30-year normal ending at the last complete decade
- Search language: English, German, French, Spanish, Italian, Portuguese, Russian, Turkish, or Hindi
- Divergence method: absolute delta or normalized anomaly

## Project structure
- `app/src/main/java/com/example/fineweather/ui` Compose UI and screens
- `app/src/main/java/com/example/fineweather/viewmodels` UI state and business logic
- `app/src/main/java/com/example/fineweather/data` repositories, models, and Room entities
- `app/src/main/java/com/example/fineweather/api` Open-Meteo Retrofit services
