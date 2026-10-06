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

## 로그인·명함 발행 연결 (2026-10-06)

`entities/account`의 `AccountClient`와 Keystore AES-GCM `SessionVault`(새 이름 `account.session.v2`, 이전 앱의 `session` 저장은 읽거나 지우지 않음), `app/AccountViewModel`이 있다. 화면 연결은 다음 PR이다. 운영 `/v1/auth`는 닫혀 있고 #89 미설정이라 운영 동작 완료가 아니다. 로컬 실제 API 확인 준비는 [iOS README](../ios/README.md#로그인명함-발행-연결-2026-10-06)와 같고, `DEARBY_REAL_API_ORIGIN`·`DEARBY_REAL_MAILPIT`을 주고 `./gradlew testDebugUnitTest --tests com.dearby.nativeapp.AccountRealApiTest`를 실행한다(값이 없으면 건너뜀).

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME=/Users/jominjun/Library/Android/sdk
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
python3 scripts/check-fsd.py --self-test
```

실제 실행 결과는 `docs/VERIFICATION.md`, 구조는 `ARCHITECTURE.md`를 참고하세요.
