# 저장소 운영

## 구조와 변경 단위

현재 구조는 iOS 소스를 별도 저장소로 관리하는 submodule 방식입니다.
상위 저장소는 iOS 소스 대신 특정 커밋을 기록합니다. `branch = main` 설정은
명시적으로 원격 업데이트를 할 때 사용할 브랜치를 지정하며, 자동으로 최신 커밋을 따라가지 않습니다.

Android는 아직 프로젝트가 없는 일반 디렉터리이고, iOS는 `apps/ios` submodule입니다.
플랫폼별 빌드와 의존성은 각 앱에서 관리하며 공통 API 명세는 `shared/contracts/`에 둡니다.
iOS 앱은 상위 저장소 없이도 빌드할 수 있도록 유지합니다.

## 다른 사람의 변경 가져오기

상위 저장소에 원격 저장소가 연결된 뒤에는 다음 순서로 동기화합니다.
작업 중인 변경이 있다면 각 저장소에서 먼저 커밋하거나 보관합니다.

```sh
git pull --ff-only
git submodule update --init --recursive
```

이 명령은 상위 저장소가 기록한 iOS 버전을 checkout합니다.
원격 iOS의 최신 버전을 자동으로 선택하지 않습니다.

## iOS 수정하기

submodule update 후에는 브랜치 대신 특정 커밋을 checkout한 detached HEAD 상태일 수 있습니다.
작업 시작 전에 iOS 저장소에서 브랜치를 만듭니다.

```sh
git -C apps/ios switch -c feature/my-change
```

iOS 저장소에서 변경 파일을 선택해 커밋하고 해당 브랜치를 푸시합니다.
iOS PR이 main에 병합되면, 상위 저장소 루트에서 참조를 갱신합니다.
아래 명령은 작업 중인 변경이 없는 상태에서 실행합니다.

```sh
git -C apps/ios fetch origin
git -C apps/ios switch main
git -C apps/ios merge --ff-only origin/main
git add apps/ios
git commit -m "Update iOS submodule"
```

상위 저장소에 원격이 연결되어 있다면 이 커밋도 푸시합니다.
iOS 커밋을 원격에 올리기 전에 상위 저장소의 참조부터 공유하면 다른 사람이 checkout하지 못합니다.
상위 저장소에서 `git push --recurse-submodules=check`를 사용하면
submodule 커밋이 원격 추적 브랜치에 있는지 확인할 수 있습니다.

## 운영 시 고려할 점

- iOS와 상위 저장소의 커밋·PR을 별도로 관리합니다. 공통 API 변경은 관련 PR 링크로 연결합니다.
- 상위 저장소 브랜치를 바꾼 후에는 submodule update도 실행해야 할 수 있습니다.
- 두 브랜치가 서로 다른 iOS 커밋을 가리키면 상위 저장소 병합 시 참조 충돌을 해결해야 합니다.
- iOS 저장소는 비공개이므로 팀원과 CI에 별도 읽기 권한이 필요합니다.
  상위 저장소의 GitHub Actions 기본 토큰만으로는 다른 비공개 저장소 접근을 가정할 수 없습니다.
- iOS 저장소만 clone하면 상위 저장소의 API 명세는 포함되지 않습니다.
  향후 코드 생성이 필요해지면 명세를 버전별 패키지나 별도 입력으로 제공할 방식을 정합니다.

독립적인 권한·이슈·릴리스 관리에는 적합합니다. 두 앱과 명세를 자주 함께 수정한다면
일반 모노레포가 변경을 한 번에 검토하고 반영하기에 더 단순합니다.

## 빌드와 로컬 설정

빌드·실행 방법은 각 앱 README에 기록합니다. iOS scheme은 공유하고 의존성 잠금 파일은 커밋합니다.
개발자별 SDK 경로, 빌드 결과, 환경 변수의 실제 값과 서명 키는 커밋하지 않습니다.
submodule은 상위 `.gitignore`에 의존하지 않고 자체 `.gitignore`를 유지합니다.

참고: [Git 공식 submodule 가이드](https://git-scm.com/book/en/v2/Git-Tools-Submodules)
