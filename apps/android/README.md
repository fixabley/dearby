# Dearby Android 클릭형 프로토타입

선택된 시안의 흰색·청록색, 사진 카드, 상세 시간표, 신청 화면, 명함 화면을 Kotlin/Compose로 구현합니다. 서버 없이 고정 예시와 메모리 상태로 동작합니다.

- 발견(2026-10-18 "발견 프로그램 묶음"): `GET /v1/discovery/programs`(`entities/activity/api/ActivityClient.kt`, 커서는 정수 오프셋)로 열린 활동이 있는 프로그램만 페이지 단위로 읽어 아코디언으로 보인다. 머리글(`widgets/activity/programHeader/ProgramHeaderRow`)은 이름·조직·열린 활동 수·가장 가까운 마감이고, 누르면 `GET /v1/activities?programId=`로 그 프로그램의 활동을 읽어 모집 중인 것만 기존 활동 카드로 보인다(최대 5페이지). 열린 활동이 1개인 프로그램과 검색어가 활동 이름에 맞은 프로그램은 펼친 채로 시작하며 펼침 상태는 메모리에만 둔다. 검색창 입력은 300ms 쉰 뒤 `q`로 보내고 첫 페이지부터 다시 읽으며, 목록 끝에 가까워지면 `nextCursor`로 다음 프로그램 페이지를 붙인다. 모집 상태는 `application` 기간만으로 정한다(기간 안 모집 중, 그 밖은 열린 활동이 아님). 카드·상세 CTA는 모집 중일 때 https `officialUrl`을 외부 브라우저로 열고, 그 밖에는 상세에서 공식 안내로 연다. `imageUrl`이 있으면 Coil로 보이고 없으면 일정 타일을 그린다. 실패하면 오류와 다시 시도, 프로그램이 없으면 "지금 모집 중인 프로그램이 없어요"이며 예시를 섞지 않는다. 유형 칩(참가등록·선발)은 Android 발견 화면에 없어 `participationType`을 보내지 않는다. 다른 검색어를 넣어도 내 활동의 신청 표시는 남는다. `INTERNET` 권한은 실제 연결 범위에 한해 쓰고, http는 debug의 10.0.2.2·localhost·127.0.0.1만 허용한다(`src/debug` network security config).
- 대상(2026-10-18 "대상자 조건"): 활동의 `eligibility`(JSON 키는 camelCase `field`·`fieldEn`·`values`·`valuesEn`·`note`·`noteEn`, 한글 어휘 + 영문 쌍, 범위는 `from`·`to`; snake_case는 DB 열에만)를 `entities/activity/model/Eligibility.kt`로 읽어 상세의 "대상" 절에 칩으로 보인다. 범위는 `나이 19~34세`·`경력 2년 이하`·`3학년 이상`·`2002년 이후 출생`·`팀 4명 이하`, 집합은 `재학상태 재학·휴학`, `기타`는 note 그대로, `any` 그룹은 `또는`으로 잇는다(`EligibilityGroup.lines()`). JSON을 읽지 못하면 조건 없음으로 보고 절을 숨긴다. 필터 UI는 아직 없다.
- 테스트: 자동 테스트는 2026-10-06 사용자 요청으로 모두 지웠다. 사용자와 하나씩 다시 만든다. debug 전용 `ApiOrigin.debugOverride`는 남겨 두었다.
- 내 활동(2026-10-18 "내 활동 진행 트랙"): 신청 표시한 활동을 진행 트랙 행(`widgets/activity/progress/ActivityProgressRow`)으로 보인다. 단계는 `entities/activity/model/ActivityStages.kt`가 활동의 `application`(맨 앞 `지원`, 마감 시각 기준), `schedules`(시작 순, 날짜 없는 일정은 그 뒤 API 순서), `activitySchedule`(맨 뒤 `활동`, 제목이 있으면 그 제목)로 만들고, 서울 날짜 기준으로 끝나지 않은 가장 이른 단계가 현재 단계다. 세부 일정이 없는 활동도 `지원 → 활동` 두 단계라 활동 기간이 끝나기 전까지 `진행 중`에 남는다. 행은 이름·프로그램 · 조직, 세그먼트 트랙(지난 단계 청록 채움, 현재 빗금 테두리, 다음 회색, 4칸 넘으면 `+N`), 단계 이름, 배지(`D-3 · 면접`, 진행 중인 날은 `D-DAY`, 모든 단계가 지나면 `지남`, 탈락 표시면 위험색 `탈락`, 날짜 없으면 `날짜 미확인 · 종류`)이며 접근성에서는 트랙이 "N단계 중 M단계 이름 진행 중" 하나로 읽힌다. 묶음은 `진행 중`과 접힌 `끝난 활동`(탈락 표시 또는 모든 단계 지남), 정렬은 다음 단계가 가까운 순(날짜 없음은 뒤). 행을 누르면 세로 타임라인(`pages/activity/ActivityProgressPage`)에서 제목 아래 `참여 확정 표시` 스위치를 켜고 끄며, 단계마다 `통과`·`대기`·`탈락`을 표시하고(`ActivityViewModel.mark`, 메모리에만), 시작·끝 시각이 있는 단계는 `겹치는 시간 확인`을 그 단계로 연다. 표시는 사용자가 남기는 것이며 주최 측 결과가 아니다. 일정이 없는 활동은 이전처럼 `신청함`/`참여 확정` 행과 `참여 확정 표시` 스위치만 보인다. 내 활동 검색창은 없고, 발견에서 다른 검색어를 넣어도 신청·단계 표시는 남는다.
- 내 명함(2026-10-18, 명함 공유 기능 삭제): 로그인한 계정의 발행 명함 목록(`GET /v1/cards`, `pages/cards/MyCardsPage`·`app/MyCardsViewModel`), 새 명함 만들기, 항목을 누르면 같은 꾸밈으로 다시 발행(`CardPublishViewModel.start(prefill)`). QR 표시·OS 공유·스캔·App Links·공유 명함 화면은 없다. 카메라 권한과 zxing·CameraX 의존성도 없앴다.
- 받은 명함: 계정 지갑(`GET /v1/wallet`)의 명함을 이름 첫 글자(한글 초성 ㄱ…ㅎ, 영문 A–Z, 그 밖 `#`)로 묶은 색인 목록(`pages/wallet`, 2026-10-18 계약 "받은 명함 목록 표시"). 묶음 머리글은 고정(sticky)되고, 묶음이 7개 이상이면 오른쪽 색인 레일을 눌러서·끌어서 이동한다(동작 줄이기에서는 애니메이션 없음). 행은 이니셜·굵은 이름·직무·소속 칩(가장 최근 승인 이력의 조직, `shared/lib/NameIndex.kt`·`walletSections`)이며 검색 중에도 묶음을 유지한다. 항목을 누르면 `WalletCardPage`로 명함을 본다. 활동별 묶음·공유 저장은 없고, 새로 받는 경로는 서버 공유 기능 삭제로 지금은 없다.
- 내 프로필: 비로그인 안내 → 예시 프로필, 이름·소개·연락처·활동 편집. 계정 연결이나 인증은 없습니다.
- 겹치는 시간: 고정 바쁜 시간 `2026-10-24 14:00~15:00 Asia/Seoul`을 비교합니다. 컨퍼런스는 1시간 겹치고 캠프·밋업은 겹침이 없습니다. OS 권한을 요청하지 않습니다.

