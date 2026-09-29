# API — 구현과 인계

2026-09-29 최신: CATALOG_BACKEND=supabase로 탐색 읽기만 전환할 수 있다. /v1/catalog 형식/최신성 규칙과 SQLite 계정·명함은 유지한다. 로컬 API58765 실행·RLS/HTTP/브라우저 연결 검증 완료. [어드민 인계](admin-implementation-and-handoff.md).
검증 시점: **2026-09-27 12:21 KST / 03:21 UTC**. #45 로컬 카탈로그 구현과 검증이며 전체 서비스·운영 배포 완료가 아니다. 정본은 `docs/product/native-spec-2026-09.md`, `shared/contracts/catalog-v1.md`, `shared/contracts/native-v1.md`다. 이전 #39 인증·명함·교환 구현은 유지하며 아래 현재 검증이 기존 회귀를 포함한다.

## 담당과 통합

- checkout `/Users/jominjun/Documents/dearby/dearby-api`, branch `feat/api-activities`, base `dcb590f4379a5e8e8e37476691791143e8bb373a`.
- 시작 시 `git status --short` 빈 출력 확인 후 `git switch -c feat/api-activities dcb590f`. 이전 `fixabley/dearby-api` 브랜치/커밋을 reset/delete하지 않았다.
- worker terminal `term_8f4dc959-3adb-4109-ba29-8321fc911357`, task `task_8cc6572cb14f`, dispatch `ctx_deaf5c8ca5dd`. 세션 식별자와 검증 시각은 별개다.
- 소유 범위 `apps/dearby-api`, 이 문서, `docs/workstreams/api.md`. 다른 checkout·shared 계약/데이터·root/CI 변경 없음. 하위 agent 없음.
- `73b2082`: 영속 공식 카탈로그·수집/과거 가져오기 CLI·HTTP 테스트. 조율자에게 usable commit으로 전달했다.
- `582a527`: 거절된 HTTP body 취소, 실제 마감·초기 실패·과거 식별자/참조 입력 회귀, UUIDv5 golden check.
- `2f26fb6`: read snapshot transaction 및 인계 문서. 후속 문구 commit은 앱에 직접 보이는 sourceNote에서 null·파서·자동입력 구현 용어를 제거하고 검증된 기간·시간·선발 조건만 남긴다. push/PR/main merge 없음. 조율자가 cherry-pick·공통 서버·플랫폼 통합을 담당한다.
- 완료 후 사용자 요청으로 이 세션을 유지하며 조율자가 retain 처리한다.

## 구현과 가역적 결정

인증 없는 `GET /v1/catalog`는 정확한 typed DTO와 JSON null을 반환한다. 초기 DB는 200 빈 배열, DB 오류는 500이다. closed/unknown 자료도 상세·저장·교환 맥락용으로 남는다. 조직/프로그램/활동은 분리하고 SQL 외래키와 입력 참조 검증을 사용한다. 읽기도 하나의 SQLite snapshot transaction이므로 외부 CLI commit 중 세 배열을 서로 다른 snapshot에서 읽지 않는다.

SQLite migration 002, WAL/FULL·출처별 transaction, 원자적 과거 import를 사용한다. UUIDv5 DNS namespace와 `dearby/catalog/{kind}/{source key}` 이름으로 안정된 ID를 만든다. 기존 slug `kakao-2026`, `feconf-2026`를 그대로 source key로 유지해 과거/실수집 순서와 재시작에 상관없이 중복을 방지한다. if(kakao) activity UUID는 `ed43a1d2-213c-5511-bfe2-e80aa26cd866`, FEConf는 `39c7261a-7d5c-510e-8e97-1982fb8f99d3`다. 프로그램/조직/schedule 규칙은 API README 참조.

`sourceCheckedAt`은 본문 근거 해석 성공 시각이고 `validUntil`은 최대 24시간 후다. HTTP 200만으로 갱신하지 않는다. source note는 근거·시간 해석·미확인을 설명하고 `good_body_sha256`은 마지막 정상 HTML hash를 보존한다. `catalog_refreshes`는 가장 최근 시도와 성공/실패 사유다. 원본 HTML 장기 archive나 전체 시도 이력은 구현하지 않았다.

읽을 때 `sourceCheckedAt <= now < validUntil`, 명시적 OPEN, 미래 시작/경과 마감을 확인한다. 시작 inclusive·마감 exclusive·24시간 exact expiry를 검증했다. 실패는 정상 내용/checkedAt/validUntil/hash를 유지하고 `unavailable`, 모집 false로 표시한다. DB 자체가 쓰기를 거부해 실패 표지도 저장할 수 없으면 CLI가 실패하며 정상 수집으로 표시하지 않는다. 과거 deadline이 확인되면 closed이고, 미확인/오래된 모집은 unknown이다.

