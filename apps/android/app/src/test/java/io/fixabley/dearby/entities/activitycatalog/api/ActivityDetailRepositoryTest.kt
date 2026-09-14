package io.fixabley.dearby.entities.activitycatalog.api

import io.fixabley.dearby.entities.activitycatalog.model.*
import org.junit.Assert.*
import org.junit.Test

class ActivityDetailRepositoryTest {
    private val notice = Notice("activity", "제목", "검토 요약", "selected", "참여 대상", "참여 조건",
        ActivityApplication("신청 안내", methods = listOf("email")),
        ActivityLocation("온라인 예선 / 결선", "mixed", "partial", listOf(ActivityVenue("final", "결선 장소", null, null))),
        listOf(ActivityPhase("preliminary", startsOn = "2026-10-14", mode = "online"), ActivityPhase("final", startsOn = "2026-11-04", mode = "offline")),
        listOf("혜택"), listOf("확인 필요"), "https://example.org/source", listOf("competition"),
        listOf(NoticeContext("parent", "event_context"), NoticeContext("unknown", "co_operator")), 2,
        organizationLinks = listOf(NoticeContext("operator", "operator")),
        sources = listOf(ActivitySource("source", "https://example.org/source", "web_page", "checked", "public", "검토")),
        evidence = listOf(ActivityEvidence("source", "신청 방법", "application", "https://example.org/source")))
    private val records = listOf(Organization("selected", "선택 조직", "parent"), Organization("parent", "상위", null),
        Organization("child", "선택 조직의 자식", "selected"), Organization("operator", "운영 조직", null))

    @Test fun twoDetailOpensReuseOneEmptyCacheIncludingSharedContextParent() {
        val fetches = mutableListOf<String>()
        var seeds = 0
        val catalog = ActivityCatalog("snapshot", records, listOf(notice))
        val repo = ActivityDetailRepository(CatalogProvider { catalog }) { rows ->
            seeds++
            OrganizationSource { id -> fetches.add(id); rows.find { it.id == id } }
        }
        repo.load()
        assertEquals(1, seeds)
        assertTrue(fetches.isEmpty())
        val first = repo.detail(notice.id)!!
        val second = repo.detail(notice.id)!!
        assertEquals(first, second)
        assertEquals(1, fetches.count { it == "selected" })
        assertEquals(1, fetches.count { it == "parent" })
        assertEquals(1, fetches.count { it == "operator" })
        assertEquals(2, fetches.count { it == "unknown" })
        assertFalse(fetches.contains("child"))
        repo.load() // Identical snapshot identity does not clear the warm cache.
        assertEquals(1, seeds)
        assertNull(repo.detail("missing activity"))
    }

    @Test fun detailKeepsOnlyRelevantReadPathAndExplicitRolesWithAllDisplayData() {
        val repo = ActivityDetailRepository(CatalogProvider { ActivityCatalog("s", records, listOf(notice)) })
        repo.load()
        val detail = repo.detail(notice.id)!!
        assertEquals("selected", detail.organizationId) // May itself have a child; never force a global leaf.
        assertEquals(listOf("parent", "selected"), detail.organizationPath.map { it.id })
        assertEquals("event_context", detail.contexts.first().role)
        assertEquals("행사 관련 기관", detail.contexts.first().label)
        assertEquals("parent", detail.contexts.first().organizationId)
        assertNull(detail.contexts.last().name)
        assertEquals("operator", detail.relatedOrganizations.single().role)
        assertEquals("운영 조직", detail.relatedOrganizations.single().name)
        assertEquals(notice.summary, detail.aiDescription)
        assertEquals("reviewed_sample_summary", detail.descriptionProvenance)
        assertEquals(notice.audience, detail.targetUser)
        assertEquals(notice.eligibility, detail.participationCondition)
        assertEquals(notice.application, detail.applicationInformation)
        assertEquals(listOf("email"), detail.applicationInformation.methods)
        assertEquals(notice.sources, detail.sources)
        assertEquals(notice.evidence, detail.evidence)
        assertEquals(notice.schedule.map { it.summary }, detail.schedules.map { it.period.summary })
        assertTrue(detail.schedules.first().locations.isEmpty())
        assertEquals(listOf("결선 장소"), detail.schedules.last().locations.map { it.name })
        assertFalse(Notice::class.java.declaredFields.any { it.name in setOf("organizations", "organizationPath") })
        assertFalse(ActivityDetail::class.java.declaredFields.any { it.type == ActivityCatalog::class.java || it.type == Notice::class.java })
    }

    @Test fun replacementChangesNamesAndParentsForDetailAndCatalogHelpersTogether() {
        val repo = ActivityDetailRepository(CatalogProvider { ActivityCatalog("s", records, listOf(notice)) })
        repo.load()
        repo.detail(notice.id)
        val replacement = ActivityCatalog("same timestamp", listOf(Organization("selected", "새 이름", "new"), Organization("new", "새 부모", null)), listOf(notice))
        repo.replaceSnapshot(replacement)
        val detail = repo.detail(notice.id)!!
        assertEquals(listOf("새 부모", "새 이름"), detail.organizationPath.map { it.name })
        assertEquals(replacement.organizationPath("selected"), detail.organizationPath)
        assertNull(detail.contexts.first().name)
        assertEquals("selected", notice.organizationId)
    }
}
