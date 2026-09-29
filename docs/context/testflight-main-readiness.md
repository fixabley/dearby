# main iOS TestFlight 배포 준비

최신 검증: 2026-09-29 20:51 KST. 역할: main 기반 iOS 배포 메타데이터·무서명 archive. Apple 인증·업로드·API 도메인/nginx·공통 차단 이슈는 root 소유.

## 현재 결과

- checkout `/Users/jominjun/Documents/dearby/ios-testflight-main`, branch `fixabley/ios-testflight-main`.
- 최초 기반은 `b3094f9fc3b2eb6eb2de950cd1ad28010d50f8c4`. 20:50 KST root 지시로 PR61이 병합된 main `0a045c4`를 포함하는 PR60 원격 `2c57cdf`를 fast-forward 반영했다. 미커밋 번들검사 인계는 `.build`에 백업 후 원문 그대로 보존했다. 발표/어드민/수집 미병합 변경 없음. iOS 배포 설정/리소스/문서만 보완했다.
- **원본 main과 메타데이터 보완본 모두 일반 iOS 기기 Release 무서명 archive 성공. TestFlight 업로드 가능한 산출물은 아니다.**
- Xcode 27.0 (27A266a), iPhoneOS SDK 27.0 (24A430), 최소 iOS 18.0, arm64, iPhone/iPad, Release `-O`, dwarf-with-dsym.
- 사용자 신규 ASC 앱 등록에 맞춰 Release ID를 `io.wid.dearby`로 변경했다. TestFlight 버전 `0.1.0 (1)`은 root 후속 승인으로 유지하며 ASC Store 초안 `1.0`은 변경하지 않았다. 서명/업로드는 root 담당이다.

## 보완과 선택 근거

| 항목 | 구현·확인 |
| --- | --- |
| 버전/빌드 | 앱 타깃 Debug/Release에 MARKETING_VERSION=0.1.0, CURRENT_PROJECT_VERSION=1. generate_project.rb에도 동일 설정. 실제 archive plist에 CFBundleShortVersionString/CFBundleVersion 포함 |
| AppIcon | 단일 1024×1024 불투명 sRGB PNG와 universal iOS catalog. ASSETCATALOG_COMPILER_APPICON_NAME=AppIcon. archive의 phone/pad 1024 아이콘과 CFBundleIcons/CFBundleIcons~ipad 확인 |
| 방향 | Debug/Release plist에 iPad 네 방향, iPhone 세로/양쪽 가로 명시. iPad 지원/멀티태스킹을 없애거나 full-screen 강제로 경고를 숨기지 않음. Xcode 방향 경고 해소 |
| AccentColor | 없는 AccentColor asset의 compiler 참조를 비움. 기존 DearbyApp의 명시적 청록 tint 보존. Xcode 경고 해소 |
| 암호화 | ITSAppUsesNonExemptEncryption=NO. 앱 전체 소스에서 URLSession HTTPS, OS Keychain SecItem*만 확인. 프로젝트에 외부 Swift package/runtime library 의존성 없음, archive 링크는 Apple 시스템 라이브러리. 자체/번들 암호화 구현을 발견하지 못한 기술적 근거이며 법적 판단이나 최종 수출 선언 승인을 뜻하지 않음 |
| API/서명 | 후속 root 승인으로 Release DEARBY_API_URL=https://wid.io.kr 설정. Debug는 빈값. 팀·인증서·프로파일 미설정 유지. Supabase URL/key 없음. root 외부 조사/공유 서비스 변경 없음 |

승인 manifest의 `logo-teal.png`는 2172×724 가로 워드마크다. 정사각 런처에 전체를 넣으면 작은 크기에서 글자가 작아진다. 기존 후보를 먼저 조사했고, main Android 런처 `apps/android/app/src/main/res/drawable/ic_launcher.xml`(도입 커밋 `911c501`, main 포함)의 청록 바탕/흰색 D를 **형태·색상·even-odd fill 그대로** CoreGraphics로 래스터화했다. 새 로고 생성·워드마크 재디자인·다른 브랜치 자산 반입은 없다. 이 후보가 main에서 이미 사용 중이라는 근거는 있으나, 별도의 시각 디자인 승인 기록까지 발견한 것은 아니다. root가 PR에서 선택을 확인할 수 있다. 과거 iOS AppIcon catalog는 filename 없는 빈 설정, 과거 Android 하트는 네이티브 재착수 이전이라 채택하지 않았다. 앱 내 승인 워드마크는 그대로 유지했다.

