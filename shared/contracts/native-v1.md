# Native API v1 — 구현 계약

2026-09-27. 사용자 제품 정본은 docs/product/native-spec-2026-09.md. 아래는 검증 가능한 첫 수직 구현을 위한 가역적 기술 계약이며 배포 완료를 의미하지 않는다. 변경은 조율 세션에 알리고 양 플랫폼·서버를 함께 맞춘다.

## 전송

JSON camelCase, UUID 문자열 식별자, UTC ISO8601 시각. 기본 `/v1`. 인증은 `Authorization: Bearer <accessToken>`(2026-10-10부터 패스키 로그인이 발급하는 JWT, 아래 "패스키 로그인"). HTTPS 운영 URL은 환경 설정이며 임의 실서비스 도메인을 만들지 않는다. 로컬 개발 HTTP 허용은 개발 구성에만 한정한다. 오류는 `{ "error": { "code": "...", "message": "..." } }`, 인증 오류 401, 권한 403, 없음/철회 404, 잘못된 입력 422, 충돌 409, rate limit 429. 민감값은 로그에 기록하지 않는다.

## 모델

- Profile: `id`, `name`, `job`, `introduction`, `contacts: Contact[]`, `histories: History[]`, `updatedAt`.
- Contact: `id`, `kind`(phone/email/kakao/instagram/github/behance), `label`, `value`. kakao value는 URL 또는 ID, label로 구분. 외부 URL 열기는 허용 scheme 검증.
- 필수 연락처(2026-10-06 사용자 결정): 프로필은 `phone`과 `email`을 각각 하나 이상 가져야 한다. 나머지 종류는 사용자가 골라 추가한다. `PUT /profile`에서 빠지면 422 `INVALID_INPUT`(필드 `contacts`)이다. phone은 숫자·`+`·`-`·공백만 허용하고 숫자 8~15자리, email은 기본 형식을 검사한다. (2026-10-10 패스키 로그인 전환으로 로그인 이메일이 없어져, 앱이 email을 미리 채우던 규칙은 없앴다.) 명함 발행 때 공개할 연락처를 고르는 방식은 그대로이며, 필수는 프로필 기준이지 명함 공개 의무가 아니다.
- History: `id`, `title`, `role`, `startDate`(YYYY-MM-DD), `endDate`(nullable), `description`.
- 활동 이력 날짜 입력(2026-10-06 사용자 결정): 앱 프로필 편집에서 이력마다 시작일(필수)과 종료일(선택, `진행 중`이면 비움)을 날짜 선택기로 입력한다. 연·월만 고르는 경우 일은 1일로 저장한다. 종료일은 시작일보다 앞설 수 없다(API도 422). 표시는 `2026.03 – 2026.06`, 진행 중이면 `2026.03 – 진행 중`이다.
- Card: `id`, `ownerId`, `name`(명함 이름), `description`, `profileName`, `job`, `introduction`, `contacts: Contact[]`, `histories: History[]`, `createdAt`. 공개 연락처·이력만 포함. 원본 프로필 전체를 클라이언트에서 가리는 방식 금지.
- ExchangeContext: `{ activityId: string|null, label: string|null }`. 등록 활동, 자유 입력, 둘 다 null 중 하나. 활동 선택은 참가 인증이 아니다.
- Receipt: `id`, `card: Card`, `context: ExchangeContext`, `receivedAt`, `reciprocal: boolean`.
- GuestSavedCard (기기 전용): `cardId`, `context`, `savedAt`. 영구 원본은 ID 중심, 오프라인 표시 캐시와 구분. 가져오기 성공 항목만 이관 완료 처리. 실패/미선택 보존.

명함 발행은 현재 공개 선택의 스냅샷이다. 프로필 편집이 기존 발행본에 자동 전파되지 않는다. 이는 공개 범위가 의도치 않게 확대되는 것을 막는 초기 구현 결정이다. 명함 철회는 서버 재조회 시 404이며 이미 전달된 정보 자체의 원격 삭제를 보장하지 않는다. 갱신 정책 확대는 별도 이슈로 추적한다.

## 첫 수직 구현 API

| 메서드·경로 | 요청 | 성공 응답 |
| --- | --- | --- |
| (인증) | 아래 "패스키 로그인"의 `/auth/passkeys/*`, `/auth/refresh`, `/auth/logout`, "애플 로그인"의 `/auth/apple/*` | 2026-10-10 이메일 인증번호(`/auth/challenges`, `/auth/sessions`, `DELETE /auth/session`)를 대체 |
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

OTP 코드는 응답에서 반환하지 않는다. 개발용 메일 수신기는 별도 명시적 테스트 구성으로 주입하고 운영 기본값은 미설정 실패이다. 인증번호 해시 저장·만료·시도/재전송 제한·세션 폐기 테스트 필수. 계정 존재 유추를 줄이는 동일 응답 적용. 제한은 사용자와 서비스 단위로 둔다(2026-10-06 사용자 결정: 운영 프록시가 사용자 IP를 보존하지 못해 IP 단위 제한은 쓰지 않는다). 발송은 정규화한 이메일마다 재전송 간격 1분·시간당 5회·하루 10회, 서비스 전체 시간당 100회·하루 400회다(개인 Gmail 발신 한도 약 500통/일보다 낮게). 실패한 발송도 센다. 검증은 인증번호마다 5회, 서비스 전체 분당 600회다. 제한에 걸리면 429 `RATE_LIMITED`이며, 그 밖의 발송 응답은 계정 존재 여부와 관계없이 같다. 토큰은 OS 보안 저장소 사용.

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

## 명함 공유 기록과 게스트 공유 정보 저장 — 2026-10-06 승인

사용자 결정: 비로그인 웹 게스트 저장에 "어떤 공유로 받았는지와 그 공유에 담긴 활동"을 함께 저장하고 `/saved`에서 활동별로 묶어 보여 준다. 위 웹 게스트 계약의 쿠키·프록시 secret·Origin·캐시·오류·상한 규칙을 그대로 적용하고 아래만 추가한다. 기존 `/v1/cards/:id`와 `/v1/guest/cards*` 동작은 바꾸지 않는다.

### 모델

