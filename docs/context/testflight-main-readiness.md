# main iOS TestFlight 배포 준비 검사

검증 시각: 2026-09-29 20:25–20:28 KST. 역할: main 기반 iOS 무서명 archive 검사. Apple 인증·배포·공통 차단 이슈는 root 소유.

## 기준과 결과

- checkout: `/Users/jominjun/Documents/dearby/ios-testflight-main`, branch `fixabley/ios-testflight-main`.
- `git fetch origin main` 후 HEAD와 origin/main 모두 `b3094f9fc3b2eb6eb2de950cd1ad28010d50f8c4`. 발표/어드민/수집 미병합 변경을 가져오지 않았다. 앱 소스·프로젝트·공통 설정은 변경하지 않았다.
- **일반 iOS 기기 Release 무서명 archive 성공(exit 0). TestFlight 업로드 가능한 산출물이 아니다.**
- Xcode 27.0 (27A266a), iPhoneOS SDK 27.0 (24A430), 최소 iOS 18.0, arm64, iPhone/iPad, Release `-O`, dwarf-with-dsym.
- archive의 Xcode `Validate` 실행 완료. 이는 App Store Connect 배포 validation이 아니다.
- 원본 Debug/Release plist, archive/app plist 모두 `plutil -lint` 통과. 실행 파일/dSYM UUID 모두 `A345D902-F6A7-3641-83D4-A0ACAF27ABDF`.
- `codesign --verify --deep --strict`는 exit 1, `code object is not signed at all`. archive Team/SigningIdentity 빈값, embedded.mobileprovision 및 _CodeSignature 없음. 예상된 무서명 상태이며 서명 검증 통과로 해석 금지.

## 재현과 로컬 증거

아래 경로는 이 checkout 기준이며 `.build` 산출물은 Git에 넣지 않는다. 동일 resultBundlePath를 재사용하면 Xcode가 거부하므로 재실행은 새 경로를 사용한다.

```sh
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby \
  -configuration Release -destination 'generic/platform=iOS' \
  -derivedDataPath apps/ios/.build/testflight-main/DerivedData \
  -archivePath apps/ios/.build/testflight-main/Dearby-unsigned.xcarchive \
  -resultBundlePath apps/ios/.build/testflight-main/Archive.xcresult \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO archive
```

- `apps/ios/.build/testflight-main/archive.log`: `** ARCHIVE SUCCEEDED **`, 경고 3건.
- `apps/ios/.build/testflight-main/release-build-settings.txt`: 무서명 override 전 기본 Release 설정.
- `apps/ios/.build/testflight-main/inspection.json`: archive 메타데이터/서명 검사 결과.
- `apps/ios/.build/testflight-main/Dearby-unsigned.xcarchive` 및 `Archive.xcresult`: 원본 main 산출물.

## 발견 사항과 root 해소 조건

| 항목 | 확인 근거·영향 | 해소 조건·의존 작업 |
| --- | --- | --- |
| 서명/팀 | Release DEVELOPMENT_TEAM 없음. root가 valid identities 0을 확인했다고 전달함(이 세션 재조사 안 함). 산출물 자체도 무서명 | root가 승인된 Apple 팀·인증서/프로파일·App Store Connect 앱 권한을 확보하고 signed archive/export/validation 수행 |
| 버전/빌드 번호 | MARKETING_VERSION/CURRENT_PROJECT_VERSION 설정 없음. 실제 app plist에 CFBundleShortVersionString/CFBundleVersion 모두 없음 | root가 기존 App Store Connect 이력과 맞는 버전/중복 없는 빌드 번호 결정. 프로젝트 및 generate_project.rb에 일관되게 반영하고 main 통합 후 재archive |
| 앱 아이콘 | ASSETCATALOG_COMPILER_APPICON_NAME 빈값, AppIcon.appiconset 없음. archive CFBundleIcons도 없음. DearbyLogo.imageset은 앱 아이콘 설정을 대체하지 않음 | 승인된 기존 로고 기반 앱 아이콘 리소스 구성과 AppIcon 설정을 별도 작은 변경으로 검토/통합. 새 로고 생성 불필요 |
| API 주소 | archive DearbyAPIURL 빈 문자열. APIClient.validatedURL은 Release HTTPS만 허용. CatalogState.refresh는 API catalog 응답에 의존 | root가 승인된 실제 HTTPS API origin과 catalog 응답 확인 후 배포 빌드에 주입. 빈 주소의 신규 설치에서는 탐색 데이터 로드 불가. 가짜 주소/localhost로 배포 가능하다고 표시 금지 |
| iPad 방향 | 기기군 1,2이나 UISupportedInterfaceOrientations 및 iPad 설정 없음. Xcode: “All interface orientations must be supported unless the app requires full screen.” | 현재 iPad 지원 유지 기준으로 방향 설정을 명시하고 회전 UI 검증. 배포 validator 통과 여부는 아직 확인하지 않았으며 무조건 upload 거부라고 단정하지 않음 |
| 암호화 선언 | ITSAppUsesNonExemptEncryption 없음. 검토한 앱 코드에서 URLSession HTTPS/OS Keychain 사용, 별도 암호화 구현·외부 crypto 패키지는 확인되지 않음. 바이너리 링크는 Apple 시스템 라이브러리 | root가 최종 배포 암호화 질문 응답을 확정. 코드 근거상 면제 암호화 후보지만 법적/수출 선언을 임의 확정하지 않음. 키 누락 자체는 컴파일 오류가 아니며 ASC 질문으로 처리 가능 |

