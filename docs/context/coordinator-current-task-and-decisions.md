# 현재 작업과 결정

2026-09-20 21:03 KST. 사용자가 iOS 설계 재평가 후보·보류 항목의 구현/병합과 Android 동일 기준 적용을 승인했다. 추가로 제품 재기획 전용 하위 세션을 요청했다.

## 완료

PR27~33은 모두 필수 architecture와 Simulator CI 성공을 확인하고 main에 순서대로 병합했다. origin/main 77fe4c0. PR32는 미사용 표시필드/export, PR33은 Shared 디자인 예외·명시적 pure UI 계약·State 보조타입/VM 형식 완화·표시 UI 귀속과 상세 Route 직접조립이다. 기존 기능·캐시·권한 수명은 유지한다. 검토/로컬 회귀는 [iOS 인계](ios-implementation-and-handoff.md)를 따른다.

Root도 PR33 소스로 자기 checkout에서 Simulator build/install/launch를 성공했다(20:45, iOS26.5 DAEAAE9E-D050-4EC0-AF3F-0E1E3FB18581). 발견 screenshot/AX 확인. 자동 tap은 success receipt와 달리 상세/즐겨찾기 화면 전환이 관측되지 않았다. 데스크톱 대체 검증 시 Simulator 앱이 보이지 않았고 선택된 Xcode 경로의 Developer/Applications/Simulator.app이 없어 창을 열지 못했다. 원인 미확정이며 UI 입력/VoiceOver/실제 권한은 통과로 주장하지 않는다. 성공 build log는 /Users/jominjun/Library/Developer/XcodeBuildMCP/workspaces/dearby-f72e5683b9f6/logs/build_run_sim_2026-09-20T11-44-33-505Z_pid2717_e21e5a8a.log.

## Android 구현·검증 완료, 통합 PR

root refactor/android-design-simplification에 플랫폼 커밋을 통합했다. 카드 미사용 투영 e370c03→01e3ab0, 상세 표시 투영 1bc9972→916d02d, 미호출 권한 요청 경로 정리 9d107e5→1bb1ef6. 원본 model/codec와 실제 설정 동의·취소 수명은 보존한다. PR34를 게시했다. Shared 경계 1740bda→06febd6 통합 후 root FSD101/회귀30 통과. 담당 세션의 JVM80/lint 오류0·경고13/APK 통과, 계측58은57통과/1실패였으며 과거 CalendarEditorTest READ_CALENDAR 미선언 가정이 현재 opt-in busy와 충돌한 실패였다. production은 그대로 두고 WRITE 미선언/Intent flags=0/기존 extras 계약으로 정정한 da43846→1e66ba7을 통합했다. 수정 클래스2개 재검증과 lint 재실행은 통과했고 전체58을 재실행했다고 기록하지 않는다. 실제 앱 지도/pin 열기·복귀, 캘린더 앱 계정 안내 전환도 확인했으며 로그인/일정저장은 하지 않았다. Android 최종 소스는 worker와 일치한다. 통합·CI·병합 정본은 [PR34](https://github.com/fixabley/dearby/pull/34)이며 재개 시 GitHub 상태를 확인한다.

Orca Run run_58f7d03f4deb, runtime f311bec3-cb3a-46db-b79e-2dbf2412ee06. Android task_74dd1744d0d1 / ctx_1d90d191c0f4 / term_dda3f25d-2d72-4efc-9c17-59970971ee35. checkout dearby-android-design-simplification, branch fixabley/dearby-android-design-simplification, base origin/main 2362917. 최초 독립 카드로 생성한 배치는 사용자 지적에 따라 20:43 메인 하위로 정정했다. 앞으로 플랫폼 worktree는 처음부터 메인 하위로 생성한다. Git base와 Orca 부모 연결은 별개다. iOS worker 086fd94/ctx_77f89952ed32는 완료·retained·ack.

## 제품 재기획 세션

사용자와 직접 대화하는 기획 전용 dearby-product-replanning을 메인 하위로 생성·활성화했다. branch fixabley/dearby-product-replanning, base origin/main 77fe4c0, terminal term_f6c5228d-4e3c-4873-a4aa-a9d1011c93fe. prompt 전달 후 기존 기획 요약 응답을 runtime에서 확인했다. 자동완료를 기다리는 감독 worker가 아니라 사용자 직접 인터뷰 세션이며 유지한다. .symposium/scratch와 docs/product, 제품 역할 문서를 소유하고 앱코드/다른checkout은 수정하지 않는다. 기존 Seed는 archive 보존하고 승인한 재기획만 기록한다. 메인에서는 제품 질문을 중복 진행하지 않는다.

## 주의와 다음 행동

사용자 root Xcode project/scheme 및 ArchitectureTests/.swiftpm 변경은 보존하며 커밋 제외. 원래 Xcode diff와 동일함을 확인했다. 하위 checkout은 .git/info/exclude에 로컬 제외하여 root에 실수로 추가하지 않는다. 강제push/보호규칙 우회 없음. Android 변경 후 Ponytail·정확성 검토 완료. worker_done succeeded/retain·delivery ack 완료, reclaimable0 확인. 최종 게시 커밋의 CI 성공을 확인한 뒤 PR34를 병합하며 이후 원격 상태는 PR에서 재확인한다. 제품 재기획 대화는 해당 세션에서 계속한다.

완료 세션: Android da438461c84101d66504884333fd17fe21bcb480 clean, 단말/5556 앱 유지. iOS도 retained. 역할 문서의 실행 검증과 한계를 다음 작업의 출발점으로 사용하고 같은 검증을 새 실행으로 복사하지 않는다.
