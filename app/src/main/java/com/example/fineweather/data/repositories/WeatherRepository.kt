import com.example.fineweather.api.OpenMeteoApiService
import com.example.fineweather.data.models.ForecastData

class WeatherRepository(private val openMeteoApi: OpenMeteoApiService) {
    suspend fun getWeatherForecast(latitude: Double, longitude: Double): ForecastData {
        return openMeteoApi.getForecast(latitude, longitude)
    }
}
