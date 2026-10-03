# 구독 Codex 활동 수집 워커 — 완료 및 운영 인계

2026-09-29 검증. 소유 checkout `/Users/jominjun/Documents/dearby/catalog-subscription-collector`, 브랜치 `fixabley/catalog-subscription-collector`. root는 IR 작업으로 전환했고 이 세션이 수집 구현을 독립 소유했다. 감독용 orchestration이나 별도 에이전트를 만들지 않았다. 사용자 요청대로 이 세션/worktree를 유지한다.

## 2026-10-03 워크트리 정리를 위한 실행 경로 이전

사용자 요청으로 담당 세션을 종료하고 워크트리 정리를 진행한다. 위 retain 지시는 이전 시점 기록이다. 워커 파일을 `~/.dearby-deploy/catalog-worker-d7e5da7/apps/catalog-worker/`로 복사하고 소스 파일별 SHA-256 일치를 확인했다. 유휴 상태에서 LaunchAgent를 해제한 뒤 경로만 바꾸어 재등록했다. 환경 파일은 외부 경로에서 0600으로 보존하고 기존 60초 실행 주기·구독 모델·수집 계약은 유지한다.

## 승인과 완료 범위

Supabase Cron으로 DB 프로그램별 일일 작업을 등록하고 **ChatGPT 구독 로그인 + 로컬 `codex exec gpt-6-luna`**로 활동을 수집·최신화한다. API 과금 경로/fallback으로 바꾸지 않았다. 기존 root 서비스·데이터를 보존했고, 자기 checkout에서만 수정·빌드했다. JSON 조건 기능 93de583과 WIP 424aee4는 재작성하지 않았다. 최초 WIP 인계는 [보관본](archive/2026-09-29-collector-wip.md)이다.

- 매일 09:00 KST Cron, 프로그램별 당일 unique 큐, 동시 claim 1개, 10분 lease, 제한된 재시도와 만료 회수.
- 구독 인증/한도 오류 시 전역 일시정지, 관리자 재시도, 시도별 토큰/오류 이력. 인증 저장소 직접 읽기/복사 없음.
- 공식 HTTPS exact host, public IPv4 DNS pinning, redirect/15초 전체 deadline/1 MB/구절 검증. 신청 링크는 실제 `<a href>` 전체 URL과 대조한다.
- 새 활동 초안, collector 소유 상태만 갱신, 관리자 수정·공식 확인·게시·JSON 조건 보존. 새 미확인 값으로 기존 내용을 지우지 않고 review 제안을 남긴다. 중복 가능성을 표시하고 회차 분리한다.
- 0건도 별도 공식 원문 근거를 확인한다. 원문 불가/모든 후보 실패를 성공으로 처리하지 않는다. 후보별 날짜·모집 내용 전체가 검증됐다고 표현하지 않는다.
- 관리자 프로그램 수집 ON/OFF·호스트, 작업 상태/오류/usage/후보/원문/활동 편집 연결·수동 등록·재시도 UI.
- ignored 0600 환경 부트스트랩, launchd 설치/상태/해제, 스케줄 ON/OFF 스크립트, CI 단위/DB/브라우저 검사. CI에 구독 credentials나 실검색을 넣지 않는다.

## 현재 실행 상태 — 2026-09-29 23:10 KST cloud 전환

- Root/API 담당자가 cloud `jsoclzeyybgdjxfuvaqs` (서울) 복원·10개 테이블 row hash·RLS/함수/grants·migration 6개 동등성을 확인했다. main PR #67/f880d41 통합은 root 소유이며 이 브랜치 이력은 보존했다.
- Root가 로컬 Supabase를 backup=true로 종료하고 DB/Storage/edge runtime volumes를 보존했다. 이 세션은 재시작하지 않았다. 검증 시 API54321 listener가 없었다.
- 자기 checkout의 worker `.env.local`은 cloud URL/service_role, admin `.env.local`은 cloud URL/anon을 사용한다. 두 파일0600, 원본 백업은 repo 밖 `~/.dearby-deploy/supabase-cloud/consumer-backups/20260929-230120` (디렉터리0700/파일0600)에 보존했다. 비밀 값은 문서·로그에 기록하지 않았다.
- **http://127.0.0.1:5174/collection** 실행 중. 기존 계정 UI 로그인200, `is_catalog_admin=true`, 프로그램36/활동39/작업28/결과8 조회, 실제 표21행을 확인했다. Supabase 요청 origin은 cloud뿐이었다. 검증 세션만 local scope logout했다.
- API 담당자의 완료 통지: enabled=true/pause_reason=null, Cron `dearby-daily-program-collection`, `0 0 * * *`, GMT 기준 매일09KST, active=true. 이 세션은 설정/Cron을 수정하지 않았다.
- 승인 후 보존한 `~/Library/LaunchAgents/com.dearby.catalog-subscription-worker.plist`를 `launchctl bootstrap gui/501`로 재등록했다. 60초 간격, runs=2, last exit code=0, 유휴 `No collection job ready.`를 확인했다. blocked18 재개나 수동 enqueue를 하지 않았다. 기존 plist의 정상 `--catch-up` 경로는 그대로이며 당일 unique 작업은 중복 등록하지 않는다.
- 재개 전후 성공10/blocked18/running0, 작업·설정 전체 snapshot 불변을 확인했다. 배포 검증 JSON은 위 repo 밖 백업 폴더에 보존한다. 다음날09시 실제 timer firing/장기 무중단은 아직 미검증이다.
- Node24 고정 경로 `/Users/jominjun/.npm/_npx/538786c08bcb9442/node_modules/node/bin/node`, Codex `/opt/homebrew/bin/codex`. 캐시/checkout 삭제 전 안정된 Node 경로로 이전해야 한다. root API/SQLite와 다른 checkout은 변경하지 않았다.

