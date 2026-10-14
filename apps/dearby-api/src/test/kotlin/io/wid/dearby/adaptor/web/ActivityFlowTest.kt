package io.wid.dearby.adaptor.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// V5가 넣은 Let'Swift 활동과 테스트가 만든 활동으로 공개 조회·검색·페이지를 확인한다
@SpringBootTest
@AutoConfigureMockMvc
class ActivityFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    private val json = JsonMapper.builder().build()

    private fun search(query: String): JsonNode =
        json.readTree(mvc.get("/v1/activities?$query").andExpect { status { isOk() } }.andReturn().response.contentAsString)

    // Let'Swift 프로그램 아래에 이름이 다른 활동을 만든다. 모든 활동은 활동 기간 일정(slot ACTIVITY)을 하나 가진다
    private fun createActivity(name: String): Long {
        val id = jdbc.sql(
            """
            insert into activities (program_id, name, description, participation_type, recruitment_status, official_url,
                                    freshness, source_note, created_at, updated_at)
            select id, ?, '', 'SELECTION', 'UNKNOWN', 'https://example.com', 'STALE', '', localtimestamp, localtimestamp
            from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'
            """
        ).param(name).update().let {
            jdbc.sql("select id from activities where name = ?").param(name).query(Long::class.java).single()
        }
        jdbc.sql(
            """
            insert into schedules (activity_id, slot, title, kind, status, date_label, sequence, created_at, updated_at)
            values (?, 'ACTIVITY', ?, 'EVENT', 'CONFIRMED', '미정', 0, localtimestamp, localtimestamp)
            """
        ).params(id, name).update()
        return id
    }

    @Test
    fun `로그인 없이 활동을 소속 기관·프로그램과 일정까지 한 번에 받는다`() {
        val item = search("q=개발 환경의 변화")["items"].single()

        assertEquals("Let'Swift 2026 · 개발 환경의 변화 워크숍", item["name"].asString())
        assertEquals("Let’Swift 준비위원회", item["organization"]["name"].asString())
        assertEquals("Let’Swift", item["program"]["name"].asString())
        assertEquals("https://letswift.kr/2026/", item["officialUrl"].asString())
        // 관리자만 보는 정보는 내보내지 않는다
        assertEquals(setOf("id", "name", "description", "organization", "program", "officialUrl", "imageUrl", "application", "activitySchedule", "schedules"), item.propertyNames().toSet())
        // 시각은 일정의 시간대로 표기한다(같은 순간)
        assertEquals("2026-10-08T00:00:00+09:00", item["application"]["endAt"].asString())
        assertEquals("2026-10-08", item["activitySchedule"]["dateLabel"].asString())
        assertEquals("Asia/Seoul", item["schedules"].single()["timeZone"].asString())
    }

    @Test
    fun `검색어는 대소문자를 가리지 않고 퍼센트 기호는 글자 그대로 찾는다`() {
        assertEquals(1, search("q=let'swift")["items"].size())
        assertEquals(0, search("q=%25")["items"].size())
    }

    @Test
    fun `오타나 띄어쓰기가 달라도 비슷한 활동을 찾고, 관계없는 말로는 찾지 않는다`() {
        assertEquals(1, search("q=워크샵")["items"].size())
        assertEquals(1, search("q=개발환경")["items"].size())
        assertEquals(0, search("q=디자인 컨퍼런스")["items"].size())
    }

    @Test
    fun `cursor로 다음 페이지를 이어 받는다`() {
        val first = createActivity("페이지 확인 A")
        val second = createActivity("페이지 확인 B")

        val page1 = search("q=페이지 확인&size=1")
        assertEquals(first, page1["items"].single()["id"].asLong())
        val page2 = search("q=페이지 확인&size=1&cursor=${page1["nextCursor"].asLong()}")
        assertEquals(second, page2["items"].single()["id"].asLong())
        assertNull(page2["nextCursor"].takeUnless { it.isNull })
    }

    @Test
    fun `기관으로 좁히고, 없는 기관이면 빈 목록이다`() {
        val organizationId = search("q=개발 환경의 변화")["items"].single()["organization"]["id"].asLong()
        assertEquals(1, search("q=개발 환경의 변화&organizationId=$organizationId")["items"].size())
        assertEquals(0, search("organizationId=-1")["items"].size())
    }

    @Test
    fun `id로 활동 하나를 목록 항목과 같은 형태로 받고, 없으면 404`() {
        val listed = search("q=개발 환경의 변화")["items"].single()
        val id = listed["id"].asLong()

        val item = json.readTree(mvc.get("/v1/activities/$id").andExpect { status { isOk() } }.andReturn().response.contentAsString)
        assertEquals(listed, item)
        mvc.get("/v1/activities/999999").andExpect { status { isNotFound() } }
        mvc.get("/v1/activities/abc").andExpect { status { isBadRequest() } }
    }

    @Test
    fun `size가 범위를 벗어나면 400`() {
        mvc.get("/v1/activities?size=0").andExpect { status { isBadRequest() } }
        mvc.get("/v1/activities?size=101").andExpect { status { isBadRequest() } }
    }
}