예시 모집 상태는 날짜가 지나도 바뀌지 않습니다. 모든 편집·저장·신청·교환 상태는 프로세스 종료 후 초기화됩니다. 실제 접수·선정·계정 생성·전송 완료를 주장하지 않습니다.

## 수정할 위치

| 대상 | 위치 (`app/src/main/java/com/dearby/nativeapp/` 기준) |
| --- | --- |
| 활동 응답 해석·모집 상태 | `entities/activity/api/ActivityClient.kt`, `entities/activity/model/ActivityModels.kt` |
| 가상 인물·연락처·명함·활동 이력 | `app/DemoFixtures.kt` |
| 검색·페이지·신청·참여 확정 표시 | `app/ActivityViewModel.kt` |
| 메모리 프로필·명함 작성/편집·교환 그룹 | `app/DemoViewModel.kt` |
| 다섯 탭·화면 연결 | `app/DearbyApp.kt`, `app/ActivityRoute.kt` |
| 화면과 공통 표시 | `pages/`, `widgets/card/cardContent/`, `shared/ui/` |

사진·QR 리소스는 `app/src/main/res/drawable-nodpi/prototype_*.png`이며 로고와 기존 이미지 자산도 보존합니다. 모든 예시 링크/QR 목적지는 `https://example.com`입니다.

