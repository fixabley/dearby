package io.fixabley.dearby.entities.notice.model

import org.junit.Assert.*
import org.junit.Test

class VenueCoordinatesTest {
    @Test
    fun finiteInclusiveBoundsAndExplicitZeroAreValid() {
        for ((lat, lon) in listOf(0.0 to 0.0, -90.0 to -180.0, 90.0 to 180.0, 0.0 to 45.0)) {
            assertTrue(VenueCoordinates(lat, lon).isValid)
        }
    }

    @Test
    fun invalidCoordinatesAndAbsenceNeverEnableMaps() {
        for ((lat, lon) in listOf(90.01 to 0.0, 0.0 to -180.01, Double.NaN to 0.0,
            0.0 to Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY to 0.0)) {
            assertFalse(NoticeVenue(null, "fixture", null, VenueCoordinates(lat, lon)).canOpenMap)
        }
        assertFalse(NoticeVenue(null, "fixture", null, null).canOpenMap)
    }
}
