# 구독 Codex 활동 수집 워커 — 구현 중 인계 (2026-09-29)

## 사용자 승인 / 최종 요구

- DB의 프로그램 이름을 기준으로 매일 활동 수집·최신화.
- Supabase 주기 작업 사용. API 대신 **ChatGPT 구독 로그인 + codex exec + gpt-6-luna**를 명시적으로 선택.
- 구현·검증 후 커밋하고 push 승인됨.
- 사용자는 지금 작업을 하위 세션에 전가하고 root에서는 IR 자료 작업을 진행하길 요청했다. 이것은 완전 소유권 인계이며 감독용 orchestration을 중복 생성하지 않는다. 하위 세션을 완료 후에도 유지한다.

## 완료 / 현재 상태

- JSON 조건 편집기(임의 타입, 중첩, 유사 키 자동완성, GIN)는 **93de583**으로 완료·origin/feat/discovery-admin push됨. admin build/lint/unit6, 실제 DB/HTTP8, Playwright2 통과. 관련 문서 admin-implementation-and-handoff.md.
- 그 이후 워커는 **WIP**이다. apps/catalog-worker/src/{worker,collect,source}.mjs 및 package.json, supabase/migrations/20260929020000_catalog_collection.sql.
- SQL migration은 root의 로컬 Supabase에 적용됐다. pg_cron 설치, 일일09:00KST 등록. 그러나 catalog_collection_settings.enabled=false이므로 자동 enqueue 비활성이다. 실제 수집 job enqueue/실행/launchd 설치는 아직 하지 않았다.
- 현재 실제 프로그램28개 + 로컬 예시1개. 과거 E2E 테스트 조직/프로그램이 몇 개 추가로 남아 있다. 이름이 정확히 `[로컬 테스트] (조직|프로그램|활동) 13자리timestamp`인 이번 E2E 생성물만 안전하게 정리 가능. 기존 데모·실데이터 삭제 금지.
- 프로그램 collection_hosts는 기존 활동 official_url의 host로 초기화, collection_enabled는 `[로컬` 이름 제외. 새 프로그램은 기본false.
- 로컬 `codex login status` 결과 Logged in using ChatGPT. auth.json은 읽거나 공유하지 않았다. CLI v0.158 계열의 exec --help/features list에서 --ignore-user-config, --ephemeral, --disable shell_tool, --disable multi_agent 지원 확인.
- 워커는 별도 임시 디렉터리, subscription login 강제, child env allowlist(HOME/PATH 등), API/DB키 전달 차단, 사용자 config 미로드, shell/multi_agent 비활성, read-only, live web search, low reasoning, standard service tier(default), JSON output-schema 사용. 실제 호출은 미검증.
- source.mjs는 공식 HTTPS host allowlist, public IPv4 DNS pinning, redirect 제한, 1MB/15s 제한, HTML->text와 증거 구절 확인을 구현했다. 외부 DNS/IP 경계와 타임아웃을 테스트해야 한다.
- 신규는 초안, collector가 마지막 저장한 버전과 같을 때만 자동 갱신, 관리자 수정/공식확인/게시 이후 변경은 collection_results에 review 제안 보존. 자동 공식확인/게시 없음.

## 남은 작업 (필수)