HTTP/Repository/Room/Keystore/기기 캘린더/WebView·인증 실행 코드와 미사용 의존성·INTERNET/READ_CALENDAR 권한·명함 딥링크를 제거했습니다. 명함·프로필·내 활동·QR은 화면 흐름만 다시 구성했습니다. applicationId `com.dearby.nativeapp`은 유지하며 이전 설치 데이터는 읽거나 삭제하거나 마이그레이션하지 않습니다. 과거 서비스 텍스트 기록은 `docs/archive`에 보존합니다. 현재 검증 증거는 [selected-card-flows](evidence/selected-card-flows/README.md)이며, 대체된 중간 증거는 [정리 기록](docs/cleanup-2026-10-04.md)에 따라 제거했습니다.

## 연결 설정

계약 `shared/contracts/native-v1.md`의 "연결 설정"을 따른다. 코드에는 운영 도메인을 두지 않는다.

- 환경값 `DEARBY_API_ORIGIN`(없으면 같은 이름의 Gradle 속성 `-P`)이 `BuildConfig.API_ORIGIN`으로 들어간다. `DEARBY_WEB_ORIGIN`·`WEB_ORIGIN`·`dearbyWebHost`는 공유 링크 전용이었고 2026-10-18 명함 공유 삭제와 함께 없앴다.
- release: 빌드가 실제로 돌 때(`preReleaseBuild`)만 값이 `https://<도메인>`(IP·포트·경로·대문자 없음)인지 검사하고, 아니면 실패한다. debug 빌드·단위 테스트·Studio sync에는 영향이 없다.
- debug: 값이 없으면 에뮬레이터 호스트의 `http://10.0.2.2:3000`을 쓴다.
- App Links(`/s/*`)와 `shared/config/AppLinks.kt`의 공유 링크 해석은 없다. `AppLinks.kt`에는 애플·구글 로그인 복귀(`dearby://auth/<apple|google>`, `authRedirect`) 해석만 남는다.

## 시작 화면 (2026-10-14)

- 스플래시: 실행할 때마다 AndroidX SplashScreen(`Theme.Dearby.Starting`)이 흰 배경에 로고(`drawable/splash_logo.xml`, `dearby_logo` 워드마크를 원형 마스크 안에 맞춤)를 보여 주고 바로 `Theme.Dearby`로 넘어간다. 인위적인 지연은 없다.
- 첫 실행 소개: 새 저장소 `dearby.onboarding`의 `dearby.onboarding.shown`이 없으면 `pages/onboarding/IntroPage`(로고, "패스키로 시작하기", "Apple로 계속하기", "Google로 계속하기", "둘러보기")를 띄운다. "패스키로 시작하기"는 기존 로그인 시트(`SignInSheet`·`AccountViewModel`)를 연다. 로그인에 성공하거나 "둘러보기"를 누르면 값을 남기고 탭 화면으로 간다. 시트를 닫거나 실패하면 소개 화면에 남는다. 이미 토큰이 있으면 소개 없이 값만 남긴다. 로그인 없이도 앱을 쓸 수 있다.

## 패스키 로그인 (2026-10-10)

계약 [native-v1 "패스키 로그인"](../../shared/contracts/native-v1.md)을 따른다. 이메일 인증번호 로그인은 없앴다.

