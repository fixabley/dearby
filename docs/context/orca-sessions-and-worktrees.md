# Git·Orca 현재 상태

2026-09-16 하위 두 레이어 제한 후속: main `45a7c96`에서 root `test/ios-two-layer-boundaries`에서 PR27의 선행 branch `refactor/ios-app-composition` 위에 경계 검사를 통합하고 PR27/28로 게시했다. 원격 CI는 각 PR에서 확인한다. iOS checkout `/Users/jominjun/Documents/dearby/dearby-ios-architecture-tests`는 유지하며 담당자가 최신 main 기반 `feat/ios-two-layer-composition` branch에서 구현했다. Run `run_58f7d03f4deb`, Task `task_adac1e244b57`, 완료 Dispatch `ctx_3ac914267e01`, terminal `term_dd600aaf-7cbe-4288-a233-2ba46a78d35b`. worker-start ready/input_accepted/turn_started와 succeeded 보고를 확인했다. 완료 후 worker-retain 및 delivery acknowledgement 완료. Root는 공통 문서와 통합, iOS는 자기 checkout의 앱 코드/검증/역할 인계 담당. 다른 플랫폼 활성 배정 없음.

재사용 시도 `ctx_865c17828a0b`는 기존 terminal의 readiness timeout으로 실패했고 입력을 전달하지 않았다. 기존 completed/retained IDs는 이전 작업 이력이며 새 작업 권한으로 재사용하지 않는다.

## 아래는 과거 확인 기록


현재 확인(2026-09-16 01:29 KST): 사용자 승인으로 PR19–25 main 병합 완료, origin/main `fb1214d`. Root에서 최신 main을 받아 lint127파일 위반0건 확인 후 인계 문서를 별도 docs/ios-merged-lint-handoff 브랜치로 정리한다. origin은 `https://github.com/fixabley/dearby.git`다. 코드 담당 worktree/retained 세션은 유지하며 이번 턴에서 재배정하지 않았다.

- iOS checkout: `/Users/jominjun/Documents/dearby/dearby-ios-architecture-tests`, branch `fixabley/dearby-ios-architecture-tests`, 최종 `ada4634`. Root에 최종 기록까지 cherry-pick 통합했다.
- Run `run_58f7d03f4deb` / Task `task_31d48a682566` / Dispatch `ctx_338f8e66f52b`: 구현·검증 succeeded 보고를 수신하고 acknowledged. Terminal `term_63e15d3b-5a6c-4075-aca7-500e2300490c`은 사용자 요청에 따라 **retained**로 유지했다. 완료 Dispatch로 추가 작업을 시작하지 않는다.
- 후속 피드백 시 런타임/checkout 상태를 재확인하고 기존 담당 terminal에 새 Task/Dispatch로 배정한다. Root가 플랫폼 구현을 중복 시작하지 않는다. 이전 `ctx_c8cf3ee2e5b6`는 PR18 완료 이력이다.
- Root는 공통 문서·CI·PR/통합, iOS worker는 자기 apps/ios·역할 인계를 담당한다. Android/API 활성 배정 없음.

## 폴더 소문자 시작 후속 (2026-09-16 01:09 KST)

사용자 추가 피드백 Task `task_cdb97a35bdce`. 기존 terminal 재사용 Dispatch `ctx_3a9e2ee36f2e`는 agent_readiness timeout으로 실패했고 작업 입력은 배정되지 않았다. 실패 receipt와 소유 상태를 확인한 뒤 같은 checkout에 새 Codex terminal로 재시도했다.

SwiftLint 추가 요청도 같은 Task의 별도 기능 commit으로 배정했다. Root가 CI, worker가 앱 lint 설정·설치/실행 스크립트와 필요한 코드 수정을 맡는다.

완료 Dispatch `ctx_ae82a98e1b6d`, terminal `term_11663b1e-05f7-41bf-9057-226d7875e2ab`: 폴더명 a338707 및 SwiftLint ee9c4e0 성공 보고를 수신·확인 처리했고 01:20 KST retained로 전환했다. Root는 af71776/051c45e로 통합했다. 후속 작업은 이 terminal을 런타임 확인 후 새 Task/Dispatch로 배정한다. 역할 범위는 동일한 apps/ios·iOS 인계이며 root는 공통 규칙 문서·CI·PR24/25 통합을 담당한다. reclaimable worker는 0개임을 확인했다. 이전 terminal은 완료 이력 보존용 retained이며 후속 작업 담당이 아니다.

## 과거 운영 이력

아래 checkout·terminal·PID·미병합 상태는 당시 기록이다. 현재 배정으로 사용하지 않는다.

