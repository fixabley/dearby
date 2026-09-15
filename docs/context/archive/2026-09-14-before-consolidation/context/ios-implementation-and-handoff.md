# 최신 조율 상황 — 2026-09-14

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

GitHub #1 구조·상태 관리 리팩터링과 #2 네이티브 UI 정비를 생성했다. #2는 #1 이후다. 현재는 이슈 등록과 컨텍스트 저장까지이며 구현은 아직 배정하지 않았다. #1은 기존 화면·동작 유지, 기능과 책임의 플랫폼 간 정합성, 플랫폼에 맞는 상태 관리와 저장 책임 분리를 요구한다.

아래는 기존 플랫폼 담당 세션의 초기 인계 원문이다. 링크의 상대 경로는 docs/workstreams와 같은 깊이여서 이 폴더에서도 유지된다. 원문의 임시 작업 제한과 검증 시점을 최신 구현 결과로 오해하지 않는다.

---

# iOS 세션 인계

2026-09-14 기준. **초기 인계 문서 작성 완료, 다음 지시 대기 중**이다. 새 기능 구현은 요청되지 않았다.
API·Android·iOS는 독립 worktree와 Orca 세션에서 개발하고 coordinator가 진행과 공통 변경을 조율한다.

## 작업 위치와 담당 범위

| 항목 | 현재 값 |
| --- | --- |
| Worktree | `/Users/jominjun/Documents/dearby/dearby-ios` |
| 브랜치 | `fixabley/dearby-ios` |
| 기준 커밋 / 확인한 HEAD | `main` 기준 `79ac2816e60154d4f48d1ad59c8e1f593d8ee9bc` |
| 플랫폼 담당 | `apps/ios/` — SwiftUI, iOS 26.0 이상, Swift 6 |
| 이번 수정 허용 범위 | `docs/workstreams/ios.md` 하나 |
| iOS 세션 | `term_b163fb41-0240-441e-a130-6d6fe90f6130` |
| Coordinator 세션 | `term_bc7eff15-d763-403b-a1ef-433a57b11f00` |

공통 배경은 Kotlin Compose Android(API 31 이상), SwiftUI iOS(26 이상), NestJS API 모노레포다.
이번에는 앱 소스·공통 명세·루트 문서·다른 플랫폼을 수정하지 않으며, 설치·빌드·시뮬레이터 조작·커밋·푸시를 수행하지 않는다.
자신의 worktree 밖에 파일을 쓰거나 추가 에이전트를 생성하지 않는다.

## 구현 현황과 주요 파일

| 경로 | 현재 역할 |
| --- | --- |
| [DearbyApp.swift](../../apps/ios/Dearby/DearbyApp.swift) | 앱 진입점 |
| [ContentView.swift](../../apps/ios/Dearby/ContentView.swift) | 하단 SwiftUI `TabView`의 발견·즐겨찾기, 세로 카드 넘김, 더블탭·버튼 저장, 상세·삭제·빈 상태·로딩 실패 안내 |
| [NoticeIdentityView.swift](../../apps/ios/Dearby/NoticeIdentityView.swift) | 상세의 관심 조직·실제 상위 조직·활동 분류·역할별 행사 관련 기관·회차 표시 |
| [ActivityCatalog.swift](../../apps/ios/Dearby/ActivityCatalog.swift) | 번들 JSON 디코딩, 표시 대상 피드, 조직 경로·분류·학교 맥락 처리 |
| [FavoriteOrganizations.swift](../../apps/ios/Dearby/FavoriteOrganizations.swift) | 조직 ID 집합과 UserDefaults 저장·삭제, 키 `dearby.favoriteOrganizationIDs.v1` |
| [activity-samples.json](../../apps/ios/Dearby/Resources/activity-samples.json) | 공통 샘플을 복사한 앱 리소스 |
| [FavoritesStoreTests.swift](../../apps/ios/tests/FavoritesStoreTests.swift) | Xcode 테스트 타깃과 별개인 독립 Swift 저장·디코딩 검증 |
| [iOS README](../../apps/ios/README.md) | 프로젝트 설정, 실행·빌드·독립 검증 안내 |

