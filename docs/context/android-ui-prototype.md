# Android 프로토타입 인계

현재 범위·예시 데이터·화면 흐름은 [공통 모바일 안내](mobile-ui-prototype.md), 실행 명령은 [Android README](../../apps/android/README.md)를 따른다. Compose 화면과 메모리 상태로 동작한다.

## 수정할 위치

- 활동 예시는 `entities/catalog/model/CatalogModel.kt`, 필터·신청 상태는 `app/CatalogViewModel.kt`에 있다.
- 바쁜 시간 예시는 `features/calendar/CalendarConflictState.kt`, 가상 프로필·명함 상태는 `DemoViewModel`이 맡는다. 편집은 선택한 카드 ID를 보존한다.
- 공통 표현은 `shared/ui`, 명함·이력 조합은 도메인 컴포넌트에서 재사용한다. [구조 안내](../../apps/android/ARCHITECTURE.md)와 [시안 적용표](../design/mobile-prototype-reference-map.md)에서 위치를 확인한다.

## 기존 검증과 한계

2026-10-04 플랫폼 작업 당시 Debug·테스트 APK 빌드, 단위 12, 구조 자가검사 25, 기본 글꼴 화면 흐름 9개와 최종 QR 검사 1개가 통과했다. lint는 오류 0, 의존성 버전 안내 경고 6개였다. 글자 1.3배의 핵심 명함 흐름을 확인하고 잘린 QR 설명을 수정·재검증했다.

[검증 보고서](../../apps/android/docs/VERIFICATION.md), [최종 캡처](../../apps/android/evidence/selected-card-flows/README.md), [파일 정리 검사](../../apps/android/docs/cleanup-2026-10-04.md)를 참고한다. 초기 두 화면 검사와 완료 전 체크포인트는 [이전 전체 인계](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/android-ui-prototype.md)에 보존한다.

당시 플랫폼 검사는 기존 Android 16 에뮬레이터에서 실행했다. 다른 운영체제·스크린리더·외부 페이지 네트워크는 검증하지 않았다. root의 실제 휴대폰 설치 결과는 [공통 통합 기록](mobile-ui-prototype.md#진행-상태)에 별도로 있다. 이번 문서 정리에서 앱 검사를 다시 실행하지 않았다.

플랫폼 작업은 통합되었고 해당 하위 세션·워크트리는 정리되었다. 서버·웹·어드민·운영 데이터는 변경하지 않았다.
