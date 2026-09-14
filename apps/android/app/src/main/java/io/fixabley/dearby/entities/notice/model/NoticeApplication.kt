package io.fixabley.dearby.entities.notice.model

internal data class NoticeApplication(
    val summary: String,
    val opensAt: String? = null,
    val opensOn: String? = null,
    val closesAt: String? = null,
    val closesOn: String? = null,
    val timezone: String = "Asia/Seoul",
    val url: String? = null,
    val methods: List<String> = emptyList(),
    val requiredDocuments: List<String> = emptyList(),
    val submissionLocations: List<String> = emptyList(),
)