2026-09-15 정리: iOS dearby-ios-2 및 Android dearby-android 워크트리 제거, 두 완료 worker terminal release 및 transcript captured. 예전 terminal/dispatch ID를 재사용하지 않는다. 루트 checkout만 유지하며 후속 작업은 main에서 새 역할별 worktree를 만든다.

백업: `/Users/jominjun/Documents/dearby-archive/20260915-122819-cleanup`. all-refs.bundle 및 두 worktree 미추적 문서/로컬 설정/검증 보고서 102파일을 복사하고 SHA256 일치를 확인했다. 재생성 가능한 build/cache는 제외했다. Git bundle로 기존 브랜치를 복원할 수 있고 manifest.json으로 파일을 찾을 수 있다. 백업에 로컬 설정이 포함될 수 있으므로 공개 게시하지 않는다.

기능별 커밋을 유지하여 PR7/8/9/11/12 병합 완료. 아래 빌드·실행 작업을 새로 배정했다. 재개 시 런타임 상태를 다시 확인한다.

## 빌드·실행 배정 (2026-09-15 12:58 KST 확인)

main `9eeb383`, Run `run_8f0a3bd7e209`. 두 worker 모두 빌드·실행 성공 보고 수신 후 transcript 보존 및 terminal release 완료(13:04 KST). 아래 terminal/Dispatch는 이력이며 재사용하지 않는다. checkout과 실행 중 앱·기기는 보존했다.

| 역할 | checkout | Dispatch | terminal |
| --- | --- | --- | --- |
| iOS | `/Users/jominjun/orca/workspaces/dearby/dearby-ios-run` | `ctx_74519e080af3` | `term_f83fac8b-87cd-48c6-ae11-a23f88a36407` |
| Android | `/Users/jominjun/orca/workspaces/dearby/dearby-android-run` | `ctx_7de27a502993` | `term_53e2377f-a6a7-451b-ae83-8fe518f0a490` |

범위: 자기 checkout 빌드·설치·실행 및 증거 기록. 앱/전용 기기는 실행 상태로 보존. 기능 변경·후속 이슈 구현 없음.

## 카드 일정 구현 완료 (2026-09-15 16:25 KST 확인)

Run `run_a54c2d35693a`의 iOS 일정/URL 후속, Android 작업 모두 succeeded 수신 후 transcript 보존 및 worker release 완료. 활성 플랫폼 작업 없음. 이전 terminal/Dispatch는 재사용하지 않는다.

- iOS checkout: `/Users/jominjun/orca/workspaces/dearby/dearby-ios-run`, 최종 `2d59853` (선행 카드 `fdcfca3`).
- Android checkout: `/Users/jominjun/orca/workspaces/dearby/dearby-android-run`, 최종 `8547adb` (선행 카드 `9d033b8`).
- 두 역할의 기능 커밋을 로컬 main에 검토 후 merge commit으로 통합했다. 원격 push/PR 없음.
- checkout의 기존 미커밋 실행 기록과 build/기기 산출물은 보존했다. 자기 checkout 외 수정 없음. 다음 작업은 이 checkout과 새 세션 사용 여부를 런타임에서 다시 확인한다.
- 앱/전용 Simulator·emulator는 유지한다. 최신 기기/검증은 verification-and-local-devices.md 참조.

## 기간·장소 줄 컴포넌트 완료 (2026-09-15 17:04 KST 확인)

동일 checkout에서 Run `run_f43d8347585e` 완료. 아래 두 worker 모두 succeeded 수신 후 transcript 보존 및 release했다. ID는 이력이며 재사용하지 않는다.

| 역할 | Dispatch | terminal |
| --- | --- | --- |
| iOS | `ctx_d9f7b9eeb75d` | `term_f1400b32-1536-4726-90b4-67439ba8fafa` |
| Android | `ctx_a9734ee105e4` | `term_491b5f4b-c686-451a-8b2c-d2956eaa2270` |

iOS `b712ca3`, Android `848b90e`를 로컬 main에 검토·통합했고 플랫폼 소스 동일성을 git diff로 확인했다. 두 checkout의 기존 미커밋 실행 기록과 로컬 산출물은 보존했다. reclaimable worker 0개이며 앱 실행 유지: iOS PID52933, Android PID8284. 후속 사용자 요청으로 root는 feat/native-card-schedules 브랜치를 push하고 PR #17을 게시했다. 현재 root 브랜치도 feat/native-card-schedules이며 원격 main은 변경하지 않았다. 다음 작업 시 checkout·런타임·기기 상태를 다시 확인한다.
