# ANDROID 구현 인계

2026-09-15 PR #11까지 main 병합 완료. 이전 담당 세션/worktree는 정리했다.

## 카드 일정 변경 — 완료 (2026-09-15 16:16 KST)

- Android 카드에 신청 및 모든 schedule의 일정명/세로 구분선/달력 아이콘·날짜/위치 아이콘·장소를 표시하고 유효 좌표의 지도 아이콘 버튼을 기존 App geo 콜백에 연결했다. 날짜·누락·온라인·phase별 장소 규칙과 큰 글자 스크롤을 유지하며 상세는 수정하지 않았다.
- 이번 검증: JVM78, 관련 계측14 및 캡처 보강 계측2, FSD100/self-test24, Debug·계측 APK·Lint 오류0/경고13 통과. 실제 지도 버튼 → Google Maps 좌표/라벨 핀 및 Dearby 복귀 확인. 상세 증거·명령·한계는 [카드 검증 문서](../../apps/android/docs/card-schedules/VERIFICATION.md)에만 기록한다.
- 런타임 확인: dearby-android-run, worker term_a3414243-5c9d-4233-a9a8-326d677a7354, coordinator term_f2158e63-1b6c-4da2-8567-3a1ed58c229e, dispatch ctx_59656bb2959b / task_e3c752cbeb7b. 전용 Dearby_Android_Run/5556과 앱을 유지한다. 재개 시 실시간 상태를 다시 확인한다.
- 카드 일정은 `9d033b8`로 커밋했다. 이후 사용자 추가 요청으로 URL을 상세와 같은 host 규칙으로 줄이고 이모지를 네이티브 vector 아이콘으로 교체했으며 JVM79·카드계측2·FSD·APK·Lint 및 최종 화면을 다시 확인했다(URL 시점 16:19 KST, PID7199; 최종 네이티브 아이콘 계측2·화면 재확인 16:23 KST, PID7535). 후속 URL·아이콘 변경은 별도 카드 기능 커밋으로 묶으며 push/PR은 하지 않는다. 작업 전 미커밋 실행 기록은 작업 트리에 보존하고 기능 커밋에 넣지 않는다. 다음 행동은 메인 세션의 검토·통합이며 #13/#14/#15 전체 후속 범위는 미착수다.

Compose 네이티브 상태 관리, NoticeModel/OrganizationModel 분리, ViewModel→State 조립, 인메모리→Room→mock cache-aside.
FSD 하향 의존, Widgets/<domain>/<widget> 안의 UI/State/ViewModel 동위 배치, Shared/UI 네이티브 디자인 시스템을 유지한다.

최신 기능: 환경설정의 `겹치는 일정 확인하기`, 최초 1회 켜기/나중에 설명, boolean 설정 영속화, 일시적 개인 busy 조회. 제목/장소는 표시하지 않고 서버로 전송하지 않는다. 활동/바쁜 시간은 배색 블록, 실제 교집합만 점선·경고·한국어 시간 요약이다. 날짜·요일 다음 줄에 시간을 표시하고 한국 시간 중복 라벨은 생략한다.

검증 정본: apps/android/docs/calendar-busy/VERIFICATION.md. 실제 개인 일정/OS 권한/저장 검증은 #13, 접근성/제스처는 #14, iOS 조회 진단 한계는 #15에서 추적한다. 과거 상세 인계는 archive/2026-09-15-before-pr-cleanup/ 참조.