원본/결과 SHA256과 출처는 [ASSET-SOURCES](../../apps/ios/Resources/ASSET-SOURCES.md)에 기록했다. 직접 픽셀 검사로 배경 RGB `[0,127,128]`, 1024×1024, alpha 없음 확인. 로컬 재현 렌더 코드는 `.build/testflight-main/render-app-icon.swift`에 보존하며 런타임 코드나 새 의존성은 추가하지 않았다.

## 실제 실행한 검증

아래 메타데이터/UI 전체 검증은 API origin 설정 전 20:35 KST 결과다. Release origin 후속 검증은 다음 절에 별도 기록한다.

- 보완본 Release 일반 iOS 기기 archive exit 0, `** ARCHIVE SUCCEEDED **`. Xcode 제품 Validate 완료. **App Store Connect distribution validation을 실행한 것은 아니다.**
- 원본/보완 Debug·Release plist 및 archive app plist lint 통과. 보완 archive 메타데이터 assertion 통과: 버전, 빌드, phone/pad AppIcon, iPad 네 방향, 암호화 bool, API 빈값, Release ATS 예외 없음.
- `assetutil --info`: phone/pad AppIcon 각각 1024×1024 Opaque=true. 원본 PNG alpha 없음. 경고는 AppIntents.framework 의존성이 없어 metadata 추출을 생략한다는 1건만 남음.
- strict SwiftLint 64파일 0위반, 구조 검사 16개 통과, generator Ruby syntax 및 git diff --check 통과.
- 전용 iPad Pro 11-inch (M5), iOS 27 Simulator `Dearby-TestFlight-Metadata-iPad` / `327481C4-C796-45D5-AF48-38EC4D98F78F`: Release build/install/launch 성공. 세로 탐색/빈 API 오류 화면 캡처 확인. 다른 사용자 Simulator를 덮어쓰지 않음.
- 같은 전용 iPad에서 Debug `DearbyTests`와 `DiscoveryNavigationTests/testOnlyDiscoveryIsVisible`: **34 통과(단위33/UI1), 7 skip, 0 실패**. skip은 실제 EventKit fixture 1, catalog HTTP fixture 1, 로컬 인증 API fixture 5. 이를 실서비스 검증으로 해석하지 않음.
- XcodeBuildMCP snapshot_ui 접근성 트리 조회는 automation session 생성 타임아웃으로 실패. screenshot과 XCUITest 성공과 구별한다. 전체 접근성 감사/회전별 UI/실기기/실제 API/캘린더 허용 흐름은 이번에 검증하지 않았다.
- `codesign --verify --deep --strict`는 원본/보완 모두 exit 1, `code object is not signed at all`. 예상된 무서명 상태로 서명 검증 통과가 아니다.
- ponytail-review: 설정·기존 자산·출처 문서만 추가, 새 런타임 래퍼/패키지/추상화 없음. 삭제 후보 없음.

## Release API origin 후속 — 20:41 KST

사용자가 데이터 게시 없이 API 연결만 요청했고 root가 `https://wid.io.kr`를 확정했다. Release 프로젝트 설정과 재생성 스크립트에 origin만 반영했으며 Debug/공유 URL/팀/서명/앱 기능은 변경하지 않았다. 앱에는 Supabase URL/key를 추가하지 않았다. APIClient가 `/v1`을 붙이므로 전체 `/v1/catalog` 경로를 설정값에 중복 입력하지 않았다.

root 전달 증거: nginx 정확 `/v1/catalog` GET/HEAD=200 JSON, POST=405, 다른 `/v1/`=404를 로컬 TLS에서 확인. **이 세션에서 해당 서버 검증을 재실행하지 않았으며 공개 인증서는 준비 중이다. 인터넷 신뢰/연결 성공으로 표시하지 않는다. published=0이고 데이터 게시 작업은 수행하지 않았다.**

후속 일반 iOS 기기 Release 무서명 archive exit 0, archive DearbyAPIURL=https://wid.io.kr 및 Release ATS 예외 없음 확인. 구조16/Ruby syntax/git diff --check 통과. Swift 소스 변경이 없어 이전 전체 테스트·lint를 재실행하지 않았으며 이전 결과와 구분한다.

후속 증거 경로는 `api-origin-archive.log`, `APIOriginArchive.xcresult`, `Dearby-api-origin-unsigned.xcarchive`, `api-origin-architecture.log`. 이전 `Dearby-metadata-unsigned.xcarchive`는 API 빈값이던 시점의 증거로 유지한다. ponytail-review: 기존 설정값과 generator 조건 한 줄만 변경, 추가 런타임 코드/추상화 없음.

## 최종 앱 번들 비밀정보 검사 — 20:44 KST

