# 공고 카드 일정·위치 검증 — 2026-09-15

## 구현

신청 기간 및 schedule 원본 순서를 왼쪽 일정명 / 가는 구분선 / 오른쪽 📅 기간과 📍 위치로 표시한다. 이모지는 본문과 분리해 긴 URL 줄바꿈의 시작점을 유지한다. 유효 좌표가 있는 phase 장소에만 44pt 이상 🗺️ 버튼과 장소별 접근성 이름을 제공한다.

NoticeCardViewModel이 순수 NoticeCardScheduleState/NoticeCardPlaceState를 조합하고 View는 State+callback만 받는다. App은 notice ID·schedule index·venue index로 현재 캐시 모델의 정확한 phase 장소를 찾아 기존 VenueMapLauncher/Link를 실행한다. 상세·Shared CompactPeriod·저장소·도메인·캘린더 export는 바꾸지 않았다.

신청 위치는 submissionLocations+application.url만 사용한다. 행사 venue·source URL을 대입하지 않는다. 온라인 schedule은 온라인 URL 또는 URL 미확인을 표시하며 venue를 숨긴다. 나머지는 기존 phase 일치 장소를 조합하고 좌표가 없어도 장소를 남긴다. 날짜만 있는 값은 시간 미확인, 없는 종료는 종료 미확인이다. 시간대가 없으면 원본 offset을 남기고, 잘못된 시간대/역순 기간은 확인 필요를 표시한다. 한국 시간 접미사는 생략하고 다른 유효 시간대는 기간 끝에 한번 표시한다.

기본 카드 일정 영역은 native ScrollView, AX 또는 viewport<520 compact는 기존 카드 전체 ScrollView를 사용한다. compact에서도 참여 대상·신청·모든 일정이 남는다. 바깥 alwaysByOne paging·더블탭 저장 코드·상세 버튼은 유지했다.

## 이번 실행

- 전용 Simulator A617D464-41FC-4C33-A3AC-A109D5C9F054 (iPhone 17, iOS 26.5), Xcode 26.6. 다른 기기 조작 없음.
- 전체 `bash apps/ios/tests/run_standalone.sh` exit 0: 두 sample의 State/favorites/calendar, map, organization, SwiftData disk/snapshot 및 FSD. `standalone-tests.txt`. 고의로 잘못된 저장 경로를 사용하는 rollback fixture의 CoreData 오류는 예상 출력이며 최종 assertion PASS/exit 0 확인.
- 마지막 formatter 변경 후 NoticeViewModelTests를 재컴파일하고 두 sample 실행, FSD 101파일/negative fixtures PASS: `final-tests.txt`.
- 마지막 compact 보완 후 XcodeBuildMCP build_run_sim 성공, 경고/오류 없음: `build.txt`, 07:12:48Z 시작, PID 38072. 이후 alert 줄바꿈만 정리했다.
- 실제 일반 글자 swipe 1→2→3→4 공고 한 장씩 이동, 일정 영역 swipe로 마지막 일정 도달 확인. 상세 버튼의 sheet 화면 확인.
- 🗺️ 실제 tap → Apple Maps 초기 안내(위치/알림 허용하지 않음) → 재탭 후 정확한 중앙도서관 2관 세미나실 핀 확인. `map-destination.png`. 기존 https maps.apple.com universal link를 유지한다.
- AX5에서 카드 내부 스크롤로 신청 날짜·긴 URL·행사 날짜·장소 및 저장/상세 하단까지 도달. 마지막 이모지 정렬 후 긴 URL을 다시 캡처. 기기 글자 크기는 large로 복원했다.

## 화면 증거

- `krc-start-final.png`: 신청 기간, 긴 신청 URL, 일정명 열.
- `krc-map-final.png`: 같은 카드에서 스크롤한 행사 장소·지도 버튼.
- `multiple-start-final.png`, `online-final.png`, `multiple-end-final.png`: 신청+3 schedules, date-only, 없는 종료, 온라인 URL/신청 위치/발표 장소 미확인, 마지막 행사 장소.
- `ax5-final.png`: 최대 접근성 글자 URL 줄바꿈과 카드 내부 스크롤.
- `detail.png`: 기존 상세 sheet.
- `map-launch.png`, `map-destination.png`: 지도 앱 전환 및 최종 목적지 핀.

## 한계

실제 더블탭은 `orca computer click --click-count 2`와 `--restore-window` 재시도가 모두 window_not_focused로 입력 전 중단되어 미실행이다. 느린 두 tap 또는 접근성 저장 action을 더블탭 성공으로 대체하지 않았다. 기존 더블탭 코드 보존과 공유 즐겨찾기 State 회귀 PASS는 별도 근거다. compact 분기는 코드/빌드 검증이며 별도 작은 기기·가로모드 런타임은 미검증이다. VoiceOver 실제 낭독·물리 햅틱·실기기는 미검증이다. AX5의 두 열은 좁아서 여러 줄로 길게 이어지며 내용은 스크롤로 읽는다.

기존 미커밋 실행 보고서 `run-20260915/` 및 역할 문서의 이전 실행 기록은 이 기능 커밋에서 제외하고 작업 트리에 보존한다. 앱은 실행 상태로 유지하며 push/PR은 만들지 않는다.
