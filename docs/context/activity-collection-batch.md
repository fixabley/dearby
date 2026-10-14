# 활동 수집 배치 (Kotlin · Spring Batch)

2026-10-15 사용자 결정으로 활동 수집을 TypeScript 구독 워커(`apps/catalog-worker`, Codex·Supabase)에서 Kotlin 멀티모듈의 `batch` 모듈로 옮긴다. 카탈로그 원본은 Kotlin DB다.

| 결정 | 내용 |
| --- | --- |
| 실행 | `apps/dearby-api/batch`, 별도 이미지(`batch/Dockerfile`)·compose 서비스 `batch`. 매일 한국 시간 00:00(`@Scheduled`, Asia/Seoul) |
| 모델 | Claude Haiku 4.5(`claude-haiku-4-5`), Anthropic Java SDK. 키는 `.env.prod`의 `ANTHROPIC_API_KEY`. 비어 있으면 수집하지 않고 경고만 남긴다 |
| 검토 | 수집 결과는 초안(`activities.publication_status = DRAFT`). 관리자가 `PUBLISHED`로 바꿔야 공개 API에 보인다. 게시된 활동은 다시 수집해도 덮어쓰지 않는다 |
| 수집 설정 | `program_sources`(프로그램별 공식 URL·정확한 허용 호스트·켜기/끄기). 새로 입력한다(Supabase 설정은 가져오지 않음) |
| 기록 | `collection_runs`(설정 하나 × 한국 날짜 하루당 한 행: 결과·찾은 수·저장 수·제외 사유·오류·토큰·검색 횟수). Spring Batch 실행 기록은 `batch_*` 테이블 |

## 수집 흐름

1. **후보 찾기**: Haiku + 웹 검색(`web_search_20250305`, `allowed_domains` = 허용 호스트, 최대 3회). 결과 URL은 검색 결과 블록에서 직접 읽고, 허용 호스트의 https 주소만 쓴다. 공식 URL은 항상 포함하고 최대 5페이지.
2. **원문 받기**: 서버가 직접 받는다. https, 허용 호스트 정확히 일치, 같은 경계 안의 리다이렉트 3번까지, 사설·내부 주소 거부, 15초, 3 MB, HTML만. JavaScript로만 그려지는 페이지는 본문이 비어 걸러진다.
3. **추출**: 받은 본문을 Haiku에 넘겨 JSON(structured outputs)으로 활동을 뽑는다. 웹 검색 결과의 citations와 structured outputs는 함께 쓸 수 없어(400) 검색과 추출을 나눴다. 본문은 15만 자까지 넘긴다.
4. **원문 대조**: 근거 구절(10~200자)이 원문에 있어야 한다(공백·마크다운 기호 차이 무시). 신청 주소는 원문 페이지의 `<a href>`에 있을 때만 남긴다. 읽을 수 없는 시각은 비우고 날짜 문구만 남긴다. **구절이 있다는 것은 모든 필드가 참이라는 증명이 아니다** — 그래서 초안으로만 저장한다.
5. **저장**: 같은 활동은 프로그램 + 정규화한 페이지 주소 + 회차 식별자로 만든 `source_id`로 찾는다. 시각 일정은 Asia/Seoul로 저장한다.

## 실행

```sh
cd apps/dearby-api
# 로컬: 개발 DB를 띄우고 .env.local에 ANTHROPIC_API_KEY를 넣은 뒤 바로 한 번 수집
./gradlew :batch:bootRun --args=--collect-now
# 운영: 상시 실행(매일 자정). 바로 한 번 돌리기
docker compose -f compose.prod.yaml --env-file .env.prod up -d batch
docker compose -f compose.prod.yaml --env-file .env.prod run --rm batch --collect-now
```

같은 설정·같은 한국 날짜는 한 번만 수집한다(`collection_runs` 유일 제약). 그날 다시 돌리려면 그날의 `collection_runs` 행을 지운다.

## 아직 없는 것

- 수집 설정 입력과 초안 검토·게시 화면(admin 모듈). 그전까지는 DB에 직접 넣고 바꾼다.
- 게시 활동의 매일 재확인(기존 워커의 `--reverify`), 실패 알림, 비용 상한.
- 실제 Claude 호출 검증: API 키가 생기면 한 번 돌려 결과와 토큰 사용량을 확인해야 한다. Haiku 4.5에서 웹 검색 도구·structured outputs 동작은 문서 기준이며 실제 호출로 확인하지 않았다.
- 기존 TS 워커의 launchd 작업(`com.dearby.catalog-subscription-worker`)이 아직 설치되어 있어 Supabase에 계속 쓴다. 새 배치가 동작하면 `node scripts/launchd.mjs uninstall`로 내린다.
