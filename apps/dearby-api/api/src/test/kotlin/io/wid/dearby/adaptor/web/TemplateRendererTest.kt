package io.wid.dearby.adaptor.web

import io.wid.dearby.application.card.TemplateRenderer
import io.wid.dearby.domain.ContactKind
import io.wid.dearby.domain.profile.Contact
import io.wid.dearby.domain.profile.ApprovalStatus
import io.wid.dearby.domain.profile.Profile
import io.wid.dearby.domain.profile.ProfileJob
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateRendererTest {

    private val phone = Contact(UUID.randomUUID(), ContactKind.PHONE, "휴대폰", "010-1234-5678")
    private val email = Contact(UUID.randomUUID(), ContactKind.EMAIL, "메일", "me@example.com")
    private val profile = Profile(UUID.randomUUID(), "조민준", listOf(ProfileJob(UUID.randomUUID(), null, "개발자", ApprovalStatus.APPROVED, null)), "안녕하세요", listOf(phone, email), emptyList(), 1, OffsetDateTime.now())

    @Test
    fun `프로필 토큰과 고른 연락처만 채우고, 고르지 않은 연락처와 모르는 토큰은 비운다`() {
        val template = "# {{profile.name}} · {{ profile.job }}\n\n{{profile.introduction}}\n\n- 전화 {{contact:${phone.id}}}\n- 메일 {{contact:${email.id}}}\n- {{contact:${UUID.randomUUID()}}}{{profile.secret}}{{nope}}"
        val rendered = TemplateRenderer.render(template, profile, listOf(phone))
        assertEquals("# 조민준 · 개발자\n\n안녕하세요\n\n- 전화 010-1234-5678\n- 메일 \n- ", rendered)
    }

    @Test
    fun `형식이 아닌 중괄호와 나머지 마크다운은 그대로 둔다`() {
        val template = "{{ not a token }} {profile.name} {{profile.name}}} **굵게** [링크](https://example.com)"
        assertEquals("{{ not a token }} {profile.name} 조민준} **굵게** [링크](https://example.com)", TemplateRenderer.render(template, profile, emptyList()))
        assertEquals(setOf(phone.id), TemplateRenderer.contactIds("{{contact:${phone.id}}} {{contact:bad}}"))
    }
}
