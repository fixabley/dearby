# Git·Orca 현재 상태

2026-10-04 KST 확인. 작업을 재개할 때 실제 상태를 다시 확인한다.

- 현재 checkout은 `/Users/jominjun/Documents/dearby`의 `main` 하나다.
- 모바일 프로토타입은 PR #72로 병합했고, 후속 불필요 파일 정리 4개 커밋도 main에 통합했다. iOS·Android 담당 범위가 각 하위 HEAD와 바이트 단위 Git diff 없이 동일한 것을 확인했다.
- `ios-ui-prototype`과 `android-ui-prototype`의 세션을 각각 1개 종료한 뒤 Orca로 워크트리·폴더 2개를 제거했다. 작업 결과와 검증은 [모바일 문서](mobile-ui-prototype.md)에 남겼다.
- 백업은 `~/.dearby-backups/worktree-cleanup-20261004-091905/`에 있다. 전체 tar 2개, 파일별 SHA-256 목록, `all-refs.bundle`, 기존 root Xcode 삭제 patch를 보존했다. iOS 7,049개·Android 2,220개 파일을 tar에서 읽고 원본 해시와 대조했으며 Git bundle verify도 통과했다.
- 백업 디렉터리는 0700, 파일은 0600이다. 개인 설정·ignored 파일도 포함하므로 원격 저장소에 올리지 않는다. Android `.idea`도 백업에 포함했다.
- root에 원래 있던 `project.pbxproj`와 `Dearby.xcscheme`의 로컬 삭제 상태는 별도 보존했다. 이번 통합 커밋에 포함하지 않는다. 원본 시안 폴더와 운영 데이터·환경 파일은 유지했다.
- 최신 사용자 선호는 완료된 작업을 통합하고 하위 세션·워크트리·작업 복제 폴더를 정리하는 것이다. 과거 문서의 retain 지시는 당시 기록이며, 현재 정책은 루트 `AGENTS.md`를 따른다.

이전 정리: [2026-10-03](worktree-cleanup-2026-10-03.md), [2026-09-29 이전 기록](archive/2026-09-29-before-worktree-cleanup/orca-sessions-and-worktrees.md). 삭제된 하위 terminal handle은 재사용하지 않는다.
