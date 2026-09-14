# 사용자 Calendar screenshot 후속 — 2026-09-15 KST

사용자 제공 `/tmp/dearby-calendar-reference-1.png`와 `-2.png`를 view_image로 직접 확인했다. 실제 이미지의 굵은 제목, 요일·오전/오후·부터/까지 문장, 도메인/열기 링크 카드와 시간 gutter/가로선/연속 파란 블록을 참고한다. 공유 버튼은 요구 범위가 아니므로 추가하지 않는다. 원본 링크를 열거나 공유하거나 일정 저장을 실행하지 않는다.

## 기간 문장과 링크 카드

EventPeriodPresentation은 같은날 날짜 한 번, 한국어 요일/오전·오후/분·초와 부터·까지를 표시한다. 기존 날짜만/시간대/충돌/역전/invalid의 원문 fallback은 유지한다. ExternalLinkCard는 검증된 URL과 접근성 label만 받고 native Link/borderedProminent/capsule을 사용한다. 신청 URL은 VM에서 원래 applicationInformation.url로, 온라인 URL은 기존 State에서 투영한다. 안전정책은 기존 HTTP(S)/host/무자격증명/무공백 검사를 그대로 재사용하며 네트워크 preview를 요청하지 않는다.

이 기능에서 실행: 독립 DetailPresentationTests 및 FSD74 PASS, Xcode26.6/iOS26.5 전용4156 build/run 2026-09-14 15:36:14Z 성공. 테스트는 요일·한국어 시간·원문/주소·안전URL을 포함하며 전체 standalone/OS 흐름 재실행이 아니다. 실제 화면 검증은 아래 타임라인 통합 검증에서 구별해 기록한다.

## 일간 미리보기 구현과 참고 API

