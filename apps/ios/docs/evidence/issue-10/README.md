# Issue10 · 바쁜 시간 연결 및 활동 겹침

PR8 head458f12d를 부모로 하는 별도 `fixabley/dearby-ios-calendar-busy` 브랜치다. 개인 일정 제목/장소/ID/참석자/메모를 provider 밖으로 전달하지 않고 두 절대 시각만 임시 메모리에 보유한다. 이 기능은 읽기 전용이며 기존 EventKit 편집기 저장 흐름과 별개다.

## 단일 상세 세션

`BusyCalendarSession`은 default OFF, 동의 전 권한요청0, 취소→OFF, 계속→권한요청, granted 재사용, denied/restricted/failed/empty를 분리한다. 세션 하나가 여러 확정 활동의 선택일을 조율하며 신청기간은 등록하지 않는다. OFF/close/background/revoke 시 결과를 즉시 비우고 Task 취소 및 generation 검증으로 늦은 응답을 무시한다. 값의 clip/merge는 half-open 경계를 사용해 접점은 충돌이 아니며 중복·중첩은 합친다.

실행: `bash apps/ios/tests/run_busy_calendar.sh` fake-provider/state/interval 검증 PASS. OFF/동의 취소/허용/재사용/복수활동 단일권한/거절/제한/실패/빈 결과/조회중OFF/날짜변경stale/철회/background/resume/close 및 종일·반복 occurrence·취소/한가함/본인거절 필터를 포함한다. 실제 OS 접근은 실행하지 않았다.
