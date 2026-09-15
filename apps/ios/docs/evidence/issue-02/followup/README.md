# PR8 후속 OS 흐름 검증 (진행 중)

검증 대상 c1045f1, Xcode 26.6 / iOS26.5, 전용 A434888F-4096-48AE-91B2-37A498233B55.

- `calendar-application.png`: 실제 신청 기간 EventKit 편집기. 제목 `[신청 기간] 한국농어촌공사 채용설명회`, 종료 2026-09-15 13:00 확인.
- `calendar-cancel-return.png`: 신청 편집기 X → 변경 사항 폐기 후 상세 복귀.
- `calendar-schedule.png`: 실제 활동 EventKit 편집기. 제목 `[행사] 한국농어촌공사 채용설명회`, 장소, 2026-09-15 14:00–16:00 확인.

Orca Computer Use의 Simulator window 1844, 456×972pt / screenshot 912×1944px(scale2)를 사용했다. 상세를 AXScrollDownByPage로 스크롤 후 접근성 tree에 `신청 기간 캘린더에 추가`, `활동 일정 캘린더에 추가: 행사: ...`, 장소 지도 버튼이 나타났다. 신청은 실제 AXPress로 진입, 활동은 확인된 화면 좌표 (145,530)에서 synthetic click으로 진입했다.

EventKit 원격 편집기는 Orca tree에 내부 컨트롤이 빠지고 XcodeBuildMCP snapshot에는 배경 Dearby 버튼이 그대로 나타났다. 따라서 도구 성공/대상 ref만으로 판정하지 않고 실제 PNG와 취소 후 복귀를 확인한다. 새 일정 저장(우상단 체크)은 누르지 않는다.

## 후속 판정

- 성공: 신청 editor 진입/취소(변경 사항 폐기 후 원래 상세), 활동 editor 진입(14:00–16:00)/이후 상세 복귀, 지도 앱의 정확한 명칭/좌표 핀 표시(`map-launch.png`). 실제 save는 실행하지 않았다.
- 성공: `orca computer click --app com.apple.iphonesimulator --window-id 1844 --x 190 --y 325 --click-count 2` 단일 synthetic doubleclick. title 영역을 눌렀으며 저장 button/AX custom action을 누르지 않았다. `doubletap-before.png`의 러시아언어문화학과 저장 피드백 → `doubletap-after.png`의 한국농어촌공사 저장 피드백으로 바뀌어 onTapGesture→save 콜백을 검증했다. 이미 저장된 조직의 멱등 재저장이므로 새 ID 추가를 주장하지 않는다.
- 환경 제약: EventKit remote UI의 AX 내부 노출이 없고 MCP는 배경 targets를 반환했다. 화면 animation 중 캡처와 stale ref로 실제 표시/클릭 위치가 어긋날 수 있어 좌표/화면 관찰을 함께 사용했다. 앱 코드 수정 없이 c1045f1에서 성공했으므로 이번 List 전환의 calendar/doubletap 회귀 증거는 발견하지 못했다. baseline 설치는 필요하지 않았다.
- 활동 편집기 취소 중 입력하지 않은 문자와 예상 밖 UI 전환이 보여 메인에 동시 조작을 확인했다. 다른 agent는 A434를 사용하지 않았으나 사용자의 직접 입력 여부는 알 수 없다는 답을 받았다. 일정 저장은 하지 않았고 다음 확인 때 편집기가 닫히고 상세가 복원되어 있었다. 신청 취소는 X→폐기 전 과정을 명시적으로 확인했고, 활동 취소의 중간 입력 provenance는 완전히 확정하지 않는다.

화면 좌표는 scale2 screenshot에서 나눈 window-local 값이며 전역 좌표/다른 Simulator를 사용하지 않았다. 신청 button의 화면 중심은 약 (145,385), 활동은 (145,530), 지도는 (240,675)였다. 캘린더 취소 X는 (64,180), 폐기는 (160,258)였다. CLI tree는 label을 제공하지만 raw AX frame 값을 내보내지 않아 정밀 frame/hit 경계 측정은 별도 미검증이다.
