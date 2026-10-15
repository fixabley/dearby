package io.wid.dearby.batch.application

// 모델이 페이지 본문에서 뽑은 활동. 아직 검증 전이라 모든 값을 믿지 않는다(시각은 ISO 8601 문자열 그대로)
data class ExtractedActivity(
    val roundKey: String,
    val name: String,
    val nameEn: String?,
    val description: String,
    val descriptionEn: String?,
    val participationType: String?,
    val applicationUrl: String?,
    val recruitmentStartAt: String?,
    val recruitmentEndAt: String?,
    val dateLabel: String,
    val schedules: List<ExtractedSchedule>,
    val location: String?,
    val cost: String?,
    val audience: String?,
    val evidenceQuote: String,
)
