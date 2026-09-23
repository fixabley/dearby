# Git·Orca 현재 상태

2026-09-24 KST 확인. 이전 세션 기록은 docs/context/archive/2026-09-24-before-web-rebuild/context-orca-sessions-and-worktrees.md에 보존한다.

- Root: /Users/jominjun/Documents/dearby, feat/web-rebuild. 기존 실행코드 보존·제거 be10837. 사용자 미커밋 Xcode 변경은 별도 백업 원본에 보존했다.
- Web: /Users/jominjun/Documents/dearby/dearby-web, refs/heads/fixabley/dearby-web. Git base feat/web-rebuild, Orca 부모는 메인 Dearby다. 자기 checkout의 웹 앱·설정·검증·web 역할 문서 소유.
- Runtime: a185645e-381f-46ad-b638-34be2cf40910. Run run_469a74207c03 / Task task_c5abe8d83ebe / Dispatch ctx_a529f998bad6 / terminal term_c0008d89-171d-424f-8ddc-122136963514.
- root terminal: term_a4c93b27-6d82-45be-a321-16dbc6a4bed9.
- dearby-ir은 별도 저장소/세션이며 이번 변경 대상이 아니다.

백업: /Users/jominjun/Documents/dearby-backups/2026-09-24-before-web-rebuild/. repository.bundle, uncommitted.patch, files 아래 전체 옛 실행 폴더·설정·의존성·로컬 파일, manifest.json을 보존한다. 삭제 대신 외부 이동한 것이며 700개 소스/로컬 파일 SHA-256을 대조했다. Git main f1d9a63에도 기존 추적 코드가 남아 있다.

진행: 웹 구현 중. 원격 push/PR/병합/배포는 수행하지 않았다. 실제 다음 행동은 조율 문서를 따른다. 세션 handle은 재개 시 반드시 실시간 재확인한다.
