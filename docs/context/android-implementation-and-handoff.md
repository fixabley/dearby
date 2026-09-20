# Android 구현 인계

## 현재 작업 — 설계 단순화 진행 중 (2026-09-20)

사용자가 iOS 설계 재평가를 Android 실제 코드에도 적용하도록 승인했다. 기능·화면·원본 모델·Room·단일 즐겨찾기·캘린더 동의/foreground/취소 계약을 보존한다. 자기 checkout의 apps/android 및 이 역할 문서/workstream만 수정하며 push/PR/merge는 메인 담당이다.

- Git/Orca 확인: 2026-09-20, origin/main PR32 `2362917`에서 만든 `fixabley/dearby-android-design-simplification`; checkout `/Users/jominjun/Documents/dearby/dearby-android-design-simplification`. 메인 Dearby의 Orca 하위 worktree이며 별도 Git checkout이다.
- Worker `term_dda3f25d-2d72-4efc-9c17-59970971ee35`, task `task_74dd1744d0d1`, dispatch `ctx_1d90d191c0f4`. 재개 시 실시간 상태를 다시 확인한다.
- 조사 완료: 카드의 미사용 신청/장소 요약·신청 날짜 표시값, 상세의 미표시 출처/분류/관계 투영, 내부 전용 진입점이 정리 후보다. Shared UI 직접 참조는 이미 허용한다. State 보조타입/VM class/Content 파일명 강제는 없으며 Swift 검사체계를 복제하지 않는다.
- 캘린더 구현/검증 완료: NoticeSession의 공유 저장소·snapshot·VM 캐시와 BusySession의 상세 조회 취소/세대/foreground를 유지했다. SettingsController와 중복된 미호출 confirm/permissionResult/retry 및 Consent/Requesting만 제거했고 overlays의 불필요한 mutable 복사를 없앴다. JVM80(지연 결과 회귀 추가)·계측 APK·FSD101/자체회귀24 통과.
- 카드 구현/검증 완료: 미사용 표시값 3개와 계산을 제거하고 계측 fixture를 갱신했다. JVM 전체·Debug/계측 APK·lintDebug(오류 0/경고 13)·FSD101/자체회귀24를 이번 checkout에서 통과했다. 전용 Dearby_Issue2_Test를 5556으로 부팅했고 계측은 최종 변경 후 실행한다. 과거 검증과 구분한다.
- 상세 구현/검증 완료: 미표시 투영 5개와 context의 중복 ID/role을 제거하고 원본·codec 검증을 유지했다. JVM79·계측 APK·FSD101/자체회귀24 통과(`apps/android/build-design-detail.log`).
- 구조 경계 구현 완료: Shared UI 직접 사용 유지 및 infrastructure 금지, 내부 FavoriteNoticeState export 정리. FSD101/자체회귀30 통과. 최종 JVM80/lint 오류0·경고13/Debug·계측 APK도 통과했고 전용5556 전체 계측 진행 중이다.
- 다음 행동: 계측 결과·제한을 [이번 검증](../../apps/android/docs/design-simplification/VERIFICATION.md)에 확정하고 구조 경계 커밋을 완료한 뒤 메인에 인계한다.

## 유지하는 구현과 이전 검증

Compose pager·더블탭 조직 저장, 카드/목록/상세 State 조립, Notice/Organization 독립 repository와 Room L1→L2→mock, snapshot 원자적 교체·실패 복구·off-main·stale publish 차단을 유지한다. 최초 캘린더 안내/boolean 설정과 일시적 busy 조회, 지도/캘린더 URL, 네이티브 버튼/접근성도 보존한다.

카드 줄/장소/긴 토큰 표시의 이전 증거는 [카드 줄 검증](../../apps/android/docs/card-schedules/CARD-LINES.md), 캘린더는 [기존 검증](../../apps/android/docs/calendar-busy/VERIFICATION.md), 실제 구조는 [ARCHITECTURE](../../apps/android/ARCHITECTURE.md)를 따른다. 과거 세션/5556 상태는 현재 상태가 아니다. #13/#14/#15 전체 후속은 이번 범위가 아니다.
