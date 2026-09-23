# 현재 작업과 결정

2026-09-20 제품 재기획 변경 공유 반영. 사용자가 iOS 설계 재평가 후보·보류 항목의 구현/병합과 Android 동일 기준 적용을 승인했다. 추가로 제품 재기획 전용 하위 세션을 요청했다.

## 완료

PR27~34는 이전 작업에서 필수 architecture와 Simulator CI 성공을 확인하고 main에 순서대로 병합했다. 이번 인계 시 로컬 main/origin/main 추적 ref는 3e56a74다. 이번에는 fetch·원격 재조회·빌드·테스트를 실행하지 않았다. PR32는 미사용 표시필드/export, PR33은 Shared 디자인 예외·명시적 pure UI 계약·State 보조타입/VM 형식 완화·표시 UI 귀속과 상세 Route 직접조립이다. 기존 기능·캐시·권한 수명은 유지한다. 검토/로컬 회귀는 [iOS 인계](ios-implementation-and-handoff.md)를 따른다.

Root도 PR33 소스로 자기 checkout에서 Simulator build/install/launch를 성공했다(20:45, iOS26.5 DAEAAE9E-D050-4EC0-AF3F-0E1E3FB18581). 발견 screenshot/AX 확인. 자동 tap은 success receipt와 달리 상세/즐겨찾기 화면 전환이 관측되지 않았다. 데스크톱 대체 검증 시 Simulator 앱이 보이지 않았고 선택된 Xcode 경로의 Developer/Applications/Simulator.app이 없어 창을 열지 못했다. 원인 미확정이며 UI 입력/VoiceOver/실제 권한은 통과로 주장하지 않는다. 성공 build log는 /Users/jominjun/Library/Developer/XcodeBuildMCP/workspaces/dearby-f72e5683b9f6/logs/build_run_sim_2026-09-20T11-44-33-505Z_pid2717_e21e5a8a.log.

## Android 구현·검증 완료, 통합 PR

