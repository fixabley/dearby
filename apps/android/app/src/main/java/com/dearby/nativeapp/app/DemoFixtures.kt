package com.dearby.nativeapp.app

import com.dearby.nativeapp.entities.catalog.model.demoActivities
import com.dearby.nativeapp.pages.profile.ProfileState
import com.dearby.nativeapp.pages.wallet.WalletEntryState
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.widgets.card.cardContent.CardState
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
val demoCards = listOf("네트워킹" to "새로운 인연에게 나를 소개해요.", "프로젝트 소개" to "함께 만드는 더 큰 가능성.", "커뮤니티" to "함께 성장하는 커뮤니티를 만들어요.").mapIndexed { index, (title, description) ->
    CardState("mine-$index", demoProfile.name, "서비스 기획", title, description, demoProfile.introduction,
        demoProfile.contacts.filter { it.id in setOf("email", "kakao") }, demoProfile.histories)
}
val demoPublicCard = demoCards.first().copy(id = "public-jimin", contacts = demoProfile.contacts.filter { it.id in setOf("email", "kakao", "github", "behance") })
// Same IDs on iOS: one card spans two activities and one has none.
private val demoWalletActivityIds = listOf(listOf("conference", "camp"), listOf("conference"), emptyList(), listOf("camp"), listOf("meetup"))
// Wallet groups follow the activity schedule order.
val walletActivities = demoActivities.sortedBy { it.schedule.startAt }.map { it.id to it.title }
val demoWallet = listOf(
    Triple("최유진", "프로덕트 디자이너", "디자인과 협업"), Triple("박서연", "서비스 기획", "프로젝트 이야기"),
    Triple("이도윤", "프론트엔드 개발", "함께 만드는 서비스"), Triple("정하린", "커뮤니티 운영", "함께하는 커뮤니티"), Triple("김현우", "개발자", "배움과 연결"),
).mapIndexed { index, (name, job, title) ->
    WalletEntryState(CardState("received-$index", name, job, title, if (index == 0) "일상 속 불편을 디자인으로 풀어요." else "함께 배우고 새로운 경험을 나누어요.", "새로운 동료와 함께 성장하고 싶어요.",
        listOf(ContactState("email", "email", "이메일", "hello@example.com"), ContactState("link", if (index == 0) "behance" else "kakao", if (index == 0) "Behance" else "카카오톡", "https://example.com")),
        demoProfile.histories), reciprocal = index >= 3, activityIds = demoWalletActivityIds[index])
}