## 실제 구독 실행과 사용량

드로이드나이츠 실제 실행에서 후보 2개 중 원문 구절을 확인한 **1개가 초안 저장**되고 1개는 구절 불일치로 제외됐다. 저장된 활동은 `2026 이력서 공개 피드백 선정`, source `https://droidknights.dev/en`, publication=draft, source_checked_at=null이다. 모집 상태·날짜는 관리자 검토 전의 제안이다. 초기 substring 검사로 통과했던 불완전한 `https://forms.gle`는 exact anchor 검사 추가 후 이 워커가 만든 미수정 초안 한 건에서만 null로 바로잡았고 collector 저장 버전도 함께 맞췄다.

이 실행의 CLI usage는 입력 **84,374**(캐시 입력 **58,624**, 입력의 부분집합), 출력 **758**, 웹 도구 시작 이벤트 **3회**다. 초기 5개 프로그램 smoke는 입력 51,525~84,374, 출력 203~758, 웹 도구 2~3회였으나 성공/원문 실패/과거 회차 제외가 섞인 작은 표본이다. 구독 잔여량·실제 크레딧 차감량은 CLI usage에 없으므로 단정할 수 없다. 28개/일이 기본이고 실패에 따라 최대 3시도/프로그램/일이 가능해 사용량이 커질 수 있다. API 단가로 구독 비용을 계산하거나 무제한 비용0으로 설명하지 않는다. 프로그램별 ON/OFF로 범위를 줄이고 구독 한도 오류 시 자동 정지 후 원인 해소 뒤 재개한다.

초기 FEConf/SOPT/Let’Swift 0건 smoke는 강한 빈 결과 근거 검증 전 실행이었다. 성공 표시는 철회했고 기존 토큰을 `unverified_smoke` 이력으로 보존한 뒤 수정된 워커의 재시도 대상으로 전환했다. FEConf 자동 재실행은 허용 host 불일치/원문 불가로 실제 failed가 기록됐다. NAVER DAN 원문 불가도 failed로 기록됐다. 한때 worker가 HTTP204 응답을 JSON으로 읽어 실패 저장 오류를 잘못 출력했으나 DB 저장은 성공했으며 빈 응답 처리와 subprocess 회귀를 추가했다.

## 실행한 검사

모두 자기 checkout, Node 24 기준이다.

- worker 단위/프로세스 검사 **12개 통과**: 날짜/구절/전체 후보 실패/회차/링크, private DNS/redirect/deadline/크기, API/DB키 미전달, 실제 CLI argv 고정, API 로그인 거부, 실패 usage/204 처리.
- 실제 Supabase queue 통합 **10개 시나리오 + 상위 테스트 = 11개 통과**: anon/viewer/admin 경계, 동시 claim, null/중복/위험 필드 거부와 rollback, idempotency, 관리자/공식 확인/게시 보존, 정보 손실 review, 같은 제목의 다른 회차, 만료/3회 제한/재시도, 구독 전역 pause, 시도별 usage, 일일 중복/과거 backlog/cron, 비활성 프로그램.
- 기존 DB/Auth/RLS/HTTP/JSON 조건 회귀 **8개 통과**. 기존 관리자 단위 **6개 통과**.
- 실제 관리자 Playwright **3개 통과**: 기존 전체 편집/게시·숨김·충돌, 모바일, 새 수집 설정/큐/재시도/원문/편집. 새 UI fixture는 UUID로 정리했다. 기존 전체 E2E의 숨긴 테스트 행은 보존했다.
- admin build/typecheck·oxlint, worker syntax/oxlint, git diff whitespace 통과. Vite의 기존 대형 chunk 경고는 남는다(~1.57 MB JS/~485 KB gzip). 기능과 무관한 전체 번들 개편은 하지 않았다.
- 새 화면 [데스크톱](../../apps/admin/docs/evidence/collection.png)·[모바일](../../apps/admin/docs/evidence/collection-mobile.png)을 실제로 열어 확인했다. 다른 화면의 재생성 스크린샷은 원래 추적 버전으로 되돌려 불필요한 diff를 제외했다.
- ponytail-review: 사용하지 않는 randomUUID import 제거, 전체 deadline과 중복인 request idle timeout 3줄 및 해당 mock 1줄 제거. URLSearchParams key 배열은 순회 중 삭제 시 항목 누락을 방지하므로 유지했다. DB/권한/저장/보안 검사는 축소하지 않았다.

