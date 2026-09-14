# 일정·아이콘 후속 검증 — 2026-09-14

대상: PR8의 후속 구현, Xcode26.6/SDK26.6, iOS26.5. 최종 build 15:01:30Z 시작/성공(카드의 신청과 활동 장소 label을 구분하는 마지막 UI 보완 포함). `regression-tests.txt`는 최종 전체 standalone suite exit0, 앱/공통 fixture를 읽기만 한 모델·캐시·SwiftData·favorites·calendar·map 회귀 및 FSD69파일/negative fixtures 결과다. build.txt에는 경고/오류가 없다.

## 기기와 입력 구분

초기 c1045f1 검증은 ../followup 기록을 따른다. A434에서 원인 미상의 UI 전환이 다시 관찰되어 사용자 조작으로 단정하지 않고 중단했다. 코디네이터의 명시적 지시에 따라 새 `Dearby-Issue2-Schedule-Verify` (4156AE92-5308-4050-93DC-C9E241918DCB, iPhone17Pro)를 생성했다. 이후 모든 동작은 이 UDID/Simulator window3055에 한정했다. A434/C38E는 이후 조작하지 않았다. 새 기기의 마지막 설정은 light/large, 발견 첫 카드이며 기기 종료/초기화는 하지 않았다.

Orca AX tree는 sheet의 실제 라벨을 확인하는 데 사용했다. XcodeBuildMCP는 sheet가 눈에 보이는데도 배경 discovery targets를 반환하는 경우가 재현됐다. Orca AXPress도 성공 응답 직후 결과가 없거나 전환 전 snapshot을 반환했다. 따라서 캘린더 판정에는 새 화면 캡처를 확인한 window 좌표 클릭을 사용했다. 이는 UI 자동화의 제약이며 앱 회귀나 사용자 입력으로 단정하지 않는다.

## 성공한 실제 동작

- 새 기기 미저장 KRC 카드 제목에 `orca computer click --window-id 3055 --x 190 --y 350 --click-count 2`: `discovery-light.png` 빈 heart → `doubletap-saved.png` 채워진 heart/저장됨. 두 느린 tap 또는 접근성 custom save action으로 대체하지 않았다. 이후 즐겨찾기에 KRC가 보였고 삭제 아이콘으로 빈 상태 전환을 확인했다.
- 새 detail의 실제 AX label은 `신청 기간 캘린더에 추가`, `행사 캘린더에 추가: 2026.9.15 14:00–16:00 (한국 시간)`이다 (`detail-ax-tree.json`). window 좌표 신청(81,470), 행사(82,735) 각각 실제 클릭으로 editor 진입했다. application-editor.png는 [신청 기간] 8/27 09:00→9/15 13:00, schedule-editor.png는 [행사] 9/15 14:00→16:00 및 해당 장소를 보여준다.
- 두 editor 모두 X(64,214) → 폐기 팝업 확인 → 변경 사항 폐기(159,292). application-cancel.png/schedule-cancel.png에서 동일 상세로 돌아왔다. 체크 표시/실제 calendar save는 누르지 않았다. 행사 editor에서 첫 keyboard tutorial이 나타났지만 날짜·제목은 그대로였고 입력하지 않고 취소했다.
- 행사 지도(133,735) 클릭으로 Maps를 실행했다 (`map-launch.png`). 새 기기 첫 실행 위치 권한 화면은 거절했다. 이 새 기기에서 pin까지 재확인한 것은 아니며 정확한 pin/좌표 증거는 ../followup/map-launch.png의 c1045f1 결과다.
- 실제 발견 swipe로 1→3→4 공고 이동, detail 진입 및 목록 scroll을 확인했다. 기본 크기 light/dark의 발견·상세·다단계·즐겨찾기 PNG, AX5 light/dark의 일정·즐겨찾기 PNG를 확인했다. discovery-ax5-light.png는 제목 아래까지 실제 스크롤한 저장/정보 액션 위치다. compact viewport는 기존 정책대로 카드 fact를 생략하며 전체 정보는 detail에 있다.

## 독립 코드/회귀 검증과 남은 한계

State의 schedule 배열은 원본 phase 순서를 유지한다. 테스트는 각 index의 이름/기간/좌표 있는 장소, 온라인 장소 분리, 장소 미확인, 연도 경계·시간 미확인·종료 미확인·invalid date/timezone·역전 기간·초 보존을 검증한다. App의 같은 원본 배열에서 생성하는 calendar callback 순서와 detail의 index 연결은 소스 검토했으며 mapper 원본URL/메모/날짜·지도 회귀도 통과했다. 다단계 공고의 모든 editor를 각각 열지는 않았다.

VoiceOver 실제 음성 낭독과 모든 기종/방향은 미검증이다. CLI compact AX tree가 raw frame bounds를 제공하지 않아 정확한 AX frame 수치를 얻지 못했다. 대신 window screenshot의 아이콘 좌표와 소스상 최소44×44pt(캘린더/지도/삭제), native large controls(저장/정보)를 확인했다. baseline f963401 재실행은 하지 않았다: c1045f1과 최신 구현에서 편집기 진입을 실제 확인해 List/Section 전환 때문에 진입 불가라는 회귀는 재현되지 않았다.

## 파일

- `discovery-*`, `doubletap-saved.png`: 표시/실제 doubletap
- `detail-*`, `application-*`, `schedule-*`: 메타데이터/캘린더/취소/큰 글자
- `multiple-schedules-*`: 온라인 예선·결선 진출 발표·결선·시상과 AX tree
- `favorites-*`: icon-only 삭제와 저장 목록 light/dark/AX5
- `map-launch.png`, `build.txt`, `regression-tests.txt`

discovery-final-light/dark.png는 마지막 활동 장소 label 보완 이후이며 다른 캡처의 기능/상세 레이아웃은 그대로다.

before는 ../before-* 및 ../followup, 이 디렉터리는 after다. 임시 스크린샷 경로를 포함하는 AX JSON의 durable PNG는 동 디렉터리 해당 이름의 PNG를 이용한다.

실행 로그의 행 끝 공백과 마지막 빈 줄만 정규화했다. 원본 실행결과 내용과 PASS/오류 fixture 출력은 유지했다.
