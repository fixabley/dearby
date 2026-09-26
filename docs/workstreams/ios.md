# iOS 담당

2026-09-27 issue #37 첫 네이티브 수직 구현. [현재 인계](../context/ios-implementation-and-handoff.md), [실제 검증·화면](../../apps/ios/docs/evidence/README.md).

Xcode 27/Swift 6/SwiftUI·SwiftData·Keychain, 5탭·계정 프로필·선택 공개 명함·QR·게스트 가져오기·받은 명함/명시적 보내기를 구현했다. 앱 테스트17, 구조16, strict SwiftLint40파일 위반0, Debug/Release Simulator build와 실제 UI 캡처를 확인했다. 격리 로컬 API로 실제 네이티브 인증·공개 projection·가져오기·iOS↔Android 전달/멱등 replay·reciprocal을 검증했다. SMTP 실제 수신이나 운영 배포의 증거는 아니다.

CI는 Simulator 기본 ad-hoc signing을 사용해야 Keychain 회귀가 통과한다. CODE_SIGNING_ALLOWED=NO는 쓰지 않는다. #42 운영 인증/HTTPS, #43 Universal Links·실기 카메라/사진 권한, #41 전체 서비스와 #36 if(kakao)는 남아 있다. 커밋/명령/한계는 인계 정본 참조. push·PR·통합은 coordinator 담당, 완료 뒤에도 사용자 요청으로 세션 유지.
