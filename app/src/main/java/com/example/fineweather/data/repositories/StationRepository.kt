package com.example.fineweather.data.repositories

import com.example.fineweather.data.models.StationData
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

private const val PATH =
    "java/com/example/fineweather/data/mapping/KL_Tageswerte_Beschreibung_Stationen.json"

class StationRepository {
    private val stationsList: List<StationData> = try {
        val fileContent = File(PATH).readText()
        Json.decodeFromString(ListSerializer(StationData.serializer()), fileContent)
    } catch (e: Exception) {
        emptyList()
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
                Pair(it.geoLaenge, it.geoBreite)
            )
        }
    }
}
