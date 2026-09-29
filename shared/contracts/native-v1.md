# Native API v1 — 구현 계약

2026-09-27. 사용자 제품 정본은 docs/product/native-spec-2026-09.md. 아래는 검증 가능한 첫 수직 구현을 위한 가역적 기술 계약이며 배포 완료를 의미하지 않는다. 변경은 조율 세션에 알리고 양 플랫폼·서버를 함께 맞춘다.

## 전송

JSON camelCase, UUID 문자열 식별자, UTC ISO8601 시각. 기본 `/v1`. 인증은 `Authorization: Bearer <sessionToken>`. HTTPS 운영 URL은 환경 설정이며 임의 실서비스 도메인을 만들지 않는다. 로컬 개발 HTTP 허용은 개발 구성에만 한정한다. 오류는 `{ "error": { "code": "...", "message": "..." } }`, 인증 오류 401, 권한 403, 없음/철회 404, 잘못된 입력 422, 충돌 409, rate limit 429. 민감값은 로그에 기록하지 않는다.

## 모델

- Profile: `id`, `name`, `job`, `introduction`, `contacts: Contact[]`, `histories: History[]`, `updatedAt`.
- Contact: `id`, `kind`(phone/email/kakao/instagram/github/behance), `label`, `value`. kakao value는 URL 또는 ID, label로 구분. 외부 URL 열기는 허용 scheme 검증.
- History: `id`, `title`, `role`, `startDate`(YYYY-MM-DD), `endDate`(nullable), `description`.
- Card: `id`, `ownerId`, `name`(명함 이름), `description`, `profileName`, `job`, `introduction`, `contacts: Contact[]`, `histories: History[]`, `createdAt`. 공개 연락처·이력만 포함. 원본 프로필 전체를 클라이언트에서 가리는 방식 금지.
- ExchangeContext: `{ activityId: string|null, label: string|null }`. 등록 활동, 자유 입력, 둘 다 null 중 하나. 활동 선택은 참가 인증이 아니다.
- Receipt: `id`, `card: Card`, `context: ExchangeContext`, `receivedAt`, `reciprocal: boolean`.
- GuestSavedCard (기기 전용): `cardId`, `context`, `savedAt`. 영구 원본은 ID 중심, 오프라인 표시 캐시와 구분. 가져오기 성공 항목만 이관 완료 처리. 실패/미선택 보존.

명함 발행은 현재 공개 선택의 스냅샷이다. 프로필 편집이 기존 발행본에 자동 전파되지 않는다. 이는 공개 범위가 의도치 않게 확대되는 것을 막는 초기 구현 결정이다. 명함 철회는 서버 재조회 시 404이며 이미 전달된 정보 자체의 원격 삭제를 보장하지 않는다. 갱신 정책 확대는 별도 이슈로 추적한다.

## 첫 수직 구현 API

| 메서드·경로 | 요청 | 성공 응답 |
| --- | --- | --- |
| POST /auth/challenges | `{email}` | 202 `{challengeId, expiresAt}` |
| POST /auth/sessions | `{challengeId, code}` | 200 `{sessionToken, profileId}` |
| DELETE /auth/session | 없음 | 204 |
| GET /profile | 없음 | 200 Profile |
| PUT /profile | `{name,job,introduction,contacts,histories}` | 200 Profile |
| GET /cards | 없음 | 200 `{items: Card[]}` (자신의 명함) |
| POST /cards | `{name,description,contactIds,historyIds}` | 201 Card |
| GET /cards/:id | 없음·비로그인 가능 | 200 Card |
| DELETE /cards/:id | 없음·소유자만 | 204 |
| GET /wallet | 없음 | 200 `{items: Receipt[]}` |
| POST /wallet/import | `{items:[{cardId,context,savedAt}]}` | 200 `{items:[{cardId,status:"imported"|"alreadySaved"|"failed",receiptId:string|null}]}` |
| POST /exchanges | `{cardId,recipientProfileId,context,requestId}` | 201 `{receiptId,deliveredAt}` |

발행·개인 프로필·wallet·import·exchange는 로그인 필수. POST /exchanges cardId 소유권 확인. 동일 sender+requestId 재시도는 같은 결과, body 변경은 409. reciprocal은 양쪽 소유자의 명함이 서로의 서버 wallet에 전달된 경우에만 true. 클라이언트 로컬 클릭만으로 true를 만들지 않는다. 가져오기는 동일 사용자+cardId 중복을 막는다. 반복 교환 이벤트는 requestId가 다를 때 이력에 보존한다.

OTP 코드는 응답에서 반환하지 않는다. 개발용 메일 수신기는 별도 명시적 테스트 구성으로 주입하고 운영 기본값은 미설정 실패이다. 인증번호 해시 저장·만료·시도/재전송 제한·세션 폐기 테스트 필수. 계정 존재 유추를 줄이는 동일 응답 적용. 토큰은 OS 보안 저장소 사용.

