package io.wid.dearby.domain.profile

import io.wid.dearby.domain.UserId
import java.time.OffsetDateTime

// 내 프로필(native-v1.md "프로필·명함 v2"): 데이터 입력만 한다. introduction은 자기소개 글이고 꾸미기 템플릿은 명함에 있다.
// 저장마다 version이 1 오르고 같은 내용이 profile_versions에 남는다
data class Profile(
    val userId: UserId,
    val name: String,
    val job: String,
    val introduction: String,
    val contacts: List<Contact>,
    val histories: List<History>,
    val version: Int,
    val updatedAt: OffsetDateTime,
)
