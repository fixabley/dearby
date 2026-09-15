# 카드 일정·장소 줄 컴포넌트 — 2026-09-15

시작/종료 원본을 각각 period 배열 원소로 만들고 VStack의 별도 Text로 표시한다. 날짜-only의 시간 미확인, 누락 경계, 역순 기간의 원문/확인 필요, timezone 의미를 유지한다. 최종 기간 문장을 split하거나 newline으로 바꾸지 않는다.

장소 State.fields는 venue.name/address, 온라인 설명/host, 신청 위치별 원본 경계를 유지한다. NoticeCardPlaceText는 공백만 단어 경계로 사용하고 SwiftUI Layout 측정으로 줄을 배치한다. 지명 추론/하드코딩 없음; 전체 열보다 긴 하나의 토큰만 내부 줄바꿈한다. 접근성 크기는 일정명을 위로 배치해 내용 폭을 확보하며 SF Symbols/44pt 지도 버튼/phase·venue index callback 및 기존 카드 ScrollView는 유지한다.

## 이번 검증

- run_standalone.sh의 NoticeViewModelTests 컴파일 명령만 추출해 Swift 6 컴파일, 앱 및 shared sample 각각 실행 PASS: tests.txt. 시작/종료 배열, date-only/누락/역순/시간대, 원본 장소 필드·URL host·정확한 venue index 검사. 전체 suite/지도 외부 실행은 반복하지 않았다.
- 최종 FSD 102 Swift 파일 및 guard fixtures PASS, git diff --check PASS.
- XcodeBuildMCP 최종 build_run_sim 성공: 07:56:08Z 시작, PID 51021, 경고/오류 없음. build.txt 참조. 앞선 설치는 Simulator 서비스 Mach -308, AX 캡처 중에도 기기 Shutting Down 발생; 데이터 삭제 없이 재실행 후 최종 화면 확인 성공.
- 지정 A617D464-41FC-4C33-A3AC-A109D5C9F054, iOS 26.5에서 직접 캡처·열람: first.png의 시작/종료 별도 줄 및 `충북대학교 중앙도서관 2관` / `세미나실(5층)`, place.png의 두 번째 카드 단어 줄배치, multiple-missing.png의 date-only·종료 누락·온라인 URL 미확인.
- AX5 최종 ax5-period.png: 행사명 위 배치 및 별개 시작/종료 Text. ax5-place.png: 충북대학교/중앙도서관/2관/세미나실/(5층) 순으로 읽히고 건물번호와 단어가 임의 음절로 끊기지 않는다. 세미나실(5층)은 열보다 길어 fallback 줄바꿈했다. 카드 내부 swipe로 장소까지 도달했다.
- 일반 화면은 AX 헤더 보완 전 캡처이며 일반 HStack 배치는 동일하다. 실제 VoiceOver 낭독/실기기/전체 지도·캘린더 회귀는 미실행. 글자 크기는 large로 복원하고 앱은 실행 유지한다.

구현 참고: [Apple SwiftUI Layout](https://developer.apple.com/documentation/swiftui/layout). 기존 run-20260915 실행 기록은 별도 미커밋 상태로 보존한다. 코드·관련 State 검사·이 증거 및 현재 역할 문서를 한 기능 커밋으로 전달하며 push/PR 없음.
