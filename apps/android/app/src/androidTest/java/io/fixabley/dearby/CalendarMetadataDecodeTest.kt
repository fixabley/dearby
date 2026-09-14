package io.fixabley.dearby

import io.fixabley.dearby.entities.noticecatalog.api.decodeNoticeApplication
import io.fixabley.dearby.entities.noticecatalog.api.decodeNoticePhase
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CalendarMetadataDecodeTest {
    @Test fun optionalFieldsPreserveLegacyAndRichApplicationPayload() {
        val json = JSONObject("""{"summary":"신청 안내","opensAt":null,"closesOn":"2026-09-16",
            "requiredDocuments":["증빙"],"evidence":[{"sourceId":"fixture","locator":"본문"}]}""")
        val original = json.toString()
        val application = decodeNoticeApplication(json)
        assertEquals("신청 안내", application.summary)
        assertNull(application.opensAt)
        assertNull(application.url)
        assertEquals("Asia/Seoul", application.timezone)
        assertEquals("2026-09-16", application.closesOn)
        assertEquals(original, json.toString())
        assertEquals("", decodeNoticeApplication(json.put("opensAt", 123)).opensAt)
        assertEquals("", decodeNoticeApplication(json.put("timezone", JSONObject.NULL)).timezone)
    }

    @Test fun phaseKeepsInclusiveDateTimezoneUrlAndMalformedPresentTypes() {
        val json = JSONObject("""{"phase":"preliminary","startsOn":"2026-10-14","endsOn":"2026-10-16",
            "mode":"online","timezone":"Asia/Seoul","onlineUrl":"https://example.org/live","evidence":[]}""")
        val original = json.toString()
        val phase = decodeNoticePhase(json)
        assertEquals("2026-10-16", phase.endsOn)
        assertEquals("Asia/Seoul", phase.timezone)
        assertEquals("https://example.org/live", phase.onlineUrl)
        assertEquals(original, json.toString())
        assertEquals("", decodeNoticePhase(json.put("startsAt", true)).startsAt)
        assertEquals("", decodeNoticePhase(json.put("endsOn", JSONObject())).endsOn)
    }
}
