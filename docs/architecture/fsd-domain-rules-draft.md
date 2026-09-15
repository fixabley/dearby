# Dearby FSD 설계안

작성: 2026-09-16. 대상: iOS·Android 클라이언트 내부 구조. NestJS 서버와 모노레포 루트 `apps/` 배치는 대상이 아니다.

이 문서는 FSD 문서 비교 후 정리한 **Harmonize 규칙·리팩터링의 목표 기준**이다. 2026-09-16 사용자 정정에 따라 먼저 iOS 검사와 실제 코드에 적용 중이다. 현재 구현이나 Harmonize 규칙이 이미 이 구조를 따른다는 뜻이 아니다. 기존 `native-apps.md` 및 각 앱 ARCHITECTURE.md와 달라지는 정책은 아래에 명시하며, 코드 적용 시 문서·검사를 함께 갱신한다.

## 1. 기본 원칙

FSD의 레이어는 책임과 의존 방향을, 슬라이스는 제품에서의 의미를, 세그먼트는 기술적 역할을 구분한다. 중간 레이어를 반드시 거치는 파이프라인으로 사용하지 않는다.

- 모든 하위 레이어를 참조할 수 있다. 상위 레이어 참조는 금지한다.
- 같은 슬라이스 내부 참조는 허용한다. 같은 레이어의 다른 슬라이스 참조는 기본적으로 금지한다.
- 다른 슬라이스에서는 명시된 공개 진입점만 사용한다.
- 필요한 레이어·슬라이스·세그먼트만 만든다. 하나의 화면 전용 코드를 재사용 계획만으로 하위 레이어에 분산하지 않는다.
- 화면·동작·저장 데이터와 원본 URL/시간대 계약을 유지하는 내부 구조 변경으로 진행한다.

