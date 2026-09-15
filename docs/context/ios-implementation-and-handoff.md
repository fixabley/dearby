# iOS 구현 인계

## 현재 상태 — lowerCamelCase 완료 / SwiftLint 진행 (2026-09-16 KST)

자기 checkout dearby-ios-architecture-tests, branch fixabley/dearby-ios-architecture-tests, 기반 ada4634. 현재 terminal term_11663b1e-05f7-41bf-9057-226d7875e2ab / task task_cdb97a35bdce / dispatch ctx_ae82a98e1b6d이며 재개 시 런타임 재확인한다.

사용자 요청대로 Dearby의 모든 사용자 정의 소스 하위 폴더를 lowerCamelCase로 이동했다. 타입/파일명, Xcode 프로젝트/앱 루트, asset 구조와 제품 내용은 보존했다. Harmonize/SwiftSyntax/public-api/fixtures/inventory/Python/실행 스크립트/앱 문서를 함께 변경했다. 폴더명 기능 하나로 커밋하고 root가 PR24로 통합한다. 공통문서/CI와 다른 checkout은 수정하지 않았다.

이번 실행: Xcode27 architecture(9 tests), full standalone, busy, detail exit0 및 전용 iOS26.5 simulator build_run_sim 성공. 실제 shared/UI 대문자 변경은 Python 전체 runner와 Swift 단독 검사 모두 exit1, 복원 후 /tmp 전체 runner exit0. 109 Swift 파일 포함 제품 전체 바이트 동일, Git 실제 철자/100% rename 확인. 첫 화면 공고와 캘린더 안내 확인, 추가 UI 조작 없음. 근거와 한계는 [폴더명 검증](../../apps/ios/docs/evidence/lowercase-folders/README.md).

추가 승인: 같은 Task에서 SwiftLint 0.65.1 portable checksum 설치/strict runner/규칙 문서를 별도 기능 커밋으로 구현한다. root가 CI/PR25 담당. 폴더명 hash를 먼저 status 전달하고 두 기능 완료 후 worker_done 한 번 전송한다. 기존 FSD 기능별 검증 기록은 apps/ios/docs/FSD-MIGRATION.md이며 과거 UI 검증을 이번 실행으로 주장하지 않는다.
