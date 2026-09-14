package io.fixabley.dearby.entities.notice.api

import io.fixabley.dearby.entities.notice.model.*
import org.junit.Assert.*
import org.junit.Test

internal fun noticeFixture(id: String = "fixture", organizationId: String? = "org") = NoticeModel(id, "제목", "검토 요약", organizationId,
    "참여 대상", "참여 조건", NoticeApplication("신청", closesOn = "2026-09-16"),
    NoticeLocation("장소", "offline", "known", emptyList()), emptyList(), emptyList(), emptyList(), "https://example.org/source", emptyList(), emptyList(), null)

class NoticeRepositoryTest {
    @Test fun independentLazyCacheFetchesSuccessOnceAndNeverInventsMissingRecord() {
        var reads = 0
        val repo = NoticeRepository(NoticeSource { reads++; if (it == "fixture") noticeFixture() else null })
        assertEquals(0, reads)
        repeat(2) { assertEquals("fixture", repo.find("fixture")!!.id) }
        assertEquals(1, reads)
        assertNull(repo.find(null))
        repeat(2) { assertNull(repo.find("missing")) }
        assertEquals(3, reads)
    }
    @Test fun replacementInvalidatesRecordAndPublishesRevision() {
        val repo = NoticeRepository(InMemoryNoticeSource(listOf(noticeFixture())))
        repo.find("fixture")
        repo.replaceSource(InMemoryNoticeSource(listOf(noticeFixture().copy(title = "새 제목"))))
        assertEquals(1, repo.revision)
        assertEquals("새 제목", repo.find("fixture")!!.title)
    }
    @Test fun noticeStoresOrganizationReferencesWithoutOrganizationModelsOrPaths() {
        val fields = NoticeModel::class.java.declaredFields
        assertFalse(fields.any { it.name in setOf("organizationPath", "organizations") || it.type.simpleName == "OrganizationModel" })
        assertEquals("org", noticeFixture().organizationId)
    }
}
