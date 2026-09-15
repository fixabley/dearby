# SwiftLint 실행 검증

2026-09-16 KST, 폴더명 commit a338707 이후 별도 SwiftLint 기능. Xcode27.0(27A266a), Swift6.4, macOS.

- 공식 portable SwiftLint0.65.1 다운로드 SHA256 c1e429b0599cf1b516f369a2d9ec04eaf0e436f3c12b637df8851fa52ff694d0 일치, setup script 실제 설치 성공.
- 최종 strict/no-cache lint: 앱109 + 테스트/패키지18 = 127 Swift 파일, 0 violations, exit0. 명시61개 규칙이며 전체 기본규칙 통과를 주장하지 않는다. 기준/예외는 ../../SWIFTLINT.md.
- check_swiftlint_runner.py PASS: 임시 checkout에서 도구누락 exit1, 버전불일치 exit1, 앱과 테스트 실제 trailing_whitespace 각각 exit2, 복원 exit0. 동일 runner/config로 cwd=/ 검증.
- 실제 제품 shared/lib/LintViolationProbe.swift 임시 공백위반: runner exit2. 제거 후 /tmp에서 runner exit0, fixture 잔존 없음. invalid/restored.log 참조.
- 최종 가독성 조정 뒤 architecture9 tests 및 Python guards exit0, standalone 전체 캐시/즐겨찾기/모델/snapshot disk 회귀 exit0, busy exit0, detail exit0. 원본 로컬 /tmp/ios-swiftlint-standalone.log는 유지하고 저장한 standalone 로그는 줄끝 공백만 정규화했으며, 로그의 의도적 파일경로 오류는 실패보존 fixture이며 최종 PASS/exit0 확인.
- 최종 XcodeBuildMCP build_run_sim 성공, iOS26.5 전용 B04DEBB6-53B1-4CB1-858C-8C290846D4AB, --busy-calendar-fixture=empty, PID56254. 첫 화면 공고와 안내 정상 표시, 추가 UI 조작 없음. 로컬 build log: XcodeBuildMCP/workspaces/dearby-ios-architecture-tests-2e6f4371f452/logs/build_run_sim_2026-09-15T16-16-47-942Z_pid39779_a620db40.log.
- bash -n 설치/runner 통과. root가 required CI에 setup → runner fixture → strict lint → 기존 구조/회귀를 연결한다. hosted CI/PR 통합은 root 담당이며 이번 로컬 결과와 구분한다. 개인 캘린더/물리기기 검증 없음.
