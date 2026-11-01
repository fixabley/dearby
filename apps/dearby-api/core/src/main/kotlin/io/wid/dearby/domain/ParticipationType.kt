package io.wid.dearby.domain

// 참여 방식(native-v1.md "유형(참가등록·선발)"). REGISTRATION: 신청하면 참가하거나 누구나 낸 결과물을 심사해 수상만 가림(빠른 신청 대상),
// SELECTION: 서류·면접·시험·예선으로 참가자를 걸러 다음 단계(본선·발표·활동)에 들어갈 사람을 뽑음
enum class ParticipationType { REGISTRATION, SELECTION }
