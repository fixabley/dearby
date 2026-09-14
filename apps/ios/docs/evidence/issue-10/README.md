# Issue10 · 기기 바쁜 시간과 활동 겹침

2026-09-15 KST 최종 기준. PR8 head458f12d 부모의 별도 fixabley/dearby-ios-calendar-busy branch / base fixabley/dearby-ios-2, Refs #10. 이 기록은 중간 상세스위치/gutter/회색 시안을 대체한다.

## 동작과 경계

최초 실행은 목적 설명과 켜기/나중에를 한 번 표시한다. 나중에는 OS 요청0, 켜기에서만 fullAccess 요청. 이후 gear→환경설정의 겹치는 일정 확인하기로 ON/OFF를 변경하며 재실행에 유지한다. App/CalendarPreferences가 permission/preference를 소유하고 비개인 enabled/firstPromptHandled boolean 두 개만 UserDefaults에 저장한다. 처음 alert가 화면 준비 전에 사라지는 문제를 발견해 초기 렌더 준비 뒤 표시하도록 수정했다.

상세에는 결과만 있으며 각 BusyCalendarSession의 개인 interval은 일시 메모리다. 설정OFF는 등록 세션을 즉시 취소/정리하고 늦은 권한/조회 응답도 generation으로 차단한다. close/background/revoke에서도 정리, foreground/선택일/저장소 변경에 재조회한다. 권한 dialog의 일시 inactive를 background로 오인해 취소하지 않는다. denied/restricted/failed/empty를 구분하고 거절·권한 실패는 전역enabledfalse다.

확정 활동의 선택일만 읽고 신청기간은 참석 충돌에서 제외한다. EventKit actor의 27시간 이하 한 날짜 predicate가 반복 occurrence를 전개하며 취소/free/본인declined 제외, half-open clip/union/양의 intersection을 사용한다. 제목/장소/ID/메모는 읽거나 UI/로그/서버/디스크에 남기지 않는다. 참가자 정보는 actor 내부 본인 거절 boolean 판별에만 사용한다. OS객체는 밖으로 전달하지 않고 Date 두 개만 반환, store.reset으로 참조 정리한다. 서버 전송·SwiftData/cache 저장 없음.

활동accent / adaptive systemTeal busy를 본문에 반투명으로 표시하고 실제 교집합만 점선·warning·자연어 겹침 시각을 보여준다. 활동14–16 / busy15–17이면 점선과 겹침문장은15–16이며 busy전체15–17은 바쁜시간으로 명확히 구분한다. 접점은 경고 없음. 짧은 구간은 정확한 높이, 전체시각 DisclosureGroup/AX; activity 최소 시각높이로 가짜 겹침을 만들지 않는다. 날짜·요일 body / 다음줄 시간 subheadline, 한국 시간 중복라벨만 제거하며 sourcezone/DST/fallback/export는 유지한다.

## 이번 검증

- [state-tests.txt](state-tests.txt): fake provider/session/preference, 최초한번/나중에0/계속허용·거절·실패·제한/재실행유지/기존권한재사용/OFF중단/지연권한·조회/권한철회/선택일stale/background/inactive/close PASS. 별도 temporary UserDefaults suite에서 두 boolean 키만 저장되는 것 확인 후 정리. 개인데이터 저장 없음. interval merge/접점/1초/종일/OS전개값 fixture/취소freedeclined/Korean 시각/자정/DST offset/14–16 대15–17 문장15–16 PASS.
- run_detail_presentations.sh: 이번 날짜·장소·safe URL/다일·DST·불명precision 기존계약 PASS. 전체 이전 suite는 재실행하지 않았다. FSD99 Swift files/부정 fixture PASS.
- Xcode26.6/Swift6/iOS26.5 전용4156: 최종 Debug build+run16:51:26Z 및 Release build16:52:06Z PASS, 경고0/오류0. 생성 Info.plist fullAccess 목적문구 확인, Release에 debug fixture 코드 없는 것 확인.
- UI는 오직 explicit debug fake-provider이며 OS fullAccess 허용/개인일정 조회/Calendar Save는 하지 않았다. 실제 first-prompt 나중에→gear/settingsOFF→ON→상세 결과, 별도 fake denied settingsOFF/설정경로 확인. 정상/접점 light, 정상 dark/AX5를 window3055 PNG로 확인. 마지막 intersection 문장 추가는 light/dark 갱신; AX5 그림은 같은 최종 배색/설정 구조에서 해당 한 줄 추가 전 촬영.
- [피드 검증](PAGING.md): 사용자 AX 자유스크롤 정책 폐기 후 고정 한장 native alwaysByOne. 일반 fast fling1→2→1, AX 카드내부 스크롤2/4유지/다음버튼/상세진입, 실제 click-count2로 이미저장된 KRC의 저장 feedback 확인(집합불변).

