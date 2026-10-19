package com.dearby.nativeapp.entities.activity.model

/** 활동 종류 (contract "활동 종류(category)", 2026-10-19): API code → Korean label, in display order. Unknown or missing = 미분류 (null). */
enum class ActivityCategory(val label: String) {
    BOOTCAMP("부트캠프"), CONFERENCE("컨퍼런스"), WORKSHOP("워크샵"), STUDY("스터디"), CLUB("동아리"), LECTURE("강연"),
    HACKATHON("해커톤"), CONTEST("공모전"), MEETUP("밋업"), OTHER("기타");

    companion object {
        fun fromCode(code: String?): ActivityCategory? = entries.firstOrNull { it.name == code }
    }
}
