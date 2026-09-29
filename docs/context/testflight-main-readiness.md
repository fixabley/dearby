# main iOS TestFlight 배포 준비

최신 검증: 2026-09-29 20:35 KST. 역할: main 기반 iOS 배포 메타데이터·무서명 archive. Apple 인증·업로드·API 도메인/nginx·공통 차단 이슈는 root 소유.

## 현재 결과

- checkout `/Users/jominjun/Documents/dearby/ios-testflight-main`, branch `fixabley/ios-testflight-main`.
- 시작/재개 시 `git fetch origin main`으로 `b3094f9fc3b2eb6eb2de950cd1ad28010d50f8c4` 확인. 발표/어드민/수집 미병합 변경 없음. iOS 배포 설정/리소스/문서만 보완했다.
- **원본 main과 메타데이터 보완본 모두 일반 iOS 기기 Release 무서명 archive 성공. TestFlight 업로드 가능한 산출물은 아니다.**
- Xcode 27.0 (27A266a), iPhoneOS SDK 27.0 (24A430), 최소 iOS 18.0, arm64, iPhone/iPad, Release `-O`, dwarf-with-dsym.
- `0.1.0 (1)`은 사용자 승인된 **임시값**. App Store Connect 기존 기록 미확인이므로 최종 업로드 번호 확정/중복 검사 필요.

## 보완과 선택 근거

| 항목 | 구현·확인 |
| --- | --- |
| 버전/빌드 | 앱 타깃 Debug/Release에 MARKETING_VERSION=0.1.0, CURRENT_PROJECT_VERSION=1. generate_project.rb에도 동일 설정. 실제 archive plist에 CFBundleShortVersionString/CFBundleVersion 포함 |
| AppIcon | 단일 1024×1024 불투명 sRGB PNG와 universal iOS catalog. ASSETCATALOG_COMPILER_APPICON_NAME=AppIcon. archive의 phone/pad 1024 아이콘과 CFBundleIcons/CFBundleIcons~ipad 확인 |
| 방향 | Debug/Release plist에 iPad 네 방향, iPhone 세로/양쪽 가로 명시. iPad 지원/멀티태스킹을 없애거나 full-screen 강제로 경고를 숨기지 않음. Xcode 방향 경고 해소 |
| AccentColor | 없는 AccentColor asset의 compiler 참조를 비움. 기존 DearbyApp의 명시적 청록 tint 보존. Xcode 경고 해소 |
| 암호화 | ITSAppUsesNonExemptEncryption=NO. 앱 전체 소스에서 URLSession HTTPS, OS Keychain SecItem*만 확인. 프로젝트에 외부 Swift package/runtime library 의존성 없음, archive 링크는 Apple 시스템 라이브러리. 자체/번들 암호화 구현을 발견하지 못한 기술적 근거이며 법적 판단이나 최종 수출 선언 승인을 뜻하지 않음 |
| API/서명 | DEARBY_API_URL 빈값 유지, 팀·인증서·프로파일 미설정 유지. root 외부 조사/공유 서비스 변경 없음 |

승인 manifest의 `logo-teal.png`는 2172×724 가로 워드마크다. 정사각 런처에 전체를 넣으면 작은 크기에서 글자가 작아진다. 기존 후보를 먼저 조사했고, main Android 런처 `apps/android/app/src/main/res/drawable/ic_launcher.xml`(도입 커밋 `911c501`, main 포함)의 청록 바탕/흰색 D를 **형태·색상·even-odd fill 그대로** CoreGraphics로 래스터화했다. 새 로고 생성·워드마크 재디자인·다른 브랜치 자산 반입은 없다. 이 후보가 main에서 이미 사용 중이라는 근거는 있으나, 별도의 시각 디자인 승인 기록까지 발견한 것은 아니다. root가 PR에서 선택을 확인할 수 있다. 과거 iOS AppIcon catalog는 filename 없는 빈 설정, 과거 Android 하트는 네이티브 재착수 이전이라 채택하지 않았다. 앱 내 승인 워드마크는 그대로 유지했다.

원본/결과 SHA256과 출처는 [ASSET-SOURCES](../../apps/ios/Resources/ASSET-SOURCES.md)에 기록했다. 직접 픽셀 검사로 배경 RGB `[0,127,128]`, 1024×1024, alpha 없음 확인. 로컬 재현 렌더 코드는 `.build/testflight-main/render-app-icon.swift`에 보존하며 런타임 코드나 새 의존성은 추가하지 않았다.

## 실제 실행한 검증

