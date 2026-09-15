# Git·Orca 현재 상태

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
