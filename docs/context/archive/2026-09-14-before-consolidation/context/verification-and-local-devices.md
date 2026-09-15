# 검증 역할 — 기존 결과와 로컬 기기

> **최신 상태 — 2026-09-14:** 구조 개선 PR #3·#4·#5·#6은 main에 병합했고 #1을 닫았다. 하위 worktree/담당 터미널은 정리 완료했다. #2 디자인 구현은 아직 시작하지 않았다. 이전 세션 ID·draft/retained 기록은 이력이다. [통합·정리 및 다음 작업 인계](coordinator-architecture-merged-and-worktrees-cleaned.md)를 먼저 읽는다.

이 문서는 과거 검증과 환경을 보존한다. 이번 이슈·컨텍스트 문서 작업에서는 빌드·제품 테스트·시뮬레이터를 실행하지 않았다. 이미 통과한 검사를 근거 없이 반복하지 않고 코드 변경 시 관련 검사만 수행한다.

## iOS

Swift 6, iOS 26+, Xcode 26.6. apps/ios/Dearby.xcodeproj, Dearby scheme, synchronized group이 파일·리소스를 자동 포함한다. bundle io.fixabley.dearby. 외부 패키지 없음.
과거 Simulator Debug 빌드, 번들 디코딩·독립 Swift 저장 검증 통과. 별도 Xcode UI 테스트 타깃 없음. 실제 더블탭 자동 UI 검증을 완료했다고 말하지 않는다.
XcodeBuildMCP 사용 당시 iPhone 17 Pro, simulator UUID C38E28BE-C541-4209-B59C-F1F134B5A3FE, derivedData apps/ios/build/DerivedData, CODE_SIGNING_ALLOWED=NO. 도구의 세션 기본값과 부팅 상태는 재조회한다. 실제 실행에는 ios-debugger-agent 스킬 등을 적용한다.

## Android

Kotlin 2.2.10, AGP 9.1.1, Gradle 9.3.1, Compose BOM 2026.02.01, activity 1.12.4. min 31, compile/target 36. 환경 SDK /Users/jominjun/Library/Android/sdk, Android Studio JDK 25, bytecode 17. 정확 현재 설정은 Gradle 파일이 우선한다.
apps/android에서 ./gradlew 실행하거나 -p apps/android를 지정한다. 별도 child checkout의 local.properties는 없을 수 있다.
Debug 빌드·Lint 통과. 계측 3건은 하단 탭 전환 이전에 통과했다. 하단 탭 변경 후에는 build/Lint와 화면 확인을 했으며 3건을 재실행했다고 주장하지 않는다.
계측 테스트는 기기의 즐겨찾기를 초기화한다. 사용자가 직접 쓰는 기기 대신 전용 테스트 기기를 준비한다. API 36에서 구형 InputManager 경로 실패를 해결하려고 runner 1.7.0, Espresso 3.7.0을 명시했다.

과거 visible AVD Dearby_Pixel_9_API_36_1 / emulator-5554. 최종 시작 옵션:

```sh
/Users/jominjun/Library/Android/sdk/emulator/emulator -avd Dearby_Pixel_9_API_36_1 -no-audio -no-snapshot -gpu swiftshader -feature -Vulkan
```

Vulkan 사용 시 검은 화면이 발생했고 끄고 정상 표시했다. 최초 부팅 중 System UI ANR는 Wait 이후 정상화했다. 마지막 visible 화면은 KRC 즐겨찾기와 하단 NavigationBar였다. 사용자가 기기를 사용할 수 있으므로 임의 종료·초기화하지 않는다. PID 2629, exec session 68227은 과거 값으로 재사용 전 조회한다.
APK apps/android/app/build/outputs/apk/debug/app-debug.apk. 과거 스크린샷 /tmp/dearby-bottom-tabs.png는 임시 파일이라 소실 가능하다. adb 화면 1080×2424는 이미지 뷰어에서 912×2048로 축소될 수 있어 좌표 혼동에 주의한다. UIAutomator bounds를 우선한다. 기기 조작이 필요하면 현재 Orca emulator/computer-use 및 플랫폼 스킬 지침을 확인한다.

## API와 공통 검사

API README에는 Node 26.5.0/npm 12.0.2 환경이 기록되어 있다. NestJS CLI scaffold이고 npm run build/lint, npm test, npm run test:e2e 명령이 있다. 이번 인계에서 API 검사 결과를 확인하지 않았으므로 통과로 단정하지 않는다.
공통 루트 npm test 13건은 과거 통과. 루트 테스트와 API 테스트는 서로 다른 명령 실행 위치다. 샘플 동기화는 양쪽 앱 파일을 수정한다.

접근성 전체 검증, 최대 글자, 태블릿·가로 화면, iOS 자동 UI 제스처 검증은 남아 있다. 향후 이슈 #2의 검증 범위에 맞춰 수행한다.
