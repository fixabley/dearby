# 스토어 심사 제출 CD — 2026-10-22

사용자 요청(2026-10-22): "CD 워크플로우에 앱스토어 심사 신청도 추가해 줄 수 있을까? 플레이스토어도. 필요하면 시뮬레이터 켜고 스크린샷 해도 좋아." 시뮬레이터 허용은 **스토어 스크린샷 캡처에만** 해당한다(화면 흐름 확인은 계속 실기기만).

## 흐름

두 스토어 워크플로는 **수동 실행(workflow_dispatch)만** 한다(사용자 결정 2026-10-22). push·병합·일정·다른 워크플로 호출로는 돌지 않는다. 기능 PR은 `dev`를 base로 올리고, `dev`→`main` 묶음 병합 시점에만 배포한다(사용자 지시 2026-10-27).

| 스토어 | 워크플로 | 하는 일 |
| --- | --- | --- |
| App Store | `ios-testflight.yml` (매일 09:00 KST·수동, 기존) | 서명한 Release를 TestFlight에 올린다(내부 테스터). 수동 실행에 `external=true`를 주면 `apps/ios/fastlane` `distribute_external` 레인이 처리 완료를 최대 40분 기다린 뒤 그 빌드를 외부 그룹(var `TESTFLIGHT_EXTERNAL_GROUP`, 기본 `비공개 테스터`)에 넣고 **TestFlight 베타 심사**에 제출한다(App Store 심사는 아래 submit 워크플로가 별도). "테스트할 내용"은 입력 `changelog`, 비우면 마지막 커밋 제목. 일정 실행은 항상 내부만. |
| App Store | `ios-appstore-submit.yml` (수동) | `apps/ios/fastlane` `submit` 레인: 처리 끝난 TestFlight 빌드(지정 번호, 없으면 그 버전의 최신)를 최대 40분 기다려 App Store 버전에 붙이고 `metadata/ko`·`screenshots/ko`를 올린 뒤 `submit`이면 심사 제출. 승인 후 출시는 수동(`automatic_release: false`). 바이너리를 올리지 않으니 ubuntu에서 돈다. |
| Google Play | `android-play-release.yml` (수동) | 업로드 키로 서명한 `bundleRelease`(versionCode = run 번호 + 1)를 `fastlane/metadata/android/ko-KR`와 함께 `internal`·`closed`·`production` 트랙에 올린다. `closed`는 var `PLAY_CLOSED_TRACK`에 적은 비공개 테스트 트랙(없으면 `alpha`)이고, `production`이 Google 심사 제출이다. |

- 문안 원본은 이제 fastlane 메타데이터 파일이다(`apps/ios/fastlane/metadata/ko/*.txt`, `apps/android/fastlane/metadata/android/ko-KR/*.txt`). [2026-10-25 문안·개인정보 답안](listing-2026-10-25.md)이 현재 기준이고, [2026-10-19 문안](listing-2026-10-19.md)은 그 시점 기록으로 남긴다.
- `release_notes.txt`(iOS)는 저장소에 두지 않는다. App Store는 첫 버전에 "새로운 기능"을 받지 않으므로 `whats_new` 입력이 있을 때만 실행 중에 만든다. Play `changelogs/default.txt`는 저장소 기본값이 있고 `release_notes` 입력이 덮어쓴다.
- 카테고리는 주 `BUSINESS`, 보조 `EDUCATION`(2026-10-25 사용자 결정, Play도 비즈니스)이다. 저작권(`2026 Dearby`)은 문안 초안의 제안값이다. 운영자 명의가 정해지면 `metadata/copyright.txt`를 고친다.

## 필요한 저장소 설정(이름만)

