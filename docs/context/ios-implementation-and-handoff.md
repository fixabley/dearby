# IOS 구현 인계

2026-09-15 PR #12까지 main 병합 완료. 이전 담당 세션/worktree는 정리했다.

SwiftUI @Observable, NoticeModel/OrganizationModel 분리, ViewModel→State 조립, 인메모리→SwiftData→mock cache-aside.
FSD 하향 의존, Widgets/<domain>/<widget> 안의 UI/State/ViewModel 동위 배치, Shared/UI 네이티브 디자인 시스템을 유지한다.

최신 기능: 환경설정의 `겹치는 일정 확인하기`, 최초 1회 켜기/나중에 설명, boolean 설정 영속화, 일시적 개인 busy 조회. 제목/장소는 표시하지 않고 서버로 전송하지 않는다. 활동/바쁜 시간은 배색 블록, 실제 교집합만 점선·경고·한국어 시간 요약이다. 날짜·요일 다음 줄에 시간을 표시하고 한국 시간 중복 라벨은 생략한다.

검증 정본: apps/ios/docs/evidence/issue-10/README.md 및 PAGING.md. 실제 개인 일정/OS 권한/저장 검증은 #13, 접근성/제스처는 #14, iOS 조회 진단 한계는 #15에서 추적한다. 과거 상세 인계는 archive/2026-09-15-before-pr-cleanup/ 참조.

## 카드 일정·장소 줄 컴포넌트 — 완료 (2026-09-15)

시작·종료는 원본별 State 배열→VStack 별도 Text, 장소는 원본 필드 배열→공백 단위 Layout으로 표시한다. AX 크기는 일정명을 위로 배치하고 긴 단일 토큰만 내부 줄바꿈한다. 기존 native 아이콘·URL host·정확한 phase/venue 지도 callback·State-only UI·카드 스크롤 유지; 카드 밖은 수정하지 않았다.

이번 State 두 sample 및 필드/경계 fixture, FSD 102파일, 최종 Simulator build_run_sim PASS(PID 51021). 첫 카드·다중/누락 일정 및 AX5 일정/장소 캡처를 직접 확인했고 large로 복원했다. 환경 종료/설치 오류 후 데이터 보존 재실행 성공. [검증·화면·한계](../../apps/ios/docs/evidence/card-lines/README.md).

2026-09-15 연결 확인: checkout dearby-ios-run, terminal term_f1400b32-1536-4726-90b4-67439ba8fafa, dispatch ctx_d9f7b9eeb75d, Simulator A617D464-41FC-4C33-A3AC-A109D5C9F054. 기반 2d59853은 main 통합 완료라는 지시; 이번 기능 한 커밋을 메인 검토·통합에 전달한다. push/PR 없음, 앱 실행 유지. 기존 미커밋 실행 기록은 그대로 보존한다.

## 완료: Harmonize 구조 테스트 구현 (2026-09-15)

현재 checkout은 dearby-ios-architecture-tests, branch fixabley/dearby-ios-architecture-tests. 시작 main 9eeb383에서 원격 PR17 포함 b0c76a1로 fast-forward했고 context/workstreams 충돌은 양쪽 기록을 보존해 해결했다. 현재 역할은 apps/ios 구조 테스트이며 위 카드 기능 기록의 이전 세션 연결을 재사용하지 않는다.

사용자 승인 범위는 독립 macOS SwiftPM + Harmonize 구조 테스트, 기존 FSD guard 전부 보존이다. apps/ios 테스트만 변경했고 앱 소스/프로젝트/런타임 의존성 변경은 없다. Harmonize 1.2.1 및 SwiftSyntax 601.0.1을 고정하고 Package.resolved를 포함한다. 단일 실행은 `bash apps/ios/tests/run_architecture.sh`; 규칙·범위·한계 정본은 [ArchitectureTests README](../../apps/ios/tests/ArchitectureTests/README.md).

이번 실제 Xcode26.6/Swift6.3.3 실행: 기존 Python 102파일/전체 fixtures, Swift Testing 6테스트(규칙 12사례 및 View 선언 3사례) 통과. Harmonize-only 공개 UI 위반과 Python-only 상향 의존을 임시 production 파일로 주입해 각각 exit1, 제거 후 /tmp cwd에서 통합 runner exit0 확인했다. 원래 102개 소스 SHA256 모두 동일하다. [검증 보고](../../apps/ios/tests/ArchitectureTests/VERIFICATION.md).

남은 작업은 메인 담당 통합 PR/CI(Xcode16.4 Swift6.1 baseline) 검증 및 리뷰다. 해당 Xcode는 로컬에 없어 통과로 주장하지 않는다. 조율에 따라 worker는 기능 commit/push만 제공하고 메인이 CI commit과 함께 하나의 PR을 만든다. 앱 runtime/build/OS 검증은 이번 테스트 도입 범위가 아니다.

## 진행: 승인 FSD 전체 iOS 이행 (2026-09-16)

현재 main f8f648c 안전 통합 완료. 새 설계는 Widget UI/Model 세그먼트, 자기VM 허용, Entity UI 자기Model 허용, 모든 하위레이어 참조/명시적 공개진입점을 사용한다. 기존 flat 규칙보다 우선한다. 공통 AST 검사·cache facade 선행 작업과 전체 standalone/구조 검증 완료; 다음은 카드→즐겨찾기→상세 행동→App 기능별 이행. 실시간 상세는 apps/ios/docs/FSD-MIGRATION.md. 로컬 기능commit만 제공하고 root가 원격 PR/통합 담당.
