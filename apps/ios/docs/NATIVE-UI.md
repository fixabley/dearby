# iOS native UI — Refs #2

Xcode 26.6 (17F113), Swift 6, deployment iOS 26.0; 검증 runtime iOS 26.5. SDK/라이브러리는 추가하거나 올리지 않는다. Figma 원본은 확인하지 않았으며 replica를 주장하지 않는다.

## shared/UI 공개 진입점 (단일 모듈 internal)

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

카드는 공고 State를 조합하는 도메인 widget에 유지하며 제목 Text도 카드 내부에 남는다. 세로 paging + doubletap은 핵심 발견 경험이므로 유지한다. 카드의 24pt rounded content surface는 콘텐츠 묶음이며 glass를 씌우지 않는다. 기본 크기에서 한 페이지, 접근성 큰 글자에서는 카드가 자연 높이로 늘어나고 페이지 snap을 생략하여 스크롤로 버튼까지 접근한다. DiscoveryPagingBehavior는 기본 크기에서 시스템 PagingScrollTargetBehavior에 위임하고 접근성 크기에서는 target을 수정하지 않는다. 저장 버튼은 기존 멱등 save와 단일 즐겨찾기 상태를 유지한다.

`NativeComponentsPreview.swift`는 light/dark/AX5 + disabled/empty fixtures를 제공한다. Shared UI에는 source/model/store/VM을 주입하지 않는다.

## 공식 근거 (2026-09-14 확인)