- 흐름: 로그인 시트 `pages/account/SignInSheet.kt`의 "패스키로 로그인"(기본)·"새 패스키로 시작"(가입) → `app/AccountViewModel` → `entities/account/api/AccountClient`의 `/v1/auth/passkeys/*` 옵션 요청 → `features/passkey/Passkeys`(Credential Manager)에 `options` JSON을 그대로 넘김 → 결과 JSON을 `credential`로 완료 요청 → TokenPair.
- 저장: Keystore AES-GCM `SessionVault` 새 이름 `account.tokens.v3`에 access·refresh token만 둔다(서버 응답도 토큰 두 개뿐이다). 사용자 id·만료는 access token 클레임에 있고, 401을 받으면 갱신한다. 이전 저장(`session`, 이메일 로그인의 `account.session.v2`)은 읽거나 지우지 않는다.
- 401: `/v1/auth/refresh`를 한 번 시도하고(동시에 401을 받아도 갱신은 한 번), 거절되면 토큰을 지우고 로그인 시트로 돌아간다. 네트워크 오류면 토큰을 남긴다. 로그아웃(`AccountViewModel.signOut`)은 `/v1/auth/logout` 뒤 네트워크 실패여도 기기 토큰을 지운다. 지금 로그아웃 버튼은 없다.
- 계정 삭제(2026-10-16, 계약 "계정 삭제"): 로그아웃 버튼이 없어서 로그인 상태의 내 프로필(불러오기 실패 화면 포함) 아래에 빨간 "계정 삭제"를 둔다. 확인 창("계정을 삭제할까요?" / 삭제·취소) → `ProfileViewModel.deleteAccount` → `AccountViewModel.deleteAccount`(`authorized`로 `DELETE /v1/me`, 401이면 갱신 후 재시도) → 204면 `SessionVault`를 지우고 로그인하지 않은 상태로 돌아가 "계정을 삭제했어요. 기기에 남은 패스키는 비밀번호 관리자에서 지울 수 있어요."를 한 번 띄운다. 갱신이 거절되면 이미 로그아웃된 것으로 보고 만료 안내, 그 밖의 실패는 "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요."와 함께 토큰을 남긴다. 요청 중에는 버튼을 막는다. 기기에 저장한 계정 데이터는 토큰뿐이고, 로그아웃 상태가 되면 QR 탭의 명함·공유 링크와 프로필 캐시(메모리)도 비운다.
- 사용자가 패스키 창을 닫으면 오류 없이 시트로 돌아간다. 기기에 패스키가 없으면 "새 패스키로 시작"을 안내한다. 패스키는 Android 9(API 28) 이상에서만 쓸 수 있다(minSdk 26).

## 애플 로그인 (2026-10-16)

계약 [native-v1 "애플 로그인"](../../shared/contracts/native-v1.md)의 웹 방식을 따른다. 패스키와 함께 고를 수 있다.

- 버튼: 로그인 시트(`SignInSheet`, 화면마다 `app/SignInRoute`로 연다)와 첫 실행 소개에 검은 "Apple로 계속하기"(`shared/ui/AppleSignInButton`). 저장소에 애플 로고 자산이 없어 글자만 둔다.
- 시작: `features/oauth/OAuthVerifier`가 32바이트 무작위 verifier(base64url, 43자)와 SHA-256 해시를 만든다 → `/v1/auth/apple/web/start` → 받은 https `authorizeUrl`을 Custom Tab(`androidx.browser`)으로 연다. 탭을 연 뒤에는 기다리지 않으므로 사용자가 탭을 닫아도 시트가 멈추지 않는다. 503이면 "지금은 애플 로그인을 쓸 수 없어요."
- verifier 보존: `AccountViewModel`의 `SavedStateHandle`에 둔다(로그에 남기지 않는다). 회전·프로세스 종료 뒤 복원에도 남고, 결과 링크를 받으면 무엇이 오든 지운다(한 번만 쓴다).
- 돌아오기: `MainActivity`(`launchMode="singleTask"`)의 `dearby://auth/apple` intent filter. 같은 인스턴스가 `onNewIntent`로 받고 위의 Custom Tab이 닫힌다. `shared/config/AppLinks.authRedirect`는 `?code=<UUID>` 하나 또는 `?error=cancelled|failed`만 받고 나머지는 버린다. 재생성된 Activity가 같은 시작 intent를 다시 받으면 처리하지 않는다.
- 자동 연결(2026-10-17 사용자 결정): 202 직후 `PendingAccountRoute`가 먼저 `AccountViewModel.autoLinkPending`을 한 번 실행한다. `Passkeys.get`은 `setPreferImmediatelyAvailableCredentials(true)`라 이 기기(비밀번호 관리자)의 Dearby 패스키만 묻는다. 있으면 생체 인증 한 번으로 로그인·연결, `NoCredentialException`이면 묻지 않고 바로 `/v1/auth/apple/register`로 새 계정을 만든다. 사용자가 패스키 프롬프트를 닫으면(null) 그때만 아래 선택 화면을 띄운다(`AccountState.pending.auto`).
- 교환: `/v1/auth/apple/web/exchange` 200은 패스키와 같은 TokenPair로 `SessionVault`에 저장한다. 202는 `MainActivity`가 모든 화면 위에 `LinkAccountSheet`("새 계정으로 시작" → `/v1/auth/apple/register`, "기존 패스키 계정에 연결" → 패스키 로그인 뒤 그 토큰으로 `/v1/me/identities/apple`)를 띄운다. linkToken도 `SavedStateHandle`에 두고 선택·닫기에서 지운다. 연결이 실패하면 iOS와 같이 패스키 토큰을 저장하지 않고 `/v1/auth/logout`을 시도한 뒤 로그인하지 않은 상태로 안내한다: 401 "애플 로그인 시간이 지났어요. 애플로 다시 시작해 주세요."·400 "이미 다른 계정과 연결돼 있어서 연결하지 못했어요."(둘 다 선택 화면을 닫는다, 400은 `AccountError.BAD_REQUEST`), 503·네트워크 "지금은 애플 로그인을 쓸 수 없어요."(선택 화면 유지). 401·`error=failed`는 다시 시도 안내, `error=cancelled`는 안내 없음.
- 실제 로그인은 서버의 Services ID·Return URL(`APPLE_CLIENT_IDS`)이 있어야 확인할 수 있다.

