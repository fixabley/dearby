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
