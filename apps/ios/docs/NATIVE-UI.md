# iOS native UI — Refs #2

Xcode 26.6 (17F113), Swift 6, deployment iOS 26.0; 검증 runtime iOS 26.5. SDK/라이브러리는 추가하거나 올리지 않는다. Figma 원본은 확인하지 않았으며 replica를 주장하지 않는다.

## Shared/UI 공개 진입점 (단일 모듈 internal)

| API | Native mapping | 실제 사용 / 책임 |
| --- | --- | --- |
| `NativeSpacing.compact/related/content/section` | 4/8/16/24pt content spacing | 카드/정보행의 관계 간격. 시스템 List/Section inset은 덮어쓰지 않음 |
| `NativeSurface.canvas/content` | systemGroupedBackground / secondarySystemGroupedBackground | 탐색 canvas와 카드. light/dark/contrast 적응 |
| `InformationRow(title:value:systemImage:)` | Text + optional Label, subheadline secondary / body primary | 카드 fact, 상세 정보. 긴 값은 세로 줄바꿈, VoiceOver label/value 결합 |
| `StatusMessage(text:systemImage:)` | Label, footnote secondary | 비차단 설명/확인 상태. 색만으로 상태를 전달하지 않음 |
| `PrimaryButton(action:label:)` | Button + borderedProminent | 카드의 핵심 저장. native controlSize(.large), 여러 줄 |
| `SecondaryButton(action:label:)` | Button + bordered | 카드의 상세 진입. 동일 터치/줄바꿈 규칙 |

폰트 token으로 SwiftUI Font를 다시 포장하지 않는다. 화면 제목은 title2.bold, 조직/섹션 강조는 headline, 정보 값은 body, 보조 문구는 subheadline/footnote/caption의 시스템 text style을 직접 사용한다. 고정 point font, custom font, 텍스트 축소로 큰 글자를 회피하지 않는다. 색은 primary/secondary/tint 및 semantic background이며 앱 기존 AccentColor를 보존한다.

## 기본 컴포넌트 사용 규칙

`List` + `Section`은 정보 구획/목록의 기본이며 section wrapper는 만들지 않는다. `ContentUnavailableView`는 전체 빈 상태, `ProgressView`는 실제 비동기 로딩이 있을 때만 직접 사용한다. 이번 동기 데이터 흐름에 새 로딩 상태를 추가하지 않는다. `NavigationStack`, `TabView`, sheet, toolbar는 기존 시스템 구현을 유지한다. 파괴 동작은 `Button(role: .destructive)`, URL은 `Link`, 목적지 이동은 `NavigationLink`를 직접 사용한다. 화면별 액션에는 의미 있는 label과 최소 44pt 터치 높이를 준다.

카드는 공고 State를 조합하는 도메인 widget에 유지하며 제목 Text도 카드 내부에 남는다. 세로 paging + doubletap은 핵심 발견 경험이므로 유지한다. 카드의 24pt rounded content surface는 콘텐츠 묶음이며 glass를 씌우지 않는다. 기본 크기에서 한 페이지, 접근성 큰 글자에서는 카드가 자연 높이로 늘어나 스크롤로 버튼까지 접근한다. 저장 버튼은 기존 멱등 save와 단일 즐겨찾기 상태를 유지한다.

`NativeComponentsPreview.swift`는 light/dark/AX5 + disabled/empty fixtures를 제공한다. Shared UI에는 source/model/store/VM을 주입하지 않는다.

## 공식 근거 (2026-09-14 확인)

- [Apple — Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass): 표준 컨트롤의 시스템 외형을 우선, custom glass는 절제.
- [Apple — Typography](https://developer.apple.com/design/human-interface-guidelines/typography): 시스템 text style과 Dynamic Type.
- [Apple — Materials](https://developer.apple.com/design/human-interface-guidelines/materials): 콘텐츠와 제어/탐색 material의 역할 구분.

최신 문서에 보이는 Xcode 27 API는 현 Xcode 26.6 범위를 넘으므로 도입하지 않았다.
