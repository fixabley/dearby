# 기기 바쁜 시간 · #10

PR9의 시간축 위에 읽기 전용 기능을 쌓는다. `BusyProvider`는 익명 `BusyInterval`만 반환하고 `BusySession`은 상세 수명의 동의·권한·조회·취소를 소유한다. Shared/UI는 범용 구간 연산 및 표시값만 제공한다. 신청 기간은 조회/비교에 등록하지 않는다.

Android `CalendarContract.Instances`는 선택일 범위의 반복 occurrence를 전개한다. 숫자 BEGIN/END/ALL_DAY/AVAILABILITY/STATUS/SELF_ATTENDEE_STATUS만 projection하고 제목·장소·메모·주최자 문자열은 읽지 않는다. 명시적 free/canceled/declined만 제외하며 NULL/unknown 및 tentative는 busy로 유지한다. 권한 거절/오류는 빈 결과와 별도 상태다.

ALL_DAY의 UTC midnight는 floating 날짜다. 별도 UTC-date query 후 기기 시간대 자정으로 변환하고 활동 source-zone의 선택일에 clip한다. 23/25시간 DST 하루 및 배타적 종료를 지킨다. 기기 시간대는 조회마다 읽으며 전체 활동기간 배열/조회를 만들지 않는다.

조회는 IO에서 실행하고 CancellationSignal로 cursor 작업을 취소한다. OFF/화면종료/백그라운드는 메모리를 비우며 generation/revision fence가 비협조적인 지연 결과까지 차단한다. 권한 철회는 결과를 지운다. Room/cache/서버/캘린더 export에는 연결하지 않는다.

공식 근거: [Instances](https://developer.android.com/reference/android/provider/CalendarContract.Instances), [EventsColumns: ALL_DAY 및 availability](https://developer.android.com/reference/android/provider/CalendarContract.EventsColumns). OS recurrence expansion은 API 계약에 의존하며 개인 캘린더를 자동 검증에서 읽지 않는다.

검증(2026-09-15): 새 BusyInterval/BusySession JVM 7개 통과, 전용5556의 AndroidBusyProvider 계측 3개 통과. in-memory SQLite numeric fixture로 SQL NULL/tentative/free/canceled/declined, projection/bounds/IO, floating 종일, null cursor 실패 및 CancellationSignal 전달을 실행했다. OS CalendarProvider 및 실제 계정 반복 확장은 읽지 않았고 공식 Instances 계약에 의존한다.

권한 요청 중 실제 ON_STOP으로 화면이 가려지면 Consent/Requesting도 OFF로 되돌린다. 오래된 permission callback을 무시한 뒤에도 스위치를 다시 조작할 수 있다. 권한 다이얼로그의 일시 ON_PAUSE에는 정리하지 않는다. confirm→background→late permission→resume 및 여러 활동의 단일 세션 조율을 회귀 검증한다.

## 상세 표시 API / native mapping

- App `NoticeDetailRoute`가 단일 BusySession, OS RequestPermission launcher, ON_RESUME/ON_STOP/종료를 조립한다. 기본 OFF이며 동의 Continue에서만 READ_CALENDAR를 요청한다. 이미 허용되었다면 즉시 켜고, OFF는 OS 권한을 철회하지 않고 조회/인메모리 결과를 제거한다.
- Page `NoticeDetailSheet`는 BusyDisplayState, phase별 BusyOverlayState, toggle/continue/settings/retry/date 콜백을 받는다. `NoticeScheduleSection`만 활동의 LocalDate를 전달한다. 신청 DayTimeline에는 busy나 조회 콜백을 전달하지 않는다.
- Shared `BusyCalendarControl`은 native M3 Switch/TextButton, `CalendarConsentDialog`는 page의 M3 AlertDialog다. 권한/OS query는 Shared/Page에 없다.
- `DayTimeline(interval, title, busy, onDate)`는 선택 날짜가 결과 window와 다르면 이전 결과를 숨긴다. 시간 gutter는 눈금만 표시하며 본문에 Primary 활동과 반투명 tertiaryContainer 바쁜 시간 블록을 겹친다.
- `timelineIntersection`은 원본 Instant의 반열린 교집합을 계산한다. `TimelineIntersectionOutline`은 교집합 높이에만 Canvas dashed stroke를 clip하고, 최소 시각 높이를 계산에 사용하지 않는다. 경고 아이콘도 실제 양의 길이 교집합에만 붙는다.
- `TimelineBusyBlock`은 제목 없는 바쁜 시간/한국어 시각을 표시한다. 활동 제목과 시각은 native Primary/onPrimary로 보호하고 측정된 제목 높이 이후의 빈 공간에 busy 문구를 배치한다. 공간이 없는 짧은 구간은 아래 `TimelineBusySummary` 및 AX로 정확한 정보를 제공한다. busy끼리 union 계약은 유지한다.
- `TimelineLegend`는 활동/내 일정 배색을 설명한다. OFF는 본문 busy·점선·경고를 함께 제거한다. [상세 날짜/시간](DATE-PRESENTATION.md)은 날짜/요일 다음 줄 시간 구조를 유지하고 한국 시간 중복 라벨만 생략한다.

Compose M3에는 읽기 전용 하루 시간축이 없어 기존의 좁은 custom plot에 배색 오버레이와 정확한 교집합 점선을 추가했다. native colors/typography/shapes/icons를 유지하고 SDK/라이브러리를 추가·업그레이드하지 않았다. [Google Material warning 원본](https://github.com/google/material-design-icons/blob/master/src/alert/warning/materialicons/24px.svg), [runtime permission 안내](https://developer.android.com/training/permissions/requesting), [정책에 의한 권한 제한 확인](https://developer.android.com/reference/android/content/pm/PackageManager#isPermissionRevokedByPolicy(java.lang.String,%20java.lang.String))을 참고했다.

Calendar의 VISIBLE은 UI 표시 선택이지 free/busy가 아니므로 숨긴 캘린더도 조회 가능한 바쁜 시간에는 포함한다. SQL fixture의 visible=0 행도 유지되는지 검증한다. 명시적 availability/status/self-status 제외 규칙만 적용한다.

최종 실행 및 대표 화면: [VERIFICATION](VERIFICATION.md). [CalendarColumns.VISIBLE](https://developer.android.com/reference/android/provider/CalendarContract.CalendarColumns#VISIBLE)의 화면 표시 여부를 free/busy로 해석하지 않는다.
