# Dearby Android 클릭형 프로토타입

흰색·청록색의 기존 탐색 카드, 상세, 신청 화면과 겹치는 시간 시트를 유지합니다. 목록 제목은 모집 예정 예시도 포함하도록 “활동 둘러보기”입니다. 서버 없이 고정 예시 세 개로 실행됩니다.

- 탐색 → 상세 → 신청 안내 → 예시 신청 상태 선택. 링크는 외부 브라우저로 엽니다.
- 참가등록형/선발형 필터와 예시 신청 상태는 메모리에만 있습니다. 프로세스 종료 후 초기화됩니다.
- 겹치는 시간 시트는 고정 바쁜 시간 `2026-10-24 14:00~15:00 Asia/Seoul`을 비교합니다. 컨퍼런스는 1시간 겹치고 캠프·밋업은 겹침이 없습니다.
- 예시 모집 상태는 날짜가 지나도 바뀌지 않습니다. 실제 접수·선정·결제 기능은 없습니다.

예시 수정: `app/src/main/java/com/dearby/nativeapp/entities/catalog/model/CatalogModel.kt`의 `demoActivities`. 세션 상태는 `app/CatalogViewModel.kt`, 화면은 `pages/catalog`, 신청·겹침 UI는 `features/application`, `features/calendar`에 있습니다.

계정·인증·명함·프로필·QR·저장 탭과 서비스 HTTP/Repository/Room/Keystore/기기 캘린더/WebView 실행 코드를 제거했습니다. INTERNET·READ_CALENDAR 권한 및 명함 딥링크도 없습니다. 기존 로고와 이미지 자산, applicationId `com.dearby.nativeapp`은 유지합니다. 이전 설치 데이터는 읽거나 삭제하거나 마이그레이션하지 않습니다. 과거 서비스 문서와 검증 증거는 `docs/archive` 및 기존 evidence에 보존되며 현재 구현 증거가 아닙니다.

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
export ANDROID_HOME=/Users/jominjun/Library/Android/sdk
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest
python3 scripts/check-fsd.py --self-test
```

실제 실행 결과는 `docs/VERIFICATION.md`, 구조는 `ARCHITECTURE.md`를 참고하세요.