## 병렬 작업 경계

- iOS: apps/ios 및 자기 인계 문서. SwiftUI, 실제 로컬 영속 저장, 명시적인 서버 연결/미설정 상태. fixture 계정은 운영 인증으로 보이지 않게 개발 구성에서만 사용.
- Android: apps/android 및 자기 인계 문서. Kotlin/Compose, Room, 같은 전송 계약과 독립적인 네이티브 UI.
- API: apps/dearby-api 및 자기 인계 문서. 이 계약을 실행하고 인증·공개 projection·저장·멱등성·오류를 실제 HTTP 테스트.
- 공통 계약·루트 설정·웹 삭제·통합·GitHub PR: 조율 담당.

미완료 활동/푸시/캘린더/if(kakao)를 가짜 성공 처리하지 않는다. 외부 조건 없이 진행할 수 있는 UI·도메인·저장·오류 처리부터 구현한다. 제품 전체 완료와 이번 구현 묶음 완료를 구분한다.

## 플랫폼 통합 결정 — 2026-09-27

- API base 설정은 origin(예: 개발 `http://127.0.0.1:4310`)이며 `/v1`을 포함하지 않는다. 클라이언트가 `/v1`을 한 번 붙인다. HTTP는 개발 구성에만 허용한다.
- 배포 도메인 없이 설치된 앱끼리 검증할 QR payload는 `dearby://card/<UUID>`다. 선택 맥락은 `?label=<percent-encoded text>` 또는 `?activityId=<UUID>` 하나만 허용한다. label 최대 200자, 비어 있으면 생략한다. unknown query·중복 키·두 맥락 동시 지정·잘못된 UUID·다른 host/path·fragment/userInfo/port를 거부한다. UUID 대소문자는 동일 ID로 취급한다.
- 예: `dearby://card/11111111-1111-4111-8111-111111111111?label=if%28kakao%29`. 맥락은 사용자가 입력한 정보이며 주최 측 참가 확인이 아니다.
- 이 custom scheme은 앱 미설치 브라우저 수신 링크가 아니다. 운영 HTTPS origin, Universal Links/App Links, 미설치 안내는 #43에서 검증한다. 도메인을 임의 생성하거나 작동하는 웹 링크라고 표시하지 않는다.
- 철회한 명함은 공개 GET에서 404, 새 wallet 응답에서 제외한다. receipt 자체는 DB에 보존하며 철회 안내용 tombstone UX는 #41의 후속 계약이다. 기존 기기 캐시의 원격 삭제를 보장하지 않는다.

## 웹 비로그인 명함 저장 — 2026-09-29 승인

이 섹션은 웹 전용 게스트 저장 계약이다. 위 로그인·프로필·명함 발행·native wallet 계약은 유지한다. 이력서 열람은 기존 공개 `GET /v1/cards/:id`의 선택된 명함 snapshot만 뜻하며, 원본 개인 Profile이나 선택하지 않은 연락처·이력을 공개하지 않는다. PDF 지원 여부는 별도 결정이다. path UUID는 발행된 미철회 명함의 조회·저장 가능 여부만 판정하며 방문자 인증 수단이 아니다.

웹은 same-origin Next 서버 프록시를 사용한다. API origin은 서버 env `DEARBY_API_ORIGIN`; 브라우저는 API/Supabase 키를 받지 않는다. Next와 API에 동일한 서버 전용 `GUEST_PROXY_SECRET`(최소 32자)을 root가 배포 시 설정한다. 모든 guest API에는 Next가 `X-Guest-Proxy-Key`를 넣고, 쿠키가 있으면 `X-Guest-Token`을 넣는다. 클라이언트가 보낸 동명 헤더는 전달하지 않고 서버 env/cookie로 다시 구성한다. API는 Cookie, Origin, forwarded IP를 방문자 인증으로 신뢰하지 않는다.

| 메서드·경로 | 성공 응답 | 의미 |
| --- | --- | --- |
| GET /v1/guest/cards | 200 `{items: Card[]}` | 자기 세션의 미철회 공개 명함 목록 |
| PUT /v1/guest/cards/:id | 무토큰 첫 저장 201 `{cardId,status:"saved",guestToken}` | path UUID·DB존재·미철회 확인 후 세션과 저장을 원자 생성 |
| PUT /v1/guest/cards/:id | 기존 토큰 200 `{cardId,status:"saved"\|"alreadySaved"}` | 중복 없이 저장, 토큰/세션 재생성 없음 |
| DELETE /v1/guest/cards/:id | 204 | 자기 참조만 제거; 이미 제거·철회된 ID도 멱등 처리 |
| DELETE /v1/guest/session | 204 | 자기 세션 및 저장 참조 폐기 |