- native compact DatePicker/이전·다음 버튼, 범위 제한·source timezone을 적용했다. [Apple DatePicker](https://developer.apple.com/documentation/swiftui/datepicker/init(selection:in:displayedcomponents:label:)) 및 [Calendar 날짜 연산](https://developer.apple.com/documentation/foundation/calendar/date(byadding:value:to:wrappingcomponents:))을 확인했다.
- Shared/Lib는 검증된 `[start,end)`를 선택일 Calendar day interval로 clip한다. 첫날9시~자정, 중간날자정~다음자정, 마지막날자정~13시이며 매일9~13시 반복이 아니다. 종료자정은 그 다음날을 포함하지 않는다. 선택 가능한 ClosedRange만 보유하고 긴 기간 모든날 배열을 만들지 않는다.
- 날짜만/마감만/혼합정밀도/invalid/역전/0길이에서는 timeline을 만들지 않는다. DST day는 실제 경과시간에 따른23/25/23.5시간 눈금을 사용하고 반복시각의 offset도 구별한다. 한국어 문장에서도 같은날 DST fold의 동일 시각은 양쪽 offset을 보존한다.
- SwiftUI에 해당 일정 grid control이 없어 좁은 custom timeline을 사용한다. 날짜선택/그리드는 별도 View 파일, 원본 조립은 State/VM이다. 기본320pt, AX최대520pt 내부 세로 스크롤이며 앱의 기존 semantic accent(분홍색)를 사용했다. 사용자 이미지의 파란색을 고정 복제하지 않는다. 짧은 일정의 최소44pt 높이는 시각 보정으로만 취급하며 실제 시간 문장/AX/안내는 유지한다.

## 이번 실행한 검증과 증거

- `bash apps/ios/tests/run_detail_presentations.sh` PASS ([tests.txt](tests.txt)): 한국어 요일/시간, 날짜/주소/URL, 첫·중간·마지막날 clip, 연도·자정 배타끝, 경계/인접날, 12초 구간, 미확인/혼합/invalid/충돌/역전/0길이, DST23/25/30분 전환 및100년 기간의 한날 계산, FSD78/negative fixtures.
- 현재 NoticeViewModelTests를 앱 fixture 하나로 재컴파일/실행 PASS ([viewmodel-tests.txt](viewmodel-tests.txt)); application URL 원문 일치 및 timeline 조립을 추가 확인했다. 전체 standalone/SwiftData/OS mapper suite는 이번에 재실행하지 않았다.
- 최종 Xcode26.6/iOS26.5 `build_sim` 2026-09-14 15:52:48Z 성공, warnings/errors 없음 ([build.txt](build.txt)). 실제 UI 실행 빌드는15:49:20Z이며 이후 변경은 DST fold의 기간문장 offset 보존뿐이다. UI fixture는 Asia/Seoul이므로 사진 내용은 동일하다.
- 전용4156/window3055에서 native 다음→이전으로8/27→8/28→8/27 선택, DatePicker에서 다음월→9/15 직접 선택, 종료일 다음 버튼 비활성화, 날짜별0시/9시 시작과 자동 스크롤 변경을 실제 화면으로 확인했다. picker의8/26이전·9/16이후 비활성화도 확인했다. 입력 provider 성공만으로 집계하지 않고 window PNG로 판정했다.
- 실제 내부/외부 스크롤, 행사14~16시 블록, light/dark, AX5의 기간/도메인/열기 버튼/날짜 선택/전체 일정명 표시를 확인했다. 큰글자에서 gutter가 과도하게 넓고 첫 눈금 글자가 잘리던 문제를 수정하고 AX5와 최종 light를 다시 확인했다.

| 증거 | 관찰 |
| --- | --- |
| [first-day-light](first-day-light.png) | 최종 레이아웃;8/27 날짜·링크·9시 시작 자동 스크롤 |
| [middle-day-light](middle-day-light.png) | 다음 날짜8/28,0시부터 연속 블록으로 바뀜 |
| [last-day-dark](last-day-dark.png) | DatePicker로9/15선택,0~13시 문장·다음 날짜 비활성화 |
| [activity-light](activity-light.png) | 단일날 행사14~16시 시간선과 블록의 끝 |
| [range-link-ax5-dark](range-link-ax5-dark.png) | 마지막 실행 빌드 AX5 문장과 도메인 줄바꿈/열기 |
| [timeline-ax5-dark](timeline-ax5-dark.png) | 마지막 실행 빌드 AX5 날짜·전체 일정명·시간문장·줄바꿈 gutter |

middle/last/activity 사진은 마지막 AX gutter 폭·눈금 정렬 보완 직전 같은 기능의 검증이며 first/AX 두 장은 보완 이후다. 총6장으로 변경된 상세만 기록했다. Before는 이전 [calendar-detail](../calendar-detail/README.md)이고 사용자 제공 두 파일은 외부 입력 참조로 확인했으며 저장소에 복제하지 않았다.

## 한계와 보존

AX tree가 배경/전환 전 화면을 돌려주고 일부 Orca synthetic 좌표 입력이 화면을 바꾸지 못했다. MCP touch와 실제 window 캡처로 날짜 이동/스크롤/선택을 교차 확인했다. 모든 중간크기/모든공고/VoiceOver 음성/Canvas 실제 렌더/물리기기는 미검증이며 짧은12초/DST는 독립값 테스트와 UI 정책 소스 검증이고 실제 해당 fixture를 Simulator로 재현한 증거는 없다. AX5 블록 내부 제목은 짧게 보일 수 있어 전체 이름을 위에 별도 표시하고 정확한 시간은 문장과 AX label로 보존한다. 원문/링크를 열거나 공유하지 않았으며 EventKit read/권한/저장, Maps 재실행, favorites 조작도 하지 않았다. A434/C38E 조작·초기화·종료 없음,4156은 light/large 상세 sheet로 남겼다. source/cache/models/card본문/calendar export/contracts 불변이다. 기기 busy time 기능(#10)은 코디네이터가 별도 후속으로 요청했으며 이번 구현에는 포함하지 않았다.
