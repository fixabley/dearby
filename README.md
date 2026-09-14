# Dearby

Kotlin Android 앱과 Swift iOS 앱을 함께 여는 상위 저장소입니다.
iOS 소스는 별도 GitHub 저장소의 submodule로 관리합니다.
iOS 앱은 초기화되어 있으며, Android 앱은 아직 생성하지 않았습니다.
앱 실행 방법은 [iOS README](apps/ios/README.md)를 참고하세요.

## 디렉터리 구조

```text
apps/
  android/       Android 클라이언트
  ios/           dearby-ios submodule
shared/
  contracts/     두 클라이언트가 사용하는 API 명세와 예제 데이터
docs/            개발 및 설계 문서
```

플랫폼별 네이티브 앱의 빌드와 의존성은 독립적으로 관리합니다.
API 명세와 개발 문서는 두 클라이언트가 함께 사용합니다.

## 프로젝트 추가

- Android 프로젝트는 `apps/android/`를 루트로 생성하거나 옮깁니다.
  Gradle 설정과 Gradle Wrapper도 이 디렉터리에 함께 둡니다.
- iOS 프로젝트는 `apps/ios/Dearby.xcodeproj`에서 관리합니다.
  원본 저장소는 [fixabley/dearby-ios](https://github.com/fixabley/dearby-ios)입니다.
- 앱을 옮길 때 중첩된 `.git` 디렉터리를 포함하지 않습니다.
  기존 저장소의 커밋 기록을 보존해야 한다면 먼저 이관 방식을 결정합니다.
- API 명세와 공통 예제 데이터는 `shared/contracts/`에서 관리합니다.

## Submodule 준비

기존 clone에서는 아래 명령으로 상위 저장소가 기록한 iOS 커밋을 가져옵니다.

```sh
git submodule update --init --recursive
```

새로 clone할 때는 `git clone --recurse-submodules <상위 저장소 URL>`을 사용합니다.
상위 저장소는 아직 원격 저장소가 연결되어 있지 않습니다.
iOS 저장소는 비공개이므로 별도 접근 권한이 필요합니다.

작업과 업데이트 절차는 [저장소 운영 가이드](docs/monorepo.md)를 참고하세요.