- 보완본 Release 일반 iOS 기기 archive exit 0, `** ARCHIVE SUCCEEDED **`. Xcode 제품 Validate 완료. **App Store Connect distribution validation을 실행한 것은 아니다.**
- 원본/보완 Debug·Release plist 및 archive app plist lint 통과. 보완 archive 메타데이터 assertion 통과: 버전, 빌드, phone/pad AppIcon, iPad 네 방향, 암호화 bool, API 빈값, Release ATS 예외 없음.
- `assetutil --info`: phone/pad AppIcon 각각 1024×1024 Opaque=true. 원본 PNG alpha 없음. 경고는 AppIntents.framework 의존성이 없어 metadata 추출을 생략한다는 1건만 남음.
- strict SwiftLint 64파일 0위반, 구조 검사 16개 통과, generator Ruby syntax 및 git diff --check 통과.
- 전용 iPad Pro 11-inch (M5), iOS 27 Simulator `Dearby-TestFlight-Metadata-iPad` / `327481C4-C796-45D5-AF48-38EC4D98F78F`: Release build/install/launch 성공. 세로 탐색/빈 API 오류 화면 캡처 확인. 다른 사용자 Simulator를 덮어쓰지 않음.
- 같은 전용 iPad에서 Debug `DearbyTests`와 `DiscoveryNavigationTests/testOnlyDiscoveryIsVisible`: **34 통과(단위33/UI1), 7 skip, 0 실패**. skip은 실제 EventKit fixture 1, catalog HTTP fixture 1, 로컬 인증 API fixture 5. 이를 실서비스 검증으로 해석하지 않음.
- XcodeBuildMCP snapshot_ui 접근성 트리 조회는 automation session 생성 타임아웃으로 실패. screenshot과 XCUITest 성공과 구별한다. 전체 접근성 감사/회전별 UI/실기기/실제 API/캘린더 허용 흐름은 이번에 검증하지 않았다.
- `codesign --verify --deep --strict`는 원본/보완 모두 exit 1, `code object is not signed at all`. 예상된 무서명 상태로 서명 검증 통과가 아니다.
- ponytail-review: 설정·기존 자산·출처 문서만 추가, 새 런타임 래퍼/패키지/추상화 없음. 삭제 후보 없음.

## 재현·증거

아래 경로는 이 checkout 기준. `.build` 산출물은 Git에 포함하지 않는다. 재실행 시 resultBundlePath는 새 경로를 사용한다.

```sh
xcodebuild -project apps/ios/Dearby.xcodeproj -scheme Dearby \
  -configuration Release -destination 'generic/platform=iOS' \
  -derivedDataPath apps/ios/.build/testflight-main/DerivedData \
  -archivePath apps/ios/.build/testflight-main/Dearby-metadata-unsigned.xcarchive \
  -resultBundlePath apps/ios/.build/testflight-main/MetadataArchive.xcresult \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO archive
```

`apps/ios/.build/testflight-main/` 내 보완 증거: `metadata-archive.log`, `MetadataArchive.xcresult`, `metadata-inspection.json`, `metadata-assets.json`, `lint.log`, `architecture.log`, `MetadataTests.xcresult`, `ipad-release-portrait.jpg`, `Dearby-metadata-unsigned.xcarchive`.

원본 main 검사(20:25 KST)는 `archive.log`, `Archive.xcresult`, `inspection.json`, `release-build-settings.txt`, `Dearby-unsigned.xcarchive`로 보존했다. 당시 버전/아이콘/방향 누락은 이번 소스 보완으로 해소했으며 API/서명 차단은 그대로다. 원본 arm64/dSYM UUID 일치도 확인했다.

## root 인계·해소 조건

1. **Apple 서명/권한**: root 전달상 Xcode의 minjun jo 팀(Admin)은 있으나 인증서 목록은 비어 있음. 이 세션은 인증을 재조사·생성하지 않았다. root가 승인된 팀/서명/프로파일·ASC 앱 레코드/권한으로 signed archive/export/validation을 수행해야 한다.
2. **API**: archive DearbyAPIURL 빈 문자열. APIClient는 Release HTTPS만 허용하며 신규 설치의 catalog 조회 불가. root가 운영 HTTPS origin과 catalog 응답을 확인해 전달한 뒤 최종 배포 빌드에 주입해야 한다. nginx/DNS/공유기 변경은 이 checkout에서 하지 않는다.
3. **최종 번호·선언**: root가 ASC 기존 버전/빌드 이력 및 최종 암호화 응답을 확인. `0.1.0 (1)`을 승인된 배포 번호로 오인하지 않는다. 향후 외부 crypto 의존성 추가 시 선언 재검토.
4. **main 통합 후 배포**: PR을 main에 통합한 commit에서 재archive. 이 브랜치에서 upload하지 않음. 기능/UI/수집/어드민 미병합 변경은 포함하지 않는다.
5. **검증 잔여**: TestFlight processing/테스터 전달, ASC privacy report 및 배포 validation, 실제 HTTPS API, 실기기/회전별 UI는 별도. 앱 PrivacyInfo.xcprivacy는 현재 없고 검토 범위에서 직접 required-reason API 사용을 찾지 못했으나 이 사실만으로 최종 privacy 통과를 단정하지 않음.

캘린더 full-access 한국어 목적 문구와 iOS 18+ requestFullAccessToEvents 대응은 보존했다. 권한은 상세의 사용자 동작 후 요청하고 시각 미확인 활동은 요청 전 unknown 처리하는 기존 main 구현 유지. 카메라/사진 추가 문구와 숨긴 기능/데이터도 삭제하지 않았다.

## 공식 근거·세션

[Apple 배포 준비](https://developer.apple.com/documentation/xcode/preparing-your-app-for-distribution), [단일 크기 AppIcon](https://developer.apple.com/documentation/xcode/configuring-your-app-icon), [암호화 선언 안내](https://developer.apple.com/documentation/security/complying-with-encryption-export-regulations)를 참고했다. OS 제공 암호화에 관한 Apple 기술 안내에 따라 구성했으며 배포자의 법적 판단을 대신하지 않는다.

Orca worktree instance `a46ac287-2318-4c3d-92bd-789038ff1a17`, runtime terminal `term_a6d0db39-eb0d-42ae-88dd-6b760a4eaac0` (2026-09-29 확인, 영구 세션 ID 아님). 카드·세션·worktree·archive 유지. 공통 GitHub 차단 이슈는 root가 작성하므로 중복 이슈 없음.