| 증거 | 확인한 상태 |
| --- | --- |
| [first-prompt.png](first-prompt.png) | 최초 native 켜기/나중에 및 정확한 목적문구 |
| [settings-off.png](settings-off.png) | 나중에 후 환경설정 OFF |
| [settings-on.png](settings-on.png) | 환경설정 fake ON, 상세 스위치 없음 |
| [settings-denied.png](settings-denied.png) | fake 거절 OFF·권한 없음·설정 열기 |
| [fake-overlap.png](fake-overlap.png) / [dark](fake-overlap-dark.png) | 날짜/시간 분리, 활동14–16 / busy15–17 / 점선 및 문장15–16 |
| [AX5](fake-overlap-ax5.png) | 큰글자 블록·점선·경고·세로스크롤 |
| [fake-touch.png](fake-touch.png) |14–16 /16–17 접점의 busy는 표시, 점선·경고 없음 |

## 미검증/제한

실제 OS 권한 허용·개인 일정·계정별 반복/종일 occurrence는 실행하지 않았다. OS adapter는 SDK 빌드와 공식 문서로 확인했으며 fake가 실제backend 검증을 대신한다고 주장하지 않는다. enumeration API에 명시 오류 채널이 없어 권한 사전/사후 검사 외 시스템 backend 오류와 빈 목록을 완전히 구분할 수 없다. synchronous query 즉시 종료를 보장하지 않으나 callback 취소검사/stop·reset 및 generation으로 뒤늦은 화면 반영을 차단한다.

VoiceOver 실제음성/모든크기·기기·공고/OS실제권한철회·설정이동/Canvas는 미검증이다. 짧은 block 내부 문자가 생략·잘릴 때 인접 요약/AX에서 전체시각을 제공한다. AX stale/일부 Orca synthetic 무반응으로 실제window PNG 및 MCP touch로 결과를 판정했다. 이전 중간스위치 사진은 최종증거에서 제거했다. A434/C38E untouched;4156 light/large 복원. 실제저장/외부URL/지도 조작 없음. 기존 models/source/cache/export/maps/favorites key 계약 유지.

## Apple API 근거 (이번 작업에서 확인)

- [Accessing the event store](https://developer.apple.com/documentation/eventkit/accessing-the-event-store): 읽기에는 full access 필요. OS 권한은 쓰기도 허용하지만 이 구현은 읽기 전용.
- [requestFullAccessToEvents](https://developer.apple.com/documentation/eventkit/ekeventstore/requestfullaccesstoevents(completion:)) 및 [usage description](https://developer.apple.com/documentation/BundleResources/Information-Property-List/NSCalendarsFullAccessUsageDescription): 명시 동의 후 native 권한 요청과 목적문구.
- [Retrieving events and reminders](https://developer.apple.com/documentation/eventkit/retrieving-events-and-reminders), [enumerateEvents](https://developer.apple.com/documentation/EventKit/EKEventStore/enumerateEvents(matching:using:)): 날짜 predicate의 occurrence 조회를 main actor 밖으로 격리.
- [Updating with notifications](https://developer.apple.com/documentation/EventKit/updating-with-notifications): 저장소 변경 후 재조회.
- [participantStatus](https://developer.apple.com/documentation/eventkit/ekparticipant/participantstatus), [declined](https://developer.apple.com/documentation/eventkit/ekparticipantstatus/declined): 본인 거절 제외.


최종 build logs: `~/Library/Developer/XcodeBuildMCP/workspaces/dearby-ios-2-e039b1051c4c/logs/build_run_sim_2026-09-14T16-51-26-328Z_pid74437_0e6d94f2.log`, `build_sim_2026-09-14T16-52-06-720Z_pid74437_08052476.log`.