- [Apple — Adopting Liquid Glass](https://developer.apple.com/documentation/technologyoverviews/adopting-liquid-glass): 표준 컨트롤의 시스템 외형을 우선, custom glass는 절제.
- [Apple — Typography](https://developer.apple.com/design/human-interface-guidelines/typography): 시스템 text style과 Dynamic Type.
- [Apple — Materials](https://developer.apple.com/design/human-interface-guidelines/materials): 콘텐츠와 제어/탐색 material의 역할 구분.

최신 문서에 보이는 Xcode 27 API는 현 Xcode 26.6 범위를 넘으므로 도입하지 않았다.

## 상세 적용

상세는 `List(.insetGrouped)`의 기본 `Section`으로 요약/조직/참여/기간·장소/혜택/출처를 묶는다. 중복 NoticeDetailField/NoticeIdentityFact를 제거하고 Shared InformationRow를 직접 사용한다. 긴 정보는 무제한 줄바꿈하며 섹션 label은 시스템 header다. Calendar/Map는 기존 콜백을 그대로 쓰는 borderless Button이고 label 44pt를 보장한다(이 스타일에는 bordered 내부 padding이 없음). 원문은 native Link다. 별도 section wrapper·도메인 제목 wrapper는 없다.

## 즐겨찾기 적용

FavoriteOrganizationCard는 도메인 widget 안에서 native Section을 조합한다. 조직별 header, 연결 공고별 NavigationLink, 독립 destructive 삭제 row를 사용해 이전 한 row 안의 복수 액션을 분리한다. 상위 조직은 InformationRow, 연결 공고 없음은 StatusMessage다. 삭제 접근성 label에 조직명을 포함하고 목록의 시스템 separator/inset/scroll을 그대로 따른다. widget View/State/VM 동위 구조는 유지한다.

## 접근성 보완과 검증

AX5에서 실제 스크롤로 카드 아래 저장/상세 버튼에 도달하고 같은 공고 상세 시트를 열었다. 카드의 긴 제목 preview를 추가했다. 즐겨찾기 고정 하단 안내에는 semantic canvas 배경을 두어 스크롤 텍스트와 겹쳐 읽히지 않게 한다. 신청/활동 캘린더 버튼은 VoiceOver label로 구별한다. [검증·before/after 증거](VERIFICATION-ISSUE-02.md)를 참고한다.

## 일정 메타데이터와 아이콘 액션 (후속)

`MetadataRow(systemImage:text:accessibilityText:)`는 shared/UI의 도메인 없는 Label/subheadline 조합이다. 아이콘은 secondary, 값은 primary이며 큰 글자에서 줄바꿈한다. 카드 신청/장소 및 상세 신청/각 단계 기간·장소에서 사용한다. 날짜는 shared/Lib `CompactPeriod.period(start:end:timezone:fallback:)`가 원시 문자열만 받아 Foundation으로 표시한다. 같은 날은 종료 시각만, 연도 경계는 양쪽 연도, 0이 아닌 초는 초까지 유지한다. 시간대가 없거나 해석할 수 없는 timestamp는 원문을 유지하고 종료 미확인은 명시한다. 이 함수는 표시 전용이며 날짜 저장 정책을 대체하지 않는다.

상세 State는 원래 schedules 순서로 이름/기간/해당 단계 장소를 투영한다. 신청은 별도 Section, 활동은 각 단계의 굵은 headline → calendar 기간 → mappin 장소 순서다. 온라인 접두사는 중복하지 않으며 온라인 단계에 오프라인 장소를 붙이지 않는다. 장소 전체 원문/불확실성은 활동 footer 내용에 남고 신청 원문 요약도 유지한다. 각 단계의 calendar callback은 같은 원본 배열 index로 연결한다. App의 EventKit/지도 매퍼와 state lifetime은 변경하지 않는다.

식별 가능한 저장/정보/삭제/캘린더/지도/원문 액션은 native `Label.iconOnly`를 사용한다. 텍스트 label은 접근성에 남고 저장 대상 조직명은 카드에 계속 보인다. calendar/map는 44×44pt 이상, 저장/정보는 native large control을 유지한다. 제목은 title2.bold, 단계는 headline, 메타데이터는 subheadline, 원문 조건/보조는 footnote로 구분하며 폰트 고정·축소 제한은 없다. emoji 대신 native SF Symbols를 사용해 Dynamic Type/접근성 및 플랫폼 일관성을 따른다.

공식 참고: [Apple iconOnly](https://developer.apple.com/documentation/swiftui/labelstyle/icononly), [Apple DateFormatter](https://developer.apple.com/documentation/foundation/dateformatter) (2026-09-14 Markdown 원문 확인). 새로운 SDK/의존성은 없다.

날짜만 주어진 값은 종일로 단정하지 않고 `시간 미확인`을 표시한다. 역전 기간/알 수 없는 시간대는 원문과 확인 필요 표시로 남긴다. phase에 장소가 없으면 다른 단계 장소를 복사하지 않고 `장소 미확인`으로 표시하며 원래 전체 장소 설명은 별도 보조 텍스트에 보존한다.

## Calendar를 참고한 상세 기간·장소 위계

Shared `EventTimeRows(lines:note:)`는 범용 날짜/시간/시작·종료 label만 렌더링한다. 날짜 body.medium, 시간 subheadline.secondary, 시간대 footnote.secondary로 분리한다. shared/Lib `EventPeriodPresentation(startsAt:startsOn:endsAt:endsOn:timezone:)`는 같은날 날짜를 한 번 표시하고, 다른날은 시작/종료 두 블록을 만든다. 날짜만 있으면 시간 미확인, 종료가 없으면 종료 미확인이다. conflicting on/at·역전·invalid·지원하지 않는 timestamp 정밀도/offset 형식은 원문 필드와 확인 메시지를 표시하며 값을 임의 정정하지 않는다. 기존 카드 CompactPeriod나 calendar mapper에는 사용하지 않는다.

Shared `LocationInformation(name:detail:action:)`는 장소명 body와 상세 subheadline.secondary, caller의 map action 슬롯을 받는다. 기본 크기에서는 장소 옆, 접근성 글자 크기에서는 장소 아래에 action을 두어 텍스트 너비를 확보한다. 화면 전용 `NoticePlaceState`는 명시적인 마지막 괄호의 층·호 또는 공백으로 분리된 숫자 호실만 보조 줄로 옮긴다. 건물코드·예정·불명확한 괄호는 장소명에 그대로 남기고 주소는 별도 문자열일 때 보조 줄에 넣는다. 원래 venue는 State에 함께 보존하여 동일 지도 callback에 전달한다. 주소를 새로 추론하거나 geocode하지 않는다.

신청/phase 제목은 기존 View의 headline, calendar action은 해당 제목 오른쪽이다. 원문 신청/장소 문구는 native DisclosureGroup에서 그대로 볼 수 있어 기본 화면의 동일 날짜·장소 반복을 줄인다. 온라인은 온라인 표시와 검증된 HTTP(S) URL Link를 유지하며 URL 전체는 접근성 label로 제공한다. Shared는 원본 notice/organization/Feature를 참조하지 않는다.

참고한 실제 자료는 [Apple iPhone Guide](https://support.apple.com/en-lamr/guide/iphone/iph3d110f84/ios)의 일간 보기 이미지와 별도의 제목/장소·영상통화/시작·종료 입력 설명, 이전 EventKit editor 캡처다. 이 자료는 날짜·시간/장소를 나누는 근거이며 이번 상세 레이아웃 자체는 Dearby의 디자인 해석이다. Apple Calendar 일정 상세 replica나 실제 앱 상세 화면 관찰을 주장하지 않는다. [최신 검증](evidence/issue-02/calendar-detail/README.md).

### 사용자 Calendar 이미지: 한국어 기간과 링크

`EventPeriodPresentation`은 한국어 요일/오전·오후와 부터·까지를 표시한다. `ExternalLinkCard(url:label:)`는 이미 안전성을 검사한 URL과 의미 label만 받아 도메인과 native `Link` 열기를 표시한다. AnyLayout은 접근성 글자 크기에서 세로 조합을 사용한다. 링크가 없으면 상위 View가 생략하며 앱 신청URL/온라인URL은 State/VM에서 조립한다. 원본 URL을 미리 fetch하거나 공유하지 않는다. [이번 근거와 검증](evidence/issue-02/calendar-reference/README.md).

### 표시 전용 일간 타임라인

| API | Native mapping / 사용처 |
| --- | --- |
| `EventDayTimeline(interval:title:)` | 신청/각 활동에서 사용; local State 날짜 선택, native ScrollViewReader + ScrollView, 320pt 기본·AX 최대520pt viewport |
| `EventDaySelector(selection:range:timeZone:calendar:title:previousDate:nextDate:)` | Binding/원시 값만 받는 compact DatePicker와 이전/다음 Button; source-zone, 범위 제한, 큰글자 세로 배치 |
| `EventTimelineGrid(day:title:hourHeight:gutter:)` | 하루 눈금·가로선·accent fill/stroke 블록, system fonts/primary text/separator/background; 정확한 구간 AX label |
| `EventTimelineInterval(start:end:timeZone:)` | 도메인 없는 shared/Lib 표시용 half-open interval; 인접일 Calendar 연산, 해당일 clip과 시간 눈금만 계산 |

SwiftUI에 이 용도의 일정 일간 시간표 control이 없어 좁은 custom grid를 유지한다. native DatePicker의 날짜 탐색이나 내비게이션/전체 달력 앱을 재구현하지 않는다. 상세 List 안의 세로 ScrollView는 사용자가 요청한 bounded 미리보기에 한정하며 header/바깥 여백으로 상세를, 내부 시간표로 하루 전체를 스크롤한다. 일반일은 24시간, DST 전환일은 실제23/25/23.5시간과 offset이 구별된 눈금이다.

같은날 기간 문장은 날짜 한 번, 여러날은 한국어 시작/종료 문장을 줄별로 읽는다. State가 생성한 `EventPeriodPresentation.timeline`은 두 timestamp·timezone·일치·순서가 검증될 때만 존재한다. 날짜만/혼합 정밀도/마감만/invalid/역전/0길이는 정보 문구를 표시하며 종일/길이를 만들지 않는다. 긴 기간 전체 날짜 배열은 만들지 않고 `[start,end)`를 선택일의 실제 자정 경계에서 자른다. 시작일 기본 선택, 끝이 자정이면 다음날 선택 제외다. 최소44pt 블록은 **시각 높이만** 확대하고 정확한 시간 문장·AX label과 확대 안내는 유지한다. 최대글자에서는 gutter 폭 제한과 별도의 전체 일정명으로 가독성을 보완한다. 어떠한 OS 일정 조회/권한/저장·외부 링크 호출도 표시 중 일어나지 않는다.

### 상세 날짜·시간 줄 분리 (2026-09-15)

`EventTimeRows`는 각 시작/종료의 날짜·요일을 body, 다음 줄 자연어 시간 범위를 subheadline으로 표시한다. 같은날 날짜 1회, 다일 각 날짜/시간 대응을 유지하며 큰글자 자연 줄바꿈을 허용한다. 한국 시간 보조라벨만 생략하고 Asia/Seoul 계산 및 export, 다른 시간대와 DST offset, 미확인/원문 fallback은 유지한다. 사용자 제공 `/tmp/dearby-period-linebreak-reference.png`를 직접 확인했고 관련 `run_detail_presentations.sh`로 변환 계약을 검증한다.

### #10 · 환경설정과 기기 일정 비교

| 진입점 | 입력/출력 | Native 매핑/사용처 |
| --- | --- | --- |
| CalendarConnectionControl | CalendarConnectionState, isEnabled, toggle/settings 콜백 | native Toggle·Button·ProgressView; pages/settings/Form 한 섹션 |
| EventDayTimeline | 기존 interval/title + BusyTimeDisplay, onSelectDay, onRetryBusy (기본 hidden/nil) | native 날짜선택/스크롤; 실제 활동에만 busy, 신청은 hidden |
| BusyTimeStatusView / SummaryView | 표시상태, source TimeZone, retry | Label·DisclosureGroup·SF Symbol; 실제 intersection 문장, 실패와 빈 성공 구분, 짧은 구간 전체 시각 읽기 |
| BusyTimeInterval / Display | start/end Date, status, intervals/overlaps | OS/공고 도메인 없는 half-open union/clip 값 |

SwiftUI는 활동+기기 busy 비교용 timeline View를 제공하지 않아 좁은 custom grid를 유지한다. 활동은 accentColor, 바쁜 시간은 UIKit adaptive systemTeal의 낮은 opacity를 사용한다. 시간 gutter는 유지하며 본문 같은 시간축의 실제 교집합만 primary dashed stroke와 exclamationmark.triangle.fill을 표시한다. 겹치는 시간 문장은 busy 전체가 아닌 동일 intersection 배열을 포맷한다. 예: 활동14–16 / busy15–17 → 겹침15–16. 접점은 warning/점선 없음. 활동명은 왼쪽, busy 명칭/시각은 오른쪽으로 분리해 가림을 줄인다. 짧은 busy/교집합은 높이를 확대하지 않고 인접 DisclosureGroup 및 AX가 정확한 전체 시각을 제공한다. 기존 activity 최소 시각높이를 교집합 계산에 사용하지 않는다. 큰글자 전체 활동명은 시간표 위에도 제공한다.

환경설정의 겹치는 일정 확인하기는 전역 설정이며 최초 설명은 App의 native alert 켜기/나중에로 한 번 표시한다. 재실행 유지 boolean만 저장하고 개인 일정 구간은 상세의 임시 세션에 남는다. Shared는 권한/저장 API에 접근하지 않는다. [최신 실행 증거와 Apple 근거](evidence/issue-10/README.md).

피드 큰글자에서는 카드 안 native ScrollView로 긴 내용을 읽고 바깥 native viewAligned(alwaysByOne)의 고정 한 장 이동을 유지한다. 제스처 경합을 피하는 이전/다음 공고 native 버튼은 AX 크기에서만 제공한다. [실제검증](evidence/issue-10/PAGING.md).
