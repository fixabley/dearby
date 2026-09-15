# ANDROID 구현 인계

2026-09-15 PR #11까지 main 병합 완료. 이전 담당 세션/worktree는 정리했다.

## 카드 줄 컴포넌트 — 완료 (2026-09-15)

- 원본 시작/종료→dateLines→Column의 별도 Text, 원본 venue.name/address·온라인/host→별도 장소 컴포넌트로 분리했다. 공백 토큰 배치와 긴 단일 토큰 soft-wrap을 사용하며 State-only·native icon·정확한 지도 callback을 유지한다.
- 이번 관련 JVM6, FSD101/self-test24, Debug/계측APK, 카드계측2(최종31.179초) 통과. 첫 카드·큰 글자/다중/누락 일정·긴 장소 PNG 직접 확인. 상세 증거·긴 토큰 fallback 한계는 [카드 줄 검증](../../apps/android/docs/card-schedules/CARD-LINES.md)에만 기록한다. 이전 일정/URL/아이콘 검증은 [기존 카드 기록](../../apps/android/docs/card-schedules/VERIFICATION.md)이다.
- 런타임 확인: dearby-android-run, worker `term_491b5f4b-c686-451a-8b2c-d2956eaa2270`, coordinator `term_f2158e63-1b6c-4da2-8567-3a1ed58c229e`, task `task_408c41375c69` / dispatch `ctx_a9734ee105e4`. 5556과 앱 실행 유지, 재개 시 실시간 재확인.
- 기준8547adb는 메인 통합 완료로 전달받았다. 이번 기능을 한 커밋으로 묶고 push/PR 없이 메인 검토·통합을 기다린다. 작업 전 미커밋 실행 기록은 작업 트리에 그대로 보존하며 기능 커밋에서 제외한다. #13/#14/#15 전체 후속 범위는 미착수다.

Compose 네이티브 상태 관리, NoticeModel/OrganizationModel 분리, ViewModel→State 조립, 인메모리→Room→mock cache-aside.
FSD 하향 의존, Widgets/<domain>/<widget> 안의 UI/State/ViewModel 동위 배치, Shared/UI 네이티브 디자인 시스템을 유지한다.

최신 기능: 환경설정의 `겹치는 일정 확인하기`, 최초 1회 켜기/나중에 설명, boolean 설정 영속화, 일시적 개인 busy 조회. 제목/장소는 표시하지 않고 서버로 전송하지 않는다. 활동/바쁜 시간은 배색 블록, 실제 교집합만 점선·경고·한국어 시간 요약이다. 날짜·요일 다음 줄에 시간을 표시하고 한국 시간 중복 라벨은 생략한다.

검증 정본: apps/android/docs/calendar-busy/VERIFICATION.md. 실제 개인 일정/OS 권한/저장 검증은 #13, 접근성/제스처는 #14, iOS 조회 진단 한계는 #15에서 추적한다. 과거 상세 인계는 archive/2026-09-15-before-pr-cleanup/ 참조.