1. WIP 전체 정확성 리뷰/수정. 특히 DB null 입력 거부·lease 소유권·만료/재시도·중복/정체성·관리자 보존·원자성, 후보가 모두 원문검증 실패했을 때 succeeded로 오인하지 않기. source proof는 현재 짧은 quote 존재만 확인하므로 모집/날짜의 모든 필드가 검증됐다고 표현하지 말 것.
2. 런타임 실제 codex exec 한 프로그램 검색 smoke. shell disabled/model/service tier/schema 옵션 실제 동작 확인. 요금 API로 fallback 금지. 실패/구독한도는 작업에 명시하고 무한 재시도 금지.
3. 안전한 worker .env.local 부트스트랩(Node24 + 로컬 supabase CLI status JSON을 메모리에서 읽어 service role 저장; 0600/ignore). 비밀 출력 금지. launchd 설치/해제 도구 또는 명확한 실행 방법. 사용자의 매일 실행 요구에 맞춰 활성화할 수 있으면 실행하고 실제 상태 기록. Mac+Supabase 실행 필요, cloud Supabase는 아직 없음.
4. 어드민 프로그램 편집에 collection_enabled/collection_hosts, 수집 작업 화면(상태·오류·사용량·pending review/후보·원문), 수동 큐 등록. 원문/수집 결과를 볼 수 있게 할 것. UI에서 임의의 draft 공개/공식확인 금지. 기존 활동으로 연결해 검토 가능하게.
5. Node 유닛 + 실제 Supabase queue/permission/claim/concurrency/retry/idempotency/manual preservation tests. 실제 관리자 브라우저 흐름. CI에 워커 검사 포함하되 구독 credentials/실검색은 CI에 넣지 말 것.
6. ponytail-review 필수. 문서/역할/증거 및 한계 갱신. WIP commit은 완료 표시가 아니므로 추가 feature/test commit으로 마무리. 자기 브랜치 push, root에 branch/hash/실행 결과를 전달. 원래 feat/discovery-admin에 자동 force push/다른 checkout 변경 금지. 필요하면 feat/discovery-admin을 base로 draft PR 생성.
7. 날짜가 알려져도 종료시각·마감시각을 추정하지 말 것. 정기 실행 주기 자체를 OpenAI Batch API 50% 할인과 혼동하지 말 것. 구독 사용량은 평소 Codex와 공유하며 무제한 비용0 보장 금지.

## 알려진 검토 사항

- collect.mjs randomUUID import 미사용. schedules 안정ID는 SHA256 기반이며 외부 식별은 officialURL+occurrence. occurrence 일관성/이전 회차 혼동 테스트 필요.
- 알려진 값이 다음 검색에서 null/unknown이 되면 자동 업데이트가 기존 수집 값을 지울 수 있으니 병합 규칙 검토.
- date/timeZone 현재 Asia/Seoul 고정(한국 프로그램). 임의 다른 시간대 행사는 원문 기준으로 처리하거나 지원 경계 기록.
- finish function은 lease 10분, exec4분 + 최대10개원문15초(redirect 포함시 더 길어질 수 있음). 전체예산과 lease 일치시킬 것.
- claim은 전역advisory lock으로 subscription동시1개. 실패30분 지연3회 후blocked. 인증/한도blocked 재개UI 아직 없음.
- 신규/기존 seed exact title+URL 일치만 연결. 유사 제목 중복을 임의 합치지 않지만 기존seed와 중복초안이 생길 수 있음. 검토 표시/탐지 개선 권장.
- 최초 JSON migration은 93de583으로 게시됐으므로 이력 수정 금지. 이후 SQL은 WIP지만 로컬에 이미 적용됨. 스키마 수정은 추가 migration으로 남기는 편이 안전.

## 로컬 환경

- Root /Users/jominjun/Documents/dearby; 기존 `discovery-admin-backend` child는 과거 readiness 문제로 미사용(retain). 새 담당자는 자기 checkout만 수정/빌드.
- Supabase dearby via OrbStack: API54321 DB54322 Studio54323. local container supabase_db_dearby.
- Admin http://127.0.0.1:5173 (root 프로세스), Supabase-backed API http://127.0.0.1:58765 (root Node24 프로세스), iPhone17는58765 연결. 해당 프로세스 종료/DB reset 금지.
- 새 checkout 환경은 `node supabase/scripts/bootstrap-local.mjs`로 자기 checkout의 ignored env 생성 가능(기존 행 ignore-duplicates; 계정/비밀번호 재생성 주의: root credentials 파일을 비공개로 보존/재사용해야 기존계정 비밀번호 불일치 방지). 기존 root env/credentials는 읽기만 하여 자기 local env에 복사 가능, Git추적 금지.
- Node24: /Users/jominjun/.npm/_npx/538786c08bcb9442/node_modules/node/bin/node
- root imported folder 01a0dd49-8ce1-7263-957a-d63f220da862는 사용자 원본, 추적/삭제 금지.
- Git push credential helper 필요시 `git -c credential.helper= -c 'credential.helper=!gh auth git-credential' push ...`.

## 공식 문서 확인

- https://supabase.com/docs/guides/cron
- https://learn.chatgpt.com/docs/non-interactive-mode : 저장된 CLI 인증 재사용, exec output schema, ChatGPT-managed auth. 공용 GitHub runner에 구독 auth복사하지 않고 로컬 로그인 사용.
- https://learn.chatgpt.com/docs/pricing : GPT-6 Luna 구독은 공통 한도/크레딧, 모델 기본 Standard. 포함한도 내 추가 API요금 없음이나 무제한은 아님.
