# 기기 캘린더 — 승인 범위와 구현 인계

갱신 2026-09-15 01:36 KST. 최신 정본은 GitHub issue10 및 아래 최우선 환경설정 변경. GitHub https://github.com/fixabley/dearby/issues/10. 구현 진행 중이며 완료되지 않았다.

## 목적

기기 캘린더의 바쁜 시간을 공고 상세 시간축에 표시해 실제 활동 시간과 겹치는지 확인한다. 사용자가 승인한 범위는 제목/장소 없이 바쁜 시간 블록·겹침 여부만 표시, 기기 내부 비교, 서버 전송 없음이다.

## 범위와 계약

- 사용자가 상세에서 명시적으로 캘린더 연결을 요청한 뒤 OS 권한을 요청한다. 자동 최초화면 권한 요청 없음.
- iOS EventKit fullAccess(읽기에 필요한 OS 권한), Android READ_CALENDAR만 사용한다. 구현은 읽기 전용이며 일정 생성/수정/삭제와 WRITE_CALENDAR 추가는 하지 않는다.
- 권한 미요청/거절/제한/조회중/실패/성공(빈 결과 포함)을 구분한다. 거절·실패를 빈 시간으로 표현하지 않는다.
- 실제 활동의 확정된 기간과 비교한다. 신청 접수기간은 지속적인 참석 시간으로 보지 않고 충돌 판단에서 제외한다.
- 선택 날짜 범위만 조회하고 반복 일정은 OS가 전개한 occurrence를 사용한다. 취소/명시적으로 한가함/본인 거절 일정은 제외하며 시간대·종일·자정·부분겹침 경계를 처리한다.
- 전체 활동기간을 모두 조회하지 않은 경우 선택 날짜 기준임을 명확히 표시한다. 겹침 없음은 참여 가능 보장이 아닌 기기 일정 기준의 결과다.
- 기기 일정 제목·장소·참석자·메모는 화면/로그/서버/디스크 캐시에 남기지 않는다. 읽은 구간은 일시 인메모리 상태이며 권한 철회·화면 종료 시 정리한다.
- Shared/UI는 busy interval 값·표시상태·콜백만 받는다. OS 조회/권한/상태는 별도 feature와 App/화면 조립으로 격리한다.
- 권한 변경·앱 복귀·선택 날짜 변경에 재조회하고 이전 요청이 늦게 완료돼 최신 화면을 덮지 않게 한다.

## 검증

임시 provider로 겹침/비겹침/접점/종일/반복/실패/거절/철회/취소·stale 결과를 검증한다. 플랫폼 빌드와 대표 화면 및 권한 거절/연결 전 UI를 확인한다. 실제 사용자 캘린더 조회·권한 허용·기존 일정 수정은 자동 검증에서 수행하지 않는다.

시간축 디자인 #2의 PR8/9를 선행으로 하는 플랫폼별 작은 후속 Draft PR로 구성한다.

## 추가 사용자 결정: 동의 안내와 표시 스위치
- 상세 화면에 네이티브 `내 일정 표시` 스위치를 제공하고 최초 기본값은 끔이다.
- 처음 켤 때 OS 권한 요청 전에 목적을 안내한다: “캘린더의 바쁜 시간 정보를 가져와 활동 일정과 겹치는 시간을 확인합니다. 일정 제목·장소는 표시하지 않으며, 서버로 전송하지 않습니다.” 계속/취소 선택 후 계속에서만 OS 권한을 요청한다.
- 권한을 이미 허용했다면 스위치로 표시/숨김을 바로 전환한다. 끄면 조회를 중단하고 인메모리 바쁜 시간과 겹침 결과를 제거하며, 지연된 조회 결과가 다시 표시되지 않는다. OS 권한 자체를 철회하는 것은 아니다.
- 동의 취소·권한 거절은 표시 켜짐/일정 없음으로 취급하지 않는다. 다시 시도하거나 설정으로 이동할 수 있게 한다.
- 권한 안내·스위치 OFF/ON·조회 도중 OFF·지연 결과·거절 상태를 fake provider로 검증한다.

