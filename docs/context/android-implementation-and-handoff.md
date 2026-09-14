# Android — 구현과 인계

2026-09-14: Issue #2 구현 중, branch `fixabley/dearby-android`, main 기준 `f963401`.

Shared/UI에 Material3 native 색·타이포·shape·최소 간격과 범용 정보/섹션/상태를 구성한다. 기본 Button/OutlinedButton은 wrapper 없이 직접 사용한다. Notice/Organization 모델·Room·favorites 소유권/키/ID·지도/캘린더 흐름·JSON 불변, widget의 flat View/State/VM 유지.

현재 기존 UI/코드 확인 및 baseline Debug/계측 APK 빌드 성공. 전용 `Dearby_Issue2_Test` AVD를 새로 만들어 5556에서 실행 중이며 사용자5554는 조작하지 않는다. before/after 증거는 `apps/android/docs/design-system/evidence/`, 검증/최종 PR은 진행 후 기록한다.
