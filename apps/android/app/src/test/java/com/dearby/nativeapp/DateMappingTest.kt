package com.dearby.nativeapp

import com.dearby.nativeapp.app.providers.receivedDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class DateMappingTest {
    @Test fun utcMidnightBoundaryUsesDeviceZone() {
        assertEquals("2026-09-27", receivedDate("2026-09-26T21:00:00Z", ZoneId.of("Asia/Seoul")))
        assertEquals("2026-09-26", receivedDate("2026-09-26T21:00:00Z", ZoneId.of("UTC")))
    }
    @Test fun malformedTimestampIsNotPresentedAsARealDate() { assertEquals("날짜 확인 필요", receivedDate("unverified")) }
}
