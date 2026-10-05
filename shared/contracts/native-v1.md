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
| 발견·활동 상세 | 공개 카탈로그를 보여 주고, 신청 CTA는 활동의 공식 신청 URL을 외부 브라우저로 연다. 실패하면 오류와 다시 시도를 보여 주며 예시 활동을 섞지 않는다. | GET /v1/catalog |
| 로그인 | 이메일 인증번호. 명함 발행·공유를 시작할 때만 요구하고, 발견·신청·받은 명함 보기는 로그인 없이 쓴다. 토큰은 OS 보안 저장소에 둔다. | /v1/auth/* |
| 명함 제작 | 프로필 입력과 공개할 연락처·이력 선택을 한 흐름으로 마치고 바로 발행한다. 수정은 새 발행본을 만든다(기존 스냅샷 규칙 유지). | /v1/profile, POST /v1/cards |
| QR 공유 | QR 화면에 들어오면 현재 명함으로 공유를 만들고 `https://<웹 origin>/s/<shareId>`를 QR로 보여 준다. 활동 선택은 선택 사항이다. 공유 버튼은 같은 URL을 OS 공유 시트로 넘긴다. | POST /v1/cards/:id/shares |
| 받기 | 앱 안 스캐너(카메라·사진)와 유니버설 링크/App Links는 같은 `/s/<shareId>` URL을 해석해 공유 명함 화면을 연다. 기존 `dearby://card/<UUID>`도 계속 해석한다. 앱이 없거나 연결 검증에 실패하면 같은 URL이 웹 공유 명함으로 열린다. | GET /v1/shares/:id |

- 웹 origin은 앱 빌드 설정값이다(운영 `https://dearby.wid.io.kr`). 앱은 `https`, 설정 host, 경로 `/s/<UUID>`만 받고 query·fragment·다른 host를 거부한다.
- 유니버설 링크·App Links 경로는 `/s/*` 하나다. 웹은 `/.well-known/apple-app-site-association`, `/.well-known/assetlinks.json`을 환경값(Apple Team ID·번들 ID, Android 패키지·서명 SHA-256)으로 만든다. 값이 없으면 404다. 값 제공은 #90, 배포는 #91에 묶인다.

### 비로그인 사용자의 저장 유지

- 웹: 위 "웹 비로그인 명함 저장" 계약의 서버 게스트 세션(`__Host-dearby_guest` 쿠키)을 그대로 쓴다. 홈 화면에 추가한 웹 앱은 Safari와 저장소가 분리된다. iOS 17.2 이상은 추가 시점에 쿠키를 한 번 복사한다. 세션 병합·토큰 전달 기능은 만들지 않는다. 저장 성공 뒤 홈 화면 추가를 선택 안내로만 보여 주며, iOS는 사용자가 공유 메뉴에서 직접 추가해야 한다(1번 클릭 설치는 불가).
- 앱: 로그인 전 받은 명함(`GuestSavedCard`에 `shareId` 추가)과 내 활동 신청 표시를 기기 영구 저장소에 둔다. 앱을 다시 실행해도 유지된다. 로그인하면 받은 명함을 POST /v1/wallet/import로 옮기고, 성공 항목만 이관 완료로 표시한다. 웹 게스트 API는 프록시 secret이 필요하므로 앱이 직접 호출하지 않는다.
- 2026-10-03 이전 기기에 남은 앱 저장 데이터는 읽거나 지우거나 이관하지 않는다. 새 저장소는 별도 이름을 쓴다.

### 예시로 남는 것

캘린더 일정 겹침, 참여 확정 표시(사용자 직접 표시이며 주최 측 확인이 아님), 명함 서버 간 직접 전달(POST /v1/exchanges)은 이번 범위가 아니다. 예시 화면은 실제 동작으로 표현하지 않는다.

### 운영 의존

운영 경로 공개 #88, 인증 메일 #89, 연결 파일 값 #90, 마이그레이션·배포 승인 #91, 외부 도달성 #65. 해소 전에는 로컬 API와 테스트 데이터로 검증하며 운영 동작 완료로 표시하지 않는다.
