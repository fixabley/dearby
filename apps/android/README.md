# Dearby Android

Kotlin + Jetpack Compose + Material 3 기반 네이티브 앱입니다.

| 항목 | 설정 |
| --- | --- |
| 앱 이름 | Dearby |
| Application ID / Namespace | `io.fixabley.dearby` |
| 최소 지원 | Android 12 (API 31) |
| Compile / Target SDK | 36 |
| 버전 | 1.0.0 (1) |
| Android Gradle Plugin / Gradle | 9.1.1 / 9.3.1 |
| Kotlin / Compose Compiler | 2.2.10 |
| Compose BOM | 2026.02.01 |

## 개발 환경

Android Studio에서 이 디렉터리(`apps/android`)를 엽니다.
SDK Manager에서 Android SDK Platform 36과 Build Tools 36.0.0을 설치합니다.
Gradle JDK는 Android Studio에 포함된 JDK를 사용합니다. 현재 프로젝트는 JDK 25에서
빌드를 검증하며, 앱 바이트코드는 Java 17을 대상으로 합니다.

SDK 경로는 Android Studio가 생성하는 `local.properties`에 설정하거나
`ANDROID_HOME` 환경 변수로 지정합니다. `local.properties`는 커밋하지 않습니다.

## 빌드와 검증

모노레포 루트에서 실행합니다.

