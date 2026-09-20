# FSD 기반 네이티브 앱 구조

사용자는 #1의 기존 구조 리팩터링을 FSD(Feature-Sliced Design) 기반으로 구체화하도록 요청했다. 승인된 Seed v2의 화면·동작·저장 호환성, 네이티브 상태 관리, 새 외부 상태관리/DI 라이브러리·빌드 모듈 제외는 유지한다. UI 외형 정비 #2는 별도다.

[FSD 레이어](https://feature-sliced.design/docs/reference/layers)와 [슬라이스·세그먼트](https://feature-sliced.design/docs/reference/slices-segments)의 책임·의존 방향을 단일 Swift/Kotlin 앱에 적용한다. 다음 배치는 Dearby의 현재 기능 규모에 맞춘 설계이며 프레임워크 공식 템플릿이 아니다.

## 2026-09-20 설계 단순화 승인

이 항목은 아래 과거 두 계층 제한의 일부를 대체한다. 기본 레이어 방향과 같은 슬라이스/공개 경계를 유지하며 Pages/Widgets가 공개 Shared UI·디자인 토큰을 직접 사용하는 좁은 예외를 둔다. Shared api/lib 전체나 저장·OS·네트워크 접근의 허가는 아니다. 상세 Route에서 앱 소유 CalendarPreferences를 Page에 전달하는 조립은 허용하고, 직접 저장소/OS 동작은 금지한다. 전달만 하는 detailPage factory를 제거할 수 있게 하되 providers의 생성·공유 수명 역할은 유지한다.

파일명이 Content로 끝나는지 대신 명시적 순수 표시 컴포넌트 계약을 사용한다. Entity/Shared UI의 기본 순수성은 유지한다. State 파일의 보조 enum/타입은 의미있는 이름을 사용할 수 있고, ViewModel의 값/참조 선택은 상태 소유와 Observation 수명에 따라 결정한다. 미사용 화면 투영 필드는 원본 정보를 보존하며 제거하고, 같은 슬라이스 내부에서만 쓰는 타입은 공개 목록에서 제외한다. 표시 전용 UI는 실제 소유 Page/Widget에 배치한다.

사용자는 보류 항목을 포함한 변경·병합을 승인했다. 구체 코드·검사·회귀는 함께 변경하며 현재 구현/완료 여부는 iOS ARCHITECTURE와 역할 인계를 따른다. [재평가 근거](reviews/2026-09-20-design-rules-reassessment.md). Android/API는 이번 구현 범위가 아니다.

## 2026-09-16 Harmonize 적용 기준

[FSD 규칙안](fsd-domain-rules-draft.md)을 iOS Harmonize 검사와 실제 코드에 적용한다. 직접 하위 두 레이어와 동일 슬라이스 내부 참조를 허용하고, 상향·세 레이어 이상 건너뛰기·동일 레이어 다른 슬라이스 내부 접근을 금지한다. app/providers의 의존성 생성·수명 관리만 더 아래 레이어 조립을 허용한다. 이 최신 정책은 iOS에 적용했으며 Android 구현 완료를 뜻하지 않는다. iOS app은 entrypoint/routes/providers 등 목적별 세그먼트, pages/widgets는 슬라이스 아래 ui/model 등 역할 세그먼트를 사용한다. 사용자 정의 소스 폴더는 lowerCamelCase로 시작하며 UI/API 약어도 ui/api로 표기한다. Swift 타입·파일명과 Xcode 도구 규격 디렉터리는 유지한다. 기존의 Widget 동위 배치와 렌더링 UI의 Model/ViewModel 일괄 금지는 이번 iOS 개편으로 대체한다.

Entity UI는 자기 도메인 값/표시값과 콜백을 받는 순수 표현이다. Widget/Page의 연결 UI는 자기 ViewModel을 관찰하고 Feature 행동을 주입할 수 있다. Repository와 캐시는 소유 도메인의 Entities에 유지하며, 지도·캘린더 OS 행동 어댑터는 해당 Feature로 옮긴다. App은 저장소 수명과 화면 간 라우팅을 조립한다. Shared는 도메인 행동 없는 디자인 시스템과 범용 기반만 가진다.

아래 기존 트리는 Android의 현재 구현과 이전 iOS 기준을 설명한다. 실제 이행 상태와 최종 노출 진입점은 각 앱 ARCHITECTURE.md, 검사 지원 범위는 iOS ArchitectureTests/README.md를 따른다. 아직 이전하지 않은 Android에 새 규칙 적용이 완료됐다고 해석하지 않는다.

## 레이어와 슬라이스

| 레이어 | Dearby의 책임 / 예시 |
| --- | --- |
| App | 앱 진입, 실제 공급/저장 구현 조립, 공유 상태 단일 소유, 탭과 화면 간 라우팅 |
| Pages | Discovery, Favorites, NoticeDetail 화면과 화면 전용 임시 UI 상태 |
| Widgets | NoticeCard(공고 내용·저장·상세 열기), FavoriteOrganizationCard(조직·연결 공고·삭제)의 독립 UI 블록 |
| Features | FavoriteOrganization의 저장·명시적 삭제 행동, 관찰 상태와 로컬 저장 경계/구현 |
| Entities | Notice의 NoticeModel·공고 조회/캐시와 Organization의 OrganizationModel·조직 조회/캐시·부모 경로 해석 |
| Shared | 도메인 지식 없는 정보 표시, 테마 등 범용 UI·기반 코드 |

공고와 조직은 독립 Entity 슬라이스다. NoticeModel은 조직 ID와 명시적 관계만 알고 OrganizationModel/이름/경로는 모른다. 두 모델의 조합은 Widgets의 ViewModel에서 수행하며 Entity 간 직접 참조를 만들지 않는다. 번들 전체 스냅샷을 읽는 공급 조립은 App 경계에 둔다. 조직 즐겨찾기 상태는 Features/FavoriteOrganization에 속한다.

```text
App/                            # Android: app/
  진입·조립·라우팅
Pages/                          # pages/
  Discovery/ui/                 # discovery/ui/
  Favorites/ui/                 # favorites/ui/
  NoticeDetail/                 # noticedetail/
    model/                      # NoticeDetailViewModel, NoticeDetailState
    ui/                         # 상세 표시
Widgets/                        # widgets/
  Notice/NoticeCard/             # notice/noticecard/
    NoticeCard, NoticeCardSaveButton
    NoticeCardViewModel, NoticeCardState
  Organization/FavoriteOrganizationCard/  # organization/favoriteorganizationcard/
    FavoriteOrganizationCard
    FavoriteOrganizationCardViewModel, FavoriteOrganizationCardState
Features/                       # features/
  FavoriteOrganization/
    model/                      # 상태·추가/삭제
    api/                        # 저장 계약·로컬 구현
Entities/                       # entities/
  Notice/                       # notice/
    model/                      # NoticeModel, 기간·장소·출처
    api/                        # 공고 원본 저장/조회·캐시
    ui/                         # 도메인 분류 표시
  Organization/                 # organization/
    model/                      # OrganizationModel
    api/                        # 조직 원본 저장/조회·캐시·경로 해석
Shared/                         # shared/
  ui/                           # PrimaryButton·SecondaryButton, 도메인 없는 정보 표시·테마
```

실제 파일 트리와 슬라이스별 외부 사용 지점은 각 앱 ARCHITECTURE.md에 기록한다. iOS 디렉터리는 Swift 관례의 이름, Android는 소문자 패키지를 사용할 수 있다. 빈 레이어·세그먼트와 사용하지 않는 추상화를 만들지 않는다.

## 의존 방향과 화면 조합

`App → Pages → Widgets → Features → Entities → Shared` 순서로 아래 레이어를 참조한다. 직접 참조는 바로 아래 두 레이어까지만 허용한다: App→Pages/Widgets, Pages→Widgets/Features, Widgets→Features/Entities, Features→Entities/Shared, Entities→Shared. app/providers의 의존성 생성·수명 관리만 예외이며 routes/entrypoint는 예외가 아니다. 같은 레이어의 다른 슬라이스를 직접 참조하지 않는다. App·Shared는 슬라이스 없이 목적별 세그먼트를 두는 예외다.

탐색 페이지가 상세 페이지 타입을 직접 생성하거나 즐겨찾기 페이지가 탐색 페이지를 참조하지 않는다. 화면 간 연결은 App의 라우팅·주입된 목적지 View/콜백으로 구성한다. SwiftUI NavigationLink·sheet와 Compose 시트/뒤로 가기의 기존 동작을 유지한다.

공고 카드와 즐겨찾기 조직 카드는 서로 참조하지 않는다. 해당 컴포넌트 State와 이벤트 콜백만 받으며 저장소·공유 상태를 직접 생성하거나 읽지 않는다. 카드의 작은 순수 표현은 책임에 따라 Entities/UI, 페이지의 UI 또는 해당 위젯 폴더에 둔다. Widgets는 도메인별로 그룹화하고 각 위젯 안의 View·ViewModel·State를 UI/Model 하위 폴더 없이 동위 배치한다. 도메인 그룹이 같아도 위젯 간 직접 의존을 허용하지 않는다. PrimaryButton·SecondaryButton은 Shared/UI의 네이티브 버튼이고, 조직 저장 문구·하트 표시는 NoticeCardSaveButton에서 조합한다. 공고 제목은 카드 안에 유지한다. 기능 한 곳의 UI를 재사용 가능하다는 이유만으로 Shared에 올리지 않는다.

## 네이티브 public API와 의존성 검증

FSD의 public API는 슬라이스 밖에서 사용할 수 있는 진입점 계약이다. JavaScript의 index.ts를 그대로 흉내 내지 않는다. 각 슬라이스의 노출 View/Composable·모델·protocol/interface를 앱 문서에 명시하고 나머지는 가능한 범위에서 private/internal로 제한한다.

Swift의 단일 앱 모듈과 Kotlin의 한 Gradle 모듈에서는 폴더만으로 슬라이스 접근을 컴파일러가 완전히 차단하지 못한다. 이를 독립 빌드 모듈 수준의 강제 경계로 주장하지 않는다. 플랫폼별 구조 검사와 리뷰에서 상향 참조·동일 레이어 슬라이스 참조·UI의 저장소 참조를 확인하고 검사 방식과 한계를 기록한다.

## 상태와 저장

즐겨찾기는 App이 상태 인스턴스 하나를 생성해 소유하고 관련 화면에 읽기 값과 추가/삭제 콜백을 전달한다. SwiftUI Observation과 Compose의 상태 관찰 방식을 유지한다. 기능 상태 구현은 Features에 두고 App은 생성·주입만 담당한다. App은 공고·조직 Repository와 저장소의 수명을 소유하고 ViewModel에 주입한다. 조회는 인메모리 → 영속 저장소(iOS SwiftData / Android Room) → 외부 source(현재 번들 mock) 순서다. 외부 성공 결과는 명시적 영속 저장 후 메모리에 반영하며, missing과 읽기·쓰기 오류를 구별한다. snapshot 교체는 영속 레코드와 manifest의 transaction 및 메모리 캐시 무효화로 조율한다. Android는 준비 작업을 IO dispatcher에서 수행하고 취소·이전 요청의 늦은 화면 반영을 방지한다. iOS의 현재 작은 동기 mock은 MainActor에서 처리한다. 실제 네트워크·인증·페이지네이션은 구현하지 않는다.

저장은 토글이 아닌 조직 ID의 멱등 추가이며 해제는 명시적 삭제다. 기존 UserDefaults 배열 키와 SharedPreferences StringSet 키, 기존 ID·상위 조직 비자동 저장·학교 맥락 분리를 보존한다. 임시 저장소 기반 독립 상태 테스트와 영속 복원 검증을 유지한다.

## iOS 상태와 조립 단순화 (2026-09-16 승인)

NoticeSession을 별도 세션이나 관찰 상태 계층으로 유지하지 않는다. 피드 갱신·페이지 추가 상태가 없는 현재 범위에서는 대체 NoticeStore도 만들지 않는다. 앱 시작 상태는 앱 상태 객체가, 의존성 생성·수명·주입은 app/providers가 맡는다. SwiftDataSnapshotStore는 저장·스냅샷 조율을 맡고 화면 ViewModel이나 즐겨찾기를 생성하지 않는다.

상세 ViewModel은 상세 화면 진입 시 조립하여 해당 화면 수명 동안 유지한다. 앱의 AppState와 화면 로컬 NoticeDetailRouteState는 실행·수명 상태이며, 도메인 표시 값만 담는 Widget의 NoticeDetailState와 구분한다. View body 재계산으로 I/O나 재생성이 일어나지 않으며, 상세만의 조회 오류는 상세에서 처리한다. 스냅샷 교체 시 공고·조직 캐시와 화면이 보관한 표시 상태를 함께 교체한다. 교체 실패 시 기존 유효 상태를 보존한다. 기존 단일 즐겨찾기 소유와 Widget ViewModel/State 조합은 유지한다. 구현·검증 상태는 컨텍스트 조율 문서를 따른다.

## iOS 캘린더·목록 수명 정리 (2026-09-16 승인)

Session/Store/ViewModel은 이름이나 크기 대신 실제 상태 소유권·수명·부수 효과로 검토한다. CalendarPreferences는 앱 전체의 동의·영속 설정·활성 상태 및 캘린더 변경 알림을 조율하고, BusyCalendarSession은 상세 화면별 선택 날짜·취소·일시적인 바쁜 시간 결과를 소유한다. 상세 객체에 별도 동의 요청 흐름을 두지 않으며 background에서는 개인 결과를 폐기하고 늦은 응답/변경 알림으로 조회를 재개하지 않는다. inactive인 OS 권한 대화상자는 요청 취소로 처리하지 않는다.

즐겨찾기 목록은 표시 대상 중심으로 구성하고 조직별 공고 인덱스를 재사용한다. 저장/삭제와 snapshot 교체가 목록에 반영되어야 하며 UI body/계산 getter에서 저장소 조회를 시작하지 않는다. 발견 카드의 불변 유효성/목록과 가변 saved 관찰을 분리한다. 한 상태 소유자를 여러 화면이 공유하는 SettingsViewModel과 FavoriteOrganizations/Store의 FSD 행동·도메인 경계는 유지한다. 적용·검증 상태는 현재 조율 문서를 따른다.

## 기능 단위 커밋과 검증

기존 게시 커밋은 재작성하지 않고 기존 PR에 후속 커밋을 추가한다. 공고 카드 경계 정리, 즐겨찾기 조직 카드 경계 정리, 상세 화면 라우팅 분리처럼 기능·컴포넌트 단위로 구성한다. 각 커밋에 필요한 코드·테스트·문서를 함께 넣고 가능한 한 빌드 가능한 상태로 유지한다. 여러 기능이 쓰는 카탈로그/즐겨찾기 경계 이동은 필요한 공통 선행 커밋으로 설명할 수 있다. 코드→테스트→문서 순서의 단계별 커밋을 기본으로 삼지 않는다.

각 앱에서 FSD 의존 경계, 실제 디렉터리와 public API 목록, 빌드 및 관련 테스트를 확인한다. 라우팅이나 컴포넌트 조합이 바뀐 사용자 흐름은 회귀 검증한다. 이전 테스트 결과와 이번 실행을 구별하고 합성 제스처 도구의 한계를 성공으로 기록하지 않는다.

PR #3은 공통 설계, #4는 Android, #5는 iOS, #6은 공통 공고 계약이며 main 대상으로 통합한다. 두 플랫폼의 구현과 검증을 함께 확인하여 #1을 정리하고, 네이티브 외형 정비는 #2에서 진행한다. 각 PR의 기능별 커밋 이력을 유지한다.


## Model·ViewModel·State 규칙

공고 도메인은 NoticeModel 하나다. 별도 Notice/NoticeDetail 원본·상세 엔티티와 상세 조립 Repository를 두지 않는다. 기간·장소처럼 의미 있는 내부 타입은 유지한다. OrganizationModel은 id·name·parentOrganizationId를 보유하며 공고와 독립적으로 저장한다.

NoticeRepository와 OrganizationRepository는 각 source/cache를 분리하고 App에서 공유한다. 처음 비어 있는 cache를 확인하고 miss일 때만 source 조회 후 성공한 모델을 캐시한다. 조직 경로는 OrganizationRepository가 순환 방지와 누락 처리를 포함해 해석한다. 같은 ID의 내용이 바뀔 수 있으므로 snapshot 교체 시 cache를 무효화한다.

NoticeCardViewModel은 두 모델을 조회하고 공유 즐겨찾기 상태를 반영하여 NoticeCardState를 제공한다. NoticeDetailViewModel도 NoticeDetailState를 구성한다. 조직 경로·표시 이름은 이 State에만 조립하며 NoticeModel에 저장하지 않는다. 화면별 표시 타입은 Model 대신 State 접미사를 쓴다.

State는 렌더링 값이다. 저장소·가변 전역 상태·콜백·부수 효과를 담지 않는다. View는 State와 별도 이벤트 콜백을 받아 표시하며 저장소나 원본 모델 조합을 직접 수행하지 않는다. 작은 하위 View에는 상위에서 필요한 값만 전달하고 ViewModel을 기계적으로 늘리지 않는다.

즐겨찾기 저장 목록은 기존 단일 상태가 원본이다. ViewModel의 State.saved는 이를 관찰해 반영하며 다른 화면에서 삭제한 결과도 기존 카드에 반영되어야 한다. UI 재계산마다 저장소·캐시·관찰 연결을 새로 만들지 않고 플랫폼에 맞는 수명을 유지한다.

Features/AddToCalendar는 Pages/Widgets의 State를 참조하지 않는다. Widget이 모델의 기간·장소 등 필요한 값을 Feature에 전달한다. 실제 지도·캘린더 OS 동작은 해당 Feature 어댑터가 실행하고, Widget이 행동과 표시를 조립한다. App routes는 목적지 연결만 담당한다.

검증에는 독립 Entity 의존 경계, State만 받는 UI, 캐시 miss/hit·교체, 누락/순환 조직, 두 모델의 State 조합, 화면 간 즐겨찾기 상태 반영 및 기존 사용자 동작을 포함한다. 구성 변경이므로 해당 흐름의 플랫폼 회귀 검증이 필요하다.

Android MainActivity 등의 프레임워크 용어와 기존 JSON activities 키·activity-samples.json 리소스 이름은 호환성을 위해 유지한다. 별도 Activity 도메인을 의미하지 않는다.