root refactor/android-design-simplification에 플랫폼 커밋을 통합했다. 카드 미사용 투영 e370c03→01e3ab0, 상세 표시 투영 1bc9972→916d02d, 미호출 권한 요청 경로 정리 9d107e5→1bb1ef6. 원본 model/codec와 실제 설정 동의·취소 수명은 보존한다. PR34를 게시했다. Shared 경계 1740bda→06febd6 통합 후 root FSD101/회귀30 통과. 담당 세션의 JVM80/lint 오류0·경고13/APK 통과, 계측58은57통과/1실패였으며 과거 CalendarEditorTest READ_CALENDAR 미선언 가정이 현재 opt-in busy와 충돌한 실패였다. production은 그대로 두고 WRITE 미선언/Intent flags=0/기존 extras 계약으로 정정한 da43846→1e66ba7을 통합했다. 수정 클래스2개 재검증과 lint 재실행은 통과했고 전체58을 재실행했다고 기록하지 않는다. 실제 앱 지도/pin 열기·복귀, 캘린더 앱 계정 안내 전환도 확인했으며 로그인/일정저장은 하지 않았다. Android 최종 소스는 worker와 일치한다. 통합·CI·병합 정본은 [PR34](https://github.com/fixabley/dearby/pull/34)이며 재개 시 GitHub 상태를 확인한다.

Orca Run run_58f7d03f4deb, runtime f311bec3-cb3a-46db-b79e-2dbf2412ee06. Android task_74dd1744d0d1 / ctx_1d90d191c0f4 / term_dda3f25d-2d72-4efc-9c17-59970971ee35. checkout dearby-android-design-simplification, branch fixabley/dearby-android-design-simplification, base origin/main 2362917. 최초 독립 카드로 생성한 배치는 사용자 지적에 따라 20:43 메인 하위로 정정했다. 앞으로 플랫폼 worktree는 처음부터 메인 하위로 생성한다. Git base와 Orca 부모 연결은 별개다. iOS worker 086fd94/ctx_77f89952ed32는 완료·retained·ack.

## 제품 재기획 세션

사용자와 직접 대화하는 기획 전용 dearby-product-replanning을 메인 하위로 생성·활성화했다. branch fixabley/dearby-product-replanning, base origin/main 77fe4c0, terminal term_f6c5228d-4e3c-4873-a4aa-a9d1011c93fe. prompt 전달 후 기존 기획 요약 응답을 runtime에서 확인했다. 자동완료를 기다리는 감독 worker가 아니라 사용자 직접 인터뷰 세션이며 유지한다. .symposium/scratch와 docs/product, 제품 역할 문서를 소유하고 앱코드/다른checkout은 수정하지 않는다. 기존 Seed는 archive 보존하고 승인한 재기획만 기록한다. 메인에서는 제품 질문을 중복 진행하지 않는다.

## 제품 재기획 변경 공유 — 구현 미승인

최신 사용자 공유를 기획 변경으로 접수했다. 메인에서는 앱·공통 계약·다른 checkout을 수정하거나 commit/push/PR/merge하지 않는다. 기존 구조 정리는 완료된 별도 작업이다. 세부 기획은 담당 세션에서 계속하며 아래 합의가 앱에 반영됐다는 뜻은 아니다.

- 초기 목표: IT·개발 관심 대학생의 교외 활동 맞춤 탐색과 여러 사이트 탐색 부담 감소.
- 탐색 기본 단위: 공고 카드 → 프로그램 카드. 프로그램 상세에 회차·직군별 공고를 둔다. 스크랩은 조직·프로그램 단위다.
- 마감된 프로그램도 노출하되 최근 모집 중인 것을 우선한다. 모집 상태는 선택 직무 기준이다. iOS 종료·디자인만 모집 중이면 iOS 모집 중 프로그램보다 후순위다.
- 자격 미충족도 제외하지 않고 안내한다. 자격 정보는 필요할 때 입력받고 재사용·수정한다.
- 필터 결과 0건이면 선택 조건의 유효한 부분 조합과 개수를 제안한다.
- ELK 활용 승인 후 PostgreSQL 대안도 논의했으나 최종 기술 스택은 미정이다. 결과 수의 프로그램/공고 단위, 최근의 날짜 기준 등 미확정 상세를 임의로 결정하지 않는다.

정본은 기획 checkout의 [재기획 명세](../../dearby-product-replanning/docs/product/replanning-2026-09.md)와 [Socrates 기록](../../dearby-product-replanning/.symposium/scratch/socrates.md)다. 링크는 로컬 하위 checkout을 가리키며 main에 자동 동기화된 문서가 아니다. 이번 읽기 시 정본 말미에는 선택 직무별 모집 판정을 미확정으로 표현한 부분이 남아 있었다. 해당 항목은 이번 사용자 공유의 승인 내용을 우선하여 기록하며 담당 checkout을 메인에서 직접 수정하지 않는다.

후속 구현 지시가 오면 당시 최신 정본을 다시 확인하고 프로그램·회차/직군 공고 관계, 선택 직무별 상태·정렬, 스크랩 ID/기존 저장값, 필터 조합·개수, 자격 정보 처리에 필요한 공통 계약을 먼저 조율한다. 이후 API·iOS·Android의 범위와 담당 하위 세션을 배정한다. 이는 향후 조율 항목이며 신규 기능·스키마·마이그레이션 승인이 아니다.

## 주의와 다음 행동

사용자 root Xcode project/scheme 및 ArchitectureTests/.swiftpm 변경은 보존하며 커밋 제외. 원래 Xcode diff와 동일함을 확인했다. 하위 checkout은 .git/info/exclude에 로컬 제외하여 root에 실수로 추가하지 않는다. 강제push/보호규칙 우회 없음. Android 변경 후 Ponytail·정확성 검토 완료. worker_done succeeded/retain·delivery ack 완료, reclaimable0 확인. PR34도 이전 작업에서 최종 CI 성공 후 병합했고 main을 동기화했다. 후속 구현 요청 전에는 새 앱·공통 계약 변경에 착수하지 않는다. 제품 재기획 대화는 해당 세션에서 계속한다.

완료 세션: Android da438461c84101d66504884333fd17fe21bcb480 clean, 단말/5556 앱 유지. iOS도 retained. 역할 문서의 실행 검증과 한계를 다음 작업의 출발점으로 사용하고 같은 검증을 새 실행으로 복사하지 않는다.
