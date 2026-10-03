Android QR 운영 링크와 교환 맥락 검증 차단

원인: native-v1.md에는 HTTPS 공개 명함 URL/앱 연결 도메인, QR 인코딩 형식, 등록 활동 목록 endpoint가 없다. Android는 임시 앱 전용 dearby://card/<UUID>?label=... 형식을 만들고 실제 QR 픽셀을 생성/해독하지만 iOS와 운영 링크 상호운용을 검증할 수 없다.

영향: 설치되지 않은 기기의 웹 fallback, App Links 소유권 검증, iOS↔Android 실기기 스캔 및 등록 활동 선택은 미완료다. 직접 활동 입력/선택 안 함과 Android 앱 전용 수신은 독립 구현 가능하다. 현재 카메라는 외부 카메라 촬영 결과를 QR로 해독하는 흐름이며 연속 실시간 스캐너가 아니다.

증거: apps/android/features/qr/QrActions.kt에 상응하는 실제 경로 app/src/main/java/com/dearby/nativeapp/features/qr/QrActions.kt와 AndroidManifest.xml의 dearby scheme; 공유 계약에 링크·활동 목록 항목 부재. JVM/에뮬레이터 QR roundtrip은 실제 두 기기 스캔 증거를 대체하지 않는다.

해소 조건: 조율 담당이 두 플랫폼의 링크 payload와 운영 HTTPS/App Links/Universal Links 도메인 및 활동 조회 계약을 확정하고, 실제 두 기기에서 공유→스캔→공개 조회→기기 ID 저장→서버 가져오기를 검증한다. #43 iOS 링크 차단, #42 운영 인증/배포, #41 전체 서비스 검증과 의존한다. #38 첫 Android slice와 별도로 추적하며 이 이슈 작성은 해결을 뜻하지 않는다.

2026-09-27 조율 후속: 설치 앱 개발용 payload를 두 플랫폼 공통으로 `dearby://card/<UUID>?label=...` 또는 `?activityId=<UUID>`로 승인했다. scheme/authority/path 및 UUID, query 중복/알 수 없는 키, label 길이 200, 활동과 직접 입력의 배타성을 검증한다. 운영 HTTPS 연결·웹 fallback·등록 활동 조회·실기기 스캔은 계속 차단이다.
