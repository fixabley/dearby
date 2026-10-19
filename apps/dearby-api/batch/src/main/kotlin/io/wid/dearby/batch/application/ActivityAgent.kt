package io.wid.dearby.batch.application

import io.wid.dearby.domain.collection.CollectionTarget
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.CollectionFeedback
import io.wid.dearby.domain.collection.FeedbackKind
import io.wid.dearby.domain.collection.PreviousEdition
import io.wid.dearby.domain.collection.WatchEntry

// 프로그램 하나를 수집하는 에이전트: 페이지를 받아 보고(tools.fetch·render) 활동 후보를 하나씩 제출한다(tools.submit).
// 원문 대조·조심 목록·초안 저장은 서비스가 제출마다 바로 하고 그 판정을 돌려준다. 구현은 adaptor/claude(Claude Code CLI + 도구 서버)
fun interface ActivityAgent {

    fun run(input: AgentInput, tools: AgentTools): AgentRun
}

// target.officialUrls는 오늘 날짜·이전 기수로 펼친 시작 주소다. 나머지는 요청 힌트(admin-v1.md 조심 목록·이전 기수, 운영자 피드백 최근 기록, native-v1.md 태그)
data class AgentInput(
    val target: CollectionTarget,
    val watchlist: List<WatchEntry>,
    val previous: PreviousEdition?,
    val feedback: List<CollectionFeedback>,
    val tagNames: List<String>,
)

// 서비스가 에이전트에게 주는 도구. fetch·render는 받을 수 없으면 PageFetchException(이유는 서비스가 rejected_reasons에 남긴다)
interface AgentTools {

    fun fetch(url: String): FetchedPage

    fun render(url: String): FetchedPage

    // sourceUrl은 이번 수집에서 받은 페이지여야 한다(그 원문과 대조한다)
    fun submit(candidate: ExtractedActivity, sourceUrl: String): SubmitVerdict

    // 운영자 피드백 검색(같은 프로그램 → 같은 호스트 → 나머지). kind가 있으면 그 종류만
    fun searchFeedback(text: String, kind: FeedbackKind?): List<CollectionFeedback>
}

// saved: 초안으로 저장됐는지. message는 모델에게 돌려줄 판정(거절 이유 포함), warnings는 저장했지만 운영자 피드백과 어긋나 보이는 점
data class SubmitVerdict(val saved: Boolean, val message: String, val warnings: List<String> = emptyList())

// log는 도구 루프 기록(collection_runs.agent_log)
data class AgentRun(val usage: ModelUsage, val log: AgentLog)

data class AgentLog(
    val workflowVersion: String, // 작업 절차 파일(WORKFLOW.md)의 SHA-256 앞 12자리
    val stopReason: String,
    val capReached: Boolean, // 턴 한도로 멈췄으면 true(rejected_reasons에도 남긴다). 페이지 한도는 도구 오류로 알리고 루프는 이어 간다
    val turns: Int,
    val pages: Int,
    val usage: ModelUsage,
    val costUsd: Double?, // CLI가 알려 주는 비용 환산값(구독이라 청구액은 아님)
    val calls: List<AgentToolCall>,
    // 운영자 피드백 id: 요청에 넣은 것, search_feedback로 찾은 것, 제출 판정(거절·주의)에 쓴 것. 서비스가 채운다
    val feedbackInjected: List<Id> = emptyList(),
    val feedbackRetrieved: List<Id> = emptyList(),
    val feedbackApplied: List<Id> = emptyList(),
)

data class AgentToolCall(val tool: String, val url: String?, val result: String)
