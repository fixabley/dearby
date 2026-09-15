# iOS 세션 인계

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](../context/coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

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
