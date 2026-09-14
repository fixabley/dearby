# FSD 기반 네이티브 앱 구조

사용자는 #1의 기존 구조 리팩터링을 FSD(Feature-Sliced Design) 기반으로 구체화하도록 요청했다. 승인된 Seed v2의 화면·동작·저장 호환성, 네이티브 상태 관리, 새 외부 상태관리/DI 라이브러리·빌드 모듈 제외는 유지한다. UI 외형 정비 #2는 별도다.

[FSD 레이어](https://feature-sliced.design/docs/reference/layers)와 [슬라이스·세그먼트](https://feature-sliced.design/docs/reference/slices-segments)의 책임·의존 방향을 단일 Swift/Kotlin 앱에 적용한다. 다음 배치는 Dearby의 현재 기능 규모에 맞춘 설계이며 프레임워크 공식 템플릿이 아니다.

## 레이어와 슬라이스

| 레이어 | Dearby의 책임 / 예시 |
| --- | --- |
| App | 앱 진입, 실제 공급/저장 구현 조립, 공유 상태 단일 소유, 탭과 화면 간 라우팅 |
| Pages | Discovery, Favorites, NoticeDetail 화면과 화면 전용 임시 UI 상태 |
| Widgets | ActivityCard(공고 내용·저장·상세 열기), FavoriteOrganizationCard(조직·연결 공고·삭제)의 독립 UI 블록 |
| Features | FavoriteOrganization의 저장·명시적 삭제 행동, 관찰 상태와 로컬 저장 경계/구현 |
| Entities | ActivityCatalog의 공고·조직·출처·계층·맥락 모델 및 공급 경계/번들 구현, 공고 분류 표시 |
| Shared | 도메인 지식 없는 정보 표시, 테마 등 범용 UI·기반 코드 |

카탈로그 샘플의 공고·조직·출처는 서로 연결된 하나의 ActivityCatalog 슬라이스 안에서 관리한다. 이를 서로 다른 entity 슬라이스로 먼저 분리해 순환 참조를 만드는 대신 현재 모델의 응집성을 유지한다. 추후 분리할 실제 필요가 생기면 관계 조합을 상위 레이어로 옮기거나 명시적 참조 계약을 별도 설계한다. 조직 저장 상태는 일반 Shared가 아닌 FavoriteOrganization 사용자 행동에 속한다.

```text
App/                            # Android: app/
  진입·조립·라우팅
Pages/                          # pages/
  Discovery/ui/                 # discovery/ui/
  Favorites/ui/                 # favorites/ui/
  NoticeDetail/ui/              # noticedetail/ui/
Widgets/                        # widgets/
  ActivityCard/ui/              # activitycard/ui/
  FavoriteOrganizationCard/ui/  # favoriteorganizationcard/ui/
Features/                       # features/
  FavoriteOrganization/
    model/                      # 상태·추가/삭제
    api/                        # 저장 계약·로컬 구현
Entities/                       # entities/
  ActivityCatalog/
    model/                      # 공고·조직·카탈로그
    api/                        # 공급 계약·번들 구현
    ui/                         # 도메인 분류 표시
Shared/                         # shared/
  ui/                           # 도메인 없는 정보 표시·테마
```

실제 파일 트리와 슬라이스별 외부 사용 지점은 각 앱 ARCHITECTURE.md에 기록한다. iOS 디렉터리는 Swift 관례의 이름, Android는 소문자 패키지를 사용할 수 있다. 빈 레이어·세그먼트와 사용하지 않는 추상화를 만들지 않는다.

## 의존 방향과 화면 조합

`App → Pages → Widgets → Features → Entities → Shared` 순서로 아래 레이어를 참조한다. 중간 레이어를 거치지 않고 더 아래를 참조할 수 있다. 같은 레이어의 다른 슬라이스를 직접 참조하지 않는다. App·Shared는 슬라이스 없이 목적별 세그먼트를 두는 예외다.

탐색 페이지가 상세 페이지 타입을 직접 생성하거나 즐겨찾기 페이지가 탐색 페이지를 참조하지 않는다. 화면 간 연결은 App의 라우팅·주입된 목적지 View/콜백으로 구성한다. SwiftUI NavigationLink·sheet와 Compose 시트/뒤로 가기의 기존 동작을 유지한다.

공고 카드와 즐겨찾기 조직 카드는 서로 참조하지 않는다. 표시에 필요한 모델·저장 여부와 이벤트 콜백만 받으며 저장소·공유 상태를 직접 생성하거나 읽지 않는다. 카드의 작은 순수 표현은 필요에 따라 Entities/UI 또는 페이지/위젯의 ui에 둔다. 기능 한 곳의 UI를 재사용 가능하다는 이유만으로 Shared에 올리지 않는다.

## 네이티브 public API와 의존성 검증

FSD의 public API는 슬라이스 밖에서 사용할 수 있는 진입점 계약이다. JavaScript의 index.ts를 그대로 흉내 내지 않는다. 각 슬라이스의 노출 View/Composable·모델·protocol/interface를 앱 문서에 명시하고 나머지는 가능한 범위에서 private/internal로 제한한다.

Swift의 단일 앱 모듈과 Kotlin의 한 Gradle 모듈에서는 폴더만으로 슬라이스 접근을 컴파일러가 완전히 차단하지 못한다. 이를 독립 빌드 모듈 수준의 강제 경계로 주장하지 않는다. 플랫폼별 구조 검사와 리뷰에서 상향 참조·동일 레이어 슬라이스 참조·UI의 저장소 참조를 확인하고 검사 방식과 한계를 기록한다.

## 상태와 저장

즐겨찾기는 App이 상태 인스턴스 하나를 생성해 소유하고 관련 화면에 읽기 값과 추가/삭제 콜백을 전달한다. SwiftUI Observation과 Compose의 상태 관찰 방식을 유지한다. 기능 상태 구현은 Features에 두고 App은 생성·주입만 담당한다. 카탈로그 공급은 Entities의 경계를 주입하며 지금은 기존 동기식 번들 공급을 유지한다. 실제 네트워크, 인증·페이지네이션·취소 정책은 구현하지 않는다.

저장은 토글이 아닌 조직 ID의 멱등 추가이며 해제는 명시적 삭제다. 기존 UserDefaults 배열 키와 SharedPreferences StringSet 키, 기존 ID·상위 조직 비자동 저장·학교 맥락 분리를 보존한다. 임시 저장소 기반 독립 상태 테스트와 영속 복원 검증을 유지한다.

## 기능 단위 커밋과 검증

기존 게시 커밋은 재작성하지 않고 기존 PR에 후속 커밋을 추가한다. 공고 카드 경계 정리, 즐겨찾기 조직 카드 경계 정리, 상세 화면 라우팅 분리처럼 기능·컴포넌트 단위로 구성한다. 각 커밋에 필요한 코드·테스트·문서를 함께 넣고 가능한 한 빌드 가능한 상태로 유지한다. 여러 기능이 쓰는 카탈로그/즐겨찾기 경계 이동은 필요한 공통 선행 커밋으로 설명할 수 있다. 코드→테스트→문서 순서의 단계별 커밋을 기본으로 삼지 않는다.

각 앱에서 FSD 의존 경계, 실제 디렉터리와 public API 목록, 빌드 및 관련 테스트를 확인한다. 라우팅이나 컴포넌트 조합이 바뀐 사용자 흐름은 회귀 검증한다. 이전 테스트 결과와 이번 실행을 구별하고 합성 제스처 도구의 한계를 성공으로 기록하지 않는다.

PR #3은 공통 설계, #4는 Android, #5는 iOS이며 각각 main 대상이다. 자동 병합하지 않고 두 플랫폼까지 검토한 뒤 #1 완료 여부를 판단한다.
