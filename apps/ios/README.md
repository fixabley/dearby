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

- `Dearby/DearbyApp.swift`: 앱 진입점
- `Dearby/ContentView.swift`: 카드 탐색·상세·조직 즐겨찾기와 Preview
- `Dearby/Assets.xcassets`: 테마 색상과 앱 아이콘 슬롯
- `Dearby.xcodeproj/xcshareddata/xcschemes/Dearby.xcscheme`: 공유 scheme

`Dearby/` 폴더는 Xcode의 파일 시스템 동기화 그룹이므로 새 소스 파일을 추가하면
프로젝트에 자동으로 반영됩니다. 외부 의존성과 테스트 타깃은 아직 없습니다.
앱 아이콘 이미지는 출시 전에 추가해야 합니다.

Swift Package Manager 의존성을 추가한다면 앱의 `Package.resolved`도 커밋합니다.
개발자별 Xcode 상태와 인증서·프로비저닝 프로파일은 커밋하지 않습니다.

## 저장소

이 앱은 Dearby 모노레포의 `apps/ios/`에서 관리합니다.
별도 Git 초기화나 submodule 설정 없이, 저장소 루트에서 다른 클라이언트 및
공통 명세와 함께 브랜치·커밋·PR을 관리합니다.

## 샘플과 저장소 검증

샘플 데이터는 루트 `scripts/sync-activity-samples.py`로 동기화합니다.
조직 ID를 UserDefaults에 저장하며 로그인이나 API 연결은 아직 없습니다.
Xcode 테스트 타깃 대신 다음 독립 Swift 검증을 모노레포 루트에서 실행할 수 있습니다.
임시 UserDefaults suite를 사용하므로 앱에 저장된 즐겨찾기를 건드리지 않습니다.

```sh
swiftc -parse-as-library \
  apps/ios/Dearby/ActivityCatalog.swift \
  apps/ios/Dearby/FavoriteOrganizations.swift \
  apps/ios/tests/FavoritesStoreTests.swift \
  -o /tmp/dearby-favorites-tests
/tmp/dearby-favorites-tests shared/contracts/activities/sample.json
```
