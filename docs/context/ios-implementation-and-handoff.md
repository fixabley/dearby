# iOS 구현 인계

## 현재 상태 — lowerCamelCase / SwiftLint 구현·검증 완료 (2026-09-16 KST)

자기 checkout dearby-ios-architecture-tests, branch fixabley/dearby-ios-architecture-tests, 기반 ada4634. 현재 terminal term_11663b1e-05f7-41bf-9057-226d7875e2ab / task task_cdb97a35bdce / dispatch ctx_ae82a98e1b6d이며 재개 시 런타임 재확인한다.

사용자 요청대로 Dearby의 모든 사용자 정의 소스 하위 폴더를 lowerCamelCase로 이동했다. 타입/파일명, Xcode 프로젝트/앱 루트, asset 구조와 제품 내용은 보존했다. Harmonize/SwiftSyntax/public-api/fixtures/inventory/Python/실행 스크립트/앱 문서를 함께 변경했다. 폴더명 commit a338707을 root가 PR24로 통합했다. 공통문서/CI와 다른 checkout은 수정하지 않았다.

이번 실행: Xcode27 architecture(9 tests), full standalone, busy, detail exit0 및 전용 iOS26.5 simulator build_run_sim 성공. 실제 shared/UI 대문자 변경은 Python 전체 runner와 Swift 단독 검사 모두 exit1, 복원 후 /tmp 전체 runner exit0. 109 Swift 파일 포함 제품 전체 바이트 동일, Git 실제 철자/100% rename 확인. 첫 화면 공고와 캘린더 안내 확인, 추가 UI 조작 없음. 근거와 한계는 [폴더명 검증](../../apps/ios/docs/evidence/lowercase-folders/README.md).

추가 승인 SwiftLint0.65.1 portable checksum 설치/strict runner/61개 규칙 문서 및 좁은 스타일 수정을 별도 기능 커밋으로 완료했다. 앱109+test18 lint0, missing/wrong-version/app+test 위반 fixture와 실제 제품 임시 위반 실패/복원 PASS. 최종 가독성 조정 후 architecture/full standalone/busy/detail 및 sim build_run_sim 재검증 PASS, PID56254 첫 화면 확인. 증거는 apps/ios/docs/evidence/swiftlint/README.md, 정책/예외/재현은 docs/SWIFTLINT.md. root가 CI/PR25 담당하며 setup→runner fixture→strict lint→기존회귀 계약을 합의했다. 두 기능 완료 후 현 Dispatch worker_done 한 번 전송하고 다음 지시를 기다린다. 기존 FSD 기능별 검증 기록은 apps/ios/docs/FSD-MIGRATION.md이며 과거 UI 검증을 이번 실행으로 주장하지 않는다.