root의 공통 배포 차단 추적은 [#62](https://github.com/fixabley/dearby/issues/62). 소스 commit `9549f1e12f74f9f1c82e4a5e12ee5750f1993e66`의 Release 산출물 `Dearby-api-origin-unsigned.xcarchive/Products/Applications/Dearby.app`을 직접 검사했다. 이번 요청은 검증만 수행하여 앱 소스/프로젝트/PR head를 변경하지 않았다. CI 진행 중인 head를 유지하고 이 로컬 인계 문서와 카드에 결과를 남긴다.

- 번들 파일 전체 6개, 총 1,158,141 bytes를 순회. 파일별 SHA256/크기를 로컬 결과 JSON에 기록. symlink와 중첩 압축 파일은 0개.
- 파일명 검사: `.env*`, `*.env`, `*.env.*`, `env`, env JSON/YAML/plist/txt/config 후보 0개.
- 실행 파일을 포함한 모든 파일의 UTF-8/UTF-16 LE/BE 문자열, 파싱한 plist key/value, `assetutil --info` 결과를 검사. Supabase 문자열(대소문자 무시) 0개, `sb_publishable_`/`sb_secret_` 키 후보 0개, JWT 후보 0개. Supabase 발행자/anon/service_role JWT 후보도 0개.
- 앱 plist의 API origin이 승인된 origin과 일치하고 Release ATS 예외가 없음 확인. Supabase URL/key나 실제 env 비밀값을 외부 저장소/설정에서 가져와 비교하지 않았으며, 실제 값은 출력하지 않았다.
- **결론: 검사한 최종 무서명 앱 번들에서 Supabase URL/key 형식과 환경 파일을 발견하지 못했다.** 알려지지 않은 키 형식·임의 인코딩/난독화·커스텀 도메인까지 부재를 보증하는 검사는 아니다. root가 향후 서명/export 또는 설정을 바꿔 만드는 최종 배포 산출물은 재검사 대상이다. 공개 TLS/인터넷 연결·업로드 성공을 의미하지 않는다.

재현 스크립트: `apps/ios/.build/testflight-main/check-bundle-secrets.py`; 값이 아닌 건수·해시·검사 범위를 보존한 결과: `apps/ios/.build/testflight-main/bundle-secret-inspection.json`. 이번에는 앱 재빌드/기능 테스트를 반복하지 않았다. 문서만 갱신했고 ponytail-review 삭제 후보 없음.

## ASC 신규 앱 ID 일치 후속 — 20:51 KST

root가 Chrome ASC 앱 정보에서 확인해 전달한 신규 Dearby 앱: Apple ID `6817330226`, 기본 언어 한국어, SKU `dearby01`, 번들 ID `io.wid.dearby`. 이번 변경은 사용자 신규 등록과 매칭하기 위한 **Release 앱 타깃 PRODUCT_BUNDLE_IDENTIFIER 한 값**과 generator 대응 조건이다. Debug 앱 `com.dearby.dearby` 및 모든 테스트 타깃 ID는 유지했다. ASC 자체·Store 초안·팀/서명은 조작하지 않았다.

- main `0a045c4`가 포함된 원격 PR60 `2c57cdf760d1ca06c299b1a37f406ad168d9d980` 반영 후 빌드했다. 다른 미병합 발표/수집/어드민 브랜치는 반영하지 않았다.
- **새 일반 iOS 기기 Release 무서명 archive exit 0**. 실제 app plist와 archive ApplicationProperties 모두 `CFBundleIdentifier=io.wid.dearby`. `CFBundleShortVersionString=0.1.0`, `CFBundleVersion=1`, `DearbyAPIURL=https://wid.io.kr` 유지.
- 앱 아이콘/iPad 네 방향/암호화 bool/Release ATS 예외 없음 assertion, archive 및 app plist lint 통과. 구조16/Ruby syntax/git diff --check 통과. Debug·테스트 ID가 기존값임을 프로젝트에서 확인했다. Swift 소스 변경이 없으므로 이전 UI/단위/lint 결과를 재실행한 것으로 기록하지 않는다.
- 새 bundle 전체6파일/1,158,135 bytes 대상으로 앞 절의 동일 검사 재실행: Supabase 문자열0, key prefix0, JWT0, env파일0, symlink/중첩압축0. 실제 비밀값 읽기/출력 없음. 서버 Supabase 변경이 main에 병합된 뒤에도 해당 앱 산출물에서 후보를 발견하지 못했다. 앞 절의 검사 한계와 최종 signed/export 재검사 조건은 유지.
- `codesign --verify --deep --strict` exit 1, `code object is not signed at all` 확인. 이 산출물은 업로드 가능한 signed archive가 아니다. 공개 TLS/인터넷 연결 결과도 이번 검사에 포함되지 않는다.
- 증거: `.build/testflight-main/asc-id-archive.log`, `ASCIDArchive.xcresult`, `Dearby-asc-id-unsigned.xcarchive`, `asc-id-architecture.log`, `check-asc-id-bundle-secrets.py`, `asc-id-bundle-secret-inspection.json` (모두 apps/ios 기준).
- 이전 20:44 번들검사 문서의 미커밋분도 이번 소스 변경과 함께 커밋/push한다. ponytail-review: 앱 설정 하나와 generator 조건으로 구현, 새 런타임 코드/추상화 없음.

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

원본 main 검사(20:25 KST)는 `archive.log`, `Archive.xcresult`, `inspection.json`, `release-build-settings.txt`, `Dearby-unsigned.xcarchive`로 보존했다. 당시 버전/아이콘/방향 누락은 이번 소스 보완으로 해소했으며 당시 API/서명 차단이 남아 있었다. 후속으로 Release origin만 확정했으며 공개 TLS/실연결과 서명 차단은 남아 있다. 원본 arm64/dSYM UUID 일치도 확인했다.

## root 인계·해소 조건

[PR #60](https://github.com/fixabley/dearby/pull/60), base main. 소스 커밋 `81e1c2c9177ba1b0698a0ac32ccdf979b9a54764` push 완료. 20:38 KST 조회에서 MERGEABLE, 원격 Native verification 진행 중(통과로 기록하지 않음). 최초 Git push는 캐시된 다른 GitHub 계정으로 403; 전역 인증 설정 변경 없이 해당 push 명령에만 기존 gh credential helper를 지정해 해결했다. root가 CI 결과를 확인하고 통합한다.

1. **Apple 서명/권한**: root 전달상 Xcode의 minjun jo 팀(Admin)은 있으나 인증서 목록은 비어 있었음. 후속으로 사용자 ASC Dearby 앱 직접 등록(Apple ID6817330226, io.wid.dearby)이 확인되어 Release ID를 일치시켰음. 이 세션은 인증을 재조사·생성하지 않았다. root가 승인된 팀/서명/프로파일·ASC 앱 레코드/권한으로 signed archive/export/validation을 수행해야 한다.
2. **API**: Release origin은 https://wid.io.kr 확정/반영. root가 공개 인증서 신뢰와 인터넷에서 실제 catalog 응답을 검증해야 한다. 현재 root 전달 published=0, 게시하지 않았다. nginx/DNS/공유기 변경은 이 checkout에서 하지 않는다.
3. **최종 번호·선언**: root 후속 승인에 따라 TestFlight `0.1.0 (1)` 유지, ASC Store 초안1.0은 변경하지 않음. root가 실제 업로드의 빌드 번호 사용 가능 여부와 최종 암호화 응답을 확인한다. 향후 외부 crypto 의존성 추가 시 선언 재검토.
4. **main 통합 후 배포**: PR을 main에 통합한 commit에서 재archive. 이 브랜치에서 upload하지 않음. 기능/UI/수집/어드민 미병합 변경은 포함하지 않는다.
5. **검증 잔여**: TestFlight processing/테스터 전달, ASC privacy report 및 배포 validation, 실제 HTTPS API, 실기기/회전별 UI는 별도. 앱 PrivacyInfo.xcprivacy는 현재 없고 검토 범위에서 직접 required-reason API 사용을 찾지 못했으나 이 사실만으로 최종 privacy 통과를 단정하지 않음.

캘린더 full-access 한국어 목적 문구와 iOS 18+ requestFullAccessToEvents 대응은 보존했다. 권한은 상세의 사용자 동작 후 요청하고 시각 미확인 활동은 요청 전 unknown 처리하는 기존 main 구현 유지. 카메라/사진 추가 문구와 숨긴 기능/데이터도 삭제하지 않았다.

## 공식 근거·세션

[Apple 배포 준비](https://developer.apple.com/documentation/xcode/preparing-your-app-for-distribution), [단일 크기 AppIcon](https://developer.apple.com/documentation/xcode/configuring-your-app-icon), [암호화 선언 안내](https://developer.apple.com/documentation/security/complying-with-encryption-export-regulations)를 참고했다. OS 제공 암호화에 관한 Apple 기술 안내에 따라 구성했으며 배포자의 법적 판단을 대신하지 않는다.

Orca worktree instance `a46ac287-2318-4c3d-92bd-789038ff1a17`, runtime terminal `term_a6d0db39-eb0d-42ae-88dd-6b760a4eaac0` (2026-09-29 확인, 영구 세션 ID 아님). 카드·세션·worktree·archive 유지. 공통 GitHub 차단 이슈는 root가 작성하므로 중복 이슈 없음.
