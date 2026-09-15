# Dearby 플랫폼별 작업 세션

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

2026-09-14 구성. 기준 커밋 `79ac281`에서 생성한 독립 Git worktree와 Orca Codex 세션이다.
Orca의 `main` 카드 아래 하위 worktree로 표시된다. 에이전트 대화, 변경 파일, 상태 comment는
각 Orca 세션에서 확인할 수 있다. 현재 메인 대화가 감독·통합을 담당한다.

| Orca 카드 | 브랜치 | checkout | 담당 |
| --- | --- | --- | --- |
| Dearby API | `fixabley/dearby-api` | `dearby-api/` | `apps/dearby-api/` |
| Dearby Android | `fixabley/dearby-android` | `dearby-android/` | `apps/android/` |
| Dearby iOS | `fixabley/dearby-ios` | `dearby-ios/` | `apps/ios/` |

checkout 경로는 메인 `/Users/jominjun/Documents/dearby` 기준이다. 각 checkout은
전체 모노레포의 독립 복사본이며, 앱 디렉터리나 submodule이 아니다.

## 작업사항을 보는 곳

- Orca 카드 comment: 현재 작업과 다음 단계 한 줄.
- 각 세션의 대화: 배정 내용, 진행 설명, 완료 요약.
- 각 checkout의 `docs/workstreams/api.md`, `android.md`, `ios.md`: 담당별 인계 문서.
- 공통 기획: `docs/product/iteration-01.md`, `activity-data-v1.md`, `interest-target-rules.md`.

초기 인계는 세 작업 모두 완료 보고를 검토했고, Orca에서 세 세션을 retained 상태로 유지했다.
새 제품 기능은 배정하지 않았다. 인계 요약: [API](api-implementation-and-handoff.md), [Android](android-implementation-and-handoff.md), [iOS](ios-implementation-and-handoff.md).
메인에 모은 인계 문서는 초기 시점의 복사본이며, 이후 각 담당 checkout의 상태 문서와 대화가 최신 기록이다.

## 후속 작업 절차

1. 메인 세션에서 범위와 공통 규격을 정한다.
2. 기존 담당 세션에 명시적인 Task를 배정한다. 한 checkout에는 동시에 하나의 편집 작업만 둔다.
3. 작업자는 자기 코드와 상태 문서를 갱신하고, 검사 결과 및 차단 사유를 보고한다.
4. 메인에서 변경·테스트·공통 규격의 일치를 검토해 통합한다.

작업자는 자기 worktree의 `git status`를 기준으로 커밋한다. 메인에서 하위 worktree 폴더를
`git add`하지 않는다. 아직 통합되지 않은 다른 worktree 변경은 자동으로 보이지 않으므로
공통 명세 변경이 필요하면 먼저 coordinator에게 요청한다.

## Orca 연결 정보

- Repository ID: `c80e1d88-9400-4765-8bc5-4bbdffe399c3`
- 초기 인계 Run: `run_8d4cf654c096`
- API 초기 Dispatch: `ctx_86e2146eb289`
- Android 초기 Dispatch: `ctx_4e374ec7ed60`
- iOS 초기 Dispatch: `ctx_68fbe5c534c1`

이 ID는 최초 인계 기록이며, 후속 작업에 완료된 Dispatch ID를 재사용하지 않는다.
터미널 handle은 런타임에 따라 바뀔 수 있으므로 저장된 handle을 맹신하지 않는다.
현재 상태는 `orca worktree list --json`과 Run을 지정한 `orca orchestration worker-list`로 확인한다.
명령 실행 전에 설치된 버전의 `orca skills get orchestration` 안내를 따른다.

감독은 작업이 진행되는 메인 세션에서 수행한다. 이 문서는 상시 백그라운드 감시나
대화의 자동 미러링을 설정하지 않는다. 사용자는 담당 Orca 세션에 직접 후속 지시를 할 수도 있다.


## 추가 런타임 기록 — 재확인 필요

orca 실행 파일을 사용했다. ORCA_CLI_COMMAND와 ORCA_DEV_REPO_ROOT는 당시 비어 있었다. 당시 runtime ready v1.4.201, runtime ID 2758203a-9fac-417f-8e4e-8034c6b2e69d.

Coordinator: term_bc7eff15-d763-403b-a1ef-433a57b11f00
API: term_db2f8998-7c62-4510-832c-3d175f17cbcb / task_f1e88c5631c0
Android: term_8bbb1e33-2874-45e5-851b-35f2a8768ad8 / task_686a4e3e7125
iOS: term_b163fb41-0240-441e-a130-6d6fe90f6130 / task_0e0e1d478e76

