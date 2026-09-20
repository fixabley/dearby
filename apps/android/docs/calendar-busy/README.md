# 기기 바쁜 시간 · #10

PR9의 시간축 위에 읽기 전용 기능을 쌓는다. `BusyProvider`는 익명 `BusyInterval`만 반환하고 `BusySession`은 상세 수명의 조회·취소를 소유하고 App의 CalendarSettingsController가 영속 선택/최초 안내/권한 요청을 조율한다. Shared/UI는 범용 구간 연산 및 표시값만 제공한다. 신청 기간은 조회/비교에 등록하지 않는다.

Android `CalendarContract.Instances`는 선택일 범위의 반복 occurrence를 전개한다. 숫자 BEGIN/END/ALL_DAY/AVAILABILITY/STATUS/SELF_ATTENDEE_STATUS만 projection하고 제목·장소·메모·주최자 문자열은 읽지 않는다. 명시적 free/canceled/declined만 제외하며 NULL/unknown 및 tentative는 busy로 유지한다. 권한 거절/오류는 빈 결과와 별도 상태다.

ALL_DAY의 UTC midnight는 floating 날짜다. 별도 UTC-date query 후 기기 시간대 자정으로 변환하고 활동 source-zone의 선택일에 clip한다. 23/25시간 DST 하루 및 배타적 종료를 지킨다. 기기 시간대는 조회마다 읽으며 전체 활동기간 배열/조회를 만들지 않는다.

조회는 IO에서 실행하고 CancellationSignal로 cursor 작업을 취소한다. OFF/화면종료/백그라운드는 메모리를 비우며 generation/revision fence가 비협조적인 지연 결과까지 차단한다. 권한 철회는 결과를 지운다. Room/cache/서버/캘린더 export에는 연결하지 않는다.

