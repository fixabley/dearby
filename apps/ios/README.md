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

## 소스 구조

- `Dearby/App/`: 앱 진입·의존성 조립·단일 즐겨찾기 상태 소유·탭 조합
- `Dearby/Features/Discovery/`: 탐색 화면과 카드
- `Dearby/Features/Favorites/`: 즐겨찾기 목록과 조직 행
- `Dearby/Features/NoticeDetail/`: 공고 상세와 조직·분류·학교·회차 표시
- `Dearby/Shared/Models/`: 공고·조직·카탈로그와 순수 조회
- `Dearby/Shared/State/`: Observation 즐겨찾기 상태와 추가·삭제 이벤트
- `Dearby/Shared/Data/`: 교체 가능한 카탈로그 공급·저장 protocol 및 번들·UserDefaults 구현
- `Dearby/Shared/UI/`: 발견·즐겨찾기에서 재사용하는 분류 표시
- `Dearby/Resources/`: 번들 샘플, `Dearby/Assets.xcassets`: 테마와 앱 아이콘 슬롯
- `Dearby.xcodeproj/xcshareddata/xcschemes/Dearby.xcscheme`: 공유 scheme

상태 생명주기·의존 방향·새 기능 배치는 [ARCHITECTURE.md](ARCHITECTURE.md)를 따른다.
[이슈 #1](https://github.com/fixabley/dearby/issues/1), [공통 설계 PR #3](https://github.com/fixabley/dearby/pull/3)을 기준으로 기존 외형과 저장 형식을 유지한다.

`Dearby/`는 Xcode 파일 시스템 동기화 그룹이므로 하위 파일을 자동 포함한다.
외부 의존성과 별도 테스트 타깃·빌드 모듈은 없다. Preview는 저장하지 않는 전용 저장소를 사용한다.
앱 아이콘 이미지는 출시 전에 추가해야 한다. 개발자별 Xcode 상태와 인증서·프로비저닝 프로파일은 커밋하지 않는다.

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
  apps/ios/Dearby/Shared/Models/*.swift \
  apps/ios/Dearby/Shared/State/*.swift \
  apps/ios/Dearby/Shared/Data/*.swift \
  apps/ios/tests/FavoritesStoreTests.swift \
  -o apps/ios/build/dearby-favorites-tests
apps/ios/build/dearby-favorites-tests shared/contracts/activities/sample.json
```

검증 항목은 임시 저장소 주입, 추가·반복 추가·삭제, 두 Observation 소비자의 동일 상태와 변경 통지,
기존 UserDefaults 문자열 배열 호환·저장소 재생성 복원, 기업/학교 분리와 대회 프로그램·회차,
카탈로그 공급자 교체·번들 디코딩·잘못된 모드/JSON 오류다.
화면 회귀는 별도 시뮬레이터에서 수행하며 실제 결과와 한계는 [구조 문서의 검증 기록](ARCHITECTURE.md#검증-기록)에 남긴다.
