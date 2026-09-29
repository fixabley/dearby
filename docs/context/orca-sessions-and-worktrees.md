# Git·Orca 현재 상태

2026-09-29 KST 확인. 재개 시 실제 상태를 다시 확인한다.

- 현재 checkout은 `/Users/jominjun/Documents/dearby` 하나이며 브랜치는 `feat/discovery-calendar`다. PR40은 main 849b1fe에 병합됐다.
- 사용자가 하위 4개(iOS/Android/API/옛 웹)의 전체 백업 후 워크트리와 연결 세션 종료·제거를 승인했다. 이전 retain 요청의 이번 예외다. Orca rm 4건 removed=true, Git/Orca 모두 main checkout 하나, 하위 terminal 0을 확인했다.
- 백업: `/Users/jominjun/Documents/dearby-worktree-backups/20260929-093612`. 전체 tar 4개(ignored 검증 자료/설정 포함), all-refs.bundle, web AGENTS 미커밋 patch 및 복구 README. bundle verify와 각 tar 목록 읽기 성공. 비공개 로컬 자료이므로 공개 업로드하지 않는다.
- 하위 소유 커밋은 모두 origin/main에 patch-equivalent임을 git cherry로 확인했다. web AGENTS의 Next 자동 추가 블록만 미커밋이었으며 백업에 포함했다.
- #54 Orca Run run_8ba522965ee6의 iOS/Android 시작 시도는 업데이트 프롬프트/준비 판정 오류로 업무 실행 전에 실패했다. #48에 증거를 기록했다. 사용자 승인으로 이 작업은 메인이 현재 checkout에서 직접 구현한다. 삭제된 하위 handle/dispatch를 재사용하지 않는다.
- 현재 coordinator handle은 term_1cbabda3-9f12-4f38-9655-ef8a0a3548fd. 실행 handle은 런타임 값이며 검증 시점과 별개다.

구현 및 검증 결과는 [조율 문서](coordinator-current-task-and-decisions.md)를 따른다. 이전 운영 기록은 [보관본](archive/2026-09-29-before-worktree-cleanup/orca-sessions-and-worktrees.md)에 있다.