검토된 공고 5건 중 학사 행정 1건을 제외한 4건을 피드로 표시한다. 더블탭과 저장 버튼은 조직 ID를 중복 없이 저장하며, 반복 저장으로 해제하지 않는다. 해제는 즐겨찾기 목록의 삭제 동작으로 한다.
즐겨찾기에는 상위 조직 경로와 연결된 샘플 공고 수·분류·학교를 표시한다. 기존에 저장한 대학일자리센터를 기업으로 강제 변환하지 않으며, 현재 연결 공고가 없는 조직도 목록에서 안내한다.
상세는 참여 대상·조건·신청 기간·활동 일정·장소·혜택·확인 필요 정보·원문 링크를 제공한다.

## 제품 의미와 공통 데이터 기준

- `parentOrganizationId`는 조직 상하위 관계, `categoryPath`는 활동 분류, `contexts`는 이번 행사의 학교 등 맥락, `favoriteOrganizationId`는 저장할 관심 대상이다. 학교 맥락을 기업의 하위 조직이나 참가 자격으로 해석하지 않는다.
- 한국농어촌공사와 DB손해보험 공고는 각각 `krc`, `db-insurance` 기업을 저장한다. 학교·운영부서는 자동 저장하지 않는다.
- 영남권 사이버 공격 방어 대회는 교육원 `yeongnam-ai-security` 아래 지속 대회 프로그램 `yeongnam-cyber-defense`를 저장한다. 제2회는 개별 공고의 `edition: 2`이며, 교육원을 함께 저장하지 않는다.
- 원문의 게시자·운영자·문의처 역할은 부모 관계나 관심 대상과 별개다. 관심 대상 승인 상태는 자격·모집 정보의 최신성을 보증하지 않는다.
- 현재 앱은 번들 샘플을 읽는다. 원문 추출은 수동이고, 승인 사례 기반 결정적 관심 대상 제안 도구는 공통 스크립트에 있다.

관련 기준 문서와 데이터:

- [활동 공고 규격·필드 사전·원문 검토](../product/activity-data-v1.md)
- [반복 01: 동작·검증·미구현 범위](../product/iteration-01.md)
- [관심 대상 선정·분류 규칙](../product/interest-target-rules.md)
- [공통 샘플](../../shared/contracts/activities/sample.json), [JSON Schema](../../shared/contracts/activities/schema.json), [승인 사례·대상 규칙](../../shared/contracts/activities/target-rules.json)
- [모노레포 운영](../monorepo.md)

## 실행·검증 명령 — 이번에는 실행하지 않음

다음은 기존 README의 재현 안내다. 후속 실행 지시가 있을 때 사용하며, 이번 인계의 검증 결과가 아니다.

`apps/ios/`에서 프로젝트를 열고 Xcode의 `Dearby` scheme 및 iOS 26 이상 시뮬레이터를 선택해 Run한다.

```sh
open Dearby.xcodeproj
xcodebuild \
  -project Dearby.xcodeproj \
  -scheme Dearby \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build/DerivedData \
  CODE_SIGNING_ALLOWED=NO \
  build
```

독립 Swift 검증은 저장소 루트에서 실행한다. 출력 위치를 현재 worktree 내부로 지정한 예시이며, 임시 UserDefaults suite를 사용하므로 앱 즐겨찾기는 변경하지 않는다.

```sh
mkdir -p apps/ios/build
swiftc -parse-as-library \
  apps/ios/Dearby/ActivityCatalog.swift \
  apps/ios/Dearby/FavoriteOrganizations.swift \
  apps/ios/tests/FavoritesStoreTests.swift \
  -o apps/ios/build/dearby-favorites-tests
apps/ios/build/dearby-favorites-tests shared/contracts/activities/sample.json
```

