package io.wid.dearby.domain

// 맞춤 대기열 장면 ①·②(native-v1.md "맞춤 대기열(스토리형 쇼츠)"): 한 줄 소개(hook)와 핵심 세 줄(highlights). 수집이 공개 설명으로 만들고 운영자가 고친다
data class Highlight(val icon: HighlightIcon, val text: String)

enum class HighlightIcon(val code: String) {
    COST("cost"), PERIOD("period"), TEAM("team"), MENTOR("mentor"), AWARD("award"), PLACE("place"), TALK("talk"), TICKET("ticket");

    companion object {
        fun of(code: String?): HighlightIcon? = entries.firstOrNull { it.code == code?.trim() }
    }
}

object StoryTexts {
    const val MAX_HOOK = 60 // 30자 안팎을 권하고 그보다 길면 받지 않는다
    const val MAX_HIGHLIGHT = 40
    const val MAX_HIGHLIGHTS = 3

    // 운영자 입력 검사(어드민 PATCH). 빈 문자열은 지운다(null)
    fun requireHook(value: String): String? {
        val hook = value.trim().ifEmpty { return null }
        if (hook.length > MAX_HOOK) throw InvalidInputException("한 줄 소개는 ${MAX_HOOK}자까지입니다")
        return hook
    }

    fun requireHighlights(items: List<Pair<String?, String?>>): List<Highlight> {
        if (items.size > MAX_HIGHLIGHTS) throw InvalidInputException("핵심 줄은 ${MAX_HIGHLIGHTS}개까지입니다")
        return items.map { (icon, text) ->
            val kind = HighlightIcon.of(icon) ?: throw InvalidInputException("icon은 ${HighlightIcon.entries.joinToString(", ") { it.code }} 중 하나입니다")
            val line = text?.trim()?.takeIf { it.isNotEmpty() } ?: throw InvalidInputException("핵심 줄 글이 비어 있습니다")
            if (line.length > MAX_HIGHLIGHT) throw InvalidInputException("핵심 줄은 ${MAX_HIGHLIGHT}자까지입니다")
            Highlight(kind, line)
        }
    }

    // 수집 결과 정리: 틀린 항목은 그 항목만 버린다(활동은 저장한다)
    fun collectedHook(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_HOOK }

    fun collectedHighlights(items: List<Pair<String?, String?>>): List<Highlight> = items.mapNotNull { (icon, text) ->
        val kind = HighlightIcon.of(icon) ?: return@mapNotNull null
        text?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_HIGHLIGHT }?.let { Highlight(kind, it) }
    }.take(MAX_HIGHLIGHTS)
}
