# 탐색 어드민 인계 — 2026-09-29

사용자 승인: Supabase + Refine, 어드민과 기존 API의 탐색 데이터까지 연결. 새 클라우드 프로젝트 대신 로컬 환경으로 구현. 현재 브랜치 feat/discovery-admin은7bd57ae에서 시작했다. 구현/스키마/권한/사용법은 [어드민 README](../../apps/admin/README.md), 검증/캡처는 [증거](../../apps/admin/docs/evidence/README.md)를 따른다.

## 현재 실행 상태

- Vite 어드민: http://127.0.0.1:5173, exec session41943.
- Supabase: 프로젝트 dearby, API54321/DB54322/Studio54323, OrbStack Docker 실행 중.
- 기존 API의 별도 Supabase 인스턴스: http://127.0.0.1:58765/v1/catalog, exec session35504. apps/dearby-api/.env.admin-local 및 별도 var/admin-preview.sqlite 사용. 기존 계정·명함 데이터에 접속하거나 변경하지 않았다.
- iPhone17/B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE/com.dearby.dearby는58765로 빌드·실행했다. 명시적 로컬 예시 게시/숨김 반영을 실제 확인했고 현재는 초안30건+숨긴 예시1건으로 탐색이 비어 있다. 앱 재시작/새로고침하면 어드민 변경을 수신한다. 푸시 실시간 갱신은 아니다.
- 로그인은 supabase/.env.credentials.json, Vite 공개 설정은 apps/admin/.env.local. 둘 다 Git에서 제외. 계정 비밀번호/서비스 키를 문서·응답·커밋에 복사하지 않는다.
- 예전 목 서버58764와 실제 SQLite API57937는 별개로 유지한다. 기존 시뮬레이터 데모 캘린더2건은 변경하지 않았다.

## Orca 상태

기존 main checkout만 있는 것을 확인한 뒤 Orca orchestration으로 discovery-admin-backend child를7bd57ae에서 생성했다. Codex v0.158.0 TUI는 대기 중이지만 readiness 검사가2회 실패하여 Task를 전달하지 못했다. [차단 이슈56](https://github.com/fixabley/dearby/issues/56). 이전 사용자 직접 구현 승인으로 root에서 구현했다.

- run_1df5f42bc019 / task_e2b3f327e707, dispatch ctx_87cf9570eefb 및 ctx_c884e92714aa: failed before task delivery, retained 기록.
- 하위 checkout/세션을 임의 삭제하지 않았다. 경로 /Users/jominjun/Documents/dearby/discovery-admin-backend, terminal term_2563add1-6bf4-4c5b-8b3b-072bb4ec63c6. 하위 세션이 구현을 완료했다는 뜻이 아니다.
- root는 코드와 문서를 작성하고 자기 checkout만 빌드했다. 현재 작업은 아직 push/PR/운영 배포하지 않았다. 별도 자동 도구 문제는 구현 완료와 구분한다.

## 남은 범위

클라우드 프로젝트/운영 연결과 관리자 초대, 수집기 Supabase 저장/정기 재확인, Android 실제 연결 검증은 미실행이다. 단위·실제 DB/HTTP·브라우저·시뮬레이터 결과는 증거 문서에 구분했다. 원격 CI 결과를 로컬 통과로 대체하지 않는다.
