# 구독 Codex 활동 수집

Supabase `pg_cron`이 한국 시간 매일 09:00에 수집 ON 프로그램별 작업을 등록한다. 로그인된 Mac의 Node 24 워커가 한 작업씩 `codex exec --model gpt-6-luna`로 검색한다. **ChatGPT 로그인만 허용하며 OpenAI API 키·API 호출 fallback은 없다.**

## 저장 규칙

- 프로그램·한국 날짜당 한 작업. 오늘의 대기/실패 작업만 claim하며 전역 동시 실행은 1개다.
- lease 10분. Codex 4분, 원문별 DNS·리다이렉트 포함 15초/1 MB. 최대 10개 후보, 빈 결과는 최대 3개 출처 확인. 실제 웹 도구 시작 이벤트 12회에서 중단한다(프롬프트 목표 8회).
- 실패 30분 뒤 재시도, 시도 3회 뒤 blocked. 프로세스 중단·lease 만료는 5분 뒤 재시도하며 이전 소유자의 쓰기는 거부한다. 구독 로그인/한도 오류는 전체 claim을 일시정지한다. 어드민에서 원인 해소 후 재시도하면 해제한다. 원문 구조 문제(모델이 보고한 원문 불가, 1 MB 초과, 설정 호스트 밖, 공인 IPv4 없음, 모든 후보가 이 사유로 실패)는 그날 다시 시도해도 같으므로 `BLOCKED:`로 바로 멈추고 전체 일시정지는 하지 않는다. 다음 날 작업은 다시 1회 실행된다. 일시적 실패(시간 초과, 구절 불일치 등)는 기존대로 다시 시도한다.
- `source_unavailable`, 빈 결과의 원문 근거 부재, 모든 후보의 검증 실패는 성공으로 기록하지 않는다. 일부만 통과하면 적용 수와 제외 사유를 함께 남긴다.
- 신규는 초안. 마지막 collector 저장 시각과 현재 `updated_at`이 같고, 게시·공식 확인되지 않은 초안만 갱신한다. 정보가 null/unknown/빈 배열로 바뀌면 삭제하지 않고 검토 제안을 남긴다.
- 관리자가 수정한 내용·게시 상태·공식 확인·JSON 조건은 자동으로 덮어쓰지 않는다. 공식 확인·게시 승인은 기존 활동 편집에서 수행한다.
- 정체성은 프로그램 + 정규화 공식 URL + 회차/활동 식별자다. 같은 페이지의 다른 신청 활동은 `2026-본행사`, `2026-이력서피드백`처럼 구분한다. 기존 식별자는 검색 프롬프트로 전달한다. 같은 제목·URL의 과거 seed만 보수적으로 연결하며 이미 다른 회차에 연결된 활동은 재사용하지 않는다.
- 제목 또는 URL이 겹치는 기존 활동은 `possible_duplicates`로 표시한다. 의미상 동일한 회차의 URL/이름이 바뀌면 중복 초안이 생길 수 있어 관리자 검토가 필요하다.

## 원문 검증 경계

관리자가 설정한 정확한 HTTPS 호스트만 허용한다. 공인 IPv4를 검증하고 TLS 요청 DNS를 해당 IP로 고정한다. 사설/예약 IP, 다른 호스트로의 redirect, 큰/느린 응답을 거부한다. IPv6-only·JavaScript 렌더링 전용·로그인 필요·봇 차단 출처는 이 워커가 지원하지 않는다.

20~200자·최대25단어의 연속 원문 구절이 본문에 존재하는지 확인하고 URL·조회 시각·본문 SHA256을 남긴다. **구절의 존재 확인은 모든 모집·일정 필드가 참이라는 증명이 아니다.** 날짜만 있으면 일정 문구에 남기고 자정·종료시각을 만들지 않는다. 한국 시간대 행사만 지원한다. 신청 URL은 원문 `<a href>` 전체 목적지와 일치해야 한다. 공개 전에 관리자가 날짜·모집·자격·신청 링크를 검토해야 한다.

후보 테이블은 회차별 최신 제안이고 후속 수집이 이전 제안을 대체한다. 작업별 통계·시도별 사용량/오류는 `run_history`에 보존한다. 전체 원문 HTML과 과거 후보 전체를 보관하는 아카이브는 아니다. 초안 적용 이력은 기존 감사 로그에도 남는다.

## 수동 실행과 검사

Node 24와 Supabase CLI, 로컬 Supabase가 필요하다. 기존 관리자 비밀번호·DB를 바꾸지 않는 워커 전용 부트스트랩이다.

