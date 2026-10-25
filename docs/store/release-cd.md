# 스토어 심사 제출 CD — 2026-10-22

사용자 요청(2026-10-22): "CD 워크플로우에 앱스토어 심사 신청도 추가해 줄 수 있을까? 플레이스토어도. 필요하면 시뮬레이터 켜고 스크린샷 해도 좋아." 시뮬레이터 허용은 **스토어 스크린샷 캡처에만** 해당한다(화면 흐름 확인은 계속 실기기만).

## 흐름

두 스토어 워크플로는 **수동 실행(workflow_dispatch)만** 한다(사용자 결정 2026-10-22). push·병합·일정·다른 워크플로 호출로는 돌지 않는다.

| 스토어 | 워크플로 | 하는 일 |
| --- | --- | --- |
| App Store | `ios-testflight.yml` (매일 09:00 KST·수동, 기존) | 서명한 Release를 TestFlight에 올린다. 심사 제출과 연결하지 않는다. |
| App Store | `ios-appstore-submit.yml` (수동) | `apps/ios/fastlane` `submit` 레인: 처리 끝난 TestFlight 빌드(지정 번호, 없으면 그 버전의 최신)를 최대 40분 기다려 App Store 버전에 붙이고 `metadata/ko`·`screenshots/ko`를 올린 뒤 `submit`이면 심사 제출. 승인 후 출시는 수동(`automatic_release: false`). 바이너리를 올리지 않으니 ubuntu에서 돈다. |
| Google Play | `android-play-release.yml` (수동) | 업로드 키로 서명한 `bundleRelease`(versionCode = run 번호 + 1)를 `fastlane/metadata/android/ko-KR`와 함께 `internal` 또는 `production` 트랙에 올린다. `production`이 Google 심사 제출이다. |

- 문안 원본은 이제 fastlane 메타데이터 파일이다(`apps/ios/fastlane/metadata/ko/*.txt`, `apps/android/fastlane/metadata/android/ko-KR/*.txt`). [2026-10-19 문안](listing-2026-10-19.md)은 그 시점 초안·설문 답안 기록으로 남긴다.
- `release_notes.txt`(iOS)는 저장소에 두지 않는다. App Store는 첫 버전에 "새로운 기능"을 받지 않으므로 `whats_new` 입력이 있을 때만 실행 중에 만든다. Play `changelogs/default.txt`는 저장소 기본값이 있고 `release_notes` 입력이 덮어쓴다.
- 카테고리(주 `PRODUCTIVITY`, 보조 `BUSINESS`)와 저작권(`2026 Dearby`)은 문안 초안의 제안값으로 넣었다. 운영자 명의가 정해지면 `metadata/copyright.txt`를 고친다.

## 필요한 저장소 설정(이름만)

- 이미 있음(TestFlight): secrets `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8`, `IOS_DIST_P12_BASE64`, `IOS_DIST_P12_PASSWORD`, `IOS_APPSTORE_PROFILE_BASE64`; vars `APPLE_TEAM_ID`, `DEARBY_API_ORIGIN`. 제출 워크플로는 앞의 세 키만 쓴다(API 키 역할은 App Manager 이상).
- Play 서명(2026-10-22 등록됨): secrets `ANDROID_UPLOAD_JKS_BASE64`(`~/.dearby-deploy/android-upload.jks`의 base64; 워크플로가 `$RUNNER_TEMP/upload.jks`로 풀고 `ANDROID_KEYSTORE_PATH`로 넘긴다), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`(`android-signing.env`와 같은 이름).
- Play 접근: Google Cloud 조직이 서비스 계정 키 생성을 막아(`iam.disableServiceAccountKeyCreation`) JSON 키는 쓰지 않고 **Workload Identity Federation**으로 인증한다. repository variables `GCP_WIF_PROVIDER`(`projects/<번호>/locations/global/workloadIdentityPools/<풀>/providers/<공급자>`), `PLAY_SERVICE_ACCOUNT`(서비스 계정 이메일). `google-github-actions/auth@v2`가 키 없는 `external_account` 자격 파일을 만들고 fastlane supply(2.241.1 이상, Gemfile 고정)가 그것을 `json_key`로 읽는다. 둘 중 하나라도 비면 첫 단계에서 실패한다.
- 선택: secret `GOOGLE_SERVER_CLIENT_ID`(없으면 경고만 내고 구글 로그인 버튼이 없는 빌드). var `DEARBY_API_ORIGIN`은 공용. `DEARBY_WEB_ORIGIN`은 비우면 `https://dearby.wid.io.kr`. vars `ADMOB_ANDROID_APP_ID`·`ADMOB_ANDROID_QUEUE_NATIVE_UNIT_ID`(맞춤 대기열 끝 광고, 둘 다 있어야 광고를 요청하고 하나라도 없으면 경고만 내고 광고 장면이 없는 빌드).

## 스크린샷

