# 구조 개선 통합 완료와 네이티브 UI 후속 인계

2026-09-14 (Asia/Seoul). 사용자 요청: “응 정리하고 워크트리도 정리해줘”.

## 현재 상태

- PR #3(공통 설계), #6(공고 계약), #4(Android), #5(iOS)를 이 순서로 main에 merge했다. squash/이력 재작성 없이 기능별 커밋을 보존했다.
- main: `f963401a83cd5db03e2f8685eb542bcf7340f21d`. root는 main이며 origin/main과 동일하다. open PR 없음.
- #1 완료로 닫음. #2는 열려 있으며 디자인 구현은 시작하지 않았다.
- Git/Orca의 iOS·Android·API 하위 worktree를 제거했고 root만 남았다. iOS/API worker-release는 transcript captured 및 terminal closed를 확인했다. Android는 reused/external terminal로 release가 retained를 반환하여, 사용자 명시적 worktree 정리 요청에 따라 해당 workspace의 terminal close --all(1 stopped)을 수행했다.
- 병합된 작업용 로컬·원격 브랜치도 정리했다. API에는 origin/main 대비 추가 커밋이 없었다.
- root의 기존 미커밋 .gitignore, README.md, Symposium 및 AGENTS.md/context/workstreams 파일은 보존한다. 로컬 인계 기록을 이번에 자동 공개·커밋한 것은 아니다.

## 보존 위치

`/Users/jominjun/Documents/dearby-archive/20260914-225950`

- repository.bundle: 정리 전 Git refs와 커밋. 이후 설계 보정 c0f4a58도 통합 main에서 보존된다.
- ios-worktree.tar / android-worktree.tar / api-worktree.tar: 각 checkout 전체. 미추적·ignored·IDE 설정·빌드 결과 포함. 미추적 파일 내용은 원본과 대조했다.
- worker-notes/<role>/: 담당별 AGENTS·context·workstreams를 읽기 쉽게 풀어 둔 복사본.
- root-local/: 정리 전 root 로컬 문서와 설정. root-local.patch 및 상태 스냅샷도 보존.
- 역할별 removal.json: Orca 제거 영수증. issue-1/2-before.json: 이슈 수정 전 원문.

과거 .git 파일은 제거된 worktree 연결을 가리킨다. tar를 새 저장소로 그대로 실행하지 말고 최신 main에서 새 worktree를 만든 뒤 필요한 로컬 설정/기록만 복원한다.

## 이번 검증

임시 Git merge-tree로 네 PR 충돌 없음과 통합된 양 앱 tree가 각 검증된 platform head와 동일함을 먼저 확인했다. 실제 GitHub 병합 후 main 전체 tree도 예상 tree와 동일했다.

main에서 공통 Node 테스트 17건, 샘플 동기화, iOS FSD66 및 guard fixtures, Android FSD60 통과. 앱 코드 수정은 없었으며 전체 앱 빌드·기기 테스트를 이번 정리에서 반복하지 않았다.

이전 실제 검증: Android JVM39/계측35(실제 Room5 포함), lint 오류0/경고12 및 APK build. iOS standalone·SwiftData 테스트, simulator build·재실행 smoke. 실행별 범위는 각 앱 ARCHITECTURE.md와 보존된 worker-notes 참조.

iOS 실제 touch swipe/doubletap, 물리 햅틱·전체 접근성, 실제 Calendar Save/동기화 및 외부 지도 내부 화면은 미검증으로 유지한다.

## 다음 작업 #2

- Figma 참고: https://www.figma.com/ko-kr/community/file/1527721578857867021/ios-and-ipados-26
- App Store처럼 익숙한 iOS 26+ 네이티브 표현 요청. 실제 Figma 내용은 아직 확인하지 못했다.
- SwiftUI 기본 컴포넌트/시스템 표현과 Android Compose Material3 관례를 따른다. 두 플랫폼의 외형 일치를 강제하지 않는다.
- 기존 카드 탐색·더블탭 조직 저장·즐겨찾기·상세·지도·캘린더를 보존한다.
- 다음 구현 시작 때 최신 main에서 역할별 이슈 전용 worktree와 Orca 세션을 새로 구성한다. 이 문서 작성 중 새 세션/디자인 구현은 시작하지 않았다.
- Widgets/<Domain>/<Widget> flat View/ViewModel/State, Shared/UI 버튼, 독립 NoticeModel·OrganizationModel 및 L1→SwiftData/Room→mock 경계를 유지한다.

이 체크포인트가 이전 기록의 draft/미병합/retained/기존 terminal handle 안내보다 우선한다.
