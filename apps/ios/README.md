# Dearby iOS

SwiftUI 기반의 네이티브 iPhone·iPad 앱입니다.

| 항목 | 설정 |
| --- | --- |
| 표시 이름 / Scheme | Dearby |
| Bundle Identifier | `io.fixabley.dearby` |
| 최소 지원 버전 | iOS 26.0 |
| Swift 언어 모드 | Swift 6 |
| 개발 도구 | Xcode 26 이상 (26.6에서 빌드 확인) |
| 앱 버전 / 빌드 | 1.0.0 / 1 |

## 실행

모노레포의 `apps/ios/` 디렉터리에서 프로젝트를 엽니다.

```sh
open Dearby.xcodeproj
```

Xcode에서 `Dearby` scheme과 iOS 26 이상 시뮬레이터를 선택하고 Run을 실행합니다.
실제 기기에서 실행하려면 앱 타깃의 Signing & Capabilities에서 본인의 개발 Team을 지정합니다.

## 명령줄 빌드

`apps/ios/` 디렉터리에서 실행합니다. 별도 패키지 설치는 필요하지 않습니다.

```sh
xcodebuild \
  -project Dearby.xcodeproj \
  -scheme Dearby \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/DerivedData \
  CODE_SIGNING_ALLOWED=NO \
  build
```

## FSD 소스 구조

- `Dearby/App/`: 앱 진입·단일 상태 소유·탭·페이지 목적지 조립
- `Dearby/Pages/{Discovery,Favorites,NoticeDetail}/UI/`: 화면과 로컬 UI 상태
- `Dearby/Widgets/{ActivityCard,FavoriteOrganizationCard}/UI/`: 독립 복합 카드, 표시 데이터·콜백만 사용
- `Dearby/Features/FavoriteOrganization/{Model,API}/`: 관찰 상태·추가/삭제·저장 계약·UserDefaults
- `Dearby/Entities/ActivityCatalog/{Model,API,UI}/`: 서로 연결된 모델·조회·공급·분류 표시
- `Dearby/Resources/`와 `Dearby/Assets.xcassets/`: 기존 번들 샘플·테마·아이콘

실제 트리, slice별 public API 계약, 의존 방향, 상태 생명주기, 새 기능 배치는 [ARCHITECTURE.md](ARCHITECTURE.md)를 따른다.
[이슈 #1](https://github.com/fixabley/dearby/issues/1)과 [공통 설계 #3](https://github.com/fixabley/dearby/pull/3)의 FSD 원칙을 적용한다.
Shared는 실제 공용 범용 코드가 필요할 때 목적별 segment로 추가하며 빈 폴더를 만들지 않는다.
`Dearby/` synchronized 그룹·공유 scheme을 유지한다. 단일 앱 모듈이며 폴더만으로 compiler-enforced slice를 제공하지 않는다.
Preview는 사용자 저장소에 쓰지 않는다. 외부 의존성·별도 테스트 타깃·빌드 모듈은 없다. 앱 아이콘 이미지는 출시 전에 필요하다.

## 저장소

이 앱은 Dearby 모노레포의 `apps/ios/`에서 관리합니다.
별도 Git 초기화나 submodule 설정 없이, 저장소 루트에서 다른 클라이언트 및
공통 명세와 함께 브랜치·커밋·PR을 관리합니다.

## 샘플과 상태·저장소 검증

샘플의 기준은 루트 `shared/contracts/activities/sample.json`이다. 공통 동기화 스크립트는
Android 리소스도 수정하므로 공통 담당과 조율한다. 앱은 번들 데이터만 사용하며 API 연결·로그인은 없다.

Xcode 테스트 타깃 없이 모노레포 루트에서 실행한다. 인메모리 저장소 및 고유한 임시
UserDefaults suite를 사용하며 사용자 앱 즐겨찾기는 변경하지 않는다. 생성한 suite와 번들 fixture는 종료 시 제거한다.

```sh
mkdir -p apps/ios/build
swiftc -swift-version 6 -parse-as-library \
  apps/ios/Dearby/Entities/ActivityCatalog/Model/*.swift \
  apps/ios/Dearby/Entities/ActivityCatalog/API/*.swift \
  apps/ios/Dearby/Features/FavoriteOrganization/Model/*.swift \
  apps/ios/Dearby/Features/FavoriteOrganization/API/*.swift \
  apps/ios/tests/FavoritesStoreTests.swift \
  -o apps/ios/build/dearby-favorites-tests
apps/ios/build/dearby-favorites-tests shared/contracts/activities/sample.json
```

검증 항목은 임시 저장소 주입, 추가·반복 추가·삭제, 두 Observation 소비자의 동일 상태와 변경 통지,
기존 UserDefaults 문자열 배열 호환·저장소 재생성 복원, 기업/학교 분리와 대회 프로그램·회차,
카탈로그 공급자 교체·번들 디코딩·잘못된 모드/JSON 오류다.
화면 회귀는 별도 시뮬레이터에서 수행하며 실제 결과와 한계는 [구조 문서의 검증 기록](ARCHITECTURE.md#검증-기록)에 남긴다.

## FSD 경계 검사

저장소 루트에서 실행한다. Swift 선언·타입 참조 기반으로 상향 의존, 같은 레이어 다른 slice,
UI의 저장소/공유 상태 접근을 검사하며 대표 금지 참조 음성 fixture도 함께 실행한다.

```sh
python3 apps/ios/tests/check_fsd_boundaries.py
```

이 검사는 간단한 lexical guard이며 Swift parser/모듈 격리를 대체하지 않는다. 추론·보간·동적 참조 등 누락 가능성은 구조 문서에 기록한다.
각 기능/컴포넌트 변경에 필요한 코드·검사·문서를 같은 커밋에 넣는다.
