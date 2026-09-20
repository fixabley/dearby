# 현재 작업과 결정

2026-09-20: 사용자가 재평가 후보의 실제 변경과 병합, 보류 항목까지 구현하도록 승인했다. iOS 작업 진행 중이며 Android/API는 범위 밖이다. 검토 정본은 [재평가 보고서](../../architecture/reviews/2026-09-20-design-rules-reassessment.md). [이전 인계](archive/2026-09-20-before-design-simplification.md)는 이력이다.

## 구현 범위

미사용 카드/상세 표시 필드, 내부 전용 export 정리. Shared 공개 디자인 UI/토큰 직접 접근의 좁은 예외와 표시 UI 소유 재배치. NoticeDetailsButton은 Widget 로컬의 의미있는 컴포넌트로 유지. 추가 승인된 보류 항목은 State 보조타입 접미사/VM class 강제 재평가, Content 파일명 대신 명시적 순수 UI 경계, detailPage 전달 factory 제거와 route의 필요한 조립 의존 예외다. 전 하위 레이어를 일괄 개방하지 않고 저장소/OS 직접 접근 금지·공개 경계를 보존한다. 어떤 구체 방식이든 코드·검사·회귀·문서를 함께 변경한다.

## 소유권과 병합

Root refactor/ios-design-simplification는 공통 정책·통합·GitHub 병합 담당. 사용자 Xcode project/scheme 및 ArchitectureTests/.swiftpm 변경 보존·커밋 제외. iOS 기존 checkout dearby-ios-architecture-tests에서 feat/ios-design-simplification 신규 브랜치로 구현한다. 기존 검토 문서는 보존하며 기능별 커밋에 반영한다.

Orca Run run_58f7d03f4deb / Task task_5680a3ed1580 / Dispatch ctx_77f89952ed32 / terminal term_13ba3b4a-9172-4a99-bff8-485358963ea0. 준비·입력·실행 시작 확인. 완료된 검토 Task와 다르다. Runtime f311bec3-cb3a-46db-b79e-2dbf2412ee06.

origin 재확인/fetch 완료. 기존 PR27~31 의존 스택을 통합할 권한을 사용자 병합 요청에 따라 적용한다. PR27~31 모두 각각 필수 검사 두 개 성공·CLEAN 확인 후 순서대로 main에 merge했다(origin/main 54038d2). root 작업 브랜치에도 충돌 없이 통합했다. 신규 표시 필드/export 정리 PR #32도 architecture·Simulator CI 성공 후 병합했다(origin/main 2362917). 구조 계약/컴포넌트 이동은 후속 PR로 통합 중이며 공개 Shared 디자인 예외·class/struct VM·명시적 pure 계약을 검사로 확인한다. 후속 변경도 보호규칙/CI 확인 후 병합한다. 강제 push/보호규칙 우회 없음.

## 완료 기준

기능별 커밋 및 변경 후 Ponytail 검토, standalone/busy/detail/architecture/production gate/strict lint/Simulator build, 가능한 UI smoke. 기존 테스트 결과를 이번 검증으로 기록하지 않는다. 아직 구현/검증/최종 병합 진행 중이다.
