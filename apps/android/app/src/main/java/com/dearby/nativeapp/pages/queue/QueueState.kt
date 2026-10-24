package com.dearby.nativeapp.pages.queue

/** The 핵심 세 줄 icons of the contract (`cost`·`period`·`team`·`mentor`·`award`·`place`·`talk`·`ticket`). */
enum class QueueIcon { COST, PERIOD, TEAM, MENTOR, AWARD, PLACE, TALK, TICKET }
data class QueueFact(val icon: QueueIcon, val text: String)

/** One scene of a card, in the order played; the card stops on the last one. */
sealed interface QueueSceneState {
    /** [hook] big, [name] under it; the card's tagLine above. */
    data class Hook(val hook: String, val name: String?) : QueueSceneState
    /** `이런 걸 해요` with the lines. */
    data class Highlights(val facts: List<QueueFact>) : QueueSceneState
    /** `지원 자격` / `이런 걸 얻어요` with one sentence. */
    data class Eligibility(val text: String) : QueueSceneState
    data class Benefit(val text: String) : QueueSceneState
    /** `마감까지` [dday] (`D-22`, null without a deadline), [deadline] (`11월 15일 마감 · 만 34세 이하`) and the reason chips. */
    data class Deadline(val dday: String?, val deadline: String?, val reasons: List<String>) : QueueSceneState
}

/**
 * One activity of 오늘의 맞춤 대기열 (contract "맞춤 대기열(스토리형 쇼츠)"): [scenes] (at least one) with [tagLine] (`부트캠프 · 데이터·AI`) over the hook.
 * Without [coverUrl] (or when it fails) the cover is the logo on a gradient of [hue]. [field] is the 분야 `이 분야는 덜 보기` names, null hides it.
 */
data class QueueCardState(
    val id: String, val organization: String, val logoUrl: String?, val coverUrl: String?, val hue: Float,
    val tagLine: String?, val scenes: List<QueueSceneState>, val liked: Boolean, val field: String?,
)

/** The `⋯` sheet's choices; [done] is the snackbar line offered with `되돌리기`. */
enum class QueueReaction(val label: String, val done: String) {
    DISMISS("관심 없어요", "관심 없는 활동으로 표시했어요"), LESS_FIELD("이 분야는 덜 보기", "이 분야는 덜 보여 드릴게요"),
    HIDE_PROGRAM("이 프로그램 숨기기", "이 프로그램을 숨겼어요"), INELIGIBLE("지원 자격이 안 돼요", "지원 자격이 안 되는 활동으로 표시했어요"),
}

/** A reaction waiting on its `되돌리기` snackbar; [serial] tells two equal messages apart. */
data class QueueUndo(val message: String, val serial: Int)

/**
 * [index] is the page on screen (the end page is `cards.size`); [moves] counts the model's own jumps to [index] (undo, 더 받기), which the pager
 * follows, while the user's paging only updates [index]. [failed] with cards is a failed `더 받기`; [exhausted] means it found nothing new.
 */
data class QueueViewState(
    val cards: List<QueueCardState> = emptyList(), val index: Int = 0, val moves: Int = 0, val loading: Boolean = true, val failed: Boolean = false,
    val refilling: Boolean = false, val exhausted: Boolean = false, val undo: QueueUndo? = null,
)
