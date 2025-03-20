package com.example.fineweather.data.repositories

import android.content.Context
import android.util.Log
import com.example.fineweather.data.models.StationData
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.pow
import kotlin.math.sqrt

private const val PATH =
    "assets/KL_Tageswerte_Beschreibung_Stationen.json"

class StationRepository (context: Context) {
    private var stationsList: List<StationData> = emptyList()
    private var isInitialized = false

    fun initialize(context: Context) {
        if (!isInitialized) {
            stationsList = loadStationsFromAssets(context.applicationContext)
            isInitialized = true
        }
    }

    private fun loadStationsFromAssets(context: Context): List<StationData> {
        return try {
            val inputStream = context.assets.open("KL_Tageswerte_Beschreibung_Stationen.json")
            val fileContent = inputStream.bufferedReader().use { it.readText() }
            Json.decodeFromString(ListSerializer(StationData.serializer()), fileContent)
        } catch (e: Exception) {
            Log.i("Failed to load stations data", "${e.message}")
            emptyList()
        }
    }

    fun findById(id: String): StationData? = stationsList.find { it.Stations_id == id }

    fun findByName(name: String): List<StationData> =
        stationsList.filter { it.Stationsname.contains(name, ignoreCase = true) }

    fun searchStations(id: String? = null, name: String? = null): List<StationData> =
        stationsList.filter { station ->
            (id == null || station.Stations_id == id) &&
                    (name == null || station.Stationsname.contains(name, ignoreCase = true))
        }

    private fun calculateDistance(
        point1: Pair<Double, Double>,
        point2: Pair<Double, Double>
    ): Double =
        sqrt((point2.first - point1.first).pow(2.0) + (point2.second - point1.second).pow(2.0))

    fun findClosestCoordinate(point: Pair<Double, Double>): StationData? {
        return stationsList.minByOrNull {
            calculateDistance(
                point,
                Pair( it.geoBreite, it.geoLaenge)
            )
        }
    }
}
