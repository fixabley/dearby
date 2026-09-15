# 기획 역할 — 제품 방향과 Symposium 결과

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

## 제품 방향

신규 조직·대내외 활동에 참여하고 싶은 사용자의 진입장벽을 줄인다. 사용자는 자격이 충분한지, 투자 시간만큼 관심 직군의 경험·정보를 얻을지 몰라 망설이는 단계에 있다. 기존 구성원과의 Q&A·대면 커피챗으로 살아있는 정보를 얻고 이어질 관계를 만든다. 향후 질문자 만족도에 따른 사후 크레딧 보상이 구상되어 있으나 정책은 미정이다.

찾는 피로를 줄이기 위해 하루 한 번 추천 카드를 제공한다는 제품 방향이다. 모집 홍보자료를 수집·간소화하고 참여 자격·관심사에 맞춰 추천한다. 세로로 스냅하며 카드를 넘기고 더블탭으로 관심을 표시한다. 자격 정보가 불확실하면 추가 정보 입력을 제안한다. 개인화·일일 갱신·자동 수집은 아직 구현하지 않았다.

관심 표시는 공고가 아닌 지속 조직·프로그램을 저장한다. SW마에스트로 13기 카드를 저장하면 SW마에스트로가 저장된다. 조직은 트리가 될 수 있고 하나의 조직에 연도·회차별 여러 공고가 연결된다.

## 애자일 진행 합의

기능을 하나씩 구현하면서 기획서를 발전시킨다. 구현 전에 목적·최소 동작·완료 기준을 정하고 구현 후 직접 써보며 확정 동작·이유·남은 질문을 기록한다. 첫 경험은 추천 카드 넘기기 → 더블탭 → 조직 즐겨찾기 목록이며 두 앱에 구현했다.

## 현재 GitHub 이슈

