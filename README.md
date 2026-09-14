# Dearby

Kotlin Android 앱과 Swift iOS 앱을 함께 관리하는 모노레포입니다.
Android와 iOS 앱의 기본 프로젝트가 초기화되어 있습니다.
실행 방법은 [Android README](apps/android/README.md)와 [iOS README](apps/ios/README.md)를 참고하세요.

## 디렉터리 구조

```text
apps/
  android/       Android 클라이언트
  ios/           Swift iOS 클라이언트
shared/
  contracts/     두 클라이언트가 사용하는 API 명세와 예제 데이터
docs/            개발 및 설계 문서
```

플랫폼별 네이티브 앱의 빌드와 의존성은 독립적으로 관리합니다.
API 명세와 개발 문서는 두 클라이언트가 함께 사용합니다.

## 프로젝트 추가

- Android 프로젝트는 `apps/android/`에서 관리합니다.
  Android Studio에서 이 디렉터리를 열어 개발합니다.
- iOS 프로젝트는 `apps/ios/Dearby.xcodeproj`에서 관리합니다.
- 앱을 옮길 때 중첩된 `.git` 디렉터리를 포함하지 않습니다.
  기존 저장소의 커밋 기록을 보존해야 한다면 먼저 이관 방식을 결정합니다.
- API 명세와 공통 예제 데이터는 `shared/contracts/`에서 관리합니다.

## 개발 흐름

저장소 전체를 일반 `git clone`으로 가져오고, 루트에서 브랜치·커밋·PR을 관리합니다.
Android·iOS·공통 명세의 연관된 변경은 하나의 커밋이나 PR에 함께 포함할 수 있습니다.
현재 이 저장소에는 원격 저장소가 연결되어 있지 않습니다.

작업과 업데이트 절차는 [모노레포 운영 가이드](docs/monorepo.md)를 참고하세요.
