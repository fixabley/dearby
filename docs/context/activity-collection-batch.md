# 활동 수집 배치 (Kotlin · Spring Batch)

2026-10-15 사용자 결정으로 활동 수집을 TypeScript 구독 워커에서 Kotlin 멀티모듈의 `batch` 모듈로 옮긴다. 활동 정보 원본은 Kotlin DB다. 2026-10-18 사용자 결정으로 TS 워커 소스(`apps/catalog-worker`)와 `supabase/`를 저장소에서 지웠다([삭제 전](https://github.com/fixabley/dearby/tree/6636e9a6/apps/catalog-worker)).

| 결정 | 내용 |
| --- | --- |
| 실행 | `apps/dearby-api/batch`, 별도 이미지(`batch/Dockerfile`)·compose 서비스 `batch`. 매일 한국 시간 00:00(`@Scheduled`, Asia/Seoul) |
| 모델 | Claude Haiku 4.5(`claude-haiku-4-5`, `dearby.collection.model`) |
| 엔진 | **Claude Code CLI 에이전트 루프만**(2026-10-20 사용자 결정; Anthropic API 엔진·`COLLECTION_ENGINE`·`ANTHROPIC_API_KEY`·`anthropic-java`는 지웠다). `claude -p`, 구독 사용량. 로컬 Mac은 로그인만으로, 컨테이너는 `claude setup-token`으로 만든 `CLAUDE_CODE_OAUTH_TOKEN`. 이미지의 CLI는 옵션을 확인한 2.1.292로 고정. 사용자 설정·세션 저장을 끄고 빈 임시 폴더에서 돌린다. 구독 계정으로 서버 무인 작업을 돌려도 되는지는 이용 조건 확인 필요 |
| 준비 안 됨 | CLI가 없거나 컨테이너에 토큰이 없으면 수집하지 않고 경고만 남긴다 |
| 검토 | 수집 결과는 초안(`activities.publication_status = DRAFT`). 관리자가 `PUBLISHED`로 바꿔야 공개 API에 보인다. 게시된 활동은 다시 수집해도 덮어쓰지 않는다 |
| 수집 대상 | DB의 **모든 프로그램**(2026-10-15 사용자 결정: 섞어서). 프로그램별 설정 `program_collection_settings`(선택, 없으면 기본값): 공식 URL·허용 호스트·주기(`interval_days`, 기본 1)·켜기/끄기 |
| 공식 도메인 | 허용 호스트가 있으면 그 안에서만 검색·원문 확인. 없으면 "프로그램 이름 + 주최 기관"으로 웹 전체를 검색하고, 그 초안은 `activities.official_source = false` |
| 주기 | 마지막 **성공** 수집 날짜부터 `interval_days` 동안은 수집 대상에서 뺀다(1 = 매일). 실패한 날은 다음 날 다시 수집한다 |
| 기록 | `collection_runs`(프로그램 하나 × 한국 날짜 하루당 한 행: 공식 도메인만인지·결과·찾은 수·저장 수·제외 사유·오류·토큰(캐시 포함 입력)·검색 횟수·받은 페이지·`agent_log`). Spring Batch 실행 기록은 `batch_*` 테이블 |

## 수집 흐름 — 에이전트 루프(2026-10-20)

예전 고정 순서(검색 → 최대 5쪽 받기 → 페이지마다 추출 → 대조)를 도구를 쓰는 에이전트 루프로 바꿨다([바꾸기 전](https://github.com/fixabley/dearby/blob/41d27044/docs/context/activity-collection-batch.md)). 판단은 모델이, 경계·대조·저장은 코드가 한다.

1. **준비**(`CollectActivitiesService`): 조심 목록·이전 기수·태그 사전·운영자 피드백 최근 10건을 읽고 시작 주소 토큰을 펼친다.
2. **도구 서버**(`McpToolServer`): 프로그램 하나의 수집 동안만 127.0.0.1의 임의 포트·실행마다 바뀌는 비밀 경로로 MCP(streamable HTTP, JSON 응답)를 연다. 도구: `fetch_page`·`render_page`·`submit_candidate`·`search_feedback`·`finish`(`AgentToolbox`). 서버 주소는 명령 인자가 아니라 이번 실행 폴더의 소유자 전용 파일(0600)로 넘기고 실행 뒤 지운다.
3. **CLI 실행**(`ClaudeCli`): `claude -p --system-prompt-file <WORKFLOW.md> --mcp-config <파일> --strict-mcp-config --tools WebSearch --allowedTools WebSearch,mcp__dearby__… --max-turns 12 --max-budget-usd 1.00 --output-format json`. 요청(표준 입력)은 프로그램·시작 주소·허용 도메인·한도·태그 사전·운영자 피드백 최근 기록·이전 기수·조심 목록. 내장 도구는 WebSearch뿐이다(WebFetch·Bash 없음). 시간 제한 15분.
4. **받기**: `fetch_page`는 서버가 직접 받는다(https, 허용 호스트 정확히 일치, 같은 경계 안의 리다이렉트 3번, 사설·내부 주소 거부, 15초, 3 MB, HTML만). `render_page`와 `renderPages`가 켜진 프로그램의 `fetch_page`는 브라우저 렌더링. 받을 수 없으면 도구 오류로 돌려주고 거절 이유에 남긴다. 페이지 한도 15쪽(실패 포함)을 넘으면 도구 오류. 모델에게는 본문 40,000자와 링크 200개까지 보인다(원문 대조는 자르지 않은 본문). Claude Code의 MCP 결과 한도는 `MAX_MCP_OUTPUT_TOKENS=40000`으로 올린다.
5. **제출마다 판정**(`submit_candidate`): sourceUrl은 이번에 받은 페이지여야 한다 → 원문 대조(근거 구절 10~200자가 원문에 있어야, 신청 주소는 원문 링크에 있을 때만) → 조심 목록 → 운영자 피드백(아래) → 초안 저장. 판정(저장됨·거절됨 + 이유, 주의)을 도구 결과로 돌려주어 모델이 고쳐 다시 낼 수 있다. 같은 회차를 다시 저장해도 한 번으로 센다. **구절이 있다는 것은 모든 필드가 참이라는 증명이 아니다** — 그래서 초안으로만 저장한다.
6. **멈춤**: `finish`, 모델이 끝냄, 또는 턴 한도(CLI `error_max_turns` → `수집 중단: 턴 한도(12)`를 거절 이유에도 남김). 그 밖의 CLI 오류는 실행 실패(FAILED).
7. **기록**: `collection_runs.agent_log`(V42): `workflowVersion`(WORKFLOW.md SHA-256 앞 12자리), 멈춘 이유·한도 여부, 턴·페이지 수, 사용량(캐시 나눔)·`costUsd`(CLI 비용 환산, 구독 청구액 아님), 도구 호출(이름·주소·결과), 운영자 피드백 id(`feedbackInjected`·`feedbackRetrieved`·`feedbackApplied`).

### 작업 절차 파일(WORKFLOW.md)

바뀌지 않는 지시(진행 순서, fetch와 render 고르기, 링크 따라가기, 날짜·기간 해석, 종류·태그·대상자 조건, `search_feedback` 쓰는 때와 따르는 법, 근거 구절, 판정에 따른 행동, 끝내는 때)는 `batch/src/main/resources/collector/WORKFLOW.md`에 있다(2026-10-20 사용자 결정, Kotlin 문자열에서 옮김). 운영에서는 `COLLECTION_WORKFLOW_PATH`(compose: 서버의 `./data/prod/collector/WORKFLOW.md`를 `/app/data/collector/WORKFLOW.md`에 읽기 전용으로 마운트)가 일반 파일이면 그것을, 아니면 jar 안의 파일을 실행 폴더에 꺼내 쓴다. 이미지를 다시 만들지 않고 다음 수집부터 바뀐다. 안전 한도(호스트·페이지·턴·근거 구절·삭제/제외 이름 거절)는 파일 내용과 상관없이 코드가 지킨다.

### 운영자 피드백(collection_feedback, V43·V44)

운영자가 어드민에서 바로잡은 것을 수집 에이전트가 검색해 판단에 쓴다(2026-10-20 사용자 결정, RAG). 종류: `DATE_CURATED`(날짜 문구 → 운영자 날짜·사유), `PERIOD_SWAP`(모집 ↔ 활동 바꾸기), `FIELD_CURATED`(이름·종류·태그·장소·대상자 조건의 수집 값 → 운영자 값), `DRAFT_DISMISSED`(수집 초안 삭제), `WATCH_ALIAS`·`WATCH_EXCLUDE`(조심 목록).

- **남기는 곳**: 어드민 활동 수정·세부 일정 수정·활동 삭제·조심 목록 추가(`AdminActivityInfoService`·`WatchlistAdminService`). 수집으로 생긴 활동(`source_id` 있음)만 남기고, 직접 만든 활동은 남기지 않는다. 한 저장에서 모집·활동 기간을 서로 바꿔 넣으면 `PERIOD_SWAP` 한 행. 조심 목록 항목을 지우면 그 행도 지운다.
- **채우기(V44)**: 지정 일정·운영자 지정 필드(운영자 값만; 고치기 전 수집 값은 남아 있지 않다)·조심 목록에서 옮겼다. 삭제한 초안은 이름이 남아 있지 않아 옮길 수 없다(2026-10-20 이후 삭제부터 쌓인다).
- **쓰는 곳**: (1) 요청에 이 프로그램의 최근 10건. (2) `search_feedback {text, kind?}`: `source_text`의 글자 세 개 단위 유사도(pg_trgm, 0.3 이상) 상위 8건, 같은 프로그램 → 같은 호스트(허용 호스트, 없으면 시작 주소 호스트) → 나머지 순. (3) 제출 판정: 같은 프로그램에서 이름(공백·대소문자 무시)이 `DRAFT_DISMISSED`·`WATCH_EXCLUDE`와 같으면 거절. 활동 기간·세부 일정의 날짜 문구가 `DATE_CURATED` 문구와 유사도 0.6 이상인데 해석이 다르면 저장하되 `주의(운영자 피드백 #id)`를 돌려준다(연도가 바뀌면 정당하게 다를 수 있어 거절하지 않는다).

## 추출·저장 규칙 — 2026-10-18·19 사용자 지시 (현재 상태)

계약은 admin-v1.md("프로그램"·"일정 직접 지정…"·"운영자 지정 필드"·"이전 기수 정보 자동완성")와 native-v1.md("활동 종류"·"대상자 조건")에 있다. 여기에는 수집기 쪽 동작만 적는다.

- **활동 단위**: 활동은 **프로그램의 기수(회차) 하나**다(예 "부스트캠프 10기", "Let'Swift 2026"; 트랙은 따로 지원하는 기수일 때만 별도 활동). 그 기수 안의 선발 단계·테스트·챌린지·교육 기간·결과 발표·면접·마감은 `schedules`에 `kind`(DEADLINE·ANNOUNCEMENT·INTERVIEW·EVENT)로 들어간다. `source_id` = 프로그램 + `roundKey`(페이지 주소 제외). 이전 규칙으로 쪼개져 들어온 초안은 자동으로 합쳐지지 않아 어드민에서 지웠다(지운 초안은 다시 만들지 않음).
- **두 기간의 정의**: 모집 기간(`recruitmentStartAt/EndAt` 또는 `recruitmentStartDate/EndDate`)은 모집·접수·지원·신청·원서·마감 낱말이 붙은 날짜, 활동 기간(`dateLabel`·`startDate`·`endDate`)은 교육·과정·행사·운영·개최가 실제로 진행되는 기간. 같은 날짜를 두 기간에 넣지 않고, 모집 기간만 있으면 `dateLabel`은 빈 문자열. 날짜만 적힌 기간은 종일 일정(`startDate`, `endDateExclusive`)으로 저장하고 `dateLabel`은 원문 보존용. 날짜가 아닌 문구("2026 신입생 모집")는 기간 필드에 넣지 않고, 다른 프로그램의 활동은 뽑지 않는다.
- **종류**: `category`(BOOTCAMP·CONFERENCE·WORKSHOP·STUDY·CLUB·LECTURE·HACKATHON·CONTEST·MEETUP·OTHER, 모르면 null)를 뽑는다. null이면 프로그램 기본 종류(`programs.category`)를 쓴다.
- **대상자 조건**: `eligibility` 트리(어휘·예시를 지시문에 넣음)와 `audience` 원문. 어휘 밖 필드는 서버가 `기타`로 바꾼다.
- **요청에 넘기는 힌트**: (1) 조심 목록 — "활동으로 만들지 않을 이름"(exclude)과 "다른 표기 → 정본 이름"(alias); 저장 전 이름 대조가 보장이고 요청은 힌트. (2) 이전 기수 — 대상자 조건·대상 원문을 "참고(이전 기수)"로(본문에 없으면 null). (3) 운영자 피드백 최근 10건(위 "운영자 피드백"; 2026-10-20 전에는 지정 일정의 날짜 문구 예시 20개였다). (4) 시작 주소 — 토큰을 펼친 주소 목록.
- **저장 때 보호·보완**: 운영자 지정 필드(`curated_fields`: name·nameEn·description·descriptionEn·eligibility·officialUrl·applicationUrl·location·cost·category)와 `schedules.curated` 일정은 덮어쓰지 않는다. `eligibility`·`audience`·`participationType`이 비면 같은 프로그램의 가장 최근 다른 활동(게시 우선)에서 가져와 `inherited_fields`에 적는다(새 초안은 처음 저장 때, 기존 초안은 빈 필드만). 운영자 직접 등록 활동(`source_id` 없음)은 수집이 건드리지 않는다.
- **시작 주소 토큰**: `officialUrl`의 `{year}`·`{yy}`·`{round}`를 `CollectionUrlTemplate`(core)이 올해·내년, 다음 기수·현재 기수로 펼친다(`previousRound`는 같은 프로그램의 가장 최근 활동 이름의 "N기", 없으면 맨 앞·뒤 숫자). `CollectActivitiesService`가 이전 기수를 읽은 뒤 펼친 주소를 요청의 시작 주소로 넘긴다. 기수를 모르면 그 주소는 건너뛰고 거절 이유 `기수 번호를 알 수 없음`을 남긴다(탐색 페이지도 모두 실패하면 결과 FAILED). 어드민 `previousRound`·`previewUrls`는 같은 규칙으로 서버가 계산한다.
- **받은 페이지 기록**: 실제로 받아 읽은 페이지를 `collection_runs.fetched_urls`(V30)에 받은 순서로 남긴다(실패 제외). 어드민 `lastFetchedUrls`는 마지막 성공 수집의 값이다.
- 변경 이력: 2026-10-18 활동 단위·날짜 전용 필드·조심 목록·이전 기수 자동완성([`a148af5d` 이전 문서](https://github.com/fixabley/dearby/blob/a148af5d/docs/context/activity-collection-batch.md)) · 2026-10-19 모집·활동 기간 혼동 수정(사용자 보고; 2026-10-18 수집분에서 "우아한 테크코스 2026"은 활동 기간에 모집 기간 2025.09.29~10.10, "Apple Developer Academy Cohort"는 모집 기간에 과정 기간 5/1~12/8, "Apple Developer Academy 2027"은 모집 마감 = 과정 종료일; 원인은 두 기간을 정의하지 않은 지시문과 필수였던 `dateLabel`; 어드민 `기간 확인` 태그·`모집 ↔ 활동 바꾸기` 추가) · 2026-10-19 주소 토큰·받은 페이지·종류·프로그램 기본 종류·날짜 문구 예시+사유.
- **태그(2026-10-19, native-v1.md "태그")**: 추출 요청에 태그 사전의 한글 이름 전체를 넘기고, 모델은 `tags`(최대 5개)를 그 이름 중에서만 고른다. 저장할 때 사전에 없는 이름은 버리고(띄어쓰기·대소문자 무시, 영문 이름도 맞춤), 하나도 남지 않으면 프로그램 태그(`program_tags`)를 기본으로 쓴다. 운영자가 태그를 지정한 활동(`curated_fields`에 `tags`)은 수집이 연결을 건드리지 않는다. 저장은 `activity_tags`(V34).

## 실행

```sh
cd apps/dearby-api
# 로컬: 개발 DB를 띄우고 claude에 로그인한 뒤 바로 한 번 수집(작업 절차를 바꿔 보려면 COLLECTION_WORKFLOW_PATH=…/WORKFLOW.md)
./gradlew :batch:bootRun --args=--collect-now
# 운영: 상시 실행(매일 자정). 바로 한 번 돌리기
docker compose -f compose.prod.yaml --env-file .env.prod up -d batch
docker compose -f compose.prod.yaml --env-file .env.prod run --rm batch --collect-now
```

같은 프로그램·같은 한국 날짜는 한 번만 수집한다(`collection_runs` 유일 제약). 그날 다시 돌리려면 그날의 `collection_runs` 행을 지운다.

`run --rm batch --collect-now`·`--recall-now`로 띄운 일회성 컨테이너는 수동 작업을 마치면 프로세스를 끝낸다(2026-10-19 사용자 결정, `CollectionLauncher`·`RecallLauncher`가 `SpringApplication.exit`). 그 전에는 스케줄러와 함께 살아 있어 정기 수집과 겹칠 수 있었다(2026-10-19 확인: 7시간 남은 수집 컨테이너). 정기 실행 컨테이너(`up -d batch`)는 그대로 계속 돈다.

## 명함 회수 — 2026-10-19 사용자 결정

같은 batch 컨테이너가 매일 04:00(Asia/Seoul)에 `RecallService`를 돌려 단방향으로 받은 명함(`receipts`)과 비로그인 저장(`guest_cards`) 중 받은 날·저장일 + 30일이 지난 것을 `recalled_at`으로 회수한다(계약 native-v1.md "명함 교환"). 맞교환(명함 주인이 받는 사람의 미철회 명함을 갖고 있음)은 남긴다. Spring Batch 잡이 아니라 트랜잭션 하나의 UPDATE 두 번이다. `--recall-now`로 띄우면 기동하자마자 한 번 실행하고 건수를 로그에 남긴다:

```sh
docker compose -f compose.prod.yaml --env-file .env.prod run --rm batch --recall-now
```

## 아직 없는 것

- 게시 활동의 매일 재확인(삭제한 TS 워커의 `--reverify`에 해당), 실패 알림, 수집 기록(`collection_runs`·`agent_log`) 화면(어드민에는 마지막 성공 날짜·받은 페이지만), 운영자 피드백 목록 화면.
- 에이전트 루프의 실제 실행 검증(2026-10-20 기준 미실행): 테스트는 CLI 대신 대본이 실제 도구 서버를 JSON-RPC로 부른다. 실제 CLI의 streamable HTTP 접속, `--system-prompt-file`·`MAX_MCP_OUTPUT_TOKENS` 동작, 운영 DB에서 한국어 문구의 pg_trgm 유사도는 `--collect-now` 한 번으로 확인해야 한다. 서버의 `./data/prod/collector/WORKFLOW.md`가 없으면 Docker가 그 자리에 폴더를 만들고 jar 안의 파일을 쓴다.
- 과거 기록: 웹 전체 검색의 실제 결과(2026-10-15, cli, 설정 없는 Let'Swift)는 공식 사이트 대신 X 계정(`x.com/letswiftkr`)에서 같은 이름의 초안 2건을 저장했다(`official_source = false`; 2분 45초, 입력 78,210·출력 17,258 토큰; CLI 내장 WebSearch는 검색 횟수가 0으로 기록). `cli` 엔진은 2026-10-15 로컬 개발 DB에서 Let'Swift(`https://letswift.kr/2026/`, 허용 `letswift.kr`)로 실제 수집했다(59초, 찾은 1·저장 1, 일정 7개 시각 정상, 입력 14,776·출력 6,183 토큰, 웹 검색 0회; 신청 주소는 원문 링크와 맞지 않아 비워졌다). 후속 후보: SNS·모음 사이트 제외 목록, 웹 전체 검색에서 찾은 공식 도메인 후보를 관리자에게 제안.
- 2026-10-18 정리 완료: 이 Mac의 launchd 작업(`com.dearby.catalog-subscription-worker`)과 `~/.dearby-deploy`의 TS 워커 배포 사본은 사용자가 `launchctl bootout`과 plist 삭제로 내렸다.

## 브라우저 렌더링 수집 — 2026-10-19

우와콘(`woowacon.com`)처럼 자바스크립트로 내용을 그리는 사이트는 서버 HTML에 글자가 거의 없어(121자) 수집이 "찾음 0"으로 끝났다. 사용자 결정으로 수집 설정에 `renderPages`(`program_collection_settings.render_pages`, V35)를 두고, 켜진 프로그램만 헤드리스 Chromium(Playwright for Java 1.63)으로 페이지를 실행한 뒤 `document.body.innerText`를 본문으로 쓴다(`batch/adaptor/browser/PlaywrightPageFetcher`).

- **언제 켜나**: 어드민 수집 설정의 `브라우저 렌더링 필요`. 서버 HTML을 받았을 때 본문이 거의 없고(수집 보고 "찾음 0", 토큰은 소비됨) 브라우저에서는 내용이 보이는 사이트에서만. 일반 사이트는 끈 채로 둔다(HTML 받기가 더 빠르고 싸다).
- **경계**: HTML 받기와 같다 — https만, 허용 호스트가 있으면 페이지와 하위 요청 모두 허용 호스트 밖은 차단(사설·내부 IP도 차단, `UrlGuard` 공용), 탐색 15초, 네트워크 유휴 대기 최대 5초(넘으면 그때까지 그려진 본문), 본문 3,000,000자. 이미지·글꼴·미디어 요청은 막는다. 실패는 거절 이유에 `렌더링 실패: …`로 남는다. 브라우저는 처음 쓸 때 띄우고 프로그램 하나의 수집이 끝나면 내린다.
- **이미지**: `batch/Dockerfile`이 `eclipse-temurin:25-jre-noble` 위에서 부트 jar 안의 Playwright CLI(`-Dloader.main=com.microsoft.playwright.CLI`)로 `install --with-deps chromium`을 실행해 `/ms-playwright`에 Chromium을 넣는다. api·admin 이미지는 그대로. 로컬(linux/arm64) 빌드 크기 **997 MB**(렌더링 전 batch 이미지 363 MB). 이미지 안에서 `app` 사용자로 Chromium 실행을 확인했다(샌드박스 옵션 없이 동작). 운영 compose는 평소처럼 `up -d --build batch`면 되고 플랫폼 플래그는 필요 없다(호스트 아키텍처의 Chromium을 받는다).
- **테스트**: 실제 브라우저는 띄우지 않는다. `CollectionJobTest`가 가짜 `RenderingPageFetcher`로 렌더링 경로 선택·거절 이유·브라우저 내림을 확인한다.
