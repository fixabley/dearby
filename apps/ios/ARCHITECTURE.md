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
App/NoticeDetailDestination.swift
App/NoticeSession.swift
App/VenueMapLauncher.swift
App/VenueMapLink.swift
Entities/Notice/API/NoticeRecordSource.swift
Entities/Notice/API/NoticeRepository.swift
Entities/Notice/API/SnapshotNoticeSource.swift
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
Entities/Organization/API/OrganizationRepository.swift
Entities/Organization/API/OrganizationSource.swift
Entities/Organization/API/SnapshotOrganizationSource.swift
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
Pages/NoticeDetail/UI/NoticeDetailField.swift
Pages/NoticeDetail/UI/NoticeDetailView.swift
Pages/NoticeDetail/UI/NoticeIdentityFact.swift
Pages/NoticeDetail/UI/NoticeIdentityView.swift
Pages/NoticeDetail/UI/NoticeLocationView.swift
Pages/NoticeDetail/UI/NoticeScheduleView.swift
Pages/NoticeDetail/UI/VenueMapButton.swift
Widgets/FavoriteOrganizationCard/Model/FavoriteOrganizationCardState.swift
Widgets/FavoriteOrganizationCard/Model/FavoriteOrganizationCardViewModel.swift
Widgets/FavoriteOrganizationCard/UI/FavoriteOrganizationCard.swift
Widgets/NoticeCard/Model/NoticeCardState.swift
Widgets/NoticeCard/Model/NoticeCardViewModel.swift
Widgets/NoticeCard/UI/NoticeCard.swift
Widgets/NoticeCard/UI/NoticeFact.swift
```

## 엔티티와 조회 경계

Entities/Notice의 **NoticeModel 하나**가 공고의 id/title/aiDescription/targetUser/participationCondition/applicationInformation, 조직 참조 ID/role/basis/note, 기간·장소·분류·혜택·확인 사항·출처·근거를 소유한다. summary/audience/eligibility/application은 decoder의 원본 JSON 키로만 남고 같은 의미의 이중 public API는 없다. aiDescription은 검토 샘플 요약이며 provenance는 reviewed_sample.summary로 새 AI 생성이라 표시하지 않는다. NoticeModel은 OrganizationModel·조직 이름·경로를 갖지 않는다. schedule 원본 기간과 schedules의 phase별 장소 값은 의미 있는 도메인 정보이며 온라인 단계에는 오프라인 장소를 넣지 않는다.

Entities/Organization의 OrganizationModel은 id/name/parentId이며 JSON parentOrganizationId에 대응한다. 두 entity는 상대 slice를 참조하지 않는다. NoticeDetail/NoticeSummary/NoticeCatalog 및 NoticeDetailRepository는 제거했고 호환 alias도 없다. App/BundleSnapshot은 기존 JSON을 NoticeModel·OrganizationModel·sources 배열로 읽는 transport 조합이며 화면이나 entity의 의존성이 아니다.

NoticeRepository와 OrganizationRepository는 각각 injectable source와 별개의 빈 ID 캐시로 시작한다. 원본은 snapshot dictionary source이고 처음 조회에서 fetch한 성공 레코드만 캐시에 넣는다. 누락은 임의 fallback 없이 nil이며 negative cache하지 않는다. Notice 조회는 조직을 읽지 않고, SnapshotNoticeSource가 참조 ID 합집합의 sources/evidence URL을 해석한다. 대표 sourceURL은 notice.sourceIds.first만 따라가며 전달된 sources의 배열 순서나 임의 대체 출처를 사용하지 않는다. sourceId/locator/fieldPath 및 확인할 수 없는 source 참조도 보존한다.

OrganizationRepository.path는 parentId를 cycle-safe하게 따라가며 누락 부모에서 복구 가능한 경로를 유지한다. 선택 조직 ID를 전역 leaf로 강제하지 않는다. 두 repository와 source는 MainActor에 한정되고 replaceSource는 해당 캐시를 모두 비운다. 글로벌 저장소·네트워크·모델 생성자의 IO는 없다.

## ViewModel과 State

| 소유 slice | 진입점 | 책임 |
| --- | --- | --- |
| Widgets/NoticeCard | NoticeCardViewModel / NoticeCardState / NoticeCard | 공고 ID·독립 저장소·공유 favorites 주입 → 카드 표시 문자열/조직 이름/현재 saved; save 결과 이름 또는 unresolved |
| Pages/NoticeDetail | NoticeDetailViewModel / NoticeDetailState / NoticeDetailView | 공고 ID를 조회하고 관련 조직 경로·역할별 이름·출처/근거·기간 요약을 State로 조합 |
| Widgets/FavoriteOrganizationCard | FavoriteOrganizationCardViewModel / FavoriteOrganizationCardState | 조직 및 연결된 feed 공고를 행 State로 조합; 공유 저장 상태에 따라 보임/사라짐 |
| Pages/Discovery | DiscoveryView | 카드 State 배열·저장 콜백·App 목적지 ID 콜백; paging/sheet/로컬 피드백만 |
| Pages/Favorites | FavoriteListView | 조직 카드 State 배열·삭제·목적지 ID 콜백; 목록 표시 |
| Features/FavoriteOrganization | FavoriteOrganizations / FavoriteOrganizationsRepository | 기존 Observation 단일 ID 집합·동기 저장·삭제·UserDefaults 배열 복원 |
| Features/AddToCalendar | CalendarDraftMapper.application / schedule | NoticeModel·NoticePhase 값만으로 초안 생성; Pages/Widgets State에 의존하지 않음 |

State는 NoticeModel을 통째로 감싸지 않는다. View는 State와 콜백을 받고 원본 모델·저장소·VM을 조회하지 않는다. 관련 path는 상세 State의 이름 배열이며 조직 source나 NoticeModel에 저장하지 않는다. 작은 기간/장소/출처 값은 State에서 재사용한다. 보조 UI는 같은 소유 UI 폴더의 개별 파일로 유지한다.

## 수명과 반응성

App/NoticeSession은 로드한 snapshot마다 **공유 repository 두 개**를 소유한다. compose 단계에서 feed의 카드·상세 및 조직 카드 VM을 만들며 이 과정에서 필요한 레코드가 lazy repository를 통해 캐시된다. VM은 snapshot 수명 동안 유지하고 이미 해결한 표시 값만 읽으므로 SwiftUI body에서 source fetch를 반복하지 않는다. NoticeSession.replaceSnapshot은 두 source/cache를 함께 교체하고 모든 snapshot VM을 재구성한다. 오래된 VM을 새 snapshot에 재사용하지 않는다. 직접 repository 교체만 한 경우 해당 scoped VM을 재생성하는 것이 계약이며 App에서는 replaceSnapshot을 사용한다. 실시간 refresh UI는 이번 범위에 없다.

각 VM의 state.saved 또는 표시 여부는 매번 같은 FavoriteOrganizations.ids를 읽는다. 별도 mutable favorites 복사본이 없고 다른 카드 저장/조직 카드 삭제가 기존 VM에 바로 보인다. App ContentView는 lazy Tab 클로저 밖에서 favorites.ids와 State 배열을 읽어 SwiftUI Observation을 유지한다. 상세/목적지는 App이 ID로 조립하여 Pages가 서로를 참조하지 않는다.

OS 지도·캘린더는 App/NoticeDetailDestination에서만 연결한다. CalendarDraftMapper는 독립 NoticeModel/phase 값을 받으므로 Page State로의 상향 의존이나 중복 mapper가 없다. 기간 strictness·KST/종일·자정 exclusive·온라인/정확 phase/복수 장소·원문과 신청 URL 구분을 유지한다. 권한 요청·EventStore.save·자동 알람·네트워크는 없다.

## 구조 검사와 확장

기본 internal 진입점이며 private source/cache/초기 렌더링 값은 외부에 노출하지 않는다. 단일 Swift 모듈로 compiler-enforced slice를 제공한다고 주장하지 않는다. check_fsd_boundaries.py는 엔티티 간 참조·상향/동일 layer 교차 slice·UI의 raw 모델/저장소/VM·저장/OS 접근·Calendar→Page State를 부정 fixture로 검사한다. private nested CodingKeys는 Swift 합성 디코딩 이름이라 symbol 연결 대상에서 제외한다. lexical 검사이므로 보간/동적 참조 등은 리뷰로 보완한다.

새 공고 정보는 NoticeModel/decoder에 의미 있는 값으로 추가하고 해당 VM에서 State로 구성한다. 새 조직 정보는 OrganizationModel/source에 두고 필요한 상위 VM만 주입한다. 새 OS 행동은 App에 어댑터를 두고 View에는 콜백만 전달한다. 범용 Shared는 현재 실제 필요가 없어 만들지 않았다.

## 검증과 한계

`bash apps/ios/tests/run_standalone.sh`가 실제 Swift 6 컴파일·실행을 수행한다. old shared 및 최신 앱 JSON에서 상태/관찰·실제 임시 UserDefaults 복원·NoticeModel 출처 근거 전체 수/경로·독립 notice/org fetch count·캐시 공유/교체/missing/cycle·VM 저장/다른 컴포넌트 삭제·snapshot 재구성과 기존 calendar 날짜/URL/phase 회귀를 확인했다. 별도 map 좌표/URL/실패 및 organization cycle 테스트도 유지한다. 55 Swift 파일 FSD/fixture 통과.

2026-09-14 전용 Dearby-Issue1-iOS A434888F-4096-48AE-91B2-37A498233B55에서 build_run_sim 11:59:59Z 성공(경고/오류 없음). 실제 KRC 저장 표시/피드백 → 즐겨찾기 탭의 KRC+DB → 상세와 기간/지도 버튼 → back → 이번에 추가한 KRC 삭제 → 발견 카드 저장 해제 및 DB 유지 확인. Discovery sheet 진입과 Escape 복귀도 확인했다. prefs 전후 모두 db-insurance이며 기존 즐겨찾기를 삭제/초기화하지 않았다. 로컬 증거 build/vm-*.json, favorites-before-vm.json, viewmodel-standalone.log.

이번에는 지도/캘린더 OS 전환·실제 캘린더 저장을 실행하지 않았다. 이전 작업의 Maps/캘린더 편집기 열기·취소는 이전 검증으로만 본다. 실제 터치 doubletap/swipe·물리 햅틱·전체 접근성·실기기는 미검증이다. 원본 JSON 활동/activities, activity-samples 리소스, activity.<id> 접근성 태그와 기존 저장 키는 호환성 예외이며 다른 앱 도메인이 아니다. 다른 플랫폼·공통 snapshot·사용자 simulator는 수정하지 않았다.

이번 변경은 이전 엔티티 타입 제거와 모든 호출부의 State 전환을 함께 묶는 하나의 화면 데이터 경계 기능 커밋이다. 삭제된 타입을 중간 호환 alias로 유지하거나 코드/테스트/문서를 별도 단계 커밋으로 나누지 않는다.
