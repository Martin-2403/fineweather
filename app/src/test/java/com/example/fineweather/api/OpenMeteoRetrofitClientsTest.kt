package com.example.fineweather.api

import org.junit.Assert.assertNotNull
import org.junit.Test

class OpenMeteoRetrofitClientsTest {

    @Test
    fun retrofitClients_initializeServices() {
        assertNotNull(OpenMeteoRetrofitClients.forecastApi)
        assertNotNull(OpenMeteoRetrofitClients.archiveApi)
        assertNotNull(OpenMeteoRetrofitClients.geocodingApi)
    }
}
