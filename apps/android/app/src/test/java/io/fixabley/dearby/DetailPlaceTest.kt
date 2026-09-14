package io.fixabley.dearby

import io.fixabley.dearby.entities.notice.model.NoticeVenue
import io.fixabley.dearby.pages.noticedetail.model.*
import org.junit.Assert.*
import org.junit.Test

class DetailPlaceTest {
    private fun venue(name: String, address: String? = null) = NoticeVenue(null, name, address, null)
    @Test fun floorRoomAndAddressAreSeparateWithoutLosingOriginal() {
        val floor = detailPlace(venue("중앙도서관 세미나실(5층)", "충북 청주시 서원구 충대로 1"))
        assertEquals("중앙도서관 세미나실", floor.name)
        assertEquals("5층\n충북 청주시 서원구 충대로 1", floor.detail)
        assertTrue(floor.description.contains("세미나실(5층)"))
        assertEquals("1호실·6호실", detailPlace(venue("중앙도서관 상담실 1호실·6호실")).detail)
        assertEquals("본관(N16-1)", detailPlace(venue("본관(N16-1) 459호")).name)
        assertEquals("강당(변경 가능)", detailPlace(venue("강당(변경 가능)")).name)
    }
    @Test fun onlyCompleteDuplicateSummaryIsRemoved() {
        val venues = listOf(venue("충북대학교 중앙도서관 세미나실(5층)"))
        assertNull(locationNote("중앙도서관 세미나실(5층)", venues))
        assertEquals("세미나실 · 장소 변경 가능", locationNote("세미나실 · 장소 변경 가능", venues))
        assertEquals("상세 장소 확인 필요", locationNote("상세 장소 확인 필요", emptyList()))
    }
}
