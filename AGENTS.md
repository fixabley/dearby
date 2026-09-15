# Dearby 협업 방식

사용자는 API·Android·iOS 작업을 별도 Orca 세션과 Git worktree로 나누고,
메인 세션에서 공통 규격과 진행 상황을 조율하도록 요청했다.

- 플랫폼 작업은 해당 Orca 담당 세션에 배정한다. 여러 플랫폼의 독립 작업은 Orca orchestration으로 병렬 배정할 수 있다.
- 새 에이전트를 만들기 전에 기존 담당 worktree와 세션을 확인한다. 현재 연결은 `docs/workstreams/README.md`를 참고하고 런타임 상태는 Orca CLI에서 다시 확인한다.
- Orca 감독을 내장 서브에이전트로 중복 실행하지 않는다. 감독 요청은 orchestration 스킬, worktree·세션 관리는 orca-cli 스킬을 따른다.
- API의 기본 소유 범위는 `apps/dearby-api/`, Android는 `apps/android/`, iOS는 `apps/ios/`다.
- `shared/contracts/`, 공통 추론 규칙, 루트 설정의 변경은 메인 세션에서 먼저 조율한다. 각 worker는 다른 플랫폼이나 다른 checkout에 직접 쓰지 않는다.
- 자기 worktree에서만 빌드·수정한다. 상대 경로의 기준을 확인하고 플랫폼별 checkout마다 빌드 결과와 로컬 설정을 관리한다.
- 작업 시작·구현·검증·차단·완료 시 Orca 카드의 짧은 comment와 자기 `docs/workstreams/<platform>.md`를 갱신한다. 세션 응답에도 변경 사항·검증·남은 작업을 한국어로 요약한다.
- 실행하지 않은 검증을 통과했다고 기록하지 않는다. 기존 기록과 이번 실행을 구별한다.
- 감독 중에는 live Dispatch preamble의 질문·완료 보고 절차를 따른다. 완료 후에는 다음 지시를 기다리며 사용자 요청에 따라 세션을 유지한다.
- 사용자가 플랫폼 세션에 직접 지시하면 해당 지시를 따른다. 다른 세션에서도 같은 변경을 동시에 시작하지 않도록 담당 범위를 확인한다.
- worktree 간 파일·대화는 자동 동기화되지 않는다. 공통 변경과 플랫폼 커밋의 통합은 메인 세션에서 검토 후 수행하며, 강제 푸시나 임의의 기존 변경 삭제를 하지 않는다.
- 전체 저장소 검색 시 로컬 하위 worktree인 `dearby-api/`, `dearby-android/`, `dearby-ios/`를 중복 검색하지 않는다.

## 컨텍스트 보존

사용자는 컨텍스트 압축 전마다 역할별 Markdown 인계를 남기도록 요청했다.

- 압축이 임박했음을 알 수 있거나 수동으로 컨텍스트를 정리하기 전에는 `docs/context/README.md`를 목차로 사용하여 관련 역할 문서를 갱신한다.
- 자동 압축 시점을 직접 제어하거나 사전 알림을 보장할 수 없으므로 주요 결정·범위 변경·작업 완료 시점에도 함께 갱신한다.
- 파일명은 coordinator, ios, android, api, product-planning 등 역할과 내용을 직관적으로 나타내고 기존 파일을 갱신하여 최신 재개 지점을 명확히 한다.
- 사용자 목적·승인 범위·결정 이유·완료/미완료 작업·관련 파일·실행한 검증과 한계·이슈/PR·다음 행동을 기록한다. 과거 결과를 새로 실행한 결과로 표시하지 않는다.
- Git/worktree/Orca 연결 정보는 확인 시점을 표시하고 재개 시 실시간 상태를 재확인한다. 토큰·비밀번호 등 비밀 정보는 기록하지 않는다.
- 플랫폼 담당자는 자기 checkout의 역할 문서와 `docs/workstreams/<platform>.md`를 갱신하고 메인은 공통 결정과 인계 목차를 조율한다. 다른 checkout에 임의로 쓰거나 자동 동기화된 것으로 가정하지 않는다.
- 압축 후에는 이 파일과 컨텍스트 목차, 담당 역할 문서를 먼저 읽고 완료된 일을 반복하거나 기존 목표를 잃지 않도록 한다.

## PR과 커밋 분리

