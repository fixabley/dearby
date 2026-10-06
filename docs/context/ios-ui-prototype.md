# iOS 프로토타입 인계

현재 범위·예시 데이터·화면 흐름은 [공통 모바일 안내](mobile-ui-prototype.md), 실행 명령은 [iOS README](../../apps/ios/README.md)를 따른다. SwiftUI 화면과 메모리 상태로 동작한다.

## 수정할 위치

- 활동 예시: `DemoActivities.swift`, 바쁜 시간 예시: `CalendarConflictState.swift`.
- 가상 프로필·명함: `DemoIdentity.swift`. Catalog/IdentityViewModel과 View state가 실행 중 상태를 소유한다.
- 공통 표현은 `shared/ui`, 명함·연락처·이력 조합은 `entities/identity/ui`에 둔다. 파일별 대응은 [시안 적용표](../design/mobile-prototype-reference-map.md)를 따른다.
- 명함 만들기는 실제 API를 쓴다(2026-10-06): `widgets/identity/ui/CardComposerPage.swift`·`SignInSheet.swift`, 상태는 `widgets/identity/model/CardPublishModel.swift`·`CardDraft.swift`·`AccountViewModel.swift`. 명함 공유·스캔은 아직 예시 화면이고, 활동의 HTTPS 링크 공유만 운영체제 공유를 사용한다.

## 기존 검증과 한계

2026-10-04 플랫폼 작업 당시 Debug·Release 무서명 빌드, SwiftLint 위반 0, 구조 검사 17, 단위 9·화면 흐름 9개가 통과했다. 후속 수정은 큰 글씨 일정 시트·QR·명함 상세/보내기 이동을 다시 확인했다. [최종 캡처](../../apps/ios/docs/evidence/ui-prototype-selected/README.md)와 [파일 정리 검사](../../apps/ios/docs/cleanup-2026-10-04.md)를 참고한다.

초기 시각 검사에서 제목 중복·QR 잘림·버튼 가림을 수정했다. 후속 테스트는 같은 이름의 버튼을 잘못 선택하는 문제를 고유 접근성 식별자로 구분하고 실제 상세 열기·닫기·보내기까지 재검증했다. 실패 실행과 재검사 상세는 [이전 전체 인계](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/ios-ui-prototype.md)에 보존한다.

기존 로그·xcresult는 프로젝트 밖 `~/.dearby-signing/ios-ui-prototype-worker/`에 기록되었다. 문서 정리에서 다시 실행하거나 외부 파일의 현존 여부를 재검사하지 않았다. 당시 iPad·VoiceOver 실제 낭독·외부 공유 대상 전송은 검증하지 않았다. root의 실기기 설치 결과는 [공통 통합 기록](mobile-ui-prototype.md#진행-상태)과 구분한다.

플랫폼 작업은 통합되었고 해당 하위 세션·워크트리는 정리되었다. 현재 checkout의 사용자 변경은 [조율 문서](coordinator-current-task-and-decisions.md)를 확인한다.
