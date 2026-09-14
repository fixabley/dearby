# Issue10 · 바쁜 시간 연결 및 활동 겹침

2026-09-15 KST 실행 기록. PR8 head458f12d를 부모로 하는 `fixabley/dearby-ios-calendar-busy`, 별도 Draft PR base `fixabley/dearby-ios-2`다. Refs #10; 부모 PR8에 권한 기능을 섞지 않는다.

## 구현/경계

단일 상세 BusyCalendarSession은 default OFF, 동의 전 권한요청0, 취소→OFF, 계속→권한요청, granted 재사용, denied/restricted/failed/empty를 분리한다. 앱 첫화면 또는 상세 진입에서 OS 권한 요청은 없다. 확정된 활동의 선택 날짜만 조회하며 신청 접수기간은 지속 참석 시간으로 판단하지 않는다. 부분 조회 결과는 선택 날짜 기준이며 참여 가능을 보장하지 않는다.

EventKitBusyProvider actor가 fullAccess 및 선택일 predicate/occurrence enumeration을 소유한다. 취소/free/본인declined 제외, 반복은 OS가 전개한 occurrences, 시간대·종일·자정은 절대시각의 반열린 clip으로 처리한다. 27시간 이하 한 선택일 범위만 허용하고 긴 활동 전체를 읽지 않는다. 선택 날짜 구간을 union하여 중복/겹침을 합치고 활동 교집합만 계산한다. 제목/장소/참석자 상세/ID/메모는 UI/로그/서버/디스크에 남기지 않는다. 참가자 객체는 adapter 내부 본인 거절 boolean 판별에만 사용한다. EventKit 객체는 actor 밖으로 전달하지 않고 start/end Date만 일시 메모리로 반환한다. 조회 뒤 store.reset 및 참조 정리, OFF/close/background/revoke 시 결과 즉시 제거·취소·generation 무효화, 늦은 응답 무시. 실제 권한 alert의 inactive는 유지하고 실제 background에서만 중단한다. foreground/선택날짜/저장소 알림은 재조회한다.

Shared는 busy interval/표시 상태/콜백만 받는다. custom timeline은 활동 accent와 busy secondary 반투명 배경을 본문 시간축에 그리며 실제 양의 교집합만 점선, 활동 warning 및 AX를 제공한다. 접점0이나 시각적 최소 activity 높이로 가짜 겹침을 만들지 않는다. 활동명과 busy 명칭은 좌우에 두어 가림을 줄이고 짧은 구간은 정확한 높이와 인접 전체 시각 요약으로 의미를 보존한다. 상세 날짜는 날짜·요일 body / 다음줄 시간 subheadline, 한국 시간 중복라벨만 생략; 내부 sourcezone/DST/원문fallback/export 그대로다.

## 이번 실행 결과

- `run_busy_calendar.sh`: PASS, [state-tests.txt](state-tests.txt). 겹침/비겹침/접점/1초/종일/중복반복 occurrence/filter, Korean 시각/자정/DST offset, OFF/동의취소/계속/복수활동 단일권한/재사용/거절/제한/실패/빈성공/조회중OFF/이전날stale/권한철회/close/background/resume 검증. 별도 held permission으로 inactive 및 Continue 직후 alert-dismissal은 허용 결과 유지, 실제 background 뒤 지연 허용은 OFF 유지 검증.
- `run_detail_presentations.sh`: 이번 소스에서 PASS. 날짜·시간·장소·safe URL, 다일 first/middle/last/end-midnight/23·25·23.5시간/DST fold/짧은시간/불명precision/100년 bounded projection. FSD 93 Swift files 및 guard 부정 fixture PASS.
- Xcode26.6/Swift6, iOS26.5 전용4156: Debug build+run 16:34:44Z PASS, 경고0/오류0; Release build 16:35:56Z PASS, 경고0/오류0. 생성 Info.plist의 fullAccess 목적문구 양쪽 확인, Release binary에 debug fixture switch/provider 없는 것 확인. 로그는 아래 로컬 경로.
- UI는 오직 `--busy-calendar-fixture=overlap|denied|touch`로 실행한 가짜 provider이며 EventKit 생성/OS권한 허용/개인일정 조회를 하지 않는다. Toggle→native 안내→계속→fakeON 및 deniedOFF, 취소OFF, ON→OFF 제거를 실제 MCP touch와 window3055 PNG로 확인했다. 마지막 표시변경의 light/dark/AX5 및 접점 화면을 갱신했다. 이전 얇은 lane/두열/gutter 시안 증거는 최종 PNG로 교체했다.