사용자는 PR 내부 커밋을 작업 단계가 아닌 기능·컴포넌트 단위로 작게 나누길 원한다.

- 예: 공고 카드 컴포넌트 분리, 즐겨찾기 조직 카드/행 컴포넌트 분리, 공고 상세 컴포넌트 분리.
- 책임 분리 → 상태 변경 → 테스트 → 문서 같은 단계별 커밋을 기본 방식으로 삼지 않는다.
- 한 기능·컴포넌트 변경에 필요한 코드·테스트·문서는 같은 커밋에 묶는다. 커밋마다 의도가 하나여야 하며 가능한 한 빌드·검증 가능한 상태를 유지한다.
- 기능 간 공통 의존성 변경은 필요한 경우 별도 선행 커밋으로 두고 해당 필요성을 설명한다. 논리적으로 연결된 변경을 억지로 나누지 않는다.
- 이 요청은 앞으로의 작업 방식이다. 이미 게시한 PR의 커밋 이력을 임의로 재작성하거나 강제 푸시하지 않는다.

## 네이티브 FSD 구조

사용자는 iOS·Android 컴포넌트를 FSD 기반으로 분리하도록 요청했다. 현재 공통 기준은 docs/architecture/native-apps.md, 실제 플랫폼 트리와 노출 진입점은 각 앱 ARCHITECTURE.md를 따른다. 화면/위젯/사용자행동/도메인/범용 UI 경계를 구분하고 상향 의존·같은 레이어의 다른 슬라이스 직접 참조를 피한다. 화면 간 조립은 App에서 수행한다. 단일 네이티브 모듈의 폴더 경계를 컴파일러가 완전히 강제한다고 주장하지 않고 구조검사 및 리뷰로 보완한다.


## Swift 코드 검사

SwiftLint는 일반 Swift 스타일, Harmonize/SwiftSyntax는 FSD 경계·폴더 규칙을 검사한다. `apps/ios/.swiftlint.yml`을 정본으로 사용하며, `scripts/setup_swiftlint.sh`의 고정 버전·체크섬으로 설치 후 `tests/run_swiftlint.sh`를 실행한다(경로는 apps/ios 기준). baseline이나 광범위 disable로 위반을 숨기지 않고 필요한 한 줄 예외는 이유를 기록한다. 코드 수정 후 관련 회귀와 기존 구조 검사를 유지한다.

## 도메인 Model과 화면 State

사용자가 정한 최신 규칙은 공고 NoticeModel과 독립 OrganizationModel, 화면별 ViewModel/State 조립이다. 이전 Notice/NoticeDetail 이중 엔티티 및 상세 생성자 조립 제안보다 우선한다.

- NoticeModel은 공고 정보와 조직 ID/역할만 보유하며 OrganizationModel·조직 이름·경로를 보유하거나 조회하지 않는다.
- NoticeRepository와 OrganizationRepository의 원본 저장·cache-aside는 별개이며 App이 인스턴스를 공유하고 snapshot 교체 시 캐시를 무효화한다. UI에서 전역 저장소에 직접 접근하지 않는다.
- NoticeCardViewModel은 두 모델과 공유 즐겨찾기 상태를 조합해 NoticeCardState를 제공한다. 상세도 NoticeDetailViewModel→NoticeDetailState로 같은 규칙을 따른다.
- 화면별 표시 타입은 State 접미사, 원본 도메인 타입은 Model 접미사를 사용한다. View는 State와 콜백으로 표시하며 작은 하위 View에 불필요한 ViewModel을 만들지 않는다.
- 즐겨찾기 원본 상태는 하나이고 State.saved는 이를 반영한다. ViewModel마다 복사한 즐겨찾기 목록을 독립적으로 변경하지 않는다.
- entities/notice와 entities/organization은 서로 직접 참조하지 않고 상위 ViewModel에서 조합한다. Feature가 화면 State에 상향 의존하지 않도록 지도·캘린더에 필요한 모델 값은 App에서 전달한다.


## iOS 3계층 캐시

사용자가 SwiftData를 인메모리 캐시 다음 계층으로, 그 다음 외부 저장소(mock-data, 향후 API)를 요청했다. 공고·조직 각각 L1 → L2 → 외부 source 순서로 조회하고, 외부 성공 결과를 명시적으로 영속 저장한 뒤 L1에 반영한다. 저장/조회 오류와 missing을 구분한다. App이 SwiftData 수명과 snapshot 버전/캐시 무효화·Session 교체를 조율하며, domain Model·State·순수 UI 경계를 유지한다. 현재 mock은 동기 번들 기반이며 실제 API 연결의 비동기/취소 정책은 별도 구현 범위다.

