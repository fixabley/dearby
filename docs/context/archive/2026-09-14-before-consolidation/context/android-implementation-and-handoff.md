# 최신 조율 상황 — 2026-09-14

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

GitHub #1 구조·상태 관리 리팩터링과 #2 네이티브 UI 정비를 생성했다. #2는 #1 이후다. 현재는 이슈 등록과 컨텍스트 저장까지이며 구현은 아직 배정하지 않았다. #1은 기존 화면·동작 유지, 기능과 책임의 플랫폼 간 정합성, 플랫폼에 맞는 상태 관리와 저장 책임 분리를 요구한다.

아래는 기존 플랫폼 담당 세션의 초기 인계 원문이다. 링크의 상대 경로는 docs/workstreams와 같은 깊이여서 이 폴더에서도 유지된다. 원문의 임시 작업 제한과 검증 시점을 최신 구현 결과로 오해하지 않는다.

---

# Android 세션 인계

2026-09-14 작성. **초기 인계 문서 작성 완료 · coordinator 검토 및 다음 지시 대기** 상태다. 새 기능 구현은 요청되지 않았으며 임의로 후속 개발을 시작하지 않는다.

| 항목 | 현재 값 |
| --- | --- |
| Worktree | `/Users/jominjun/Documents/dearby/dearby-android` |
| 브랜치 | `fixabley/dearby-android` |
| 기준 / 확인한 HEAD | `main` 기준 `79ac281` (`79ac2816e60154d4f48d1ad59c8e1f593d8ee9bc`) |
| 플랫폼 담당 | `apps/android/` |
| 이번 수정 허용 파일 | `docs/workstreams/android.md` 하나 |
| Android 세션 | `term_8bbb1e33-2874-45e5-851b-35f2a8768ad8` |
| Coordinator 세션 | `term_bc7eff15-d763-403b-a1ef-433a57b11f00` |

API·Android·iOS는 각각의 worktree와 Orca 세션에서 개발하며 coordinator가 진행과 공통 변경을 조율한다. 이번 인계에서는 소스·SDK 설정을 수정하지 않고 커밋·푸시도 하지 않는다.

## 담당 경로와 구현 현황

Kotlin + Jetpack Compose + Material 3 앱이다. 최소 Android 12/API 31, compile/target SDK 36, Java 바이트코드 대상 17이다. 모노레포의 다른 플랫폼은 SwiftUI iOS 26+와 NestJS API이며 이 세션의 수정 범위가 아니다.

| 경로 | 역할 / 현재 구현 |
| --- | --- |
| [Android README](../../apps/android/README.md) | 환경·빌드·검증 방법 |
| [MainActivity.kt](../../apps/android/app/src/main/java/io/fixabley/dearby/MainActivity.kt) | 앱 진입점 |
| [DiscoveryScreen.kt](../../apps/android/app/src/main/java/io/fixabley/dearby/discovery/DiscoveryScreen.kt) | 발견·즐겨찾기 하단 Material 3 NavigationBar, 세로 카드 넘김, 더블탭/버튼 저장, 상세 시트, 목록·삭제·빈 상태·로딩 실패 안내 |
| [ActivityCatalog.kt](../../apps/android/app/src/main/java/io/fixabley/dearby/discovery/ActivityCatalog.kt) | 번들 JSON 로딩, 조직 경로·분류·학교 맥락·회차 모델, SharedPreferences 저장 |
| [NoticeIdentity.kt](../../apps/android/app/src/main/java/io/fixabley/dearby/discovery/NoticeIdentity.kt) | 상세의 관심 조직·상위 조직·활동 분류·역할별 맥락 기관·회차 표시 |
| [activity-samples.json](../../apps/android/app/src/main/assets/activity-samples.json) | 공통 샘플의 Android 복사본 |
| [DiscoveryFlowTest.kt](../../apps/android/app/src/androidTest/java/io/fixabley/dearby/DiscoveryFlowTest.kt) | 기존 Compose 계측 테스트 3건 |
| `apps/android/app/src/main/res/`, `ui/theme/`, Gradle 설정 | Android 리소스·테마·빌드 설정 |

검토된 공고 5건 중 학사 행정 1건을 제외한 4건을 피드에 표시한다. 카드는 분류와 행사 관련 학교를, 상세는 요약·대상·조건·기간·일정·장소·혜택·확인 사항·원문 링크를 제공한다. 즐겨찾기는 조직 경로와 연결 공고 수, 공고별 분류·학교를 표시한다.

