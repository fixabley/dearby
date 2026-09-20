# 기존 설계 규칙 재평가 — Ponytail 관점

2026-09-20. 사용자는 과거 제안도 근거를 다시 묻고, 효과가 없는 규칙은 완화하는 방식에 동의했다. 검토 기준은 줄 수보다 기능·데이터 보존·상태 수명·이해 비용이다. 이번 문서는 현재 iOS 코드/검사에 대한 재평가이며 아래 대안을 앱 코드나 실행 검사에 적용했다는 뜻이 아니다. Android/API의 구현을 동일하다고 가정하지 않는다.

후속 승인: 사용자 요청으로 후보와 보류 항목을 iOS에 구현했다. PR32는 병합, 나머지 구조/배치 변경은 후속 PR 통합 중이며 아래는 승인 전 검토 근거를 보존한다. Android도 같은 기준으로 구현 중이다. 최신 결과는 역할 컨텍스트와 앱 DESIGN-SIMPLIFICATION.md를 따른다.

## 유지할 결과 계약

| 계약 | 필요한 이유 | 보존할 검증 |
| --- | --- | --- |
| 독립 공고/조직 원본, ID로 연결 | 조직 이름/경로의 복제와 서로 다른 갱신 시점 방지 | 원본 조합, 조직 누락/순환, snapshot 교체 |
| 즐겨찾기 원본 하나 | 카드/목록/열린 상세가 서로 다른 저장 상태를 갖지 않게 함 | 저장/삭제 즉시 관찰, 멱등 추가, 명시적 삭제/복원 |
| 캐시 저장 성공 후 게시·교체 실패 시 기존 데이터 보존 | 화면만 새 데이터거나 디스크만 비워지는 상태 방지 | L1/L2/source 및 transaction rollback |
| 권한·취소·화면 수명·개인 결과 폐기 | background/상세 종료/늦은 응답에서 개인 일정이 다시 나타나지 않게 함 | busy-calendar 동의·취소·generation·알림 회귀 |
| 네이티브 동작과 접근성 | 카드 paging, 큰 글씨, 원본 URL 캘린더, 정확한 장소 등 사용자가 정한 기능 | 관련 standalone/detail 회귀와 UI 확인 |
| 상향 참조/도메인 순환 방지·명시적 공개 경계 | 화면 변경이 원본 도메인으로 번지는 결합 억제 | production boundary 검사 및 실제 위반 probe |

이 계약을 구현하는 특정 클래스/파일명이 영구적으로 고정된다는 뜻은 아니다. 더 간단한 구현도 같은 검증을 만족하면 평가할 수 있다. 단일 구현체 Repository와 작은 Session은 그 자체로 삭제 근거가 아니다.

## 완화 후보와 코드 근거

### 1. Shared 디자인 시스템 직접 사용을 허용하는 좁은 거리 예외

- 현재 규칙: `apps/ios/tests/ArchitectureTests/Tests/DearbyArchitectureTests/FSDBoundaries.swift:90,104`는 두 레이어보다 먼 모든 하향 참조를 막는다.
- 실제 비용: `features/saveOrganization/ui/SavedOrganizationList.swift:4-20`은 행동 없이 빈 목록/저장 위치 안내를 그리지만 Feature에 위치한다. `pages/favorites/ui/FavoriteListView.swift:13`의 표시 조각이며 Shared spacing/surface 접근이 필요하다. `features/saveOrganization/ui/NoticeIdentityView.swift:3-25`도 공고/조직 표시만 담당하고 주석은 NoticeDetail 소유라고 설명한다. 배치가 역할보다 접근 가능한 레이어에 좌우되고 있다.
- 후보: 기존 거리 제한을 전면 폐기하기 전에 Pages/Widgets→Shared의 공개 디자인 컴포넌트/토큰을 허용한다. 저장소·OS/네트워크 API까지 허용하지 않는다. 표시 UI를 의미 있는 소유 슬라이스에 둘 수 있게 한다.
- 보존: 형제/상향 금지, 공개 목록, 순수 표시 UI의 부수 효과 금지. 정확히 허용하는 Shared 진입점과 금지해야 할 storage/API 참조를 함께 테스트한다. 관련 화면의 빈/실패/큰글씨 상태와 접근성을 검증한다.

### 2. 파일 접미사가 순수 UI 여부를 결정하는 규칙 축소

- 현재: `FSDBoundaries.swift:19`는 모든 `*Content.swift`를 순수 UI로 취급한다. `:97-115`의 안전 규칙이 파일 이름에 의존한다.
- 비용: 이름 변경이 실제 책임 변경 없이 검사 적용 범위를 바꾼다. 반대로 어떤 폴더의 Content라도 자동으로 같은 제약을 받는다.
- 후보: Entity/Shared UI 경로의 순수성은 유지하고, Widget의 표시/연결 UI는 명시적 역할 또는 좁게 정의한 경로 기준으로 구분한다. 별도 marker/설정이 과도해지면 기존 단순 기준 유지도 가능하다.
- 보존: Repository/OS/네트워크 직접 접근 거부, 연결 Widget의 VM 관찰 허용. 이름 변경으로 우회되는 사례와 정상 연결 UI 사례를 회귀에 포함해야 한다. 즉시 삭제할 규칙이 아니라 비용 대비 대안 검토 대상이다.

### 3. State/ViewModel의 역할보다 강한 파일 강제 완화