- Debug 빌드에만 있는 장면 훅: iOS 실행 인자 `-DearbyScreenshot <scene>`(`Sources/shared/config/ScreenshotScene.swift`, `#if DEBUG`), Android 인텐트 extra `dearbyScreenshot`(`app/ScreenshotScene.kt`, `BuildConfig.DEBUG`일 때만 읽음). 장면은 `discover`, `discover-expanded`(첫 프로그램 펼침), `detail`(첫 프로그램의 첫 모집 중·예정 활동), `mine`(그 활동을 내 활동에 담은 상태), `queue`(오늘의 맞춤 대기열 첫 카드, 추천 설정 퍼널·`menu_open` 없이 바로), `favorites-calendar`(내 활동 탭 즐겨찾기의 캘린더 보기), `cards`, `settings`. 스크립트 기본 순서(파일 이름 순서 = 스토어 순서)는 `discover discover-expanded queue detail mine favorites-calendar cards settings`.
- `bash apps/ios/scripts/capture_screenshots.sh`: iPhone 17 Pro Max·iPad Pro 13-inch 시뮬레이터, 상태 막대 9:41·배터리 100%, 운영 API로 Debug 빌드 → `apps/ios/fastlane/screenshots/ko/{iPhone69,iPad13}_NN_<scene>.png`.
- `bash apps/android/scripts/capture_screenshots.sh`: 연결된 **실기기**에 운영 API Debug 빌드를 설치하고 데모 모드 상태 막대, 화면 크기 `1080x2160`(Play는 긴 변이 짧은 변의 두 배를 넘으면 거부)으로 캡처 → `…/ko-KR/images/phoneScreenshots/NN_<scene>.png`. 끝나면 데모 모드·화면 크기를 되돌린다.
- `queue`·`favorites-calendar`는 **로그인이 필요하다**(비로그인이면 보일 내용이 없다). 즐겨찾기 캘린더는 계정에 일정이 있는 즐겨찾기가 있어야 채워지고, 이 장면이 고른 `캘린더` 보기는 기기에 저장된 채 남는다. 대기열 장면을 열면 그 계정에 대기열 반응 신호가 기록될 수 있다.
- 명함·설정(과 로그인 시 내 활동)도 계정이 있어야 의미 있는 화면이다. 캡처 전에 각 시뮬레이터·기기의 Debug 앱에서 한 번 로그인한다. 시연용 계정의 명함 내용은 공개 스크린샷에 실리므로 실제 개인정보가 아닌 값으로 채운다.

## 콘솔에서 남은 수동 단계

App Store Connect

- 앱 개인정보(App Privacy) 라벨 — [초안](listing-2026-10-19.md) "App Privacy 답안 초안".
- 연령 등급 설문, 가격·배포 국가, 심사 연락처(이름·전화·이메일)와 심사 노트·데모 계정 안내: **[연락 이메일]**, **[테스트 계정 안내]**. 패스키·Apple·Google만 있으므로 심사용 로그인 방법을 노트에 적어야 한다.
- 첫 제출 전 `metadata/ko`의 지원 URL(`https://dearby.wid.io.kr`)이 실제 연락 수단을 보여 주는지 확인(가이드라인 1.5). 별도 지원 페이지가 생기면 `support_url.txt`를 바꾼다: **[지원 페이지]**.

Google Play Console

- 앱 만들기(패키지 `io.wid.dearby`), Play 앱 서명 등록, **첫 AAB 수동 업로드**(API는 앱이 콘솔에 있어야 하고 첫 업로드는 콘솔에서 한다; versionCode 1).
- Google Cloud: Google Play Android Developer API 사용 설정, 서비스 계정 생성(키 없음), GitHub OIDC 공급자가 있는 Workload Identity 풀 생성(속성 조건으로 이 저장소만 허용, 예: `assertion.repository == 'fixabley/dearby'`), 그 풀의 주체에 서비스 계정 `roles/iam.workloadIdentityUser` 부여. 값은 위 두 variable에 넣는다.
- Play Console "사용자 및 권한"에서 그 서비스 계정 이메일을 초대하고 이 앱에 출시 권한을 준다.
- 데이터 보안(Data safety), `READ_CALENDAR` 권한 선언, 콘텐츠 등급, 타깃층, 광고 없음, 개인정보처리방침 URL, 앱 액세스(심사용 로그인 안내) — [초안](listing-2026-10-19.md).
- 앱이 아직 한 번도 게시되지 않았으면 Play API는 `draft` 상태 출시만 받는다. 첫 실행은 `track=internal`, `status=draft`로 하고 콘솔에서 출시를 마친다.
- 새 개인 개발자 계정이면 프로덕션 신청 전 비공개 테스트 요건(테스터 수·기간)이 붙을 수 있다 — 계정 유형에 따라 확인.

## 출시 문안의 전제

설명·프로모션 문안에는 2026-10-22 기준 **아직 main에 없는** 두 기능이 들어 있다: `캘린더에 추가`(계약 "내 캘린더에 추가", `ee27a568`)와 로그인 시 계정에 저장되는 내 활동(`feat/user-activities-server`). 두 기능이 제출하는 빌드에 들어가기 전에는 심사 제출(`submit`/`production`)을 하지 않는다. 설명에 없는 기능을 적으면 App Store 2.3.1 거절 사유다.
