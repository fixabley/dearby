# Dearby — 사용자 가치와 핵심 기술

작성일 2026-10-19. 저장소 `main`(PR #271 직전)과 운영 반영 상태를 기준으로, 계약(`shared/contracts/native-v1.md`·`admin-v1.md`), 컨텍스트 문서(`docs/context/*`), API·앱·웹 소스를 직접 읽고 정리했다. 코드에서 확인하지 못한 항목은 그렇게 표시했다. "설계 완료·구현 예정"은 계약에는 있으나 코드가 아직 없는 것이다.

2026-10-20 갱신: 활동 수집을 Claude Code CLI 에이전트 루프(로컬 MCP 도구 서버, WORKFLOW.md 작업 절차, 운영자 피드백 검색)로 바꾸고 Anthropic API 엔진을 지웠다. 2-1과 관련 줄만 고쳤고 나머지는 2026-10-19 기준이다. 세부는 [활동 수집 배치](../context/activity-collection-batch.md).

---

## 1부. 사용자가 얻는 가치

Dearby는 **개발·창업 활동(부트캠프·컨퍼런스·해커톤·동아리 등)을 한곳에서 찾고, 거기서 만난 사람과 명함을 교환하고, 그 관계를 활동 맥락과 함께 관리하는** 서비스다. 활동 정보는 운영자가 손으로 넣는 것이 아니라 **공식 안내 페이지를 매일 수집·검증해 초안으로 만들고 운영자가 게시**한다.

### 1-1. 활동을 찾는 사용자 (학생·주니어 개발자)

| 겪는 문제 | Dearby가 주는 것 |
| --- | --- |
| 모집 공지가 기관 사이트·SNS·커뮤니티에 흩어져 있고, 마감을 놓친다 | **발견 탭**: 프로그램(부스트캠프, SLASH, Let'Swift…) 단위로 묶어 **모집 마감이 이른 순**으로 보여 준다. 프로그램을 펼치면 지금 모집 중인 기수(활동)가 **종류별**(부트캠프·컨퍼런스·워크샵·스터디·동아리·강연·해커톤·공모전·밋업)로 묶여 나온다. |
| "나도 지원할 수 있나"를 공지 원문을 읽어야 안다 | **대상자 조건**을 구조화해(나이·학년·학력·재학상태·전공·지역·취업상태·경력·국적·팀규모) 상세에 칩으로 보여 주고, API는 `age=24`, `educationStatus=enrolled` 같은 필터를 받는다(앱 필터 UI는 아직 없음). |
| 지난 기수 정보가 없어 프로그램 성격을 모른다 | **모집 중만 보기** 토글을 끄면 최근 2년 안에 활동이 있었던 프로그램까지 보이고, 펼치면 모집 중이 없을 때 `지난 활동` 목록이 나온다. |
| 지원한 뒤 서류·테스트·면접·발표가 어디까지 왔는지 흩어진다 | **내 활동 진행 트랙**: 신청 표시한 활동을 `지원 → 세부 일정 → 활동` 단계 트랙으로 보여 주고 `D-3 · 면접`처럼 다음 단계까지 남은 날을 배지로 보인다. 단계마다 통과·대기·탈락을 직접 표시한다(기기 메모리, 주최 측 결과가 아님). |
| 일정이 겹치는지 모른다 | 활동 상세의 `겹치는 시간 확인`. 계약은 기기 캘린더 읽기(iOS EventKit·Android READ_CALENDAR)를 기기 안에서만 비교하도록 정했으나, **현재 앱 코드에는 EventKit·CalendarContract 호출이 없고 고정 예시 시간과 비교하는 화면만 있다**(설계 완료·구현 예정). |
| 계정 만들기가 귀찮다 | 패스키(iCloud 키체인·Google 비밀번호 관리자 동기화)로 가입과 로그인이 한 동작. Apple 로그인, Google 네이티브 로그인(운영 콘솔 값 입력 전)으로도 같은 계정을 쓴다. 둘러보기는 로그인 없이 된다. |

### 1-2. 활동에서 만난 사람과 명함을 교환하는 사용자

| 겪는 문제 | Dearby가 주는 것 |
| --- | --- |
| 종이 명함은 잃어버리고, 디지털 명함은 "어디서 만났는지"가 남지 않는다 | **명함 교환**: 내 명함의 `QR 보이기` 한 번. QR에는 공개 명함 주소와 **지금 진행 중인 내 활동**이 자동으로 붙는다. 받은 쪽 지갑에는 `부스트캠프 10기에서 받음`처럼 프로그램·활동이 남는다. |
| 명함을 만들 때 어디까지 공개할지 걱정된다 | 프로필(데이터)과 명함(발행 스냅샷)을 분리했다. 명함은 **공개할 연락처·승인된 이력만 골라** 발행하고, 발행 뒤 프로필을 고쳐도 바뀌지 않는다. 직접 입력한 이력은 운영자 승인 전에는 명함에 넣을 수 없고, 인증자료를 올리면 `확인됨` 배지가 붙는다. |
| 한 번 건넨 연락처를 연락 없는 사람이 계속 들고 있는 것이 싫다 | **30일 회수**: 서로 명함을 주고받지 않은(단방향) 명함은 받은 날부터 30일 뒤 서버가 지갑에서 거둔다. 받은 쪽은 `N일 뒤 사라져요`를 보고 `내 명함도 건네기`로 맞교환하면 남는다. |
| 앱이 없는 사람에게는 못 건넨다 | QR을 폰 카메라로 읽으면 웹에서 명함을 보고 **로그인 없이 브라우저에 저장**할 수 있다(쿠키 세션, 홈 화면 추가 시 1회용 코드로 세션 잇기). 앱이 있으면 `Dearby 앱에 저장`으로 계정 지갑에 들어간다. |
| 받은 명함이 쌓이면 못 찾는다 | 받은 명함은 Outlook 연락처처럼 초성·A–Z 색인 목록이고, 행에 직무·소속 칩·프로그램 칩·`맞교환`/`N일 뒤 사라져요` 배지가 붙는다. |

### 1-3. 운영자(어드민)

| 겪는 문제 | Dearby가 주는 것 |
| --- | --- |
| 공지를 매일 찾아 손으로 옮기면 비용이 크고 틀린다 | **수집 배치**가 매일 자정 프로그램별 공식 페이지를 받아 Claude로 구조화하고, 원문 대조를 통과한 것만 초안으로 저장한다. 운영자는 초안을 보고 게시만 한다. |
| 모델이 틀리게 읽은 값을 고치면 다음 수집이 덮어쓴다 | **운영자 지정 보호**: 어드민에서 고친 필드(`curated_fields`)와 직접 지정한 기간(`schedules.curated`)은 재수집이 건드리지 않는다. 고친 날짜·필드·기간 바꾸기·삭제·조심 목록은 **운영자 피드백**(`collection_feedback`)으로 쌓여, 수집 에이전트가 요청(최근 10건)과 검색 도구로 찾아 같은 실수를 줄이고, 지운 초안과 같은 이름은 저장 전에 거절된다. |
| 회차마다 주소가 바뀐다(toss.im/slash-23, slash-24) | 수집 주소에 `{year}`·`{yy}`·`{round}` 토큰을 쓰면 올해·내년, 다음·현재 기수 주소로 펼쳐 차례로 본다. 실제로 읽은 페이지가 기록된다. |
| 같은 기수가 다른 이름으로 중복 수집된다 | **조심 목록**: 이름을 "다른 표기(정본으로 합침)" 또는 "제외"로 지정하면 수집기가 피한다. |
| 활동이 많아지면 찾기 어렵다 | 활동 화면이 **조직 → 프로그램 → 종류 → 활동** 구조고, 프로그램에 기본 종류를 두어 활동에 일괄 적용한다. 활동 직접 추가·프로그램 편집·삭제가 있다. |
| 직접 입력 이력·직무를 누가 검증하나 | 이력 승인·거절(사유), 인증자료 확인, 직무 승인 화면. |

### 1-4. 설계 완료·구현 예정 (계약에만 있음)

- **태그(얻을 수 있는 것)**: 창업·창의성·개발지식·프로젝트 개발·협업·멘토링 등 통제 어휘를 프로그램·활동에 붙이고 검색·필터에 쓰는 설계(`native-v1.md` "태그(얻을 수 있는 것)", V34 예정). 어드민은 검색형 다중 선택 + 직접 입력으로 사전에 추가.
- **프로그램 상세 화면**(이전 활동 목록): 발견 탭의 `지난 활동`으로 핵심을 대체했고, 별도 상세는 결정 대기.
- **기기 캘린더 읽기**: 위 표 참고. 계약은 있고 코드는 없다.
- **Google 로그인 운영 가동**: 코드는 배포됐지만 `GOOGLE_CLIENT_IDS`와 앱 빌드 값이 비어 있어 버튼이 숨겨진 상태.
- **활동 대상자 필터 UI**(앱·웹): API만 있다.

---

## 2부. 핵심 기술 (상세)

### 2-0. 전체 구조

| 구성 | 기술 | 위치 |
| --- | --- | --- |
| API 서버 | Kotlin · Spring Boot(가상 스레드 `spring.threads.virtual.enabled`) · jOOQ(생성 코드 커밋) · Flyway V1–V33 · PostgreSQL 17 + pgvector 이미지 | `apps/dearby-api` 멀티모듈: `core`(도메인·애플리케이션·jOOQ 영속화), `auth`(패스키·JWT·Apple·Google·보안 설정), `api`(공개/회원 API), `admin`(운영자 API, 별도 컨테이너), `batch`(수집·회수) |
| 사용자 웹 | Next.js 16 · React 19 · zod 4, 같은 출처 `/api/*` 프록시 | `apps/web` |
| 어드민 웹 | Vite 7 · React 19 · Ant Design 5 · TanStack Query 5, Vercel rewrite로 `/api/auth/*`·`/api/admin/*`를 API로 전달 | `apps/admin` |
| iOS | SwiftUI, Swift 6 strict concurrency, iOS 18+, 생성형 Xcode 프로젝트(xcodeproj gem), 유일한 외부 패키지 GoogleSignIn-iOS 10.x | `apps/ios` |
| Android | Kotlin · Jetpack Compose, minSdk 26 / target·compile 36, Credential Manager, zxing core(QR 인코딩만) | `apps/android` |
| 인프라 | Docker Compose(`compose.prod.yaml`: postgres·api·admin·batch, 모두 127.0.0.1 바인딩), 앞단 nginx(TLS·프록시), Vercel(web·admin) | `apps/dearby-api/compose.prod.yaml`, `infra/nginx` |

표기 규칙(2026-10-18 사용자 지시): DB 열 이름만 snake_case, JSON 키·쿼리 키·코드·어휘는 camelCase. 어휘는 한글 기준에 영문 쌍을 함께 저장한다.

---

### 2-1. 활동 수집 파이프라인 (`batch` 모듈)

**실행**: `CollectionLauncher`가 매일 00:00 Asia/Seoul(`@Scheduled(cron = "0 0 0 * * *")`)에 Spring Batch 잡 `collectActivities`를 띄운다. `--collect-now` 옵션이면 기동 직후 한 번 더 실행한다. 엔진 준비(`CollectionReadiness`)가 안 되면(CLI·토큰 없음) 실행하지 않고 경고만 남겨 컨테이너 재시작 루프를 막는다. 모델은 `claude-haiku-4-5`(`CollectionProperties.model`). 엔진은 Claude Code CLI 에이전트 루프 하나다(2026-10-20; `claude -p`, `CLAUDE_CODE_OAUTH_TOKEN`, 이미지의 CLI 2.1.292 고정, 사용자 설정·세션 저장을 끄고 빈 임시 폴더에서 실행, 환경변수는 허용 목록만 전달, 프로그램당 15분·`--max-turns 12`·`--max-budget-usd 1.00`).

**대상 선정** (`JooqCollectionTargetRepository.findDue`): 모든 프로그램이 대상이다. `program_collection_settings`(V8)가 있으면 `official_url`·`allowed_hosts[]`·`interval_days`(≥1, 기본 1)·`enabled`를 쓰고, 없으면 기본값(웹 전체 검색, 매일). "마지막 **성공** 수집일 + interval_days > 오늘"이면 건너뛴다. `collection_runs`는 `(program_id, run_date)` 유일 제약으로 같은 날 중복 실행을 막는다.

**단계** (`CollectActivitiesService.collect`, 한 프로그램당):

1. **힌트 로딩**: 조심 목록(`collection_watchlist`, V24), 이전 기수(`findPreviousEdition`: 같은 프로그램의 다른 활동 중 게시 우선·최근 생성 순 1건의 이름·대상 원문·대상자 조건·참여 방식), 태그 사전, 운영자 피드백 최근 10건(`collection_feedback`, V43; 2026-10-20 전에는 지정 일정의 날짜 문구 예시 20개).
2. **시작 주소 펼치기** (`CollectionUrlTemplate`, core 순수 함수): `{year}`(4자리)·`{yy}`(2자리)는 올해·내년, `{round}`는 이전 기수 번호 +1과 현재. 기수 번호는 이전 활동 이름의 `(\d+)\s*기`에서, 없으면 이름 맨 앞·뒤 숫자(`SLASH 24` → 24). 토큰이 여럿이면 조합(최대 4개, 다음 연도·다음 기수 먼저). `{round}`인데 기수를 모르면 빈 목록이고 거절 이유 `기수 번호를 알 수 없음`을 남긴다. 검증은 어드민 저장 시 모르는 `{…}` 400, https 검사는 샘플 값(2026, 1)으로 치환한 주소로 한다.
3. **에이전트 루프** (`ClaudeCliActivityAgent`, 2026-10-20): 프로그램 하나의 수집 동안 127.0.0.1의 임의 포트·비밀 경로로 MCP 도구 서버(`McpToolServer`, streamable HTTP의 JSON 응답만)를 열고, 그 주소를 소유자 전용 임시 파일(0600)로 `--mcp-config`에 넘긴다. 작업 절차는 `--system-prompt-file`의 `WORKFLOW.md`(jar 안 `collector/WORKFLOW.md`, 운영은 `COLLECTION_WORKFLOW_PATH` 마운트 파일이 있으면 그것). 내장 도구는 `WebSearch`만이고 도구는 `fetch_page`·`render_page`·`submit_candidate`·`search_feedback`·`finish`(`AgentToolbox`). 모델이 어떤 페이지를 받을지·무엇을 제출할지 정하고, 경계·대조·저장은 코드가 한다. 페이지 한도 15쪽(도구 서버가 거절), 모델에게 보이는 본문은 40,000자와 링크 200개.
4. **원문 받기** (`fetch_page` → `JsoupPageFetcher`, `render_page`와 렌더링 설정 프로그램은 `PlaywrightPageFetcher`): https만, 허용 호스트가 있으면 정확히 일치, 리다이렉트는 수동 추적 최대 3회(같은 경계 안), 호스트의 모든 IP가 루프백·사설·링크로컬·any가 아니어야 함(SSRF 방지), 연결·요청 타임아웃 15초, `Content-Type`에 html만, 본문 **3,000,000바이트** 초과 시 중단, User-Agent `DearbyCollector/1.0`. Jsoup으로 `body().text()`와 `a[href]` 절대 URL 집합을 만든다. 받을 수 없으면 도구 오류로 모델에게 돌려주고 거절 이유에 남긴다.
5. **추출 규칙**(`WORKFLOW.md`, 모델이 `submit_candidate` 입력 스키마 `CollectionPrompts.SUBMIT_SCHEMA`로 활동 하나씩 제출):
   - **활동 = 프로그램의 기수·회차 하나**("부스트캠프 10기", "Let'Swift 2026"). 선발 단계·테스트·챌린지·교육·OT·발표·면접·마감은 활동이 아니라 그 활동의 `schedules`. 트랙은 따로 모집할 때만 별도 활동. `roundKey`는 기수·연도(·트랙)만.
   - **두 기간 정의**: 모집 기간(`recruitmentStartAt/EndAt` 또는 `recruitmentStartDate/EndDate`)은 모집·접수·지원·신청·원서·마감 낱말이 붙은 날짜, 활동 기간(`dateLabel`·`startDate`·`endDate`)은 교육·과정·행사·운영·개최 날짜. 같은 날짜를 두 곳에 넣지 않고, 모집 기간만 있으면 `dateLabel`은 빈 문자열.
   - 시각은 본문에 시각이 있을 때만 ISO 8601 오프셋, 날짜만이면 `yyyy-MM-dd`(끝 포함). 연도가 없으면 본문의 다른 날짜로 알 수 있을 때만.
   - `schedules[].kind`: DEADLINE·ANNOUNCEMENT·INTERVIEW·EVENT. `participationType`: REGISTRATION·SELECTION. `category`: BOOTCAMP·CONFERENCE·WORKSHOP·STUDY·CLUB·LECTURE·HACKATHON·CONTEST·MEETUP·OTHER, 모르면 null.
   - `evidenceQuote`: 본문에서 그대로 복사한 10~200자 연속 구절. `name·description` 한국어, `nameEn·descriptionEn` 번역.
   - `eligibility` 트리와 어휘·예시(2-2 참고). 어휘 밖은 `기타`/`other`에 원문 note.
   - 판단이 애매하면 `search_feedback`(운영자 피드백의 pg_trgm 유사도 상위 8건, 같은 프로그램 → 같은 호스트 → 나머지)으로 운영자 해석을 찾아 따른다. 요청에는 최근 피드백·이전 기수 참고·조심 목록·태그 사전·한도가 붙는다.
6. **원문 대조·정규화** (`submit_candidate`마다 바로, `ActivityVerifier.verify`; 판정을 도구 결과로 돌려주어 모델이 고쳐 다시 낼 수 있다): `roundKey`·이름 필수, 근거 구절 길이 10~200, 공백·마크다운 기호(`*_\`#>`)를 지운 뒤 원문에 포함돼야 함, 모집 끝 < 시작이면 거절. 신청 주소는 https이고 **원문 `<a href>`에 실제로 있을 때만** 남긴다. 읽을 수 없는 시각·날짜는 비운다. 구절 존재는 "모든 필드가 참"의 증명이 아니므로 **항상 초안(DRAFT)** 이다.
7. **저장** (`JooqActivityDraftRepository.saveDraft`, 트랜잭션·`FOR UPDATE`):
   - 식별: `source_id = UUIDv3("dearby/activity/<programId>|<roundKey 정규화>")`. 페이지 주소를 넣지 않아 같은 모집이 여러 페이지·여러 날에 보여도 초안 하나(2026-10-18).
   - 운영자가 지운 출처(`dismissed_activity_sources`, V11)는 다시 만들지 않음(`SKIPPED_DISMISSED`), 게시된 활동은 덮어쓰지 않음(`SKIPPED_PUBLISHED`).
   - 조심 목록: EXCLUDE면 저장 안 함, ALIAS면 정본의 `source_id`로 정본 이름으로 갱신(정본 출처가 없으면 거절).
   - 운영자 피드백: 같은 프로그램에서 이름이 지운 초안(`DRAFT_DISMISSED`)·제외 이름(`WATCH_EXCLUDE`)과 같으면 거절, 날짜 문구가 운영자가 날짜를 지정한 문구(`DATE_CURATED`)와 유사도 0.6 이상인데 해석이 다르면 저장하되 주의를 돌려준다.
   - **운영자 지정 보호**: `curated_fields`(V26, jsonb 배열)에 있는 필드(name·nameEn·description·descriptionEn·eligibility·officialUrl·applicationUrl·location·cost·category·audience·participationType)는 기존 값을 유지, 나머지는 수집값으로 갱신. 종류는 수집값 → 없으면 프로그램 기본 종류(`programs.category`, V33).
   - **이전 기수 자동완성**(V25 `inherited_fields`): `eligibility`·`audience`·`participationType`이 비면 이전 기수 값을 쓰고 필드 이름을 기록. 수집값이 생기면 표시가 빠진다. 기간·장소·비용·링크는 가져오지 않는다.
   - 일정: `curated = false`인 일정은 모두 지우고 다시 넣는다. ACTIVITY 슬롯(활동 기간, 종일 또는 문구만)·APPLICATION 슬롯(모집 기간, 시각 또는 종일)·슬롯 없는 세부 일정. `curated = true`인 슬롯은 보존. 시각 일정의 time_zone은 `Asia/Seoul`.
   - `valid_until = checkedAt + 24h`, `freshness = VERIFIED`, 대상자 조건은 `activity_eligibility_terms`로 펼쳐 쓴다.
8. **기록** (`collection_runs`, V8+V30): 결과(SUCCEEDED/FAILED/SKIPPED: 받은 페이지가 있으면 성공, 하나도 못 받고 거절 이유가 있으면 실패), 찾은 수·저장 수·거절 이유 배열·오류·입력(캐시 포함)/출력 토큰·웹 검색 횟수·**실제로 읽은 페이지 `fetched_urls`**(받은 순, 실패 제외)·`agent_log`(V42: 작업 절차 판 `workflowVersion`, 멈춘 이유, 턴·페이지, 사용량·비용 환산, 도구 호출, 쓴 피드백 id). 어드민 수집 설정 응답의 `lastFetchedUrls`는 마지막 성공 실행의 값, `previewUrls`·`previousRound`는 지금 펼칠 주소와 읽은 기수 번호다. Spring Batch 메타 테이블은 V9.

**회수 배치** (`RecallLauncher`/`RecallService`): 매일 04:00 Asia/Seoul, `--recall-now`로 즉시. 한 트랜잭션에 UPDATE 두 번(Spring Batch 잡 아님). `receipts`: `recalled_at is null and received_at < now-30d`이고 맞교환(2-5)이 아닌 행에 `recalled_at`을 기록. `guest_cards`: `saved_at < now-30d`인 행. 행은 보존하고 목록에서만 제외한다. 운영 참고: `run --rm batch --collect-now/--recall-now`로 띄운 일회성 컨테이너는 작업 뒤 스케줄러 때문에 종료되지 않아 수동으로 멈춰야 한다(2026-10-19 관찰, 수정 미정).

---

### 2-2. 대상자 조건(eligibility) 규격과 검색

**모델** (`domain/eligibility`): 트리 `EligibilityGroup(op: ALL|ANY, items)` 최상위는 그룹, 깊이 3까지(`MAX_DEPTH`). 조건 `EligibilityCondition(field, from, to, values, valuesEn, note, noteEn)`. 어휘 `EligibilityField`(한글 → 영문, 종류):

| 한글 | 영문(JSON·쿼리 키) | 종류 | 고정 값 어휘 |
| --- | --- | --- | --- |
| 나이 | age | 숫자 범위 | |
| 출생연도 | birthYear | 숫자 | |
| 학년 | schoolYear | 숫자 | |
| 학력 | educationLevel | 집합 | 고졸 highSchool · 대학 university · 대학원 graduateSchool |
| 재학상태 | educationStatus | 집합 | 재학 enrolled · 졸업 graduated · 휴학 onLeave · 졸업예정 graduating |
| 전공 | major | 집합 | 자유 |
| 지역 | region | 집합 | 자유 |
| 취업상태 | employment | 집합 | 학생 student · 미취업 unemployed · 재직 employed · 창업 founder |
| 경력연수 | experienceYears | 숫자 | |
| 국적 | nationality | 집합 | ISO 3166-1 alpha-2 |
| 팀규모 | teamSize | 숫자 | |
| 기타 | other | 메모 | note만, 검색 안 함 |

**파서** (`EligibilityJson`): 두 모드. `strict`(어드민 PATCH/POST)는 어휘 밖 필드·모양 오류·`from > to`·values/valuesEn 길이 불일치를 400으로 거부. `lenient`(수집)는 어휘 밖 필드·틀린 조건을 `기타`로 바꾸고 원문 값을 note(≤200자)에 남기며, 깊이를 넘는 그룹은 조건만 꺼내 부모에 편다. values(한글)·valuesEn(영문) 한쪽만 있으면 고정 어휘로 짝을 채우고(없으면 같은 글자), 최대 50개.

**저장** (`V23`, `EligibilityRows`): `activities.eligibility jsonb`(원문 트리) + 검색용 평탄화 테이블 `activity_eligibility_terms(field_en, from_value numeric, to_value numeric, value_en)`. 그룹 구조는 버리고 조건만 한 행씩(집합은 값마다 한 행), `기타`는 넣지 않는다. 인덱스 `(field_en, value_en)`, `(field_en, from_value, to_value)`.

**검색 2단계**:
- 1차(SQL, `EligibilityRows.conditions`): 요청 필드마다 `NOT EXISTS(그 필드 조건) OR EXISTS(맞는 조건)`. 숫자는 `from ≤ 값 ≤ to`(없는 쪽 열림), 집합은 `lower(value_en) = 값`. **조건이 없는 활동은 통과**(제한 없음으로 해석).
- 2차(서버 JSON, `EligibilityGroup.accepts`): 1차를 지난 활동의 트리를 `op`대로 평가. 요청하지 않은 필드의 조건은 참. `ActivityService.search`는 `size+1`을 읽어 2차에서 걸러진 만큼 페이지가 짧아질 수 있고 커서는 그대로 이어진다. 발견 프로그램 묶음은 필터가 있으면 `openActivities`를 2차까지 거른 뒤 메모리에서 묶는다(`ProgramSummaryService.filteredSummaries`).

쿼리 예: `GET /v1/activities?age=24&educationStatus=enrolled`, 필드 값 100자 제한, 숫자 아닌 값은 400.

---

### 2-3. 일정 모델과 열린 활동·정렬 규칙

**도메인** (`Schedule`, iCalendar VEVENT 대응): `title`(SUMMARY), `kind`(DEADLINE·ANNOUNCEMENT는 TRANSPARENT, INTERVIEW·EVENT는 시간을 차지), `status`(TENTATIVE·CONFIRMED·CANCELLED), `time: ScheduleTime?`(`Timed(start?, end?)` 중 하나 이상 + 시간대 / `AllDay(start, endExclusive)` / null = 날짜 미정 → `dateLabel`만), `address`·`conferenceUrl`·`sequence`(변경마다 +1, 구독 캘린더 변경 인식용)·`curated`·`curatedNote`.

**DB 제약** (`schedules`, V2+V24+V32): `slot in (APPLICATION, ACTIVITY)` nullable, 활동당 슬롯 유일 `(activity_id, slot)`. CHECK: `time_type null`이면 모든 시각·날짜 null; `TIMED`면 `start_at` 또는 `end_at` 하나 이상 + `time_zone` 필수 + `end ≥ start`; `ALL_DAY`면 `start_date`와 `end_date_exclusive > start_date` 필수. **ACTIVITY 슬롯은 TIMED 금지**(활동 기간은 종일만). 좌표·주소는 넷 다 있거나 넷 다 없어야 함. V24 `curated boolean`, V32 `curated_note varchar(300)`.

**운영자 지정 변환** (`ScheduleEdit.toTime`): 날짜 둘 → AllDay(끝 +1일 exclusive); `endDate`만 → `Timed(null, 그날 23:59 Seoul)`(마감만 지정); `startDate`만 → `Timed(00:00, null)`; 시각은 그대로 Timed. 날짜와 시각을 섞으면 400, 끝이 앞서면 400. 지정 시 `curated = true`, 해제(null)는 구조화 값·사유를 지우고 문구만 남긴다.

**열린 활동 판정 한 곳** (`OpenActivityConditions`, PR #271): 발견과 `GET /v1/activities?open=true`가 같은 조건을 쓴다. 게시 활동이고 (a) 모집 기간이 TIMED면 `start_at ≤ now`(없으면 열림) and `end_at > now`(없으면 열림), (b) ALL_DAY면 서울 오늘이 `[start_date, end_date_exclusive)` 안, (c) 구조화된 모집 기간이 없으면(행 없음 또는 문구만) 활동 시작(`act.start_at` 또는 `start_date`의 서울 0시) `> now`. 이전 구현은 TIMED만 보고 ALL_DAY 모집 기간을 "없음"으로 다뤄 앱이 펼친 활동이 비는 버그가 있었다.

**정렬 키**: `deadline = coalesce(app.end_at, app.end_date_exclusive 서울 0시, 활동 시작)`. 발견 프로그램 순서(`nearestRecruitmentEndAt` 최소값), 어드민 묶음 보기, 앱·웹 종류 묶음 안 정렬이 모두 "모집 마감 → 없으면 활동 시작 → 없으면 맨 뒤, 같으면 이름순".

**내 활동 진행 트랙**(앱, `ActivityStages.swift`/`ActivityStages.kt`): 단계 = APPLICATION 슬롯 `지원`(마감 시각까지 카운트다운) → 세부 일정(시작 순, 날짜 없는 것은 뒤) → ACTIVITY 슬롯 `활동`. 서울 날짜 기준으로 끝나지 않은 가장 이른 단계가 현재이고, 사용자 표시 `통과`는 지난 것으로, `탈락`은 활동을 끝난 묶음으로 보낸다(기기 메모리). 배지 `D-n · 종류`, 진행 중인 날 `D-DAY`, 모두 지나면 `지남`, 날짜 없음 `날짜 미확인 · 종류`.

---

### 2-4. 발견 API (`GET /v1/discovery/programs`)

**집계 SQL** (`JooqProgramSummaryRepository.search`): `programs ⋈ organizations ⋈ activities ⟕ schedules app(APPLICATION) ⟕ schedules act(ACTIVITY)`를 프로그램으로 `GROUP BY`하고 활동 행 필터(게시 + `participationType` + `category` IN + 대상자 1차)를 건다.
- `openCount = count(when isOpen then id)`, `nearestEnd = min(when isOpen then deadline)`, `latestStart = max(activityStart)`, `matchedIds = array_agg(distinct when 활동 이름 매치 and isOpen then id)`.
- `q`는 활동 이름·영문 이름 부분 일치 또는 프로그램·조직 이름(한·영) 부분 일치(`HAVING bool_or`). 길이 ≤100.
- `openOnly=true`(기본): 열린 활동이 있는 프로그램만. `false`: 최근 2년(`RECENT_YEARS = 2`) 안에 활동 시작·정렬 키·생성 시각 중 하나가 있는 프로그램 전부(`openActivityCount`가 0일 수 있음).
- 순서: `openCount > 0 DESC, nearestEnd ASC NULLS LAST, latestStart DESC NULLS LAST, name, id`. **정수 오프셋 커서**(`cursor` = 이전 `nextCursor`, `limit size offset cursor`), size 1–100(기본 20). 응답 `ProgramSummary {id, name, nameEn, organization, openActivityCount, nearestRecruitmentEndAt, matchedActivityIds, category, latestActivityAt}`.
- 대상자 필터가 있으면 SQL 집계 대신 `openActivities`(2차 판정 포함) 결과를 메모리에서 묶고 같은 규칙으로 정렬·페이지한다(이 경로는 `openOnly=false`를 지원하지 않음).
- 언어: `Accept-Language`(ko·en)로 이름 하나를 고르고 `Content-Language`·`Vary: Accept-Language`를 붙인다.
- 펼치기는 `GET /v1/activities?programId=&open=true`(size 최대 100). 앱·웹은 스스로 걸러내지 않는다(판정 일원화). 모집 중이 없으면 `GET /v1/activities?programId=`를 받아 최근 2년·활동 시작 최근 순으로 `지난 활동`을 보인다.
- `GET /v1/activities`: id 오름차순 keyset 커서, `q`는 이름·설명(한·영) 부분 일치 또는 `pg_trgm word_similarity ≥ 0.4`(V7, 오타·띄어쓰기 차이 허용), `organizationId`·`programId`·`category`(쉼표 목록)·`open`·대상자 필터.

---

### 2-5. 명함·교환·게스트 저장

**프로필과 명함 분리** (V16): `profiles`(name, job, introduction, version), `contacts`(클라이언트 UUID, kind PHONE·EMAIL·KAKAO·INSTAGRAM·GITHUB·BEHANCE), `profile_histories`(조직/활동 종류, 카탈로그 id 또는 직접 입력 이름, `approval_status`·`verification_status`), `history_evidence`(bytea, 이력당 3개·5MB, jpeg/png/webp/pdf), `profile_versions`(저장마다 전체 JSON 스냅샷, 되돌리기는 새 버전). `PUT /v1/profile`은 `If-Match: <version>`으로 409 낙관적 잠금. 직무는 `jobs` 계층(V18)과 승인.

**발행 스냅샷** (`CardService.publish`): 프로필의 연락처·이력 중 고른 id만 복사하고, 이력은 `APPROVED`만 허용하며 evidence를 비운다. `business_cards`에 `template`(앱 내장 기본 템플릿, 현재 `{{profile.introduction}}` 하나)과 `introduction`(채운 본문), `profile_name`, `job`, `contacts jsonb`, `histories jsonb`, `revoked`를 저장한다. 발행 뒤 프로필을 고쳐도 바뀌지 않는다("고치기" = 새 명함 발행). V27·V28은 옛 템플릿(`# 이름 / 직무 / 소개 / - 라벨: 연락처`)으로 발행된 본문에서 중복 줄을 떼어냈다.

**템플릿 채우기** (`TemplateRenderer`): 정규식 `\{\{\s*([a-zA-Z]+)(?:[.:]([^{}\s]+))?\s*}}`로 `{{profile.name}}`·`{{profile.job}}`·`{{profile.introduction}}`·`{{contact:<uuid>}}`만 치환, 고르지 않은 연락처·모르는 토큰은 빈 문자열(원문 토큰을 남기지 않음). 마크다운 부분집합(제목·굵게·기울임·목록·링크, 원시 HTML 금지, https·mailto·tel만), 텍스트 10,000자·제어 문자 금지.

**공개 조회**: `GET /v1/cards/:id`는 인증 없음, 철회면 404, `template`은 싣지 않음. 웹 `/cards/:id`가 이 응답으로 카드를 그린다(이니셜 아바타·이름·직무 칩·소속·소개·연락처·활동 이력, Mobbin 참고 레이아웃).

**QR과 스킴**: iOS `CIQRCodeGenerator`, Android `com.google.zxing:core`(인코딩만, 카메라 없음)로 `https://<DEARBY_WEB_ORIGIN>/cards/<cardId>?activity=<활동 숫자 id>`를 그린다. 활동 칩은 진행 트랙의 `진행 중` 중 현재 단계가 가장 가까운 것이 기본. 웹 `/cards/:id?activity=`는 `Dearby 앱에 저장` 링크 `dearby://cards/<cardId>?activity=<n>`(커스텀 스킴, 유니버설 링크·App Links 없음)과 `브라우저에 저장`을 보인다. iOS `CFBundleURLTypes`에 `dearby`, Android `AndroidManifest`에 `dearby://auth/apple`·`dearby://cards/*` 인텐트 필터.

**지갑(receipts, V16+V29)**: `(recipient_id, card_id)` 유일, `context jsonb {"activityId": n}`(옛 `label`은 읽기만), `received_at`, `recalled_at`. `WalletService.save`: 없는·철회 명함 404, 자기 명함 409 `OWN_CARD`, 모르는 활동 id는 null, 이미 있으면 받은 날 유지·활동만 채움(`ON CONFLICT DO UPDATE`가 회수 행도 되살린다). 응답 `Receipt {card, context{activityId, activityName, programName}, receivedAt, reciprocal, expiresAt}` — `expiresAt = receivedAt + 30일`은 서버 계산, 맞교환이면 null.

**맞교환 규칙** (`JooqWalletRepository`): 받은 명함 R(받는 사람 U, 명함 주인 O)은 **O가 U의 미철회 명함 중 하나를 회수되지 않은 상태로 갖고 있으면** 맞교환(계정 쌍 기준, 활동이 달라도 성립). `reciprocate(cardId, myCardId)`: 호출자가 `cardId`를 가진 적이 없으면 403, `myCardId`가 자기 명함이 아니면 404, 주인의 지갑에 같은 활동으로 Receipt 생성. 회수 SQL은 `NOT EXISTS(mine ⋈ back ⋈ theirs where theirs.user_id = r.recipient_id and theirs.revoked = false and back.recalled_at is null)`.

**게스트(비로그인) 저장** (`GuestService`, V17+V29): 토큰은 43자 base64url(32바이트 난수), DB에는 SHA-256 digest만(`guest_sessions`), 자동 만료 없음. 모든 게스트 요청은 Next 서버가 보낸 `X-Guest-Proxy-Key`를 서버의 `GUEST_PROXY_SECRET`(≥32자)과 상수 시간 비교로 확인(브라우저 직접 호출 차단). 제한: 1분 창 전체 1,200 요청·세션 생성 60·세션당 120, 세션 10,000개, 세션당 명함 100개(프로세스 메모리 카운터, forwarded IP 불신). 첫 저장은 201과 토큰, 이후 200(`saved`/`alreadySaved`), 활동은 첫 저장 때만 기록. 웹은 토큰을 `__Host-dearby_guest`(HttpOnly·SameSite=Lax·Secure, 400일) 쿠키로 들고 다니고, 탭 간 첫 저장은 `navigator.locks`로 직렬화한다. **홈 화면 세션 잇기**: iOS 홈 화면 웹앱은 Safari와 쿠키가 분리되므로 1회용 코드(10분, 43–128자)를 발급하고, 코드에서 HKDF-SHA256(salt `dearby/guest-handoff`, info `v1 token key`)로 만든 AES-256-GCM 키(AAD = 세션 digest)로 토큰을 암호화해 둔다(`guest_handoffs`, 세션당 하나). DB만으로는 토큰을 복원할 수 없고 교환 즉시 사용 처리, 없음·만료·사용됨은 모두 404.

---

### 2-6. 인증

**패스키(WebAuthn)** (`WebAuthnPasskeyService`, Spring Security webauthn4j): 계정은 패스키로만 생성. RP ID `WEBAUTHN_RP_ID`(운영 `wid.io.kr`), 허용 origin `WEBAUTHN_ALLOWED_ORIGINS`(iOS `https://wid.io.kr`, Android `android:apk-key-hash:<서명 SHA-256 base64url>`). 세션 쿠키를 쓰지 않고 `challengeId`로 옵션→완료를 잇는다(`webauthn_challenges`, V4, **5분 TTL**, 1회 소비). 등록 옵션에서 서버가 사용자 UUID를 정해 16바이트 user handle로 쓰고, `displayName` 기본 `Dearby`(≤64자). 알고리즘 EdDSA·ES256·RS256, attestation NONE. 완료 시 계정 생성·user entity 저장·검증을 한 트랜잭션으로 묶어 실패하면 롤백. 인증 옵션은 `allowCredentials` 비움(기기 패스키 중 선택). iOS entitlement `webcredentials:wid.io.kr`(AASA는 nginx 쪽 `wid.io.kr` 정적 파일), Android Credential Manager.

**JWT** (`JwtIssuer`, `JwtProperties`): RS256, 키는 `JWT_KEY_PATH`의 PKCS#8 PEM(없으면 기동 시 생성, api·admin 컨테이너가 같은 파일을 공유), `iss = JWT_ISSUER`, `sub = 사용자 UUID`, `roles` 클레임(검증 쪽은 `ROLE_` 접두사 권한). **access 1분, refresh 90일**(`application.yaml`). refresh 토큰은 UUID 원문을 주고 DB에는 SHA-256만 저장(`refresh_tokens`), 소비는 조건부 UPDATE(`revoked_at is null and expires_at > now`)라 동시 요청에도 한 번만 성공(회전). `/v1/auth/**`에는 만료 토큰이 붙어도 무시해 refresh가 401로 막히지 않게 했다. 응답 `TokenPair {accessToken, refreshToken, user{id, roles}}`.

**Apple 로그인** (`AppleSignInService`, `NimbusAppleIdProvider`): 네이티브(iOS `ASAuthorizationAppleIDRequest`)와 웹 방식(Android Custom Tab, Services ID). 서버가 발급한 nonce(`apple_sign_in_states`, **10분 TTL**, 1회)가 identity token의 `nonce` 클레임과 같아야 하고, JWKS `https://appleid.apple.com/auth/keys` 서명, `iss = https://appleid.apple.com`, `aud ∈ APPLE_CLIENT_IDS`(iOS 번들 ID, Services ID), 만료를 검증. 웹 방식은 앱이 만든 verifier의 SHA-256을 `web/start`에 맡기고 콜백은 1회용 `code`를 `dearby://auth/apple?code=`로 돌려주며, `web/exchange`에서 verifier를 상수 시간 비교 — 토큰은 주소에 싣지 않는다. 처음 보는 `sub`는 202 `{linkToken(10분), email}` → `register`(새 계정) 또는 `/me/identities/apple`(패스키 계정에 연결). 이메일로 계정을 합치지 않는다(private relay 고려). `authorizationCode`가 오면 `.p8`로 서명한 client secret으로 Apple refresh token을 받아 두고 계정 삭제 때 `appleid.apple.com/auth/revoke`로 폐기한다(실패해도 삭제 진행).

**Google 로그인(네이티브, 2026-10-19 전환)** (`GoogleSignInService`, `NimbusGoogleIdProvider`, `GoogleTokenDecoder`): 앱 SDK(iOS GoogleSignIn-iOS 10.x, Android Credential Manager + `googleid`)가 받은 ID 토큰을 서버가 검증한다. `POST /v1/auth/google/nonce`가 32바이트 난수 nonce를 주고 상태 id는 `UUIDv3("dearby/google/nonce/<nonce>")`로 정해 같은 상태 표(provider = GOOGLE)에 둔다. `POST /v1/auth/google {idToken, nonce}`: JWKS `https://www.googleapis.com/oauth2/v3/certs`, `iss`는 `https://accounts.google.com`과 `accounts.google.com` 둘 다(문자열 비교, 옛 URL 변환 버그 수정), `aud ∈ GOOGLE_CLIENT_IDS`(쉼표 목록: Android는 웹 클라이언트 id, iOS SDK는 iOS 클라이언트 id), 만료, nonce 상수 시간 비교·1회 소비. `email_verified`가 아니면 이메일을 저장하지 않는다. refresh token은 받지 않으므로 삭제 때 폐기할 것도 없다. 웹 방식(콜백·client secret)은 삭제됐고 설정 키는 `GOOGLE_CLIENT_IDS` 하나다. 값이 없으면 503이고 앱은 버튼을 숨긴다.

**계정 삭제** (`DELETE /v1/me`, `AccountDeletionService`): Apple 토큰 폐기 시도 후 사용자 행 삭제(연락처·프로필·명함·이력·참여·refresh token·패스키 credential은 FK cascade 또는 서비스 삭제). access token은 1분 수명으로 자연 만료. 기기의 패스키 삭제는 안내만.

**접근 규칙**: `ApiAccessRules` — `GET /v1/activities*`, `GET /v1/discovery/programs`, `GET /v1/cards/*`, `/v1/guest/**`는 인증 없음, 나머지는 인증. `AdminAccessRules` — `/v1/admin/**`는 `ADMIN` 역할, 그 외 모두 거부. 첫 관리자는 DB `user_roles`에 직접 넣는다. 오류 본문은 RFC 9457 ProblemDetail(`detail`은 사용자용 문장), 409에는 `code`(예 `OWN_CARD`).

---

### 2-7. 어드민(운영) 기능과 보호 규칙

- **조직 계층**: `organizations`는 자기 참조 트리(`parentOrganizationId`), 응답에 `ancestors`·`childCount`·`programCount`. 같은 계위에서 **띄어쓰기만 다른 이름은 중복**(V12 함수 유일 인덱스 `regexp_replace(name, '\s', '', 'g')`, 영문 이름도 V13), 자기·하위를 상위로 지정하면 순환 거부, 하위가 있으면 삭제 거부. 프로그램·활동·직무·역할도 같은 중복 규칙.
- **활동 편집** (`PATCH /v1/admin/activities/:id`): 이름·설명(한·영)·대상자 조건(strict 파서)·종류·공식 안내 URL(https 필수)·신청 링크(빈 문자열 삭제)·모집/활동 기간. 보낸 필드만 바꾸고 `curated_fields`에 남긴다. 기간 지정에 `note`(≤300) 사유. 어드민 화면은 모집 시작이 활동 종료보다 늦거나 두 기간이 같거나 활동 원문에 모집·접수 낱말이 있으면 `기간 확인` 태그를 붙이고 `모집 ↔ 활동 바꾸기` 버튼을 둔다.
- **활동 직접 등록** (`POST /v1/admin/activities`): 초안, `source_id` 없음(수집이 건드리지 않음), `official_source = true`, `source_note "운영자 등록"`, 보낸 필드 모두 운영자 지정, 기간은 `curated`. 활동 기간은 날짜 범위 또는 원문 문구 중 하나 필수.
- **조심 목록** (`collection_watchlist`): `markDuplicate`는 활동 이름을 ALIAS(정본 지정, 같은 프로그램만) 또는 EXCLUDE로 올리고 활동을 지운다(출처는 dismissed로 남아 재생성되지 않음). `(program_id, normalized_name)` 유일.
- **프로그램 기본 종류·일괄 적용**: `PATCH /v1/admin/programs/:id {category, applyCategoryToActivities}` → `category is null or not jsonb_exists(curated_fields, 'category')`인 활동만 갱신, `appliedActivityCount` 반환.
- **수집 설정**: 토큰 검증, `previewUrls`·`previousRound`·`lastFetchedUrls`·`lastSucceededOn`.
- **이력·직무 검토**: 승인 시 id를 주면 직접 입력을 카탈로그 항목으로 치환, 거절은 사유 필수, 인증자료 확인(`verified`).
- **어드민 웹**: 활동 화면은 조직(프로그램 바로 위) → 프로그램(펼침, 종류 소제목, 모집 마감 순) → 활동; 검색어·프로그램 필터 시 평면 목록. 중복 제출은 `useSingleFlight` ref 잠금. 로그인은 패스키, 동시 401은 refresh 하나로 공유.

---

### 2-8. 클라이언트 아키텍처

- **FSD 계층**(app → pages → widgets → features → entities → shared): 상향 참조·같은 계층 다른 슬라이스 직접 참조 금지, 슬라이스 공개 API만 참조, pages/widgets의 `@Composable`/View는 State와 콜백만 받고 entities·I/O를 직접 만지지 않는다.
  - Android `scripts/check-fsd.py`: 패키지 = 디렉터리 검사, 참조 방향·형제 슬라이스·공개 API(`API` 사전)·순수 UI(LocalContext·SharedPreferences·Intent·ViewModel·Repository 금지) 검사, 프로토타입 경계(Room·HttpClient·WebView·CalendarContract·SQLite·SharedPreferences·SavedStateHandle 등은 허용 파일 외 금지: 네트워크는 `ActivityClient`·`AccountClient`, 세션은 `SessionVault`, 첫 실행 플래그는 `MainActivity`, 로그인 중간 상태는 `AccountViewModel`의 SavedStateHandle). `--self-test` 25케이스. CI에서 매 PR 실행.
  - iOS: `architecture/public-api.json`(슬라이스별 공개 타입 목록)·`pure-ui.json`(입력+콜백만 그리는 뷰) 수동 유지, SwiftLint strict. 하위 2계층 제한 규칙.
- **생성형 Xcode 프로젝트** (`scripts/generate_project.rb`): `project.pbxproj`를 추적하지 않고 CI·로컬에서 xcodeproj gem으로 생성해 머지 충돌을 없앤다. 번들 ID Debug `io.wid.dearby`/Release `io.wid.dearby`, Swift 6, iOS 18, `SWIFT_STRICT_CONCURRENCY=complete`, 빌드 설정으로 `DEARBY_API_ORIGIN`·`DEARBY_WEB_ORIGIN`·`GOOGLE_IOS_CLIENT_ID`·`GOOGLE_IOS_URL_SCHEME` 주입, `validate_origins.sh`가 Release에서 https 도메인만 허용. UUID는 `predictabilize_uuids`로 안정화.
- **연결 설정**: iOS `AppOrigins`(Info.plist `DearbyAPIOrigin`·`DearbyWebOrigin`, Release는 `https://<소문자 도메인>`만, Debug는 localhost http 허용), Android `BuildConfig.API_ORIGIN/WEB_ORIGIN/GOOGLE_SERVER_CLIENT_ID`(Gradle 속성·환경변수, `validateReleaseOrigins`가 preReleaseBuild에서 검사). 값은 저장소에 두지 않고 로컬은 `~/.dearby-deploy/mobile.env`.
- **공통 UI 토큰**: 청록 `#007F80`, 민트 `#F0FCFA`, 보조 `#657078`, 구분선 `#E3E8EA`, 바탕 `#F5F7F8`, 본문 `#172027`, 위험 `#A12A2A`(iOS `DearbyStyle`, Android `shared/ui/Components.kt`), 글꼴 Pretendard(`shared/assets/fonts`). 하단 탭은 플랫폼 기본(`TabView`, Material 3 `NavigationBar`).
- **토큰 저장**: iOS `TokenVault`(공개 API 목록에 있음), Android `SessionVault`(SharedPreferences 허용 파일). 401 → refresh 1회 → 401이면 로그아웃, 네트워크 오류·5xx는 토큰 유지.
- **검증 원칙**(2026-10-18 사용자 지시): 시뮬레이터·에뮬레이터를 띄우지 않고 실기기 원격 디버깅(USB)으로만 화면을 확인한다. 빌드 검사(`xcodebuild generic/platform=iOS Simulator`, `assembleDebug lintDebug`)는 계속 쓴다. 기기가 없으면 미실행으로 기록한다.

---

### 2-9. 인프라·운영·테스트 전략

- **컨테이너**: `compose.prod.yaml` — `postgres`(pgvector/pgvector:pg17, 데이터 `./data/prod/postgres`, 127.0.0.1만), `api`·`admin`(각 Dockerfile 멀티스테이지: `eclipse-temurin:25-jdk` 빌드 → `25-jre`, uid 10001, JWT 키 볼륨 공유, admin은 읽기 전용), `batch`(JRE + Node 24 + `@anthropic-ai/claude-code` 전역 설치, cli 엔진용). 모든 포트는 127.0.0.1에 바인딩하고 nginx가 프록시한다.
- **설정 키**: `DB_*`, `JWT_KEY_PATH`·`JWT_ISSUER`, `WEBAUTHN_RP_ID`·`WEBAUTHN_ALLOWED_ORIGINS`, `APPLE_CLIENT_IDS`·`APPLE_SERVICES_ID`·`APPLE_CALLBACK_URL`·`APPLE_TEAM_ID`·`APPLE_KEY_ID`·`APPLE_PRIVATE_KEY_PATH`, `GOOGLE_CLIENT_IDS`, `GUEST_PROXY_SECRET`, `CLAUDE_CODE_OAUTH_TOKEN`·`COLLECTION_WORKFLOW_PATH`(2026-10-20; 수집 엔진 선택과 API 키는 없앴다). 선택 기능은 값이 없으면 그 경로만 503이고 기동은 된다.
- **마이그레이션**: Flyway V1–V33(핵심: V2 도메인, V8 수집, V16 프로필·명함 v2, V17 게스트, V19 공유 삭제, V21 웹 버전 삭제, V22 Google, V23 대상자 조건, V24 지정 일정·조심 목록, V25 상속 필드, V26 운영자 지정 필드, V27·V28 본문 정리, V29 교환·회수, V30 받은 페이지, V31 활동 종류, V32 사유, V33 프로그램 종류). jOOQ 생성 코드는 `core/src/generated`에 커밋하고 CI가 `:core:jooqCodegen` 후 diff가 없는지 검사한다.
- **nginx**: `infra/nginx`(compose·certbot 스크립트·정적 html). 실제 운영 `conf.d`는 bind mount로 저장소 밖(gitignore)이며 `api.dearby.wid.io.kr`을 api/admin 컨테이너로, `wid.io.kr/.well-known`(AASA·assetlinks)을 정적으로 낸다. 저장소 README의 3000/3001 라우팅 설명은 TS API 시절의 것으로 현재 포트와 다르다(코드로 확인 불가).
- **배포 절차**(2026-10-18~19 실제 수행): 운영 서버 사본 `~/.dearby-deploy/api-kotlin-2a061187/apps/dearby-api`로 `rsync`(data·.env.prod·build·.gradle 제외) → `docker compose -f compose.prod.yaml --env-file .env.prod up -d --build api admin batch` → Flyway가 첫 기동에 적용 → 엔드포인트 probe. 웹은 Vercel GitHub 연동으로 main 푸시마다, 어드민은 Git 미연결 프로젝트라 `git archive origin/main apps/admin` 후 `vercel deploy --prod`(Root Directory `apps/admin`, `vercel.json` rewrite로 `/api/auth/*`·`/api/admin/*` → API).
- **CI** (`.github/workflows/native.yml`): 변경 경로로 잡을 고른다. `android`(ubuntu, JDK 21, `check-fsd.py` + `lintDebug assembleDebug`), `API test`(ubuntu, JDK 25, pgvector 서비스 컨테이너, jOOQ diff 검사 + `./gradlew test`), `iOS build`(macos-26, SwiftLint + 프로젝트 생성 + 시뮬레이터 generic 빌드). `ios-testflight.yml`은 매일 09:00 KST 변경이 있을 때만 수동 서명(p12·프로비저닝 프로파일 시크릿)으로 아카이브·업로드.
- **테스트 전략**(2026-10-06 사용자 결정): 저장소의 자동 테스트를 전부 지운 뒤 사용자와 합의한 것만 다시 만든다. 지금은 API 통합 테스트 클래스(예 `ActivityFlowTest`, `AdminActivityInfoFlowTest`, `CardFlowTest`, `GuestFlowTest`, `AppleSignInFlowTest`, `CollectionJobTest`)만 있고 **새 테스트 파일을 만들지 않고 기존 클래스를 확장**한다. 큰 글씨 접근성 테스트는 보류. 실제 실행한 검사만 통과로 기록한다.
- **문서 체계**: 계약(`shared/contracts/*.md`)이 플랫폼 간 단일 기준이고, `docs/context/coordinator-current-task-and-decisions.md`에 날짜별 결정, `docs/context/activity-collection-batch.md`에 수집 세부, `AGENTS.md`에 협업 규칙(실기기 검증, 병렬 작업, 표기 규칙)을 둔다.

---

## 부록. 확인 범위와 한계

- 위 내용은 2026-10-19 저장소 상태를 읽어 정리했다. 발견 판정 일원화·`openOnly`·`지난 활동`(PR #271)은 작성 시점에 병합 대기였다.
- 코드로 확인하지 못한 것: 운영 nginx 설정 본문(저장소 밖), 운영 서버의 `.env.prod` 값, 앱의 기기 캘린더 접근(코드 없음), 태그 기능(코드 없음).
- 과거 문서(`docs/context/admin-web-deployment.md`의 Supabase 절, `infra/nginx/README.md`의 3000/3001 라우팅, `apps/ios/ARCHITECTURE.md`의 2026-10-04 프로토타입 설명 일부)는 당시 기록이며 현재 구현과 다른 부분이 있다.