## 구글 로그인 (2026-10-18)

계약 [native-v1 "구글 로그인"](../../shared/contracts/native-v1.md)을 따른다. 애플의 Android 웹 방식과 같은 코드를 쓴다(`features/oauth/OAuthVerifier`·`OAuthBrowser`, `AccountViewModel.startWeb(AuthProvider.GOOGLE, …)`, `/v1/auth/google/web/start` → Custom Tab → `dearby://auth/google` → `/v1/auth/google/web/exchange`). SDK는 없다.

- 버튼: 로그인 시트와 첫 실행 소개의 애플 버튼 아래 흰 "Google로 계속하기"(`shared/ui/DearbyGoogleButton`, 회색 테두리). 공식 "G" 로고 자산이 아직 없어 굵은 G 글자로 대신한다. 빌드 플래그는 없고, 서버가 `web/start`에 503을 돌려주면(`GOOGLE_CLIENT_ID` 미설정) `AccountState.googleAvailable`이 false가 되어 버튼을 숨긴다.
- 202(처음 보는 구글 계정)·연결·새 계정은 애플과 같은 `PendingAccountRoute`·`LinkAccountSheet`(제목 "처음 보는 Google 계정이에요")를 쓴다. `SavedStateHandle` 키 `web.provider`로 어느 제공자인지 기억한다. 다른 제공자의 code가 돌아오면 버린다.
- 실제 로그인은 서버의 `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`GOOGLE_CALLBACK_URL`이 있어야 확인할 수 있다.

### 도메인 연결 (`wid.io.kr`)

패스키는 RP ID `wid.io.kr`이 이 앱을 신뢰해야 동작한다. 아래 두 값을 서명 인증서마다(디버그, 배포) 넣는다. Play 앱 서명을 쓰면 배포 값은 업로드 키가 아니라 Play Console의 "앱 서명 키 인증서" SHA-256이다.

1. `https://wid.io.kr/.well-known/assetlinks.json`(운영 ingress가 낸다):

   ```json
   [{
     "relation": ["delegate_permission/common.get_login_creds"],
     "target": { "namespace": "android_app", "package_name": "io.wid.dearby",
       "sha256_cert_fingerprints": ["<서명 SHA-256, AA:BB:… 형식>"] }
   }]
   ```

2. API `WEBAUTHN_ALLOWED_ORIGINS`에 `android:apk-key-hash:<같은 SHA-256의 base64url, 패딩 없음>`.

`sha256_cert_fingerprints`는 배열이라 디버그·배포 지문을 한 항목에 함께 넣을 수 있다. 두 값을 구하는 명령(디버그 키스토어 예시, 배포는 키스토어·alias·비밀번호를 바꾼다):

```sh
KEYTOOL="$JAVA_HOME/bin/keytool"
# assetlinks용 hex. 한국어 로캘에서 keytool -v가 실패하면 -J-Duser.language=en을 붙인다.
"$KEYTOOL" -J-Duser.language=en -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android | grep SHA256:
# 허용 origin용 base64url
"$KEYTOOL" -exportcert -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android \
  | openssl sha256 -binary | openssl base64 | tr '+/' '-_' | tr -d '='
```

