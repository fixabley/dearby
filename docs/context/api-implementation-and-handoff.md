# API — 구현과 인계

검증 시점: 2026-09-27 05:55 KST (2026-09-26 20:55 UTC). #39 첫 수직 구현이며 제품 전체·운영 배포 완료가 아니다. 정본은 `docs/product/native-spec-2026-09.md`, 전송 계약은 `shared/contracts/native-v1.md`다. 이전 scaffold 설명은 현재 트리와 달라 교체했다.

## 담당 및 상태

- checkout: `/Users/jominjun/Documents/dearby/dearby-api`, branch `fixabley/dearby-api`, 시작 base `8bb694c`.
- Orca worker terminal: `term_ae3d042f-c8ad-44cc-82e1-686f8be3b249`; task `task_588982931d2d`, dispatch `ctx_ce111f22ff1f`. 이는 세션 식별자이며 검증 시각과 별개다.
- 소유 범위: `apps/dearby-api`, 이 문서, `docs/workstreams/api.md`만 변경. root/shared/다른 앱은 변경하지 않았다. 하위 worker 없음.
- 완료 보고 뒤 세션은 사용자 요청에 따라 유지하며 조율자가 retain 처리한다. push/PR은 하지 않았고 조율자가 통합한다.

## 구현

Node 24.21.0 LTS, Fastify 5.12.5, better-sqlite3 13.0.3, Zod 4.6.5, Nodemailer 10.0.10, TypeScript 7.0.2/tsx 4.23.15를 공식 npm registry/문서로 확인 후 고정했다. `@types/node`는 런타임과 맞는 24.19.0이다. Node 26은 호스트 기본값이지만 Current이므로 검증 명령에서 Node 24를 명시했다. 근거 링크는 [API README](../../apps/dearby-api/README.md)에 있다.

SQL migration + foreign keys + WAL + synchronous FULL + 짧은 동기 transaction을 사용한다. ORM/Repository/DI 계층 없이 인증, 공개 projection, wallet 라우트를 나눴다. 개인 프로필·계정·세션 해시·OTP 해시/시도/만료·rate limit·발행 스냅샷·수신 이력·멱등 키가 SQLite 파일에 영속된다.

계약의 모든 첫 수직 endpoint를 구현했다: challenge/session/revoke, owner profile GET/PUT, own cards list/publish, public read/owner revoke, authenticated wallet/import, direct exchanges. OTP는 응답하지 않으며 로그인 성공만 계약대로 sessionToken을 반환한다. 로그에 토큰·코드·HTTP 본문을 남기지 않는다. OTP HMAC-SHA256, 5분 만료/5회 시도/1회 사용, 메일당 분당 1회·시간당 5회/발신 IP 시간당 20회/확인 IP 분당 60회 제한. 세션은 무작위 256비트, SHA256 저장, 30일 만료·개별 폐기다.

SMTP는 명시적 설정 없으면 HTTP 503으로 실패한다. 실제 SMTP 어댑터는 TLS 필수·인증서 검증·timeout을 사용하며 원본 오류를 클라이언트에 노출하지 않는다. 메모리 메일 sink는 test 폴더 안에서 NODE_ENV=test를 확인하고 주입하며 서버 실행 경로/환경 옵션에는 없다. `.env.example`만 검토·작성했고 비밀을 탐색하지 않았다.

공개 카드에는 선택한 연락처·이력만 포함하고 프로필 수정이 기존 카드에 전파되지 않는다. 철회는 공개 재조회 404, 새 wallet 응답에서 필터링하며 DB 수신 이력은 보존한다. 조율 피드백을 반영해 revoked 내용을 wallet으로 재노출하던 초안 경로와 allowRevoked 옵션을 제거했다. tombstone 모델이 없어 숨겨진 카드 안내 UX는 #41 후속이다.

import는 항목별 transaction과 순서 보존 결과를 사용한다. 실패/유효하지 않은 항목은 failed/null이며 다른 항목 성공을 되돌리지 않는다. 동일 사용자/card 중복은 기존 직접 전달까지 포함해 alreadySaved다. 앱은 성공한 선택 항목만 기기에서 제거해야 한다. savedAt은 guest 수신 시각으로 보존하고 direct exchange는 서버 시각을 사용한다. sender/requestId 재시도는 같은 receipt/time, 검증된 본문 변경은 409. 새 requestId는 별도 반복 이력을 만든다. receipt 및 idempotency 저장은 원자적이다. 철회 전 완료된 요청의 재시도는 기존 receipt/time만 반환하며 새 전달은 404다. reciprocal은 양방향 실제 수신 이력에서 계산한다.