- [#1 iOS·Android 컴포넌트 구조 및 상태 관리 체계화](https://github.com/fixabley/dearby/issues/1)
- [#2 iOS·Android 네이티브 컴포넌트 중심 UI 정비](https://github.com/fixabley/dearby/issues/2)

#1은 기능별 디렉터리, 화면/재사용 UI/상태/로딩·저장 책임과 의존 방향을 정하고 기존 코드에 적용한다. 새 기능 추가 위치도 문서화한다. UI·동작과 기존 즐겨찾기를 보존한다. 특정 MVVM/MVI/TCA/Redux 라이브러리나 임의 파일 길이 제한은 아직 지정하지 않았다. 기본 컴포넌트 우선 원칙만 담는다.

#2는 #1 이후 실제 외형과 UI 컴포넌트를 정비한다. SwiftUI 기본 UI와 Compose Material 3를 우선하며 핵심 카드 제스처 등에 필요한 커스텀 구현은 이유를 남긴다. 전후 화면, 큰 글자, 다크 모드, 스크린 리더 주요 레이블과 터치 영역 검증을 본문에 포함했다. 실제 UI 감사·구현은 아직 하지 않았다.

## Symposium 상태

사용자가 Q00/Symposium 설치와 Socrates 사용을 요청했다. 설치된 스킬은 /Users/jominjun/.codex/skills/ 아래 socrates, wonder, reflect, refine, restate, evolve-step, ontology, interview-harness. 설치 당시 upstream 기준 600378…로 기록되어 있다(정확 SHA는 재확인 필요).

원본 인터뷰: .symposium/scratch/socrates.md. Cycle 1 제품 애자일 Seed는 Archived Seed로 보존하고 Cycle 2 앱 구조 리팩터링 Seed를 유일한 최상위 ## Seed로 확정했다. 과거 '확인 대기' 문구는 인터뷰 이력이며 마지막 사용자 승인 기록이 우선한다. 이 폴더에도 전체 스냅샷이 있다.

Socrates는 Wonder → Reflect → Refine → Restate를 통해 사용자 승인 goal/constraints/acceptance_criteria를 닫는다. 이번 사이클은 범위·플랫폼 일관성·기존 동작 유지·이슈 분리까지 승인받았으므로 같은 질문을 다시 시작하지 않는다. 기획의 남은 미구현 사항을 임의로 현재 작업으로 확장하지 않는다.

## 최신 상태 — Seed v2 확정 및 이슈 반영

사용자가 A·B·C와 D·E·F를 모두 채택하여 Seed v2를 확정했다. GitHub #1 본문을 갱신하고 원격 본문과 로컬 문서의 일치를 확인했다. 목표는 유지하며 재사용 UI의 저장소 접근 금지, 교체 가능한 데이터 공급 경계(네트워크 미구현), 즐겨찾기 상태 단일 소유, 새 외부 상태 관리·DI 라이브러리 및 빌드 모듈 분할 제외를 제약에 추가했다. 디렉터리 트리·파일 배치 예시 및 앱 실행 없는 임시 저장소 테스트를 완료 기준에 추가했다.

원본 Seed와 채택 이력은 .symposium/scratch/evolve-step.md, 정본 Seed는 .symposium/scratch/socrates.md. docs/context/symposium-seed-evolution.md에도 스냅샷을 저장했다. 이전 선택 대기 기록은 과거 이력이다. 구현 진행 요청은 유효하지만 이번 Seed 갱신 시점에는 코드 수정·Orca 구현 배정·커밋·푸시는 수행하지 않았다. 다음 구현은 갱신한 #1을 기준으로 기존 담당 세션에서 진행한다. #2 외형 변경은 이후다.

## 구현 착수 — 2026-09-14

사용자가 "시작해줘. 작업할 때 PR 쪼개주고!"로 구현과 PR 분리를 승인했다.
Run run_d3badbfbd573. iOS task_1ed24fa47609 / ctx_6ab501fa2fe3, Android task_0f8e9917dac7 / ctx_566d3833a549. 기존 담당 터미널과 worktree를 재사용했고 둘 다 turn_started를 확인했다. API는 배정하지 않았다.

메인은 docs/native-app-architecture 브랜치에서 공통 설계 docs/architecture/native-apps.md만 63b74d3으로 커밋·푸시했다. 기존 미커밋 AGENTS, context/workstreams, Symposium, README/.gitignore 변경은 보존했다. 공통 설계 PR, iOS PR, Android PR 세 개로 분리하며 앱은 기존 플랫폼 브랜치에서 main 대상 draft PR을 생성한다. 제품 코드 수정·검증·PR은 각 worker 소유다. 자동 병합하지 않는다.

기존 "구현 미착수" 기록은 이 시점 이후로 과거 상태다. 완료 보고를 기다리며 실제 변경·검증·PR 내용을 메인에서 검토하고 settled 세션을 retain한 후 보고한다.

## Issue #1 구현·PR 생성 완료 — 2026-09-14

사용자가 요청한 분리 PR 세 개를 생성했다. 모두 main 대상 draft이며 자동 병합하지 않았다.

- 공통 설계 #3: https://github.com/fixabley/dearby/pull/3 ; docs/native-app-architecture ; 63b74d3f34b4df3611aef83bfd198d2e953ef3a2
- Android #4: https://github.com/fixabley/dearby/pull/4 ; fixabley/dearby-android ; 3c8d2c5264352f0cde1cfc6fe93af5e1b998b74a
- iOS #5: https://github.com/fixabley/dearby/pull/5 ; fixabley/dearby-ios ; aa4a25845f9bde2ed2d3f46e9a1b0f547b477a34

메인에서 각 PR 파일 범위·핵심 상태/저장/UI 경계·테스트 소스·문서·증거를 검토했다. Android JVM 5건/계측 7건, Debug 빌드·Lint 오류 0(권고 11), 기존 저장 데이터 cold launch 두 번 유지가 확인됐다. iOS Swift 6 독립 상태/실제 저장 호환 검사·Xcode Simulator 빌드·공통 데이터 13건 및 샘플 일치가 통과했고 저장 버튼·DB 더블클릭/중복·목록·상세·삭제·재실행 유지가 전용 기기에서 확인됐다. 메인도 재실행 후 두 기업 목록 스크린샷을 확인했다.

iOS 카드 이동은 접근성 스크롤로 확인했으나 실제 터치 스와이프는 검증 미완료다. 접근성/대화면/실기기 전체 검증도 완료로 주장하지 않는다. 이 한계를 #1 및 PR #5에 명시했다. 기능·화면의 스타일 변경 #2는 착수하지 않았다. 실제 소스는 각 앱 브랜치에 있으며 main에 병합된 것으로 말하지 않는다.

Orca Run run_d3badbfbd573의 Android task_0f8e9917dac7 / ctx_566d3833a549와 iOS task_1ed24fa47609 / ctx_6ab501fa2fe3 모두 worker_done succeeded를 검토했다. 사용자 요청대로 두 세션 retain, 완료 delivery ack 처리했다. API 세션은 이전 retained 상태이며 이번 작업은 배정하지 않았다.

다음은 사용자 PR 검토·후속 지시다. 자동 merge하지 않았고 #1 이슈도 열려 있다. 기존 미커밋 AGENTS.md, docs/context·workstreams, Symposium, README/.gitignore 변경은 보존했다. 이번 PR에는 기존 인계 문서를 섞지 않았다. 현재 메인 checkout 브랜치는 docs/native-app-architecture다.

## FSD 구현 착수 — 2026-09-14

사용자 FSD 요청에 따라 기존 PR #3/#4/#5를 후속 커밋으로 갱신 중이다. 원래 커밋은 재작성하지 않는다. 기능별(공고 카드·즐겨찾기 카드·상세 라우팅)로 코드/관련 테스트/문서를 묶는 작은 커밋을 요청했다.

Run run_3a0308534b10, iOS task_8fa8cb6aa260 / ctx_eae04b03a183, Android task_e04d876caf70 / ctx_af7df449aba2. 기존터미널재사용, 두 turn_started 확인. 이전 완료dispatch들은종료된이력이다.

공통 PR #3에 ef34b61 FSD 설계커밋 push 완료. App/Pages/Widgets/Features/Entities/Shared, Entities/ActivityCatalog 단일응집slice(공고·조직·출처관계), Features/FavoriteOrganization 상태/저장, Widgets 카드두종류, App화면조립. 하위layer의존·동일layer다른slice참조금지, 문서화된publicAPI와구조검사. Swift/Kotlin 단일모듈의폴더는완전컴파일경계가아님을명시.

GitHub #1의 추가요청 및 정본Seed에FSD제약을반영했다. 이전v2승인이력은보존. 기존미커밋인계변경은보존하고플랫폼PR에는앱파일만포함. 사용자기기보존·전용기기검증. 현재완료결과는아직없다.

## FSD 전환 완료 — 2026-09-14

공통 PR #3은 ef34b61, Android PR #4는 26c517e19f86b7b563a57310e75e6e2b2ff0b1f7, iOS PR #5는 4e9d9b2425371c08bb57f733749f7411ac7e8537로 갱신했다. 모두 draft/main 대상/미병합이다.

추가된 기능별 커밋:
- Android: 484c7a5 카탈로그·즐겨찾기 공통경계 → 493fd6c 공고 카드 → da96a9c 즐겨찾기 조직 카드 → 26c517e 상세페이지/App라우팅.
- iOS: 316ea49 공고 카드·카탈로그 → 6729972 즐겨찾기 카드·행동 → 4e9d9b2 상세페이지/App라우팅.
기존 3c8d2c5/aa4a258 커밋을 재작성하지 않았고 각 변경에 관련 코드·검사·문서를 묶었다. GitHub PR 파일 범위는 Android 30개 모두 apps/android, iOS 22개 모두 apps/ios다.

실제 책임: Pages에 화면, Widgets에 공고 카드/즐겨찾기 조직 카드, Features/FavoriteOrganization에 상태·저장 행동/구현, Entities/ActivityCatalog에 연결된 공고·조직·출처 모델과 공급/UI, App에 주입·라우팅. Android Shared는 범용 UI/테마. iOS는 실제 범용 공용 Swift UI가 없어 빈 Shared를 만들지 않았다. 양 플랫폼 같은 레이어 다른 slice와 상향 참조를 피하고 native public API 진입점을 문서화했다.

메인 검토: iOS 전체 14 Swift 파일 lexical check와 음성 fixture 직접 실행 통과. Android 17 Kotlin 파일 구조검사와 11 fixture(금지8/허용3) 직접 실행 통과. 상태/컴포넌트/라우팅 코드와 public API 문서·링크·PR 범위 검토. Android 결과 XML에서 이번 JVM5/계측11 전부 실패0 확인. 양 플랫폼 빌드 및 상태·복원·변경된 상세라우팅 회귀는 worker 기록에 증거가 있다. iOS 실제터치스와이프 미검증은 지속되며 접근성스크롤 이동과 더블클릭/저장/삭제/재실행은 확인됐다. 구조검사는 언어전체를 해석하는 compiler 경계가 아니며 보간/추론/동적참조 누락가능성을 문서에 명시했다.

Run run_3a0308534b10의 iOS task_8fa8cb6aa260/ctx_eae04b03a183 및 Android task_e04d876caf70/ctx_af7df449aba2 모두 worker_done succeeded를 수신·검토했고 세션을 retain했다. 완료delivery ack 및 reclaimable0 확인. 이 Run은 활성 작업이 남아 있지 않으며 후속 작업은 새 task/dispatch로 기존 세션을 재사용한다. API 작업과 #2 외형전환은 미착수다.

기존 main checkout의 미커밋 AGENTS/Symposium/context/workstreams/README/.gitignore 변경은 보존했다. 사용자 지시에 따라 역할별 문서를 갱신했으며 다음은 PR 검토와 후속 요청이다. 자동merge·forcepush는 하지 않았다.
