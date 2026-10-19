# 활동 수집 배치 (Kotlin · Spring Batch)

2026-10-15 사용자 결정으로 활동 수집을 TypeScript 구독 워커에서 Kotlin 멀티모듈의 `batch` 모듈로 옮긴다. 활동 정보 원본은 Kotlin DB다. 2026-10-18 사용자 결정으로 TS 워커 소스(`apps/catalog-worker`)와 `supabase/`를 저장소에서 지웠다([삭제 전](https://github.com/fixabley/dearby/tree/6636e9a6/apps/catalog-worker)).

| 결정 | 내용 |
| --- | --- |
| 실행 | `apps/dearby-api/batch`, 별도 이미지(`batch/Dockerfile`)·compose 서비스 `batch`. 매일 한국 시간 00:00(`@Scheduled`, Asia/Seoul) |
| 모델 | Claude Haiku 4.5(`claude-haiku-4-5`). 엔진은 `COLLECTION_ENGINE`으로 고른다(2026-10-15 사용자 결정, 둘 다 지원) |
| 엔진 `api` | Anthropic Java SDK. `ANTHROPIC_API_KEY`, 사용량 과금. 웹 검색을 서버 도구 `allowed_domains`로 정확히 제한하고 검색 결과 블록에서 주소를 읽는다 |
| 엔진 `cli` | Claude Code CLI(`claude -p`), 구독 사용량. 로컬 Mac은 로그인만으로, 컨테이너는 `claude setup-token`으로 만든 `CLAUDE_CODE_OAUTH_TOKEN`. 기본 시스템 프롬프트·사용자 설정·MCP·세션 저장을 끄고 빈 임시 폴더에서 돌린다(시스템 프롬프트를 바꾸기 전 3.9만 → 1.1천 입력 토큰). 내장 WebSearch만 허용하며 검색 범위는 지시문으로만 좁혀지므로 받은 주소를 허용 호스트로 다시 거른다. 구독 계정으로 서버 무인 작업을 돌려도 되는지는 이용 조건 확인 필요 |
| 준비 안 됨 | 엔진을 쓸 수 없으면(API 키 없음, CLI 없음, 컨테이너에 토큰 없음) 수집하지 않고 경고만 남긴다 |
| 검토 | 수집 결과는 초안(`activities.publication_status = DRAFT`). 관리자가 `PUBLISHED`로 바꿔야 공개 API에 보인다. 게시된 활동은 다시 수집해도 덮어쓰지 않는다 |
| 수집 대상 | DB의 **모든 프로그램**(2026-10-15 사용자 결정: 섞어서). 프로그램별 설정 `program_collection_settings`(선택, 없으면 기본값): 공식 URL·허용 호스트·주기(`interval_days`, 기본 1)·켜기/끄기 |
| 공식 도메인 | 허용 호스트가 있으면 그 안에서만 검색·원문 확인. 없으면 "프로그램 이름 + 주최 기관"으로 웹 전체를 검색하고, 그 초안은 `activities.official_source = false` |
| 주기 | 마지막 **성공** 수집 날짜부터 `interval_days` 동안은 수집 대상에서 뺀다(1 = 매일). 실패한 날은 다음 날 다시 수집한다 |
| 기록 | `collection_runs`(프로그램 하나 × 한국 날짜 하루당 한 행: 공식 도메인만인지·결과·찾은 수·저장 수·제외 사유·오류·토큰·검색 횟수). Spring Batch 실행 기록은 `batch_*` 테이블 |

## 수집 흐름

1. **후보 찾기**: Haiku + 웹 검색(최대 3회). 허용 호스트가 있으면 그 도메인으로 제한하고 그 안의 https 주소만, 없으면 웹 전체의 https 주소를 쓴다. 공식 URL이 있으면 항상 포함하고 최대 5페이지.
2. **원문 받기**: 서버가 직접 받는다. https, (허용 호스트가 있으면) 정확히 일치, 같은 경계 안의 리다이렉트 3번까지, 사설·내부 주소 거부, 15초, 3 MB, HTML만. JavaScript로만 그려지는 페이지는 본문이 비어 걸러진다.
3. **추출**: 받은 본문을 Haiku에 넘겨 JSON(structured outputs)으로 활동을 뽑는다. 웹 검색 결과의 citations와 structured outputs는 함께 쓸 수 없어(400) 검색과 추출을 나눴다. 본문은 15만 자까지 넘긴다.
4. **원문 대조**: 근거 구절(10~200자)이 원문에 있어야 한다(공백·마크다운 기호 차이 무시). 신청 주소는 원문 페이지의 `<a href>`에 있을 때만 남긴다. 읽을 수 없는 시각은 비우고 날짜 문구만 남긴다. **구절이 있다는 것은 모든 필드가 참이라는 증명이 아니다** — 그래서 초안으로만 저장한다.
5. **저장**: 같은 활동은 프로그램 + 회차 식별자(`roundKey`)로 만든 `source_id`로 찾는다(2026-10-18부터 페이지 주소는 넣지 않는다. 같은 모집이 여러 페이지·여러 날에 보여도 초안 하나다). 시각 일정은 Asia/Seoul로 저장한다.

## 추출·저장 규칙 — 2026-10-18·19 사용자 지시 (현재 상태)

계약은 admin-v1.md("프로그램"·"일정 직접 지정…"·"운영자 지정 필드"·"이전 기수 정보 자동완성")와 native-v1.md("활동 종류"·"대상자 조건")에 있다. 여기에는 수집기 쪽 동작만 적는다.

- **활동 단위**: 활동은 **프로그램의 기수(회차) 하나**다(예 "부스트캠프 10기", "Let'Swift 2026"; 트랙은 따로 지원하는 기수일 때만 별도 활동). 그 기수 안의 선발 단계·테스트·챌린지·교육 기간·결과 발표·면접·마감은 `schedules`에 `kind`(DEADLINE·ANNOUNCEMENT·INTERVIEW·EVENT)로 들어간다. `source_id` = 프로그램 + `roundKey`(페이지 주소 제외). 이전 규칙으로 쪼개져 들어온 초안은 자동으로 합쳐지지 않아 어드민에서 지웠다(지운 초안은 다시 만들지 않음).
- **두 기간의 정의**: 모집 기간(`recruitmentStartAt/EndAt` 또는 `recruitmentStartDate/EndDate`)은 모집·접수·지원·신청·원서·마감 낱말이 붙은 날짜, 활동 기간(`dateLabel`·`startDate`·`endDate`)은 교육·과정·행사·운영·개최가 실제로 진행되는 기간. 같은 날짜를 두 기간에 넣지 않고, 모집 기간만 있으면 `dateLabel`은 빈 문자열. 날짜만 적힌 기간은 종일 일정(`startDate`, `endDateExclusive`)으로 저장하고 `dateLabel`은 원문 보존용. 날짜가 아닌 문구("2026 신입생 모집")는 기간 필드에 넣지 않고, 다른 프로그램의 활동은 뽑지 않는다.
- **종류**: `category`(BOOTCAMP·CONFERENCE·WORKSHOP·STUDY·CLUB·LECTURE·HACKATHON·CONTEST·MEETUP·OTHER, 모르면 null)를 뽑는다. null이면 프로그램 기본 종류(`programs.category`)를 쓴다.
- **대상자 조건**: `eligibility` 트리(어휘·예시를 지시문에 넣음)와 `audience` 원문. 어휘 밖 필드는 서버가 `기타`로 바꾼다.
- **운영자 피드백을 지시문에 넘기는 것**: (1) 조심 목록 — "활동으로 만들지 않을 이름"(exclude)과 "다른 표기 → 정본 이름"(alias); 저장 전 이름 대조가 보장이고 지시문은 힌트. (2) 이전 기수 — 대상자 조건·대상 원문을 "참고(이전 기수)"로(본문에 없으면 null). (3) 날짜 문구 해석 예시 — 운영자가 지정한 일정(`curated`, 원문 `date_label` + 날짜, 사유 `curated_note`)을 최근 20개(같은 프로그램 우선, 문구 중복 제거) `[모집] "…" → startDate …, endDate … (이유: …)` 형식으로. (4) 시작 주소 — 토큰을 펼친 주소 목록을 탐색 힌트로.
- **저장 때 보호·보완**: 운영자 지정 필드(`curated_fields`: name·nameEn·description·descriptionEn·eligibility·officialUrl·applicationUrl·location·cost·category)와 `schedules.curated` 일정은 덮어쓰지 않는다. `eligibility`·`audience`·`participationType`이 비면 같은 프로그램의 가장 최근 다른 활동(게시 우선)에서 가져와 `inherited_fields`에 적는다(새 초안은 처음 저장 때, 기존 초안은 빈 필드만). 운영자 직접 등록 활동(`source_id` 없음)은 수집이 건드리지 않는다.
- **시작 주소 토큰**: `officialUrl`의 `{year}`·`{yy}`·`{round}`를 `CollectionUrlTemplate`(core)이 올해·내년, 다음 기수·현재 기수로 펼친다(`previousRound`는 같은 프로그램의 가장 최근 활동 이름의 "N기", 없으면 맨 앞·뒤 숫자). `CollectActivitiesService`가 이전 기수를 읽은 뒤 펼친 주소를 탐색 결과 앞에 둔다. 기수를 모르면 그 주소는 건너뛰고 거절 이유 `기수 번호를 알 수 없음`을 남긴다(탐색 페이지도 모두 실패하면 결과 FAILED). 어드민 `previousRound`·`previewUrls`는 같은 규칙으로 서버가 계산한다.
- **받은 페이지 기록**: 실제로 받아 읽은 페이지를 `collection_runs.fetched_urls`(V30)에 받은 순서로 남긴다(실패 제외). 어드민 `lastFetchedUrls`는 마지막 성공 수집의 값이다.
- 변경 이력: 2026-10-18 활동 단위·날짜 전용 필드·조심 목록·이전 기수 자동완성([`a148af5d` 이전 문서](https://github.com/fixabley/dearby/blob/a148af5d/docs/context/activity-collection-batch.md)) · 2026-10-19 모집·활동 기간 혼동 수정(사용자 보고; 2026-10-18 수집분에서 "우아한 테크코스 2026"은 활동 기간에 모집 기간 2025.09.29~10.10, "Apple Developer Academy Cohort"는 모집 기간에 과정 기간 5/1~12/8, "Apple Developer Academy 2027"은 모집 마감 = 과정 종료일; 원인은 두 기간을 정의하지 않은 지시문과 필수였던 `dateLabel`; 어드민 `기간 확인` 태그·`모집 ↔ 활동 바꾸기` 추가) · 2026-10-19 주소 토큰·받은 페이지·종류·프로그램 기본 종류·날짜 문구 예시+사유.
- **태그(2026-10-19, native-v1.md "태그")**: 추출 요청에 태그 사전의 한글 이름 전체를 넘기고, 모델은 `tags`(최대 5개)를 그 이름 중에서만 고른다. 저장할 때 사전에 없는 이름은 버리고(띄어쓰기·대소문자 무시, 영문 이름도 맞춤), 하나도 남지 않으면 프로그램 태그(`program_tags`)를 기본으로 쓴다. 운영자가 태그를 지정한 활동(`curated_fields`에 `tags`)은 수집이 연결을 건드리지 않는다. 저장은 `activity_tags`(V34).

## 실행

```sh
cd apps/dearby-api
# 로컬: 개발 DB를 띄우고 .env.local에 ANTHROPIC_API_KEY를 넣은 뒤 바로 한 번 수집
COLLECTION_ENGINE=cli ./gradlew :batch:bootRun --args=--collect-now   # 또는 .env.local의 COLLECTION_ENGINE
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

- 게시 활동의 매일 재확인(삭제한 TS 워커의 `--reverify`에 해당), 실패 알림, 비용 상한, 수집 기록(`collection_runs`) 화면(어드민에는 마지막 성공 날짜·받은 페이지만).
- 태그 추출(native-v1.md "태그", 설계 완료·구현 예정): 사전을 지시문에 넘기고 사전 밖 이름은 버린다.
- `api` 엔진의 실제 호출 검증: API 키가 생기면 한 번 돌려 확인해야 한다(Haiku 4.5의 서버 웹 검색·structured outputs는 문서 기준).
- 과거 기록: 웹 전체 검색의 실제 결과(2026-10-15, cli, 설정 없는 Let'Swift)는 공식 사이트 대신 X 계정(`x.com/letswiftkr`)에서 같은 이름의 초안 2건을 저장했다(`official_source = false`; 2분 45초, 입력 78,210·출력 17,258 토큰; CLI 내장 WebSearch는 검색 횟수가 0으로 기록). `cli` 엔진은 2026-10-15 로컬 개발 DB에서 Let'Swift(`https://letswift.kr/2026/`, 허용 `letswift.kr`)로 실제 수집했다(59초, 찾은 1·저장 1, 일정 7개 시각 정상, 입력 14,776·출력 6,183 토큰, 웹 검색 0회; 신청 주소는 원문 링크와 맞지 않아 비워졌다). 후속 후보: SNS·모음 사이트 제외 목록, 웹 전체 검색에서 찾은 공식 도메인 후보를 관리자에게 제안.
- 2026-10-18 정리 완료: 이 Mac의 launchd 작업(`com.dearby.catalog-subscription-worker`)과 `~/.dearby-deploy`의 TS 워커 배포 사본은 사용자가 `launchctl bootout`과 plist 삭제로 내렸다.

## 브라우저 렌더링 수집 — 2026-10-19

우와콘(`woowacon.com`)처럼 자바스크립트로 내용을 그리는 사이트는 서버 HTML에 글자가 거의 없어(121자) 수집이 "찾음 0"으로 끝났다. 사용자 결정으로 수집 설정에 `renderPages`(`program_collection_settings.render_pages`, V35)를 두고, 켜진 프로그램만 헤드리스 Chromium(Playwright for Java 1.63)으로 페이지를 실행한 뒤 `document.body.innerText`를 본문으로 쓴다(`batch/adaptor/browser/PlaywrightPageFetcher`).

- **언제 켜나**: 어드민 수집 설정의 `브라우저 렌더링 필요`. 서버 HTML을 받았을 때 본문이 거의 없고(수집 보고 "찾음 0", 토큰은 소비됨) 브라우저에서는 내용이 보이는 사이트에서만. 일반 사이트는 끈 채로 둔다(HTML 받기가 더 빠르고 싸다).
- **경계**: HTML 받기와 같다 — https만, 허용 호스트가 있으면 페이지와 하위 요청 모두 허용 호스트 밖은 차단(사설·내부 IP도 차단, `UrlGuard` 공용), 탐색 15초, 네트워크 유휴 대기 최대 5초(넘으면 그때까지 그려진 본문), 본문 3,000,000자. 이미지·글꼴·미디어 요청은 막는다. 실패는 거절 이유에 `렌더링 실패: …`로 남는다. 브라우저는 처음 쓸 때 띄우고 프로그램 하나의 수집이 끝나면 내린다.
- **이미지**: `batch/Dockerfile`이 `eclipse-temurin:25-jre-noble` 위에서 부트 jar 안의 Playwright CLI(`-Dloader.main=com.microsoft.playwright.CLI`)로 `install --with-deps chromium`을 실행해 `/ms-playwright`에 Chromium을 넣는다. api·admin 이미지는 그대로. 로컬(linux/arm64) 빌드 크기 **997 MB**(렌더링 전 batch 이미지 363 MB). 이미지 안에서 `app` 사용자로 Chromium 실행을 확인했다(샌드박스 옵션 없이 동작). 운영 compose는 평소처럼 `up -d --build batch`면 되고 플랫폼 플래그는 필요 없다(호스트 아키텍처의 Chromium을 받는다).
- **테스트**: 실제 브라우저는 띄우지 않는다. `CollectionJobTest`가 가짜 `RenderingPageFetcher`로 렌더링 경로 선택·거절 이유·브라우저 내림을 확인한다.
