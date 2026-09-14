# Dearby

Kotlin Android 앱, Swift iOS 앱과 NestJS API 서버를 함께 관리하는 모노레포입니다.
실행 방법은 [Android README](apps/android/README.md), [iOS README](apps/ios/README.md),
[API README](apps/dearby-api/README.md)를 참고하세요.

## 디렉터리 구조

```text
apps/
  android/       Android 클라이언트
  ios/           Swift iOS 클라이언트
  dearby-api/    NestJS API 서버
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
- API 서버는 `apps/dearby-api/`에서 npm으로 의존성과 빌드를 관리합니다.
- 앱을 옮길 때 중첩된 `.git` 디렉터리를 포함하지 않습니다.
  기존 저장소의 커밋 기록을 보존해야 한다면 먼저 이관 방식을 결정합니다.
- API 명세와 공통 예제 데이터는 `shared/contracts/`에서 관리합니다.

## 개발 흐름

저장소 전체를 일반 `git clone`으로 가져오고, 루트에서 브랜치·커밋·PR을 관리합니다.
Android·iOS·공통 명세의 연관된 변경은 하나의 커밋이나 PR에 함께 포함할 수 있습니다.
원격 저장소는 [fixabley/dearby](https://github.com/fixabley/dearby)이며, 기본 브랜치는 `main`입니다.

```sh
git clone https://github.com/fixabley/dearby.git
```

작업과 업데이트 절차는 [모노레포 운영 가이드](docs/monorepo.md)를 참고하세요.

## 첫 기능: 공고 탐색과 조직 즐겨찾기

두 앱에서 검토한 공고 샘플을 세로로 넘기고, 더블탭 또는 저장 버튼으로 조직을 저장합니다.
즐겨찾기는 기기에 보관하며 API 연결 없이 실행할 수 있습니다.

- [활동 공고 규격과 원문 검토](docs/product/activity-data-v1.md)
- [첫 반복의 동작·검증·남은 결정](docs/product/iteration-01.md)
- [관심 대상 선정·분류 규칙과 추론 도구](docs/product/interest-target-rules.md)
- [공통 샘플](shared/contracts/activities/sample.json) / [JSON Schema](shared/contracts/activities/schema.json)

루트 npm은 공통 데이터 검증용입니다. 네이티브 앱 빌드에는 필요하지 않습니다.

```sh
npm ci
python3 scripts/sync-activity-samples.py
npm test
```
