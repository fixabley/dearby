# 신청 활동·명함 공유·인라인 편집

2026-10-06 사용자 요청과 결정. UI·유저플로우 담당이 iOS·Android(웹은 해당 항목만)를 같은 기준으로 구현하기 위한 정본이다. 담당 경로는 [에이전트 운영](agent-roster.md)을 따른다.

## 결정

- 모바일은 [프로토타입](mobile-ui-prototype.md) 범위를 유지한다. 신청·공유·편집은 예시 데이터와 실행 중 메모리 상태로만 동작하고 서버·로그인·영구 저장을 연결하지 않는다.
- 사용자는 여러 명함을 각각 수정한다. 수정 내용은 그 명함 ID 전체에 반영된다. 배포 1건별 별도 내용은 두지 않는다.
- 수정은 별도 폼 없이 보고 있는 화면에서 바로 한다(인라인 편집).
- 검색은 명함함·신청한 활동·활동 탐색 세 곳에 둔다.
- 웹 비로그인 저장은 공유 정보(어떤 공유로 받았는지, 담긴 활동)까지 저장한다. 서버·계약 변경이 필요해 아래 [웹](#웹)의 순서로 진행한다.
- 새 화면에는 승인된 시안이 없다. 기존 토큰·컴포넌트로 구성하고, 통합 전에 iOS·Android 캡처를 사용자에게 보여 확인받는다.

## 공통 예시 모델

두 플랫폼은 같은 ID와 값을 쓴다. 예시 데이터는 각 플랫폼 `entities`(플로우 담당)에 둔다.

| 개념 | 필드 | 예시 초기값 |
| --- | --- | --- |
| 신청 | `activityId`, `appliedAt` | 개발자 컨퍼런스 1건 신청 상태로 시작 |
| 공유 | `cardId`, `activityIds`(신청한 활동 중 0개 이상), `sharedAt` | 없음 |
| 받은 명함 | 기존 항목 + `activityIds` | 기존 받은 명함마다 0~2개 활동을 지정. 2개 활동에 걸친 명함과 활동이 없는 명함을 각각 1개 이상 둔다 |

신청은 기존 활동 상세의 예시 신청 동작으로 추가·취소한다. 공유에는 신청한 활동만 고를 수 있다. 활동 선택은 참가 인증이 아니라는 기존 계약 의미를 유지한다.

## 화면 흐름

1. **신청한 활동 목록:** `저장` 탭 상단에 `저장한 활동 | 신청한 활동` 전환을 둔다. 신청 목록은 일정순이며 항목을 누르면 활동 상세로 간다. 비어 있으면 탐색으로 가는 안내를 보여 준다.
2. **공유할 때 활동 고르기:** QR의 내 명함 공유와 명함 상세의 `나도 주기` 화면에 신청한 활동 칩을 보여 준다. 기본 선택은 없다. 고른 활동은 QR 화면과 보내기 결과에 표시된다.
3. **명함함 활동별 분류:** 받은 명함 화면에 `전체 | 활동별` 전환을 둔다. 활동별 보기는 활동마다 머리글과 개수를 보여 주고, 여러 활동에 걸친 명함은 각 묶음에 모두 나온다. 활동이 없는 명함은 마지막 `활동 없음` 묶음에 둔다.
4. **검색:** 세 곳 모두 같은 규칙을 쓴다. 공백으로 나눈 단어가 모두 들어 있으면 일치로 보고, 대소문자와 앞뒤 공백은 무시하며 부분 일치를 허용한다. 검색어는 메모리에만 둔다.
   - 명함함: 이름, 소속·직무, 소개, 그룹, 활동 이름
   - 신청한 활동: 활동 이름, 유형, 조직, 상태 문구, 일정(예: `10월`)
   - 활동 탐색: 기존 검색에 조직·분야·일정을 추가하고, 기존 유형 필터와 함께 적용
5. **프로필 작성:** 앱은 지금처럼 예시 프로필로 시작한다. `내 프로필`에 `처음부터 작성`을 두어 빈 프로필의 인라인 편집 상태로 바꾼다.
6. **명함 생성·수정:** 내 명함 목록에서 각 명함을 열어 인라인으로 수정한다. 새 명함은 같은 화면이 빈 명함의 편집 상태로 열린다. 기존 작성 → 미리보기 → 생성 단계와 별도 편집 화면은 이 화면으로 대체한다.

## 인라인 편집 규칙

- 프로필·명함 상세 화면의 `편집`을 누르면 같은 배치에서 글자가 입력 칸으로 바뀐다. 모양과 위치는 유지하고, 입력 가능한 곳은 옅은 배경이나 밑줄로 구분한다.
- 연락처·이력은 그 자리에서 추가·삭제하고, 명함에서는 항목별 공개 여부를 켜고 끈다.
- `완료`를 누르면 메모리에 반영되고 `취소`를 누르면 편집 전 상태로 돌아간다. 이름이 비어 있으면 `완료`를 비활성화하고 이유를 보여 준다. 편집 중에 화면을 벗어나려 하면 변경을 버릴지 묻는다.
- 편집 상태의 전환은 VoiceOver·TalkBack에 알리고, 입력 칸마다 접근성 이름을 둔다. 큰 글씨에서도 잘리지 않아야 한다.
- 기존 명함 수정은 같은 ID를 갱신하고 새 명함만 새 ID를 만든다.

향후 실제 서비스에서는 현재 계약의 "발행 명함은 스냅샷" 정책을 "명함 수정이 받은 사람에게도 반영"으로 바꿔야 한다. 서버 연결 시 계약 변경 대상으로 남긴다.

## 웹

- 검색: 활동 탐색 검색에 위 공통 규칙을 맞춘다. 기존 URL 보존 동작은 유지한다.
- 비로그인 공유 정보 저장: 메인이 먼저 `shared/contracts/native-v1.md`에 공유 기록과 게스트 저장 확장 계약을 정한다. 그다음 API 구현과 웹 `/saved` 활동별 분류를 배정한다. 현재 모바일은 오프라인이라 실제 공유 기록을 만드는 클라이언트가 없다. 웹 기능은 테스트 데이터로 검증하며, 운영 DB 마이그레이션과 배포는 사용자 승인 후 진행한다.

## 담당과 순서

- **UI 담당:** 인라인 편집 입력 칸, 활동 선택 칩 묶음, 개수가 있는 묶음 머리글, 검색 칸, 두 항목 전환을 iOS·Android `shared/ui`·`widgets`에 만든다. 기존 것이 있으면 재사용한다.
- **유저플로우 담당:** 예시 모델·초기값, 위 1~6 흐름, 검색 규칙을 iOS·Android에 구현한다. UI 컴포넌트가 통합되기 전에는 모델·상태·이동부터 진행하고, 통합된 뒤 화면에 연결한다.
- 기능 단위 PR로 나눈다. 각 PR은 두 플랫폼을 함께 바꾸거나, 남은 플랫폼을 [디자인 적용표](../design/mobile-prototype-reference-map.md)에 `미반영`으로 표시한다.
- 검증: iOS UI 테스트·Android 계측 테스트에 위 흐름별 시나리오를 추가하고, 큰 글씨 캡처를 포함한다. 실제로 실행한 검사만 통과로 기록한다.

## UI 컴포넌트 공개 API

2026-10-06 UI 담당 확정. 이름은 두 플랫폼이 같고 모두 각 플랫폼 `shared/ui`에 있다. 상태는 호출하는 화면이 가지고, 컴포넌트는 값과 변경 콜백만 받는다. 시안이 없어 기존 토큰(청록·민트·옅은 배경·보조 글자, 최소 높이 44)으로 구성한다.

| 용도 | iOS (SwiftUI) | Android (Compose) |
| --- | --- | --- |
| 두 항목 전환 | `DearbySegments(labels: [String], selection: Binding<Int>)` 기존 것 재사용 | `DearbySegments(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier)` |
| 묶음 머리글(개수, 접고 펴기) | `DearbySectionHeader(title: String, count: Int, expanded: Bool? = nil, onToggle: () -> Void = {})` | `DearbySectionHeader(title: String, count: Int, modifier, expanded: Boolean? = null, onToggle: () -> Unit = {})` |
| 검색 칸 | `DearbySearchField(prompt: String, text: Binding<String>, identifier: String = "search", expansion: Double = 1, onExpand: () -> Void = {})` | `DearbySearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier, expansion: Float = 1f, onExpand: () -> Unit = {})` |
| 당김으로 검색 펼치기 | 목록 `ScrollView`에 `.dearbySearchReveal($expansion)` | `val reveal = rememberDearbySearchReveal()` → 목록에 `Modifier.nestedScroll(reveal.connection)`, 검색 칸에 `reveal.expansion`·`reveal::expand` |
| 활동 선택 칩 | `DearbyChoiceChips(items: [DearbyChoice], selection: Binding<Set<String>>, label: String)` | `DearbyChoiceChips(items: List<DearbyChoice>, selected: Set<String>, onToggle: (String) -> Unit, label: String, modifier)` |
| 인라인 입력 칸 | `DearbyInlineField(label: String, text: Binding<String>, editing: Bool, prompt: String = "", multiline: Bool = false, font: Font = .body)` | `DearbyInlineField(label: String, value: String, onValueChange: (String) -> Unit, editing: Boolean, modifier, placeholder: String = "", singleLine: Boolean = true, style: TextStyle = bodyLarge)` |
| 함께한 활동 라벨 | `DearbyTogetherActivityLabel(title: String, otherCount: Int = 0)` | `DearbyTogetherActivityLabel(title: String, modifier, otherCount: Int = 0)` |
| 받은 명함 목록 항목(widgets) | `ReceivedCardRow(name: String, job: String, activityTitle: String? = nil, otherActivityCount: Int = 0, open: () -> Void)` (`widgets/identity`) | `ReceivedCardRow(name: String, job: String, open: () -> Unit, modifier, activityTitle: String? = null, otherActivityCount: Int = 0)` (`widgets/card/cardContent`) |
| QR 이미지 | `DearbyQRCode(text: String)` (CoreImage) | `DearbyQrCode(text: String, modifier)` (zxing core 3.5.3) |
| QR 공유 카드(widgets) | `QRShareCard(name:job:url: URL?, errorMessage:retry:activities:selectedActivityIDs:)` — 공유 버튼은 `ShareLink` | `QrShareCard(name, job, url: String?, onShare, modifier, errorMessage, onRetry, activities, selectedActivityIds, onToggleActivity)` — `onShare`에서 화면이 ACTION_SEND chooser를 연다 |
| 스캔 틀(widgets) | `QRScanOverlay(scanFromPhotos:preview:)` | `QrScanOverlay(scanFromPhotos, modifier, preview)` |
| 명함 제작(widgets) | `CardComposer(name:job:introduction:contacts:histories:requiresLogin:publishing:errorMessage:publish:)` + `CardComposerContact`·`CardComposerHistory` | `CardComposer(name, job, introduction, contacts, histories, onNameChange, onJobChange, onIntroductionChange, onContactChange, onHistoryChange, onPublish, modifier, requiresLogin, publishing, errorMessage)` |
| 활동 카드·빠른 신청(widgets) | `ActivityCard(activityID:title:summary:status:dateAndPlace:applyURL:recruitmentEnd:open:)` (`widgets/catalog`) | `ActivityCard(title, summary, status, dateAndPlace, artwork, open, modifier, applyUrl, recruitmentEnd, onApply)` (`widgets/activity/activityCard`) |

- `DearbyChoice`는 `id`·`title`만 가진다. 활동 모델을 `shared`에 들이지 않도록 화면이 활동을 변환해 넘긴다. 칩은 줄바꿈 배치이며 선택은 색과 체크 표시로 함께 구분한다. 읽기 전용 표시는 기존 `DearbyBadge`/`ExampleBadge`를 쓴다.
- 인라인 입력 칸은 읽기·편집에서 같은 여백을 써 위치가 바뀌지 않는다. 편집 중에만 옅은 배경과 밑줄을 보인다. `label`은 입력 칸의 접근성 이름이다. 편집 상태 전환 알림, `완료`/`취소`, 이름이 비었을 때의 이유 문구는 화면(플로우 담당)이 맡는다.
- 묶음 머리글은 "제목, N개"로 읽히는 머리글이다. `expanded`를 주면 누를 때 `onToggle`을 부르는 버튼이 되고 화살표(펼침 아래, 접힘 오른쪽)와 접근성 상태 `펼침`/`접힘`을 보인다. 동작 줄이기에서는 화살표 회전 애니메이션이 없다. 묶음 내용을 숨기는 일과 펼침 상태 보관·검색 시 자동 펼침은 화면이 맡는다. 검색 칸은 내용이 있을 때 `검색어 지우기` 버튼을 보인다. 검색 규칙은 화면이 적용한다.
- 접히는 검색 칸: `expansion` 0~1을 받고, 검색어가 있거나 입력 중이면 컴포넌트가 1로 고정한다. 1 미만에서는 `검색` 버튼으로 읽히는 막대이고, 누르면 `onExpand`를 부르고 펼친 뒤 입력 칸에 초점을 준다. 동작 줄이기(iOS 동작 줄이기, Android 애니메이션 배율 0)에서는 0.5 기준으로 0 또는 1만 쓴다. 두 플랫폼 수치:

  | 값 | 수치 |
  | --- | --- |
  | 막대 높이 → 입력 칸 높이 | 28 → 48 pt/dp (터치 영역 최소 44) |
  | 안내 문구 | `expansion` 0.5부터 나타나 1에서 불투명 |
  | 완전히 펼치는 당김 거리 | 56 pt/dp, 위로 민 거리만큼 같은 비율로 줄어듦 |
  | 손을 뗀 뒤 | 0.5 이상이면 1, 미만이면 0으로 맞춤 |
- 함께한 활동 라벨은 `함께한 활동 · ○○`(2개 이상이면 뒤에 ` 외 N개`)를 확인 아이콘 없이 보여 주고 긴 활동 이름만 말줄임한다. 첫 활동(일정이 가장 이른 것) 선택, 활동이 없을 때 생략, 상세의 전체 목록·이동은 화면이 맡는다. iOS 명함 앞면이 `entities`에 있어 상위 계층 참조를 피하려고 `shared/ui`에 둔다.
- 받은 명함 목록 항목은 이니셜·이름·직무·함께한 활동 라벨·오른쪽 화살표이며 전체가 눌리는 한 줄이다. 화면이 명함 모델을 값으로 바꿔 넘긴다.
- 웹은 같은 모양으로 `SectionHeader({title, count, expanded?, onToggle?, controls?})`(`aria-expanded`)를 `apps/web/src/shared/ui/section-header.tsx`에, `TogetherActivityLabel({title, otherCount})`와 `ReceivedCardRow({href, name, job, activity?})`를 `apps/web/src/widgets/card`에 둔다. `/saved`에 `전체 | 활동별` 전환이 없어 웹 `Segments`는 만들지 않는다.
- QR 화면은 `DearbySegments(['내 코드', '스캔'])` 아래에 QR 공유 카드나 스캔 틀을 둔다. 카드는 호출 측이 넘긴 공유 URL(`https://<웹 origin>/s/<shareId>`) 문자열을 그대로 QR과 글자로 보이며 도메인을 고정하지 않는다. `url`이 없으면 만드는 중, `errorMessage`가 있으면 다시 시도를 보인다. 공유 버튼 하나로 OS 공유 시트를 바로 열고(QR 진입 1 → 공유 2탭), 함께 보낼 활동은 접힌 선택 칩이다. 카메라 미리보기·사진 선택·공유 만들기·로그인은 화면이 맡는다.
- 명함 제작은 한 화면에서 이름·직함·소개를 입력하고 연락처·활동 이력마다 공개 스위치를 고른 뒤 하단의 `명함 발행` 하나로 끝난다. 로그인이 필요하면 버튼이 `로그인하고 명함 발행`으로 바뀌고 '발행할 때만 로그인' 안내를 보인다. 이름이 비면 버튼을 끄고 이유를 보인다. 로그인·발행 요청·오류 문구 결정은 화면이 맡는다.
- 빠른 신청(조사 보고서 A안, 2026-10-06 승인): 활동 카드는 `applyUrl`과 마감일(`recruitmentEndAt`)을 선택 인자로만 받는다. 값을 주면 카드 아래에 `M월 d일 (요일) 마감`(서울 시간, 없으면 `마감일 미확인`)과 외부 링크 아이콘이 있는 44 이상 높이의 `공식 사이트에서 신청`을 붙인다. 보이는 조건(`participationType == registration`, 모집 중, `applicationUrl` 있음)은 호출 측이 카탈로그 값으로 판정한다. 카드 본문을 누르면 지금처럼 상세로 간다. 웹은 `widgets/activity/activity-card.tsx`의 같은 인자(`applyUrl`, `recruitmentEndAt`)를 쓴다.