요청 body는 필요 없다. 새 세션 생성 전용 endpoint는 없다. 무쿠키 목록 조회는 Next에서 `{items:[]}`로 처리해 API 호출이나 DB 세션 생성을 하지 않는다. 첫 저장 응답 `guestToken`은 Next 서버만 소비하며 브라우저 JSON/로그에 노출하지 않는다. DB에는 SHA-256 token digest와 card ID 연결만 저장하고 원문 토큰·쿠키·방문자 개인정보를 저장하지 않는다. 회원 wallet과 guest 저장은 서로 독립이며 자동 전환/가져오기를 수행하지 않는다.

쿠키 이름은 `__Host-dearby_guest`, `HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=34560000`이며 **Domain 속성을 넣지 않는다**. 서버 세션은 자동 만료가 없고 `expiresAt` 응답도 없다. 브라우저 쿠키는 400일이며 요청 시 같은 token의 Max-Age만 갱신한다. 갱신으로 새 token/session을 만들거나 기존 저장을 지우지 않는다. 브라우저 자체 보관 제한·사용자 쿠키 삭제로 접근이 끊길 수 있으며 무제한 쿠키 보관을 보장하지 않는다. 명시적 세션 삭제에서 API 401(이미 폐기)도 웹은 쿠키를 삭제한다. 다른 401을 임의로 새 세션으로 대체하지 않는다.

GET과 성공 mutation 모두 `Cache-Control: no-store`다(API는 오류 응답에도 적용). 웹 프록시도 동일하게 유지하며 개인화 응답/Set-Cookie를 CDN 공유 캐시하지 않는다. 웹 mutation은 Origin을 현재 허용된 웹 origin과 정확히 대조하고 JSON 또는 custom-header 조건을 검증한다. 누락/불일치 Origin도 거부한다. SameSite만으로 CSRF 검증을 대체하지 않는다. guest API는 Cookie를 받는 것만으로 권한을 부여하지 않으며 proxy secret 없는 직접 요청은 거부한다. 첫 무토큰 저장은 웹에서 Web Locks 등으로 탭 간 직렬화하여 중복 세션/쿠키 덮어쓰기를 방지한다. 별도 기기/경쟁 요청 사이 무토큰 멱등성을 서버가 보장하지는 않는다.

- 401 `GUEST_SESSION_INVALID`: 토큰 누락(기존 세션 필요 경로), 잘못된 형식, 미존재/폐기. 해당 token으로 PUT도 자동 재생성하지 않는다.
- 403 `FORBIDDEN`: proxy secret 누락/불일치. 서버 secret 미설정/너무 짧으면 503 `GUEST_UNAVAILABLE`.
- 404 `NOT_FOUND`: 조회/저장 대상이 없거나 철회됨. UUID 형식 오류는 422 `INVALID_INPUT`. 저장 제거는 공개 가능 여부와 무관하게 자신의 참조만 지울 수 있다.
- 409 `GUEST_CAPACITY_EXCEEDED`: 세션당 활성 명함 100개 또는 전체 guest 세션 10,000개 상한. 철회 명함은 조회에서 제외하고 다음 저장에서 참조를 정리해 용량을 반환한다. 동일 명함 재저장은 상한에서도 성공한다. 전체 세션 상한은 신규 세션만 차단하며 기존 목록/저장/삭제는 유지한다. 자동 만료가 없으므로 전체 상한은 운영 해소가 필요하다; 임의 오래된 세션 삭제를 의미하지 않는다.
- 429 `RATE_LIMITED`: 단일 API 프로세스 고정 1분 창 기준 전체 guest 요청 1,200회, 유효 세션당 120회, 신규 세션 생성 60회. 카운터는 메모리이며 재시작 시 초기화된다. 저장 상한은 DB에 유지된다. forwarded IP를 신뢰해 우회시키지 않는다. 웹은 신뢰 가능한 실제 client 단위 요청 제한/남용 방어를 별도 적용해야 한다.
- 저장 실패는 500 `INTERNAL_ERROR`이며 새 세션과 명함 참조를 함께 rollback한다. DB·proxy secret·guest token 등 내부값은 오류 응답/로그에 넣지 않는다.

HTTPS ingress는 catalog GET/HEAD, 공개 cards/:id GET/HEAD, 위 guest 경로만 좁혀 공개한다. 기존 `/v1/profile`, 회원 `/v1/wallet`, 명함 생성/철회, SMTP/auth는 열지 않는다. guest proxy secret 생성·배포와 nginx 운영 변경은 root가 수행하며 앱서버 구현 PR만으로 운영 반영됐다고 표시하지 않는다.