아이콘/버전은 배포 메타데이터 보완 사항이다. 이번에는 정확한 main 원본 검사 증거를 보존하고 소스 수정 없이 보고한다. 버전과 API·팀을 임의 결정하지 않았으며, 모든 보완은 root의 main 통합 이후 배포 archive에 반영해야 한다. 중복 GitHub 이슈·PR·push·업로드는 만들거나 실행하지 않았다.

## 캘린더·권한·기타 검사

- archive에 `NSCalendarsFullAccessUsageDescription`의 한국어 목적 문구가 포함됨. iOS 18+에서 사용하는 `requestFullAccessToEvents()`와 대응한다.
- 코드상 상세의 사용자 동작으로 열리는 CalendarConflictView에서만 권한 연결. 시작/종료 시각이 없으면 요청 전에 unknown 처리. OS EventKit 조회 후 캘린더 선택 정보/바쁜 시간만 화면 상태로 전달하며 종료/비활성 전환에 clear. 이번 검사는 정적 확인이며 실기기 권한 허용·거부나 반복 일정 동작을 새로 검증한 것은 아니다.
- 카메라/사진 추가 권한 목적 문구도 포함됨. 기존 숨긴 명함 기능의 소스·권한은 삭제하지 않았다.
- Release archive에 NSAppTransportSecurity 예외 없음. Debug의 로컬 네트워크 예외가 Release에 유입되지 않음.
- `dearby` URL scheme은 남아 있으나 기본 Discovery 루트와 숨긴 기능 차단은 기존 main 구현 그대로다.
- 나머지 빌드 경고: 존재하지 않는 AccentColor asset 참조(명시 SwiftUI 색상과 기본 control tint 일관성 점검 권장), AppIntents.framework 의존성 없어 metadata 추출 생략(이 앱에 App Intents 구현을 요구하는 경고가 아님).
- PrivacyInfo.xcprivacy는 앱 리소스에 없음. 검토 범위에서 UserDefaults/파일 timestamp/시스템 uptime 등 직접 사용은 찾지 못했다. 이것만으로 manifest 불필요나 전체 privacy 검증 통과를 단정하지 않으며, 최종 distribution privacy report는 root 배포 검증에 남긴다.
- unit/UI/구조/SwiftLint는 이번에 실행하지 않았다(실행 소스 변경 없음). 이전 문서의 통과 결과를 이번 Release archive 검증으로 옮겨 적지 않았다. 실기기 설치, HTTPS 운영 API, TestFlight processing/테스터 배포, Organizer/ASC validation도 미실행.

## 공식 문서와 판단 범위

- [Apple 배포 준비](https://developer.apple.com/documentation/xcode/preparing-your-app-for-distribution): 버전·빌드·아이콘 등 배포 메타데이터 기준.
- [CFBundleVersion](https://developer.apple.com/documentation/bundleresources/information-property-list/cfbundleversion): App Store 필수 빌드 식별 키.
- [ITSAppUsesNonExemptEncryption](https://developer.apple.com/documentation/bundleresources/information-property-list/itsappusesnonexemptencryption): 키를 넣지 않으면 업로드마다 암호화 질문으로 처리.

## 인계·세션 보존

Orca worktree instance `a46ac287-2318-4c3d-92bd-789038ff1a17`, runtime terminal `term_a6d0db39-eb0d-42ae-88dd-6b760a4eaac0` (2026-09-29 확인, 영구 세션 ID가 아님). 작업 카드에 검사 완료/차단 요약을 남기고 세션·worktree·archive를 유지한다. 공통 이슈 작성과 통합/배포는 root가 진행한다.

문서 diff ponytail-review: 중복 구현·새 래퍼·추상화 없음. 기능 변경 없이 검증 정본 하나로 유지.
