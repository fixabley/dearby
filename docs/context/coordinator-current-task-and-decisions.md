# 현재 작업과 결정

2026-09-15: 현재 작업은 Swift 아키텍처 테스트 적용이다. 이전 UI/캘린더 작업 정리는 완료됐다.

- 사용자 승인: Harmonize + Swift Testing으로 지원되는 구조 규칙을 검사하고 기존 Python FSD 참조 검사를 유지한다. 프로덕션 모듈 분리나 UI 변경은 하지 않는다.
- iOS 담당 worktree: dearby-ios-architecture-tests. root는 ci/ios-architecture-tests에서 .github/workflows/ios-architecture.yml 및 공통 검사 운영 문서를 담당한다.
- Run run_172be1f40d2f / Task task_b014b64c1f3f / Dispatch ctx_c8cf3ee2e5b6 / terminal term_63e15d3b-5a6c-4075-aca7-500e2300490c, runtime b2a34e5f-8da0-4f91-83e6-b081d2c899e2. 재개 시 실시간 확인.
- main branch protection 적용 완료: iOS architecture required, strict=true, enforce_admins=true, force push/deletion 금지. workflow는 아직 미병합/미실행이므로 현재 검사 완료 상태가 아니다.
- worker는 apps/ios와 자기 역할 인계만 commit/push하고 PR 생성하지 않는다. root가 worker branch를 통합해 구현+CI 단일 PR을 만들고 실제 CI를 검증한다. 현재 Xcode16.4/macOS15 workflow 초안, worker 호환성 확인 대기.

이전 완료 기록:

- #2 native Shared/UI: PR7 공통 원칙, PR8 iOS, PR9 Android 병합 완료.
- #10 캘린더 바쁜 시간: PR11 Android, PR12 iOS 병합 완료. merge commit으로 기능별 커밋 보존.
- 남은 검증·개선: #13 실제 OS 캘린더, #14 접근성/큰 글자 제스처·짧은 블록, #15 iOS 빈 결과 진단. 아직 구현하지 않음.
- 기존 코드 검증은 플랫폼 문서 기록을 따른다. 이번 정리에서는 PR 범위/diff check, head의 main 포함과 백업을 확인했다. 새로운 전체 앱 테스트 실행으로 주장하지 않는다.
- 완료된 iOS·Android Orca 세션은 transcript를 보존하고 종료, worktree 제거. 다음 구현은 main에서 이슈별로 만들며 역할 분리를 유지한다.

현재 정본: docs/architecture/native-apps.md, native-design-system.md 및 각 앱 ARCHITECTURE.md. 과거 논의는 archive/2026-09-15-before-pr-cleanup/에 보존했다.