ExchangeContext는 activity UUID/자유 label/둘 다 null 중 하나이며 양쪽 동시 입력은 422다. 등록 활동의 실제 존재/참가를 검증했다고 주장하지 않는다. 이전 catalog slug는 공통 UUID 매핑 후 연결해야 한다. 직접 자기 자신에게 전달은 422인 가역적 초기 선택이다. contact URL scheme은 HTTPS만 허용하고 일반 전화/이메일/핸들 텍스트는 지원한다. 외부 URL을 여는 네이티브 측 검증도 필요하다.

## 실제 실행한 검증

`apps/dearby-api`에서 Node 24.21.0으로 실행했다. 호스트 재현은 `npm exec --yes --package=node@24.21.0 -- npm --prefix apps/dearby-api <command>`; 해당 디렉터리에서 nvm use 후 일반 npm 명령도 가능하다.

- `npm ci`: 깨끗한 의존성 재설치 통과, npm audit 결과 0 vulnerabilities. npm 11의 install-scripts 승인 안내는 출력됐지만 native DB 로드와 전체 테스트가 실제 통과했다.
- `npm test`: 10/10. Fastify inject가 아닌 임시 포트의 TCP listener + fetch, 파일 SQLite, 테스트별 임시 디렉터리를 사용한다.
- `npm run typecheck`, `npm run lint`, `npm run build`: 모두 exit 0. lint 경고 없음.
- HTTP 검증: OTP 비반환/해시/한 번 사용/오입력 누적/만료/재전송/계정 재로그인 동일 ID/세션 만료·폐기, 이메일·IP quotas, SMTP 미설정 fail-closed.
- HTTP 검증: owner 보호, 숨긴 필드/로그인 이메일 비노출, 스냅샷 불변, 타인 revoke 차단, 철회 공개/지갑 비노출, 입력 오류 및 위험 scheme/공백·제어문자 차단.
- HTTP 검증: import 부분 실패·중복·동시 요청·수신 시각/맥락 보존, 직접 전달 후 import 중복, 다른 사용자 wallet 분리, 반복 교환·동시 replay·키 순서 무관 동일 요청·body mismatch, reciprocal.
- SQL trigger 실패 주입 후 실제 HTTP: exchange receipt와 멱등 키 모두 rollback, import 실패 항목만 rollback 및 재시도 성공.
- DB/서버 재개방 후 HTTP: 프로필·세션·wallet·철회·멱등 replay·quota·단일 migration 적용 유지.
- 별도 compiled `dist/server.js` 프로세스 smoke: HTTP 인증 없는 profile 401, SMTP 미설정 challenge 503, SQLite 파일 mode 0600, stdout/stderr 요청/비밀 로그 없음, SIGTERM 종료 확인. 이 smoke는 이번 실행의 증거이며 자동 test suite에는 포함하지 않았다.
- `git diff --check`: 통과.
- ponytail-review: 실제 모듈·diff·호출 흐름을 검토했고 추가 제거 후보 없음 — `Lean already. Ship.` 보안/정확성은 위 회귀로 따로 확인했다.

초기 TypeScript unknown 오류 처리와 persistence test의 빈 DELETE에 잘못 붙인 JSON Content-Type은 수정 후 재실행했다. 최종 결과에 실패를 숨기지 않으며 이전 9/10 실행은 최종 통과 증거가 아니다.

## 통합 커밋

- `8051170`: SQLite/migration/runtime 및 안전한 이메일 OTP·세션·HTTP 테스트.
- `00e7751`: owner profile 및 공개 명함 projection/철회·HTTP 테스트.
- `25d2311`: 항목별 import, 멱등 direct exchange/상호성, 재시작·rollback 회귀.
- `b8ace11`: Node LTS 타입 정렬과 quota/위험 입력 회귀.
- 이 인계 문서 갱신은 별도 docs 커밋으로 뒤따른다. 조율자는 브랜치 전체를 통합하며 shared 계약을 담당자가 변경하지 않았음을 확인한다.

## 남은 조건

[이슈 #42](https://github.com/fixabley/dearby/issues/42): SMTP 실제 수신, HTTPS 운영 환경/비밀 주입·교체, 단일 호스트 SQLite 저장/백업·복구, trusted proxy IP 제한, 운영 모니터링·데이터 수명 관리, 두 플랫폼 실제 기기/서버 연동 증거가 필요하다. 생성만으로 해결 처리하지 않았다. 운영 DB scaling/고가용성/백업 검증은 미완료다. 이 실행은 메일 테스트 sink로만 인증했으므로 AUTH-01 실메일 수신 완료를 뜻하지 않는다.

활동 수집·푸시 APNs/FCM·캘린더·if(kakao)는 이 vertical API 계약 범위에 없다. 관련 전체 서비스 조건은 여전히 남아 있고, 실 SMTP/APNs/배포 비밀은 확보하지 않았다. 이 작업은 로컬 HTTP 수직 구현 완료로만 인계한다.
