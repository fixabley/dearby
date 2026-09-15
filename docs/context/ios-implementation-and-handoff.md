# IOS 구현 인계

2026-09-15 PR #12까지 main 병합 완료. 이전 담당 세션/worktree는 정리했다.

SwiftUI @Observable, NoticeModel/OrganizationModel 분리, ViewModel→State 조립, 인메모리→SwiftData→mock cache-aside.
FSD 하향 의존, Widgets/<domain>/<widget> 안의 UI/State/ViewModel 동위 배치, Shared/UI 네이티브 디자인 시스템을 유지한다.

최신 기능: 환경설정의 `겹치는 일정 확인하기`, 최초 1회 켜기/나중에 설명, boolean 설정 영속화, 일시적 개인 busy 조회. 제목/장소는 표시하지 않고 서버로 전송하지 않는다. 활동/바쁜 시간은 배색 블록, 실제 교집합만 점선·경고·한국어 시간 요약이다. 날짜·요일 다음 줄에 시간을 표시하고 한국 시간 중복 라벨은 생략한다.

검증 정본: apps/ios/docs/evidence/issue-10/README.md 및 PAGING.md. 실제 개인 일정/OS 권한/저장 검증은 #13, 접근성/제스처는 #14, iOS 조회 진단 한계는 #15에서 추적한다. 과거 상세 인계는 archive/2026-09-15-before-pr-cleanup/ 참조.

## 카드 일정·위치 작업 — 완료 (2026-09-15)

신청 기간과 모든 schedule을 일정명/구분선/📅 기간·📍 위치로 조합하고 유효 좌표의 🗺️ 버튼을 기존 App 지도 어댑터로 연결했다. 한국 시간 접미사 생략, 다른 유효 시간대 1회, 날짜/종료/위치 누락 표시를 유지한다. compact는 정보를 숨기지 않으며 AX·낮은 viewport는 카드 전체 스크롤을 쓴다. 상세 표시·저장소·공통/다른 플랫폼은 변경하지 않았다.

전체 standalone 및 마지막 State 재컴파일/두 sample·FSD 101파일 통과, 최종 Simulator build_run_sim 성공(07:12:48Z, PID 38072). 실제 paging 1→4·일정 스크롤·AX5·상세·지도 목적지 핀 확인. 실제 doubletap 입력은 창 포커스 오류로 미실행, 작은 화면 compact 런타임/VoiceOver/실기기도 미검증이다. [이번 증거와 한계](../../apps/ios/docs/evidence/card-schedules/README.md)를 따른다.

checkout dearby-ios-run, 전용 Simulator A617D464-41FC-4C33-A3AC-A109D5C9F054, terminal term_a43ee181-c2d1-4763-9b01-dbada287dfaa, dispatch ctx_1d94ed0fa101(확인 2026-09-15). 앱 실행 유지, 기존 미커밋 실행 기록 보존, push/PR 없음. 다음 행동은 메인 세션의 기능 커밋 검토·통합이다.