초기 세 작업은 worker_done succeeded를 검토하고 worker-retain을 실행했다. 모든 delivery ack, reclaimable 0을 확인했다. 세 카드는 todo / 인계 완료·다음 지시 대기 comment다. 새 구조 이슈는 아직 할당하지 않았다. 위 값은 저장 시점의 새 조회 결과가 아닌 이전 작업 기록이다.

다음 배정 전 orca-cli와 orchestration 스킬을 읽고 orca skills get orchestration의 현재 안내를 따른다. 기존 터미널과 worktree를 조회하고 새로운 task/dispatch로 기존 세션을 재사용한다. 완료된 dispatch를 다시 쓰거나 worker_done을 coordinator가 대신 보내지 않는다. 기존 터미널을 지정하는 worker-start에는 agent 옵션을 무작정 함께 주지 않는다. CLI 도움말을 우선한다.

감독 중 메시지는 check의 delivery 묶음 단위로 처리하고 질문에 응답·완료 검증·retain/reuse/release 판단 후 ack한다. idle이나 timeout만으로 실패 처리하지 않는다. 사용자가 계속 쓸 세션 유지 요청을 했으므로 완료 후 retain했다. 내장 collaboration 에이전트로 Orca 감독을 중복하지 않는다.

기존 AGENTS.md, workstreams README와 세 역할 초기 보고서를 각 child checkout에도 복사했다. 이번 docs/context는 메인 checkout에 새로 저장했으며 child에 자동 복제되지 않는다. 각 세션은 메인의 절대 경로 /Users/jominjun/Documents/dearby/docs/context/README.md를 읽어 최신 인계를 확인할 수 있다. 이번 요청 때문에 새 dispatch를 만들거나 세션에 메시지를 전송하지 않았다.

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

## 2026-09-14 iOS summary·Repository 완료
PR #5 https://github.com/fixabley/dearby/pull/5 에 522cef76a2b1384502a9d3ef50c73057411ec186 (카드 summary) 및 a6d7b98d906ba1f98f19f0151905c7b39cec0fe6 (즐겨찾기 업무 연산/Repository) push 완료. draft 유지, merge 없음.
ActivityNoticeSummary(notice, organization, String contextNames)를 catalog.summary(for:)로 생성, position/saved/compact/events 별도. FavoriteOrganizations가 injected FavoriteOrganizationsRepository와 단일 ids를 소유하며 saveOrganization(for:in:) -> SaveOrganizationResult.saved(organization)/unresolved. 화면은 결과 안내/햅틱만 담당. 기존 Provider/Storage 계약은 Repository로 이름 명확화; 별도 중복 Service/UseCase 없음. 저장키/배열/기존ID 보존. App은 루트 Observation 읽기 유지.
Worker 검증: 독립 Swift6 summary/유효·nil·unknown 대상/중복저장/무효 무쓰기·무알림/관찰/실제 UserDefaults 복원/카탈로그 검사 PASS. Xcode Simulator build_sim/build_run_sim PASS. 전용 Dearby-Issue1-iOS 저장 안내/더블클릭 반복/탭 반영/삭제 후 발견 반영 PASS. 실제 터치스와이프·물리햅틱·전체실기기 미검증; 기존 DB 보존. 증거 child apps/ios/build/refinement-regression 및 ARCHITECTURE.md. Main 직접 전체16Swift 경계+fixtures 및 diff --check PASS, 실제 코드/테스트/PR본문·remote head 검토.
Run run_120606aecc15 task_6837cbbea205 dispatch ctx_6c15718d7940 succeeded 보고 검토, 기존 세션 retain, delivery_17ccbd9c64aa ack, reclaimable 0. 활성작업 없음. Android/API 변경 없음. Root 미커밋 및 child untracked project.xcworkspace/AGENTS/context/workstreams 보존.

