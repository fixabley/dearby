# 현재 작업과 결정

2026-09-16: **iOS Harmonize FSD 규칙·리팩터링·회귀 검증 완료, PR 사용자 리뷰 대기**. 사용자는 문서만이 아닌 규칙 구현→리팩터링→회귀 검증 반복을 요청했다. 사용자 피드백 전 main에 병합하지 않는다. Android/API 및 #13/#14/#15는 이번 범위 밖이다.

## 구현과 검토 단위

[공통 FSD 기준](../architecture/fsd-domain-rules-draft.md), [실제 iOS 구조](../../apps/ios/ARCHITECTURE.md), [검증 정본](../../apps/ios/docs/FSD-MIGRATION.md)을 따른다. 모든 하위 레이어 참조 허용, 동일 레이어 다른 슬라이스 직접 참조 금지, App 목적별 세그먼트, Entity 저장소, 순수 UI와 연결 UI 구분을 Harmonize/SwiftSyntax 검사로 구현했다. 임시 경로 매핑은 제거했고 재도입도 검사한다. 단일 모듈의 검사이며 컴파일러 수준의 완전한 타입 의존성 분석이라고 주장하지 않는다.

| PR | 범위 | base |
| --- | --- | --- |
| [#19](https://github.com/fixabley/dearby/pull/19) | 공통 FSD 검사·저장소 노출 경계 | main |
| [#20](https://github.com/fixabley/dearby/pull/20) | 공고 카드 | #19 브랜치 |
| [#21](https://github.com/fixabley/dearby/pull/21) | 즐겨찾기 조직 카드·공유 저장 상태 | #20 브랜치 |
| [#22](https://github.com/fixabley/dearby/pull/22) | 상세 캘린더·지도 Feature | #21 브랜치 |
| [#23](https://github.com/fixabley/dearby/pull/23) | App 조립·캘린더 연결 UI 경계·최종 검사 및 CI | #22 브랜치 |

Root 브랜치는 `refactor/ios-app-routing-fsd`. 기능별 커밋을 검토하여 순서대로 통합했고 각 단계에서 아키텍처 검사를 실행했다. 원격 main은 2026-09-16 00:56 KST 확인 당시 f8f648c(PR18 병합), 다섯 PR 모두 충돌 없음. 게시 이력 재작성·강제 push 없음.

## 검증과 환경

최종 제품 소스 a214cda(worker 57aba2a와 apps/ios 동일)의 hosted [run34990938224](https://github.com/fixabley/dearby/actions/runs/34990938224)는 Harmonize 9 tests/109파일, 전체 standalone/busy/detail 회귀 및 실제 Simulator 빌드 모두 성공했다. 구조·회귀 job은 Xcode16.4/Swift6.1.2, 앱 빌드는 Xcode26.6이다. CI에 세 회귀 스크립트와 앱 빌드를 추가했으며 기존 required context `iOS architecture`와 보호 정책은 유지했다. 최종 PR head 상태는 [PR23 checks](https://github.com/fixabley/dearby/pull/23/checks)를 확인한다.

로컬 Xcode가 26.6에서 27.0으로 외부 갱신되어 Swift exit69가 발생했으나, 사용자가 약관에 직접 동의했고 00:53 KST Swift6.4/Xcode27.0 정상 실행을 확인했다. 이후 root Harmonize 9 tests/109파일 및 담당 전체 회귀·앱 빌드가 통과했다. 전용 Simulator 글자 크기는 large로 복원했다. 라이선스 문제는 해결됐으며 추가 동의 요청이 필요하지 않다.

실제 임시 위반 네 종류(상향 참조, 형제 Widget VM, Entity 순수 UI 저장소, 비공개 Record)는 각각 exit1, 제거 후 exit0 및 원본 108파일 SHA256 동일을 확인했다. 이는 최종 캘린더 UI 경계 후속 파일 추가 전 Xcode26.6 검사다. 시점별 UI 증거와 검증 한계는 앱 검증 정본에 기록하며 과거 PR18 결과를 이번 결과로 사용하지 않는다.

## 역할과 다음 행동

iOS는 기존 checkout/terminal을 런타임 확인 후 새 Run `run_58f7d03f4deb` / Task `task_31d48a682566` / Dispatch `ctx_338f8e66f52b`로 배정했다. 실제 현재 수명은 [Orca 운영 문서](orca-sessions-and-worktrees.md)와 런타임을 확인한다. 플랫폼 담당은 자기 apps/ios·역할 인계, root는 공통 문서·CI·통합과 PR 검토를 맡는다.

최종 새 Xcode27 UI 증거와 iOS 인계 ada4634를 root 2a81bab으로 통합했다. 담당 성공 보고를 수신하고 01:04 KST 세션을 피드백용 retained로 전환했다. PR23 최종 문서·주석 commit 이후 CI 상태는 위 checks에서 확인한다. 사용자의 구체적 변경 제안이 오면 해당 기능 커밋과 동일한 구조·회귀 검증으로 반복한다. 자동 테스트 통과를 사용자의 디자인·설계 만족으로 대신 판단하지 않는다.

## 이전 완료 이력

2026-09-15: Swift 아키텍처 검사 구현과 CI 연결을 [PR18](https://github.com/fixabley/dearby/pull/18)로 관리한다. 로컬과 hosted Xcode16.4/Swift6.1.2에서 전체 검사를 통과했고, 사용자가 계속 진행하도록 요청해 최종 CI 확인 후 병합한다. 실제 병합 상태와 최신 검사 결과는 PR18이 정본이다.

- Harmonize1.2.1/SwiftSyntax601.0.1 고정 테스트 패키지와 기존 Python FSD 검사를 함께 실행한다. 앱 모듈/런타임/기존 guard는 변경하지 않았다.
- 실행: `bash apps/ios/tests/run_architecture.sh`. 로컬 Swift6.3.3에서 102파일/기존 fixtures 및 Swift Testing6테스트 통과. 두 임시 위반 exit1, 제거 후 /tmp에서 exit0, 원본 파일 해시 동일.
- main protection: `iOS architecture` required, strict=true, enforce_admins=true. 모든 PR/main push workflow는 macOS15/Xcode16.4를 선택한다. 원격 CI의 현재 결과는 PR18 Checks 및 실행 로그가 정본이다.
- origin https://github.com/fixabley/dearby.git를 fetch하여 PR17 포함 main b0c76a1을 root/worker에 통합했다. 충돌은 root 인계3개/worker 인계2개뿐이며 양측 기록을 보존했다. 제품 소스 충돌/변경 없음.
- iOS task task_b014b64c1f3f / dispatch ctx_c8cf3ee2e5b6 succeeded. 기능 commit74e6189를 root ci/ios-architecture-tests에 merge했다. 담당 세션은 사용자 협업 방침에 따라 retained, 새 지시 없이 추가 작업하지 않는다.
- API·Android 및 후속 #13/#14/#15는 이번 범위 밖. 근거·한계는 apps/ios/tests/ArchitectureTests/VERIFICATION.md와 README.md 참조.

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
