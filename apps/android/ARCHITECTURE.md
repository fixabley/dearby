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
    cache/NoticeCacheDatabase.kt       App 소유 Room DB v1·손상 파일 보존
    cache/SnapshotManifest.kt          전체 hash/codec/목록 metadata
    cache/RoomSnapshotStore.kt         양쪽 L2+manifest 원자적 준비
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
  notice/noticecard/
    NoticeCard.kt                       State·위치·콜백
    NoticeCardSaveButton.kt             저장 문구와 PrimaryButton 조합
    NoticeCardState.kt                  불변 표시값
    NoticeCardViewModel.kt              공고/조직/즐겨찾기 조합
  organization/favoriteorganizationcard/
    FavoriteOrganizationCard.kt         State·삭제/상세 콜백
    FavoriteOrganizationCardState.kt    표시값과 연결 공고 행
    FavoriteOrganizationCardViewModel.kt
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
    api/NoticeRepository.kt             독립 lazy L1 cache-aside
    api/NoticeDiskStore.kt              L2 계약·StoredNoticeSource(mock L3)
    api/NoticeRecord.kt                 ID/codec/payload Room 행·DAO·adapter
    api/NoticeStorageCodec.kt           공고별 모든 필드 binary codec v1
    ui/NoticeClassification.kt          분류 문자열 표시
  organization/
    model/OrganizationModel.kt          id/name/parentId
    api/OrganizationSource.kt           주입 계약·인메모리 원본
    api/OrganizationRepository.kt       독립 L1 cache-aside·순환 안전 경로
    api/OrganizationDiskStore.kt        L2 계약·StoredOrganizationSource
    api/OrganizationRecord.kt           id/name/parentId Room 행·DAO·adapter
shared/ui/
  buttons/PrimaryButton.kt              native Button 강조수준 진입점
  buttons/SecondaryButton.kt            native OutlinedButton 강조수준 진입점
  InformationRow.kt                     도메인 없는 label/value 묶음 읽기
  MetadataRow.kt                        아이콘+간결한 값/전체 접근성 설명
  PeriodText.kt                         구조화 날짜의 순수 표시 formatter
  ContentSection.kt                     공통 tonal 정보/액션 묶음
  StatusPanel.kt                        neutral/loading/error와 독립 action 슬롯
  theme/Theme.kt                        dynamic/static Material3 theme
  theme/Spacing.kt                      콘텐츠 간격 토큰