네이티브 앱 코드를 바꾸지 않아 iOS/Android 빌드·기기 검사는 이번 작업에서 실행하지 않았다. GitHub CI는 로컬 검사와 구분하며 push 이후 실행 상태를 따로 확인한다.

## 남은 외부 조건 / 운영 경계

[운영 후속 #57](https://github.com/fixabley/dearby/issues/57), 기존 [#49](https://github.com/fixabley/dearby/issues/49) 추적. 이슈 작성으로 해결 처리하지 않는다.

- FEConf 등 현재 공식 host는 관리자가 근거 확인 후 allowlist를 갱신해야 한다. exact host를 임의 확장하지 않았다.
- 빈 HTML/JS 전용/봇 차단/IPv6-only/로그인 출처는 대체 원문 또는 별도 수집 방식이 필요하다.
- Mac 로그인/전원/네트워크와 ChatGPT 로그인/구독 한도 유지가 필요하다. DB는 cloud로 전환했지만 워커 상시 호스트, 외부 이메일/푸시 실패 경보, 정기 백업 정책 및 장기 연속 Cron/재부팅 검증은 남는다.
- 후보 구절 일치는 사실 전체의 검증이 아니다. 자동 공식 확인·게시하지 않는다. 의미상 같은 활동의 URL/회차 이름이 바뀌면 중복 후보가 생길 수 있다. 후보 테이블은 최신 제안만 보존한다.
- 로그는 ignored 파일로 쌓이며 장기 보관 용량 정책이 필요하다. 구독 한도는 일반 Codex/IR 작업과 공유한다.

[실행·해제·검증 안내](../../apps/catalog-worker/README.md)를 따른다. 기능별 커밋/자기 브랜치 push 결과는 아래 전달 기록과 이 세션 최종 응답에 남긴다. 강제 push·root 통합·기존 서비스 교체는 하지 않는다.

## 전달 기록 — 2026-09-29 13:33 KST

- `403ee92`: 구독 실행·원문 검증·DB 큐/저장 안전성 및 회귀.
- `d32c9d4`: 프로그램 설정·수집 검토 UI 및 브라우저 검증.
- `0755157`: launchd·CI·운영 인계.
- 위 3개 커밋을 `origin/fixabley/catalog-subscription-collector`에 push했고 [Draft PR #58](https://github.com/fixabley/dearby/pull/58)을 `feat/discovery-admin` 기준으로 생성했다. root에 merge/cherry-pick하지 않았다.
- GitHub CI는 PR에서 실행 중이다. 최종 결과는 PR checks와 이 세션 최종 응답을 따른다. 로컬 검사와 원격 CI를 혼동하지 않는다.
- root admin5173/API58765의 HTTP200, 로컬 비밀 파일0600, launchd 실제 동작 및 오늘 28개 큐를 재확인했다. 13:32 스냅샷은 succeeded2/failed5/queued21이며 계속 바뀐다. 실패를 성공으로 치환하지 않는다.
- ponytail 반영 후 추가 불필요한 추상화·의존성은 발견하지 않았다: Lean already. Ship.


### 원격 CI 후속

첫 최신-head CI에서 build/lint/단위/새 DB migration/큐 통합/수집 UI는 통과했으나 기존 JSON 태그 입력 뒤 열린 dropdown이 다음 버튼을 가려 기존 E2E가 실패했다. `admin.spec.ts`에 Enter 뒤 Escape로 dropdown을 닫는 사용자 동작을 명시했다. JSON 조건 구현/저장 계약은 변경하지 않았다. 추가 브라우저 재검증 후 CI 재실행 결과는 PR checks를 따른다.