공식 collector는 `if.kakao.com/2026`, `2026.feconf.kr/` 두 URL만 순차 요청한다. 요청별 15초, decoded HTML 1MiB, redirect 금지, HTML content type 확인. 외부 링크 수집·임의 URL·JavaScript 실행·로그인·개인정보 제출·자동 retry·범용 crawler framework 없음. 검토한 2026 원문 구조/일자가 달라지면 파서는 보수적으로 실패하며 새 구조 검토가 필요하다.

if(kakao)는 참가 신청 영역의 OPEN과 FAQ 선발 조건을 확인하여 selection으로 표시한다. 9월 28일 낮 12시는 한국 현지 기준 03:00Z로 해석했다. 신청 시작 9월 7일은 시각이 없어 null이고 행사 10월 13–14일도 start/end null이다. 후원 감사 영역 문구는 모집 종료 근거가 아니다. FEConf는 TICKET OPEN D-14와 행사 개장을 구분해 scheduled이며 상대 카운트다운으로 모집 날짜를 계산하지 않는다. 행사 개장 2026-10-24 10:00 Seoul만 01:00Z로 기록하고 종료 시각은 null이다.

기존 snapshot은 명시적 `import-legacy`로만 가져온다. 30개 모두 stale/unknown, semantic checked/expiry null이며 기존 open/current는 신뢰하지 않는다. audience 배열은 개별 공고 안에서만 읽기 쉬운 문자열로 합치고 round를 사람용 제목에 사용한다. duplicate identity/missing reference는 전체 rollback, 재import는 기존 정상/실패 자료를 덮어쓰지 않는다. 서버 기본 시작에는 fixture/과거 자료 자동 seed가 없다.

ExchangeContext/receipt DTO와 기존 UUID-format validation은 변경하지 않았다. 등록 활동 선택은 참가 인증이 아니다. 앱 캐시도 validUntil 외 모집 시작/마감 시각을 확인해야 한다는 리뷰 의견을 조율자에게 전달했다. 조율자는 두 앱 모두 이 경계를 검사하고 화면 만료 갱신도 구현했다고 회신했다. 이는 조율자 회신이며 worker가 앱 코드를 직접 검증한 것은 아니다.

## 실제 실행한 명령과 결과

호스트 기본 Node는 26.10.0이므로 모든 실행/테스트는 Node **24.21.0**, npm **11.19.1**을 명시했다. 의존성을 추가/업그레이드하지 않았다. 설치된 버전: Fastify 5.12.5, better-sqlite3 13.0.3, Zod 4.6.5, Nodemailer 10.0.10, TypeScript 7.0.2, tsx 4.23.15, oxlint 1.85.0, @types/node 24.19.0, @types/better-sqlite3 9.6.0, @types/nodemailer 8.0.2.

checkout root에서:

```sh
npm exec --yes --package=node@24.21.0 -- npm --prefix apps/dearby-api run typecheck
npm exec --yes --package=node@24.21.0 -- npm --prefix apps/dearby-api run lint
npm exec --yes --package=node@24.21.0 -- npm --prefix apps/dearby-api run build
npm exec --yes --package=node@24.21.0 -- npm --prefix apps/dearby-api test
npm exec --yes --package=node@24.21.0 -- npm --prefix apps/dearby-api run catalog -- import-legacy --db /tmp/dearby-api-activities-final-history.sqlite
npm exec --yes --package=node@24.21.0 -- npm --prefix apps/dearby-api run catalog -- refresh --db /tmp/dearby-api-activities-final-live.sqlite
git diff --check
```

모두 최종 통과. **20/20 tests**, lint warning 없음. 새 카탈로그 10개 테스트와 기존 auth/card/wallet/persistence 10개가 포함된다. Fastify inject 대체가 아닌 TCP listener + fetch, 테스트별 실제 임시 디스크 SQLite를 사용했다. source parsing fixture는 test 폴더에만 있고 real source 검증과 구분했다.

