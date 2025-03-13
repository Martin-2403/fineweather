package com.example.fineweather.utils

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import android.location.Geocoder.GeocodeListener
import android.os.Build
import androidx.annotation.RequiresApi
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object GeocoderUtil {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun getCoordinates(context: Context, locationName: String): Pair<Double, Double>? {
        return suspendCoroutine { continuation ->
            val geocoder = Geocoder(context, Locale.getDefault())
            geocoder.getFromLocationName(locationName, 1, object : GeocodeListener {
                override fun onGeocode(addresses: MutableList<android.location.Address>) {
                    if (addresses.isNotEmpty()) {
                        continuation.resume(Pair(addresses[0].latitude, addresses[0].longitude))
                    } else {
                        continuation.resume(null)
                    }
                }

                override fun onError(errorMessage: String?) {
                    continuation.resume(null)
                }
            })
        }
    }
}
