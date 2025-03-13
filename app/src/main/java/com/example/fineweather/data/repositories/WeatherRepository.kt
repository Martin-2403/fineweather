import com.example.fineweather.api.OpenMeteoApiService
import com.example.fineweather.api.RetrofitClient
import com.example.fineweather.data.models.ForecastData

class WeatherRepository {

    // Creating an instance of OpenMeteoApiService through RetrofitClient
    private val openMeteoApi: OpenMeteoApiService by lazy {
        RetrofitClient.openMeteoApi // RetrofitClient is used here to initialize the API service
    }

    suspend fun getWeatherForecast(latitude: Double, longitude: Double): ForecastData {
        return openMeteoApi.getForecast(latitude, longitude)
    }
}