디버그 키스토어는 개발 기기마다 다르므로 각자 값을 넣는다. Play Console 값(hex)만 있으면 `echo <hex 콜론 제거> | xxd -r -p | openssl base64 | tr '+/' '-_' | tr -d '='`로 바꾼다. 로컬 API로 확인하려면 `DEARBY_API_ORIGIN`을 지정한다(Debug 기본 `http://10.0.2.2:3000`은 Kotlin API 포트와 다르다). 그래도 RP ID가 `wid.io.kr`이므로 assetlinks가 배포되기 전에는 패스키 창이 실패한다.

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME=/Users/jominjun/Library/Android/sdk
./gradlew :app:assembleDebug :app:lintDebug
python3 scripts/check-fsd.py --self-test
```

실제 실행 결과는 `docs/VERIFICATION.md`, 구조는 `ARCHITECTURE.md`를 참고하세요.

## 프로필·명함 v2 (2026-10-17)

계약 `native-v1.md` "프로필·명함 v2". 유비쿼터스 언어는 "활동"(조직·프로그램·활동·역할을 아우르는 코드 이름은 `ActivityInfo`).

- 내 프로필(`pages/profile/ProfilePage.kt`)은 데이터 입력만 한다. 칸을 바로 고치고, 불러온 프로필과 달라지면 "저장"·"원래대로" 막대가 나타난다(`ProfileViewModel.changed`). 저장은 `If-Match: <version>`을 보내고 409면 다시 불러오라고 안내한다. 변경 이력(`ProfileVersionsPage`)에서 버전을 보고 되돌린다(새 버전으로 저장).
- 처음 저장하기 전(`version` 0) 프로필 화면은 토스식 아코디언 퍼널(`ProfileSetupPage`, 2026-10-17 결정)이다: 이름 → 직무(선택) → 전화번호 → 이메일 → 소개(선택) 섹션이 순서대로 쌓이고, 열린 섹션은 하나뿐이며 답한 섹션은 56dp 행으로 접혀 탭하면 다시 열린다. 질문 `headlineMedium`(24sp 굵게), 입력 20sp, 좌우 24dp, 하단 버튼 56dp("다음" → 모두 답하면 "프로필 저장"). 퍼널 동안 하단 탭바를 숨긴다(`DearbyApp`, `ProfileViewState.settingUp`). 섹션 상태는 `remember`라 회전하면 첫 질문으로 돌아간다(입력값은 VM에 남는다). 저장되면 인라인 편집기로 바뀐다. 프로필 없이 명함 만들기를 열면 안내만 보여 준다.
- 직무(계약 "직무", 2026-10-17): 직함 문자열 대신 `jobs` 태그. `JobTagPicker`(widgets/profile/profileFields)가 `GET /v1/jobs` 검색 결과(`ProfileViewModel.searchJobs`, 300ms 디바운스)를 `InputChip`으로 여러 개(10개) 고르고, 없으면 직접 적는다(시계 아이콘 = 승인 대기). 퍼널의 직무 섹션과 편집기의 "직무" 항목이 같은 위젯을 쓴다. 명함·버전 화면의 `job`은 서버가 승인된 이름을 이어 준 문자열 그대로다.
- 이력은 단계별 입력(`HistoryFunnelPage`): 조직/활동 → 조직 → 프로그램 → (활동) → 역할 → 기간 → 설명 → 확인. 각 단계는 `ActivityInfoPicker`로 등록된 활동 정보를 검색(`GET /v1/organizations|programs|activities|roles`, 300ms 디바운스)하거나 그대로 적는다(직접 입력 = 승인 대기). 인증자료(`EvidenceFields`)는 저장된 이력에서 사진·PDF를 3개까지 올린다(파일 고르기는 `ProfileRoute`). `verified`가 아니면 "미확인".
- 명함 만들기(`CardComposerRoute`): 명함 이름을 적고 공개할 연락처·이력을 고른다. 본문은 앱 내장 기본 템플릿(`TemplateDocument.starter`, 이름·직무·소개·전화·이메일 토큰)이며 사용자가 고치는 UI는 없다(2026-10-18 사용자 결정으로 렌더링 화면 편집기·웹 버전 스위치 삭제). 제작 화면은 채운 결과를 읽기 전용 "미리보기"(`MarkdownBody`)로 보이고, 서버가 발행할 때 같은 템플릿을 채운다. 본문에 들어간 연락처는 자동 공개, 미승인 이력은 선택 불가. 내용이 바뀌면 "원래대로"가 나타난다.
- 400 오류는 ProblemDetail의 `detail`(`AccountException.detail`)을 그대로 보인다. 받은 명함 화면은 새 이력 모델(`CardHistoryState.verified`)과 마크다운 본문(`MarkdownBody`)이다.
- 2026-10-18: 명함 공유(공유 기록·QR·스캔·App Links·공유 저장·활동별 묶음)를 지웠다. 내 명함 탭에서 명함을 고친다. 실제 서버·기기 동작은 확인하지 않았다.