공통 데이터 검증 명령은 루트의 `npm test`, `npm run samples:check`, `npm run samples:infer`다. 의존성 설치나 샘플 동기화는 이번에 수행하지 않는다. `samples:sync`는 다른 플랫폼 리소스까지 쓰므로 iOS 세션에서 임의 실행하지 않고 coordinator와 조율한다.

## 기존 검증 기록과 이번 확인의 구분

| 구분 | 근거와 범위 |
| --- | --- |
| 기존 iOS 빌드·화면 기록 | `docs/product/iteration-01.md`와 iOS README에 Xcode 26.6 Simulator Debug 빌드 성공 및 iPhone 17 Pro 카드·즐겨찾기 실행 확인이 기록되어 있음 |
| 기존 독립 Swift 기록 | 실제 JSON 디코딩, 기업별 독립 저장·반복 저장 중복 방지·저장소 재생성 후 유지·삭제 통과 기록이 있음; 기업/학교 분리, 대회 상위 경로·회차 검증 추가도 기록되어 있음 |
| 기존 공통 검증 기록 | 제품 문서에 공통 데이터 테스트 13건 통과 기록이 있음; 이번에 재실행한 결과가 아님 |
| 이번 확인 | Git 경로·브랜치·HEAD 및 초기 clean 상태, 제품 문서·공통 JSON·규칙·iOS 소스·검증 소스·프로젝트 설정을 읽고 대조함 |
| 이번 문서 검토 | 새 문서의 diff와 공백 오류, 링크 대상, Git 변경 범위를 확인함; 수정 파일은 이 문서 하나 |

이번에는 테스트·빌드·앱 실행·시뮬레이터 조작을 하지 않았다. 기존 성공 기록을 재현하거나 최신 실행 결과로 인증한 것이 아니다.

## 알려진 한계와 대기 상태

원문 자동 추출·주기 수집, API 연결, 로그인·기기 간 동기화, 개인화 추천·자동 자격 판정, Q&A·커피챗·Dearby 크레딧은 미구현이다. 외부 CIEAT 마일리지는 Dearby 크레딧과 별개다.
전체 조직을 접고 펼치는 트리 탐색 및 운영자 검토 화면도 없다. iOS 제스처 자동 UI 테스트와 Xcode 테스트 타깃은 없으며, VoiceOver·최대 글자 크기·태블릿·가로 화면 전체 검증은 남아 있다. 외부 의존성은 없고 앱 아이콘 이미지는 출시 전에 필요하다.
현재는 고정된 검토 샘플이므로 실제 모집 현황으로 단정하지 않는다. 위 항목은 기존 한계의 기록이며, 이번에 새 기능 요청이나 실행 계획으로 전환하지 않는다.

**현재 상태: 인계 완료 후 coordinator 검토와 다음 명시적 지시를 기다린다.** 세션을 유지하며 후속 구현·검증을 임의로 시작하지 않는다.

## 공통 변경 요청 절차

공통 계약·샘플·대상 규칙·제품 문서·루트 문서 또는 다른 플랫폼 변경이 필요하면 iOS에서 직접 수정하지 않는다. 변경 이유, 관련 경로·필드, 현재/제안 동작, iOS 영향과 필요한 검증을 coordinator에게 Orca orchestration으로 전달한다.
Coordinator가 사용자 의도와 플랫폼 영향을 확인해 담당 세션·변경 범위를 배정한 뒤, 승인된 공통 변경과 리소스 동기화 기준을 받아 iOS 범위에 반영한다. 응답이 필요한 경우 현재 dispatch의 `orca orchestration ask`를 사용하며, 완료된 task/dispatch의 권한을 후속 작업에 재사용하지 않는다.
공통 명세나 다른 플랫폼을 우회 수정하지 않으며 커밋·푸시는 별도 지시를 따른다.

## 최신 상태 — Seed v2 확정 및 이슈 반영