- 현재: `ArchitectureRules.swift:47-70`은 State의 struct/model 위치/파일명 일치 및 같은 파일 선언의 State 접미사, ViewModel의 class/model 위치/파일명 일치를 강제한다.
- 유지할 이유: State 값과 동작 소유자를 구분하고 검색/탐색을 쉽게 만든다.
- 재평가 부분: `:58-59`에서 State 파일의 모든 보조 타입까지 State 접미사를 요구하는 것, `:62-64`에서 상태를 소유하지 않는 변환기에도 ViewModel 이름만으로 class를 강제하는 것. 이름이 역할을 대신해 추상화를 늘릴 수 있다.
- 후보: State/Model 용어는 유지하되 보조 enum 등은 의미에 맞게 이름 붙일 여지를 둔다. 순수 변환은 함수/값 타입으로 둘 수 있고 불필요한 ViewModel을 만들지 않는다. 현재 이 접미사 규칙 때문에 별도 파일이 늘어났다는 근거는 확인하지 못했으므로 즉시 변경 우선순위는 낮다. 기존 관찰 객체를 일괄 값 타입으로 바꾸지 않는다.
- 보존: shared state 복제 금지, body I/O 금지, SwiftUI Observation 수명. fixtures를 새 의미에 맞춰 교체하며 중요한 검사를 없애지 않는다.

### 4. 사용하지 않는 표시 필드 제거

- `widgets/noticeCard/model/NoticeCardState.swift:7-8`의 applicationSummary/locationSummary는 현재 카드 렌더링에서 읽지 않는다. VM 생성과 preview 및 applicationSummary를 복사 비교하는 테스트가 남아 있다. 실제 카드 일정 표시는 schedules를 사용하며 `:13`의 applicationPeriod도 production 소비자가 없다.
- 후보: 읽지 않는 카드 표시 필드와 생성 인자를 제거한다. 원본 NoticeModel의 기간/장소나 상세/캘린더 정보는 삭제하지 않는다. 이름이 같은 다른 모델 필드를 일괄 삭제하지 않는다.
- 상세의 `NoticeDetailState.swift:7,16-18`에 남은 descriptionProvenance/applicationSummary/scheduleSummaries/applicationPeriod도 같은 종류의 후보다. 원본 provenance는 NoticeStorageCodec에서 사용하므로 원본 값까지 지우면 안 된다.
- 검증: production/preview caller 컴파일, 카드·상세 일정/장소 표시와 ViewModel 회귀, lint/구조검사. 테스트는 복사 필드 존재 대신 실제 표시 계약을 검증하도록 조정한다.

## 담당 검토의 추가 후보와 조율 판단

- `architecture/public-api.json:3,42,91`: NoticeClassificationView/CalendarEventEditor/SettingsView는 확인한 caller가 같은 슬라이스 내부이므로 공개 목록에서만 제외할 후보다. 타입과 화면은 유지한다. 적용 시 production graph와 private-reference fixture를 실행한다. 타입 추론으로 외부에 노출되는 다른 항목까지 문자열 검색만으로 삭제하지 않는다.
- `app/providers/AppComposition.swift:10-12`: detailPage는 Page 생성을 전달만 한다. 제거는 App→Feature preferences 조립 규칙의 별도 조정이 필요하다. Shared만 허용하는 좁은 대안으로는 해결되지 않으므로 이번 우선 변경 대상으로 넣지 않는다.
- `features/openNoticeDetails/ui/NoticeDetailsButton.swift:4-13`: Feature에 독립 행동 정책이 없으므로 Shared 접근 완화 후 Widget 로컬 UI로 이동하는 후보다. worker는 인라인을 제안했으나 root는 문구·AX identifier·명명된 표시 책임을 보존하는 로컬 컴포넌트 대안도 타당하다고 판단했다. 컴포넌트 자체를 반드시 없애는 결론은 채택하지 않는다.
- worker checkout의 AGENTS.md에는 과거 모든 하위 레이어 허용 문구가 남아 있다. root 최신 AGENTS와 양쪽 실행 검사는 두 계층 제한이다. 이는 worktree 문서 동기화 차이이며 이번 사용자 승인을 모든 하위 참조 허용으로 해석하지 않는다. 다음 구현 배정 때 최신 공통 정책을 전달해야 한다.

## 현재 유지할 수단

public-api.json은 단일 Swift 모듈의 슬라이스 내부 접근을 보완하므로 유지한다. 파일별 접근 수준의 public/internal과 다른 문제다. Shared UI의 public 금지는 패키지 분리 전까지 당장 비용 증거가 작아 우선 변경하지 않는다. 재사용 횟수 하나만으로 컴포넌트를 합치지 않는다. NoticeDetailsButton은 문구/아이콘/접근성 식별자를, NoticeCardBody는 레이아웃/큰글씨/더블탭을 소유하므로 단순 전달만 한다고 평가하지 않는다.

## 적용 순서 제안

미사용 카드/상세 표시 필드 및 내부 전용 export 정리 → Shared 디자인 진입점의 좁은 예외와 표시 UI 소유 재배치 → 이름/파일 규칙의 실제 불편 사례별 조정. 예외를 우회용 래퍼나 alias로 구현하지 않는다. 각 변경은 해당 기능 코드·규칙·회귀·문서를 함께 다룬다. 줄 수 절감은 미측정이며 임의 수치를 쓰지 않는다.

## 이번 검토의 한계

소스/호출 위치를 읽은 검토다. 앱 코드·테스트 규칙 변경과 새 빌드/회귀/성능 측정은 하지 않았다. 실행 규칙은 현행을 유지한다. 플랫폼 담당의 독립 코드/호출 검토를 교차 확인했고 역할 인계 두 파일의 diff를 root에 통합했다. task_ba39e8fd4fba / ctx_41ab5059821a succeeded·retained·delivery ack. 새 코드 검증 없이 문서 diff 검사만 통과했다. 상세 검토는 docs/context/ios-implementation-and-handoff.md의 2026-09-20 단락이다. 후보의 줄 수 감소는 측정하지 않았다.