## 현재 담당
Android task_b230cc9d6d88 / ctx_8ade3dbca19e, term_3addcbfe-11e6-456f-96d7-9ad3af965286. 기존 dearby-android worktree에서 fixabley/dearby-android-calendar-busy 브랜치로 구현, PR base fixabley/dearby-android. iOS는 직전 timeline task_6db37b235ba5/ctx_f3f9269635ed 완료 보고를 기다린 후 같은 terminal에서 재사용 예정. iOS timeline remote458f12d는 root가 rangecheck/apps-only/대표PNG/검증문서를 읽었다.
Task spec은 /tmp/dearby-busy-ios-spec.md 및 android-spec.md. Android 시작receipt /tmp/dearby-busy-android-start.json. 두 플랫폼 source/query 권한은 Shared에서 분리, 같은 표시 의도만 공유하고 각 OS의 native control 및 상태 도구 사용.

## 공식 참고
- https://developer.apple.com/documentation/eventkit/accessing-the-event-store
- https://developer.apple.com/documentation/eventkit/retrieving-events-and-reminders
- https://developer.android.com/identity/providers/calendar-provider
- https://developer.android.com/reference/android/provider/CalendarContract.EventsColumns
Android 종일 일정은 timezone-independent 날짜다. UTC millis를 sourcezone에 그대로 투영해 서울09시 종일블록으로 오해하지 않도록 검증한다. tentative는 busy 취급, free/canceled/본인declined 제외.

iOS timeline 완료 delivery_ecc34b04a308 검토수락/ack, same terminal 재사용 #10 task_11eadc19d0b0 / ctx_a0a1c01f677b ready·turnStart observed. 시작receipt /tmp/dearby-busy-ios-start.json. 현재 두 #10 작업 모두 active, timeline 둘 다 succeeded. iOS후속 branch fixabley/dearby-ios-calendar-busy / base fixabley/dearby-ios-2. 종료 전 두 #10 완료검증 및 retain/ack/reclaimable0 필요.

## 시간축 블록 표시 최신 검토
사용자가 옆에 얇게 표시하기보다 타임라인의 직관적인 블록을 문의했다. root는 가림/혼잡 단점과 겹칠때 나란히 배치하는 방안을 설명했고 승인된 busy block 기능의 UI 품질로 두 담당에게 적용 요청했다. 충분한 너비, 바쁜 시간+한국어 시각 label, 겹치는 시간 색+테두리, 합친 개인 busy구간, 비겹침 fullwidth/겹침 두열. 짧은 구간 정확한높이/보조문장/AX 유지. Android raw ZonedDateTime/종료제외 같은 구현용문구 삭제. 전달 msg_05e18e9b11d4(iOS), msg_8ed42c5204c5(Android), 현재 active Task 유지.

## 최신 사용자 요청: 범례와 경고
타임라인 왼쪽 상단에 활동 / 내 일정 범례를 표시하고 시간 눈금 공간은 유지한다. 실제로 겹치는 블록에 native 경고 아이콘을 표시하며 접근성에도 겹침을 전달한다. 끝·시작 접점이나 시각적 최소높이 확대는 실제 충돌로 취급하지 않는다. 짧은 구간은 인접 요약/접근성으로 경고와 정확한 시간을 보존한다.

## 최우선 시각 결정 — 시간 눈금 배경 오버랩
사용자가 시간 눈금 위에 오버랩 되는 구조를 재문의했다. 앞선 좌측범례는 상단legend가 아니라 시간gutter 의미로 root가 재해석해 사용자에게 명시했다. 최신형태: 내 일정은 시간눈금 영역 뒤 반투명 배경블록, 시간글자/눈금선은 위에 읽기, 활동은 기존전체너비, 실제겹치는 활동에 경고아이콘+AX. OFF시 배경/경고함께제거. 앞선 fullwidth busy/두열/우측lane 방안은 폐기. 개인busy union 및 exacty/height, 짧은구간 보조한국어시각 유지. 두activeworker에게 최신정정전달.

