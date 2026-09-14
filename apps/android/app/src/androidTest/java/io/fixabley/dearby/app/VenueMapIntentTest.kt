package io.fixabley.dearby.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import io.fixabley.dearby.entities.activitycatalog.model.ActivityVenue
import io.fixabley.dearby.entities.activitycatalog.model.VenueCoordinates
import org.junit.Assert.*
import org.junit.Test

class VenueMapIntentTest {
    private val venue = ActivityVenue("event", "한글 & # 장소(5층)", null, VenueCoordinates(12.5, -45.25))

    @Test
    fun unpinnedViewIntentEncodesLabelAndExactCoordinates() {
        val captured = mutableListOf<Intent>()
        openVenueMap(venue, captured::add) { fail("Handler is available") }
        val intent = captured.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertNull(intent.`package`)
        assertNull(intent.component)
        assertEquals("geo", intent.data!!.scheme)
        val uri = intent.data.toString()
        assertTrue(uri.startsWith("geo:12.5,-45.25?q="))
        assertTrue(uri.contains("%26"))
        assertTrue(uri.contains("%23"))
        assertEquals("12.5,-45.25(한글 & # 장소(5층))", Uri.decode(uri.substringAfter("?q=")))
        assertEquals(0, intent.flags)
    }

    @Test
    fun missingOrBlockedHandlerGivesFeedbackWithoutCrashingOrLaunchingAnotherRequest() {
        var attempts = 0
        var feedback = 0
        for (error in listOf(ActivityNotFoundException(), SecurityException())) {
            openVenueMap(venue, { attempts++; throw error }, { feedback++ })
        }
        assertEquals(2, attempts)
        assertEquals(2, feedback)
    }

    @Test
    fun missingOrInvalidCoordinatesNeverConstructOrLaunchAnIntent() {
        for (coordinates in listOf(null, VenueCoordinates(Double.NaN, 0.0), VenueCoordinates(91.0, 0.0))) {
            val invalid = venue.copy(coordinates = coordinates)
            assertNull(venueMapIntent(invalid))
            openVenueMap(invalid, { fail("No launch") }, { fail("No map action") })
        }
        assertTrue(venueMapIntent(venue.copy(coordinates = VenueCoordinates(0.0, 0.0)))!!.data.toString().contains("0.0"))
    }
}