사용자가 A·B·C와 D·E·F를 모두 채택하여 Seed v2를 확정했다. GitHub #1 본문을 갱신하고 원격 본문과 로컬 문서의 일치를 확인했다. 목표는 유지하며 재사용 UI의 저장소 접근 금지, 교체 가능한 데이터 공급 경계(네트워크 미구현), 즐겨찾기 상태 단일 소유, 새 외부 상태 관리·DI 라이브러리 및 빌드 모듈 분할 제외를 제약에 추가했다. 디렉터리 트리·파일 배치 예시 및 앱 실행 없는 임시 저장소 테스트를 완료 기준에 추가했다.

원본 Seed와 채택 이력은 .symposium/scratch/evolve-step.md, 정본 Seed는 .symposium/scratch/socrates.md. docs/context/symposium-seed-evolution.md에도 스냅샷을 저장했다. 이전 선택 대기 기록은 과거 이력이다. 구현 진행 요청은 유효하지만 이번 Seed 갱신 시점에는 코드 수정·Orca 구현 배정·커밋·푸시는 수행하지 않았다. 다음 구현은 갱신한 #1을 기준으로 기존 담당 세션에서 진행한다. #2 외형 변경은 이후다.

## 완료한 iOS 담당의 최종 보고

# iOS #1 Seed v2 — 구현·검증·draft PR 완료

2026-09-14 확인. 현재 상태는 coordinator 리뷰 및 다음 지시 대기이며 세션을 유지한다.

- Worktree: `/Users/jominjun/Documents/dearby/dearby-ios`
- 브랜치: `fixabley/dearby-ios`; 시작 기준 `79ac281`
- 커밋: `aa4a25845f9bde2ed2d3f46e9a1b0f547b477a34` (origin push 완료)
- Draft PR: https://github.com/fixabley/dearby/pull/5 (`main` 대상, apps/ios만 포함)
- 이슈: Related #1; 설계 참고 #3; 자동 merge 및 #1 종료 없음.
- 담당: apps/ios; API·Android·공통 규격·루트 설정을 수정하지 않았다.

## 목적과 완료 범위

승인된 Seed v2 A~F에 따라 기존 화면·문구·탭·저장 형식·조직 규칙을 유지하면서 App/Features/Shared 책임을 나눴다.
App에서 즐겨찾기를 단일 @State/Observation 인스턴스로 소유하고 화면은 표시 데이터·콜백을 받는다.
카탈로그 공급과 영속 저장은 protocol로 교체 가능하며 모델은 순수 조회만 담당한다.
공유 상태 읽기는 지연 탭 클로저 앞에서 수행하도록 실제 회귀 중 수정했다.
외부 라이브러리·빌드 모듈·네트워크·API 및 #2 외형 변경은 추가하지 않았다.
구조·상태 수명·의존 방향·새 기능 배치와 상세 결과는 `apps/ios/ARCHITECTURE.md`, 재현 명령은 `apps/ios/README.md`에 있다.

## 이번 실행한 검증

- Swift 6 standalone: 임시 인메모리 저장소 추가·중복·삭제, 두 Observation 소비자 일관성/변경 통지, 실제 UserDefaults 기존 배열 호환·복원, 모델 및 공급자 교체·오류 통과.
- Xcode 26.6 Simulator Debug 최종 빌드 성공 (`CODE_SIGNING_ALLOWED=NO`), warnings/errors 없음.
- 공통 `npm test` 13건, `python3 scripts/sync-activity-samples.py --check` 통과.
- 전용 Simulator에서 저장 버튼, DB 미저장 카드 더블클릭 저장·반복 저장, 두 기업 각 한 행, 연결 상세 기업/학교 분리, 삭제와 카드 반영 확인.
- 앱 종료/재실행 후 두 기업 유지, KRC 삭제 후 재실행하여 DB만 유지 확인.
- 접근성 scroll down으로 1/4 KRC→2/4 DB 이동 확인. 합성 터치 드래그는 전환 미확인으로, 실제 터치 스와이프 통과로 간주하지 않는다.
- 원래 한국어 UI 문자열 누락 없음, 문서 링크·git diff --check, 원격 PR의 앱 전용 파일 범위·draft/main/head 확인.

