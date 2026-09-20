# Android 구현 인계

## 현재 작업 — 설계 단순화 진행 중 (2026-09-20)

사용자가 iOS 설계 재평가를 Android 실제 코드에도 적용하도록 승인했다. 기능·화면·원본 모델·Room·단일 즐겨찾기·캘린더 동의/foreground/취소 계약을 보존한다. 자기 checkout의 apps/android 및 이 역할 문서/workstream만 수정하며 push/PR/merge는 메인 담당이다.

- Git/Orca 확인: 2026-09-20, origin/main PR32 `2362917`에서 만든 `fixabley/dearby-android-design-simplification`; checkout `/Users/jominjun/Documents/dearby/dearby-android-design-simplification`. 메인 Dearby의 Orca 하위 worktree이며 별도 Git checkout이다.
- Worker `term_dda3f25d-2d72-4efc-9c17-59970971ee35`, task `task_74dd1744d0d1`, dispatch `ctx_1d90d191c0f4`. 재개 시 실시간 상태를 다시 확인한다.
- 조사 완료: 카드의 미사용 신청/장소 요약·신청 날짜 표시값, 상세의 미표시 출처/분류/관계 투영, 내부 전용 진입점이 정리 후보다. Shared UI 직접 참조는 이미 허용한다. State 보조타입/VM class/Content 파일명 강제는 없으며 Swift 검사체계를 복제하지 않는다.
- 수명 소유자는 유지: NoticeSession은 공유 저장소·snapshot·VM 캐시, BusySession은 상세 조회 취소/세대/foreground를 담당한다. SettingsController와 중복된 BusySession의 미호출 권한 요청 API를 검토 중이다.
- 카드 구현/검증 완료: 미사용 표시값 3개와 계산을 제거하고 계측 fixture를 갱신했다. JVM 전체·Debug/계측 APK·lintDebug(오류 0/경고 13)·FSD101/자체회귀24를 이번 checkout에서 통과했다. 전용 Dearby_Issue2_Test를 5556으로 부팅했고 계측은 최종 변경 후 실행한다. 과거 검증과 구분한다.
- 다음 행동: 기능별 코드·관련 테스트·문서 변경, Ponytail 변경 후 검토, JVM/lint/APK/FSD와 가능한 전용 emulator 계측 후 작은 커밋을 메인에 인계한다.

## 유지하는 구현과 이전 검증

Compose pager·더블탭 조직 저장, 카드/목록/상세 State 조립, Notice/Organization 독립 repository와 Room L1→L2→mock, snapshot 원자적 교체·실패 복구·off-main·stale publish 차단을 유지한다. 최초 캘린더 안내/boolean 설정과 일시적 busy 조회, 지도/캘린더 URL, 네이티브 버튼/접근성도 보존한다.

카드 줄/장소/긴 토큰 표시의 이전 증거는 [카드 줄 검증](../../apps/android/docs/card-schedules/CARD-LINES.md), 캘린더는 [기존 검증](../../apps/android/docs/calendar-busy/VERIFICATION.md), 실제 구조는 [ARCHITECTURE](../../apps/android/ARCHITECTURE.md)를 따른다. 과거 세션/5556 상태는 현재 상태가 아니다. #13/#14/#15 전체 후속은 이번 범위가 아니다.