## 16:20Z 중간 검토
iOS state-tests.txt: inactive 권한프롬프트/Continue 처리, 실제background 지연 grant 무시, halfopen/merge/종일/필터, OFF/동의/거절/제한/실패/빈결과/stale/revoke/resume/close와 FSD90 통과. 최종 시각 수정 검증은 아직 남음. Android provider 숫자필드만 projection/NULL 허용 및 종일 별도UTC-datequery, 기본provider/session JVM7+계측3 보고 읽음. root 발견한 SQL NULL누락 및 permission중background→Requesting stuck를 수정했고 관련회귀테스트 추가됨. iOS동일permission inactive혼동도 lifecycle case로 테스트됨. 최신overlay UI 완료/PR생성은 아직 안 됐으며 두worker active.

## 최우선 16:28Z 사용자 재결정
사용자가 타임라인 안에 primary 색과 배색으로 블록표시, 겹치는 부분 점선을 요청. gutter배경방안 폐기. 활동primary/accent + 개인busy구분되는 semantic보조색 본문블록, 반투명중첩, 실제intersection만 점선overlay. warning아이콘/ONOFF 유지. 두activeworker 최신지시전달. 이전 gutter/두열방안은 현재완료기준 아님. 이슈10 본문을 최종기준으로 교체함.

## Android 완료 후 최신표시 후속 Task
16:29Z 직전 Android #10 task_b230cc9d6d88/ctx_8ade3dbca19e succeeded, PR11 https://github.com/fixabley/dearby/pull/11 head1cd2ae66d7d0aaedc2db3158b89477a9aedce874 basefixabley/dearby-android. JVM13/계측12/debug/lint/FSD92+24/대표10장 보고, root apps-only/rangecheck/대표PNG검토. 사용자가직전 gutter형태에서 본문배색+점선으로다시변경하여 새 Task 필요. inactive전달실패 후 완료inbox확인/수락, terminal즉시재사용: task_67f4ae81cfc8 / ctx_9489e36b2512 ready/turnStart. receipt /tmp/dearby-busy-android-overlay-start.json, spec /tmp/dearby-busy-android-overlay-spec.md. PR11같은branch에서최종표시만후속. delivery_daf91d817452(iosheartbeat4+androiddone) 처리/ack. iOS ctx_a0a1c01f677b는 active인채최신본문+점선지시 msg_deac912ff86d 전달됨.

## 상세 기간 줄바꿈 요청
새 userPNG TzOaC7 01:29:54는 날짜요일뒤시간이 한줄이어져 오후/4시까지가 갈라지는 iOS상세였다. root view_image 직접읽고 rawbytes docs/context/references/calendar-screenshots/user-reference-period-linebreak.png 및 /tmp/dearby-period-linebreak-reference.png 보존. 사용자제안 채택: 요일뒤줄바꿈(date첫줄/time다음줄한단계작은nativefont/timezone보조줄). 큰글자시간자연줄바꿈허용 잘림금지. 상세에만 적용, 카드/export/원본precision불변. 두현재active Task에 전달; Android기존구성이동일하면변경없이확인.

추가 최신: 사용자가 한국 시간 표시 생략 요청. 상세의 한국 시간 보조라벨 제거, 날짜/시간줄분리 유지. 내부sourcezone/AsiaSeoul/export/원본데이터 불변. 다른시간대/모호한DST의 유의미한 offset까지없애라는뜻아님. 두activeworker전달완료.