저장 키는 조직 ID다. 반복 저장은 중복이나 해제를 만들지 않으며 목록에서 삭제한다. `dearby.favorites.v1` SharedPreferences의 `organizationIDs`에 저장하고 Activity 재생성 후 다시 읽는다. 기존에 저장된 조직의 연결 공고가 없어도 항목과 빈 상태 안내를 유지한다.

## 공통 제품 의미와 기준 자료

- [반복 01과 기존 검증 기록](../product/iteration-01.md)
- [활동 공고 규격 v1](../product/activity-data-v1.md)
- [관심 대상 선정 규칙](../product/interest-target-rules.md)
- [샘플 원본](../../shared/contracts/activities/sample.json), [스키마](../../shared/contracts/activities/schema.json), [승인 규칙](../../shared/contracts/activities/target-rules.json)

`parentOrganizationId`는 조직 상하위 관계, `categoryPath`는 활동 분류, `contexts`는 이번 행사 학교 등의 맥락, `favoriteOrganizationId`는 실제 저장 대상이다. 이 관계들을 서로 대신 사용하지 않는다. 연도·회차마다 공고 ID는 달라져도 지속 조직 ID는 유지한다.

한국농어촌공사와 DB손해보험 공고는 각각 해당 기업을 저장한다. 충북대학교는 행사 맥락이며 기업의 부모나 재학생 전용 조건이 아니다. 대학일자리센터의 운영 역할은 저장 대상과 별개다. 영남권 대회는 사용자 지정에 따라 교육원 아래 지속 대회 프로그램을 저장하고 개별 공고는 제2회로 표시한다. 교육원의 원문 문의처 역할을 단독 주관으로 바꾸지 않으며 상위 조직까지 자동 저장하지 않는다. 기존 운영부서 즐겨찾기를 기업으로 강제 변환하지 않는다.

## 실행·검증 명령

아래는 **향후 실행할 때 참고할 기존 명령**이며 이번 인계에서는 실행하지 않았다. Android README 기준 환경은 JDK 25, Android SDK Platform 36, Build Tools 36.0.0이다. 현재 checkout에는 `apps/android/local.properties`가 없다. 루트 [.gitignore](../../.gitignore)의 `local.properties` 제외 규칙을 확인했으며 SDK 경로나 환경 변수를 변경하지 않았다. 실제 SDK/JDK 준비 여부는 이번에 검증하지 않았다.

```sh
# 이 worktree 루트에서 Android 빌드·Lint
cd apps/android
./gradlew :app:assembleDebug :app:lintDebug

# Android 12 이상 전용 개발 기기에 설치
./gradlew :app:installDebug

# 연결된 전용 개발 기기에서 기존 계측 테스트 실행
./gradlew :app:connectedDebugAndroidTest
```

Debug APK 경로는 `apps/android/app/build/outputs/apk/debug/app-debug.apk`다. 계측 테스트는 실행 기기의 Dearby 즐겨찾기를 초기화하므로 전용 개발 기기를 사용한다. 설치·기기 실행은 다음 작업 범위가 정해진 뒤 수행한다.

공통 검증 참고 명령은 worktree 루트의 `python3 scripts/sync-activity-samples.py --check`와 `npm test`다. 이번에는 실행하지 않았다. `--check` 없는 동기화는 두 앱 리소스를 쓰므로 이 세션에서 임의로 실행하지 않는다.

## 기존 검증과 이번 확인의 구분