## 2026-09-14 내부 UI 컴포넌트 파일 분리 완료
iOS PR #5 head 54b669991a75a0f60b6245fa5cda3c44d61c430f, 카드 분리 08b5b1f53121b76249b29730c1e46d722620939e 및 상세 분리 54b6699 두 기능 커밋 push. Widgets/ActivityCard/UI/NoticeFact.swift, Pages/NoticeDetail/UI/{NoticeIdentityView,NoticeDetailField,NoticeIdentityFact}.swift 개별 파일. 순수 표시·간격·폰트·콜백 그대로. 각 slice 내부 helper internal, 공유 레이어 승격 없음. Preview repository 등 UI 아닌 함수는 유지.
iOS 전체 원본 UI8파일 및 Swift 선언 감사. 기존 독립Swift6 테스트/전체20Swift 경계+fixture/각 component Simulator build PASS. Main 구조검사와 diff check 직접 PASS 및 코드검토. 이번 무상태 추출은 UI 런타임 재검사 안함.
Android 전체 생산 Kotlin17/UIComposable10개 감사, 이미 각각 독립파일이며 local/private 추가UI 없음. 코드/PR#4 head26c517e 변경 없음. 구조검사17+fixtures11 PASS, 앱 무변경으로 빌드/계측 재실행 없음.
Run run_c07087395cbe: iOS task_6c4d317a225e/ctx_01b2e9893ff0, Android task_36ddc48e3048/ctx_e1915b754340 모두 succeeded 보고 검토 및 retain, completion deliveries ack, reclaimable0. 각 Orca 카드/role/workstream 기록. API 제외, untracked/개인기기 유지, merge없음.

## 2026-09-14 모델 단순화·지도 연결 최종 완료
공통 PR#6 https://github.com/fixabley/dearby/pull/6 head a157e42 (feat/activity-location-contract, root현재branch). 선택coordinates/coordinateEvidence 확장+공식건물좌표 3공고+공통JSONmetadata보존+양root리소스동기화+문서/15테스트.
iOS PR#5 72e48a57f73fd4fb67c095f2c1c349d1bd93a5ab 단순래퍼제거/장소모델 → e0b16b20634ef1fa9fe2e85b5bfe416778709e6d 좌표/MapURL/Appdestination/error표현 → 910360901513013b3594d9d9c32eadf667448cda 온라인유효좌표UI제외수정. ActivityNotice audience/eligibility/application:String benefits/qualityIssues[String], location typed summary/mode/status/venues. ActivityCoordinates finite/range checked; invalidoptional decode dropscoords retainsvenue. App puremapURL+launcher+NoticeDetailDestination errorsheetsafe. Native button each ownUIfile. Apple Maps 실제pin/label 확인 (건물대표점) 및 DBfavorites/nav복귀 PASS. Failure launcher주입테스트 PASS, 미설치OS거절실기기UI 미검증. 마지막온라인guard변경 purefilter standalone/FSD28/buildPASS, runtime 재실행불필요.
Android PR#4 b90adacaea9ee4a94f3e27a48c38da45be038613. String필드기존유지, typedlocation, coordsparse, onlinebuttonsuppress, App geo ACTION_VIEW unpinned; missinghandler/security nativeToast. FSD24+selftest11 JVM7/계측20 Debug/Linterror0advisory11 PASS. 실제Mapsrending/chooser미검증, handlerexists 확인+Intentcapture/error tests. 전용5556종료, user5554유지.
Main: rootnpm15/samplecheckPASS; childFSD28/24+fixtures직접PASS; AndroidXML JVM7계측20failure0확인; canonical3fileshash407b0c5e...일치. merge-tree 공통branch와각앱branch 무충돌 (실제merge안함). PRhead확인.
Run run_5feb189ab803 originaliOS task_212033abaf14/ctx_415579c0d7ba 완료→즉시수정task_2ccf48a09197/ctx_8cf87f51a1ee 재사용, 최종succeededretain. Android task_6842c653abbe/ctx_7c78d6143a66 succeededretain. 모든completionack/reclaimable0. API/기존untracked보존. 역할문서최신, noforce/merge. 다음 새요청은 새dispatch로기존세션재사용.