## 증거와 한계

전용 기기: `Dearby-Issue1-iOS`, `A434888F-4096-48AE-91B2-37A498233B55` (iOS 26.5, iPhone 17 Pro).
기존 사용자 기기 `C38E28BE-C541-4209-B59C-F1F134B5A3FE`는 변경하지 않았다.
로컬 증거: `apps/ios/build/regression/build-final.log`, `favorites-two-organizations.png`, `restored-after-relaunch.png`, `deletion-restored.png` (PR 제외).
MCP AX snapshot/elementRef가 실제 화면과 불일치해 Orca computer 창 조작과 screenshot으로 보완했다.
터치 스와이프, VoiceOver 전체 흐름, 최대 글자, iPad/가로, 실기기 전체 검증은 남아 있다. 이번 회귀는 상시 XCUITest가 아니다.
이전 검증 기록과 이번 실행 결과는 구분하며, 새 기능 요청을 임의 추가하지 않는다.

## 후속 절차

Coordinator가 PR을 검토한다. 추가 지시 전 구현·merge를 시작하지 않는다.
공통 변경이 필요하면 이유·경로·iOS 영향·검증 범위를 coordinator에 Orca ask로 전달한다.
기존 untracked AGENTS.md와 다른 workstreams 문서는 보존했고 PR에 포함하지 않았다. 자기 workstreams/역할 컨텍스트는 로컬 인계 기록이다.

## FSD 최종 담당 보고

# iOS FSD 후속 구현 완료 — PR #5 리뷰 대기

2026-09-14 확인. 사용자 요청에 따라 기존 PR에 FSD를 실제 적용했으며 다음 지시를 기다린다.

- Worktree: `/Users/jominjun/Documents/dearby/dearby-ios`
- Branch: `fixabley/dearby-ios`, HEAD `4e9d9b2425371c08bb57f733749f7411ac7e8537`
- PR: https://github.com/fixabley/dearby/pull/5 (`main` 대상 draft, 앱 경로만 포함)
- Related #1, 설계 참고 #3. 기존 aa4a258 보존, force push·자동 merge 없음.

## 기능별 후속 커밋

```
316ea49 refactor(ios): 공고 카드를 FSD 위젯과 카탈로그 엔티티로 분리
6729972 refactor(ios): 즐겨찾기 조직 카드와 저장 행동 슬라이스 분리
4e9d9b2 refactor(ios): 상세 페이지 목적지와 시트 라우팅을 App에서 조립
```

각 커밋에 해당 컴포넌트 코드와 관련 검사/문서를 함께 포함했고 각각 Simulator 빌드를 확인했다.
API·Android·공통 계약·루트 설정을 수정하지 않았다. 초기 untracked AGENTS/다른 workstreams 문서는 보존·PR 제외했다.

## 최종 경계와 결정

App이 단일 @State/Observation 즐겨찾기를 소유하고 Pages에 ID 집합·콜백·generic 목적지 ViewBuilder를 주입한다.
Discovery/Favorites 페이지가 NoticeDetail 페이지를 직접 생성하지 않으며 sheet/NavigationLink/back 형태는 유지한다.
공고/조직 카드는 별도 Widget이며 저장소·공유 상태에 접근하지 않는다. Favorites 행동은 Features/FavoriteOrganization, 연결된 모델/공급/분류는 Entities/ActivityCatalog 단일 slice다.
루트 favorites.ids 읽기는 lazy Tab 클로저 앞에 유지했다. 카드 요약과 상세 identity는 같은 파일 private helper다.
실제 범용 공용 Swift UI가 없어 빈 Shared 폴더를 만들지 않았으며 기존 Assets/Resources 경로는 유지한다.
공통 모델·키·포맷·문구·UI·제스처 코드는 보존하고 새 라이브러리·빌드모듈·네트워크·#2 외형 변경은 없다.
구조·진입점·상태 수명·의존 방향·새 기능 예시는 apps/ios/ARCHITECTURE.md와 README에 기록했다.