- CardShare: `id`(UUID), `cardId`, `activities: [{id, title}]`(0~10개), `createdAt`. 소유자가 명함을 공유할 때 만드는 기록이다. `activities`는 생성 시점의 카탈로그 활동 ID와 제목 사본이며 이후 카탈로그 변경을 따라가지 않는다. 활동은 사용자가 고른 정보이며 참가 확인이 아니다.
- 공유 ID는 명함 ID처럼 공개 조회 가능 여부만 판정하며 방문자 인증 수단이 아니다. 명함이 철회되면 그 명함의 모든 공유도 공개 조회·저장에서 404다. 공유 단건 철회는 이번 범위가 아니다.
- 게스트 저장은 세션당 명함 1건을 유지하고, 그 명함에 연결된 공유 ID를 0개 이상 기록한다. 같은 명함을 여러 공유로 받으면 공유 기록이 늘어나며 명함은 중복되지 않는다. 명함당 공유 기록 최대 20개. DB에는 세션 digest·명함 ID·공유 ID 연결만 저장한다.

### API

| 메서드·경로 | 인증 | 성공 응답 | 의미 |
| --- | --- | --- | --- |
| POST /v1/cards/:id/shares | 로그인·소유자만 | 201 CardShare | body `{activityIds: string[]}`. 중복 없이 0~10개, 모두 현재 카탈로그에 있는 활동이어야 한다(아니면 422). 철회·타인 명함은 404/403 |
| GET /v1/shares/:id | 비로그인 가능 | 200 `{share: CardShare, card: Card}` | 공유와 공개 명함 사본. 없거나 명함 철회 시 404 |
| PUT /v1/guest/shares/:id | 게스트 프록시 | 무토큰 첫 저장 201 `{cardId, shareId, status:"saved", guestToken}`, 기존 토큰 200 `{cardId, shareId, status:"saved"\|"alreadySaved"}` | 공유를 확인해 그 명함을 저장하고 공유 기록을 연결한다. 명함이 이미 저장돼 있고 공유가 새로우면 `saved`, 둘 다 있으면 `alreadySaved` |
| GET /v1/guest/cards | 게스트 프록시 | 200 `{items: Card[], shares: GuestShare[]}` | 기존 `items`는 그대로 두고 `shares`를 추가한다 |

- GuestShare: `{cardId, shareId, activities: [{id, title}], savedAt}`. 철회된 명함의 공유는 `items`와 함께 제외한다. `/v1/guest/cards/:id` 저장(공유 없이 받은 명함)은 공유 기록 없이 계속 동작한다.
- `DELETE /v1/guest/cards/:id`는 그 명함의 공유 기록도 함께 지운다. `DELETE /v1/guest/session`은 모두 지운다.
- 409 `GUEST_CAPACITY_EXCEEDED`에 명함당 공유 기록 20개 상한을 추가한다. 이미 연결된 공유의 재저장은 상한에서도 성공한다. 요청 제한은 기존 게스트 규칙과 같은 카운터를 쓴다.
- HTTPS ingress에는 `GET/HEAD /v1/shares/:id`, `PUT /v1/guest/shares/:id`만 추가한다. `POST /v1/cards/:id/shares`는 회원 경로이므로 열지 않는다.

### 웹

- 공유 링크는 `/s/:shareId`다. 공개 명함과 "함께 공유된 활동"을 보여 주고, 저장 버튼은 `PUT /v1/guest/shares/:id`를 쓴다. 기존 `/cards/:id`는 공유 정보 없이 유지한다.
- `/saved`는 `전체 | 활동별` 보기를 제공한다. 활동별 보기는 공유 기록의 활동으로 묶고, 여러 활동에 걸친 명함은 각 묶음에 모두 나오며, 활동이 없는 명함은 마지막 `활동 없음` 묶음에 둔다(모바일 명함함 규칙과 같다).

### 현재 한계

앱의 공유 QR은 아래 "모바일 실제 연결 경계"에서 `https://<웹 origin>/s/<shareId>`로 정했다(`dearby://share/` scheme은 만들지 않는다). 운영 DB 마이그레이션·ingress 변경·배포는 사용자 승인 후 root가 수행한다.

## 모바일 실제 연결 경계 — 2026-10-06 사용자 결정 반영

