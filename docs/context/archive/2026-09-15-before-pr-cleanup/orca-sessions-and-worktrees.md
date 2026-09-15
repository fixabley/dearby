> 최신 상태 (2026-09-15 02:00 KST): #10 최신 요청 구현·검증 완료, iOS PR12 / Android PR11 Draft push 완료. 두 담당 세션 retained, active 작업 없음. 최종 head·검증·한계는 [issue-10-device-calendar.md](issue-10-device-calendar.md)의 최종 완료 확인 참조. 아래 진행 중 기록은 이전 이력입니다.

# Git·Orca — 현재 상태와 역할 배정

갱신: 2026-09-15 01:34 KST, #10 후속 active. 새 구현 시작 전 실시간 상태를 다시 확인한다.

## 현재 상태

#1은 main f963401 통합. #2 시간축 완료 후 같은 Run `run_207680e6497e`에서 #10 기기 캘린더 작업 진행 중. 현재 Task가 정산되기 전 종료하지 않는다.

| 역할 | checkout / 브랜치 | Task / Dispatch |
| --- | --- | --- |
| 메인 | root / docs/native-design-system | 공통 기준·감독 |
| iOS | dearby-ios-2/ / fixabley/dearby-ios-calendar-busy | task_11eadc19d0b0 / ctx_a0a1c01f677b active |
| Android | dearby-android/ / fixabley/dearby-android-calendar-busy | task_67f4ae81cfc8 / ctx_9489e36b2512 active |

iOS terminal term_92037054-bdf7-447f-b5aa-fdf99c59141d, Android terminal term_3addcbfe-11e6-456f-96d7-9ad3af965286. 최신 Android 이전 task_b230cc9d6d88 완료 후 즉시 재사용, delivery_daf91d817452 처리/ack. 두 현재 Task의 검증·완료보고·retain/reuse·ack가 남았다. 최신 범위와 GitHub는 [#10 인계](issue-10-device-calendar.md).

root 미커밋 기획·인계 파일은 보존하고 앱 PR에 섞지 않는다. iOS 자동 경로 suffix -2는 .git/info/exclude에 로컬 제외했다.

## 다음 플랫폼 작업

사용자는 API·Android·iOS별 Orca 세션/worktree와 메인 감독을 요청했다. 플랫폼 작업을 재개할 때 최신 main에서 이슈 전용 담당 worktree·세션을 구성한다. 기존 세션이 있는지 먼저 확인하고 같은 작업을 중복 배정하지 않는다.

| 역할 | 소유 범위 |
| --- | --- |
| 메인 | 공통 규격·기획·루트 설정·검토·통합 |
| iOS | apps/ios/ |
| Android | apps/android/ |
| API | apps/dearby-api/; #2에서는 배정하지 않음 |

Orca 관리에는 orca-cli, 감독 작업에는 orchestration 스킬의 현재 안내를 읽는다. 파일·대화는 자동 동기화되지 않으므로 작업 카드와 담당 인계를 갱신한다. 내장 서브에이전트로 Orca 감독을 중복 실행하지 않는다.

Repository ID 기록: `c80e1d88-9400-4765-8bc5-4bbdffe399c3`. ID와 런타임의 유효성을 확인한 후 사용한다. 상시 백그라운드 감시를 설정한 상태는 아니다.
