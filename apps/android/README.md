# Dearby Android 클릭형 프로토타입

선택된 시안의 흰색·청록색, 사진 카드, 상세 시간표, 신청 화면, 명함 화면을 Kotlin/Compose로 구현합니다. 서버 없이 고정 예시와 메모리 상태로 동작합니다.

- 발견: `GET /v1/catalog`(`entities/catalog/api/CatalogClient.kt`)의 모집 중 활동(웹과 같은 판정) → 상세. 빠른 신청과 상세 CTA는 https 공식 신청 주소를 외부 브라우저로 연다. 실패하면 오류와 다시 시도이며 예시를 섞지 않는다. `INTERNET` 권한은 실제 연결 범위에 한해 쓰고, http는 debug의 10.0.2.2·localhost·127.0.0.1만 허용한다(`src/debug` network security config).
- 테스트: 자동 테스트는 2026-10-06 사용자 요청으로 모두 지웠다. 사용자와 하나씩 다시 만든다. debug 전용 `ApiOrigin.debugOverride`는 남겨 두었다.
- 내 활동: 신청한 활동을 일정순으로 보여 줍니다. 컨퍼런스 1건이 신청된 상태로 시작합니다. 목록과 상세에서 켜고 끄는 `참여 확정 표시`는 사용자가 스스로 남기는 예시 표시이며 주최 측 확정이 아닙니다. 활동 즐겨찾기·조직 저장은 없습니다.
- QR: 예시 QR 표시·확대, 명함 선택·신규 생성·같은 ID 편집, 공유 메뉴, 예시 스캔 → 공개 카드. 카메라·파일·클립보드·공유 API는 사용하지 않습니다.
- 받은 명함: 이름·직무·활동 검색, 위아래 카드 넘김, 미교환/상호 교환 그룹, 상세 → 내 명함 선택 → 로컬 교환 상태. 외부 전달은 없습니다.
- 내 프로필: 비로그인 안내 → 예시 프로필, 이름·소개·연락처·활동 편집. 계정 연결이나 인증은 없습니다.
- 겹치는 시간: 고정 바쁜 시간 `2026-10-24 14:00~15:00 Asia/Seoul`을 비교합니다. 컨퍼런스는 1시간 겹치고 캠프·밋업은 겹침이 없습니다. OS 권한을 요청하지 않습니다.

예시 모집 상태는 날짜가 지나도 바뀌지 않습니다. 모든 편집·저장·신청·교환 상태는 프로세스 종료 후 초기화됩니다. 실제 접수·선정·계정 생성·전송 완료를 주장하지 않습니다.

## 수정할 위치

| 대상 | 위치 (`app/src/main/java/com/dearby/nativeapp/` 기준) |
| --- | --- |
| 활동 3개·일정·링크 | `entities/catalog/model/CatalogModel.kt`의 `demoActivities` |
| 가상 인물·연락처·명함·활동 이력 | `app/DemoFixtures.kt` |
| 메모리 필터·신청·참여 확정 표시 | `app/CatalogViewModel.kt` |
| 메모리 프로필·명함 작성/편집·교환 그룹 | `app/DemoViewModel.kt` |
| 다섯 탭·화면 연결 | `app/DearbyApp.kt`, `app/CatalogRoute.kt` |
| 화면과 공통 표시 | `pages/`, `widgets/card/cardContent/`, `shared/ui/` |

사진·QR 리소스는 `app/src/main/res/drawable-nodpi/prototype_*.png`이며 로고와 기존 이미지 자산도 보존합니다. 모든 예시 링크/QR 목적지는 `https://example.com`입니다.

HTTP/Repository/Room/Keystore/기기 캘린더/WebView·인증 실행 코드와 미사용 의존성·INTERNET/READ_CALENDAR 권한·명함 딥링크를 제거했습니다. 명함·프로필·내 활동·QR은 화면 흐름만 다시 구성했습니다. applicationId `com.dearby.nativeapp`은 유지하며 이전 설치 데이터는 읽거나 삭제하거나 마이그레이션하지 않습니다. 과거 서비스 텍스트 기록은 `docs/archive`에 보존합니다. 현재 검증 증거는 [selected-card-flows](evidence/selected-card-flows/README.md)이며, 대체된 중간 증거는 [정리 기록](docs/cleanup-2026-10-04.md)에 따라 제거했습니다.

