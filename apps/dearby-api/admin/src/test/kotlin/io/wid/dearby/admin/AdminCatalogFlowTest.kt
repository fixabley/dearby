package io.wid.dearby.admin

import io.wid.dearby.TestDatabaseConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MockHttpServletRequestDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import kotlin.test.Test
import kotlin.test.assertEquals

// admin 앱은 io.wid.dearby.admin·auth만 스캔하므로 공유 테스트 DB 설정(스키마 비우고 다시 마이그레이션)을 직접 가져온다
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestDatabaseConfig::class)
class AdminCatalogFlowTest(@Autowired val mvc: MockMvc, @Autowired val jdbc: JdbcClient) {

    private val json = JsonMapper.builder().build()

    private fun MockHttpServletRequestDsl.asAdmin() {
        with(jwt().authorities(SimpleGrantedAuthority("ROLE_ADMIN"), SimpleGrantedAuthority("ROLE_USER")))
    }

    private fun MockHttpServletRequestDsl.body(value: String) {
        contentType = MediaType.APPLICATION_JSON
        content = value
    }

    private fun read(result: org.springframework.test.web.servlet.ResultActionsDsl): JsonNode =
        json.readTree(result.andReturn().response.contentAsString)

    @Test
    fun `토큰이 없으면 401, ADMIN이 아니면 403, ADMIN이면 200`() {
        mvc.get("/v1/admin/organizations").andExpect { status { isUnauthorized() } }
        mvc.get("/v1/admin/organizations") { with(jwt().authorities(SimpleGrantedAuthority("ROLE_USER"))) }
            .andExpect { status { isForbidden() } }
        mvc.get("/v1/admin/organizations") { asAdmin() }.andExpect { status { isOk() } }
        // 관리자 경로가 아닌 곳은 막는다(공개 API는 admin 앱에 없다)
        mvc.get("/v1/activities") { asAdmin() }.andExpect { status { isForbidden() } }
    }

