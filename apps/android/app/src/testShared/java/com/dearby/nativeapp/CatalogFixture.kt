package com.dearby.nativeapp

/**
 * Test-only `GET /v1/catalog` body (contract catalog-v1), same IDs and values as iOS `CatalogFixture`.
 * Times are fixed far ahead so recruiting stays open; the conference overlaps the example busy hour.
 */
object CatalogFixture {
    const val CONFERENCE = "b1000000-0000-4000-8000-000000000001"
    const val CAMP = "b1000000-0000-4000-8000-000000000002"
    const val SCHEDULED = "b1000000-0000-4000-8000-000000000003"
    const val STALE = "b1000000-0000-4000-8000-000000000004"
    const val APPLY_URL = "https://apply.example.test/conference"

    fun activity(
        id: String, title: String, type: String = "registration", status: String = "open", recruiting: Boolean = true,
        freshness: String = "verified", day: String, start: String, end: String, application: String? = APPLY_URL,
        override: Map<String, String> = emptyMap(),
    ): String {
        val fields = linkedMapOf(
            "id" to q(id), "programId" to q("c1000000-0000-4000-8000-000000000001"),
            "organizationId" to q("a1000000-0000-4000-8000-000000000001"), "title" to q(title), "summary" to q("$title 소개"),
            "participationType" to q(type), "recruitmentStatus" to q(status), "isRecruiting" to "$recruiting",
            "recruitmentStartAt" to q("2026-01-01T00:00:00Z"), "recruitmentEndAt" to q("2098-12-31T14:59:00Z"),
            "dateLabel" to q("$day $start~$end"), "location" to q("서울"), "cost" to q("무료"), "audience" to q("누구나"),
            "qualification" to "null", "roles" to "[${q("개발자")}]",
            "schedules" to "[{\"id\":${q(id)},\"title\":${q(title)},\"startAt\":${q("${day}T$start:00+09:00")},\"endAt\":${q("${day}T$end:00+09:00")},\"dateLabel\":${q("$day $start~$end")},\"timeZone\":\"Asia/Seoul\"}]",
            "officialUrl" to q("https://official.example.test/$id"), "applicationUrl" to (application?.let(::q) ?: "null"),
            "sourceCheckedAt" to q("2026-01-01T00:00:00.000Z"), "validUntil" to q("2099-01-01T00:00:00Z"),
            "freshness" to q(freshness), "sourceNote" to q("테스트 출처"),
        )
        fields.putAll(override)
        return fields.entries.joinToString(",", "{", "}") { (key, value) -> "${q(key)}:$value" }
    }
    fun body(activities: List<String> = defaultActivities) =
        "{\"generatedAt\":\"2026-01-01T00:00:00Z\",\"organizations\":[{\"id\":\"a1000000-0000-4000-8000-000000000001\",\"name\":\"테스트 주최\",\"description\":\"\"}],\"programs\":[],\"activities\":[${activities.joinToString(",")}]}"
    val defaultActivities = listOf(
        activity(CONFERENCE, "테스트 컨퍼런스", day = "2026-10-24", start = "13:00", end = "17:00"),
        activity(CAMP, "테스트 메이커 캠프", type = "selection", day = "2026-11-07", start = "10:00", end = "18:00"),
        activity(SCHEDULED, "테스트 예정 밋업", status = "scheduled", recruiting = false, day = "2026-11-21", start = "14:00", end = "17:00"),
        activity(STALE, "테스트 오래된 활동", freshness = "stale", day = "2026-11-28", start = "14:00", end = "17:00"),
    )
    /** JSON string literal; fixture values contain no quotes or backslashes. */
    fun q(value: String) = "\"$value\""
}