검증: exact DTO/빈 성공과 서버 오류 구분, 시작·마감·24시간·역방향 clock, 실제 if(kakao) noon 경계, scheduled 상세 유지, fetch/파싱 실패 및 복구, 정상 내용/hash 보존, SQL trigger write 실패 rollback, 참조/UUID golden/dedup/재import, 실제 snapshot 30건과 invalid input rollback, DB+HTTP 재시작, URL allowlist/HTTP status/type/body size/실제 로컬 HTTP redirect 거부. 기존 OTP·공개 projection·소유권·교환 멱등성·부분 import·저장 재개방 회귀 통과.

최초 과거 import 시 audience가 배열인 공고를 문자열 schema가 거부했다. union 변환을 수정하고 실제 preserved snapshot 테스트와 CLI import를 다시 통과시켰다. 이 초기 실패는 최종 성공 증거로 대체 표시하지 않는다. 이번에는 npm ci/audit 재실행하지 않았으므로 과거 #39 결과를 이번 결과로 주장하지 않는다.

## 공식 수집 증거 — fixture와 별도

웹 도구로 [if(kakao) 공식 페이지](https://if.kakao.com/2026)를 열어 신청/마감/선정 조건을 확인했다. FEConf 웹 도구는 internal access error였지만 실제 shell/Node HTTPS 수집은 성공했다. 이를 공식 사이트 다운으로 해석하지 않았다.

compiled collector 실제 실행 후 같은 격리 DB를 `dist/server.js`에서 제공하는 별도 process smoke도 실행했다. Node 24.21.0 inline script가 임시 디렉터리 SQLite 생성, 두 `refreshSource` 호출, 독립 임시 OTP_SECRET 생성, PORT=4319 서버 시작, 실제 fetch 검증, SIGTERM/임시 DB 삭제를 수행했다. 공통 integration server는 시작하거나 변경하지 않았다.

| Source | Semantic checked UTC | HTTP/body | SHA256 | 실제 결과 |
| --- | --- | --- | --- | --- |
| https://if.kakao.com/2026 | 2026-09-27T03:16:36.246Z | 200, 573993 bytes | a60a6aed76e113e2b24e3ae5b6d75e681ed031b29f68a50886c516e30d42d97b | verified/open, 모집 true, deadline 2026-09-28T03:00:00Z |
| https://2026.feconf.kr/ | 2026-09-27T03:16:36.287Z | 200, 60694 bytes | 5e80b9d7e75bdbcdbc55800a409ff8349bac56113152dda42282feb62f8b2b97 | verified/scheduled, 모집 false |

compiled HTTP는 2 organizations/2 programs/2 activities/1 recruiting, strict schema 통과, 미인증 profile 401, stdout/stderr 비어 있음, 종료 성공. 이 smoke 이후 최종 read snapshot transaction 변경은 위 20 HTTP tests/build/typecheck/lint로 재검증했고 compiled process smoke를 반복하지 않았다. `/tmp` CLI DB들은 검사 전용이며 운영 DB나 지속 서비스로 사용하지 않는다.

조율자 메시지에 따르면 통합 checkout에서 별도 실제 source 수집 서버를 52777에 제공하여 양 앱 검증을 진행 중이다. 이는 조율자가 실행한 결과이며 이 worker가 플랫폼 실행을 확인한 증거가 아니다.

## 리뷰·남은 조건

ponytail-review로 최종 추가 모듈/SQL/CLI/호출 흐름을 읽었다. 불필요한 dependency·Repository·generic crawler·cache abstraction을 추가하지 않았고 추가 삭제 후보 없음 — **Lean already. Ship.** 정확성/저장 회귀는 위 테스트로 별도 검증했다. 별도 CLI가 갱신할 때 API read가 일관된 참조 집합을 반환하도록 read transaction을 보강했다.

[#49](https://github.com/fixabley/dearby/issues/49): 수집은 수동 CLI만 구현. 운영 scheduler/중복 실행 방지/실패 경보/파서 변경 대응/장기 backup·복구 미검증. 명령 실행을 지속적 최신화로 주장하지 않는다. issue에 원인·영향·실제 evidence·해소 조건·#41/#42/#45 의존성을 기록했다.

[#42](https://github.com/fixabley/dearby/issues/42): 실제 SMTP 수신·운영 HTTPS/비밀·영속 저장/backup·두 플랫폼 실기 서버 검증은 여전히 미완료. [#41](https://github.com/fixabley/dearby/issues/41)의 푸시·캘린더·전체 서비스와 [#36](https://github.com/fixabley/dearby/issues/36)의 외부 로그인 이후 자동입력도 미완료다. 외부 인증·동의·제출을 실행하지 않았다. 두 네이티브 앱 UI/기기 검증은 해당 담당자와 조율자의 소유다.
