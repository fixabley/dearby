# Android #38 검증 — 2026-09-27 KST

첫 명함 수직 구현 검증이며 Dearby 전체 서비스 출시 완료가 아니다. 원래 Android 앱이 삭제된 checkout에서 새 앱을 만들었으며 기존 2026-09-20 기록은 docs/archive에 보존했다.

## 실행 환경과 재현

- macOS ARM64, Android Studio JBR **25.0.2**, Gradle wrapper 9.3.1, AGP9.1.1, built-in Kotlin2.2.10, compile/target36/min26.
- 설치 SDK platform36/36.1, build-tools35/36.0.0 확인. 실제 사용 AVD `Dearby_Issue2_Test`, API36 Android16, serial `emulator-5554`; 다른 기기 조작 없음.
- 공식 의존성 출처/더 최신 안정 버전 존재/고정 이유는 README 및 dependency-registry.json. 검증한 조합을 최신 릴리스라고 주장하지 않는다.

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
python3 scripts/check-fsd.py --self-test
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
# Running emulator:
./gradlew :app:connectedDebugAndroidTest
```

JDK17은 공식 최소 지원 버전이며 이번 실제 실행은 JBR25.0.2다. Release APK는 unsigned 빌드 검증이며 배포/스토어 서명 완료가 아니다. localhost API origin은 빌드 property `dearbyApiUrl`에 `/v1` 없이 설정; 기본 구성은 미설정이고 HTTP는 debug만 허용한다.

## 최종 자동 검증

- 기본 API 미설정 구성 Debug·Release·AndroidTest APK, lintDebug 성공. **JVM18, 실패0/skip0**: 명시 선택만 전송, stale 선택 거절, 비공개 기본값, 부분/누락/충돌/실패 가져오기 ID 보존, 중복 저장의 원본 시각/맥락 보존, 계정 캐시 분리, 실제 loopback HTTP 요청/오류, 재생성 후 동일 전달 요청 재시도와 A-불명확/B/A 전환, 안전한 연락처 목적지, 기기 시간대 날짜 표시.
- **에뮬레이터11, 실패0**: Room DB 닫기/재열기와 부분 가져오기 보존, 실제 Keystore 암호화/새 인스턴스 복원, QR 픽셀 encode/decode와 모호한 payload 거절, 가져오기 기본 미선택/선택 콜백, 자동 전송 없음, 공개 선택/해제, 지갑 활동 검색과 영역 숨김, QR 메뉴/새 명함 안내, 프로필 CTA/로그인 gate, 펼침 전후 헤더 위치 고정과 연락처 클릭 콜백.
- 최종 source checker **32 Kotlin files + 24 self-test cases** 통과. 2362917의 Android 검사 방식(방향/동위 슬라이스/public API/State UI/OS 경계)을 복원·현재 slice에 조정. 컴파일러 수준 모듈 격리나 iOS 전용 두 레이어 거리 강제라고 주장하지 않는다.
- lint **0 errors, 15 warnings**. 새 의존성 버전 제안, KTX 편의 API와 legacy backup 제안을 원문 XML로 확인했다. backup 경고는 fullBackupContent=false가 소스와 merged manifest에 존재해도 남으며, 경고를 숨기지 않았다. baseline/광범위 disable 없음. 원본 결과는 로컬 app/build/reports/lint-results-debug.xml, JVM XML은 app/build/test-results/testDebugUnitTest.
- 최종 일반 테스트는 명시적으로 `ScreenTest,PersistenceTest`를 직접 instrumentation 실행해 화면 파일이 Gradle teardown에서 제거되지 않도록 했다. opt-in LocalApiIntegrationTest는 기본 실행 시 가정으로 제외되며 아래에서 따로 실행했다.

## 실제 로컬 API와 두 플랫폼 교환

조율 담당의 격리 서버 `http://127.0.0.1:53634`, emulator origin `http://10.0.2.2:53634`, 임시 DB 및 호스트 전용 테스트 메일 수신기 사용. 실제 운영 이메일을 보낸 검증이 아니다. OTP는 호스트 수신 파일에서 앱 private test file로 직접 주입하고 인증 후 삭제; 출력/커밋/스크린샷에 OTP나 session token을 남기지 않았다.

`LocalApiIntegrationTest`는 Android 제품의 실제 HttpClient/AuthRepository/ProfileRepository/CardRepository/GuestStore/WalletRepository/TokenVault를 사용했다:

1. 실제 POST challenge와 OTP session 성공, Keystore 토큰 보관.
2. 프로필 PUT/GET 및 선택 이메일만 담은 명함 POST/비로그인 GET 성공, 비공개 전화가 응답에서 제외됨을 assertion.
3. 명함 ID Room 저장→server import→확인된 ID만 제거→server wallet 확인.
4. Android에서 iOS 프로필로 전송, Room에 저장된 요청 재사용 및 동일 HTTP body replay 결과의 receiptId 동일 확인.
5. iOS 역방향 전송 후 Android GET wallet에서 iOS owner receipt가 존재하고 `reciprocal=true`임을 assertion. 실제 앱의 상호 교환 영역도 캡처.