## 이번 실행 결과와 증거

- FSD lexical 구조 검사: 14 Swift 파일 전체 및 7종 금지 참조 음성 fixture 통과.
- 독립 Swift 6: 인메모리 추가/중복/삭제, 두 관찰 소비자의 추가·삭제 통지/동일 값, 기존 UserDefaults 배열 호환·복원, 카탈로그 공급 교체·손상 오류 통과.
- 각 컴포넌트 Simulator Debug 빌드 통과, 최종 Xcode 26.6 / iOS 26.5 실행 성공.
- 실제 발견 상세 sheet 열기/취소 복귀, KRC 더블클릭 저장, 즐겨찾기 DB 상세/뒤로 가기, KRC 삭제→발견 카드 갱신, 앱 재실행 후 기존 DB만 유지 확인.
- 접근성 scroll down으로 카드 1→2 이동 확인. 실제 터치 스와이프는 새로 검증하지 않아 이전 한계를 그대로 유지한다.
- 한국어 UI 문자열 동일, git diff 공백 및 문서 링크, 원격 PR title/body/head/main/draft/앱 전용 범위 확인.

전용 simulator: A434888F-4096-48AE-91B2-37A498233B55 (Dearby-Issue1-iOS), 사용자 기기 변경 없음.
증거: apps/ios/build/fsd-regression/{build.log,boundaries.log,state-tests.log,discovery-sheet.png,favorites-detail.png,restored-favorites.png} (PR 제외).
이전 npm 공통 13건 및 샘플 검증 기록은 이번 FSD 실행과 구분하며 재실행했다고 주장하지 않는다.

## 한계와 다음 행동

단일 Swift 모듈은 폴더로 slice를 컴파일러 강제하지 않는다. lexical guard는 추론된 타입/보간/동적 참조/매크로를 놓칠 수 있으며 문서 계약·리뷰·빌드로 보완한다.
터치 스와이프·VoiceOver 전체·최대 글자·iPad/가로·실기기 검증은 남아 있고 상시 XCUITest를 추가하지 않았다.
Coordinator가 PR #5를 검토한다. 세션을 유지하고 후속 작업을 임의로 시작하지 않는다. 공통 변경이 필요하면 현재 유효 dispatch의 Orca ask로 먼저 조율한다.

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

iOS 신청 ab7c845, 활동9a8cd02. 실제 증거 dearby-ios/apps/ios/build/calendar-regression. 담당 worktree 역할 문서가 상세 원본이다.


## 2026-09-14 ActivityDetail 완료
iOS 완료 PR5 2a2b05f. 상세 source/evidence/관련 기관 이름 + 인메모리 source/cache. 구체 테스트/한계는 dearby-ios/apps/ios/ARCHITECTURE.md와 해당 checkout 역할 문서. 기관 이름 후속 누락까지 보완했고 최종45파일/FSD와 관련빌드·독립검사 통과. ctx_83dc09fa205d retained.


## 2026-09-14 ActivityDetail 생성자 리팩터링 진행
사용자는 iOS detail(id:) 긴 조립을 ActivityDetail 생성자로 옮기는 제안을 승인했다. iOS만 대상. Repository는 notice/조직 경로·맥락·링크/관련 source 조회와 cache 책임, init은 조회된 값으로 필드·근거URL·단계별장소·원문URL 조립. Catalog/Repository/IO 의존 생성자 금지. 원문URL은 notice.sourceIds 첫 ID로 선택하여 source 배열 순서 바뀌어도 기존 의미 보존. 별도 Mapper/Factory 도입 없음. 기존 cache/favorites/UI/map/calendar 보존.
새 run_6cee981daf56 기존 iOS terminal 재사용, 관련 독립값생성자 테스트 및 기존 상세/캐시/캘린더·FSD·빌드 검증+한 component commit PR5 followup 담당. 아직 완료 대기. root공통/Android/API 변경 불필요.


