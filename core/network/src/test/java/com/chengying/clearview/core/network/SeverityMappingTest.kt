package com.chengying.clearview.core.network

import com.chengying.clearview.core.model.SeverityLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class SeverityMappingTest {
    @Test
    fun knownSeverityValuesAreMappedExplicitly() {
        assertEquals(SeverityLevel.LOW, "LOW".toSeverityLevel())
        assertEquals(SeverityLevel.MEDIUM, "medium".toSeverityLevel())
        assertEquals(SeverityLevel.HIGH, " HIGH ".toSeverityLevel())
    }

    @Test
    fun unknownOrAbsentSeverityIsNeverReportedAsLow() {
        listOf(null, "", "invalid", "UNKNOWN", "4", "critical").forEach {
            assertEquals(SeverityLevel.UNKNOWN, it.toSeverityLevel())
        }
    }
}