- 이미 있음(TestFlight): secrets `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8`, `IOS_DIST_P12_BASE64`, `IOS_DIST_P12_PASSWORD`, `IOS_APPSTORE_PROFILE_BASE64`; vars `APPLE_TEAM_ID`, `DEARBY_API_ORIGIN`. 제출 워크플로는 앞의 세 키만 쓴다(API 키 역할은 App Manager 이상).
- Play 서명(2026-10-22 등록됨): secrets `ANDROID_UPLOAD_JKS_BASE64`(`~/.dearby-deploy/android-upload.jks`의 base64; 워크플로가 `$RUNNER_TEMP/upload.jks`로 풀고 `ANDROID_KEYSTORE_PATH`로 넘긴다), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`(`android-signing.env`와 같은 이름).
- Play 접근: Google Cloud 조직이 서비스 계정 키 생성을 막아(`iam.disableServiceAccountKeyCreation`) JSON 키는 쓰지 않고 **Workload Identity Federation**으로 인증한다. repository variables `GCP_WIF_PROVIDER`(`projects/<번호>/locations/global/workloadIdentityPools/<풀>/providers/<공급자>`), `PLAY_SERVICE_ACCOUNT`(서비스 계정 이메일). `google-github-actions/auth@v2`가 키 없는 `external_account` 자격 파일을 만들고 fastlane supply(2.241.1 이상, Gemfile 고정)가 그것을 `json_key`로 읽는다. 둘 중 하나라도 비면 첫 단계에서 실패한다.
- 비공개 테스터 배포(2026-10-27): vars `PLAY_CLOSED_TRACK`(비공개 테스트 트랙 이름; Play Console › 테스트 › 비공개 테스트 › 트랙 관리에서 확인, 기본 트랙은 `alpha`), `TESTFLIGHT_EXTERNAL_GROUP`(App Store Connect › TestFlight의 외부 그룹 이름, 비우면 `비공개 테스터`). 그룹이 없으면 레인이 처리 대기 전에 실패한다.
- 선택: secret `GOOGLE_SERVER_CLIENT_ID`(없으면 경고만 내고 구글 로그인 버튼이 없는 빌드). var `DEARBY_API_ORIGIN`은 공용. `DEARBY_WEB_ORIGIN`은 비우면 `https://dearby.wid.io.kr`. vars `ADMOB_ANDROID_APP_ID`·`ADMOB_ANDROID_QUEUE_NATIVE_UNIT_ID`(맞춤 대기열 끝 광고, 둘 다 있어야 광고를 요청하고 하나라도 없으면 경고만 내고 광고 장면이 없는 빌드).

## 스크린샷

- Debug 빌드에만 있는 장면 훅: iOS 실행 인자 `-DearbyScreenshot <scene>`(`Sources/shared/config/ScreenshotScene.swift`, `#if DEBUG`), Android 인텐트 extra `dearbyScreenshot`(`app/ScreenshotScene.kt`, `BuildConfig.DEBUG`일 때만 읽음). 장면은 `discover`, `discover-expanded`(첫 프로그램 펼침), `detail`(첫 프로그램의 첫 모집 중·예정 활동), `mine`(그 활동을 내 활동에 담은 상태), `queue`(오늘의 맞춤 대기열 첫 카드, 추천 설정 퍼널·`menu_open` 없이 바로), `favorites`(내 활동 탭 즐겨찾기, 캘린더), `cards`, `settings`. 스크립트 기본 순서(파일 이름 순서 = 스토어 순서)는 `discover discover-expanded queue detail mine favorites cards settings`.
- `bash apps/ios/scripts/capture_screenshots.sh`: iPhone 17 Pro Max·iPad Pro 13-inch 시뮬레이터, 상태 막대 9:41·배터리 100%, 운영 API로 Debug 빌드 → `apps/ios/fastlane/screenshots/ko/{iPhone69,iPad13}_NN_<scene>.png`.
- `bash apps/android/scripts/capture_screenshots.sh`: 연결된 **실기기**에 운영 API Debug 빌드를 설치하고 데모 모드 상태 막대, 화면 크기 `1080x2160`(Play는 긴 변이 짧은 변의 두 배를 넘으면 거부)으로 캡처 → `…/ko-KR/images/phoneScreenshots/NN_<scene>.png`. 끝나면 데모 모드·화면 크기를 되돌린다.
- `queue`·`favorites`는 **로그인이 필요하다**(비로그인이면 보일 내용이 없다). 즐겨찾기 캘린더는 계정에 일정이 있는 즐겨찾기가 있어야 채워진다. 대기열 장면을 열면 그 계정에 대기열 반응 신호가 기록될 수 있다.
- 명함·설정(과 로그인 시 내 활동)도 계정이 있어야 의미 있는 화면이다. 캡처 전에 각 시뮬레이터·기기의 Debug 앱에서 한 번 로그인한다. 시연용 계정의 명함 내용은 공개 스크린샷에 실리므로 실제 개인정보가 아닌 값으로 채운다.

## 콘솔에서 남은 수동 단계

App Store Connect

- 앱 개인정보(App Privacy) 라벨 — [답안](listing-2026-10-25.md) "App Privacy(App Store) 답안".
- 연령 등급 설문, 가격·배포 국가, 심사 연락처(이름·전화·이메일)와 심사 노트·데모 계정 안내: **[연락 이메일]**, **[테스트 계정]**. 노트 초안은 [listing-2026-10-25.md](listing-2026-10-25.md) "심사 노트 초안".
- 첫 제출 전 `metadata/ko`의 지원 URL(`https://dearby.wid.io.kr`)이 실제 연락 수단을 보여 주는지 확인(가이드라인 1.5). 별도 지원 페이지가 생기면 `support_url.txt`를 바꾼다: **[지원 페이지]**.
- TestFlight 외부 배포(`external=true`) 전 **한 번**: TestFlight › 외부 그룹 만들기(이름 = `TESTFLIGHT_EXTERNAL_GROUP`, 기본 `비공개 테스터`), 베타 앱 심사 정보(연락처·데모 계정), 테스트 정보(베타 앱 설명·피드백 이메일·개인정보처리방침 URL). fastlane은 이 값을 보내지 않고 저장된 값을 쓴다. 첫 외부 빌드는 TestFlight 베타 심사(보통 하루 안)를 거친다. `ios-testflight.yml`은 `cancel-in-progress`라서 09:00 KST 일정 실행이 처리 대기 중인 외부 배포 실행을 취소할 수 있다.