## 연결 설정

계약 `shared/contracts/native-v1.md`의 "연결 설정"을 따른다. 코드에는 운영 도메인을 두지 않는다.

- 환경값 `DEARBY_API_ORIGIN`, `DEARBY_WEB_ORIGIN`(없으면 같은 이름의 Gradle 속성 `-P`)이 `BuildConfig.API_ORIGIN`·`WEB_ORIGIN`과 manifest placeholder `dearbyWebHost`로 들어간다.
- release: 빌드가 실제로 돌 때(`preReleaseBuild`)만 두 값이 `https://<도메인>`(IP·포트·경로·대문자 없음)인지 검사하고, 아니면 실패한다. debug 빌드·단위 테스트·Studio sync에는 영향이 없다.
- debug: 값이 없으면 에뮬레이터 호스트의 `http://10.0.2.2:3000`(API)과 `http://10.0.2.2:3210`(웹)을 쓴다.
- App Links: `MainActivity`에 `https://${dearbyWebHost}/s/` intent filter(`autoVerify`)가 있다. 받은 `<웹 origin>/s/<UUID>`는 `shared/config/AppLinks.kt`가 공유 ID로 바꾼다. 공유 명함 화면은 아직 연결하지 않았고, 링크를 받으면 그 사실만 안내한다. debug의 http origin은 https 전용 App Links와 맞지 않으므로 debug에서는 명시 intent(계측 테스트 `LinkRoutingTest`)로 확인한다. 실제 검증은 `assetlinks.json` 배포(#90·#91) 뒤에만 가능하다.

## 패스키 로그인 (2026-10-10)

계약 [native-v1 "패스키 로그인"](../../shared/contracts/native-v1.md)을 따른다. 이메일 인증번호 로그인은 없앴다.

- 흐름: 로그인 시트 `pages/account/SignInSheet.kt`의 "패스키로 로그인"(기본)·"새 패스키로 시작"(가입) → `app/AccountViewModel` → `entities/account/api/AccountClient`의 `/v1/auth/passkeys/*` 옵션 요청 → `features/passkey/Passkeys`(Credential Manager)에 `options` JSON을 그대로 넘김 → 결과 JSON을 `credential`로 완료 요청 → TokenPair.
- 저장: Keystore AES-GCM `SessionVault` 새 이름 `account.tokens.v3`에 access·refresh token과 userId를 둔다. `expiresIn`은 저장하지 않고 401을 받으면 갱신한다. 이전 저장(`session`, 이메일 로그인의 `account.session.v2`)은 읽거나 지우지 않는다.
- 401: `/v1/auth/refresh`를 한 번 시도하고(동시에 401을 받아도 갱신은 한 번), 거절되면 토큰을 지우고 로그인 시트로 돌아간다. 네트워크 오류면 토큰을 남긴다. 로그아웃(`AccountViewModel.signOut`)은 `/v1/auth/logout` 뒤 네트워크 실패여도 기기 토큰을 지운다. 지금 로그아웃 버튼은 없다.
- 사용자가 패스키 창을 닫으면 오류 없이 시트로 돌아간다. 기기에 패스키가 없으면 "새 패스키로 시작"을 안내한다. 패스키는 Android 9(API 28) 이상에서만 쓸 수 있다(minSdk 26).

### 도메인 연결 (`wid.io.kr`)

패스키는 RP ID `wid.io.kr`이 이 앱을 신뢰해야 동작한다. 아래 두 값을 서명 인증서마다(디버그, 배포) 넣는다. Play 앱 서명을 쓰면 배포 값은 업로드 키가 아니라 Play Console의 "앱 서명 키 인증서" SHA-256이다.

1. `https://wid.io.kr/.well-known/assetlinks.json`(운영 ingress가 낸다):

   ```json
   [{
     "relation": ["delegate_permission/common.get_login_creds"],
     "target": { "namespace": "android_app", "package_name": "com.dearby.nativeapp",
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
