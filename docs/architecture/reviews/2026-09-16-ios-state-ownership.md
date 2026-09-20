# iOS 상태·조립 후속 검토

최초 검토: 2026-09-16, root 2ad8c53 / iOS worker 7f3e3c3의 동일 앱 소스 기준. 아래 발견 내용은 수정 전 소스 분석이다. 최초 검토 때는 새 검증을 실행하지 않았으며, 이후 사용자가 네 항목 수정을 승인했다. 이번 구현·검증 결과는 아래에 구분하여 기록한다.

## 우선순위

1. **P2: 백그라운드에서 캘린더 조회 재개 가능 경로.** `features/checkCalendarOverlap/api/BusyCalendarLifecycleModifier.swift:20`은 앱 활성 상태와 무관하게 변경 알림에서 `refreshAuthorization`을 호출한다. `model/CalendarPreferences.swift:64`는 활성 상태를 보관/검사하지 않으며 fullAccess 결과가 `publish`의 resume/setEnabled로 이어진다(91줄). 캘린더 ON·상세 attach 상태에서 background 이후 알림이 처리되면 중지한 조회를 다시 시작할 수 있다. Preferences가 앱 활성 상태를 소유하고 background의 알림은 갱신 필요 표시만 하며 실제 재개는 active에서만 허용하는 방향. 실제 OS 알림 전달 시점 미재현.
2. **P2: 즐겨찾기 화면 전체 선조립이 초기 피드와 결합.** `app/providers/AppSnapshotComposition.swift:15`가 저장 여부와 무관하게 모든 organizationIDs의 VM을 만든다. `widgets/favoriteOrganizationCard/model/FavoriteOrganizationCardViewModel.swift:10`은 조직마다 feed 전체를 순회하고 경로 조회 후에야 state getter에서 저장 여부를 확인한다(23줄). CPU 순회는 조직 수×공고 수이며 미저장 조직의 조회/경로 오류도 초기 전체 transaction을 실패시킨다. 표시 대상 조직을 기준으로 조립하고 조직 ID→공고 ID 인덱스를 한 번 구성하는 방향. 저장 추가/삭제 때 화면에 즉시 반영되는 기존 단일 상태 계약은 유지해야 한다. 현재 sample의 체감 성능 문제를 실측했다는 뜻은 아니다.
3. **P3: 발견 페이지가 카드 유효성 검사로 즐겨찾기 전체를 관찰.** `pages/discovery/ui/DiscoveryView.swift:8`은 filter에서 각 VM.state를 읽고 body/ForEach에서 cards를 반복 평가한다. `widgets/noticeCard/model/NoticeCardViewModel.swift:28`의 state getter는 saved 계산을 위해 favorites.ids를 읽는다. 존재 여부만 필요한 부모가 불필요한 즐겨찾기 변경 의존성을 얻는다. 불변 유효성/표시 목록과 카드의 saved 관찰을 분리한다. 접근성 이전/다음, 페이지 count는 같은 필터 결과를 재사용한다. UI 갱신 비용 실측 없음.
4. **P3: 캘린더 갱신과 동의 책임 중복.** 변경 알림이 preferences.refreshAuthorization과 session.refresh를 동시에 부르고, 권한 결과에서 다시 resume→setEnabled→refresh한다. 취소 토큰은 오래된 결과 게시를 방어하지만 불필요한 재시작 경로다. BusyCalendarSession.cancelConsent/continueConsent는 production 호출자가 없고 직접 호출하는 기존 테스트만 남아 있다. 권한 요청은 Preferences 하나가 맡고 상세에는 조회/취소/일시 결과만 남긴다. 세션 자체 삭제는 권하지 않는다.

## 작은 정리 후보

DiscoveryView.detail은 목적지 ID만 사용하는데 NoticeCardState 전체를 저장한다(11,55줄). 가벼운 상세 라우팅 값으로 줄일 수 있다. NoticeCardState.applicationSummary/locationSummary는 현재 카드 UI에서 읽지 않지만 VM·preview 및 일부 테스트에서 계속 생성한다. 표시 요구와 테스트 의도를 확인하고 미사용 필드를 제거할 수 있다.

## 유지할 역할

BusyCalendarSession은 화면별 선택 날짜·조회 취소·generation·개인 결과 폐기를 소유하므로 AppState/Preferences와 수명이 다르다. SettingsViewModel은 두 탭의 설정 열기와 sheet 바인딩을 잇는 실제 공유 표시 상태이며 권한값을 복제하지 않는다. FavoriteOrganizations는 조직을 받는 Feature 행동/피드백, FavoriteOrganizationStore는 독립 Entity의 observable ID와 저장 경계로 역할이 나뉜다. 후자를 합치며 Entity가 OrganizationModel을 참조하도록 만들면 FSD 형제 슬라이스 금지와 충돌한다. 객체 이름이나 코드 길이만으로 제거하지 않는다.

## 구현·검증 진행

캘린더 두 항목은 worker 9e95709 / 통합 361b668, [PR30](https://github.com/fixabley/dearby/pull/30)으로 반영했다. background 알림으로 개인 결과가 재개되는 기존 구현을 테스트로 실패 재현한 뒤 수정했다. Preferences가 활성 상태와 권한 요청을 소유하고 Feature 어댑터가 OS 알림을 한 번 수신한다. BusyCalendarSession은 상세 선택·조회 취소·임시 결과만 담당한다. production 호출이 없는 consent/resume 및 중복 lifecycle 경로를 제거했다.

이번 worker 실행: calendar/standalone/detail 회귀, architecture 12개, production gate, strict SwiftLint 157개 파일 위반 0건, Simulator build 통과. 실제 OS 알림 전달 시점과 기기 UI 조작은 검증하지 않았다. 즐겨찾기(ec6129a)와 발견 페이지(27783ab)도 PR31로 반영했다. 저장된 조직만 조립하고 조직별 인덱스/동기 변경 구독을 사용한다. 저장 오류 시 기존 카드 보존, snapshot 실패/성공별 구독 수명과 부모 관찰0·개별 State.saved 갱신을 테스트했다. 최종 strict lint는160파일 위반0이며 전체 회귀·구조검사·gate와 worker/root 통합 Simulator build가 통과했다.

iOS에서 남은 Session 선언은 BusyCalendarSession 하나다. SettingsViewModel은 설정 sheet 표시, FavoriteOrganizationStore는 즐겨찾기 원본 상태, FavoriteOrganizations는 행동과 피드백, SwiftDataSnapshotStore는 영속 transaction을 소유한다. 단순 전달 세션으로 취급해 합치지 않는다. UserDefaultsCalendarPreferenceStore는 설정 영속 adapter, MemoryCalendarPreferenceStore는 테스트/인메모리 adapter이다. 화면별 ViewModel은 모델 조립을 담당한다.

이번 작업은 iOS에 한정하며 Android/API를 변경하지 않았다. 현재 세션과 후속 상태는 역할별 context 문서를 따른다.
