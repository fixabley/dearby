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

## 활동 단위 — 2026-10-18 사용자 지시

부스트캠프처럼 한 모집 페이지에서 문제 해결력 테스트·베이직 챌린지·멤버십·프리코스가 각각 활동 초안으로 쪼개져 들어왔다. 활동은 **프로그램의 기수(회차) 하나**다(사용자 정의: 활동은 프로그램을 기수별로 쪼개는 단위. 예: "부스트캠프 10기", "Let'Swift 2026". 트랙은 따로 지원하는 기수일 때만 별도 활동). 그 기수 안의 선발 단계·테스트·챌린지·교육 기간·결과 발표·면접·마감은 그 활동의 `schedules`에 `kind`(DEADLINE·ANNOUNCEMENT·INTERVIEW·EVENT)로 들어간다. 추출 지시문·출력 스키마와 `source_id` 규칙을 그렇게 바꿨다. 이전 규칙으로 들어온 쪼개진 초안은 `source_id`가 달라 자동으로 합쳐지지 않으므로 어드민 활동 화면에서 지운다(지운 초안은 다시 만들지 않는다).

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

## 아직 없는 것

- 프로그램별 수집 설정 입력과 초안 검토·게시 화면(admin 모듈). 그전까지는 DB에 직접 넣고 바꾼다.
- 게시 활동의 매일 재확인(삭제한 TS 워커의 `--reverify`에 해당), 실패 알림, 비용 상한.
- `api` 엔진의 실제 호출 검증: API 키가 생기면 한 번 돌려 확인해야 한다(Haiku 4.5의 서버 웹 검색·structured outputs는 문서 기준).
- 웹 전체 검색의 실제 결과(2026-10-15, cli, 설정 없는 Let'Swift): 공식 사이트 대신 X 계정(`x.com/letswiftkr`)에서 같은 이름의 초안 2건을 저장했다(`official_source = false`). 2분 45초, 입력 78,210·출력 17,258 토큰. CLI 내장 WebSearch는 사용량에 검색 횟수가 잡히지 않아 0으로 기록된다. 후속 후보: SNS·모음 사이트 제외 목록, 웹 전체 검색에서 찾은 공식 도메인 후보를 관리자에게 제안.
- `cli` 엔진은 2026-10-15 로컬 개발 DB에서 Let'Swift(`https://letswift.kr/2026/`, 허용 `letswift.kr`)로 실제 수집했다: 59초, 찾은 1·저장 1(DRAFT), 일정 7개 시각 정상(19:00 KST), 입력 14,776·출력 6,183 토큰, 웹 검색 0회(모델이 공식 페이지만으로 충분하다고 판단). 신청 주소는 원문 링크와 맞지 않아 비워졌다.
- 이 Mac의 launchd 작업(`com.dearby.catalog-subscription-worker`)과 `~/.dearby-deploy`의 TS 워커 배포 사본은 저장소 밖이라 2026-10-18 소스 삭제로 멈추지 않는다. 사용자가 `launchctl bootout gui/$(id -u)/com.dearby.catalog-subscription-worker`와 plist 삭제로 내린다(저장소의 `scripts/launchd.mjs uninstall`은 더 없다).