```

## 진입점과 의존 방향

`App → Pages → Widgets → Features → Entities → Shared`이며 아래 레이어를 건너뛰는 참조는 허용한다. 같은 layer의 다른 slice를 참조하지 않는다. 특히 `entities.notice`와 `entities.organization`은 서로 import하지 않는다. App/Shared는 segment 예외다.

| Slice | 외부에서 사용하는 진입점 |
| --- | --- |
| Pages/discovery, favorites | 각 `ui.*Screen` |
| Pages/noticedetail | `model.NoticeDetailViewModel`, `model.NoticeDetailState`, `ui.NoticeDetailSheet` |
| Widgets/notice/noticecard | `NoticeCardViewModel`, `NoticeCardState`, `NoticeCard` |
| Widgets/organization/favoriteorganizationcard | `FavoriteOrganizationCardViewModel`, `FavoriteOrganizationCardState`, `FavoriteNoticeState`, `FavoriteOrganizationCard` |
| Features/favoriteorganization | `model.FavoritesState`, `api.FavoriteStore`, `api.SharedPreferencesFavoriteStore` |
| Features/addtocalendar | `model.CalendarDraft`, `applicationCalendarDraft`, `phaseCalendarDraft` |
| Entities/notice | `NoticeModel`, `NoticeContext`, 신청/기간/장소/좌표/출처/근거 모델, `api.NoticeSource`, `InMemoryNoticeSource`, `NoticeRepository`, `NoticeRecord/NoticeDao/RoomNoticeStore/StoredNoticeSource/NoticeStorageCodec`, `ui.NoticeClassification` |
| Entities/organization | `OrganizationModel`, `OrganizationSource`, `InMemoryOrganizationSource`, `OrganizationRepository`, `OrganizationRecord/OrganizationDao/RoomOrganizationStore/StoredOrganizationSource` |

정확한 심볼 allowlist는 `scripts/check-fsd.py`와 일치한다. `model.NoticeSource`는 출처 메타데이터이고 `api.NoticeSource`는 공고 조회 계약이다. UI helper는 같은 slice 안의 internal 파일이며 다른 slice에 공개하지 않는다. 각 클래스의 캐시·derived content·setter는 private이다. Kotlin internal은 모듈 밖 접근만 막으며 **slice를 컴파일러가 격리하는 구조는 아니다**. 작은 구조 검사는 package/경로·import/FQN·상향/교차 의존·진입점·UI 저장소 및 전체 도메인 모델 접근을 검사한다. 정규식 기반으로 문자열 보간/별칭 전파/리플렉션 등 모든 Kotlin 의미를 분석하지 않는다.

## 모델과 조회 책임

NoticeModel에는 선택 조직 ID와 contexts/organizationLinks의 명시적 역할 ID만 있다. 조직 이름·객체·경로·전체 트리는 없다. 부모가 있는 선택 노드를 전역 leaf로 바꾸지 않고 기존 관심 대상 ID를 유지한다. 조직 record는 별도로 보관하고 VM이 두 저장소를 조합한다. State의 조직 이름/조상 이름/해석한 역할은 렌더링 값이며 영속 공고 데이터가 아니다. View는 전체 NoticeModel이나 저장소를 받지 않는다. 의미 있는 작은 기간·장소·출처 값과 immutable CalendarDraft는 State/내부 UI 입력에 사용한다.

NoticeModel은 title, aiDescription, targetUser, participationCondition, applicationInformation, schedules, location, benefits/issues, sourceURL/sources/evidence를 보존한다. aiDescription은 기존 검토 sample summary이며 provenance `reviewed_sample_summary`로 표시하고 새로운 AI 생성이라고 주장하지 않는다. 원본 출처 id/url/kind/checkedAt/access/note, 근거 sourceId/locator/fieldPath/sourceURL을 실제 디코딩한다. 모르는 URL은 null이며 사실을 만들지 않는다. 원본 rich JSON은 변경하지 않고 앱 표시 필드만 평탄화한다.

NoticeSnapshot/Reader는 App 경계에서 기존 번들을 두 독립 모델 목록으로 읽는 transport 조합이다. UI 입력이나 별도 공고 Entity가 아니다. 모델 생성자는 I/O/조회가 없는 순수 데이터다.

## 캐시·VM·관찰 수명

MainActivity가 Compose 밖에서 FavoritesState와 NoticeSession 및 IO 준비 콜백을 한 번 생성한다. Session은 공고/조직 Repository를 각각 공유하며 snapshot별 독립 source를 주입한다. source의 dictionary와 처음 비어 있는 ID cache는 별개다. find는 cache 확인 → miss 시 source 호출 → 성공만 저장한다. 누락 ID는 repository에서 캐시하지 않는다. Organization path는 parentId를 따라 visited ID로 순환을 중단하며 복구 가능한 부분 경로를 유지한다. find/path/replaceSource는 monitor로 동기화한다.

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

## Room 준비와 화면 게시

Room 2.8.5/KSP 2.3.12는 version catalog에서 관리한다. 실제 Maven 해결·현재 AGP9.1.1/built-in Kotlin2.2.10/JDK25 빌드로 호환성을 확인했다. [Room 공식 문서](https://developer.android.com/jetpack/androidx/releases/room)와 [비동기 조회 안내](https://developer.android.com/training/data-storage/room/async-queries)를 따른다. 추가 state/DI 라이브러리는 없다. `app/schemas/...NoticeCacheDatabase/1.json`은 생성된 DB v1 스키마다.

앱 DB는 `dearby-notice-cache-v1.db`다. 공고 행은 id/codecVersion/개별 payload, 조직 행은 id/name/parentId이며 whole-catalog blob이 아니다. NoticeStorageCodec v1은 모든 저장 모델 필드를 길이 구분 binary로 roundtrip하고 버전·잘림·trailing bytes·row/payload ID 불일치를 거부한다. L2와 mock L3의 반환 ID도 요청과 일치해야 한다. 모든 source/evidence/기간/복수 장소/좌표/provenance를 유지하며 조직 객체/경로는 공고 payload에 없다.

LaunchedEffect가 suspend Session.load를 호출한다. Dispatchers.IO에서 번들을 읽고 App 소유 DB를 열어 prepare한 뒤 finally에서 닫는다. Room 기본 main-thread 금지를 유지한다. DB open/read/corrupt/write/external 실패를 missing으로 바꾸지 않으며 파괴적 migration/reset을 사용하지 않는다. SQLite onCorruption도 파일 삭제 대신 오류를 전달한다. 실패하면 기존 메모리 snapshot을 유지하고 재시도 안내를 보여준다.

준비 트랜잭션의 임시 공고/조직 Repository는 각각 **L1 memory → L2 Room ID query → L3 snapshot mock** 순서로 읽는다. L2 miss 뒤 외부 성공은 upsert가 성공해야 임시 L1에 반환한다. 이 L1들은 준비 요청에만 존재하며 화면에 공개하지 않고 폐기한다. 같은 전체 hash+codec 재시작에서는 L2가 공급하여 외부 record fetch는 0이다(번들 hash/metadata 확인을 위한 읽기·해석은 여전히 수행한다).

manifest에는 전체 원문 hash와 모든 decoded fields를 포함한 fingerprint/codecVersion/날짜/순서 있는 공고·조직 ID 목록을 저장한다. 변경 시 두 테이블 invalidate → 모든 필요한 ID 재조회/upsert → manifest 교체를 **동일 Room transaction**에서 수행한다. 목록 삭제/이름/부모 변경을 함께 반영하고 중간 실패/취소는 이 트랜잭션 전체를 rollback한다. commit 성공 후에만 준비된 모델 목록이 Main으로 돌아와 화면용 memory-only 원본·관찰 revision을 교체한다. 따라서 화면용 L1 승격/게시 보장은 전체 transaction commit 뒤다. 화면용 Repository/VM은 Room에 접근하지 않고 준비된 목록에서만 읽으며 이 메모리는 Session 수명이다. 준비 단계의 임시 cache와 화면의 지속 cache를 혼동하지 않는다.

각 요청은 generation을 가지며 replaceSnapshot/새 load가 기존 요청을 무효화한다. IO 중 checkActive와 Main 게시 직전 generation/취소를 검사해 오래된 응답이 최신 snapshot을 덮지 않게 한다. CancellationException은 다시 던지고 취소된 effect의 finally가 새 로딩 상태를 덮지 않도록 활성 context를 확인한다. 실패 중 기존 화면 데이터는 유지되며 현재 bundle mock 이외 실제 API/네트워크는 없다.

## Shared 디자인 시스템

[디자인 시스템 API·native mapping·사용 규칙](docs/design-system/README.md)을 따른다. DearbyTheme은 API31+ dynamic color를 기본 사용하고 static light/dark로 opt out 가능하다. Typography/Shapes는 native 기본값이며 화면에서 semantic MaterialTheme 역할을 읽는다. 콘텐츠 간격만 Spacing으로 관리한다.

PrimaryButton/SecondaryButton은 Material3 Button/OutlinedButton에 enabled·modifier·content를 위임한다. 나머지 컨트롤은 직접 사용하며 NoticeCardSaveButton은 widget에서 저장 문구와 하트를 조합한다. 제목은 카드 안에 남긴다. navigation/tab/sheet와 domain 카드의 소유권은 유지한다.

Widget은 domain/widget 폴더에 Composable·State·ViewModel을 함께 둔다. ui/model 하위 폴더가 없으며 domain은 cross-widget 의존 예외가 아니다. 구조 검사는 @Composable 파일의 raw Model/VM/repository/OS 접근을 막고 비렌더링 VM의 하위 의존을 허용한다. 같은 domain의 다른 widget 금지 fixture도 있다.

## 검증 명령과 범위

```sh
cd apps/android
python3 scripts/check-fsd.py --self-test
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
ANDROID_SERIAL=emulator-5556 JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest
```

순수 테스트는 기존 favorites/VM/좌표/캘린더와 디스크 source의 조회 순서·호출수·실패/no promotion·잘못된 ID·payload 전체 필드·Session 최신 요청/취소를 검증한다. Room 계측은 unique test DB의 실제 close/reopen, 동일 hash external fetch0, 변경/삭제/reparent, SQL write failure·external failure·cancel rollback, payload/DB 손상 파일 보존을 검사한다. 기존 UI 흐름은 비동기 작업이 Compose idle 밖에 있으므로 실제 catalog.ready 게시를 waitUntil로 기다린다. 고정 sleep으로 로딩 완료를 가정하지 않는다.

기존 계측30/Lint 기록은 이전 작업 결과다. 사용자5554를 조작하지 않으며 전용5556과 unique test DB만 사용한다. 기존 테스트의 즐겨찾기는 백업/복원하고 실제 Calendar Save는 수행하지 않는다. 외부 지도 렌더링·캘린더 앱 내부 UI/저장·동기화·전체 접근성은 별도 미검증이다. 영속 캐시 TTL/암호화/실제 API·로그인·자동 추출은 이번 범위에 없다.


#1 병합 당시 검증(2026-09-14): FSD60파일/self-test24(금지18/허용6), JVM39, Debug·계측 APK 컴파일, Lint 오류0/경고12, 전용5556 계측35 모두 통과(실패/오류/skip0). 실제 DB5건과 기존 UI/저장/지도/캘린더30건을 함께 실행했다. 기록은 `build/room-final-build.log`, `build/room-instrumentation.log`, 표준 JVM/계측 XML·Lint 보고서이며 중간 버튼/widget/저장소 단계 결과와 구분한다. 전용5556은 검증 후 종료했고 사용자5554는 조작하지 않았다. canonical asset SHA256 `c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f` 유지, 실제 Calendar Save/외부 지도 앱 내부 화면은 검증하지 않았다.


#2 검증(2026-09-14): FSD66/self-test24, JVM39, Debug·Release·계측 APK 빌드, Lint 오류0/기존경고12, 전용5556 전체계측39 모두 통과. 신규 디자인 회귀4건을 포함한다. [before/after 화면·검증·한계](docs/design-system/VERIFICATION.md), [Shared API와 native 사용 규칙](docs/design-system/README.md)을 참고한다.

PR9 후속: 공고/조직 액션은 native icon button으로 줄이고 관심 조직명은 카드에 유지한다. NoticeScheduleState가 phase 이름·간결한 날짜·짧은 장소/전체 접근성 설명을 조립하며 신청과 활동 일정을 분리한다. 모델/캐시/캘린더 exporter는 그대로다. 상세한 API·정책은 디자인 시스템 문서의 후속 항목을 따른다.

PR9 아이콘/일정 후속 검증(2026-09-14): JVM46·전체계측39·FSD68/self-test24·Debug/Release/계측 APK·Lint 오류0/기존12 통과. [최신 화면과 실행 기록](docs/design-system/VERIFICATION.md#pr9-후속--아이콘-액션과-일정-위계-2026-09-14)을 참고한다.

### Calendar-style detail projection (PR9 후속)

NoticeDetail의 DetailPeriodState/DetailPlaceState 순수 projection이 날짜/시간과 장소명/주소·호실을 조립한다. Shared `DetailMetadata`는 primary/secondary/description/icon과 native action 슬롯만 받는다. 지도 액션은 phase의 원본 NoticeVenue를 전달하고 하단 장소 구획에는 중복되지 않은 원문 안내/미배치 venue만 남긴다. 카드/도메인 모델/cache/favorites/exporter 불변이며 상세의 초·소수초/미확인 경계도 보존한다. 참고와 이번 검증은 design-system/VERIFICATION.md의 2026-09-15 절에 분리 기록한다.
