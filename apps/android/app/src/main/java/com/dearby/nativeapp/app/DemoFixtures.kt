package com.dearby.nativeapp.app

import com.dearby.nativeapp.pages.profile.ProfileState
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

val demoProfile = ProfileState("김지민", "서비스 기획 · 커뮤니티", "사람을 연결하는 경험을 만듭니다.", listOf(
    ContactState("phone", "phone", "전화번호", "010-0000-0000"),
    ContactState("email", "email", "이메일", "jimin@example.com"),
    ContactState("kakao", "kakao", "카카오톡", "https://example.com"),
    ContactState("instagram", "instagram", "인스타그램", "@jimin_example"),
    ContactState("github", "github", "GitHub", "https://example.com"),
    ContactState("behance", "behance", "Behance", "https://example.com"),
), listOf(
    CardHistoryState("conference", "Dearby 개발자 컨퍼런스", "운영 스태프", "2026.09"),
    CardHistoryState("camp", "Dearby 메이커 캠프", "서비스 기획", "2026.03 – 08"),
    CardHistoryState("hackathon", "캠퍼스 해커톤", "서비스 기획", "2025.11"),
))
