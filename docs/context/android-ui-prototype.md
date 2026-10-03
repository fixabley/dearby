# Android UI prototype — 2026-10-03

담당 checkout: `/Users/jominjun/Documents/dearby/android-ui-prototype`. Orca worker terminal `term_6ffd3ac2-247f-446d-ba7b-d9eddfa52d29`, task `task_61e6cd5b0981`, dispatch `ctx_ea8dcd777824`. 위 ID는 이번 작업 연결이며 검증 시점과 다릅니다.

최신 사용자 승인에 따라 Android를 고정 예시 기반 클릭형 프로토타입으로 단순화했습니다. 기존 탐색 카드·상세·신청·겹침 시트의 색·간격·아이콘·로고 자산을 유지하고, 숨겨진 계정·명함·프로필·QR와 서버/Room/Keystore/기기 캘린더/WebView 실행 코드를 제거했습니다. 앱 ID는 그대로이며 기존 설치 데이터는 읽기/삭제/마이그레이션하지 않습니다. 서버·웹·어드민·공통 규격과 root 설정은 수정하지 않았습니다.

고정 예시는 `apps/android/app/src/main/java/com/dearby/nativeapp/entities/catalog/model/CatalogModel.kt`, 필터/신청 메모리 상태는 `app/CatalogViewModel.kt`입니다. 컨퍼런스 10/24, 캠프 11/7, 밋업 11/21 모두 Asia/Seoul이며 모집 상태는 날짜와 무관합니다. 바쁜 시간은 `features/calendar/CalendarConflictState.kt`의 10/24 14–15시로 고정되어 있고, 0건 결과도 제공합니다. 신청 화면은 로컬 안내와 명시적 예시 URL 외부 이동만 제공합니다.

검증(2026-10-03 23:58 KST): assembleDebug, JVM 5개, lint 오류 0/경고 7개, AndroidTest APK, 구조검사 13개 파일/19개 자가검사 통과. 기존 Dearby_Calendar_Verify Android 16 에뮬레이터에서 UI 2개 흐름 통과; UiAutomation으로 최종 화면 5개를 캡처해 직접 시각 확인했습니다. 사용자 폰 조작/설치는 하지 않았고 push/PR/merge는 coordinator 담당입니다. 완료 후 세션 retain은 사용자 요청입니다.


로컬 커밋: `a61bfa2` 서비스 제거/고정 prototype 전환(README·구조·테스트 포함), `cee8225` 모집예정 항목과 맞춘 목록 제목 “활동 둘러보기” 변경. 테스트 의존성 Espresso 3.7은 Android 16 호환에 실제 필요하여 복원했습니다. 동작 변경 없이 화면 캡처를 UiAutomation으로 바꿨으며 최종 검증/증거는 아래 문서와 함께 후속 커밋에 포함됩니다.

조율 후 예시 URL은 모두 `https://example.com`으로 통일했습니다(원래 하위 경로는 404 가능). 기본 host GPU의 기존 AVD가 시작 timeout을 보여 같은 AVD를 데이터 초기화 없이 software GPU 모드로 재시작했습니다. 새 AVD 생성은 하지 않았습니다. 추적되지 않은 `apps/android/.idea`는 로컬 IDE가 생성한 파일이며 커밋하지 않았습니다.


완료 근거: `apps/android/docs/VERIFICATION.md`, `apps/android/evidence/ui-prototype/`(빌드·unit·lint·구조·UI runner 출력 및 5개 PNG). 최종 UI 실행은 `OK (2 tests)` / 8.095초. Ponytail 검토도 완료했으며 불필요한 wrapper/repository를 남기지 않았습니다. 테스트 헬퍼와 Espresso 복원 외 추가 제품 코드 변경은 없습니다. 물리 기기·다른 OS·큰 글꼴·스크린리더·외부 페이지 네트워크는 이번 worker 검증 범위 밖입니다. root의 통합·push·PR·실제 폰 검증이 남습니다.
