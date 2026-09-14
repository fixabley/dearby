# Android 모델·State·ViewModel과 FSD

[Related #1](https://github.com/fixabley/dearby/issues/1), [공통 설계 #3](https://github.com/fixabley/dearby/pull/3), [장소·기간 계약 #6](https://github.com/fixabley/dearby/pull/6)을 참고한다. 단일 `:app` 모듈에서 공고는 **NoticeModel 하나**, 조직은 독립 OrganizationModel로 관리한다. 기존 공고/상세 Entity 이중 모델, 카탈로그 Entity와 상세 Repository는 제거했다. 과거 구현은 Git 이력에 남는다.

## 실제 트리

`app/src/main/java/io/fixabley/dearby/` 기준이며 파일별 UI 구성요소를 유지한다.

```text
MainActivity.kt                         의존성 생성·플랫폼 진입점
app/
  DearbyApp.kt                          탭·상세 라우팅, State/콜백 전달
  NoticeSession.kt                      snapshot·공유 저장소·VM 수명
  OpenCalendarEditor.kt                 ACTION_INSERT 생성/실패 안내
  OpenVenueMap.kt                       geo Intent 생성/실패 안내
  data/
    NoticeSnapshot.kt                  번들 transport DTO/Reader 계약
    AssetNoticeSnapshotReader.kt        기존 JSON → 독립 모델 목록
    DecodeCalendarMetadata.kt          신청/단계 날짜·방식 해석
    DecodeNoticeEvidence.kt            출처 ID·locator·fieldPath 해석
    DecodeNoticeLocation.kt            복수 장소·선택 좌표 해석
pages/
  discovery/ui/DiscoveryScreen.kt       State 목록·pager·피드백
  favorites/ui/FavoritesScreen.kt       State 목록·빈 화면
  noticedetail/
    model/NoticeDetailViewModel.kt      공고·조직·즐겨찾기 조합
    model/NoticeDetailState.kt          상세 표시값·역할·단계 장소·초안
    ui/NoticeDetailSheet.kt             State + 콜백
    ui/NoticeIdentity.kt                관심 조직·분류·맥락·회차
    ui/NoticeLocationSection.kt         기존 장소 안내
    ui/NoticeScheduleSection.kt         단계 안내·캘린더 액션
    ui/VenueMapButton.kt                장소별 지도 액션
    ui/AddToCalendarButton.kt           캘린더 초안 액션
widgets/
  noticecard/
    model/NoticeCardViewModel.kt        ID 조회·표시 조합·저장 행동
    model/NoticeCardState.kt            카드 표시값·saved
    ui/NoticeCard.kt                    State·위치·콜백
  favoriteorganizationcard/
    model/FavoriteOrganizationCardViewModel.kt
    model/FavoriteOrganizationCardState.kt  조직·상위 이름·연결 공고 행
    ui/FavoriteOrganizationCard.kt      State·삭제/상세 콜백
features/
  favoriteorganization/
    model/FavoritesState.kt             단일 관찰 ID 집합
    api/FavoriteStore.kt                저장 계약
    api/SharedPreferencesFavoriteStore.kt
  addtocalendar/model/
    CalendarDraft.kt                    immutable OS 편집기 입력
    ApplicationCalendarDraft.kt        NoticeModel → 신청 초안
    PhaseCalendarDraft.kt              NoticeModel/NoticePhase → 단계 초안
    CalendarPeriod.kt                  strict 시각/종일 경계
    CalendarLinks.kt                   검증된 http(s) 링크
entities/
  notice/
    model/NoticeModel.kt                공고 정보·조직 ID/명시 역할만
    model/NoticeApplication.kt          summary·기간·URL·신청 방식
    model/NoticePhase.kt                phase·기간·온라인 URL
    model/NoticeLocation.kt             장소 summary·mode·venues
    model/NoticeVenue.kt                phase·이름·주소·선택 좌표
    model/VenueCoordinates.kt           유한 수/범위 검사
    model/NoticeSource.kt               출처 메타데이터·필드별 근거
    api/NoticeSource.kt                 주입 계약·인메모리 원본
    api/NoticeRepository.kt             독립 lazy cache-aside
    ui/NoticeClassification.kt          분류 문자열 표시
  organization/
    model/OrganizationModel.kt          id/name/parentId
    api/OrganizationSource.kt           주입 계약·인메모리 원본
    api/OrganizationRepository.kt       독립 cache-aside·순환 안전 경로
shared/ui/
  NoticeFact.kt                         범용 제목/값
  theme/Theme.kt                        기존 Material 3 테마
```

## 진입점과 의존 방향

`App → Pages → Widgets → Features → Entities → Shared`이며 아래 레이어를 건너뛰는 참조는 허용한다. 같은 layer의 다른 slice를 참조하지 않는다. 특히 `entities.notice`와 `entities.organization`은 서로 import하지 않는다. App/Shared는 segment 예외다.

| Slice | 외부에서 사용하는 진입점 |
| --- | --- |
| Pages/discovery, favorites | 각 `ui.*Screen` |
| Pages/noticedetail | `model.NoticeDetailViewModel`, `model.NoticeDetailState`, `ui.NoticeDetailSheet` |
| Widgets/noticecard | `model.NoticeCardViewModel`, `model.NoticeCardState`, `ui.NoticeCard` |
| Widgets/favoriteorganizationcard | `model.FavoriteOrganizationCardViewModel`, `model.FavoriteOrganizationCardState`, `model.FavoriteNoticeState`, `ui.FavoriteOrganizationCard` |
| Features/favoriteorganization | `model.FavoritesState`, `api.FavoriteStore`, `api.SharedPreferencesFavoriteStore` |
| Features/addtocalendar | `model.CalendarDraft`, `applicationCalendarDraft`, `phaseCalendarDraft` |
| Entities/notice | `NoticeModel`, `NoticeContext`, 신청/기간/장소/좌표/출처/근거 모델, `api.NoticeSource`, `InMemoryNoticeSource`, `NoticeRepository`, `ui.NoticeClassification` |
| Entities/organization | `OrganizationModel`, `OrganizationSource`, `InMemoryOrganizationSource`, `OrganizationRepository` |

정확한 심볼 allowlist는 `scripts/check-fsd.py`와 일치한다. `model.NoticeSource`는 출처 메타데이터이고 `api.NoticeSource`는 공고 조회 계약이다. UI helper는 같은 slice 안의 internal 파일이며 다른 slice에 공개하지 않는다. 각 클래스의 캐시·derived content·setter는 private이다. Kotlin internal은 모듈 밖 접근만 막으며 **slice를 컴파일러가 격리하는 구조는 아니다**. 작은 구조 검사는 package/경로·import/FQN·상향/교차 의존·진입점·UI 저장소 및 전체 도메인 모델 접근을 검사한다. 정규식 기반으로 문자열 보간/별칭 전파/리플렉션 등 모든 Kotlin 의미를 분석하지 않는다.

## 모델과 조회 책임

NoticeModel에는 선택 조직 ID와 contexts/organizationLinks의 명시적 역할 ID만 있다. 조직 이름·객체·경로·전체 트리는 없다. 부모가 있는 선택 노드를 전역 leaf로 바꾸지 않고 기존 관심 대상 ID를 유지한다. 조직 record는 별도로 보관하고 VM이 두 저장소를 조합한다. State의 조직 이름/조상 이름/해석한 역할은 렌더링 값이며 영속 공고 데이터가 아니다. View는 전체 NoticeModel이나 저장소를 받지 않는다. 의미 있는 작은 기간·장소·출처 값과 immutable CalendarDraft는 State/내부 UI 입력에 사용한다.

NoticeModel은 title, aiDescription, targetUser, participationCondition, applicationInformation, schedules, location, benefits/issues, sourceURL/sources/evidence를 보존한다. aiDescription은 기존 검토 sample summary이며 provenance `reviewed_sample_summary`로 표시하고 새로운 AI 생성이라고 주장하지 않는다. 원본 출처 id/url/kind/checkedAt/access/note, 근거 sourceId/locator/fieldPath/sourceURL을 실제 디코딩한다. 모르는 URL은 null이며 사실을 만들지 않는다. 원본 rich JSON은 변경하지 않고 앱 표시 필드만 평탄화한다.

NoticeSnapshot/Reader는 App 경계에서 기존 번들을 두 독립 모델 목록으로 읽는 transport 조합이다. UI 입력이나 별도 공고 Entity가 아니다. 모델 생성자는 I/O/조회가 없는 순수 데이터다.

## 캐시·VM·관찰 수명

MainActivity가 Compose 밖에서 FavoritesState와 NoticeSession을 한 번 생성한다. Session은 공고/조직 Repository를 각각 공유하며 snapshot별 독립 source를 주입한다. source의 dictionary와 처음 비어 있는 ID cache는 별개다. find는 cache 확인 → miss 시 source 호출 → 성공만 저장한다. 누락 ID는 repository에서 캐시하지 않는다. Organization path는 parentId를 따라 visited ID로 순환을 중단하며 복구 가능한 부분 경로를 유지한다. find/path/replaceSource는 monitor로 동기화한다.

App/UI 스레드가 Session과 VM 수명을 소유한다. 카드/상세 VM은 ID별로 재사용하고 렌더링마다 생성하지 않는다. VM의 derivedStateOf는 repository revision을 관찰하며 같은 세대의 성공/누락 표시 결과를 재사용한다. replaceSource는 모든 ID 캐시를 지우고 관찰 revision을 증가시킨다. Session.replaceSnapshot은 Compose snapshot 안에서 양쪽 source와 목록을 교체한다. 기존 카드·상세 VM은 새 이름/부모/공고 값을 읽으며, 즐겨찾기 VM 목록은 snapshot의 연결 공고 ID 구성이 바뀔 수 있어 재구성한다. live refresh UI는 없고 이 API의 교체 동작을 테스트한다.

saved는 단일 FavoritesState.ids에서 State getter가 읽는다. 별도 mutable 복사본으로 보관하지 않아 다른 카드/탭에서 저장·삭제해도 기존 VM과 Compose 관찰자가 즉시 갱신된다. rendering View는 State와 콜백만 받는다. 페이지 간 목적지 콜백과 지도/캘린더 OS 부작용은 App이 조립한다. pager·일시 피드백은 발견 페이지, rememberSaveable 탭·remember 상세 선택은 App에 남는다. Activity 재생성 시 저장 ID는 복원하고 상세 선택은 해제한다.

## 지도·캘린더와 호환성

NoticeModel.venuesFor는 온라인이면 빈 장소, 그 외 exact phase 일치의 모든 장소를 반환하는 단일 순수 join이다. 상세 VM과 캘린더 Feature가 같은 규칙을 사용한다. Feature는 Page/Widget State를 import하지 않는다. 장소 summary/층/호실 및 기존 UI 문구·제스처·탭·sheet/back은 유지한다.

좌표가 유한하고 범위 내인 venue만 지도 액션을 제공한다. 생략/null/불완전 좌표는 미확인이고 명시 (0,0)은 유효하다. package를 고정하지 않은 geo ACTION_VIEW의 한국어/특수 문자 label을 인코딩하며 handler 부재를 안내한다. 위치 권한·geocoding·현재 위치·네트워크는 추가하지 않는다.

캘린더는 ACTION_INSERT/CalendarContract.Events.CONTENT_URI로 사용자 편집기를 열고 저장했다고 간주하지 않는다. 제목·장소·시작/종료·종일 값은 유지하며 DESCRIPTION은 검증된 NoticeModel.sourceURL 문자열 하나만 전달한다. 접두어·요약·날짜 안내·신청/온라인 URL·지도 링크를 붙이지 않는다. 원본 URL이 없거나 유효한 HTTP(S)가 아니면 빈 문자열이며 다른 URL로 대체하지 않는다. Android에 별도 가짜 URL extra를 추가하지 않는다. 캘린더 권한·직접 provider 쓰기·참석자·자동 알림은 없다. ActivityNotFoundException/SecurityException을 안내한다.

정확한 시작/종료 시각이면 실제 구간을 사용한다. 나머지는 strict 날짜와 원래 시간대(기본 Asia/Seoul)의 종일 구간이며 inclusive 종료 날짜 다음날/정확한 자정의 exclusive 경계를 구별한다. 마감만 있으면 마감일(자정이면 전날), 시작만 있으면 알려진 하루다(원래 안내는 앱 상세에서 유지한다). 잘못된/역전 날짜는 액션이 없다. 임의 한 시간을 만들지 않는다. Android 종일 millis는 UTC 자정으로 원래 달력 날짜를 보존한다. EVENT_LOCATION은 온라인 단계의 온라인 표시와 오프라인 단계의 모든 일치 장소/주소를 유지한다.

`dearby.favorites.v1`/`organizationIDs` StringSet·조직 ID·manifest·test tag를 유지한다. JSON `activities`, `activity-samples.json`, `activity.*` fixture/tag, Android MainActivity/ComponentActivity 등은 통신/플랫폼 호환성 이름이며 별도 앱 도메인이 아니다. canonical asset SHA256은 `c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f`다.

## 새 기능 배치 예시

- 공고 표시 항목은 NoticeModel/번들 decoder에서 의미를 보존하고 카드 또는 상세 VM에서 State로 조합한다. View에서 저장소를 조회하지 않는다.
- 새 카드 UI helper는 소유 widget의 ui 파일로 추출한다. 분리만을 이유로 Shared로 올리지 않는다.
- 공통 조직 정보는 Organization source/repository에서 제공하고 각 소비 VM이 조합한다. Notice Entity에 조직 의존을 넣지 않는다.
- 새 OS 액션은 하위 Feature에서 순수 입력을 만들고 App 어댑터/콜백으로 실행한다. 페이지가 다른 페이지를 import하지 않는다.

## 이번 검증과 한계 (2026-09-14)

```sh
cd apps/android
python3 scripts/check-fsd.py --self-test
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
ANDROID_SERIAL=emulator-5556 JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest
```

이번 최종 구조49파일/self-test21(금지15/허용6), JVM29, Debug/계측 APK 컴파일, Lint 오류0/경고12, 전용5556 계측30 모두 통과했다. 테스트 실패/오류/skip은 0이다. JVM은 두 캐시의 miss/hit/원본 호출수·공유 부모·누락·순환·교체, ID-only 모델, VM 역할/출처/장소 조합, 기존 VM의 snapshot 교체, 다른 컴포넌트 삭제 후 saved 및 실제 Compose SnapshotStateObserver invalidation, 기존 즐겨찾기/좌표/기간 정책을 검사한다. 계측은 기존 swipe/doubletap/save/delete/tab/detail/recreate, 실제 저장 복원/asset 해석과 지도/캘린더 Intent 전달·오류를 검증한다.

증거는 `build/state-final-build.log`, `build/state-instrumentation.log`, 표준 `app/build/test-results/testDebugUnitTest/`, `app/build/outputs/androidTest-results/connected/debug/`, `app/build/reports/lint-results-debug.xml`이다. 중간 기반/VM 단계 JVM31/33/34는 제거 전 구 테스트와 함께 실행한 결과이며 최종29와 구분한다. 이전 작업의 계측 결과를 이번 결과로 재사용하지 않았다.

전용5556에서만 실행하고 테스트의 기존 즐겨찾기 집합을 백업/복원했다. 테스트 후 전용 기기만 종료했으며 사용자5554를 조작하지 않았다. 외부 지도 렌더링·캘린더 편집기 UI 열기/취소·실제 저장/동기화는 이번에 검증하지 않았고 캘린더 Save를 수행하지 않았다. 네트워크·로그인·원문 자동 추출·TTL/디스크 캐시·전체 접근성 검증은 범위 밖이다. 동기식 번들 source이며 비동기 공급이 필요하면 별도 수명/취소 정책이 필요하다.


## 캘린더 메모 변경 검증 (2026-09-14)

신청/활동 일정 모두 메모는 검증된 원본 HTTP(S) URL 문자열 하나이며 누락/오류는 빈 문자열이다. 신청/온라인 URL로 대체하지 않는다. 이번 JVM30·Debug·계측 APK 컴파일·FSD49파일/self-test21 통과, 증거는 `build/calendar-source-only.log`와 표준 JVM XML이다. 앞선 계측30/Lint 결과는 이전 구조 작업 결과이며 이번에는 기기 실행·일정 저장·Lint를 반복하지 않았다.