    @Test
    fun `조직과 프로그램을 만들고 수집 설정을 넣으면 검색 결과에 함께 보인다`() {
        jdbc.sql("delete from program_collection_settings").update()
        val organization = read(
            mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"  테스트 조직 "}""") }.andExpect { status { isCreated() } }
        )
        assertEquals("테스트 조직", organization["name"].asString())
        val organizationId = organization["id"].asLong()

        val program = read(
            mvc.post("/v1/admin/programs") {
                asAdmin()
                body("""{"organizationId":$organizationId,"name":"테스트 프로그램","collection":{"officialUrl":"https://example.org/apply","allowedHosts":["Example.org"],"intervalDays":7,"enabled":true}}""")
            }.andExpect { status { isCreated() } }
        )
        assertEquals(listOf("example.org"), program["collection"]["allowedHosts"].values().map { it.asString() })
        assertEquals(true, program["collection"]["officialOnly"].asBoolean())
        assertEquals(7, program["collection"]["intervalDays"].asInt())

        val found = read(mvc.get("/v1/admin/programs?q=테스트 프로그램&organizationId=$organizationId") { asAdmin() })["items"].single()
        assertEquals("테스트 조직", found["organization"]["name"].asString())
        assertEquals(true, found["collection"]["configured"].asBoolean())

        // 설정이 없는 프로그램은 기본값(웹 전체 검색, 매일)으로 보인다
        val letswift = read(mvc.get("/v1/admin/programs?q=Let") { asAdmin() })["items"].single()
        assertEquals(false, letswift["collection"]["configured"].asBoolean())
        assertEquals(false, letswift["collection"]["officialOnly"].asBoolean())
    }

    private fun createOrganization(name: String, parentId: Long? = null): Long = read(
        mvc.post("/v1/admin/organizations") { asAdmin(); body("""{"name":"$name","parentOrganizationId":${parentId ?: "null"}}""") }
            .andExpect { status { isCreated() } }
    )["id"].asLong()

    private fun MockHttpServletRequestDsl.patchBody(value: String) = body(value)

    @Test
    fun `조직도는 최상위와 하위를 나눠 주고, 이름·상위 조직을 바꾸되 순환은 막는다`() {
        val root = createOrganization("트리 최상위")
        val child = createOrganization("트리 하위", root)
        val grandchild = createOrganization("트리 손자", child)

        val roots = read(mvc.get("/v1/admin/organizations?rootsOnly=true&q=트리") { asAdmin() })["items"]
        assertEquals(listOf(root), roots.values().map { it["id"].asLong() })
        assertEquals(1, roots.single()["childCount"].asInt())
        assertEquals(listOf(child), read(mvc.get("/v1/admin/organizations?parentId=$root") { asAdmin() })["items"].values().map { it["id"].asLong() })
        // 검색 결과는 최상위부터의 상위 경로를 함께 준다
        val found = read(mvc.get("/v1/admin/organizations?q=트리 손자") { asAdmin() })["items"].single()
        assertEquals(listOf("트리 최상위", "트리 하위"), found["ancestors"].values().map { it["name"].asString() })
        assertEquals(0, roots.single()["ancestors"].size())

        // 자기 자신·자기 하위 아래로는 못 옮긴다
        mvc.patch("/v1/admin/organizations/$root") { asAdmin(); patchBody("""{"parentOrganizationId":$grandchild}""") }
            .andExpect { status { isBadRequest() } }
        mvc.patch("/v1/admin/organizations/$root") { asAdmin(); patchBody("""{"parentOrganizationId":$root}""") }
            .andExpect { status { isBadRequest() } }
        // 이름만 바꾸면 상위는 그대로, clearParent면 최상위로
        val renamed = read(mvc.patch("/v1/admin/organizations/$child") { asAdmin(); patchBody("""{"name":"트리 하위(수정)"}""") }
            .andExpect { status { isOk() } })
        assertEquals(listOf("트리 하위(수정)", root.toString()), listOf(renamed["name"].asString(), renamed["parentOrganizationId"].asString()))
        val moved = read(mvc.patch("/v1/admin/organizations/$grandchild") { asAdmin(); patchBody("""{"clearParent":true}""") }
            .andExpect { status { isOk() } })
        assertEquals(true, moved["parentOrganizationId"].isNull)
        mvc.patch("/v1/admin/organizations/999999") { asAdmin(); patchBody("""{"name":"x"}""") }.andExpect { status { isNotFound() } }

        // 프로그램은 다른 조직으로 옮기고 이름을 바꿀 수 있다. 조직의 programCount가 따라 바뀐다
        val program = read(mvc.post("/v1/admin/programs") { asAdmin(); body("""{"organizationId":$child,"name":"트리 프로그램"}""") }
            .andExpect { status { isCreated() } })["id"].asLong()
        val updated = read(mvc.patch("/v1/admin/programs/$program") { asAdmin(); patchBody("""{"organizationId":$root,"name":"트리 프로그램(이동)"}""") }
            .andExpect { status { isOk() } })
        assertEquals(listOf(root.toString(), "트리 프로그램(이동)"), listOf(updated["organization"]["id"].asString(), updated["name"].asString()))
        assertEquals(1, read(mvc.get("/v1/admin/organizations?rootsOnly=true&q=트리 최상위") { asAdmin() })["items"].single()["programCount"].asInt())
        mvc.patch("/v1/admin/programs/$program") { asAdmin(); patchBody("""{"organizationId":999999}""") }.andExpect { status { isBadRequest() } }
    }

    @Test
    fun `수집 설정은 https 주소와 호스트 이름만 받는다`() {
        val programId = jdbc.sql("select id from programs where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db'").query(Long::class.java).single()
        val url = "/v1/admin/programs/$programId/collection-settings"
        mvc.put(url) { asAdmin(); body("""{"officialUrl":"http://letswift.kr","allowedHosts":["letswift.kr"],"intervalDays":1,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        mvc.put(url) { asAdmin(); body("""{"allowedHosts":["https://letswift.kr/2026"],"intervalDays":1,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        mvc.put(url) { asAdmin(); body("""{"allowedHosts":["letswift.kr"],"intervalDays":0,"enabled":true}""") }
            .andExpect { status { isBadRequest() } }
        val saved = read(mvc.put(url) { asAdmin(); body("""{"officialUrl":"https://letswift.kr/2026/","allowedHosts":["letswift.kr"],"intervalDays":3,"enabled":true}""") }
            .andExpect { status { isOk() } })
        assertEquals(3, saved["intervalDays"].asInt())
        mvc.get("/v1/admin/programs/999999/collection-settings") { asAdmin() }.andExpect { status { isNotFound() } }
    }

    @Test
    fun `초안도 검색되고, 게시하면 상태가 바뀐다`() {
        jdbc.sql("update activities set publication_status = 'DRAFT', official_source = false where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e'").update()

        val draft = read(mvc.get("/v1/admin/activities?publicationStatus=DRAFT&q=워크숍") { asAdmin() })["items"].single()
        assertEquals("DRAFT", draft["publicationStatus"].asString())
        assertEquals(false, draft["officialSource"].asBoolean())
        assertEquals("강남 코드스쿼드 오피스, 서울 강남구 강남대로62길 23 4층", draft["location"].asString())

        val published = read(
            mvc.patch("/v1/admin/activities/${draft["id"].asLong()}/publication-status") { asAdmin(); body("""{"publicationStatus":"PUBLISHED"}""") }
                .andExpect { status { isOk() } }
        )
        assertEquals("PUBLISHED", published["publicationStatus"].asString())
        mvc.patch("/v1/admin/activities/999999/publication-status") { asAdmin(); body("""{"publicationStatus":"PUBLISHED"}""") }
            .andExpect { status { isNotFound() } }
    }
}