```sh
cd apps/catalog-worker
node scripts/bootstrap.mjs
codex login status  # Logged in using ChatGPT
node --env-file=.env.local src/worker.mjs --enqueue --program PROGRAM_UUID
npm run check
npm test
# 저장소 루트에서 실제 로컬 DB·권한·HTTP 계약 검사
node --test apps/catalog-worker/test/queue.integration.mjs
```

`.env.local`은 ignored/0600이고 service-role 키는 워커만 사용한다. 자식 Codex에는 HOME/PATH 등 허용된 환경 변수만 전달한다. API/DB키를 전달하지 않으며 사용자 config·rules·shell·multi_agent·apps·js_repl을 비활성화하고 별도 임시 디렉터리에서 read-only/ephemeral로 실행한다. 인증 저장소는 CLI가 직접 사용하며 워커가 auth.json을 읽거나 복사하지 않는다.

`--enqueue`는 수동 요청, `--catch-up`은 DB enabled 설정이 ON일 때 한국 시간 09시 이후 오늘 작업을 보충한다. Mac이 09시에 꺼져 있었다면 재가동 후 오늘분만 등록한다. 장기간 놓친 과거 날짜를 모두 재수집하지 않는다. 한 실행은 한 작업을 처리한다.

## 운영·비용

구독 사용량은 일반 Codex 작업과 공유한다. 토큰 수는 CLI 실행 보고값이고 구독 차감량·남은 횟수·청구액으로 직접 환산할 수 없다. 캐시 입력은 입력 토큰의 부분집합이다. API 단가나 Batch API 50% 할인은 이 구독 워커의 비용 근거가 아니다. 포함 한도/추가 크레딧 정책은 사용 중인 플랜에 따르며 무제한·추가 비용 0을 보장하지 않는다.

현재 28개 프로그램이면 기본 28작업/일이고 실패 시 최대 3시도/프로그램/일이다. 프로그램별 ON/OFF로 운영 범위를 줄일 수 있다. 로그인/한도 차단은 UI에서 표시하지만 외부 이메일·푸시 실패 경보는 아직 없다. 최신 실제 측정·실행 상태는 [작업 인계](../../docs/context/catalog-subscription-worker-handoff.md)를 따른다.

공식 근거: [Codex 비대화형 실행](https://learn.chatgpt.com/docs/non-interactive-mode), [구독 정책](https://learn.chatgpt.com/docs/pricing), [Supabase Cron](https://supabase.com/docs/guides/cron). 현재 설치된 CLI `exec --help`와 실제 ChatGPT 로그인 실행으로 옵션을 추가 확인했다.

## macOS 자동 실행

```sh
cd apps/catalog-worker
# Node 24로 실행: 설치 시 현재 node와 codex 절대 경로를 plist에 고정한다.
node scripts/launchd.mjs install
node --env-file=.env.local scripts/schedule.mjs on
node scripts/launchd.mjs status
# 자동 신규 등록 중단 (기존 큐는 남음)
node --env-file=.env.local scripts/schedule.mjs off
# 워커 프로세스 및 이후 실행 중단 (기존 큐·데이터·인증은 보존)
node scripts/launchd.mjs uninstall
```

사용자 LaunchAgent `com.dearby.catalog-subscription-worker`가 로그인 시와 60초 간격으로 실행된다. 동일 label의 중복 프로세스를 launchd가 막고, 수동 워커와의 경쟁은 DB lease가 막는다. 현재 checkout 경로를 고정하므로 worktree를 삭제/이동하기 전에 uninstall 후 새 경로에서 재설치해야 한다. Node/npm 캐시 정리로 고정 Node 실행 파일이 사라지면 Node24를 다시 준비하고 재설치한다. Mac 로그인·네트워크·Supabase 컨테이너·ChatGPT 인증이 유지되어야 한다. 절전 중 실행을 보장하지 않으며 절전 방지 설정을 임의 변경하지 않는다.

로그는 ignored `logs/worker.log`, `logs/worker-error.log`에 쌓인다. service-role/auth 파일은 로그에 출력하지 않는다. 장기 운용 시 로그 용량 관리가 필요하다. 현재 로컬 배치이며 cloud Supabase 배포·24시간 가동 호스트·외부 실패 알림·자동 DB 백업은 별도 운영 조건이다.

CI에서는 단위/구문·실제 로컬 DB·관리자 브라우저 검사만 실행한다. 구독 인증이나 실검색을 공용 runner에 복사하지 않는다. 실제 DB 통합 검사는 운영 워커와 경쟁하지 않도록 별도 테스트 Supabase에서 수행한다. 공유 로컬 DB에서 검사할 경우 워커가 유휴이고 예약 실행이 중단된 상태에서만 실행한다.