[근거: FSD 개요](https://fsd.how/docs/get-started/overview/), [레이어의 import 규칙](https://fsd.how/docs/reference/layers/#import-rule-on-layers).

## 2. 허용 참조표

| 출발 레이어 | 허용하는 다른 레이어 |
| --- | --- |
| App | Pages, Widgets, Features, Entities, Shared |
| Pages | Widgets, Features, Entities, Shared |
| Widgets | Features, Entities, Shared |
| Features | Entities, Shared |
| Entities | Shared |
| Shared | 없음 |

이 표는 프로젝트 내부 의존성에 대한 것이다. SwiftUI/Foundation, Compose/Kotlin 표준 라이브러리 같은 외부 모듈은 역할별 규칙으로 별도 검사한다. 같은 슬라이스 내부 `ui → model` 또는 `model → api`는 위반이 아니다. 순환 의존은 같은 슬라이스 안에서도 피한다.

App과 Shared는 도메인 슬라이스 없이 목적별 세그먼트를 직접 둔다. 내부 세그먼트 간 참조를 허용하되 Shared에서 상위 도메인을 참조하지 않는다. Entities의 명시적 교차 API(`@x`)는 문서에 존재하지만, Dearby의 Notice/Organization 분리에는 현재 필요하지 않으므로 기본 금지를 유지한다. 예외가 필요하면 관계와 공개 계약을 먼저 정의한다.

## 3. 레이어별 책임

| 레이어 | Dearby의 책임 | 대표 예 |
| --- | --- | --- |
| App | 앱 진입, 라우팅·딥링크, 의존성 생성, 앱 수명과 공통 상태 연결 | Router, AppDependencies, SwiftData/Room 컨테이너 구성 |
| Pages | 하나의 완성된 화면과 그 화면 전용 상태·UI·조립 | 발견, 즐겨찾기 목록, 공고 상세, 환경설정 |
| Widgets | 여러 화면에서 쓰거나 화면 내 독립성이 큰 완성 UI 블록 | 동작하는 공고 카드, 즐겨찾기 조직 카드 |
| Features | 사용자에게 의미 있는 재사용 행동 및 그 UI·상태·API | 조직 저장, 일정 추가, 장소 열기, 겹치는 일정 확인 |
| Entities | 도메인 Model, 조회·저장 경계, 도메인의 재사용 표현 | NoticeModel, NoticeRepository, OrganizationModel, 공고 표현 UI |
| Shared | 도메인 행동이 없는 공통 UI와 범용 기반 | 버튼·타이포·색상·간격, 범용 날짜 계산 |

Features는 모든 함수와 Repository를 모으는 서비스 폴더가 아니다. `공고를 조회하고 캐시한다`는 엔티티 접근 책임이고, `조직을 관심 목록에 저장한다`는 사용자 행동이다. 클래스 이름이 Repository인지보다 어떤 데이터를 소유하고 어떤 행동을 수행하는지로 배치한다.

Widgets가 반드시 Features와 Entities를 모두 참조해야 하는 것은 아니다. 화면 전용 작은 조합은 Pages에 두며, 중간 전달만 하는 Widget은 만들지 않는다. fsd.how는 Widgets 사용을 권장하지 않는다고 설명하고, feature-sliced.design은 재사용되거나 독립성이 큰 UI 블록에 사용하는 기준을 제시한다. Dearby는 후자의 필요성 기준으로 제한적으로 사용한다.

[근거: 레이어별 책임](https://fsd.how/docs/reference/layers/), [Widgets 사용 기준](https://feature-sliced.design/docs/reference/layers#widgets).

## 4. 폴더와 이름

App·Shared를 제외한 레이어는 `레이어/슬라이스/세그먼트`를 기본으로 한다. 슬라이스 이름을 모든 레이어에서 user/notice 같은 명사로 통일하지 않는다.

- Entities: `notice`, `organization`처럼 업무 개념.
- Features: `save-organization`, `add-to-calendar`처럼 사용자 행동.
- Widgets: `notice-card`, `favorite-organization-card`처럼 독립 UI 블록.
- Pages: `discovery`, `favorites`, `notice-detail`, `settings`처럼 화면.
- 세그먼트: `ui`, `model`, `api`, `lib`, `config` 중 필요한 것만 사용한다. 관례는 `apis`가 아닌 `api`다.
- `ui`에는 표시 컴포넌트와 표시 형식, `model`에는 Model·State·ViewModel과 로직, `api`에는 외부 접근 계약·구현을 둔다.
- `lib`는 날짜·문자열 등 구체적인 목적이 있는 코드에만 사용하고 범용 helpers 모음으로 만들지 않는다.
- 슬라이스 그룹은 탐색용으로만 허용한다. 그룹 폴더 안에 형제 슬라이스가 공유하는 코드를 넣지 않는다.

아래는 **논리 트리**다. iOS의 기존 PascalCase 폴더나 Android의 소문자 패키지명과 대응시킨다. 예를 들어 논리 `save-organization`은 iOS `SaveOrganization`, Android `saveorganization`으로 매핑할 수 있다. Android 패키지에는 하이픈을 넣지 않는다. 구현 검사에서는 플랫폼별 실제 경로와 논리 슬라이스의 대응을 명시해 사용한다.

```text
app/
  entrypoint/                  # DearbyApp / MainActivity 진입 연결
  routes/                      # 경로 해석, 화면 목적지와 내비게이션
  providers/                   # 저장소·서비스·공통 상태 생성 및 수명 연결
pages/
  discovery/ui/
  favorites/ui/
  notice-detail/
    ui/
    model/                     # 상세 전용 ViewModel·State가 필요하면 배치
  settings/ui/
widgets/
  notice-card/
    ui/                        # 연결된 공고 카드
    model/                     # NoticeCardViewModel, NoticeCardState
  favorite-organization-card/
    ui/
    model/
features/
  save-organization/
    model/                     # 저장·명시적 삭제 행동과 상태 반영
    ui/                        # 독립적으로 재사용할 행동 UI가 있을 때만
  add-to-calendar/
    model/                     # 일정 초안 생성, 검증
    api/                       # 시스템 캘린더 편집기 연결
  open-location/api/
  check-calendar-overlap/
    model/                     # 조회 상태·취소·겹침 판단
    api/                       # EventKit / CalendarProvider 접근
entities/
  notice/
    model/                     # NoticeModel, NoticeSchedule, NoticeLocation
    api/                       # NoticeRepository, L1/L2/source 어댑터
    ui/                        # 재사용되는 공고 표현과 일정 표시
  organization/
    model/                     # OrganizationModel
    api/                       # 조직 조회·캐시와 경로 해석
    ui/                        # 조직의 순수 표현
  favorite/
    model/                     # 저장된 조직 ID 집합, 데이터 불변식
    api/                       # 영속 저장·복원 계약과 구현
shared/
  ui/                          # 기본 버튼·정보 행·범용 시간축 UI
  config/                      # 테마·색상·타이포·간격
  lib/date/                    # 도메인 정책이 없는 날짜·구간 연산
```

이 트리를 맞추려고 빈 폴더를 만들지 않는다. `favorite` 엔티티 추가는 이 설계의 제안이며 현재 Features/FavoriteOrganization에 있는 데이터와 행동을 분리할 때 필요성을 확인한다. 단순 저장 상태만으로 충분하면 불필요한 Model 타입을 만들지 않는다. 아직 없는 API 서버용 클라이언트도 미리 만들지 않는다.

[근거: 슬라이스·세그먼트와 공개 API](https://fsd.how/docs/reference/slices-segments/).

## 5. 공고 카드의 조립 예시

Entities의 UI는 도메인 Model을 받아도 된다. 화면에 필요한 일부 값만 받는 편이 명확하다면 해당 슬라이스의 작은 표시 타입을 받는다. 원본 Model 전달을 일괄 금지하지 않지만 UI 안에서 Repository 조회·저장·OS 작업을 수행하지 않는다.

| 구성 요소 | 배치 | 입력 / 책임 |
| --- | --- | --- |
| NoticeModel | entities/notice/model | 공고 정보와 조직 ID·역할. 조직 이름·경로를 직접 조회하지 않음 |
| NoticeCardContent | entities/notice/ui | 공고의 표시값 또는 NoticeModel과 이벤트 콜백으로 순수 렌더링 |
| NoticeScheduleContent | entities/notice/ui | 재사용되는 일정의 이름·기간·장소 표현 |
| SaveOrganization | features/save-organization/model | 조직 ID의 멱등 저장·명시적 삭제와 결과 전달 |
| NoticeCardViewModel | widgets/notice-card/model | Notice/Organization 조회 결과와 공유 즐겨찾기를 조합해 State·동작 제공 |
| NoticeCardState | widgets/notice-card/model | 제목·조직명·저장 여부 등 표시값. 저장소나 부수 효과를 담지 않음 |
| NoticeCard | widgets/notice-card/ui | ViewModel의 State를 읽고 Entities UI에 저장·상세 콜백 주입 |
| DiscoveryPage | pages/discovery/ui | 카드 배열·페이징과 화면 조합 |

의도 예시(컴파일 대상이 아닌 의사 코드):

```text
AppDependencies
  ├─ NoticeRepository
  ├─ OrganizationRepository
  ├─ FavoriteRepository / 공유 즐겨찾기 상태
  └─ SaveOrganization

NoticeCardViewModel
  ├─ NoticeModel + OrganizationModel + 공유 즐겨찾기 → NoticeCardState
  └─ save() → SaveOrganization.execute(organizationID)

NoticeCard (Widget)
  └─ NoticeCardContent (Entity UI)
       표시값 = State에서 필요한 값
       onSave = ViewModel.save
       onDetail = App 라우팅에 연결된 콜백
```

도메인 Notice UI에서 Organization 슬라이스를 직접 참조하지 않는다. 조직 이름을 문자열로 전달하거나 두 표현의 조합을 Widget에서 수행한다. NoticeCardContent가 오직 한 곳에서만 쓰이고 분리할 책임도 없다면 억지로 Entities에 추출하지 않고 Widget에 유지한다.

## 6. 상태와 저장소 수명

- 도메인 원본은 `NoticeModel`, `OrganizationModel`, 화면 표시값은 `NoticeCardState` 같은 State 이름을 유지한다.
- ViewModel은 조회 결과 조립·로딩·실패·사용자 동작을 책임질 때 둔다. 단순 전달용 ViewModel을 만들지 않는다.
- Widget의 연결 UI는 자기 슬라이스 ViewModel을 사용할 수 있다. Entity/Shared의 순수 UI는 값·콜백으로 제한한다.
- Pages는 자기 화면 State/ViewModel을 둘 수 있다. Pages에서 하위 Feature나 Entity를 사용하는 것도 허용한다.
- App/providers가 공유 저장소·서비스를 생성하고 수명을 관리한다. App이 하위 레이어를 참조할 수 있으므로 불필요한 중간 전달 계층을 만들지 않는다.
- 공고·조직은 독립된 L1→SwiftData/Room→외부 source(현재 mock) 경계를 유지한다. 영속화 성공 전 캐시 승격 금지, snapshot 교체·오류/미존재 구분을 보존한다.
- 즐겨찾기 상태 소유자는 하나이며 화면별 가변 복사를 만들지 않는다. 추가는 멱등이고 제거는 명시적 동작이다.
- 기기 busy 시간은 임시 메모리에만 보유하고 OFF·종료·권한 철회 시 정리한다. 동의·서버 미전송·원본 URL 메모 계약도 유지한다.
- SwiftUI Observation / Compose 관찰 방식과 OS 수명 처리는 그대로 사용한다. 새 외부 상태 관리나 DI 라이브러리는 도입하지 않는다.

## 7. App과 딥링크

대상은 각 클라이언트의 App/app 레이어다. 저장소 루트 `apps/ios`, `apps/android`, `apps/dearby-api`는 유지한다.

`app/routes`는 외부 URL과 내부 라우트 값을 연결하고 필요한 Page를 생성한다. 공고 조회·즐겨찾기 저장은 직접 구현하지 않고 주입된 하위 서비스에 위임한다. 딥링크 경로와 관계없는 저장소 설정은 `app/providers`, 진입 코드는 `app/entrypoint`에 둔다.

예시 구조:

```text
app/routes/
  AppRoute                     # discovery, favorites, noticeDetail(id), settings
  DeepLinkParser               # URL → 검증된 AppRoute
  AppRouter                    # route 상태와 화면 전환
  notices/NoticeDestination    # noticeDetail → 상세 Page
```

위 라우트는 설계 예시다. 실제 URL scheme·universal/app link 도메인과 경로 계약은 아직 확정하지 않았으므로 새 주소를 등록하거나 동작을 바꾸지 않는다. 도입 시 지원하지 않는 URL·빈 ID·미존재 공고·앱 cold/warm start를 구별해 검증한다. 라우팅 파일이 늘어날 때 경로별 하위 폴더를 사용하며, 작은 라우터를 경로당 파일로 기계적으로 분리하지 않는다.

## 8. 아키텍처 검사에 반영할 규칙

| 검사 | 허용 / 위반 예 |
| --- | --- |
| 레이어 방향 | Pages→Entities/Shared 허용, Entities→Features 금지 |
| 슬라이스 독립 | notice-card 내부 ui→model 허용, notice-card→다른 Widget 내부 금지 |
| 폴더 배치 | Pages도 slice/ui 등 사용, App·Shared에 domain slice 강제 금지 |
| 순수 표현 경계 | Entity UI→자기 Model 허용, Entity/Shared UI→Repository·OS side effect 금지 |
| 연결 UI 경계 | Widget UI→자기 ViewModel/Feature 진입점 허용, 다른 슬라이스 내부 API 접근 금지 |
| 원본 Model 독립 | NoticeModel→조직 ID 허용, Organization 조회·SwiftData/Room 의존 금지 |
| 공개 진입점 | 상위에서 공개 계약 사용 허용, storage record/codec 등 내부 구현 접근 금지 |
| 검사 범위 | 현재 checkout의 프로덕션 소스만 검사. 빈 결과·레이어 누락·다른 worktree 혼입은 실패 |

Swift 단일 앱 모듈에서는 폴더 간 의존성이 `import` 문에 나타나지 않는다. Harmonize 구문 검사와 현재 lexical 참조 검사를 조합하되, 별칭·타입 추론·매크로·동적 호출까지 완전히 해석한다고 주장하지 않는다. 슬라이스 공개 계약은 Swift `public` 접근 수준과 별개이며, 단일 모듈에서는 internal 선언도 공개 진입점 목록에 포함될 수 있다. Android에도 같은 논리 정책을 적용하고 플랫폼별 실제 경로에 맞춰 검사한다.

정상·위반 fixture에는 현재 금지됐다가 허용되는 사례도 넣는다. 기존 guard의 UI raw Model/VM 일괄 금지와 Widgets 동위 파일 강제는 새 범위로 대체하고, 저장·권한·데이터 호환성에 관한 기존 검사는 보존한다. 규칙 변경 전에 기존 테스트가 어떤 정책을 보장하는지 매핑한다.

## 9. 현재 구조에서의 변경 범위와 진행 순서

| 현재 | 설계안 |
| --- | --- |
| 모든 하위 레이어 참조 가능 | 유지. 인접 1~2계층 제한은 도입하지 않음 |
| Widgets/Domain/Widget에 UI·State·VM 동위 배치 | 기본은 Widget slice/ui·model. 기존 동위 배치 규칙을 변경하는 제안 |
| Entities의 공고·조직 Repository | 소유 데이터에 맞는 Entity에 유지. 일괄 Features 이동 없음 |
| 모든 렌더링 UI의 Model/VM 참조 금지 | Entity UI는 자기 Model 허용, 연결 Widget/Page UI는 자기 VM 허용 |
| Shared UI + 기반 코드 | 유지하되 범용 날짜 연산과 공고/캘린더 행동 정책을 구분 |
| App의 진입·OS 어댑터·공급자 혼재 | routes/entrypoint/providers로 정리하고 행동 어댑터는 해당 Feature에 배치 |
| Pages/Slice/UI·Model | 유지. Pages 세그먼트 제거는 하지 않음 |

적용은 기능별로 나눈다: 공고 카드 → 즐겨찾기 조직 카드 → 상세 일정/지도/캘린더 → 앱 라우팅·설정. 각 기능 PR에 필요한 코드 이동·상태 연결·규칙/fixture·문서를 함께 넣는다. 여러 기능이 공유하는 규칙 변경은 명시적 선행 PR로 분리할 수 있지만 미이전 코드 때문에 CI를 전체 비활성화하지 않는다. 이전/새 경계를 경로별로 임시 매핑하고 이전이 완료되면 그 매핑을 제거한다.

완료 기준은 두 앱 빌드, 아키텍처 검사, 해당 기능의 기존 동작·저장 복원·화면 간 상태 동기화 검증이다. 파일만 옮기는 경우와 사용자 흐름이 바뀌는 경우의 검증 범위를 구별한다.

현재 iOS Harmonize 규칙과 실제 코드의 적용·회귀 검증을 진행한다. Android 적용 완료를 의미하지 않는다. 기능별 검증과 PR 검토 후 사용자 변경 제안을 같은 방식으로 반영하며, 테스트 통과를 사용자 만족의 대리 기준으로 삼지 않는다.
