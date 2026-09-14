package io.fixabley.dearby

import io.fixabley.dearby.entities.activitycatalog.api.decodeActivityLocation
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ActivityLocationDecodeTest {
    @Test
    fun legacyVenuesAndEvidenceRemainIntactWithoutCoordinates() {
        val json = JSONObject("""{"summary":"5층 459호","mode":"mixed","status":"partial",
            "venues":[{"phase":"final","name":"검증 장소","address":null},
            {"phase":"event","name":"다른 장소","coordinates":null}],
            "evidence":[{"sourceId":"fixture","locator":"원문"}]}""")
        val original = json.toString()
        val location = decodeActivityLocation(json)
        assertEquals("5층 459호", location.summary)
        assertEquals("mixed", location.mode)
        assertEquals("partial", location.status)
        assertEquals(2, location.venues.size)
        assertEquals("final", location.venues.first().phase)
        assertNull(location.venues.first().address)
        assertTrue(location.venues.all { it.coordinates == null })
        assertEquals(original, json.toString())
    }

    @Test
    fun malformedCoordinatesAreUnknownInsteadOfCoercedOrZeroFilled() {
        val invalid = listOf("{}", "null", "[]", "\"invalid\"", """{"latitude":12}""",
            """{"longitude":20}""", """{"latitude":null,"longitude":20}""",
            """{"latitude":"12","longitude":20}""", """{"latitude":true,"longitude":20}""",
            """{"latitude":90.1,"longitude":0}""", """{"latitude":0,"longitude":180.1}""")
        for (coordinates in invalid) {
            val json = JSONObject("""{"summary":"fixture","venues":[{"coordinates":$coordinates}]}""")
            assertNull(coordinates, decodeActivityLocation(json).venues.single().coordinates)
        }
        // Android's JSON parser rejects nonfinite numeric literals before model decoding.
        // DearbyApp handles provider failure through its existing retry/error state.
        for (value in listOf("1e309", "-1e309")) {
            assertThrows(org.json.JSONException::class.java) {
                JSONObject("""{"summary":"fixture","venues":[{"coordinates":{"latitude":$value,"longitude":0}}]}""")
            }
        }
    }

    @Test
    fun multipleValidVenuesKeepExplicitZeroAndLabelsAndDoNotMutateEvidence() {
        val json = JSONObject("""{"summary":"층·호실 유지","mode":"offline","venues":[
            {"phase":"preliminary","name":"한글 & # 장소","address":"주소","coordinates":{"latitude":0,"longitude":0},
             "coordinateEvidence":[{"sourceId":"fixture","locator":"검증 좌표"}]},
            {"phase":"final","name":"두 번째","coordinates":{"latitude":-90,"longitude":180}}]}""")
        val original = json.toString()
        val venues = decodeActivityLocation(json).venues
        assertEquals(2, venues.size)
        assertEquals(0.0, venues.first().coordinates!!.latitude, 0.0)
        assertEquals(0.0, venues.first().coordinates!!.longitude, 0.0)
        assertEquals("한글 & # 장소", venues.first().name)
        assertEquals("주소", venues.first().address)
        assertEquals(-90.0, venues.last().coordinates!!.latitude, 0.0)
        assertEquals(180.0, venues.last().coordinates!!.longitude, 0.0)
        assertEquals(original, json.toString())
    }
}
