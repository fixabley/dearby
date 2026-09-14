package io.fixabley.dearby.entities.notice.api

import io.fixabley.dearby.entities.notice.model.*
import org.junit.Assert.*
import org.junit.Test

class NoticeStorageTest {
    @Test fun versionedPayloadRetainsEveryFieldAndRejectsCorruption() {
        val value = noticeFixture().copy(
            applicationInformation = NoticeApplication("신청", "open", "day", "close", "end", "Asia/Seoul", "https://example.org/apply", listOf("방식"), listOf("서류"), listOf("제출 장소")),
            location = NoticeLocation("5층 & 호실", "mixed", "known", listOf(NoticeVenue("final", "이름", "주소", VenueCoordinates(0.0, -45.0)), NoticeVenue(null, null, null, null))),
            schedules = listOf(NoticePhase("final", "start", "day", "end", "last", "Asia/Seoul", "offline", "https://example.org/online")),
            benefits = listOf("혜택"), issues = listOf("이슈"), categoryPath = listOf("career"), edition = 3,
            contexts = listOf(NoticeContext("school", "venue_institution")), organizationLinks = listOf(NoticeContext("operator", "operator")),
            sources = listOf(io.fixabley.dearby.entities.notice.model.NoticeSource("source", "https://example.org", "page", "today", "public", "note")),
            evidence = listOf(NoticeEvidence("source", "문단", "location.venues[0]", "https://example.org")), descriptionProvenance = "reviewed_sample_summary")
        val bytes = NoticeStorageCodec.encode(value)
        assertEquals(value, NoticeStorageCodec.decode(bytes))
        assertArrayEquals(bytes, NoticeStorageCodec.encode(NoticeStorageCodec.decode(bytes)))
        assertThrows(IllegalArgumentException::class.java) { NoticeStorageCodec.decode(bytes.copyOf().also { it[3] = 99 }) }
        assertThrows(Exception::class.java) { NoticeStorageCodec.decode(bytes.copyOf(20)) }
        assertThrows(IllegalArgumentException::class.java) { NoticeStorageCodec.decode(bytes + byteArrayOf(0)) }
    }

    @Test fun l1DiskMockOrderingAndWriteFailureHasNoMemoryPromotion() {
        var stored: NoticeModel? = null; var reads = 0; var fetches = 0; var fail = true
        val value = noticeFixture()
        val disk = object : NoticeDiskStore {
            override fun find(id: String): NoticeModel? { reads++; return stored }
            override fun upsert(value: NoticeModel) { if (fail) error("disk full"); stored = value }
        }
        val source = StoredNoticeSource(disk, NoticeSource { fetches++; value })
        val repository = NoticeRepository(source)
        assertThrows(IllegalStateException::class.java) { repository.find(value.id) }; assertNull(stored)
        fail = false
        assertEquals(value, repository.find(value.id)); assertEquals(value, repository.find(value.id))
        assertEquals(2, reads); assertEquals(2, fetches)
        assertEquals(value, NoticeRepository(source).find(value.id)); assertEquals(3, reads); assertEquals(2, fetches)
    }

    @Test fun missingAndFailuresAreNotSilentlyCached() {
        var calls = 0
        val disk = object : NoticeDiskStore {
            override fun find(id: String): NoticeModel? = null
            override fun upsert(value: NoticeModel) = error("unexpected")
        }
        val missing = NoticeRepository(StoredNoticeSource(disk, NoticeSource { calls++; null }))
        repeat(2) { assertNull(missing.find("missing")) }; assertEquals(2, calls)
        val failed = NoticeRepository(StoredNoticeSource(disk, NoticeSource { error("mock failure") }))
        repeat(2) { assertThrows(IllegalStateException::class.java) { failed.find("missing") } }
        val corrupt = object : NoticeDiskStore {
            override fun find(id: String): NoticeModel? = error("corrupt payload")
            override fun upsert(value: NoticeModel) = error("unexpected")
        }
        assertThrows(IllegalStateException::class.java) { StoredNoticeSource(corrupt, NoticeSource { calls++; null }).find("missing") }
        assertEquals(2, calls)
    }
    @Test fun mismatchedIdsNeverWriteOrReturnSuccess() {
        val value = noticeFixture()
        var writes = 0
        val disk = object : NoticeDiskStore {
            override fun find(id: String): NoticeModel? = null
            override fun upsert(value: NoticeModel) { writes++ }
        }
        assertThrows(IllegalArgumentException::class.java) { StoredNoticeSource(disk, NoticeSource { value }).find("requested") }
        assertEquals(0, writes)
        val corrupt = object : NoticeDiskStore {
            override fun find(id: String): NoticeModel? = value
            override fun upsert(value: NoticeModel) { writes++ }
        }
        assertThrows(IllegalArgumentException::class.java) { StoredNoticeSource(corrupt, NoticeSource { error("must not fetch") }).find("requested") }
    }
}