## 2026-09-14 신청·활동 캘린더 완료 (최신)
사용자 요청: 신청기간은 신청 URL, 활동기간은 해당 단계의 장소와 연결하고 온라인이면 접속 URL 또는 온라인 표시. 두 앱에 네이티브 편집기 추가를 구현했으며 사용자가 직접 저장/취소한다. 직접 이벤트 저장·권한 요청·초대·알림은 추가하지 않았다.
공통 PR #6 head79f5a82c6c62776fab718d5bd86f0cad09034e72, iOS PR #5 head9a8cd0272d1dfc657a127d90a2dae719bc701f94, Android PR #4 head6e5ad55420fe53cef305b81ad3db105ad470d43c. 모두 원격 확인 및 draft/미병합. 각 앱 신청/활동 두 기능 커밋으로 테스트·문서를 함께 묶었다. main에 병합하지 않았다.
공통 JSON SHA256 c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f가 root/iOS/Android 리소스에 일치한다. application.url, phase.onlineUrl/endsOn/timezone optional 규격. CIEAT 공개 신청 페이지 3건 확인, 이메일 신청 공고는 URL null. 종료 날짜는 inclusive, OS 종일 종료는 exclusive, 24:00은 다음날 00:00. 날짜 누락/모순/역전은 추측하지 않는다. 활동 장소는 phase 정확 일치, 온라인은 오프라인 장소를 섞지 않는다.
검증: root 공통17 테스트 및 sample sync 검사 통과. iOS 39파일 FSD/독립 날짜·상태·지도 검사와 Simulator 빌드 통과. 전용 Simulator 실제 신청/활동 편집기 기간·URL·장소 및 취소 복귀 확인, root가 calendar-regression의 두 editor PNG 확인. 종일/온라인 OS 화면·계정 없는 상황·실기기는 미검증. Android 35파일 FSD/self-test13, JVM17·계측28 실패0, Debug/Lint 오류0(권고12). 전용5556에 캘린더 handler가 없어 실제 외부 편집기 open/cancel 미검증; Intent 전달/오류/상세 유지 검증으로 한계를 기록. 실제 이벤트 저장 없음, 사용자 기기 보존.
Orca run_ee6bffef760e: iOS task_89dc548a4542/ctx_6e2ea9205c28, Android task_ea27305ed676/ctx_b77e3de3a94c 모두 succeeded/retained. 완료 delivery_11ad3bc86932 전부 검토·ack, reclaimable0 확인. 기존 담당 세션 유지, API 변경 없음. 기존 untracked/미커밋 변경 보존. 다음은 PR 검토 또는 후속 요청이며 이미 완료한 구현을 다시 시작하지 않는다.


## 2026-09-14 ActivityDetail 및 조직 캐시 최종 완료
사용자 승인 상세 모델을 두 앱에 적용했다. 화면은 ActivityDetail과 콜백만 받으며 카탈로그/원시 공고/저장소를 조회하지 않는다. 제목/aiDescription(기존 검토 summary라는 provenance)/대상/조건/신청방법·날짜/단계별 일정·장소/혜택·확인사항/원문 URL/출처/fieldPath를 가진 근거가 상세에 있다. 조직은 원본 공고에서 선택 ID만 저장하고 원본 조직(id/name/parentId)은 별도 인메모리 source에 저장한다. 관련 경로/기관이름은 저장하지 않는 조회 결과다. 말단은 선택 경로 끝이며 전역 leaf 강제 아님.
OrganizationRepository는 독립 빈 cache → source 조회 → 성공만 cache, missing 미캐시 및 부모 cycle-safe. App이 snapshot lifetime을 소유해 상세 재진입/재구성에도 공유한다. replaceSnapshot/source는 모든 캐시를 무효화한다. 영구저장/TTL/네트워크/live refresh UI는 추가 안 함. 현재 UI 갱신 기능은 없고 미래 refresh는 App 상태 연결 필요. 기존 favorites/map/calendar/화면 유지, canonical c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f 그대로 root/양app 확인.
공통 docs/product/activity-data-v1.md 문서 commit c71cac33f586ee53313efb742bf10bf4e970fc21 PR6. Android PR4: 1034366 조직 캐시 + b8838e2f2bd2165cffb64c858e0e28ce36b5c866 상세 조회. iOS PR5: 157d2024b7f790ae03e4eb0ab1511913302efa16 조직 캐시 + f6d28b7b878f33f57d8baa20d32f2515e956b5bf 상세 조회 + 2a2b05ff2a02efdde9683d08145ba84a141571fc 기관 링크 이름 보완. 모두 원격확인/draft/미병합; user기존미커밋/untracked보존.
검증: Android 구조41/self-test16, JVM24·전용5556계측30 실패0, Debug/Lint 오류0 권고12. main 실제 XML/로그 확인, 구조 직접통과. iOS source/cache 호출수·공유 경로/두 상세재진입·missing/cycle/rename-reparent invalidation·원문 근거/unknownsource·해결/미해결 기관이름 및 old/new JSON 상세/캘린더 검사, 기존 상태·지도 검사 및 Simulator build 통과. 최종 링크보완 빌드 11:24:30Z, main FSD45+fixtures 직접통과. 외부 Maps/캘린더 런타임은 이번 재실행하지 않음, 이전 검증을 새 결과로 주장하지 않음.
Orca run_b6299acf926a: Android task_1d72f23112c7/ctx_0710ea32dce0 succeeded/retained, completion delivery_67e85faf78c6 ack. iOS 최초 task_6ab832b92fc2/ctx_61bc02e98694 succeeded 뒤 누락된 링크 이름 보완을 즉시 같은 terminal 새 task_de204010b115/ctx_83dc09fa205d로 재사용하고 이전 delivery_a08d8a073ed0 ack. 보완 실제코드/테스트검토 후 succeeded/retain 및 delivery_1f74439b67d1 ack. reclaimable0 확인, 활성작업 없음. 기존 앱 세션 유지/API미변경. 다음은 PR 검토/새 사용자 요청이며 완료된 작업을 재실행하지 않는다.


