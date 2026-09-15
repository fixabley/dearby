# Android 세션 인계

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](../context/coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

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