**기존 기록:** [반복 01의 Android 화면 반영](../product/iteration-01.md#android-화면-반영)에 Debug 빌드·Lint 및 API 36.1 에뮬레이터 계측 테스트 3건 통과가 기록되어 있다. 테스트 소스의 세 항목은 다음과 같다.

1. `careerDetailSeparatesInterestTargetFromEventSchool`: 채용 분류, 관심 기업과 행사 학교 분리.
2. `competitionDetailShowsParentAndEdition`: 세로 넘김 후 대회 상위 교육원과 제2회 표시.
3. `doubleTapSavesDistinctSubjectsAndSurvivesActivityRecreation`: 더블탭·세로 넘김, 두 기업 독립 저장·중복 방지, Activity 재생성 후 유지·삭제.

제품 문서에는 공통 데이터 테스트 13건 통과도 기록되어 있다. 이는 기존 문서의 결과를 인계한 것으로, 현재 worktree에서 새로 재현한 성공 결과가 아니다. 하단 NavigationBar는 제품 문서와 현재 소스에서 확인했으며 이번에 화면을 실행하지 않았다.

**이번 확인:** 시작 시 변경 없는 작업 트리와 HEAD·브랜치를 확인하고 제품 문서, 샘플·스키마·규칙, Android 소스·테스트·Gradle 설정을 읽었다. Python 읽기 전용 비교로 Android 샘플과 공통 원본의 바이트 일치를 확인했다. SDK 로컬 설정 부재와 ignore 규칙을 확인했다. 인계 파일 생성 후 Git diff와 상태를 확인하여 변경 파일을 이 문서 하나로 제한했다. 빌드·Lint·테스트·설치·에뮬레이터 조작은 수행하지 않았다.

## 알려진 한계와 다음 지시

현재 데이터는 수동 검토한 고정 샘플이며 실시간 모집 현황이나 개인화 추천이 아니다. 원문 자동 추출·자동 수집, API 연결, 로그인·기기 간 동기화, 자동 자격 판정, Q&A·커피챗·Dearby 크레딧은 미구현이다. 공통 관심 대상 규칙 엔진은 승인 사례 기반 제안 단계이며 원문 추출기가 아니다. 외부 CIEAT 마일리지를 Dearby 크레딧으로 환산하지 않는다.

전체 조직 트리 탐색과 운영자 검토 UI는 미구현이다. TalkBack, 최대 글자 크기, 태블릿·가로 화면의 전체 검증은 남아 있고 배포용 서명 설정도 없다. 이 목록은 기존 한계를 기록한 것이며 새 기능 요청이나 착수 계획이 아니다.

다음 지시를 기다린다. 공통 명세·샘플·규칙·루트 문서 또는 다른 플랫폼 변경이 필요하면 변경 이유, 관련 경로, 제안 내용, Android와 API/iOS 영향 및 필요한 검증을 coordinator에 Orca CLI로 전달한다. 현재 Dispatch 중 질문은 live preamble의 `orca orchestration ask`를 사용하고 완료 후에는 종료된 Task/Dispatch ID를 재사용하지 않는다. Coordinator가 공통 변경 담당과 적용 순서를 정하기 전에는 해당 파일을 직접 수정하거나 다른 worktree에 쓰지 않는다. 커밋·푸시와 후속 개발은 별도 지시를 따른다.

## 최신 상태 — Seed v2 확정 및 이슈 반영

사용자가 A·B·C와 D·E·F를 모두 채택하여 Seed v2를 확정했다. GitHub #1 본문을 갱신하고 원격 본문과 로컬 문서의 일치를 확인했다. 목표는 유지하며 재사용 UI의 저장소 접근 금지, 교체 가능한 데이터 공급 경계(네트워크 미구현), 즐겨찾기 상태 단일 소유, 새 외부 상태 관리·DI 라이브러리 및 빌드 모듈 분할 제외를 제약에 추가했다. 디렉터리 트리·파일 배치 예시 및 앱 실행 없는 임시 저장소 테스트를 완료 기준에 추가했다.

원본 Seed와 채택 이력은 .symposium/scratch/evolve-step.md, 정본 Seed는 .symposium/scratch/socrates.md. docs/context/symposium-seed-evolution.md에도 스냅샷을 저장했다. 이전 선택 대기 기록은 과거 이력이다. 구현 진행 요청은 유효하지만 이번 Seed 갱신 시점에는 코드 수정·Orca 구현 배정·커밋·푸시는 수행하지 않았다. 다음 구현은 갱신한 #1을 기준으로 기존 담당 세션에서 진행한다. #2 외형 변경은 이후다.

## 완료한 Android 담당의 최종 보고

# Android #1 Seed v2 구현 인계 — draft PR 검토 대기

2026-09-14 최종 확인. 사용자의 A~F 승인 범위를 실제 Android 코드에 적용하고 Android만 포함한 main 대상 draft PR을 생성했다. 자동 merge하지 않았으며 coordinator 검토 및 다음 지시를 기다린다. 세션은 유지한다.

| 항목 | 현재 값 |
| --- | --- |
| 담당 | `apps/android/` |
| Worktree | `/Users/jominjun/Documents/dearby/dearby-android` |
| 브랜치 | `fixabley/dearby-android` (동일 원격 브랜치 push 완료) |
| 시작 기준 | `main`의 `79ac281` |
| 커밋 | `3c8d2c5264352f0cde1cfc6fe93af5e1b998b74a` |
| Draft PR | https://github.com/fixabley/dearby/pull/4 |
| 관련 이슈 / 설계 | https://github.com/fixabley/dearby/issues/1 / https://github.com/fixabley/dearby/pull/3 |
| Android 세션 | `term_8bbb1e33-2874-45e5-851b-35f2a8768ad8` |
| Coordinator | `term_bc7eff15-d763-403b-a1ef-433a57b11f00` |

## 적용 내용

기존 discovery 패키지의 화면·모델·로딩·저장을 `app`, `feature/discovery`, `feature/favorites`, `feature/noticedetail`, `core/model`, `core/state`, `core/data`, `core/ui`로 나눴다. MainActivity가 실제 공급자와 저장소를 조립하고 FavoritesState를 단일 소유하며 루트가 각 화면에 읽기 데이터와 이벤트 콜백을 전달한다. UI는 저장소나 상태 소유자를 직접 만들지 않는다.

CatalogProvider/AssetCatalogProvider, FavoriteStore/SharedPreferencesFavoriteStore 경계를 적용했다. 조직 ID 및 `dearby.favorites.v1` / `organizationIDs` StringSet 형식과 apply 저장을 보존했다. 반복 저장은 추가, 삭제는 명시적 동작이며 부모/학교 자동 저장과 기존 운영부서 강제 치환은 없다. 기존 하단 Material 3 NavigationBar, 문구·제스처·탭·상세 구성은 유지했다.

실제 파일 트리, 상태 수명, 의존 방향, 새 기능 배치 예시는 [앱 ARCHITECTURE.md](../../apps/android/ARCHITECTURE.md), 실행 명령은 [Android README](../../apps/android/README.md)에 있다. 공통 제품 의미는 [활동 규격](../product/activity-data-v1.md)과 [관심 대상 규칙](../product/interest-target-rules.md)을 따른다.

## 이번에 실행한 검증

Android Studio JDK 25, SDK `/Users/jominjun/Library/Android/sdk`, `apps/android`에서 실행했다.

- `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`: JVM 5건 통과, Debug 빌드 성공; 최종 Lint 오류 0/권고 11건.
- `./gradlew :app:assembleDebugAndroidTest`: 계측 APK 빌드 성공.
- `ANDROID_SERIAL=emulator-5556 ./gradlew :app:connectedDebugAndroidTest`: API 36.1 전용 Dearby_Architecture_Test에서 7건 통과.
- JVM: 임시 저장소의 기존 ID 복원, 추가/중복/삭제/재생성, 부모·학교 비저장, 두 Compose 관찰 소비자의 일관성, 읽기 스냅샷 검증.
- 계측: 기존 3건을 변경 없이 유지하고 현재 하단 NavigationBar에서 재실행; 버튼/탭/연결 상세 1건, 실제 SharedPreferences XML 및 번들 디코딩 2건, 공급 교체·실패/재시도·루트 상태 유지 1건 추가.
- Cold-start smoke: 계측 종료 후 Debug APK를 전용 기기에 재설치하고 기존 XML에 krc/cbnu-career를 넣어 PID 7756→7826의 두 앱 재실행에서 KRC 저장 표시와 두 ID 보존 확인.
- 한국어 UI 문자열 집합 동일, Android 샘플과 공통 원본 바이트 일치, feature/core UI의 저장소·상태 객체 의존 부재, 문서 링크 및 git diff --check 확인.

첫 계측 실행은 새 상세 테스트가 배경 목록과 시트의 같은 기업명을 함께 찾아 1건 실패했다. 상세 시트 하위로 matcher 범위를 좁힌 뒤 전체 7건 재실행 성공했다. 과거 계측 3건은 하단 탭 변경 전 기록이었으며, 위 결과는 이번 커밋에서 직접 실행한 결과다.

검사 증거(로컬 build 산출물, Git 제외):

- `apps/android/app/build/test-results/testDebugUnitTest/TEST-io.fixabley.dearby.core.state.FavoritesStateTest.xml`
- `apps/android/app/build/outputs/androidTest-results/connected/debug/TEST-Dearby_Architecture_Test(AVD) - 16-_app-.xml`
- `apps/android/app/build/reports/lint-results-debug.html`
- `apps/android/build/cold-start-result.txt`, `cold-start-1.xml`, `cold-start-2.xml`

## 경계·환경·남은 한계

전용 AVD는 checkout의 `apps/android/build/avd/` 아래 만들었으며 검증 후 emulator-5556만 종료했다. 사용자 emulator-5554는 설치·초기화·테스트·종료하지 않았고 마지막 adb 조회에서도 유지됨을 확인했다. local.properties는 자기 checkout에서 설정했고 ignore 상태로 커밋하지 않았다.

원격 PR의 draft=true, base=main, head=fixabley/dearby-android, head 커밋 일치와 24개 변경 파일이 전부 apps/android 아래임을 확인했다. 원문 body 파일은 `apps/android/build/pr-body.md`에 있으며 Related #1 / 설계 참고 #3을 사용했다. 기존 untracked AGENTS.md와 docs/workstreams, 자기 역할 docs/context는 로컬 인계용으로 남기고 PR에서 제외했다. 다른 플랫폼·공통 규격·루트 설정은 수정하지 않았다.

현재 공급자는 동기식 번들용이다. 네트워크·API·로그인·기기 간 동기화와 #2 UI 정비는 구현하지 않았다. TalkBack·최대 글자 크기·태블릿·가로 화면 전체 검증과 Lint 권고 11건은 남아 있다. 새 외부 상태관리/DI 및 빌드 모듈을 추가하지 않았다(JUnit은 테스트 의존성).

다음은 coordinator의 PR 검토다. 공통 명세나 다른 플랫폼 변경이 필요하면 이유·경로·영향·검증 계획을 coordinator와 Orca CLI로 조율하고 다른 checkout에 쓰지 않는다. 이번 Dispatch 완료 후 그 Task/Dispatch ID를 재사용하거나 임의 후속 작업을 시작하지 않는다. 재개 시 Git/Orca 런타임과 새 지시를 다시 확인한다.

## FSD 최종 담당 보고

# Android FSD 구현 완료 — PR #4 리뷰 대기

2026-09-14 최종 확인. `/Users/jominjun/Documents/dearby/dearby-android`, branch `fixabley/dearby-android`, 담당 `apps/android`. 최신 메인 AGENTS/FSD 원칙에 따라 실제 코드를 적용했다. 기존 게시 커밋3c8d2c5를 재작성하지 않고 후속 커밋을 push했다. 자동 merge 없이 coordinator 검토와 다음 지시를 기다리며 세션을 유지한다.

PR: https://github.com/fixabley/dearby/pull/4 (draft, base main)
최종 HEAD: `26c517e19f86b7b563a57310e75e6e2b2ff0b1f7`
Related #1, 설계 참고 #3. 원격 제목/본문·head·파일 범위를 확인했다.

## 기능별 후속 커밋

- `484c7a5d16d41fbacda167315c277d47a7a5b9ce`: 카드들이 공유할 카탈로그·즐겨찾기 경계 선행 이동, 기존 테스트 참조/패키지와 문서 함께 수정, JVM5/Debug/계측APK 통과.
- `493fd6cb9073afeb08d018459de9041de5d59744`: 공고 카드 ActivityCard widget·발견 page·entity 분류 UI, 독립 위젯 테스트1건 및 문서.
- `da96a9cb5616afbc09732bd4ba5e7a32797fad6b`: 실제 FavoriteOrganizationCard 추출·즐겨찾기 page, 연결 공고 콜백/공고 없는 기존 조직 테스트2건 및 문서.
- `26c517e19f86b7b563a57310e75e6e2b2ff0b1f7`: 상세 page·App 라우팅 경계, back/탭 복귀 회귀 및 구조 검사·최종 FSD 문서.

## 실제 구조와 계약

App이 의존성 조립·라우팅·단일 FavoritesState를 소유한다. MainActivity는 기존 manifest 컴포넌트 이름을 보존하는 App 진입점 예외이며 DearbyApp이 Pages를 연결한다. Pages discovery/favorites/noticedetail은 서로 참조하지 않고 목적지 콜백만 받는다. Widgets activitycard/favoriteorganizationcard는 서로 참조하지 않고 데이터와 이벤트 콜백만 받는다.

Features favoriteorganization의 model/api가 상태·저장을 소유한다. Entities activitycatalog는 서로 연결된 공고·조직·맥락·출처·공급·분류 UI의 단일 slice다. Shared는 범용 정보 표시·테마다. 기존 UI/문구/제스처/탭/sheet/back/조직ID/저장키/형식/샘플을 유지했다. API·네트워크·외형#2·새 DI/상태관리/빌드모듈은 추가하지 않았다.

문서: [앱 ARCHITECTURE.md](../../apps/android/ARCHITECTURE.md), [README](../../apps/android/README.md), [구조 검사](../../apps/android/scripts/check-fsd.py). 실제 트리·slice 진입점·상태 생명주기·방향·새 기능 예시와 검사 한계를 기록했다.

## 이번 실행 결과

JDK25 / SDK36 / API36.1 전용 Dearby_Architecture_Test emulator-5556:

- `python3 apps/android/scripts/check-fsd.py --self-test`: main Kotlin17개 통과, 금지 참조8건 거절/허용3건 통과. 상향·페이지간·위젯간·state/storage 직접 참조·비진입점 helper·완전한 패키지 이름/별칭 사례를 검사했다.
- `:app:testDebugUnitTest`: JVM5건 통과, 임시 저장소 상태·중복/삭제/복원·여러 소비자 관찰 일관성.
- `:app:assembleDebug` 및 계측APK 컴파일 성공.
- `:app:lintDebug`: 오류0 / 권고11(버전·KTX·카탈로그 권고).
- `ANDROID_SERIAL=emulator-5556 :app:connectedDebugAndroidTest`: 총11건 통과, 실패/오류/skip0. 기존7 + 공고카드1 + 조직카드2 + 상세back1; 실제 저장 복원과 발견/즐겨찾기 상세 왕복 포함.
- 한국어 문자열 집합·샘플 JSON의 이전 코드와 일치, 문서 링크, git diff --check 확인.

처음 공통 경계 이동 시 fun interface의 modifier 순서 컴파일 오류를 수정한 뒤 통과했다. 이번 계측은 모두 통과했다. 이전 3c8d2c5의 별도 cold-start smoke는 반복하지 않았고 현재 검증으로 주장하지 않는다. 현재 실제 SharedPreferences/Activity 재생성 회귀는 위 계측에 포함한다.

증거(Git 제외): `apps/android/build/fsd-foundation.log`, `fsd-activitycard.log`, `fsd-favoritecard.log`, `fsd-final.log`; JVM `apps/android/app/build/test-results/testDebugUnitTest/TEST-io.fixabley.dearby.features.favoriteorganization.model.FavoritesStateTest.xml`; 계측 `apps/android/app/build/outputs/androidTest-results/connected/debug/`; Lint `apps/android/app/build/reports/lint-results-debug.html`.

## 소유 경계·남은 한계·다음 행동

PR의 30개 변경 파일은 모두 apps/android 아래다. 자기 docs/workstreams/context는 로컬 인계용으로 갱신하고 PR에 넣지 않았다. 기존 untracked AGENTS.md는 보존했다. 공통 문서·다른checkout·API/iOS·루트 설정은 수정하지 않았다. build/local.properties는 ignore 상태다. 별도5556만 실행 후 종료했고 사용자5554는 설치·초기화·테스트·종료하지 않았으며 최종 adb 조회에서도 유지했다.

Kotlin 단일모듈 internal은 slice별 compiler 경계가 아니다. 진입점 계약과 간단한 import/FQ-name 검사로 보완하며 전체 Kotlin 타입해석·reflection·문자열/interpolation·생성/테스트 소스·간접 참조의 완전성을 증명하지 않는다. 스크립트는 Python 표준 라이브러리만 사용하며 과한 parser를 도입하지 않았다. TalkBack·최대 글자·태블릿·가로화면 전체 검증 및 비동기 공급·네트워크는 범위 밖이다.

다음은 coordinator의 PR 검토다. 공통/타플랫폼 변경이 필요하면 Orca CLI로 조율한다. 완료된 Task/Dispatch ID를 재사용하거나 후속 작업을 임의 시작하지 않는다. 새 지시로 재개할 때 Git/Orca 상태를 다시 확인한다.

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

Android 신청 aa7a6db, 활동6e5ad55. 증거 dearby-android/apps/android/build/calendar-final-verified.log 및 표준 XML. 담당 worktree 역할 문서가 상세 원본이다.


## 2026-09-14 ActivityDetail 완료
Android 완료 PR4 b8838e2. 상세 projection과 조직 source/cache App 소유. JVM24/계측30/FSD41/self16/Debug/Lint 오류0. dearby-android/apps/android/build/activity-detail-final.log와 해당 checkout 역할 문서에 증거. ctx_0710ea32dce0 retained.


## 2026-09-14 Notice 네이밍 최종 완료
공고 domain: ActivityNotice→Notice, ActivityDetail→NoticeDetail, ActivityCatalog→NoticeCatalog, ActivityNoticeSummary→NoticeSummary, 관련 Activity접두 모델/저장소/helper 및 Entities/NoticeCatalog/Android entities.noticecatalog. ActivityCard→NoticeCard, Widgets/NoticeCard/widgets.noticecard. iOS 순수 생성자+저장소/cache동작 보존, Android 구조동작동일. Android MainActivity/ComponentActivity/ActivityNotFoundException 등 플랫폼, 기존 JSON activities/resourceactivity-samples.json/sharedcontracts경로/IDs/testtags/과거증거파일명은 호환예외로 유지. 이전 domain별칭없음.
PR5 iOS 도메인670bd8be201a222fd6f6e83ca9a2490987948963 / 카드eb6b25fac7b983a17e5a7c99a025ca5a30d67912. PR4 Android 도메인f2799c9 / 카드bfee6dc1d345d9b2de56b3d3775ce80e0819e67a. Root PR6 product c07b0191de4cc61395abf9e1e60704feb0b33d9e, PR3 architecture d493fd1120a01ada0dfd09ad74e6685879ed08ea. 원격 모두확인/draft/미병합. root docsbranch잠깐수정후 feat/activity-location-contract복귀, 기존미커밋변경보존.
검증: iOS 상세old/new·조직·즐겨찾기·지도·캘린더old/new standalone PASS, 최종FSD45+fixtures 및 Simulator build11:37:31Z PASS. Android JVM24fail0, FSD41/self16, Debug/계측APK컴파일/Lint오류0권고12 PASS. 지정rename과 Kotlin60파일일치/비코틀린자원불변 검사. Main구조검사/rg이전domain참조감사/AndroidXML및로그/원격head/샘플hash직접확인. 샘플SHA c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f root/양앱동일. 이름만변경이므로기기계측/외부지도캘린더이번재실행없음.
run_94fec7f4f195 iOS task_86eb10324b73/ctx_ae0df4320b17 succeededretain completiondelivery_1f3f1653a199ack, Android task_d1b7b6178550/ctx_99a1185ce5e5 succeededretain delivery_b086a7f5a689ack, reclaimable0. 세션유지/API변경없음/활성작업없음. 다음은새사용자요청. 코드검색재개시 Notice경로를 사용한다.


## 2026-09-14 Android UI·Room 최종 검증 체크포인트
PR4 원격 head8572ecddd93e92260114b1b988b2595d9b02c700 확인. 기능커밋66e5f3c 공통버튼,66f88f1 도메인widget동위배치,996f88c 조직Room,0a7e6d4 공고codec/source,695a0ae 하트표시,8572ecd App Roomtransaction/async연결. Root XML직접집계 JVM39/계측35 실패오류skip0, FSD60/fixtures24 통과, build/room-final-build.log 빌드/Lint성공·Lint오류0경고12 worker문서확인. canonical root/iOS/Android SHA c649b0a1d898497adf9bd4e2363c5753a1eecf996a7467e604dadaaee4a9e95f 동일. 실제 uniqueDB 재개/외부0/rollback/손상보존, 기존UI30+DB5 확인. 실제Calendar Save/외부Maps내부화면 미검증. 스토어transaction임시L1후 UI memoryonlySession으로Main게시, Room2.8.5/KSP2.3.12; 실제API 없음. root 계약 PR6 d97543b push·본문갱신 완료. ctx15dee worker_done 및retain정리만 대기.

최종 완료: run_620743435f4b/task_cc58819a0e23/ctx_15dee1ad27be succeeded 및 retained. delivery_b91f5f7c7bc2 완료보고검증·ack, reclaimable0 확인. PR4 head8572ecd, PR6 d97543b. root최종검토메시지는 이미완료된dispatch여서 inactive반환(작업실패 아님); 신규followup없음. 다음사용자지시 대기.
