# 현재 작업과 결정

2026-09-15: 카드 일정 표시 개선 완료. iOS fdcfca3·2d59853, Android 9d033b8·8547adb를 검토 후 로컬 main에 merge commit으로 통합했다. 최종 요구는 네이티브 아이콘과 URL 도메인 표시다. 원격 push/PR은 하지 않았다.

## 현재 카드 일정 표시 결정

- 왼쪽 일정명, 가는 세로 구분선, 오른쪽 캘린더 아이콘+`시작부터 종료까지` / 위치 아이콘+`위치(URL·주소 등)` 구조. 신청 기간과 각 schedule을 표시한다.
- 추가 요청: 카드 웹 URL은 상세보기처럼 도메인(host)만 표시한다. 원본 URL은 보존하고 주소/자유텍스트를 URL로 추정하지 않는다.
- 사용자 정정(16:20): 이모지가 아니라 플랫폼 네이티브 아이콘이다. SF Symbols/Android vector 아이콘을 사용하고 좌표가 유효한 장소에만 접근성 이름·터치 영역을 갖는 지도 아이콘 버튼을 제공한다. 기존 App 지도 scheme 어댑터로 연결한다.
- 일정 phase에 맞는 장소만 연결한다. 신청 위치는 application 제출 장소/URL이며 행사 장소를 신청 장소로 추정하지 않는다.
- 날짜만 있거나 한쪽 기간이 없을 때 시각/종료를 생성하지 않는다. 긴 문자열·다중 일정·큰 글자에서도 모든 일정에 접근 가능하게 한다.
- Model→ViewModel→State 및 콜백 경계를 유지한다. 공통 계약/API/상세 화면의 별도 변경은 범위 밖이다.
- 플랫폼별 카드 일정 기능과 사용자 추가 요청(도메인·아이콘)을 각 기능 커밋으로 묶어 검증/통합했다. 원격 push·PR 생성 요청은 없음.


- #2 native Shared/UI: PR7 공통 원칙, PR8 iOS, PR9 Android 병합 완료.
- #10 캘린더 바쁜 시간: PR11 Android, PR12 iOS 병합 완료. merge commit으로 기능별 커밋 보존.
- 남은 검증·개선: #13 실제 OS 캘린더, #14 접근성/큰 글자 제스처·짧은 블록, #15 iOS 빈 결과 진단. 아직 구현하지 않음.
- 기존 코드 검증은 플랫폼 문서 기록을 따른다. 이번 정리에서는 PR 범위/diff check, head의 main 포함과 백업을 확인했다. 새로운 전체 앱 테스트 실행으로 주장하지 않는다.
- 완료된 iOS·Android Orca 세션은 transcript를 보존하고 종료, worktree 제거. 다음 구현은 main에서 이슈별로 만들며 역할 분리를 유지한다.

현재 정본: docs/architecture/native-apps.md, native-design-system.md 및 각 앱 ARCHITECTURE.md. 과거 논의는 archive/2026-09-15-before-pr-cleanup/에 보존했다.
