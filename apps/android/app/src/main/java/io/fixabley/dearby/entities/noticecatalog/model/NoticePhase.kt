package io.fixabley.dearby.entities.noticecatalog.model

internal data class NoticePhase(
    val phase: String,
    val startsAt: String? = null,
    val startsOn: String? = null,
    val endsAt: String? = null,
    val endsOn: String? = null,
    val timezone: String = "Asia/Seoul",
    val mode: String = "unknown",
    val onlineUrl: String? = null,
) {
    val label: String get() = when (phase) {
        "event" -> "행사"
        "preliminary" -> "예선"
        "finalist_announcement" -> "결선 진출 발표"
        "final" -> "결선·시상"
        else -> phase
    }
    // Keep the existing UI text while retaining exact source fields for calendar export.
    val summary: String get() {
        val start = startsAt?.take(16)?.replace('T', ' ') ?: startsOn ?: "일정 미확인"
        val end = endsAt?.let { " ~ " + it.take(16).replace('T', ' ') } ?: ""
        return "$label: $start$end (한국 시간)"
    }
}