## 네이티브 Widget 배치와 Harmonize 개편

2026-09-16 사용자는 FSD 공식 문서 비교 후 Harmonize 규칙 작성과 실제 리팩터링·회귀 검증을 요청했다. 상세 목표는 docs/architecture/fsd-domain-rules-draft.md다. iOS는 widgets/<slice>/ui|model, pages/<slice>/ui|model 및 app 목적별 세그먼트를 사용한다. 2026-09-16 추가 결정으로 소스 폴더는 소문자로 시작하는 lowerCamelCase(noticeCard, addToCalendar)이며 약어는 ui/api/lib로 소문자 표기한다. Swift 타입·파일 이름과 Xcode 프로젝트/asset 규격 이름은 유지한다. Entity 순수 UI는 자기 도메인 값과 콜백을 받으며, Widget/Page 연결 UI는 자기 ViewModel과 Feature 진입점을 사용할 수 있다. 최신 승인인 직접 하위 두 레이어 제한을 적용하며 app/providers의 조립만 예외다. 같은 레이어 다른 슬라이스 직접 참조는 금지한다. 이 결정은 이전 Widget 안의 UI/Model 폴더 금지보다 우선한다. Android는 이번 iOS 작업에 포함하지 않으며 현재 widgets/<domain>/<widget>/ 동위 배치를 유지한다. 구현/검사 완료 여부는 각 플랫폼 인계와 ARCHITECTURE.md를 확인한다.

## Android 3계층 캐시
사용자는 iOS와 동등한 Android 영속캐시도 승인했다. Room 기반 L1→L2→외부mock 조회와 명시적 저장승격, snapshot transaction 무효화, 실패시 데이터보존, off-main I/O와 취소후 stale publish 방지를 적용한다. Shared UI는 플랫폼 네이티브 디자인버튼, Widgets는 도메인별 화면조합을 담당한다.

## 워크트리 정리 이후 최신 상태 (2026-09-14)

#1의 PR #3/#4/#5/#6을 main에 통합하고 기존 플랫폼 worktree·세션을 제거했다. 이전 terminal handle과 retained 상태는 재사용하지 않는다. 다음 플랫폼 구현은 최신 main에서 이슈 전용 역할별 worktree·Orca 세션을 새로 구성한다. #2와 #10은 PR7/8/9/11/12로 main 통합했고 2026-09-15 완료된 플랫폼 세션과 worktree도 정리했다. 후속 #13/#14/#15는 미착수다. 현재 세션 배정은 docs/context/orca-sessions-and-worktrees.md를 따른다. 재개 시 docs/context/README.md와 coordinator-current-task-and-decisions.md를 먼저 읽는다. 백업 위치는 coordinator-architecture-merged-and-worktrees-cleaned.md에 있다.

## 컨텍스트 문서 유지 방식

현재 상태는 담당 역할 문서에서 교체·갱신하고 동일한 작업 로그를 여러 파일에 복제하지 않는다. 진행·완료·미착수를 구분하며 상세 이력은 docs/context/archive/날짜별-폴더에 보존한다. docs/workstreams는 역할별 context로 연결되는 안내로 유지한다. 과거 스냅샷의 세션 ID·Draft·미병합 상태를 현재 지시로 해석하지 않는다.

## 직접 하위 두 레이어 제한 (2026-09-16 최신 승인)

App→Pages/Widgets, Pages→Widgets/Features, Widgets→Features/Entities, Features→Entities/Shared, Entities→Shared만 직접 참조한다. 동일 슬라이스 내부는 허용하고 형제 슬라이스·상향 참조 금지는 유지한다. app/providers의 의존성 생성·공유 수명 관리·주입만 모든 하위 레이어 조립을 허용한다. app/routes와 entrypoint는 예외가 아니다. providers에 기능 UI를 몰아넣거나 타입 별칭·단순 전달 래퍼로 제한을 우회하지 않는다. ContentView는 시작 상태와 루트 화면 연결만 맡고 저장소 초기화·재시도, 화면별 지도/캘린더 행동, 설정 표시를 책임에 따라 분리한다. 현재 iOS에 적용했으며 Android 적용 완료를 뜻하지 않는다.
