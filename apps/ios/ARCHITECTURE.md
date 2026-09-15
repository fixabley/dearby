# iOS 모델·ViewModel·State 구조

[Related #1](https://github.com/fixabley/dearby/issues/1), [설계 #3](https://github.com/fixabley/dearby/pull/3), [별도 공통 계약 #6](https://github.com/fixabley/dearby/pull/6).
이 문서는 현재 단일 NoticeModel 구조를 설명하며, 이전 Notice/NoticeDetail 생성자와 NoticeCatalog entity 설계를 대체한다. 앱 모듈·Swift 6 Observation·iOS 26·synchronized 그룹은 유지한다.

## 실제 소스 트리

아래 경로는 Dearby/ 기준이다. Resources/activity-samples.json 및 Assets.xcassets는 기존 경로다.

```text
App/BundleSnapshot.swift
App/CalendarEditorDelegate.swift
App/CalendarEditorRequest.swift
App/CalendarEventEditor.swift
App/ContentView.swift
App/DearbyApp.swift
App/NoticeDestinationView.swift
App/NoticeDetailDestination.swift
App/NoticeSession.swift
App/SnapshotManifest.swift
App/SwiftDataSnapshotStore.swift
App/VenueMapLauncher.swift
App/VenueMapLink.swift
Entities/Notice/API/NoticeRecord.swift
Entities/Notice/API/NoticeRecordSource.swift
Entities/Notice/API/NoticeRepository.swift
Entities/Notice/API/NoticeStorageCodec.swift
Entities/Notice/API/SnapshotNoticeSource.swift
Entities/Notice/API/SwiftDataNoticeSource.swift
Entities/Notice/Model/NoticeApplication.swift
Entities/Notice/Model/NoticeContext.swift
Entities/Notice/Model/NoticeCoordinates.swift
Entities/Notice/Model/NoticeEvidence.swift
Entities/Notice/Model/NoticeLocation.swift
Entities/Notice/Model/NoticeModel.swift
Entities/Notice/Model/NoticePhase.swift
Entities/Notice/Model/NoticeSchedule.swift
Entities/Notice/Model/NoticeSource.swift
Entities/Notice/Model/NoticeVenue.swift
Entities/Notice/UI/NoticeClassificationView.swift
Entities/Organization/API/OrganizationRecord.swift
Entities/Organization/API/OrganizationRepository.swift
Entities/Organization/API/OrganizationSource.swift
Entities/Organization/API/SnapshotOrganizationSource.swift
Entities/Organization/API/SwiftDataOrganizationSource.swift
Entities/Organization/Model/OrganizationModel.swift
Features/AddToCalendar/Model/CalendarDatePolicy.swift
Features/AddToCalendar/Model/CalendarDraftMapper.swift
Features/AddToCalendar/Model/CalendarEventDraft.swift
Features/FavoriteOrganization/API/FavoriteOrganizationsRepository.swift
Features/FavoriteOrganization/API/UserDefaultsFavoriteOrganizationsRepository.swift
Features/FavoriteOrganization/Model/FavoriteOrganizations.swift
Features/FavoriteOrganization/Model/SaveOrganizationResult.swift
Pages/Discovery/UI/DiscoveryView.swift
Pages/Favorites/UI/FavoriteListView.swift
Pages/NoticeDetail/Model/NoticeDetailState.swift
Pages/NoticeDetail/Model/NoticeDetailViewModel.swift
Pages/NoticeDetail/UI/CalendarAddButton.swift
Pages/NoticeDetail/UI/NoticeApplicationView.swift
Pages/NoticeDetail/UI/NoticeDetailView.swift
Pages/NoticeDetail/UI/NoticeIdentityView.swift
Pages/NoticeDetail/UI/NoticeLocationView.swift
Pages/NoticeDetail/UI/NoticeScheduleView.swift
Pages/NoticeDetail/UI/VenueMapButton.swift
Widgets/Organization/FavoriteOrganizationCard/FavoriteOrganizationCardState.swift
Widgets/Organization/FavoriteOrganizationCard/FavoriteOrganizationCardViewModel.swift
Widgets/Organization/FavoriteOrganizationCard/FavoriteOrganizationCard.swift
Widgets/Notice/NoticeCard/NoticeCardState.swift
Widgets/Notice/NoticeCard/NoticeCardViewModel.swift
Widgets/Notice/NoticeCard/NoticeCard.swift
Widgets/Notice/NoticeCard/NoticeCardSaveButton.swift
Shared/UI/Buttons/PrimaryButton.swift
Shared/UI/Buttons/SecondaryButton.swift
Shared/UI/InformationRow.swift
Shared/UI/StatusMessage.swift
Shared/UI/NativeStyle.swift
Shared/UI/NativeComponentsPreview.swift
```

## 엔티티와 조회 경계

Entities/Notice의 **NoticeModel 하나**가 공고의 id/title/aiDescription/targetUser/participationCondition/applicationInformation, 조직 참조 ID/role/basis/note, 기간·장소·분류·혜택·확인 사항·출처·근거를 소유한다. summary/audience/eligibility/application은 decoder의 원본 JSON 키로만 남고 같은 의미의 이중 public API는 없다. aiDescription은 검토 샘플 요약이며 provenance는 reviewed_sample.summary로 새 AI 생성이라 표시하지 않는다. NoticeModel은 OrganizationModel·조직 이름·경로를 갖지 않는다. schedule 원본 기간과 schedules의 phase별 장소 값은 의미 있는 도메인 정보이며 온라인 단계에는 오프라인 장소를 넣지 않는다.

Entities/Organization의 OrganizationModel은 id/name/parentId이며 JSON parentOrganizationId에 대응한다. 두 entity는 상대 slice를 참조하지 않는다. NoticeDetail/NoticeSummary/NoticeCatalog 및 NoticeDetailRepository는 제거했고 호환 alias도 없다. App/BundleSnapshot은 기존 JSON을 NoticeModel·OrganizationModel·sources 배열로 읽는 transport 조합이며 화면이나 entity의 의존성이 아니다.

NoticeRepository와 OrganizationRepository는 각각 injectable source와 별개의 빈 ID 캐시로 시작한다. 조회 순서는 L1 repository 값 캐시 → L2 SwiftData ID fetch → L3 주입된 snapshot mock source이며 성공한 레코드만 캐시에 넣는다. 누락은 임의 fallback 없이 nil이며 negative cache하지 않는다. Notice 조회는 조직을 읽지 않고, SnapshotNoticeSource가 참조 ID 합집합의 sources/evidence URL을 해석한다. 대표 sourceURL은 notice.sourceIds.first만 따라가며 전달된 sources의 배열 순서나 임의 대체 출처를 사용하지 않는다. sourceId/locator/fieldPath 및 확인할 수 없는 source 참조도 보존한다.

OrganizationRepository.path는 parentId를 cycle-safe하게 따라가며 누락 부모에서 복구 가능한 경로를 유지한다. 선택 조직 ID를 전역 leaf로 강제하지 않는다. 두 repository와 source는 MainActor에 한정되고 replaceSource는 해당 캐시를 모두 비운다. 글로벌 저장소·네트워크·모델 생성자의 IO는 없다.

## ViewModel과 State

| 소유 slice | 진입점 | 책임 |
| --- | --- | --- |
| Widgets/Notice/NoticeCard | NoticeCardViewModel / NoticeCardState / NoticeCard | 공고 ID·독립 저장소·공유 favorites 주입 → 카드 표시 문자열/조직 이름/현재 saved; save 결과 이름 또는 unresolved |
| Pages/NoticeDetail | NoticeDetailViewModel / NoticeDetailState / NoticeDetailView | 공고 ID를 조회하고 관련 조직 경로·역할별 이름·출처/근거·기간 요약을 State로 조합 |
| Widgets/Organization/FavoriteOrganizationCard | FavoriteOrganizationCardViewModel / FavoriteOrganizationCardState | 조직 및 연결된 feed 공고를 행 State로 조합; 공유 저장 상태에 따라 보임/사라짐 |
| Pages/Discovery | DiscoveryView | 카드 State 배열·저장 콜백·App 목적지 ID 콜백; paging/sheet/로컬 피드백만 |
| Pages/Favorites | FavoriteListView | 조직 카드 State 배열·삭제·목적지 ID 콜백; 목록 표시 |
| Features/FavoriteOrganization | FavoriteOrganizations / FavoriteOrganizationsRepository | 기존 Observation 단일 ID 집합·동기 저장·삭제·UserDefaults 배열 복원 |
| Features/AddToCalendar | CalendarDraftMapper.application / schedule | NoticeModel·NoticePhase 값만으로 초안 생성; Pages/Widgets State에 의존하지 않음 |

State는 NoticeModel을 통째로 감싸지 않는다. View는 State와 콜백을 받고 원본 모델·저장소·VM을 조회하지 않는다. 관련 path는 상세 State의 이름 배열이며 조직 source나 NoticeModel에 저장하지 않는다. 작은 기간/장소/출처 값은 State에서 재사용한다. 보조 UI는 같은 소유 UI 폴더의 개별 파일로 유지한다.

## 수명과 반응성

App/ContentView가 SwiftDataSnapshotStore의 container/context와 현재 NoticeSession을 소유한다. NoticeSession은 snapshot마다 공유 repository 두 개와 카드·상세·조직 카드 VM을 구성한다. compose에서 필요한 ID가 lazy L1/L2/L3 조회를 거쳐 해결되며 이후 VM은 표시 값을 읽는다. NoticeDestinationView는 cachedNotice만 읽어 body에서 디스크 조회를 하지 않는다. snapshot 변경은 Store.prepare의 명시 save 성공 후 새 Session/VM 그래프를 반환하는 방식이며 오래된 VM을 새 snapshot에 재사용하지 않는다. repository.replaceSource 단독 호출자는 해당 VM도 재생성해야 한다. NoticeSession의 snapshot 편의 생성자는 기존 독립 테스트의 인메모리 fixture 전용이며 production은 Store.makeSession을 사용한다. 실시간 refresh UI는 없다.

각 VM의 state.saved 또는 표시 여부는 매번 같은 FavoriteOrganizations.ids를 읽는다. 별도 mutable favorites 복사본이 없고 다른 카드 저장/조직 카드 삭제가 기존 VM에 바로 보인다. App ContentView는 lazy Tab 클로저 밖에서 favorites.ids와 State 배열을 읽어 SwiftUI Observation을 유지한다. 상세/목적지는 App이 ID로 조립하여 Pages가 서로를 참조하지 않는다.

OS 지도·캘린더는 App/NoticeDetailDestination에서만 연결한다. CalendarDraftMapper는 독립 NoticeModel/phase 값을 받으므로 Page State로의 상향 의존이나 중복 mapper가 없다. 기간 strictness·KST/종일·자정 exclusive·온라인/정확 phase/복수 장소·원문과 신청 URL 구분을 유지한다. 권한 요청·EventStore.save·자동 알람·네트워크는 없다.

## 구조 검사와 확장

기본 internal 진입점이며 private source/cache/초기 렌더링 값은 외부에 노출하지 않는다. 단일 Swift 모듈로 compiler-enforced slice를 제공한다고 주장하지 않는다. check_fsd_boundaries.py는 엔티티 간 참조·상향/동일 layer 교차 slice·UI의 raw 모델/저장소/VM·저장/OS 접근·Calendar→Page State를 부정 fixture로 검사한다. private nested CodingKeys는 Swift 합성 디코딩 이름이라 symbol 연결 대상에서 제외한다. lexical 검사이므로 보간/동적 참조 등은 리뷰로 보완한다.

새 공고 정보는 NoticeModel/decoder에 의미 있는 값으로 추가하고 해당 VM에서 State로 구성한다. 새 조직 정보는 OrganizationModel/source에 두고 필요한 상위 VM만 주입한다. 새 OS 행동은 App에 어댑터를 두고 View에는 콜백만 전달한다. 범용 버튼 스타일은 Shared/UI/Buttons에 두고 문구·조직 상태 조합은 Widgets/Notice/NoticeCard에 둔다.

## SwiftData 저장과 snapshot 계약

App/SwiftDataSnapshotStore가 NoticeRecord·OrganizationRecord·SnapshotManifestRecord schema를 조립한다. 각 entity API의 @Model은 저장 레코드이며 독립 값 도메인에 SwiftData 객체/context를 노출하지 않는다. production은 Application Support/DearbyNoticeCache/notices.store, preview만 명시적인 inMemory 설정이다. 단일 MainActor와 autosaveEnabled=false인 ModelContext를 사용한다. iOS 27 observer API·외부 라이브러리·네트워크는 없다.

NoticeRepository/OrganizationRepository의 L1 hit는 L2/L3를 호출하지 않는다. SwiftDataNoticeSource/SwiftDataOrganizationSource는 predicate+ID로 L2를 조회하고 miss일 때 외부 ID source를 호출한다. 현재 외부 source는 번들 snapshot mock이다. 성공한 외부 레코드는 encode/insert/명시 save 후 반환하고 그때 L1에 들어간다. 조직 부모도 같은 순서다. missing은 nil이며 negative cache하지 않는다. 조회·codec·외부·저장 오류는 throws로 전달하고 save 실패 시 rollback하여 L1에 성공을 기록하지 않는다. corrupt L2를 외부 값으로 조용히 덮어쓰지 않는다. 앱은 기존 오류/재시도 화면을 표시하며 초기화 실패 시 파일 삭제/강제 초기화/fatalError를 실행하지 않는다.

NoticeStorageCodec v1은 공고 한 건의 모든 저장 필드를 명시한 payload를 만든다. 원문 source metadata·evidence의 sourceId/locator/fieldPath/URL·unknown 참조·신청 URL/기간·일정·복수 장소·좌표·role을 보존한다. 전체 catalog blob을 저장하거나 L2 전체 데이터를 dictionary source로 다시 로드하지 않는다. OrganizationRecord는 id/name/parentId만 저장한다. 모델 생성자에는 IO가 없다.

SnapshotManifest는 raw bundle SHA256+codec version, schemaVersion/mode/snapshotAt/feed IDs/organization IDs를 보존한다. 프로그램으로 만든 테스트 snapshot은 전체 typed payload의 결정적 hash를 쓴다. 같은 digest에서는 manifest/L2를 재작성하지 않는다. 최초 prepare는 manifest만 저장하여 L3 fallback을 실제 production에 연결한다. snapshot 변경은 삭제된 항목을 포함하여 두 L2 테이블을 비우고 manifest를 갱신하는 단일 save다. 실패하면 rollback하여 이전 manifest·L2·기존 Session/L1을 보존한다. 성공 후 새 외부 snapshot source와 빈 L1을 가진 Session을 구성한다. 이후 개별 record 승격은 별도 save이며 그 실패는 로드/재시도 오류로 전달한다. 새 Session 구성 전에 전체 record를 사전 seed하지 않는다. 작은 feed의 VM 구성 자체가 첫 실행에 필요한 레코드를 조회한다.

UserDefaults favorites 키 dearby.favoriteOrganizationIDs.v1 및 값/복원은 별도 기존 저장소에 남는다. snapshot 삭제로 해당 조직이 없어져도 사용자 저장 ID를 삭제하지 않는다. [ModelContext](https://developer.apple.com/documentation/swiftdata/modelcontext)의 명시 save/rollback/autosave 계약을 사용한다.

## 검증과 한계

2026-09-14 `bash apps/ios/tests/run_standalone.sh` 전체 exit 0. old shared JSON과 최신 앱 JSON의 favorites/Observation/VM·calendar·map·organization 회귀를 유지하고 실제 임시 **디스크** SwiftDataOrganizationTests/SwiftDataNoticeTests/SwiftDataSnapshotTests를 실행했다. L1 hit/L2 fetch/L3 fetch 횟수, 컨테이너 재오픈 후 external 0회 조회, full codec roundtrip, missing/throw/corrupt 구분, save 실패 재시도, 같은 seed 무쓰기/중복 없음, 공고·조직 수정/삭제 및 날짜/feed metadata 교체, 원자 save 실패 시 기존 disk/L1/favorites 보존을 확인했다. 초기화 실패 fixture의 의도적 잘못된 경로에서 CoreData 오류 로그가 발생하지만 원래 파일 보존 assertion과 전체 검사 exit 0이 성공 기준이다.

FSD 63 Swift 파일과 negative fixtures 통과. entity 간 참조·UI raw 모델/저장소 접근·도메인/UI SwiftData 접근·Calendar→Page 의존을 검사하며 단일 모듈의 lexical 검사 한계는 코드 리뷰로 보완한다. Simulator build 12:31:28Z 및 build_run_sim 12:32:38Z 성공, 경고/오류 없음. 로컬 로그는 build/swiftdata-final-tests.log와 XcodeBuildMCP workspace build 로그다.

전용 Dearby-Issue1-iOS A434888F-4096-48AE-91B2-37A498233B55에서 새 앱 첫 실행 후 종료/두 번째 실행을 수행했다. 안정화 후 발견·KRC 상세·복귀·즐겨찾기 DB 표시를 캡처/시각 확인했다. read-only SQLite 비교에서 공고 4/조직 8/manifest 1건 및 manifest 전체 row가 재실행 전후 동일했고 preferences는 두 번 모두 [db-insurance]였다. 증거는 build/swiftdata-first-launch.png, swiftdata-second-launch.png, swiftdata-detail-stable.png, swiftdata-favorites-stable.png 및 swiftdata-store-first/second.json이다. 사용자 기기·즐겨찾기 초기화/삭제 및 실제 캘린더 저장은 하지 않았다.

이번 smoke는 디스크 재실행과 상세/탭 표시 확인이다. 이전 저장/삭제 상호 반영 smoke 및 Maps/캘린더 편집기 열기·취소는 이전 결과이며 이번에 외부 OS 전환을 재실행하지 않았다. 실제 touch doubletap/swipe·물리 햅틱·전체 접근성·실기기·대용량 성능·향후 SwiftData schema migration은 미검증이다. 동기 MainActor는 현재 작은 sample 범위다. 원본 JSON activities, activity-samples 리소스, activity.<id> 접근성 태그는 호환성 예외다.

이전 모델/State 공통 migration은 제거되는 entity 타입과 모든 호출부를 함께 바꿔 빌드 가능한 하나의 화면 데이터 경계 기능으로 묶었다. 이번 SwiftData는 조직 source, 공고 source/codec, App snapshot/재실행 통합의 기능 커밋으로 나눈다.

## 캘린더 원본 URL 메모 (2026-09-14)

신청 및 활동 CalendarEventDraft.notes는 NoticeModel.sourceURL을 HTTP(S) 검증한 URL 문자열 하나이며 누락/잘못된 원본은 빈 문자열이다. 접두어·요약·날짜 설명·지도 링크를 덧붙이거나 신청 URL로 대체하지 않는다. EKEvent.url은 기존 신청 URL/온라인 URL을 유지하고 제목·기간·장소 및 지도 버튼은 그대로다. schedule mapper의 불필요한 mapURL 콜백과 호출부를 제거했다.

이번 검증은 run_standalone.sh의 calendar swiftc 명령으로 CalendarDraftTests를 컴파일하고 앱/old shared JSON 각각 실행한 것이다. 원본 HTTP/HTTPS·누락·잘못된 scheme/host/credentials·무관한 source ID·이벤트 URL 분리 및 기존 KST/날짜/phase/장소 회귀가 통과했다. FSD 63파일/negative fixtures와 Simulator build 12:58:10Z(경고/오류 없음) 통과. 전체 SwiftData/favorites 검사는 이전 결과이며 이번 작은 notes 변경으로 재실행하지 않았고 실제 캘린더 저장/편집기 실행도 하지 않았다.

## 공통 버튼과 카드 조합 (2026-09-14)

Shared/UI/Buttons/PrimaryButton과 SecondaryButton은 generic ViewBuilder label·action만 받는 internal 네이티브 스타일 컴포넌트다. 각각 borderedProminent/bordered이며 도메인 타입·문구·저장 상태를 모른다. Widgets/Notice/NoticeCard/NoticeCardSaveButton(saved:organizationName:onSave:)이 기존 저장 문구·heart 아이콘·무한 너비·lineLimit(2)를 PrimaryButton label로 조합한다. 이전 Features/FavoriteOrganization/UI/SaveOrganizationButton은 제거했으며 실제 저장 의미/상태는 기존 Feature에 남는다.

NoticeCard는 표시 조건·save.<noticeID>/details.<noticeID>·더블탭 및 제목 Text를 유지한다. 상세 동작은 SecondaryButton을 사용하며 bordered 스타일은 이번 요청의 의도된 외형 변경이다. 불필요한 로딩/크기 변형이나 추가 title 컴포넌트는 만들지 않았다. Shared/UI segment 경로는 기존 FSD 일반 규칙으로 허용된다.

이번 FSD 66 Swift 파일/negative fixtures 및 Simulator build 13:20:44Z 통과(경고/오류 없음). diff에서 제목 Text·표시 조건·doubletap·접근성 ID 유지 확인. 단순 컴포넌트 조합 변경으로 전체 suite/실제 UI 및 캘린더 동작은 실행하지 않았다.

## 위젯 도메인 그룹과 같은 폴더 배치 (2026-09-14)

Widgets/Notice/NoticeCard는 NoticeCard·NoticeCardSaveButton·NoticeFact·NoticeCardViewModel·NoticeCardState 5개 파일, Widgets/Organization/FavoriteOrganizationCard는 View·ViewModel·State 3개 파일을 같은 컴포넌트 폴더에 둔다. UI/Model 하위 폴더는 사용하지 않는다. 도메인은 그룹이며 slice identity는 Domain+Widget 전체다. 같은 도메인이라도 다른 위젯을 직접 참조하지 않는다. 저장소 조합은 ViewModel, 렌더링은 State/값/콜백 계약을 유지한다. 다른 layer와 Shared 버튼 구조는 변경하지 않았다.

구조 검사는 위젯 파일의 Swift View 준수 선언을 식별하며 multiline·generic constraint·SwiftUI.View fixture를 포함한다. 자체 State 사용·VM→repository는 허용하고 View→VM/raw model/repository/OS/storage와 같은/다른 도메인의 형제 위젯 참조는 차단한다. generic 인자의 View 제약만으로 State를 View라 판단하지 않는다. 작은 lexical 검사이므로 복잡한 Swift 매크로/동적 alias 전체를 파싱하는 compiler 보장은 아니다.

이번 FSD 66파일/positive·negative fixtures, run_standalone.sh에서 갱신한 *State.swift/*ViewModel.swift 경로로 NoticeViewModelTests 컴파일 및 old shared/앱 JSON 각각 실행 통과. Simulator build 13:28:05Z 성공(경고/오류 없음). 8개 Swift 파일 내용은 이동 전과 동일함을 비교했고 전체 disk/calendar suite·실제 UI 실행은 하지 않았다.

## #2 native Shared UI

현재 공개 API와 기본 Section/List/폰트 사용 규칙은 [NATIVE-UI](docs/NATIVE-UI.md)에 있다. Shared는 순수 표시 값과 콜백만 받으며 InformationRow가 도메인 없는 fact 조합을 대체한다. 도메인 카드·제목·State/VM의 flat 구조는 그대로이며 App 상태 수명과 body 조회 경계는 변경하지 않는다. 탐색은 semantic grouped 배경과 native large 버튼을 쓰고 접근성 크기에서 카드 자연 높이를 허용한다. 초기 구현 FSD 69파일 및 Xcode 26.6 simulator build 성공; 실제 화면 검증 결과는 별도 증거 문서에서 갱신한다.

#2 상세는 native List/Section과 Shared InformationRow/StatusMessage로 구성한다. Calendar/Map route·callback·원본 URL 메모는 그대로다. 중복 순수 fact 파일 2개를 제거했다.

#2 FavoriteOrganizationCard는 native Section을 반환하며 FavoriteListView의 List 안에 직접 조립한다. 저장소/ID/삭제 callback은 변하지 않고 삭제 label에 조직명이 포함된다.

## #2 최종 검증 및 접근성 정책

기본 발견은 시스템 paging, 접근성 크기는 자연 높이·자연 스크롤이다. 같은 State/로컬 저장 피드백 수명 안에서 scroll target 정책만 달라진다. Shared API/native mapping과 사용처는 [NATIVE-UI](docs/NATIVE-UI.md), 실제 build·회귀·터치·PNG 및 미검증 항목은 [검증 기록](docs/VERIFICATION-ISSUE-02.md)을 따른다. 이번 전체 standalone 회귀/FSD 67파일/최종 simulator build가 통과했다. 실제 doubletap·지도 앱·calendar editor·VoiceOver 낭독은 검증 완료로 주장하지 않는다.

## #2 일정·아이콘 후속

Shared/UI MetadataRow는 아이콘/문자열만, Shared/Lib CompactPeriod는 원시 날짜 문자열/시간대만 받는다. NoticeDetailState의 NoticeScheduleState가 원본 phase 순서대로 표시 이름·기간·phase별 장소를 조합하고, View는 같은 index의 App callback을 호출한다. 원본 Notice/Organization model, repository/cache, CalendarDatePolicy 및 지도/캘린더 메모 계약은 변경하지 않는다. 제목은 기존 View 내부, widget flat 구조와 Observation 소유권은 유지한다. 최신 실행 결과는 VERIFICATION-ISSUE-02의 후속 기록이 이전 미검증 기록보다 우선한다.

## Calendar 참고 상세 표시 후속

EventPeriodPresentation/EventTimeRows/LocationInformation은 Shared의 범용 값·표시 API다. NoticeScheduleState/NoticeSchedulePlaceState/NoticePlaceState와 Detail VM이 원본 일정·장소를 표시용으로 조합하며 Views는 값과 콜백을 렌더링한다. 이 표시 포맷은 calendar draft와 공유하지 않으므로 export 정책/원본 URL memo 및 원본 model/cache는 그대로다. 카드 표현도 변경하지 않는다. 장소 변환은 명확한 문자열 경계만 사용하며 원본 venue/summary는 보존한다. 관련 테스트 실행 범위·대표 상세 사진은 docs/evidence/issue-02/calendar-detail/README.md에 기록한다.

Calendar screenshot 후속: Shared/Lib의 `EventTimelineInterval`은 명시적 timezone의 검증된 날짜 값만 받아 한 날짜의 half-open clip/눈금을 계산한다. NoticeDetail State/VM의 `EventPeriodPresentation` 및 applicationURL이 이를 조립하며 Shared/UI의 `EventDayTimeline`, `EventDaySelector`, `EventTimelineGrid`, `ExternalLinkCard`는 값/Binding만 받는다. 선택 날짜는 미리보기의 일시적인 local State이며 repository, favorite owner, export mapper, App 수명은 바꾸지 않는다. UI 하위 조각은 역할별 View 파일이며 순수 scroll helper만 함수로 둔다. OS busy/calendar provider는 이번 범위 밖이다.