```sh
cd apps/android
python3 scripts/check-fsd.py --self-test
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Debug APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.
Android 12 이상 에뮬레이터 또는 USB 디버깅 기기를 선택해 Android Studio에서 Run하거나,
연결된 기기에 `./gradlew :app:installDebug`로 설치합니다.

Release 빌드는 R8 코드·리소스 축소를 사용합니다. 배포용 서명 설정은 아직 없으며,
서명 키는 저장소에 커밋하지 않습니다.

JVM 단위 테스트는 앱 실행 없이 임시 저장소로 즐겨찾기 추가·중복·삭제·복원과 여러 소비자의 상태 일관성을 검증합니다.
전용 에뮬레이터를 지정해 `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest`를 실행하면 더블탭,
세로 넘김, 조직 중복 방지, Activity 재생성 후 저장 유지·삭제, 버튼·탭·연결 상세와 실제 저장/카탈로그 공급을 검증합니다.
`emulator-5556`은 예시이며 실제 전용 기기 serial을 확인합니다. 사용자 즐겨찾기가 있는 `emulator-5554`에는 실행하지 않습니다.
흐름 테스트는 기존 즐겨찾기를 백업/복원하지만 실행 중 값을 변경하므로 전용 개발 기기만 사용합니다.

## 구조와 사용 흐름

공고는 `entities/notice/NoticeModel`, 조직은 `entities/organization/OrganizationModel`로 독립 관리합니다(실제 파일은 각 model segment). App의 NoticeSession이 두 lazy cache-aside 저장소와 카드/상세 ViewModel 수명을 공유합니다. ViewModel이 조직 ID를 해석해 immutable State를 만들고 UI는 State와 콜백만 받습니다. 즐겨찾기는 기존 FavoritesState 하나를 관찰하여 다른 탭에서 삭제해도 기존 카드의 saved가 갱신됩니다.

- `app/`, `app/data/`: 앱 조립·라우팅·OS 어댑터·번들 transport
- `pages/discovery/ui/`, `pages/favorites/ui/`: State 목록 표시
- `pages/noticedetail/model/`, `ui/`: NoticeDetailViewModel → NoticeDetailState → 상세 시트
- `widgets/notice/noticecard/`: NoticeCardViewModel → NoticeCardState → 카드
- `widgets/organization/favoriteorganizationcard/`: 조직 카드의 표시 조합과 삭제 콜백
- `entities/notice/`, `entities/organization/`: 독립 model/api, 서로 참조하지 않음
- `features/favoriteorganization/`, `features/addtocalendar/`: 공유 저장 상태·순수 캘린더 초안
- `shared/ui/`: 범용 표시·테마

실제 트리·진입점·캐시 교체/관찰 수명·새 기능 배치·검사 한계는 [ARCHITECTURE.md](ARCHITECTURE.md)를 참고합니다. 앱 안에 원시/상세 공고 Entity를 중복 보관하지 않으며 조직 이름/경로는 State의 표시값입니다. 공고에는 선택 조직 및 명시적 맥락 역할의 ID만 있습니다. 출처/필드별 근거도 실제 디코딩합니다. 기존 summary는 검토 요약이며 새 AI 생성이라고 표시하지 않습니다.

공고를 세로로 넘기고 더블탭/버튼으로 조직을 저장합니다. 즐겨찾기는 연결 공고와 분류·학교를 표시하며 관심 대상·부모·학교 역할을 구분합니다. 상세의 지도 버튼은 유효한 좌표가 있는 각 장소에만 표시합니다. 신청/활동 단계의 캘린더 버튼은 알려진 날짜로 OS 편집기를 열며 메모에는 검증된 원본 URL 하나만 전달합니다. 원본이 없거나 유효하지 않으면 메모는 비우며 신청/온라인 URL로 대체하지 않습니다. 온라인 단계에 다른 단계의 오프라인 장소를 붙이지 않습니다. 사용자가 편집·저장/취소하며 앱은 저장 완료로 간주하지 않습니다. 권한/현재 위치/직접 일정 저장/외부 라이브러리는 추가하지 않습니다.

기존 JSON `activities`·asset `activity-samples.json`·ID·저장 키·test tag·Android Activity 이름은 호환성을 위해 유지합니다. 공통 계약은 [PR #6](https://github.com/fixabley/dearby/pull/6), 작업은 [이슈 #1](https://github.com/fixabley/dearby/issues/1)과 [설계 #3](https://github.com/fixabley/dearby/pull/3)을 참조합니다.

## 영속 캐시와 재시도

Room 2.8.5/KSP 2.3.12를 사용합니다. IO 준비 단계에서 독립 공고/조직 L1→Room ID 조회→번들 mock을 사용하며 개별 공고 payload와 별도 조직 레코드를 저장합니다. 전체 내용 hash/codec/목록 manifest가 바뀌면 양쪽 cache와 metadata를 한 transaction으로 교체합니다. 같은 hash 재실행은 L2에서 읽으며 외부 record fetch가 없습니다. 화면 VM은 준비된 메모리만 읽습니다.

DB/원본/쓰기 오류는 기존 snapshot을 유지하고 재시도하도록 전달합니다. 손상 파일을 자동 삭제하거나 destructive migration하지 않습니다. 취소/새 요청이 이전 응답의 게시를 막습니다. 즐겨찾기 Prefs는 이 DB와 별개이며 기존 키/ID를 유지합니다. 자세한 수명·한계·Room schema/public API는 [ARCHITECTURE.md](ARCHITECTURE.md)를 참고합니다.

[Shared/UI 디자인 시스템](docs/design-system/README.md)은 native Material3 theme·간격·범용 표시를 제공한다. Primary/Secondary 버튼은 Material3에 위임하고 저장 문구·하트는 NoticeCardSaveButton이 소유한다. widget의 flat View/State/VM 구조를 유지한다.

기기 테스트는 사용자5554가 아닌 전용5556에서 uniqueDB로 수행하며 실제 캘린더 저장/외부 지도 화면은 검증하지 않습니다.


#1 병합 당시 검증(2026-09-14): FSD60파일/self-test24(금지18/허용6), JVM39, Debug·계측 APK 컴파일, Lint 오류0/경고12, 전용5556 계측35 모두 통과(실패/오류/skip0). 실제 DB5건과 기존 UI/저장/지도/캘린더30건을 함께 실행했다. 기록은 `build/room-final-build.log`, `build/room-instrumentation.log`, 표준 JVM/계측 XML·Lint 보고서이며 중간 버튼/widget/저장소 단계 결과와 구분한다. 전용5556은 검증 후 종료했고 사용자5554는 조작하지 않았다. canonical asset SHA256 `c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f` 유지, 실제 Calendar Save/외부 지도 앱 내부 화면은 검증하지 않았다.


#2 검증(2026-09-14): FSD66/self-test24, JVM39, Debug·Release·계측 APK 빌드, Lint 오류0/기존경고12, 전용5556 전체계측39 모두 통과. 신규 디자인 회귀4건을 포함한다. [before/after 화면·검증·한계](docs/design-system/VERIFICATION.md), [Shared API와 native 사용 규칙](docs/design-system/README.md)을 참고한다.

PR9 아이콘/일정 후속 검증(2026-09-14): JVM46·전체계측39·FSD68/self-test24·Debug/Release/계측 APK·Lint 오류0/기존12 통과. [최신 화면과 실행 기록](docs/design-system/VERIFICATION.md#pr9-후속--아이콘-액션과-일정-위계-2026-09-14)을 참고한다.