공식 근거: [Instances](https://developer.android.com/reference/android/provider/CalendarContract.Instances), [EventsColumns: ALL_DAY 및 availability](https://developer.android.com/reference/android/provider/CalendarContract.EventsColumns). OS recurrence expansion은 API 계약에 의존하며 개인 캘린더를 자동 검증에서 읽지 않는다.

검증(2026-09-15): 새 BusyInterval/BusySession JVM 7개 통과, 전용5556의 AndroidBusyProvider 계측 3개 통과. in-memory SQLite numeric fixture로 SQL NULL/tentative/free/canceled/declined, projection/bounds/IO, floating 종일, null cursor 실패 및 CancellationSignal 전달을 실행했다. OS CalendarProvider 및 실제 계정 반복 확장은 읽지 않았고 공식 Instances 계약에 의존한다.

권한 요청과 지연 callback의 세대 검사는 CalendarSettingsController/Host만 소유한다. 실제 ON_STOP에서는 진행 중인 요청을 OFF로 되돌리고 늦은 응답을 무시하며, 권한 다이얼로그의 일시 ON_PAUSE에는 정리하지 않는다. BusySession은 이미 허용된 접근을 확인하고 상세의 조회/취소/복귀만 담당한다. 2026-09-20 caller 조사에서 테스트 외 사용되지 않는 confirm/permissionResult/retry와 Consent/Requesting 상태를 제거했다. 재조회는 실제 화면 경로인 foreground 복귀/명시적 enable로 수행하며, 거절·제한·철회와 off/background/close/new-date의 stale publish 차단 회귀를 유지한다.

## 상세 표시 API / native mapping

- App `CalendarSettingsController`/`CalendarSettingsHost`가 최초 실행 안내, 명시적인 READ_CALENDAR 요청, 별도 boolean 영속 설정과 lifecycle을 조립한다. 상단 gear→Pages/Settings의 ‘겹치는 일정 확인하기’ Switch에서 변경한다. [상세 소유권/키/검증](SETTINGS.md).
- Page `NoticeDetailSheet`는 결과 문구, phase별 BusyOverlayState, 날짜 콜백만 받는다. 동의/스위치는 상세에 없다. `NoticeScheduleSection`만 활동 LocalDate를 App으로 전달하고 신청 시간축은 연결하지 않는다.
- `DayTimeline(interval, title, busy, onDate)`는 선택 날짜가 결과 window와 다르면 이전 결과를 숨긴다. 시간 gutter는 눈금만 표시하며 본문에 Primary 활동과 반투명 tertiaryContainer 바쁜 시간 블록을 겹친다.
- `timelineIntersection`은 원본 Instant의 반열린 교집합을 계산한다. `TimelineIntersectionOutline`은 교집합 높이에만 Canvas dashed stroke를 clip하고, 최소 시각 높이를 계산에 사용하지 않는다. 경고 아이콘도 실제 양의 길이 교집합에만 붙는다.
- `TimelineBusyBlock`은 제목 없는 바쁜 시간/한국어 시각을 표시한다. 활동 제목과 시각은 native Primary/onPrimary로 보호하고 측정된 제목 높이 이후의 빈 공간에 busy 문구를 배치한다. 공간이 없는 짧은 구간은 아래 `TimelineBusySummary` 및 AX로 정확한 정보를 제공한다. busy끼리 union 계약은 유지한다.
- `TimelineBusySummary`의 겹치는 시간은 점선과 같은 실제 교집합만 포맷한다(활동14–16/개인15–17 → 오후3시부터오후4시까지). 개인 전체 시간은 블록의 바쁜 시간 라벨과 AX로 구분한다. `TimelineLegend`는 활동/내 일정 배색을 설명한다. OFF는 본문 busy·점선·경고를 함께 제거한다. [상세 날짜/시간](DATE-PRESENTATION.md)은 날짜/요일 다음 줄 시간 구조를 유지하고 한국 시간 중복 라벨만 생략한다.

Compose M3에는 읽기 전용 하루 시간축이 없어 기존의 좁은 custom plot에 배색 오버레이와 정확한 교집합 점선을 추가했다. native colors/typography/shapes/icons를 유지하고 SDK/라이브러리를 추가·업그레이드하지 않았다. [Google Material warning 원본](https://github.com/google/material-design-icons/blob/master/src/alert/warning/materialicons/24px.svg), [runtime permission 안내](https://developer.android.com/training/permissions/requesting), [정책에 의한 권한 제한 확인](https://developer.android.com/reference/android/content/pm/PackageManager#isPermissionRevokedByPolicy(java.lang.String,%20java.lang.String))을 참고했다.

Calendar의 VISIBLE은 UI 표시 선택이지 free/busy가 아니므로 숨긴 캘린더도 조회 가능한 바쁜 시간에는 포함한다. SQL fixture의 visible=0 행도 유지되는지 검증한다. 명시적 availability/status/self-status 제외 규칙만 적용한다.

최종 실행 및 대표 화면: [VERIFICATION](VERIFICATION.md). [CalendarColumns.VISIBLE](https://developer.android.com/reference/android/provider/CalendarContract.CalendarColumns#VISIBLE)의 화면 표시 여부를 free/busy로 해석하지 않는다.

## 캘린더 편집기 권한 회귀 정정 (2026-09-20)

전체 계측에서 과거 CalendarEditorTest의 앱 전체 READ_CALENDAR 미선언 assertion이 실패했다(36줄). 기준 PR32부터 manifest는 별도 동의형 busy 조회를 위해 READ_CALENDAR를 선언하므로 exporter의 계약과 다르다. 이 assertion을 편집기 Intent의 URI 권한 grant flags=0 검증으로 교체하고 WRITE_CALENDAR 미선언, ACTION_INSERT의 정확한 extras·초대자/자동 저장 없음, handler 실패 안내 검증을 유지했다. 실제 AndroidManifest/권한 요청/OS exporter 코드는 변경하지 않는다. 재검증 결과는 [설계 단순화 검증](../design-simplification/VERIFICATION.md)에 기록한다.