| 증거 | 실제 확인 |
| --- | --- |
| [consent.png](consent.png) | 정확한 목적 안내와 native 계속/취소 (표시 최종정리 전 동일 제어) |
| [off-after-cancel.png](off-after-cancel.png) | 동의 취소 후 OFF, 조회 없음 안내 |
| [fake-denied.png](fake-denied.png) | fake 거절은 OFF 및 권한 없음/설정 버튼, 빈성공 아님 |
| [fake-overlap.png](fake-overlap.png) | 최종 날짜/시간 분리, 활동14–16와 busy15–17, 점선15–16만 |
| [fake-overlap-dark.png](fake-overlap-dark.png) | 동일 최종 상태 dark |
| [fake-overlap-ax5.png](fake-overlap-ax5.png) | 최대 접근성 글자크기, 독립 블록/점선/경고, 세로스크롤 |
| [fake-touch.png](fake-touch.png) | 활동14–16 / busy16–17 접점: busy 표시하되 점선·경고 없음 |
| [off-after-on.png](off-after-on.png) | 최종 ON→OFF 즉시 busy/점선/결과 제거 |

## 미검증 및 제한

실제 OS 권한 허용·개인 일정 조회·계정별 반복/종일 occurrence를 실행하지 않았다. EventKit adapter는 SDK 빌드 및 공식 문서로 확인했고 fake 상태/값 테스트가 OS backend 검증을 대신한다고 주장하지 않는다. enumeration API는 명시적 오류 반환 채널이 없으므로 권한 사전/사후 검사 외 시스템 backend 오류와 빈 목록의 구분은 보장할 수 없다. synchronous OS enumeration의 즉시 중단을 보장하지 않지만 callback 취소검사/stop·actor reset과 generation guard로 이후 화면 반영은 차단한다.

VoiceOver 실제 음성, 모든 기기/공고/크기, OS 실제 철회/설정 이동, Canvas는 미검증이다. 짧은 busy 구간은 시간 높이를 늘리지 않아 블록 내부 문자가 생략/잘릴 수 있고 DisclosureGroup 및 AX가 전체 시각을 제공한다. AX 트리 stale/Orca synthetic input 무반응이 있어 명령 성공만으로 UI 통과 처리하지 않았으며 실제 window PNG를 확인했다. 전체 이전 suite/36장 재촬영을 하지 않았다. 기존 export/map/cache/favorites는 소스 계약 유지, 이번에 Calendar Save/외부 링크/지도/즐겨찾기 조작 없음. A434/C38E untouched;4156은 light/large로 복원했다.

## Apple API 근거 (이번 작업에서 확인)

- [Accessing the event store](https://developer.apple.com/documentation/eventkit/accessing-the-event-store): 읽기에는 full access 필요. OS 권한은 쓰기도 허용하지만 이 구현은 읽기 전용.
- [requestFullAccessToEvents](https://developer.apple.com/documentation/eventkit/ekeventstore/requestfullaccesstoevents(completion:)) 및 [usage description](https://developer.apple.com/documentation/BundleResources/Information-Property-List/NSCalendarsFullAccessUsageDescription): 명시 동의 후 native 권한 요청과 목적문구.
- [Retrieving events and reminders](https://developer.apple.com/documentation/eventkit/retrieving-events-and-reminders), [enumerateEvents](https://developer.apple.com/documentation/EventKit/EKEventStore/enumerateEvents(matching:using:)): 날짜 predicate의 occurrence 조회를 main actor 밖으로 격리.
- [Updating with notifications](https://developer.apple.com/documentation/EventKit/updating-with-notifications): 저장소 변경 후 재조회.
- [participantStatus](https://developer.apple.com/documentation/eventkit/ekparticipant/participantstatus), [declined](https://developer.apple.com/documentation/eventkit/ekparticipantstatus/declined): 본인 거절 제외.

로컬 build logs: `~/Library/Developer/XcodeBuildMCP/workspaces/dearby-ios-2-e039b1051c4c/logs/build_run_sim_2026-09-14T16-34-44-906Z_pid74437_853b0ba6.log`, `build_sim_2026-09-14T16-35-56-809Z_pid74437_c07d229f.log`.
