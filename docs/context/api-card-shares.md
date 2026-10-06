# API 명함 공유 기록·게스트 공유 저장 인계

2026-10-06 KST. API 담당 worktree `api-agent`, branch `fixabley/api-agent`. 계약은 [native-v1 「명함 공유 기록과 게스트 공유 정보 저장」](../../shared/contracts/native-v1.md#명함-공유-기록과-게스트-공유-정보-저장--2026-10-06-승인)이다. 로컬 구현·테스트 결과이며 운영 DB 마이그레이션·ingress·배포는 하지 않았다.

## 구현

- DDL `supabase/migrations/20261006000000_api_card_shares.sql`: `dearby_api.card_shares`(활동 `{id,title}` 사본 JSON, `created_at`)와 `dearby_api.guest_card_shares`(세션 digest·명함 ID·공유 ID·`saved_at`). 처음 API 마이그레이션의 권한 부여는 그때 있던 테이블에만 적용됐으므로, 새 테이블에도 runtime 권한·RLS·시퀀스 권한과 브라우저 role 차단을 다시 적용했다.
- 연결 테이블은 `(session_digest, card_id) → guest_cards`에 `ON DELETE CASCADE`다. 그래서 `DELETE /v1/guest/cards/:id`, `DELETE /v1/guest/session`, 저장할 때 하는 철회 명함 정리에서 공유 기록도 함께 지워진다. `(share_id, card_id) → card_shares(id, card_id)` FK가 다른 명함의 공유와 연결되는 것을 막는다.
- `POST /v1/cards/:id/shares`(`src/shares.ts`): 확인 순서는 401 → body 422 → 404 → 403 → 카탈로그 422 `INVALID_SELECTION`이다. 활동 ID는 소문자로 맞춘 뒤 중복을 검사하고, 공개 카탈로그(`catalog_public_snapshot`, published만)와 대조한다. 카탈로그를 읽지 못하면 503 `CATALOG_UNAVAILABLE`이다. 활동이 0개이면 카탈로그를 읽지 않는다.
- `GET /v1/shares/:id`: 비로그인. 공유가 없거나 명함이 철회되면 404다. HEAD는 Fastify가 자동으로 제공한다.
- `PUT /v1/guest/shares/:id`(`src/guest.ts`): 기존 명함 저장 로직(`saveCard`)을 함께 쓴다. 따라서 세션 생성·100장·10,000 세션·생성 60회/분 규칙이 같다. 명함당 공유 기록은 20개까지다. 이미 연결된 공유는 존재 여부를 상한보다 먼저 확인하므로 상한에서도 `alreadySaved`다. 요청 제한은 guest plugin의 같은 카운터를 쓴다.
- `GET /v1/guest/cards`: `items`는 그대로 두고 `shares`(저장 순서)를 추가했다. 철회 명함의 공유는 빠진다. `/v1/cards*`와 `/v1/guest/cards/:id` 동작은 그대로다.
- OpenAPI: 27개 operation(새 POST·GET·자동 HEAD·PUT). guest 목록 응답만 별도 schema이며, owner `GET /v1/cards`의 schema는 바꾸지 않았다.

## 계약 해석 — 메인 확인 필요

- 계약은 "DB에는 세션 digest·명함 ID·공유 ID 연결만 저장한다"고 하지만 응답 `GuestShare.savedAt`을 만들려면 저장 시각이 필요하다. 서버 시각 `saved_at`만 추가로 저장했다(receipt `received_at`과 같은 방식이며 방문자 정보가 아니다). 다르게 정하면 알려 달라.
- 회원 `POST /v1/cards/:id/shares`에는 공유 수 상한이나 요청 제한을 두지 않았다. 계약에 없고 공개 ingress에도 열리지 않는 경로다. 서버 연결 단계에서 정할 사항이다.

## 웹 테스트 데이터

(2026-10-06 테스트 전체 삭제로 아래 파일은 지금 저장소에 없다. 당시 기록이다.) `apps/dearby-api/test/fixtures/card-shares.json`에 가상 UUID를 고정한 데이터를 두었다. 명함 3장(3번은 철회 대상)과 공유 5건이 있다. 공유 1은 활동 2개, 활동 2는 명함 두 장에 걸치고, 공유 4는 활동이 없다(`활동 없음` 묶음). 공유 5는 철회 명함의 공유다. `guestSaves`는 저장 순서별 실제 응답(201/200, `saved`·`alreadySaved`)이다. `afterRevocation`은 철회 뒤의 `GET /v1/shares/:id` 404와 `GET /v1/guest/cards` 응답이다(`items`는 명함 ID로 적었다). `test/shares.test.ts` 첫 테스트가 이 파일로 실제 PostgreSQL에 데이터를 넣고 HTTP 응답과 일치하는지 확인한다. 웹 계약 대역 `apps/web/tests/fixture-api.ts`는 유저플로우 담당 소유라 수정하지 않았다.

## root 운영 인계

- **병합 = 운영 DB 적용.** Prisma API의 DB는 별도 PostgreSQL이 아니라 Supabase 운영 프로젝트의 private `dearby_api` schema다([Prisma 인계](api-prisma-postgres.md): 기존 `20260929150000_api_prisma.sql`도 Supabase GitHub 자동 migration으로 적용됐다). 그래서 main 병합 시 이 마이그레이션이 운영 DB에 자동 적용될 가능성이 높다. 사용자 승인 후 메인이 병합한다.
- 이 마이그레이션은 새 테이블 두 개만 추가하므로 현재 운영 API 이미지에는 영향이 없다. 새 경로는 새 이미지를 배포해야 작동하며, 테이블이 없는 DB에서 새 코드를 실행하면 공유·게스트 목록 경로가 500이다. 순서는 마이그레이션 적용 → API 배포다.
- ingress는 바꾸지 않았다. 계약대로 `nginx-web-guest.location.conf` 형식에 다음 두 가지를 추가하는 안을 제안한다: `^/v1/shares/[0-9a-fA-F-]{36}$` GET/HEAD(공개 명함 location과 같은 헤더 제거), guest regex를 `^/v1/guest/(cards(?:/[0-9a-fA-F-]{36})?|shares/[0-9a-fA-F-]{36}|session)$`로 확장. `POST /v1/cards/:id/shares`는 열지 않는다.

## 검증

2026-10-06 KST, Node 24.21.0, `apps/dearby-api`에서 실제 실행: `npm run typecheck`·`npm run lint`(경고 0)·`npm run build`·`npm test`(격리 임시 Supabase PostgreSQL 컨테이너, **41 tests pass, fail 0**)·`git diff --check` 통과. 기존 테스트 중 `GET /v1/guest/cards` 응답을 `{items}`로만 비교하던 5곳(`guest.test.ts` 4, `prisma-import.test.ts` 1)은 계약상 추가된 `shares: []`를 포함하도록 고쳤다. `DROP TABLE guest_cards` 오류 주입은 새 FK 때문에 `CASCADE`를 붙였다. 권한 테스트의 RLS 테이블 수는 14에서 16으로 바뀌었다. 운영 DB·ingress·배포·secret은 확인하거나 바꾸지 않았다. 큰 글씨 접근성 테스트는 이 API 작업에 해당하지 않는다.