## 최우선 환경설정 이동 / 최초실행 안내 (모든 상세스위치 규칙보다 우선)
사용자 최신: 내 일정 표시를 겹치는 일정 확인하기로 변경, 환경설정 이동, 확인메시지 최초실행. root 해석을 사용자에게 설명: 최초실행 설명+켜기/나중에1회, 켜기에서만OS권한, 이후환경설정ONOFF재실행유지, 상세에는결과만. App공유preference/permission owner, 설정boolean만영속/개인busy는상세임시. detail열때enabled+권한으로조회, OFF/닫기/배경은query취소/메모리삭제. Native환경설정page와gear진입점, 불필요한새tab없음. spec /tmp/dearby-calendar-settings-steering.md, 두현재Task전달성공 msg_b5b56d93ea66(ios), msg_33ed6b51d9c4(android). delivery_814fb116283d heartbeat2 처리/ack. GitHub #10 본문은 모순없이최신규격으로재작성했다.

## 추천피드 버그 최신 추가
사용자가 스크롤시쇼츠처럼1장씩아니라쭈욱넘어간다고보고. root iOS DiscoveryView.swift 직접읽음: typeSize.isAccessibilitySize이면height nil/DiscoveryPagingBehavior(enabled:false) 자유스크롤분기(부모PR8코드,#10diff없음). 원인가능성사용자에설명, 실제현재AX설정/재현은담당확인필요. 사용자최신기준으로큰글자에도바깥1장paging유지/긴내용은카드내접근, 이전AXfree정책폐기. iOSactive담당별도fix기능커밋/관련실기검증배정; AndroidVerticalPager유지이므로회귀확인만. 현재settings/firstprompt/배색점선/날짜줄/한국시간제거도계속완료.

최신 추가: 사용자가 겹치는시간3시~4시로표시요청. summary도busy전체(15~17)가아니라활동14~16과교집합15~16을표시. 문구/점선같은intersectionsource사용/하드코딩금지. 여러교집합구간명확히, 개인fullbusy보이면별도바쁜시간라벨. 두activeworker전달완료.


## 최종 완료 확인 · 2026-09-15 02:00 KST

최신 사용자 요청까지 두 플랫폼 완료. 겹침 문구와 점선은 실제 교집합만 표시한다(활동14–16/busy15–17 → 오후 3시부터 오후 4시까지). 환경설정의 영속 `겹치는 일정 확인하기`, 최초 1회 켜기/나중에 설명, 날짜/시간 줄분리와 한국 시간 중복라벨 생략 포함. iOS AX 자유스크롤 회귀 수정: 바깥 한장 paging, 큰 글자 카드 내부 스크롤 및 이전/다음 버튼. Android 기존 VerticalPager 빠른 이동/저장/상세 검증.

iOS Draft PR12 https://github.com/fixabley/dearby/pull/12 head97eff3a1f7eddcb60119a149ab705e11e8309189, base PR8 branch. Android Draft PR11 https://github.com/fixabley/dearby/pull/11 headc3881f6a0350718ada2f6c2e1cc9754c5bbea5dd, base PR9 branch. 병합하지 않음. Root가 두 범위 diff --check 통과 및 각각 apps/ios 53파일/apps/android 56파일만 포함 확인.

iOS fake preference/session/interval/date/place/timeline 테스트, FSD99, Debug/Release 통과. 일반 large fast fling1→2→1, AX5 버튼으로2번 이동/내부스크롤/상세, 실제 double-click 저장feedback 확인. Android 관련 JVM8/계측11, Debug/AndroidTest, FSD98/selftest24 통과. 최종 대표 PNG와 검증기록을 root 리뷰. 실제 개인 일정 조회/OS권한 허용/Calendar Save는 하지 않았으며 fake 기반 검증, OEM/실기 및 EventKit 오류 구분 한계는 앱 검증 문서에 유지.

두 active task의 worker_done succeeded를 확인했고 ctx_a0a1c01f677b/ctx_9489e36b2512 모두 사용자 요청에 따라 retained. delivery_1e8138f0d47c ack 후 inbox0. 현재 미완료 worker 없음. 플랫폼 세션과 worktree는 다음 작업을 위해 유지.