공개 테스트 ID만 기록: Android profile `8057795d-d722-4b9d-b418-29c1008b8b66`, card `7ccde76f-802e-4cc0-98db-1b4db632dd6e`; iOS profile `c4d0f524-119f-42f2-9e3a-1b0808700f1b`. 이 검증은 native repository/API 실행이며 OTP 숫자를 실제 로그인 화면에 타이핑한 사용자 E2E라고 주장하지 않는다.

## 화면 증거

- `evidence/local-api-qr.png`: 실제 임시 서버 발행 명함, 흰색/청록 QR 및 하단 선택.
- `evidence/local-api-qr-expanded.png`, `qr-expanded-ax.json`: 앱 내부에서 QR만 있는 확대 화면과 접근성 노드, 다시 탭 복귀 확인.
- `evidence/local-api-reciprocal.png`: 실제 iOS 명함과 상호 교환 영역.
- `evidence/local-api-profile.png`: 실제 서버 계정의 전체 프로필. 테스트 계정의 비공개 연락처는 자신의 전체 프로필에서만 확인된다.
- `evidence/ui-final/fixture-*.png`: 독립 Compose UI 테스트의 합성 데이터이며 서비스 계정/전달 성공 증거가 아니다. QR, 프로필, 지갑, 선택 가져오기, 전송 선택, 연락처·이력 상세 화면을 검사했다.

이미지를 직접 열어 검토했다. 초기 보라색 선택색과 과도한 접힌 카드 높이를 고친 후 새 캡처를 남겼다. QR 확대 첫 즉시 캡처는 렌더 완료 전이어서 확대 증거로 쓰지 않고 AX 확인 후 다시 캡처했다. 작은 화면/큰 글자 전체 흐름·TalkBack·실물 카메라 스캔은 미검증이며 screenshot-perfect 복제를 주장하지 않는다.

## 실패 이력과 해소

- 최초 Room 계측 함수가 Boolean 반환으로 JUnit void 규칙에 실패 → Unit 반환으로 수정, 전체 계측 재실행 성공.
- Compose UI 입력이 transitive 구형 Espresso의 InputManager.getInstance에서 API36 오류 → 공식 수정 버전 Espresso3.7.0 명시, UI 전체 재실행 성공.
- 최초 역방향 교환 assertion은 iOS 전송 전 수신이 없어 실패 → 실제 역방향 전송 후 같은 조회 assertion 재실행 성공; 로컬 reciprocal을 임의 변경하지 않음.
- OTP private file 주입 최초 shell quoting 오류 → 출력 없이 private stdin 주입으로 수정; 코드/토큰 노출 없음.

## Ponytail 및 남은 범위

Ponytail 검토에서 AuthRepository의 미사용 DAO 매개변수·저장 import를 제거했다. 나머지 HTTP/Room/Keystore 경계와 typed UI State는 개인정보 선택·실패 보존·OS 수명·테스트 경계를 위해 유지한다. 별도 인터페이스/DI 프레임워크/추상 계층을 추가하지 않았다. 정확성·개인정보·접근성 회귀는 위 검사로 별도 확인했으며 Ponytail이 이를 대체하지 않는다.

- #42: 운영 SMTP 실제 수신·HTTPS 배포.
- #44/#43: 공개 HTTPS App Links/Universal Links·웹 fallback, 등록 활동 선택 API, 두 실기기 스캔. 설치 앱 개발 payload는 양 플랫폼 공통 `dearby://card/UUID`로 조율됨.
- 카메라는 외부 카메라의 촬영 결과/사진 선택을 실제 ZXing으로 해독한다. 실시간 연속 scanner가 아니며 낮은 preview 해상도에서 실패할 수 있어 선명한 사진 선택을 제공한다. QR 이미지 저장은 시스템 문서 저장 picker를 사용하며 실제 파일 선택 왕복은 이번 수동 검사에서 실행하지 않았다.
- #41/#36: 활동 발견/저장 수집·알림·캘린더·if(kakao) 자동 입력·신청 추적 등 전체 서비스 후속. 빈/준비 화면은 완료 서비스로 포장하지 않았다.
- 계정 wallet은 서버가 정본이며 이번 오프라인 복원은 전체 프로필 cache·기기 수신 명함에 한정된다. 모든 서버 wallet의 오프라인 사용을 보장하지 않는다.

## 기능별 커밋 (push/PR 없음)

```text
bc293b0 feat(android): add native profile and card exchange vertical slice
051d710 fix(android): preserve ambiguous exchange requests for idempotent retries
71d815f fix(android): validate shared QR payloads and persistence regressions
0629081 refactor(android): enforce screen state and provider ownership with UI regressions
b3ae050 feat(android): add safe contact actions and compact card layouts
3588df0 fix(android): exclude private data from device transfer backups
1fd7f49 fix(android): show local exchange dates and clear product status copy
4c50d71 fix(android): disable legacy Android backup content
d79fac2 test(android): verify actual API authentication and bidirectional exchange
a62c0e2 fix(android): isolate unresolved delivery intents across retries
c9ff82a fix(android): align QR tiles and keep exchange metadata beside cards
011450b fix(android): keep QR carousel text within equal-sized tiles
```

최종 증거/인계 문서 커밋 SHA는 worker_done 보고에 포함한다. 조율 담당이 순서대로 통합하며 이미 받은 커밋을 재작성하지 않는다.
