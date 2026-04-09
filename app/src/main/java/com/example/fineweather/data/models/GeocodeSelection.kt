package com.example.fineweather.data.models

data class GeocodeSelection(
    val primary: GeoPlace,
    val candidates: List<GeoPlace>,
)
