# 환경설정과 최초 실행 안내

App `CalendarSettingsController`가 표시 선택과 읽기 권한 요청을 조율한다. `SharedPreferencesCalendarSettingsStore`는 별도 `dearby.calendar.settings.v1` 파일의 `enabled`, `firstPromptHandled` boolean만 저장한다. 개인 구간·캘린더 ID·제목·장소는 저장하지 않는다.

첫 실행에는 목적을 설명하는 native AlertDialog를 한 번 표시한다. 명시적인 ‘켜기’ 또는 ‘나중에’ 응답에서 안내 완료를 저장한다. 나중에는 OFF이며 OS 요청을 하지 않는다. 켜기에서만 READ_CALENDAR를 요청하고 허용 후 ON을 저장한다. 거절/제한/권한 요청 실패는 OFF다. 이미 권한이 있으면 다시 요청하지 않는다. 앱 재실행은 저장된 선택을 읽고, 권한이 철회되면 OFF를 저장한다. 실제 ON_STOP 중 미완료 권한 요청은 취소하며 늦은 callback으로 켜지지 않는다.

기존 상단의 앱 이름 옆 Material settings gear가 Pages/Settings의 native sheet/ListItem/Switch를 연다. 스위치 이름은 ‘겹치는 일정 확인하기’다. 새 탭은 없다. App `CalendarSettingsHost`만 ActivityResult launcher와 lifecycle을 소유하며 Page는 SettingsState와 콜백만 받는다.

상세는 동의나 스위치를 소유하지 않고 전역 enabled와 결과만 받는다. App `NoticeDetailRoute`는 여전히 상세마다 BusySession을 만들고, 상세 종료/백그라운드/OFF에 메모리를 지운다. OFF 값은 먼저 표시 결과를 비우고 세션 query를 취소하여 지연 결과를 막는다. 설정 ON은 재실행 후 유지되지만 구간 결과는 유지되지 않는다. 신청 기간은 조회에 등록하지 않는다. 조회 실패는 빈 시간으로 표시하지 않고 상세 재진입을 안내한다.

검증은 임시 store/provider로만 실행했다. 최초 안내 1회/나중에 요청0/권한 거절/허용 후 재생성 유지/기존 허용 재사용/설정 OFF/지연 callback/재개 철회 및 상세 query 중 OFF를 확인했다. 실제 사용자 설정·캘린더 권한 허용/개인 조회는 자동 검증하지 않았다.
