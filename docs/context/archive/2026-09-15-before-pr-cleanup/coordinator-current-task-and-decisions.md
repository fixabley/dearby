> 최신 상태 (2026-09-15 02:00 KST): #10 최신 요청 구현·검증 완료, iOS PR12 / Android PR11 Draft push 완료. 두 담당 세션 retained, active 작업 없음. 최종 head·검증·한계는 [issue-10-device-calendar.md](issue-10-device-calendar.md)의 최종 완료 확인 참조. 아래 진행 중 기록은 이전 이력입니다.

# 조율 — 현재 상태와 다음 행동

갱신: 2026-09-15 KST. #2 시간축 완료, #10 기기 캘린더 연동 구현 중. 현재 정본은 issue-10-device-calendar.md. 최신 추가: 겹치는 일정 확인하기 설정을 환경설정으로 이동하고 최초실행에 안내/켜기·나중에1회, 설정영속, 상세에는결과만. 사용자 최신 UI는 타임라인 본문 활동Primary/개인일정보조색 반투명블록 + 실제겹침 점선 + warning icon이다. 시간눈금 배경방안도 최신요청으로 폐기됨. 아래 과거 두열/우측선/상단범례 해석보다 우선한다. main 미병합.

## 완료 상태

[#1 구조 개선](https://github.com/fixabley/dearby/issues/1)을 완료로 닫고 PR #3(설계)·#6(공통 계약)·#4(Android)·#5(iOS)를 main에 merge했다. 기능별 커밋 이력을 유지했다. main 기준은 `f963401a83cd5db03e2f8685eb542bcf7340f21d`이다.

기존 플랫폼 worktree·담당 세션과 병합된 브랜치를 정리했다. 그 정리 이후 #2 전용 iOS·Android worktree를 새로 구성했다. [백업 및 통합 기록](coordinator-architecture-merged-and-worktrees-cleaned.md)에 복원 위치가 있다. 기존 로컬 AGENTS·Symposium·README·.gitignore·인계 문서는 미커밋 상태로 보존했다.

## 유지할 결정

- 두 앱의 기능 구분과 책임은 맞추며 상태 관리는 SwiftUI·Compose 관례를 따른다.
- FSD 의존 방향과 독립 NoticeModel·OrganizationModel을 유지한다. ViewModel이 화면 State를 조립하고 순수 UI는 표시값·콜백을 받는다.
- Widgets는 도메인별 그룹 아래 View·ViewModel·State를 동위 배치한다. Primary/Secondary 버튼은 Shared/UI, 조직 저장 문구·하트 조합은 widget, 제목은 카드 내부다.
- 즐겨찾기 상태는 단일 소유하며 기존 조직 ID와 저장 형식을 유지한다.
- 조회는 인메모리 → SwiftData/Room → 외부 source(현재 mock)다. 실제 API 연결은 미구현이다.
- 지도·캘린더 흐름을 유지한다. 일정 메모는 검증된 공고 원본 URL만 담는다.

상세 기준은 [공통 아키텍처](../architecture/native-apps.md)와 [공통 데이터 인계](shared-data-and-source-decisions.md)를 따른다.

## 이전 완료 웨이브 (검증 시점 구분)

[#2 네이티브 UI](issue-02-native-ui.md): 공통 기준 Draft PR7 `6b1c5e3`, iOS PR8 `83c3f9a48c2a702ab9055578b11e6cd3b3481170`, Android PR9 `ff8ccaa648494b93784ce480881074447694cf5e`. 각 플랫폼 diff는 apps/ios 또는 apps/android 하위만이며 root 기존 미커밋 파일은 보존했다. API는 작업하지 않았다.

사용자 추가 요구인 명확한 동작 아이콘화·기간/장소 간소화·글꼴 위계·일정별 이름 강조를 반영했다. 신청은 별도 구획, 각 일정은 굵은 이름→기간→장소/온라인과 해당 캘린더 버튼으로 구성한다. State/VM 표시 변환만 허용하고 도메인·캐시·일정 배열·원본 URL 메모 계약은 유지한다. Figma 원본은 미확인이고 플랫폼 기본 컴포넌트를 따른다.

## 작업 방식

기능·컴포넌트 단위로 PR과 커밋을 나눈다. 한 기능의 코드·관련 테스트·문서를 묶고 기존 게시 이력을 임의로 재작성하지 않는다. 이전에 승인한 내용을 다시 인터뷰하지 않는다. [검증 기록](verification-and-local-devices.md)의 미검증 항목을 통과로 취급하지 않는다.

## 이전 완료 웨이브의 검증과 정산

최신 iOS build/full standalone/FSD69 및 실제 doubletap·저장·삭제·swipe·신청/행사 EventKit 진입/폐기·Maps 실행·light/dark/AX5를 확인했다. Android JVM46/계측39/FSD68/검사fixture24/Debug·Release·lint 오류0(기존12), 최신 PNG36개를 확인했다. root는 XML/로그·주요 실제 PNG·PR head/범위와 range diff check를 검토했다. 검증 한계와 기기 상태는 역할 문서 및 verification-and-local-devices.md가 정본이다.

Run run_207680e6497e의 최초2건과 후속 iOS task_40e1dc885a8e/ctx_02de61cc3e88, Android task_117faf7b80eb/ctx_386a8761ea2f 모두 succeeded. 후속 완료 delivery_22eddd2d921c·delivery_93bc4931f011 처리/ack. 사용자 역할 세션 유지 요청에 따라 두 terminal retained. scoped reclaimable0, pending delivery없음. 상시 백그라운드 감시는 아니다.

이전 완료 시점의 상태다. 이후 아래 활성 후속을 수행 중이므로 현재 종료/retained 상태로 해석하지 않는다. 자동 merge하지 않았다. 역할 인계와 context는 로컬 보존, 공통 기준 문서는 PR7에 있다.

## 활성 후속 — 사용자 시간축 스크린샷

직전 캘린더 참고 상세는 iOS053340d(독립변환/VM/FSD73/build·대표4장), Android5dbb8c5(JVM52/관련계측7/FSD71/build·대표8장) 완료, 원격/diff/대표PNG 확인. iOS completion delivery_9e03e9603841 검토 후 다음 task로 즉시재사용/ack. Android 이전completion은 retain/ack 후 재사용했다.

사용자가 NSIRD_screencaptureui_mOXjQF 및 XWdqJk의 실제 PNG2장을 제공했다. root가 view_image로 둘 다 읽었다. 핵심: 자연스러운 기간문장, 도메인+열기 카드, 시간축/일정블록. 셸의 TemporaryItems 폴더 접근이 막혀 view_image bytes를 /tmp/dearby-calendar-reference-1.png·2.png에 보존했고 docs/context/references/calendar-screenshots에도 복사했다. worker 파일없음 질문2건은 배치완료로 답하고 delivery_a2521b5e4219 ack했다.

새 iOS task_6db37b235ba5 / ctx_f3f9269635ed, Android task_e7f82188be17 / ctx_e777ed68e657, 기존terminal/worktree 재사용 ready·turnStart 확인. 두 PNG 직접확인 후 natural period/link card/일간 timeline 구현을 배정했다. bounded 세로스크롤 시간축, 날짜선택/이전다음, 정확한 연속기간 day clipping/sourcezone/DST/자정/배타끝, 날짜/시간 미확인에는 가짜블록금지. 기존domain/cache/calendar export/map/favorites/card본문 유지. 관련변환·clipping tests/빌드/실제상세·날짜전환·큰글자 대표화면만 검증. 기능별commits 기존Draft PR8/9 push, noforce/no merge. 이 활성 후속 완료/검증/retention·ack 정산 전 종료하지 않는다.

## 활성 후속 — 기기 캘린더 #10
사용자가 바쁜 시간 블록과 겹침 여부만 표시를 채택했다. https://github.com/fixabley/dearby/issues/10 생성 완료. 현재 시간축 작업을 완료한 동일 플랫폼 세션을 즉시 재사용해 별도 후속 PR로 구현한다. 기존 플랫폼 브랜치 기반 stacked Draft PR, 앱별 범위 유지. 상세한 승인 규격은 GitHub #10과 /tmp/dearby-device-calendar-issue.md.
최신 추가 요청: 동의 시 바쁜 시간 조회·활동 일정 겹침 확인 목적과 제목/장소 미표시·서버 미전송을 설명한다. `내 일정 표시` native switch 기본 OFF, 최초 ON에서 안내 후 명시적 계속→OS permission, OFF에서 조회취소/인메모리결과삭제/stale 방지. 기존권한철회와는 별개. 읽기 범위는 선택일 활동시간만, 신청기간은 제외. 거절/오류≠일정없음. 개인 캘린더 접근을 자동 허용하거나 실제 읽지 않고 fake provider로 검증한다.

Android timeline task_e7f82188be17/ctx_e777ed68e657 succeeded at 15:54Z head9e7879c93da8c38ddca94eb761b43d10c8bc0554. JVM58/계측10/debug/lint/FSD80+24 및 대표PNG 확인, PR9 apps-only 확인. root rangecheck에서 EOF공백2건 찾아 후속 담당에게 정리 전달. completion delivery_c35d79cd4d6a 처리/ack 및 동일terminal 즉시 재사용: 새 #10 Android task_b230cc9d6d88 / ctx_8ade3dbca19e ready/turnStart observed. spec /tmp/dearby-busy-android-spec.md, receipt /tmp/dearby-busy-android-start.json. 기존 PR9 EOF정리 후 새 branch fixabley/dearby-android-calendar-busy, base fixabley/dearby-android stackedPR. iOS timeline Task는 아직 active.

iOS timeline 완료 delivery_ecc34b04a308 검토수락/ack, same terminal 재사용 #10 task_11eadc19d0b0 / ctx_a0a1c01f677b ready·turnStart observed. 시작receipt /tmp/dearby-busy-ios-start.json. 현재 두 #10 작업 모두 active, timeline 둘 다 succeeded. iOS후속 branch fixabley/dearby-ios-calendar-busy / base fixabley/dearby-ios-2. 종료 전 두 #10 완료검증 및 retain/ack/reclaimable0 필요.
