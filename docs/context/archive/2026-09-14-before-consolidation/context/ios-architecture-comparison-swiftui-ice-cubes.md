# Dearby와 현대 SwiftUI 아키텍처 비교

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

## 후속 결정: SwiftData 도입

사용자가 이후 SwiftData 및 외부 fallback을 승인했다. 현재 iOS 구현은 인메모리 캐시 → SwiftData → 외부 mock-data 순서이며, 아래 비교의 “SwiftData 미사용”은 도입 전 판단 기록이다. 최신 구현은 iOS ARCHITECTURE.md와 coordinator의 SwiftData 작업 절을 따른다.

2026-09-14, 완료된 NoticeModel/OrganizationModel 리팩터링 기준. Apple 공식 자료와 Ice Cubes 실제 소스를 확인했다. 이 문서는 비교 판단이며 SwiftData·새 SDK 도입 승인을 의미하지 않는다. 관련 구현은 iOS PR #5, Android PR #4에 반영되었다.

## 결론

현재 FSD + ViewModel/State 구조는 Observation과 양립한다. FSD는 파일 배치와 의존 방향, ViewModel은 화면 로직의 책임, Observation은 변경 감지 방식이다. 바꿀 핵심은 패턴의 이름보다 상태 소유권과 불필요한 중복이다.

| 항목 | 현재 Dearby 방향 | 참고 자료와 비교·판단 |
| --- | --- | --- |
| 상태 | 공유 즐겨찾기 원본 하나, 화면 State는 표시 값 | Observation 방향과 일치. 저장 목록과 State.saved를 각각 수정하면 안 된다. |
| ViewModel | 공고·조직을 조회하고 카드/상세 State를 조합 | 실제 조합 책임이 있어 유지할 가치가 있다. 단순 라벨·버튼까지 VM을 만들 필요는 없다. |
| 표시 State | View에 State와 콜백 주입 | 독립 렌더링·미리보기·테스트에 유리하다. 반면 모든 필드의 재선언·매핑 비용이 생긴다. 편집 화면까지 원본 전체를 복제하는 규칙으로 확대하지 않는다. |
| 저장소 | 공고와 조직의 source/cache 분리, App이 공유 | 향후 API 교체 및 독립 테스트에 이유가 있다. 모든 로컬 조회에 Repository가 필수라는 뜻은 아니다. |
| 모듈 경계 | 단일 모듈 안 FSD 디렉터리·구조 검사 | Ice Cubes의 Swift Package 분리는 컴파일 의존 경계를 더 강하게 제공한다. 현재 규모에서 즉시 모듈을 늘리는 비용은 별도 판단한다. |

## Observation 적용 기준

관찰할 변경을 소유하는 객체에 @Observable을 사용한다. View가 소유하면 @State, 받은 객체를 읽으면 일반 프로퍼티, 양방향 바인딩이면 @Bindable, 계층 공유라면 @Environment를 사용한다. @Environment는 전역 singleton을 강제하지 않으며 앱 루트에서 주입할 수 있다. 순수 표현 View에는 State·콜백을 유지할 수 있다.

NoticeModel·OrganizationModel 같은 불변 값이나 스스로 가변 상태가 없는 조합 객체까지 @Observable을 붙일 필요는 없다. 현재 iOS 구현의 NoticeCardViewModel은 공유 FavoriteOrganizations를 읽어 State.saved를 계산한다. 이 읽기가 실제 View 관찰 범위에서 수행되는지 테스트해야 한다. 로딩·오류·선택 등 VM 자체 상태가 추가되면 해당 VM을 @Observable로 만들 이유가 생긴다.

[Apple 모델 데이터 관리](https://developer.apple.com/documentation/swiftui/managing-model-data-in-your-app)

## Ice Cubes에서 참고할 점과 그대로 따르지 않을 점

README는 대부분 MVVM과 Swift Package 구성을 설명한다. 실제 StatusRowViewModel은 @MainActor @Observable이며 로딩·번역·펼침·네트워크 동작을 관리한다. 이 클래스는 별도 StatusRowState로 모든 프로퍼티를 옮기지 않는다. UI에 필요한 상태를 직접 관찰하도록 연결하므로 계층과 매핑이 줄어든다.

동시에 이 구현에는 일부 singleton 접근과 ViewBuilder 표현 코드가 포함되어 있다. Dearby가 승인한 순수 UI·주입·독립 테스트 경계를 그대로 대체할 근거는 아니다. 참고할 핵심은 ViewModel의 실제 책임이며 파일 모양의 복제는 아니다.

[Ice Cubes README](https://github.com/Dimillian/IceCubesApp) · [StatusRowViewModel](https://github.com/Dimillian/IceCubesApp/blob/main/Packages/StatusKit/Sources/StatusKit/Row/StatusRowViewModel.swift)

## SwiftData와 도구 체인

@Query 우선 권고는 SwiftData를 SwiftUI View에서 조회하는 상황이다. Dearby는 현재 번들 원본과 별도 저장소를 사용한다. SwiftData를 새로 도입해 Repository를 없애는 것은 별도의 저장 방식 변경이다. 이후 도입한다면 기존 캐시와 SwiftData의 관리 객체를 중복 소유하지 않도록 재검토한다.

로컬 확인값은 Xcode 26.6(Build17F113), Simulator SDK26.5, 최소 iOS26이다. WWDC26의 @State 매크로 지연 초기화는 새 Xcode27 도구 체인으로 빌드할 때 적용되는 개선이며 iOS17+ backport 설명과 구분해야 한다. 공식 문서 메타데이터에서 ResultsObserver·HistoryObserver는 iOS27 도입으로 확인했다. 현 SDK/최소 버전에서 무조건 사용하지 않는다.

[WWDC26 SwiftUI](https://developer.apple.com/videos/play/wwdc2026/269/) · [WWDC26 SwiftData](https://developer.apple.com/videos/play/wwdc2026/274/) · [ResultsObserver](https://developer.apple.com/documentation/swiftdata/resultsobserver) · [HistoryObserver](https://developer.apple.com/documentation/swiftdata/historyobserver)