## 2026-09-14 iOS ActivityDetail 생성자 완료
ActivityDetail.init(notice:organizationPath:contexts:organizationLinks:sources:)로 필드 매핑/근거URL/대표원문URL/단계별장소 조립 이동. Repository는 조회·관련 source 선택·캐시 책임 후 생성자 호출. 생성자에 Catalog/Repository/IO 없음, 기존 표시/참조/캐시/지도/캘린더 유지. iOS만 수정, Android/API/shared 변경 없음. PR5 94d5ab4cbb8be7be1db72e9cce3638631e154181 원격확인/draft미병합, 4파일 한 component commit(코드/테스트/문서).
검증: 저장소 없이 값만 주입한 직접생성에서 source순서/대표ID미해결·근거URL·온라인/단계별복수장소·필드보존, old/new JSON 상세/캘린더 및 cache standalone 통과. Simulator build11:31:42Z 통과; main diff/test review 및 FSD45+fixtures 직접통과. 순수조립 이동이라 runtime재실행 없음. 기존untracked/기기데이터 보존.
run_6cee981daf56 task_c8e76aa33fe7/ctx_60b181268fd4 succeeded/retained, delivery_db03b50238fb ack, reclaimable0. 활성작업없음. 기존 iOSterminal 유지, 다음사용자요청대기.


## 2026-09-14 Notice 네이밍 최종 완료
공고 domain: ActivityNotice→Notice, ActivityDetail→NoticeDetail, ActivityCatalog→NoticeCatalog, ActivityNoticeSummary→NoticeSummary, 관련 Activity접두 모델/저장소/helper 및 Entities/NoticeCatalog/Android entities.noticecatalog. ActivityCard→NoticeCard, Widgets/NoticeCard/widgets.noticecard. iOS 순수 생성자+저장소/cache동작 보존, Android 구조동작동일. Android MainActivity/ComponentActivity/ActivityNotFoundException 등 플랫폼, 기존 JSON activities/resourceactivity-samples.json/sharedcontracts경로/IDs/testtags/과거증거파일명은 호환예외로 유지. 이전 domain별칭없음.
PR5 iOS 도메인670bd8be201a222fd6f6e83ca9a2490987948963 / 카드eb6b25fac7b983a17e5a7c99a025ca5a30d67912. PR4 Android 도메인f2799c9 / 카드bfee6dc1d345d9b2de56b3d3775ce80e0819e67a. Root PR6 product c07b0191de4cc61395abf9e1e60704feb0b33d9e, PR3 architecture d493fd1120a01ada0dfd09ad74e6685879ed08ea. 원격 모두확인/draft/미병합. root docsbranch잠깐수정후 feat/activity-location-contract복귀, 기존미커밋변경보존.
검증: iOS 상세old/new·조직·즐겨찾기·지도·캘린더old/new standalone PASS, 최종FSD45+fixtures 및 Simulator build11:37:31Z PASS. Android JVM24fail0, FSD41/self16, Debug/계측APK컴파일/Lint오류0권고12 PASS. 지정rename과 Kotlin60파일일치/비코틀린자원불변 검사. Main구조검사/rg이전domain참조감사/AndroidXML및로그/원격head/샘플hash직접확인. 샘플SHA c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f root/양앱동일. 이름만변경이므로기기계측/외부지도캘린더이번재실행없음.
run_94fec7f4f195 iOS task_86eb10324b73/ctx_ae0df4320b17 succeededretain completiondelivery_1f3f1653a199ack, Android task_d1b7b6178550/ctx_99a1185ce5e5 succeededretain delivery_b086a7f5a689ack, reclaimable0. 세션유지/API변경없음/활성작업없음. 다음은새사용자요청. 코드검색재개시 Notice경로를 사용한다.
