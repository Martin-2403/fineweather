package com.example.fineweather.data.models

import org.junit.Assert.assertNotNull
import org.junit.Test

class FineWeatherStorageTest {

    @Test
    fun fineWeatherStorage_canBeConstructed() {
        assertNotNull(FineWeatherStorage())
    }
}
