# Android #47 검증 — 2026-09-27 KST

범위는 API 활동 발견·저장·상세·신청 본인 기록·등록 활동 교환 맥락이다. 전체 서비스 출시 완료나 외부 신청 제출 성공을 뜻하지 않는다. 이전 #38 명함 검증은 [보존 기록](archive/verification-38-2026-09-27.md)에 있다.

## 환경과 실행

자기 checkout `/Users/jominjun/Documents/dearby/dearby-android`, branch `feat/android-activities`, base `dcb590f`. 기존 `fixabley/dearby-android`와 커밋은 보존했다. Android Studio JBR25.0.2, Gradle9.3.1, AGP9.1.1, API36 AVD `Dearby_Issue2_Test`, serial `emulator-5554`.

```sh
python3 scripts/check-fsd.py --self-test
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:lintDebug :app:assembleDebugAndroidTest
./gradlew -PdearbyApiUrl=http://10.0.2.2:52777 :app:testDebugUnitTest :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest
```

기본 URL 미설정 빌드와 실제 API 지정 빌드를 각각 실행했다. unsigned release는 스토어 배포 검증이 아니다. Production 기본값에는 fixture/서버 주소/샘플 계정이 없다. HTTP는 debug만 허용하며 release는 HTTPS를 요구한다.

## 자동 검증

- JVM **28 tests, failures 0, errors 0, skipped 0**. 기존18 + catalog10: 공개 GET/no token, 실제 loopback HTTP 오류/빈 응답, 디스크 실패 뒤 이전 cache 유지, origin 분리, 중복 ID/잘못된 참조 거절, 손상 cache에서 네트워크 복구, 읽기 실패 local 기록 덮어쓰기 방지, durable commit 전 State 비공개, 실패 뒤 신청 기록 보존, 만료 경계·예약·종료·시간대 표시.
- 실제 API36 emulator **18 tests, failures 0**: 기존 명함/프로필/QR/지갑11 + catalog UI6/Room1. 발견에서 종료 활동 제외·저장에서 표시, 출처 버튼은 report 미호출, fixed banner/수정, 나중에 미저장, error/loading/empty 구별, 1.6× 큰 글자, 등록/직접입력/없음 ID·label 배타성, Room 재개와 실패 commit 보존.
- 실제 격리 API **3 별도 계측 테스트**: public catalog repository+Room, guest 제품 UI 흐름, force-stop 후 재시작 복원. Fixture 테스트와 별도 실행했다.
- FSD **43 Kotlin files / 28 자체 회귀** 통과. 공개 API·방향·동위 슬라이스·순수 UI/State 경계를 유지하고 새 catalog/picker 경계를 등록했다. 컴파일러 모듈 격리의 증거로 과장하지 않는다.
- Android lint **errors 0, warnings 17**: 의존성 업데이트·기존 및 신규 KTX 편의 API 권고. baseline/전역 disable 없음. WebView의 JavaScript 허용은 외부 신청 폼 실행 목적의 해당 함수 단일 주석이며 file/content access와 mixed content는 차단했다.
- Debug·AndroidTest APK 및 unsigned Release build 성공. `git diff --check` 통과. Ponytail는 별도 [검토](ponytail-review.md).

## 실제 API와 화면

조율 담당이 운영한 격리된 실제 Dearby API `http://127.0.0.1:52777`, emulator에서는 `http://10.0.2.2:52777`. 실제 공식 출처를 수집한 **30개 활동, 모집 중1(if(kakao)26)**. 응답 자체를 client fixture로 치환하지 않았다.

실제 제품 HttpClient/CatalogRepository로 public GET, 참조 검증, Room cache 저장/재열기, 프로그램·조직 UUID와 applied 기록 복원을 assertion했다. 별도 제품 UI에서는 발견→if(kakao) 상세→프로그램/조직 저장→신청하지 않음 기록→신청 WebView 열기/닫기→나중에(기존 기록 유지)→명시적 신청함 기록→저장 탭→QR 등록 활동에서 과거 YAPP 선택을 실행했다. 외부 로그인·폼 입력·실제 제출은 하지 않았다. 이후 `am force-stop`과 새 계측 process로 시작해서 두 북마크와 신청 안내의 복원을 확인했다.

- `evidence/catalog/real-catalog-api.txt`: 실제 HTTP/Room 결과.
- `real-catalog-discovery.png`, `real-catalog-detail.png`, `real-catalog-saved.png`: 실제 API 표시/저장 화면.
- `real-catalog-self-report.png`, `real-catalog-restarted.png`: 사용자가 기록했다고 가정한 테스트 입력의 저장/재시작 증거. 외부 신청 성공 증거가 아니다.
- `real-catalog-context.png`: 실제 카탈로그의 과거 등록 활동을 QR 맥락으로 선택.
- `fixture-catalog-*.png`, `fixture-context-picker.png`: test target만의 UI fixture. 모집 종료/오류/빈 상태·큰 글자·대화상자 검증용이며 실서버 데이터가 아니다.

이미지를 직접 열어 화면·고정 안내·본문 스크롤·큰 글자·대화상자를 검토했다. 초기 Dialog root가 두 개라 캡처 assertion이 실패한 것은 dialog node 지정으로 수정했고 재실행했다. 기본 보라색 dialog surface는 흰색으로 맞췄다. 일정의 양쪽 시각이 없으면 “시간 미정” 한 줄을 표시하며 날짜 원문을 유지한다.

## 남은 한계

외부 브라우저 종료로 신청을 추론하지 않는다. source만 열면 prompt가 없으며, application WebView를 닫을 때만 확인한다. 지원하지 않는 로그인 scheme은 차단하고 시스템 브라우저를 안내한다. 실제 외부 인증·로그인 복귀/자동입력은 #36 미완료이고 캘린더/푸시도 별도다. 시간대와 expiry는 도메인 테스트로 검증했으며 기기 시계 정확도를 보장하지 않는다. Full TalkBack, 실제 휴대폰/외부 제출, 운영 HTTPS 배포는 미검증이다. 기존 profile/QR/wallet 동작의 targeted regressions는 실행했지만 #38의 양방향 실서버 명함 전달 전체를 이번 catalog slice에서 다시 수행하지 않았다.
