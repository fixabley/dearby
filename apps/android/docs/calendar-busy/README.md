# 기기 바쁜 시간 · #10

PR9의 시간축 위에 읽기 전용 기능을 쌓는다. `BusyProvider`는 익명 `BusyInterval`만 반환하고 `BusySession`은 상세 수명의 동의·권한·조회·취소를 소유한다. Shared/UI는 범용 구간 연산 및 표시값만 제공한다. 신청 기간은 조회/비교에 등록하지 않는다.

Android `CalendarContract.Instances`는 선택일 범위의 반복 occurrence를 전개한다. 숫자 BEGIN/END/ALL_DAY/AVAILABILITY/STATUS/SELF_ATTENDEE_STATUS만 projection하고 제목·장소·메모·주최자 문자열은 읽지 않는다. 명시적 free/canceled/declined만 제외하며 NULL/unknown 및 tentative는 busy로 유지한다. 권한 거절/오류는 빈 결과와 별도 상태다.

ALL_DAY의 UTC midnight는 floating 날짜다. 별도 UTC-date query 후 기기 시간대 자정으로 변환하고 활동 source-zone의 선택일에 clip한다. 23/25시간 DST 하루 및 배타적 종료를 지킨다. 기기 시간대는 조회마다 읽으며 전체 활동기간 배열/조회를 만들지 않는다.

조회는 IO에서 실행하고 CancellationSignal로 cursor 작업을 취소한다. OFF/화면종료/백그라운드는 메모리를 비우며 generation/revision fence가 비협조적인 지연 결과까지 차단한다. 권한 철회는 결과를 지운다. Room/cache/서버/캘린더 export에는 연결하지 않는다.

공식 근거: [Instances](https://developer.android.com/reference/android/provider/CalendarContract.Instances), [EventsColumns: ALL_DAY 및 availability](https://developer.android.com/reference/android/provider/CalendarContract.EventsColumns). OS recurrence expansion은 API 계약에 의존하며 개인 캘린더를 자동 검증에서 읽지 않는다.

검증(2026-09-15): 새 BusyInterval/BusySession JVM 7개 통과, 전용5556의 AndroidBusyProvider 계측 3개 통과. in-memory SQLite numeric fixture로 SQL NULL/tentative/free/canceled/declined, projection/bounds/IO, floating 종일, null cursor 실패 및 CancellationSignal 전달을 실행했다. OS CalendarProvider 및 실제 계정 반복 확장은 읽지 않았고 공식 Instances 계약에 의존한다.