사용자 결정: "QR 공유는 실제 동작까지", "활동 신청이 쉽고 명함 제작·교환이 간결해야 한다", "비로그인 사용자는 세션 기준으로 저장이 유지되어야 한다", "유니버설 링크를 적용한다". 이 절은 iOS·Android 오프라인 프로토타입 범위([모바일 프로토타입](../../docs/context/mobile-ui-prototype.md)) 중 아래 항목만 실제 서비스로 바꾼다. 나머지 화면은 예시로 남는다. 근거 조사: `docs/research/activity-and-qr-friction-2026-10-06.md`(PR #94).

### 실제로 바꾸는 것

| 영역 | 동작 | 사용 API |
| --- | --- | --- |
| 발견·활동 상세 | 공개 카탈로그를 보여 주고, 신청 CTA는 활동의 공식 신청 URL을 외부 브라우저로 연다. 실패하면 오류와 다시 시도를 보여 주며 예시 활동을 섞지 않는다. | GET /v1/activities (2026-10-14부터, 아래 "활동 조회") |
| 로그인 | 패스키(2026-10-10 결정, 아래 "패스키 로그인"). 명함 발행·공유와 받은 명함 저장을 할 때만 요구하고, 발견·신청·받은 명함 보기는 로그인 없이 쓴다. access·refresh 토큰은 OS 보안 저장소에 둔다. | /v1/auth/* |
| 명함 제작 | 프로필 입력과 공개할 연락처·이력 선택을 한 흐름으로 마치고 바로 발행한다. 수정은 새 발행본을 만든다(기존 스냅샷 규칙 유지). | /v1/profile, POST /v1/cards |
| QR 공유 | QR 화면에 들어오면 현재 명함으로 공유를 만들고 `https://<웹 origin>/s/<shareId>`를 QR로 보여 준다. 활동 선택은 선택 사항이다. 공유 버튼은 같은 URL을 OS 공유 시트로 넘긴다. | POST /v1/cards/:id/shares |
| 받기 | 앱 안 스캐너(카메라·사진)와 유니버설 링크/App Links는 같은 `/s/<shareId>` URL을 해석해 공유 명함 화면을 연다. 기존 `dearby://card/<UUID>`도 계속 해석한다. 앱이 없거나 연결 검증에 실패하면 같은 URL이 웹 공유 명함으로 열린다. | GET /v1/shares/:id |

- 앱은 `https`, 설정한 웹 도메인, 경로 `/s/<UUID>`만 받고 query·fragment·다른 host를 거부한다. 도메인은 아래 "연결 설정"의 환경값으로 정한다.
- 유니버설 링크·App Links 경로는 `/s/*` 하나다. 웹은 `/.well-known/apple-app-site-association`, `/.well-known/assetlinks.json`을 환경값(Apple Team ID·번들 ID, Android 패키지·서명 SHA-256)으로 만든다. 값이 없으면 404다. 값 제공은 #90, 배포는 #91에 묶인다.

### 비로그인 사용자의 저장 유지

2026-10-06 사용자 결정: 비로그인 사용자는 앱을 설치하지 않았고 로그인하지 않은 사람이다. QR을 기본 카메라로 읽으면 기본 브라우저에서 웹 `/s/<shareId>`가 열린다. 서버가 만료 없는 게스트 세션 ID를 발급하고, 저장 데이터는 DB에 두며 API가 세션 ID로 조회한다. 브라우저에는 세션 ID 쿠키만 둔다.

- 웹: 위 "웹 비로그인 명함 저장" 계약의 서버 게스트 세션(`__Host-dearby_guest` 쿠키)을 그대로 쓴다. 서버 세션은 만료되지 않는다. 브라우저는 쿠키 수명을 최대 400일로 제한하므로, 방문할 때마다 Max-Age를 갱신한다(기존 규칙). 1년 넘게 방문하지 않거나 사용자가 쿠키를 지우면 접근이 끊긴다.
- 홈 화면 추가: Android Chrome은 바로가기·설치 모두 Chrome과 같은 쿠키를 쓴다. iOS는 '웹 앱으로 열기'가 기본으로 켜져 있고 사이트가 바꿀 수 없어서, 그대로 추가하면 Safari와 쿠키가 분리된다(2026-10-06 iOS 26.5 시뮬레이터 확인). 사용자 결정으로 아래 "홈 화면 세션 잇기"를 둔다. manifest `display`는 Android 설치 버튼을 위해 `standalone`을 유지한다. iOS는 공유 메뉴에서 직접 추가해야 하므로 1번 클릭 설치는 불가하고 안내로 제공한다.
- 홈 화면 세션 잇기(2026-10-06 사용자 결정): 홈 화면 앱이 처음 열릴 때 1회용 코드로 Safari와 같은 게스트 세션 ID 쿠키를 받는다. 이후 두 곳은 같은 세션을 쓰며, 한쪽에서 세션을 지우면 다른 쪽도 끊긴다.
  - 발급: 웹 manifest는 요청마다 만든다. 게스트 쿠키가 있고 저장한 명함이 1개 이상이면 Next가 `POST /v1/guest/handoffs`(게스트 프록시)로 코드를 받아 `start_url`을 `/saved?handoff=<code>`로 넣는다. 아니면 `/saved`. manifest 응답은 `Cache-Control: private, no-store`와 `Vary: Cookie`이다.
  - API `POST /v1/guest/handoffs`: 기존 토큰 필요, 201 `{code, expiresAt}`. 코드는 32바이트 이상 무작위 base64url, 수명 10분, 1회용. 세션당 유효 코드는 1개이며 새로 발급하면 이전 코드는 무효다. DB에는 코드의 SHA-256 digest·세션·만료·사용 시각과, 발급 요청의 게스트 토큰을 코드에서 파생한 키(HKDF)로 AES-256-GCM 암호화한 값만 둔다. DB만으로는 토큰을 복원할 수 없고, 사용·만료 시 암호문을 지운다.
  - API `POST /v1/guest/handoffs/redeem`: body `{code}`, 게스트 프록시, 토큰 없이 호출. 200 `{guestToken}`(코드로 복호화한, Safari와 같은 토큰). 없음·만료·사용됨·세션 폐기는 모두 404 `NOT_FOUND`(구분하지 않음). 성공하면 즉시 사용 처리한다. 요청 제한은 게스트 카운터와 같고, 실패도 센다.
  - 웹 `/saved?handoff=<code>`: 게스트 쿠키가 없을 때만 교환한다. 성공하면 쿠키를 설정하고, 성공·실패 모두 303으로 쿼리 없는 `/saved`로 보낸다. 이미 쿠키가 있으면 교환하지 않고 바로 보낸다. 페이지는 `Referrer-Policy: no-referrer`를 쓰고, 서버 로그에서 `handoff` 쿼리를 가린다.
  - 위험: 코드는 유효 시간 동안 게스트 세션 전체(저장한 명함과 공유 기록)를 넘겨받는 열쇠다. 다른 기기에서의 사용을 막을 수 없다. 1회용·10분·digest 저장·요청 제한·manifest 캐시 금지로 줄인다. 마이그레이션은 운영 DB 변경이므로 별도 PR로 사용자 승인 뒤 병합한다.
- 앱을 설치한 사람은 같은 `/s/` URL이 유니버설 링크·App Links로 앱에서 열린다. 앱에서 받은 명함 저장은 로그인 계정의 wallet에 한다. 앱의 게스트 세션·게스트 API는 만들지 않는다.
- 2026-10-03 이전 기기에 남은 앱 저장 데이터는 읽거나 지우거나 이관하지 않는다.

### 연결 설정 — 도메인 기반, 환경값으로 지정

2026-10-06 사용자 결정: 모든 연결은 도메인 기반이며 환경값으로 지정한다. 코드에 운영 도메인·IP·포트를 고정하지 않는다.

| 환경값 | 쓰는 곳 | 의미 |
| --- | --- | --- |
| `DEARBY_API_ORIGIN` | 웹 서버, iOS·Android 빌드 | API origin(scheme+도메인, `/v1` 제외). 예: `https://wid.io.kr` |
| `DEARBY_WEB_ORIGIN` | iOS·Android 빌드 | 공유 URL `/s/<shareId>`를 만들고 받는 웹 origin. 유니버설 링크·App Links 도메인도 이 값의 host에서 만든다. 예: `https://dearby.wid.io.kr` |
| `DEARBY_APPLE_TEAM_ID`, `DEARBY_IOS_BUNDLE_IDS`, `DEARBY_ANDROID_PACKAGE`, `DEARBY_ANDROID_CERT_SHA256` | 웹 서버 | `/.well-known` 연결 파일(#96) |

- 운영·배포 빌드는 `https`와 도메인 host만 허용한다. IP 주소 host, `http`, 포트 지정, 경로가 있는 값은 빌드 또는 시작 시 실패시킨다. 개발 구성만 `http://localhost`·`127.0.0.1`·Android 에뮬레이터 `10.0.2.2`를 허용한다.
- iOS: 빌드 환경값을 Info.plist 키(`DearbyAPIOrigin`, `DearbyWebOrigin`)로 넣고, Associated Domains는 `applinks:$(DEARBY_WEB_HOST)`로 만든다. 값이 없으면 Release 빌드는 실패하고, Debug는 로컬 개발 기본값을 쓴다.
- Android: Gradle이 같은 환경값(또는 같은 이름의 Gradle 속성)을 읽어 `BuildConfig`와 manifest placeholder(App Links host)로 넣는다. 값이 없으면 release 빌드는 실패한다.
- 웹은 요청이 들어온 자기 origin을 기준으로 동작하므로 웹 도메인 환경값이 따로 필요 없다. API 서버의 `HOST`·`PORT`는 내부 바인딩이며, 외부에서는 ingress 도메인으로만 접근한다.

### 앱에서 받은 공유 저장 (회원 wallet) — 2026-10-06

앱 설치자는 스캔·유니버설 링크로 연 공유 명함을 로그인 계정의 wallet에 저장한다. 웹 게스트의 공유 저장(`PUT /v1/guest/shares/:id`)과 같은 의미를 회원 경로로 둔다.

| 메서드·경로 | 인증 | 성공 응답 | 의미 |
| --- | --- | --- | --- |
| PUT /v1/wallet/shares/:id | 로그인 | 첫 저장 201 `{receiptId, cardId, shareId, status:"saved"}`, 이후 200 `{…, status:"saved"\|"alreadySaved"}` | 공유를 확인하고 그 명함을 wallet에 저장하며 공유 기록을 연결한다. 명함이 이미 있고 공유가 새로우면 `saved`, 둘 다 있으면 `alreadySaved`. 자기 명함의 공유는 422, 없거나 철회되면 404 |
| GET /v1/wallet | 로그인 | 200 `{items: Receipt[], shares: WalletShare[]}` | 기존 `items`는 그대로 두고 `shares`를 추가한다 |

- WalletShare: `{receiptId, cardId, shareId, activities: [{id, title}], savedAt}`. 철회된 명함은 `items`와 함께 제외한다.
- 한 명함(Receipt)에 공유 기록을 0개 이상 연결하고, 명함당 20개까지 둔다(웹 게스트와 같다). 동일 사용자+명함은 Receipt 하나다.
- `reciprocal`은 이 저장만으로 true가 되지 않는다(기존 규칙 유지).
- 받은 명함함의 활동별 묶음은 `shares[].activities`로 만든다. 활동이 없는 명함은 `활동 없음` 묶음이다.
- HTTPS ingress에는 `PUT /v1/wallet/shares/:id`를 회원 경로로 추가한다(Authorization만 전달). 마이그레이션은 운영 DB 변경이므로 사용자 승인 뒤 병합한다.

### 신청 확인과 기기 캘린더 겹침 — 2026-10-06 사용자 결정

- **신청 확인:** 활동 상세나 빠른 신청에서 공식 신청 URL을 외부 브라우저로 연 뒤, 앱이 처음 다시 활성화되면 그 활동에 대해 한 번 "신청하셨나요?"를 묻는다. 선택지는 `신청했어요`(내 활동에 신청함으로 표시) / `아직이에요`(표시하지 않음, 다음에 신청 URL을 열면 다시 물음) / `다시 묻지 않기`(이 활동은 묻지 않음)다. 신청 URL을 열지 않았거나 이미 신청함인 활동은 묻지 않는다. 신청 표시는 사용자 직접 기록이며 주최 측 접수 확인이 아니다. 저장은 기존처럼 기기 메모리에 둔다.
- **기기 캘린더 겹침:** 활동 상세의 `겹치는 시간 확인`을 누를 때만 캘린더 읽기 권한을 요청한다(iOS EventKit 전체 접근 `requestFullAccessToEvents`, Android `READ_CALENDAR`). 활동 일정(`schedules`의 시작·끝·`timeZone`)과 기기 일정의 겹침을 기기 안에서만 계산해 보여 준다. 일정 제목·시각은 서버로 보내거나 앱에 저장하지 않으며, 화면을 떠나면 버린다. 권한을 거부하면 이유와 설정 열기를 안내하고 신청 흐름은 막지 않는다. 시작·끝 시각이 없는 활동은 버튼을 숨긴다. 캘린더에 쓰기(일정 추가)는 하지 않는다.

### 예시로 남는 것

참여 확정 표시(사용자 직접 표시이며 주최 측 확인이 아님), 명함 서버 간 직접 전달(POST /v1/exchanges)은 이번 범위가 아니다. 예시 화면은 실제 동작으로 표현하지 않는다.

### 운영 의존

운영 경로 공개 #88, 인증 메일 #89, 연결 파일 값 #90, 마이그레이션·배포 승인 #91, 외부 도달성 #65. 해소 전에는 로컬 API와 테스트 데이터로 검증하며 운영 동작 완료로 표시하지 않는다.

## 패스키 로그인 — 2026-10-10 사용자 결정

계정은 패스키로만 만들고 로그인한다. 이메일 인증번호 로그인은 없앤다. 처음 패스키를 만들 때 계정도 함께 생기고, 이후에는 그 패스키로 로그인한다. 기기를 잃으면 OS의 패스키 동기화(iCloud 키체인·Google 비밀번호 관리자)에 의존하며, 별도 계정 복구 경로는 없다.

### 신뢰 당사자와 연결

- RP ID는 `wid.io.kr`(API `WEBAUTHN_RP_ID`). 바꾸면 등록된 패스키를 모두 쓸 수 없다.
- `https://wid.io.kr/.well-known/apple-app-site-association`에 `webcredentials.apps`(`<TeamID>.<bundle ID>`)를, `https://wid.io.kr/.well-known/assetlinks.json`에 `delegate_permission/common.get_login_creds`(패키지·서명 SHA-256)를 둔다. `wid.io.kr`은 웹(Vercel)이 아니라 운영 ingress(nginx)가 내준다. 유니버설 링크 `/s/*`의 `applinks`는 기존대로 웹 도메인에 둔다.
- iOS entitlements에 `webcredentials:wid.io.kr`, Android는 Credential Manager를 쓴다.
- API 허용 origin(`WEBAUTHN_ALLOWED_ORIGINS`)에 iOS의 `https://wid.io.kr`과 Android의 `android:apk-key-hash:<서명 SHA-256의 base64url>`을 넣는다. 웹에서 패스키를 쓰면 웹 origin도 넣는다.

### API

브라우저 세션 쿠키에 기대지 않는다. 옵션 요청이 `challengeId`를 돌려주고, 완료 요청이 그 값을 다시 보낸다. challenge는 한 번만 쓸 수 있고 5분 뒤 만료된다. `options`와 `credential`은 WebAuthn 표준 JSON 형식(`PublicKeyCredentialCreationOptionsJSON`·`RequestOptionsJSON`, `RegistrationResponseJSON`·`AuthenticationResponseJSON`, base64url)이다.

| 메서드·경로 | 인증 | 요청 | 성공 응답 |
| --- | --- | --- | --- |
| POST /v1/auth/passkeys/registration/options | 없음 | `{displayName?}` | 200 `{challengeId, options}` |
| POST /v1/auth/passkeys/registration | 없음 | `{challengeId, credential}` | 201 TokenPair. 이때 계정이 생긴다 |
| POST /v1/auth/passkeys/authentication/options | 없음 | 없음 | 200 `{challengeId, options}` (`allowCredentials`는 비움, 기기에 있는 패스키 중 고름) |
| POST /v1/auth/passkeys/authentication | 없음 | `{challengeId, credential}` | 200 TokenPair |
| POST /v1/auth/refresh | 없음 | `{refreshToken}` | 200 TokenPair. 쓴 refresh token은 폐기된다(회전) |
| POST /v1/auth/logout | 없음 | `{refreshToken}` | 204. 이미 무효여도 204 |

- TokenPair: `{accessToken, refreshToken, user: {id, roles}}`(2026-10-15 사용자 결정으로 user 추가, 이전 10-10 결정은 토큰만). `user`는 클라이언트가 화면 권한 등에 쓰는 값이고 서버는 토큰만 믿는다. 역할이 바뀌면 다음 refresh 응답에 반영된다. access token은 API가 RS256으로 서명한 JWT이고, 사용자 id는 `sub`, 역할은 `roles`, 만료는 `exp` 클레임에 있다. refresh는 받은 refresh token을 폐기하고 새 access·refresh token을 준다.
- 실패: challenge 없음·만료·재사용, 서명 검증 실패, 모르는 패스키는 401. 형식이 틀린 요청은 400. 오류 본문은 RFC 9457 ProblemDetail이고 `detail`이 사용자에게 보여도 되는 문장이다(2026-10-11 통일). 앱은 상태 코드로 분기하고 `detail`은 안내 문구로만 쓴다.
- `displayName`은 OS 패스키 목록에 계정 이름으로 보이는 이름이다(패스키의 `user.name`·`user.displayName` 둘 다 이 값). 없으면 `Dearby`(2026-10-15 사용자 결정, 이전에는 `user.name`이 내부 UUID라 목록에 UUID가 보였다). 같은 이름을 여러 계정이 써도 된다. 패스키의 `user.id`(user handle)는 서버가 정하는 사용자 UUID 16바이트이고, 로그인할 때 이 값으로 계정을 찾는다. 앱은 정하지 않는다.

### 앱 동작

- 처음 실행할 때 한 번만(2026-10-14 사용자 결정) 로고와 "패스키로 시작하기"·"둘러보기"가 있는 시작 화면을 보여 준다. 로그인은 선택이며, 로그인에 성공하거나 둘러보기를 누르면 다시 보이지 않는다. 이미 로그인되어 있거나 공유 링크로 처음 열리면 건너뛴다. 매 실행의 OS 스플래시에는 로고만 둔다.
- 로그인이 필요한 동작(명함 발행·공유, 받은 명함 저장)에서 로그인 화면을 띄운다. "패스키로 로그인"이 기본이고, 패스키가 없거나 사용자가 고르면 "새 패스키로 시작"(가입)을 한다.
- access token이 만료돼 401을 받으면 refresh를 한 번 시도한다. refresh가 401로 거절되면 토큰을 지우고 로그인 화면으로 돌아간다. 네트워크 오류·5xx면 토큰을 남기고 그 요청만 실패시킨다(불안정한 연결에서 로그아웃되지 않게). refresh token이 회전하므로 동시에 여러 요청이 401을 받아도 refresh는 한 번만 한다.
- 로그아웃은 `/v1/auth/logout` 뒤 기기 토큰을 지운다. 네트워크 실패여도 기기 토큰은 지운다.
- 기존 이메일 로그인 화면·저장 토큰(`sessionToken`)은 쓰지 않는다. 이전 앱 저장 데이터는 읽거나 지우지 않는다는 저장소 원칙을 따른다.

## 프로필·명함 v2 — 2026-10-16 사용자 결정

TypeScript API를 지운 뒤 비어 있던 프로필·명함·공유·지갑 경로를 Kotlin `api`가 다시 제공한다. 위 "모델"·"첫 수직 구현 API"의 Profile·Card 설명은 이 섹션이 대체한다. 오류는 ProblemDetail(400·401·403·404·409)이며 옛 422는 400이 됐다. id는 모두 UUID 문자열이고(조직·프로그램·활동·역할만 숫자), 연락처·이력 id는 클라이언트가 만든 UUID를 서버가 그대로 둔다.

### 프로필과 버전 이력

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| GET /v1/profile | Bearer | — | 200 Profile. 아직 없으면 빈 프로필(`version` 0) |
| PUT /v1/profile | Bearer, `If-Match: <version>` 선택 | `{name, job, introduction, contacts[], histories[]}` | 200 Profile(`version`+1). `If-Match`가 현재 버전과 다르면 409 |
| GET /v1/profile/versions | Bearer | `cursor?`, `size?` | 200 `{items: [{version, createdAt}], nextCursor}` 최신순 |
| GET /v1/profile/versions/:n | Bearer | — | 200 `{version, createdAt, profile}` 그때의 프로필 |
| POST /v1/profile/versions/:n/restore | Bearer | — | 200 Profile. 그 버전 내용으로 새 버전을 만든다(이력은 지우지 않는다) |

- Profile: `{id(=사용자 id), name, job, introduction, contacts: Contact[], histories: History[], version, updatedAt}`.
- 프로필은 **데이터 입력**만 한다(2026-10-16 사용자 결정). `introduction`은 자기소개 글이고, 꾸미기(마크다운 템플릿)는 명함 제작에서 한다(아래 "템플릿").
- 저장할 때마다 전체 프로필 스냅샷을 버전으로 남긴다(노션처럼 이전 상태를 보고 되돌린다). 같은 내용이면 저장하지 않고 현재 프로필을 돌려준다.
- 연락처 규칙은 그대로다: phone·email 각각 하나 이상, phone은 숫자·`+`·`-`·공백만(숫자 8~15자리), email 기본 형식, 값에 제어 문자 금지, URL은 https만. 어기면 400.

### 이력 (History) — 단계별 입력

사용자는 토스 퍼널처럼 단계별로 고른다: **조직인지 활동인지 → 어떤 조직 → 어떤 프로그램 → (활동이면) 어떤 활동 → 역할 → 기간**. 조직·프로그램·활동·역할은 등록된 활동 정보에서 검색해 고르고, 없으면 직접 입력한다.

- History: `{id, kind: "organization"|"activity", organization: Ref|null, organizationName, program: Ref|null, programName, activity: Ref|null, activityName, role: Ref|null, roleName, startDate, endDate, description, approval, approvalReason, verification, evidence: Evidence[]}`
  - Ref는 `{id(숫자), name}`. 등록된 활동 정보에서 고르면 Ref와 이름이 함께 오고, 직접 입력이면 Ref가 null이고 `*Name`만 있다. 요청에서는 `organizationId`·`programId`·`activityId`·`roleId`(숫자, 선택)와 `organizationName`·`programName`·`activityName`·`roleName`(직접 입력, 선택)을 보낸다. 둘 다 보내면 id를 쓴다.
  - `kind=organization`은 조직(선택으로 프로그램)과 역할, `kind=activity`는 활동(소속 프로그램·조직 포함)과 역할. 조직 또는 조직 이름은 필수, 역할 또는 역할 이름은 필수. `startDate` 필수, `endDate`는 진행 중이면 null, 시작보다 앞설 수 없다.
  - `approval`: `approved` | `pending` | `rejected`. 직접 입력(`*Name`만 있는 값)이 하나라도 있으면 `pending`으로 저장되고 **관리자가 승인해야 명함에 넣을 수 있다**. 등록된 활동 정보 값만 쓰면 바로 `approved`. 승인된 뒤 직접 입력 값을 바꾸면 다시 `pending`. 거절되면 `approvalReason`에 사유가 온다.
  - `verification`: `unverified` | `pending` | `verified` | `rejected`. 인증자료를 하나라도 올리면 `pending`, 관리자가 확인하면 `verified`. 앱은 `verified`가 아니면 **미확인 배지**를 붙인다. 미인증이어도 명함에 넣을 수 있다.
- 검색(로그인, 앱 선택 창용): `GET /v1/organizations?q&parentId&rootsOnly&cursor&size`, `GET /v1/programs?q&organizationId&cursor&size`, `GET /v1/roles?q&cursor&size`, 활동은 기존 `GET /v1/activities`. 응답은 admin과 같은 keyset 페이지(`{items, nextCursor}`), 항목은 `{id, name, nameEn?}`(조직은 `parentOrganizationId`, 프로그램은 `organization` 포함).

### 인증자료 (Evidence)

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| POST /v1/profile/histories/:id/evidence | Bearer | multipart `file` | 201 Evidence. 이력당 3개, 파일당 5MB, `image/jpeg`·`image/png`·`image/webp`·`application/pdf`만 |
| GET /v1/profile/histories/:id/evidence/:eid | Bearer(소유자) | — | 200 파일 |
| DELETE /v1/profile/histories/:id/evidence/:eid | Bearer | — | 204 |

- Evidence: `{id, fileName, contentType, size, uploadedAt}`. 파일은 DB에 둔다(bytea). 명함·공유에는 실리지 않는다.
- 올리면 `verification`이 `pending`이 된다(이미 `verified`면 유지). 건너뛰고 나중에 올릴 수 있다. 다 지워도 `verified`는 유지된다.

### 명함·공유·지갑

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| GET /v1/cards | Bearer | — | 200 `{items: Card[]}` 철회 안 한 내 명함, 만든 순 |
| POST /v1/cards | Bearer | `{name, description, template, contactIds[], historyIds[]}` | 201 Card. 내 프로필의 id가 아니거나 `approval`이 `approved`가 아닌 이력이면 400 |
| GET /v1/cards/:id | 없음 | — | 200 Card. 없거나 철회면 404 |
| DELETE /v1/cards/:id | Bearer(소유자) | — | 204 철회. 남의 것 403 |
| POST /v1/cards/:id/shares | Bearer(소유자) | `{activityIds: number[]}` 0~10개, 게시된 활동만 | 201 CardShare |
| GET /v1/shares/:id | 없음 | — | 200 `{share: CardShare, card: Card}`. 철회된 명함의 공유도 404 |
| GET /v1/wallet | Bearer | — | 200 `{items: Receipt[], shares: WalletShare[]}` |
| PUT /v1/wallet/shares/:id | Bearer | — | 첫 저장 201, 이후 200 `{receiptId, cardId, shareId, status: "saved"\|"alreadySaved"}`. 자기 명함 400, 명함당 공유 20개 넘으면 409 |

- Card: `{id, ownerId, name, description, template, introduction, profileName, job, contacts: Contact[], histories: History[], createdAt}`. 발행 시점 스냅샷이며 프로필을 고쳐도 바뀌지 않는다. `template`은 발행할 때 보낸 마크다운 템플릿, `introduction`은 그것을 **채운 결과**(공개 본문)다. 이력에는 `evidence`가 없고 `verification`만 남는다. 공개 조회(`GET /v1/cards/:id`, `GET /v1/shares/:id`)에는 `template`을 싣지 않는다.
- CardShare: `{id, cardId, activities: [{id(숫자), title}], createdAt}`. **활동 id가 UUID에서 숫자로 바뀌었다**(2026-10-14 활동 조회 결정). 앱·웹의 ShareActivity를 맞춘다.
- Receipt: `{id, card, context: {activityId: null, label: null}, receivedAt, reciprocal: false}`. WalletShare는 "앱에서 받은 공유 저장"과 같다.
- 아직 없는 것(4단계): 웹 비로그인 저장(`/v1/guest/*`, 홈 화면 세션 잇기), `POST /v1/wallet/import`, `POST /v1/exchanges`. 웹 `/s/` 페이지는 명함을 보여 주되 저장은 그때까지 실패(`REQUEST_FAILED`)한다.

### 템플릿 (명함 꾸미기)

- 꾸미기는 **명함 제작**에서 시작한다. 앱은 명함 제작 화면에서 프로필 데이터를 받아 템플릿을 채워 렌더링한 화면을 보여 주고, 그 화면에서 바로 고친다. 발행하면 `template`과 채운 결과가 명함에 남는다.
- 토큰: `{{profile.name}}`, `{{profile.job}}`, `{{profile.introduction}}`, `{{contact:<연락처 id>}}`. 앱 선택 창(프로필 데이터)에서 고르면 토큰이 들어가고, 편집기에서는 고칠 수 없는 칩으로 보인다.
- 서버는 **발행할 때** 채운다. `{{contact:…}}`는 그 명함에 고른 연락처만 채우고, 고르지 않았거나 없는 연락처·모르는 토큰은 빈 문자열이 된다(원문 토큰을 남기지 않는다). 템플릿에만 쓴 연락처는 공개되지 않는다.
- 발행한 명함은 바뀌지 않는다. 명함을 고치면 앱이 그 `template`을 미리 채운 새 명함을 발행하고, 이전 명함은 사용자가 지울 때까지 남는다(공유 링크 유지).
- 마크다운 부분집합: 제목(#~###), 굵게, 기울임, 목록, 번호 목록, 링크, 줄바꿈. 원시 HTML은 허용하지 않고 글자로 보여 준다. 링크는 `https`·`mailto`·`tel`만. 서버는 `template`·`introduction` 10,000자, 제어 문자(줄바꿈·탭 제외)를 막는다.
- 앱 편집: 명함 제작 화면에서 내용이 바뀌면 수정 모드로 들어가 "저장"(발행)·"원래대로"(기기 안에서 되돌림)를 보여 준다. 프로필 데이터 화면도 같은 방식이며, 프로필의 서버 버전 되돌리기는 위 `restore`다.
- 새 명함의 템플릿 기본값은 앱에 내장한다(이름·직함·소개·연락처 토큰이 들어간 기본 템플릿).

### 관리자 (admin-v1.md에도 기록)

- `GET /v1/admin/histories?approval=pending|rejected|approved&verification=pending|…&cursor&size`: 검토할 이력. 항목은 History에 `profileName`(작성자 프로필 이름)을 더한 것. 계정 id는 싣지 않는다.
- `POST /v1/admin/histories/:id/approval` `{decision: "approve"|"reject", organizationId?, programId?, activityId?, roleId?, reason?}`: 승인 때 id를 주면 직접 입력 값을 그 활동 정보 항목으로 바꾼다(없는 조직·프로그램·역할은 기존 admin API로 먼저 만든다). 거절은 `reason`을 남긴다.
- `POST /v1/admin/histories/:id/verification` `{decision: "verify"|"reject"}`.
- `GET /v1/admin/histories/:id/evidence/:eid`: 파일.
- 역할 목록: `GET /v1/admin/roles?q`, `POST /v1/admin/roles {name, nameEn?}`, `PATCH /v1/admin/roles/:id`, `DELETE /v1/admin/roles/:id`(이력이 쓰면 400). 이름은 띄어쓰기를 뺀 값으로 유일.

## 애플 로그인 — 2026-10-16 사용자 결정

패스키와 함께 쓰는 두 번째 로그인 수단이다. iOS·Android·웹에서 같은 애플 계정은 같은 Dearby 계정이 된다. 애플의 `sub`는 같은 팀의 앱 ID(`io.wid.dearby`)와 Services ID에서 같다. 계정은 이메일로 합치지 않는다(가린 이메일 `@privaterelay.appleid.com`이 올 수 있다). 운영에 `APPLE_CLIENT_IDS`가 없으면 아래 `/auth/apple/*`는 503이다.

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| POST /v1/auth/apple/nonce | 없음 | — | 200 `{challengeId, nonce}`. nonce는 10분, 한 번만 |
| POST /v1/auth/apple | 없음 | `{challengeId, identityToken, displayName?, authorizationCode?}` | 200 TokenPair(이미 연결된 애플 계정) 또는 202 `{linkToken, email}`(처음 보는 계정). 검증 실패 401 |
| POST /v1/auth/apple/register | 없음 | `{linkToken}` | 201 TokenPair. 새 Dearby 계정(USER)을 만들고 애플 계정을 연결한다 |
| POST /v1/me/identities/apple | Bearer | `{linkToken}` | 204. 로그인한 계정(패스키 계정)에 애플 계정을 연결한다. 이미 다른 애플 계정이 연결돼 있으면 400 |
| POST /v1/auth/apple/web/start | 없음 | `{verifierHash}` | 200 `{authorizeUrl}`. 웹 방식(Android) |
| POST /v1/auth/apple/callback | 없음 | 애플 form_post | 303 `dearby://auth/apple?code=<1회용>` 또는 `?error=cancelled\|failed` |
| POST /v1/auth/apple/web/exchange | 없음 | `{code, verifier}` | `POST /v1/auth/apple`과 같은 200·202. 401이면 처음부터 다시 |

- 처음 보는 애플 계정(202): 앱은 "새 계정으로 시작"과 "기존 패스키 계정에 연결"을 보여 준다. 서버는 그 사람에게 패스키 계정이 있는지 알 수 없어 사용자가 고른다.
  - 새 계정: `register`.
  - 연결: 패스키로 로그인한 뒤 그 access token으로 `/me/identities/apple`을 부른다. 연결 후 애플 로그인은 그 패스키 계정으로 바로 로그인된다(200).
  - `linkToken`은 10분, 한 번만 쓴다.
- identity token 검증
  - 애플 공개키 서명, `iss = https://appleid.apple.com`, `aud`가 `APPLE_CLIENT_IDS` 중 하나, 만료를 확인한다.
  - `nonce` 클레임이 발급한 nonce와 같아야 한다. 클라이언트는 받은 nonce를 해시하지 않고 그대로 인가 요청에 넣는다.
- 이름
  - 애플은 이름을 첫 인가 때만 준다.
  - iOS는 받은 `fullName`을 `displayName`으로 보낸다. 웹 방식은 콜백의 `user`에서 서버가 읽는다.
  - `user_identities.display_name`에 남겨 프로필을 채울 때 쓸 수 있게 한다. 패스키 목록 이름(`Dearby`)과는 관계없다.
- iOS: `AuthenticationServices`로 `ASAuthorizationAppleIDRequest`(scope `fullName`·`email`, `nonce`)를 보내고 `identityToken`을 `POST /v1/auth/apple`로 보낸다. 앱 ID에 Sign in with Apple 기능과 entitlement `com.apple.developer.applesignin`이 필요하다.
- Android(웹 방식)
  1. 무작위 verifier(43자 이상)를 만들고 SHA-256(base64url, 패딩 없음)을 `web/start`로 보낸다.
  2. 받은 `authorizeUrl`을 Custom Tab으로 연다.
  3. 애플은 Services ID의 Return URL(`https://api.dearby.wid.io.kr/v1/auth/apple/callback`)로 form_post한다. 서버는 토큰을 확인하고 `dearby://auth/apple?code=…`로 돌려보낸다.
  4. 앱은 그 code와 verifier로 `web/exchange`를 부른다.
  - 결과 code는 10분, 한 번만 쓴다. 주소에 토큰을 싣지 않고, verifier가 없는 다른 앱은 code를 바꿔 갈 수 없다.
- 웹: 사용자 웹은 아직 로그인해서 쓰는 기능이 없다(명함 제작은 앱). 웹 애플 로그인은 웹에 로그인 기능이 생길 때 Apple JS(Services ID, popup)로 `POST /v1/auth/apple`을 쓴다.
- 애플 토큰
  - iOS는 credential의 `authorizationCode`를 함께 보낸다. 웹 방식은 콜백의 `code`를 서버가 쓴다.
  - 서버는 `.p8` 키로 서명한 client secret으로 code를 애플 refresh token으로 바꿔 `user_identities`에 둔다. 아래 "계정 삭제" 때 폐기하기 위해서다.
  - 키가 설정되지 않았거나 교환이 실패해도 로그인은 막지 않는다. 다시 로그인하면 새 토큰으로 바꾼다.

## 계정 삭제 — 2026-10-16 사용자 결정

App Store 심사 기준 5.1.1(v)에 따라 앱 안에서 계정을 지울 수 있어야 한다.

| 메서드·경로 | 인증 | 응답 |
| --- | --- | --- |
| DELETE /v1/me | Bearer | 204. 없는 토큰·무효 토큰은 401 |

- 되돌릴 수 없다. 앱은 내 프로필 탭 맨 아래 "계정 삭제"에서 확인 대화상자("계정을 삭제할까요?" / "명함과 받은 명함 등 모든 정보가 지워지고 되돌릴 수 없어요.")를 거쳐 부른다. 성공하면 저장한 토큰과 메모리의 계정 데이터를 지우고 로그아웃 상태로 돌아간다. 401 뒤 refresh도 거절되면 로그아웃된 것으로 본다.
- 지우는 것
  - 사용자, 역할, 연락처, 프로필, 명함과 명함 하위 행, 활동 이력, 참여 기록, 기관 멤버·프로그램 스태프 행
  - refresh token. 이미 발급한 access token은 수명(1분)이 지나면 끝난다.
  - 패스키 user entity와 credential. 기기에 남은 패스키는 앱이 지울 수 없어 안내만 한다(iOS "설정 > 암호", Android "비밀번호 관리자").
  - 외부 로그인 연결
- 애플 계정이 연결돼 있으면 저장한 애플 refresh token을 먼저 폐기한다(`appleid.apple.com/auth/revoke`). 토큰이 없거나 폐기가 실패해도 삭제는 진행하고 서버 로그에 남긴다.

## 활동 조회 — 2026-10-14 사용자 결정

`GET /v1/catalog`(전체 카탈로그 한 번에)를 없애고 `GET /v1/activities`로 바꾼다. 카탈로그 원본은 Kotlin DB다(Supabase는 쓰지 않음). 앱·웹은 아직 `/v1/catalog`를 부르므로 이 계약에 맞춰 바꿔야 한다.

| 메서드·경로 | 인증 | 요청 | 성공 응답 |
| --- | --- | --- | --- |
| GET /v1/activities | 없음 | query `q?`, `organizationId?`, `programId?`, `cursor?`, `size?`(1–100, 기본 20) | 200 `{items: Activity[], nextCursor}` |
| GET /v1/activities/:id | 없음 | 숫자 id | 200 Activity(목록 항목과 같은 형태). 없거나 소속 프로그램·기관이 없으면 404, 숫자가 아니면 400 |

- 게시된(`PUBLISHED`) 활동만 보인다. 수집 배치가 넣은 초안(`DRAFT`)은 관리자가 게시하기 전까지 목록·검색·단건 조회에 나오지 않는다(2026-10-15).
- 순서는 활동 id 오름차순이다. 다음 페이지는 `nextCursor`를 `cursor`로 보낸다. `nextCursor`가 null이면 마지막 페이지다. `size`가 범위를 벗어나면 400.
- 언어(2026-10-15 사용자 결정): 요청 헤더 `Accept-Language`에서 지원 언어(`ko`, `en`) 중 가장 선호하는 것으로 `name`·`description`·`organization.name`·`program.name`을 하나씩 준다. 영문 값이 없는 항목은 한글로 채운다. 헤더가 없거나 읽을 수 없거나 지원 언어가 없으면 한국어. 응답에 `Content-Language`(`ko`·`en`)와 `Vary: Accept-Language`를 붙인다. 앱은 기기 언어 순서(iOS `Locale.preferredLanguages`, Android `LocaleList`, 최대 5개, 순서대로 q 값)를, 웹 프록시는 브라우저의 `Accept-Language`를 활동 조회에만 그대로 보낸다(언어 목록 형식이 아니면 버린다). 앱 화면 자체는 아직 한국어다.
- `q`는 활동 이름·설명(한글·영문 모두)에서 찾는다. 대소문자 없는 부분 일치(`%`·`_`도 글자 그대로) 또는 단어 유사도(PostgreSQL `pg_trgm` `word_similarity` 0.4 이상)면 결과에 넣는다. 그래서 오타("워크샵"→"워크숍")·띄어쓰기 차이("개발환경")도 찾는다. 순서는 여전히 id 순이다(유사도 순 정렬은 하지 않음).
- id는 숫자(bigint)다. 기존 카탈로그 UUID는 쓰지 않는다.
- Activity: `{id, name, description, organization{id,name}, program{id,name}, officialUrl, imageUrl, application, activitySchedule, schedules[]}`. 2026-10-14 사용자 결정으로 원래 도메인 필드에 공식 안내 링크(`officialUrl`)와 이미지(`imageUrl`)만 더한다. 모집 상태·참여 방식·확인 시각·장소·비용·대상·자격요건(JSON)·역할·신청 링크·안내 문구는 같은 `activities` 행(+`activity_roles`)에 있지만 운영자용 도메인 `ActivityCuration`이 다루고 공개 API는 내보내지 않는다. 같은 DB를 쓰는 운영자(admin) 모듈용이다.
  - 모집 여부는 `application`(모집 기간: 시작·끝 중 아는 쪽)으로 판단한다. 신청은 `officialUrl`을 연다.
  - 한 항목만으로 목록·상세를 그릴 수 있어 기관·프로그램을 따로 조회하지 않는다.
  - `application`(모집 기간, 없으면 null), `activitySchedule`(활동 기간·날짜 문구), `schedules[]`(개별 일정)는 Schedule이다.
- Schedule: `{id, title, kind, status, startAt, endAt, timeZone, startDate, endDateExclusive, dateLabel, address, conferenceUrl}`. 시각 일정은 `startAt`·`endAt` 중 하나 이상과 `timeZone`이 있고, 시각은 그 시간대 오프셋으로 표기한다(예: `2026-10-08T00:00:00+09:00`). 종일 일정은 `startDate`·`endDateExclusive`. 날짜 미정은 모두 null이고 `dateLabel`만 있다.
