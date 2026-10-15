package io.wid.dearby.adaptor.web.v1.activities

import java.util.Locale

// 응답 언어(2026-10-15 사용자 결정): Accept-Language에서 지원 언어 중 가장 선호하는 것을 고른다.
// 헤더가 없거나 읽을 수 없거나 지원 언어가 없으면 한국어. 서버 기본 로캘에는 기대지 않는다
enum class ResponseLanguage(val tag: String) {
    KO("ko"),
    EN("en");

    // 영문 값이 없으면 한글로 채운다
    fun pick(korean: String, english: String?): String = if (this == EN) english ?: korean else korean

    companion object {
        fun from(acceptLanguage: String?): ResponseLanguage {
            val ranges = acceptLanguage?.let { runCatching { Locale.LanguageRange.parse(it) }.getOrNull() } ?: return KO
            val tag = Locale.lookupTag(ranges, entries.map { it.tag }) ?: return KO
            return entries.first { it.tag == tag }
        }
    }
}