Google Play Console

- 앱 만들기(패키지 `io.wid.dearby`), Play 앱 서명 등록, **첫 AAB 수동 업로드**(API는 앱이 콘솔에 있어야 하고 첫 업로드는 콘솔에서 한다; versionCode 1).
- Google Cloud: Google Play Android Developer API 사용 설정, 서비스 계정 생성(키 없음), GitHub OIDC 공급자가 있는 Workload Identity 풀 생성(속성 조건으로 이 저장소만 허용, 예: `assertion.repository == 'fixabley/dearby'`), 그 풀의 주체에 서비스 계정 `roles/iam.workloadIdentityUser` 부여. 값은 위 두 variable에 넣는다.
- Play Console "사용자 및 권한"에서 그 서비스 계정 이메일을 초대하고 이 앱에 출시 권한을 준다.
- 데이터 보안(Data safety), `READ_CALENDAR` 권한 선언, 콘텐츠 등급, 타깃층, 광고 없음, 개인정보처리방침 URL, 앱 액세스(심사용 로그인 안내) — [답안](listing-2026-10-25.md) "Play 데이터 보안" · "심사 노트 초안".
- 앱이 아직 한 번도 게시되지 않았으면 Play API는 `draft` 상태 출시만 받는다(`closed`도 같다). 첫 실행은 `track=internal`, `status=draft`로 하고 콘솔에서 출시를 마친다.
- 비공개 테스트(`track=closed`): 트랙은 콘솔에서 먼저 만들어져 있어야 하고 테스터 목록(이메일 목록·Google 그룹)은 콘솔에서 관리한다. 워크플로는 트랙에 출시만 만든다.
- 새 개인 개발자 계정이면 프로덕션 신청 전 비공개 테스트 요건(테스터 수·기간)이 붙을 수 있다 — 계정 유형에 따라 확인.

## 출시 문안의 전제

2026-10-26 쐐기 결정(컨퍼런스 당일 도구 + 명함 교환; [listing-2026-10-25.md](listing-2026-10-25.md) "2026-10-26 쐐기 결정과 문안 방향")에 맞춘 문안이 적는 기능 중 세션 시간표·세션 찜·`찜한 세션`·`프로그램 | 이벤트`·`활동 상태`·선발 과정/활동 일정 분리는 main `7e8e5632`(2026-10-26)에 있다. **세션 이동 알림(10분 전 로컬 알림)은 아직 main에 없다** — `feat/session-reminder-ios`·`feat/session-reminder-android`가 병합된 뒤의 커밋으로만 제출한다. 기능별 근거는 같은 문서 "문안이 주장하는 기능과 근거". 설명에 없는 기능을 적거나 있는 기능을 빠뜨리면 App Store 2.3.1 거절 사유이므로, 제출하는 빌드가 그 커밋 이후인지 확인한다.

심사 제출(`submit`/`production`) 전에 남은 것:

- `feat/session-reminder-ios`·`feat/session-reminder-android` 병합 확인(위).
- Play 그래픽 이미지(`featureGraphic.png`) 문구 `대외활동, 매일 맞춤으로`를 쐐기 문구로 재제작(listing 문서 "쐐기 결정과 문안 방향").
- 스크린샷을 listing 문서 "스크린샷 순서 제안"의 다섯 장면으로 재촬영(`~/Downloads/dearby-store/mockups`는 옛 화면).

- 웹 `/privacy`(시행일 2026-10-22)에 Firebase Analytics·기기 토큰과 시간대·알림 기록·즐겨찾기·추천 설정·맞춤 대기열 반응·지원 자격 정보를 더해 개인정보 라벨·데이터 보안 답안과 맞춘다(문구 게시는 사용자 확인 뒤).
- 운영 빌드에 Firebase 설정 파일(`GoogleService-Info.plist`·`google-services.json`)이 들어가는지 확인한다. 라벨·데이터 보안 답안은 Firebase가 켜진 빌드 기준이다.
- 시간표·찜한 세션·QR 보이기·받은 명함 상세의 스크린샷 장면 훅이 없다(위 "스크린샷" 장면 목록; `queue`·`favorites`는 있다). 자동 캡처로 가려면 더하고, 그전까지는 실기기에서 직접 찍는다.
