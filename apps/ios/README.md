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

## 구조와 검증

현재 앱은 Entities/Notice의 NoticeModel과 Entities/Organization의 OrganizationModel을 독립적으로 조회합니다. App이 snapshot과 repository 수명을 소유하고 카드·상세·즐겨찾기 ViewModel이 State를 조합합니다. View는 State·콜백만 받습니다. 실제 트리·진입점·캐시/Observation 수명은 [ARCHITECTURE.md](ARCHITECTURE.md)를 참고하세요.

저장 키/JSON activities·activity-samples.json·접근성 태그와 기존 한국어 문구를 유지합니다. [Related #1](https://github.com/fixabley/dearby/issues/1), [설계 #3](https://github.com/fixabley/dearby/pull/3), [공통 계약 #6](https://github.com/fixabley/dearby/pull/6)은 별도 통합합니다.

저장소 루트에서 실행합니다. 외부 설치나 앱 실행 없이 임시 저장소/별도 UserDefaults suite로 검사합니다.

```sh
bash apps/ios/tests/run_standalone.sh
python3 apps/ios/tests/check_fsd_boundaries.py
git diff --check
```

run_standalone.sh에 실제 swiftc 파일 목록과 실행 명령이 있습니다. 현재 tests는 FavoritesStoreTests, OrganizationRepositoryTests, NoticeViewModelTests, CalendarDraftTests, VenueMapTests이며 old shared JSON과 앱 JSON을 모두 읽습니다. 앱 리소스 canonical SHA256은 c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f입니다. shared snapshot은 변경하지 않으므로 공통 PR #6 통합 전 samples:check의 리소스 차이는 예상됩니다.

이번 standalone/FSD·Simulator build 및 전용 기기 저장/탭/상세 smoke 결과와 미검증 영역은 ARCHITECTURE.md에 구분해 기록했습니다. 지도/캘린더 어댑터는 유지하며 캘린더 권한 요청·직접 저장은 하지 않습니다.