## 2026-09-14 iOS ActivityDetail 생성자 완료
ActivityDetail.init(notice:organizationPath:contexts:organizationLinks:sources:)로 필드 매핑/근거URL/대표원문URL/단계별장소 조립 이동. Repository는 조회·관련 source 선택·캐시 책임 후 생성자 호출. 생성자에 Catalog/Repository/IO 없음, 기존 표시/참조/캐시/지도/캘린더 유지. iOS만 수정, Android/API/shared 변경 없음. PR5 94d5ab4cbb8be7be1db72e9cce3638631e154181 원격확인/draft미병합, 4파일 한 component commit(코드/테스트/문서).
검증: 저장소 없이 값만 주입한 직접생성에서 source순서/대표ID미해결·근거URL·온라인/단계별복수장소·필드보존, old/new JSON 상세/캘린더 및 cache standalone 통과. Simulator build11:31:42Z 통과; main diff/test review 및 FSD45+fixtures 직접통과. 순수조립 이동이라 runtime재실행 없음. 기존untracked/기기데이터 보존.
run_6cee981daf56 task_c8e76aa33fe7/ctx_60b181268fd4 succeeded/retained, delivery_db03b50238fb ack, reclaimable0. 활성작업없음. 기존 iOSterminal 유지, 다음사용자요청대기.


## 2026-09-14 Notice 네이밍 최종 완료
공고 domain: ActivityNotice→Notice, ActivityDetail→NoticeDetail, ActivityCatalog→NoticeCatalog, ActivityNoticeSummary→NoticeSummary, 관련 Activity접두 모델/저장소/helper 및 Entities/NoticeCatalog/Android entities.noticecatalog. ActivityCard→NoticeCard, Widgets/NoticeCard/widgets.noticecard. iOS 순수 생성자+저장소/cache동작 보존, Android 구조동작동일. Android MainActivity/ComponentActivity/ActivityNotFoundException 등 플랫폼, 기존 JSON activities/resourceactivity-samples.json/sharedcontracts경로/IDs/testtags/과거증거파일명은 호환예외로 유지. 이전 domain별칭없음.
PR5 iOS 도메인670bd8be201a222fd6f6e83ca9a2490987948963 / 카드eb6b25fac7b983a17e5a7c99a025ca5a30d67912. PR4 Android 도메인f2799c9 / 카드bfee6dc1d345d9b2de56b3d3775ce80e0819e67a. Root PR6 product c07b0191de4cc61395abf9e1e60704feb0b33d9e, PR3 architecture d493fd1120a01ada0dfd09ad74e6685879ed08ea. 원격 모두확인/draft/미병합. root docsbranch잠깐수정후 feat/activity-location-contract복귀, 기존미커밋변경보존.
검증: iOS 상세old/new·조직·즐겨찾기·지도·캘린더old/new standalone PASS, 최종FSD45+fixtures 및 Simulator build11:37:31Z PASS. Android JVM24fail0, FSD41/self16, Debug/계측APK컴파일/Lint오류0권고12 PASS. 지정rename과 Kotlin60파일일치/비코틀린자원불변 검사. Main구조검사/rg이전domain참조감사/AndroidXML및로그/원격head/샘플hash직접확인. 샘플SHA c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f root/양앱동일. 이름만변경이므로기기계측/외부지도캘린더이번재실행없음.
run_94fec7f4f195 iOS task_86eb10324b73/ctx_ae0df4320b17 succeededretain completiondelivery_1f3f1653a199ack, Android task_d1b7b6178550/ctx_99a1185ce5e5 succeededretain delivery_b086a7f5a689ack, reclaimable0. 세션유지/API변경없음/활성작업없음. 다음은새사용자요청. 코드검색재개시 Notice경로를 사용한다.
