# Dearby 플랫폼별 작업 세션

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](../context/coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

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
새 제품 기능은 배정하지 않았다. 인계 요약: [API](api.md), [Android](android.md), [iOS](ios.md).
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

## 컨텍스트 압축 전 상세 인계

2026-09-14 이슈 등록 이후의 역할별 컨텍스트는 [컨텍스트 목차](../context/README.md)를 참고한다. #1 구조 리팩터링·#2 네이티브 UI 정비는 등록만 완료했고 구현은 아직 배정하지 않았다.

#1 구현 기준은 사용자 승인된 Seed v2로 갱신했다. [최신 이슈 본문](../context/issue-01-native-app-architecture.md)과 [Seed 발전 이력](../context/symposium-seed-evolution.md)을 참조한다. 이번 갱신에서는 새 구현 task를 배정하지 않았다.

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

2026-09-14 지도 연동 완료: root feat/activity-location-contract PR#6; iOS PR#5 head9103609/Android PR#4 b90adac, 세션retain. 검증과범위 docs/context/README.md 참조.

최신 완료: 신청·활동 캘린더 연결. 공통PR6 79f5a82 / iOSPR5 9a8cd02 / AndroidPR4 6e5ad55, 모두 draft 미병합. run_ee6bffef760e 양쪽 succeeded/retained 및 완료 ack. coordinator/pr-split 역할 문서 마지막 항목의 검증·한계 참조.

최신 완료: ActivityDetail·조직 ID cache-aside. 공통PR6 c71cac3 / iOSPR5 2a2b05f / AndroidPR4 b8838e2, 모두 draft 미병합. run_b6299acf926a 최종 후속까지 succeeded/retained·delivery ack·reclaimable0. coordinator/pr-split 마지막 완료 항목 참조.

최신 완료: iOS 순수 ActivityDetail 생성자 분리 PR5 94d5ab4, 상세/캐시/캘린더 검사·빌드 통과. run_6cee981daf56 succeeded/retained·ack 완료. coordinator/ios 최신 항목 참조.

최신 완료: Notice 명칭 통일. iOSPR5 eb6b25f/AndroidPR4 bfee6dc/설계PR3 d493fd1/공통PR6 c07b019. 코드경로 Entities/NoticeCatalog·widgets.noticecard 등으로 변경. run_94fec7f4f195 모두 succeeded/retained·ack완료. 역할문서 마지막항목 참조.

최신 완료: iOS SwiftData 3계층 캐시와 외부 mock fallback. PR5 7c4b857 / 공통PR6 049e9bb. run_0a9cd0651126 succeeded/retained/ack, 활성 작업 없음. 기존 세션 유지. coordinator 및 iOS 최신 역할 문서 참조.


## 2026-09-14 캘린더 메모 원본 URL 반영

공통 PR6 0da0284, Android PR4 40be6ff, iOS PR5 017d68e 원격 head 확인. 신청/활동 메모는 검증된 sourceURL만, 없으면 빈값. iOS event.url 신청/온라인 URL 유지, 양 플랫폼 날짜/제목/장소 유지. 코드·관련 회귀테스트 검토 및 diff check 완료. Android JVM30 실패0, Debug/계측 APK 컴파일/FSD 통과. iOS old/new CalendarDraftTests/FSD63/Simulator build 통과. 이번 실제 calendar UI/Save 및 SwiftData 전체검사 재실행 안함. 각 플랫폼 자기 역할문서 최신기록 참조. Run run_e87b226af3c1 Android ctx_4017d4c64bbc succeeded/retained, iOS ctx_a6d798fd887f succeeded/retained. 양쪽 완료보고 처리·ack 및 reclaimable 0 확인, 작업 완료. 기존 dirty/untracked 보존.

완료: iOS PR5 d2a0210769ee7d1314f09d241a04031dad11fb6a push. SaveOrganizationButton(saved, organizationName, onSave), 호출부 접근성ID 보존, 공고제목 미변경. root diff검토/FSD64 fixtures/diffcheck 통과, worker Simulator build13:15:06Z 통과. 전체 suite/실제 UI 미실행. run_7883296084ad ctx_dbf72a2a6877 succeeded/retained, delivery 처리 ack, reclaimable0.

완료: PR5 57026bc146bc0bc73c3e60bc39152ed5d04b5a4a push. Shared/UI/Buttons Primary(borderProminent)/Secondary(bordered), Widgets/NoticeCard/UI/NoticeCardSaveButton 조립, Feature 저장버튼 제거·로직 유지·제목미변경. root diff/FSD66 통과, worker Simulator build13:20:44Z 통과; 실제UI/전체suite 미실행. run_5cdf34efb48c/task_2a7766f3b4aa/ctx_90acb493e9ce succeeded/retained·ack·reclaimable0. 사용자가 도메인별 widget 그룹에 관해 질문하여 FSD slice groups 가능/그룹내 독립성 유지 설명, 추가 폴더재배치는 지시 없으므로 미실행. 공식근거 https://fsd.how/docs/reference/slices-segments/#slice-groups .

완료: PR5 e8880149851f0851ff4ae894594714ba282c90bb push. 8개Widget소스 byte-identical 이동 확인. FSD66/fixtures/rootdiffcheck 통과, worker 새경로 NoticeViewModelTests old/new 및 Simulator build13:28:05Z 통과. 실제UI/전체disk suite 미실행. run_cbcfbd136775/task_8bf23c7eaa15/ctx_72bf843e608c succeeded/retained·완료ack·reclaimable0. Widgets/Notice/NoticeCard와 Widgets/Organization/FavoriteOrganizationCard 동위 View/State/ViewModel 배치 완료.


## 2026-09-14 Android UI·Room 최종 검증 체크포인트
PR4 원격 head8572ecddd93e92260114b1b988b2595d9b02c700 확인. 기능커밋66e5f3c 공통버튼,66f88f1 도메인widget동위배치,996f88c 조직Room,0a7e6d4 공고codec/source,695a0ae 하트표시,8572ecd App Roomtransaction/async연결. Root XML직접집계 JVM39/계측35 실패오류skip0, FSD60/fixtures24 통과, build/room-final-build.log 빌드/Lint성공·Lint오류0경고12 worker문서확인. canonical root/iOS/Android SHA c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f 동일. 실제 uniqueDB 재개/외부0/rollback/손상보존, 기존UI30+DB5 확인. 실제Calendar Save/외부Maps내부화면 미검증. 스토어transaction임시L1후 UI memoryonlySession으로Main게시, Room2.8.5/KSP2.3.12; 실제API 없음. root 계약 PR6 d97543b push·본문갱신 완료. ctx15dee worker_done 및retain정리만 대기.

최종 완료: run_620743435f4b/task_cc58819a0e23/ctx_15dee1ad27be succeeded 및 retained. delivery_b91f5f7c7bc2 완료보고검증·ack, reclaimable0 확인. PR4 head8572ecd, PR6 d97543b. root최종검토메시지는 이미완료된dispatch여서 inactive반환(작업실패 아님); 신규followup없음. 다음사용자지시 대기.
