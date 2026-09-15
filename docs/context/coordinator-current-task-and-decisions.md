# 현재 작업과 결정

2026-09-15: 현재 작업은 Swift 아키텍처 테스트 적용이다. 이전 UI/캘린더 작업 정리는 완료됐다.

- 사용자 승인: Harmonize + Swift Testing으로 지원되는 구조 규칙을 검사하고 기존 Python FSD 참조 검사를 유지한다. 프로덕션 모듈 분리나 UI 변경은 하지 않는다.
- iOS 담당 worktree: dearby-ios-architecture-tests. root는 ci/ios-architecture-tests에서 .github/workflows/ios-architecture.yml 및 공통 검사 운영 문서를 담당한다.
- Run run_172be1f40d2f / Task task_b014b64c1f3f / Dispatch ctx_c8cf3ee2e5b6 / terminal term_63e15d3b-5a6c-4075-aca7-500e2300490c, runtime b2a34e5f-8da0-4f91-83e6-b081d2c899e2. 재개 시 실시간 확인.
- main branch protection 적용 완료: iOS architecture required, strict=true, enforce_admins=true, force push/deletion 금지. workflow는 아직 미병합/미실행이므로 현재 검사 완료 상태가 아니다.
- worker는 apps/ios와 자기 역할 인계만 commit/push하고 PR 생성하지 않는다. root가 worker branch를 통합해 구현+CI 단일 PR을 만들고 실제 CI를 검증한다. Xcode16.4/Swift6.1/macOS15 CI 기준에 맞춰 Harmonize1.2.1/SwiftSyntax601.0.1 고정. 실제 CI 검증 대기.

이전 완료 기록:

2026-09-15 17:04 KST: 기간 시작·종료를 별도 컴포넌트로 나누고 장소를 필드·단어 단위로 줄배치하는 후속 개선까지 로컬 main 통합 완료. iOS b712ca3, Android 848b90e를 검토 후 merge commit으로 통합했다. 두 앱 빌드·실행 및 관련 검증 완료; 세션은 transcript 보존 후 release하고 checkout·기기는 유지한다.

## 현재 카드 일정 표시 결정

- 최신: 시작 `…부터`, 종료 `…까지`를 원본 경계에서 만든 별도 State/Text 컴포넌트로 세로 배치한다. 합쳐진 기간 문자열의 split/개행으로 대체하지 않는다. 장소는 이름/주소/온라인·host의 필드 경계를 우선 분리하고 단어·건물번호·괄호의 맥락이 끊기지 않게 공백 경계 줄배치를 적용한다. 하드코딩/임의 지명 추론은 피한다.
- 왼쪽 일정명, 가는 세로 구분선, 오른쪽 캘린더 아이콘+`시작부터 종료까지` / 위치 아이콘+`위치(URL·주소 등)` 구조. 신청 기간과 각 schedule을 표시한다.
- 추가 요청: 카드 웹 URL은 상세보기처럼 도메인(host)만 표시한다. 원본 URL은 보존하고 주소/자유텍스트를 URL로 추정하지 않는다.
- 사용자 정정(16:20): 이모지가 아니라 플랫폼 네이티브 아이콘이다. SF Symbols/Android vector 아이콘을 사용하고 좌표가 유효한 장소에만 접근성 이름·터치 영역을 갖는 지도 아이콘 버튼을 제공한다. 기존 App 지도 scheme 어댑터로 연결한다.
- 일정 phase에 맞는 장소만 연결한다. 신청 위치는 application 제출 장소/URL이며 행사 장소를 신청 장소로 추정하지 않는다.
- 날짜만 있거나 한쪽 기간이 없을 때 시각/종료를 생성하지 않는다. 긴 문자열·다중 일정·큰 글자에서도 모든 일정에 접근 가능하게 한다.
- Model→ViewModel→State 및 콜백 경계를 유지한다. 공통 계약/API/상세 화면의 별도 변경은 범위 밖이다.
- 플랫폼별 카드 일정 기능과 사용자 추가 요청(도메인·아이콘)을 각 기능 커밋으로 묶어 검증/통합했다. 사용자가 후속으로 PR 정리·push를 요청했다. feat/native-card-schedules 브랜치로 push하고 양쪽 플랫폼의 기능별 커밋을 보존한 [PR #17](https://github.com/fixabley/dearby/pull/17)을 게시했다. 게시 당시 OPEN이었다. 이번 원격 확인에서 PR17이 origin/main b0c76a1에 병합된 것을 확인했고 현재 아키텍처 작업에 통합했다. 당시 로컬 검증 기록은 PR에 연결되어 있다.

- #2 native Shared/UI: PR7 공통 원칙, PR8 iOS, PR9 Android 병합 완료.
- #10 캘린더 바쁜 시간: PR11 Android, PR12 iOS 병합 완료. merge commit으로 기능별 커밋 보존.
- 남은 검증·개선: #13 실제 OS 캘린더, #14 접근성/큰 글자 제스처·짧은 블록, #15 iOS 빈 결과 진단. 아직 구현하지 않음.
- 기존 코드 검증은 플랫폼 문서 기록을 따른다. 이번 정리에서는 PR 범위/diff check, head의 main 포함과 백업을 확인했다. 새로운 전체 앱 테스트 실행으로 주장하지 않는다.
- 완료된 iOS·Android Orca 세션은 transcript를 보존하고 종료, worktree 제거. 다음 구현은 main에서 이슈별로 만들며 역할 분리를 유지한다.

현재 정본: docs/architecture/native-apps.md, native-design-system.md 및 각 앱 ARCHITECTURE.md. 과거 논의는 archive/2026-09-15-before-pr-cleanup/에 보존했다.
