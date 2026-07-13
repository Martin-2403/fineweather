package com.example.fineweather.data.models

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistoricReferenceTest {

    @Test
    fun displayLabel_classicUsesFixedRange() {
        val label = HistoricReference.CLASSIC.displayLabel(LocalDate.of(2026, 3, 1))
        assertEquals("1961-1990", label.replace('\u2013', '-'))
    }

    @Test
    fun displayLabel_currentUsesLastCompleteDecade() {
        val label = HistoricReference.CURRENT.displayLabel(LocalDate.of(2026, 3, 1))
        assertEquals("1991-2020", label.replace('\u2013', '-'))
    }

    @Test
    fun fromId_resolvesKnownAndUnknownValues() {
        assertEquals(HistoricReference.CURRENT, HistoricReference.fromId("current"))
        assertEquals(HistoricReference.CLASSIC, HistoricReference.fromId("1961_1990"))
        assertEquals(HistoricReference.CLASSIC, HistoricReference.fromId("unknown"))
        assertEquals(HistoricReference.CLASSIC, HistoricReference.fromId(null))
    }

    @Test
    fun latestCompleteDecadeEndYear_handlesDecadeBoundaries() {
        assertEquals(2010, HistoricReference.latestCompleteDecadeEndYear(LocalDate.of(2020, 1, 1)))
        assertEquals(2020, HistoricReference.latestCompleteDecadeEndYear(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun displayLabel_usesDefaultNowParameter() {
        val label = HistoricReference.CLASSIC.displayLabel()
        assertEquals("1961-1990", label.replace('\u2013', '-'))
    }
}
