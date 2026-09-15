# lowerCamelCase 폴더 검증

2026-09-16 KST, 기반 ada4634, Xcode 27.0 (27A266a), Swift 6.4. 이번 실행 결과이며 이전 FSD 실행과 구분한다.

- Dearby 사용자 정의 하위 폴더를 임시 중간 이름으로 이동했다. Git index의 이전 대문자 철자를 제거하고 새 철자를 등록했으며 110개 파일이 100% rename으로 기록된다. Swift 109개 및 번들 JSON/asset 전체의 이동 전후 바이트가 동일하다.
- architecture runner: Python guard 및 Swift Testing 9 tests PASS. standalone 전체(cache/favorite/model/snapshot disk 포함), busy, detail runner 각각 exit0.
- 실제 shared/ui를 중간 이름을 거쳐 shared/UI로 변경: 전체 runner exit1 및 Swift 단독 검사 exit1. 원복 후 /tmp에서 전체 runner exit0. Swift inventory fixture는 빈 폴더·resources 하위 대문자도 거부하고 Xcode asset 구조만 허용한다.
- Xcode synchronized Dearby root 참조는 그대로 유효하다. build_run_sim 성공, 전용 Dearby-FSD-Verify B04DEBB6-53B1-4CB1-858C-8C290846D4AB (iOS26.5), PID44414. launch --busy-calendar-fixture=empty, 첫 화면 공고/캘린더 안내 정상 표시(first-screen.jpg). 추가 UI 조작, 개인 캘린더, 물리 기기, hosted CI 검증은 이번 범위에 포함하지 않았다.
- 로그는 이 폴더, 앱 빌드 로그는 로컬 XcodeBuildMCP logs/build_run_sim_2026-09-15T16-09-46-271Z_pid39779_e161ad04.log. 앱/캐시/데이터 계약 변경 없음. root가 후속 PR과 통합을 담당한다.
