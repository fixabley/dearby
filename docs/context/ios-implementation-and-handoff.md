# IOS 구현 인계

2026-09-15 PR #12까지 main 병합 완료. 이전 담당 세션/worktree는 정리했다.

SwiftUI @Observable, NoticeModel/OrganizationModel 분리, ViewModel→State 조립, 인메모리→SwiftData→mock cache-aside.
FSD 하향 의존, Widgets/<domain>/<widget> 안의 UI/State/ViewModel 동위 배치, Shared/UI 네이티브 디자인 시스템을 유지한다.

최신 기능: 환경설정의 `겹치는 일정 확인하기`, 최초 1회 켜기/나중에 설명, boolean 설정 영속화, 일시적 개인 busy 조회. 제목/장소는 표시하지 않고 서버로 전송하지 않는다. 활동/바쁜 시간은 배색 블록, 실제 교집합만 점선·경고·한국어 시간 요약이다. 날짜·요일 다음 줄에 시간을 표시하고 한국 시간 중복 라벨은 생략한다.

검증 정본: apps/ios/docs/evidence/issue-10/README.md 및 PAGING.md. 실제 개인 일정/OS 권한/저장 검증은 #13, 접근성/제스처는 #14, iOS 조회 진단 한계는 #15에서 추적한다. 과거 상세 인계는 archive/2026-09-15-before-pr-cleanup/ 참조.

## 카드 URL host·네이티브 아이콘 표시 후속 — 완료 (2026-09-15)

카드 일정 기능 fdcfca3은 main 통합 완료라는 조율 지시를 바탕으로 표시만 수정했다. 신청 URL·온라인 URL·submissionLocations의 전체 웹 URL만 host로 축약하고 주소/자유텍스트와 모델 원문을 보존한다. State/콜백/지도/날짜 레이아웃은 유지한다. 사용자 정정으로 날짜·위치·지도 이모지를 SF Symbols로 교체하고 아이콘 열·44pt 지도 터치영역·접근성 이름을 유지했다.

이번 실행: 관련 State 두 sample+URL/주소 fixture 및 FSD 통과, Simulator build_run_sim 성공(PID 42661). 첫 카드 도메인과 다중 온라인 일정 화면 확인. 온라인 URL이 있는 화면은 번들에 없으므로 fixture로 검증했다. [명령·화면·한계](../../apps/ios/docs/evidence/card-url-host/README.md).

checkout dearby-ios-run, Simulator A617D464-41FC-4C33-A3AC-A109D5C9F054, terminal term_abceb608-7e86-4722-bd18-0712d2285816, dispatch ctx_810fc77755e2(2026-09-15 확인). 기존 미커밋 실행 기록 보존, 후속 한 커밋으로 메인 검토·통합 대기, push/PR 없음. 이전 카드 일정·지도·AX 검증은 [이전 증거](../../apps/ios/docs/evidence/card-schedules/README.md)이며 이번에 반복하지 않았다.
