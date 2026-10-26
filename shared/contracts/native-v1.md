# Native API v1 — 구현 계약

2026-09-27. 사용자 제품 정본은 docs/product/native-spec-2026-09.md. 아래는 검증 가능한 첫 수직 구현을 위한 가역적 기술 계약이며 배포 완료를 의미하지 않는다. 변경은 조율 세션에 알리고 양 플랫폼·서버를 함께 맞춘다.

## 전송

JSON camelCase, UUID 문자열 식별자, UTC ISO8601 시각. 기본 `/v1`. 인증은 `Authorization: Bearer <accessToken>`(2026-10-10부터 패스키 로그인이 발급하는 JWT, 아래 "패스키 로그인"). HTTPS 운영 URL은 환경 설정이며 임의 실서비스 도메인을 만들지 않는다. 로컬 개발 HTTP 허용은 개발 구성에만 한정한다. 오류는 `{ "error": { "code": "...", "message": "..." } }`, 인증 오류 401, 권한 403, 없음/철회 404, 잘못된 입력 422, 충돌 409, rate limit 429. 민감값은 로그에 기록하지 않는다.

## 모델

- Profile: `id`, `name`, `job`, `introLink`(소개 링크; `introduction`은 2026-10-26 폐지 예정), `contacts: Contact[]`, `histories: History[]`, `updatedAt`.
- Contact: `id`, `kind`(phone/email/kakao/instagram/github/behance), `label`, `value`, `verified`, `verifiedAt`(아래 "연락처 인증", 2026-10-26; 서버가 인증 결과로만 켠다). github·instagram·behance·kakao의 value는 사용자명(ID)만이다("연락처 아이콘·바로가기" 절의 사용자명 입력, 2026-10-26; 그 전에 저장된 카카오톡 오픈채팅 URL은 표시·열기 유지). 외부 URL 열기는 허용 scheme 검증.
- 필수 연락처(2026-10-06 사용자 결정): 프로필은 `phone`과 `email`을 각각 하나 이상 가져야 한다. 나머지 종류는 사용자가 골라 추가한다. `PUT /profile`에서 빠지면 422 `INVALID_INPUT`(필드 `contacts`)이다. phone은 숫자·`+`·`-`·공백만 허용하고 숫자 8~15자리, email은 기본 형식을 검사한다. (2026-10-10 패스키 로그인 전환으로 로그인 이메일이 없어져, 앱이 email을 미리 채우던 규칙은 없앴다.) 명함 발행 때 공개할 연락처를 고르는 방식은 그대로이며, 필수는 프로필 기준이지 명함 공개 의무가 아니다.
- History: `id`, `title`, `role`, `startDate`(YYYY-MM-DD), `endDate`(nullable), `description`.
- 활동 이력 날짜 입력(2026-10-06 사용자 결정): 앱 프로필 편집에서 이력마다 시작일(필수)과 종료일(선택, `진행 중`이면 비움)을 날짜 선택기로 입력한다. 연·월만 고르는 경우 일은 1일로 저장한다. 종료일은 시작일보다 앞설 수 없다(API도 422). 표시는 `2026.03 – 2026.06`, 진행 중이면 `2026.03 – 진행 중`이다.
- Card: `id`, `ownerId`, `name`(명함 이름), `description`, `profileName`, `job`, `introLink`(`introduction`은 폐지 예정), `contacts: Contact[]`, `histories: History[]`, `createdAt`. 공개 연락처·이력만 포함. 원본 프로필 전체를 클라이언트에서 가리는 방식 금지.
- ExchangeContext: `{ activityId: string|null, label: string|null }`. 등록 활동, 자유 입력, 둘 다 null 중 하나. 활동 선택은 참가 인증이 아니다.
- Receipt: `id`, `card: Card`, `context: ExchangeContext`, `receivedAt`, `reciprocal: boolean`.
- GuestSavedCard (기기 전용): `cardId`, `context`, `savedAt`. 영구 원본은 ID 중심, 오프라인 표시 캐시와 구분. 가져오기 성공 항목만 이관 완료 처리. 실패/미선택 보존.

명함 발행은 현재 공개 선택의 스냅샷이다. 프로필 편집이 기존 발행본에 자동 전파되지 않는다. 이는 공개 범위가 의도치 않게 확대되는 것을 막는 초기 구현 결정이다. 명함 철회는 서버 재조회 시 404이고 서버의 명함 내용도 바로 비운다(2026-10-25, 아래 "명함 지우기"). 이미 전달된 정보 자체의 원격 삭제는 보장하지 않는다. 갱신 정책 확대는 별도 이슈로 추적한다.

## 첫 수직 구현 API

| 메서드·경로 | 요청 | 성공 응답 |
| --- | --- | --- |
| (인증) | 아래 "패스키 로그인"의 `/auth/passkeys/*`, `/auth/refresh`, `/auth/logout`, "애플 로그인"의 `/auth/apple/*` | 2026-10-10 이메일 인증번호(`/auth/challenges`, `/auth/sessions`, `DELETE /auth/session`)를 대체 |
| GET /profile | 없음 | 200 Profile |
| PUT /profile | `{name,job,introLink,contacts,histories}` | 200 Profile |
| GET /cards | 없음 | 200 `{items: Card[]}` (자신의 명함) |
| POST /cards | `{name,description,contactIds,historyIds}` | 201 Card |
| GET /cards/:id | 없음·비로그인 가능 | 200 Card |
| PUT /cards/:id | `{name,description,contactIds,historyIds}`·소유자만 | 200 Card(같은 id, `updatedAt` 갱신; 2026-10-20 "명함 고치기는 같은 명함을 수정"). 남의 명함·철회된 명함 404, 검증 400은 POST와 같음. 본문은 현재 프로필 자기소개로 다시 만든다(아래 "템플릿") |
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

웹은 same-origin Next 서버 프록시를 사용한다. API origin은 서버 env `DEARBY_API_ORIGIN`; 브라우저는 API 키를 받지 않는다. Next와 API에 동일한 서버 전용 `GUEST_PROXY_SECRET`(최소 32자)을 root가 배포 시 설정한다. 모든 guest API에는 Next가 `X-Guest-Proxy-Key`를 넣고, 쿠키가 있으면 `X-Guest-Token`을 넣는다. 클라이언트가 보낸 동명 헤더는 전달하지 않고 서버 env/cookie로 다시 구성한다. API는 Cookie, Origin, forwarded IP를 방문자 인증으로 신뢰하지 않는다.

| 메서드·경로 | 성공 응답 | 의미 |
| --- | --- | --- |
| GET /v1/guest/cards | 200 `{items: GuestItem[]}` | 자기 세션의 미철회·미회수 공개 명함 목록(2026-10-19부터 GuestItem, 아래 "명함 교환") |
| PUT /v1/guest/cards/:id | 무토큰 첫 저장 201 `{cardId,status:"saved",guestToken}` | path UUID·DB존재·미철회 확인 후 세션과 저장을 원자 생성 |
| PUT /v1/guest/cards/:id | 기존 토큰 200 `{cardId,status:"saved"\|"alreadySaved"}` | 중복 없이 저장, 토큰/세션 재생성 없음 |
| DELETE /v1/guest/cards/:id | 204 | 자기 참조만 제거; 이미 제거·철회된 ID도 멱등 처리 |
| DELETE /v1/guest/session | 204 | 자기 세션 및 저장 참조 폐기 |

요청 body는 필요 없다. 새 세션 생성 전용 endpoint는 없다. 무쿠키 목록 조회는 Next에서 `{items:[]}`로 처리해 API 호출이나 DB 세션 생성을 하지 않는다. 첫 저장 응답 `guestToken`은 Next 서버만 소비하며 브라우저 JSON/로그에 노출하지 않는다. DB에는 SHA-256 token digest와 card ID 연결만 저장하고 원문 토큰·쿠키·방문자 개인정보를 저장하지 않는다. 회원 wallet과 guest 저장은 서로 독립이며 자동으로 옮기지 않는다. 2026-10-26부터 웹에 로그인한 사용자가 직접 요청하면 `POST /v1/wallet/import-guest`로 게스트 저장을 계정 지갑으로 옮긴다(아래 "웹 로그인").

쿠키 이름은 `__Host-dearby_guest`, `HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=34560000`이며 **Domain 속성을 넣지 않는다**. 서버 세션은 자동 만료가 없고 `expiresAt` 응답도 없다. 브라우저 쿠키는 400일이며 요청 시 같은 token의 Max-Age만 갱신한다. 갱신으로 새 token/session을 만들거나 기존 저장을 지우지 않는다. 브라우저 자체 보관 제한·사용자 쿠키 삭제로 접근이 끊길 수 있으며 무제한 쿠키 보관을 보장하지 않는다. 명시적 세션 삭제에서 API 401(이미 폐기)도 웹은 쿠키를 삭제한다. 다른 401을 임의로 새 세션으로 대체하지 않는다.

GET과 성공 mutation 모두 `Cache-Control: no-store`다(API는 오류 응답에도 적용). 웹 프록시도 동일하게 유지하며 개인화 응답/Set-Cookie를 CDN 공유 캐시하지 않는다. 웹 mutation은 Origin을 현재 허용된 웹 origin과 정확히 대조하고 JSON 또는 custom-header 조건을 검증한다. 누락/불일치 Origin도 거부한다. SameSite만으로 CSRF 검증을 대체하지 않는다. guest API는 Cookie를 받는 것만으로 권한을 부여하지 않으며 proxy secret 없는 직접 요청은 거부한다. 첫 무토큰 저장은 웹에서 Web Locks 등으로 탭 간 직렬화하여 중복 세션/쿠키 덮어쓰기를 방지한다. 별도 기기/경쟁 요청 사이 무토큰 멱등성을 서버가 보장하지는 않는다.

- 401 `GUEST_SESSION_INVALID`: 토큰 누락(기존 세션 필요 경로), 잘못된 형식, 미존재/폐기. 해당 token으로 PUT도 자동 재생성하지 않는다.
- 403 `FORBIDDEN`: proxy secret 누락/불일치. 서버 secret 미설정/너무 짧으면 503 `GUEST_UNAVAILABLE`.
- 404 `NOT_FOUND`: 조회/저장 대상이 없거나 철회됨. UUID 형식 오류는 422 `INVALID_INPUT`. 저장 제거는 공개 가능 여부와 무관하게 자신의 참조만 지울 수 있다.
- 409 `GUEST_CAPACITY_EXCEEDED`: 세션당 활성 명함 100개 또는 전체 guest 세션 10,000개 상한. 철회 명함은 조회에서 제외하고 다음 저장에서 참조를 정리해 용량을 반환한다. 동일 명함 재저장은 상한에서도 성공한다. 전체 세션 상한은 신규 세션만 차단하며 기존 목록/저장/삭제는 유지한다. 자동 만료가 없으므로 전체 상한은 운영 해소가 필요하다; 임의 오래된 세션 삭제를 의미하지 않는다.
- 429 `RATE_LIMITED`: 단일 API 프로세스 고정 1분 창 기준 전체 guest 요청 1,200회, 유효 세션당 120회, 신규 세션 생성 60회. 카운터는 메모리이며 재시작 시 초기화된다. 저장 상한은 DB에 유지된다. forwarded IP를 신뢰해 우회시키지 않는다. 웹은 신뢰 가능한 실제 client 단위 요청 제한/남용 방어를 별도 적용해야 한다.
- 저장 실패는 500 `INTERNAL_ERROR`이며 새 세션과 명함 참조를 함께 rollback한다. DB·proxy secret·guest token 등 내부값은 오류 응답/로그에 넣지 않는다.

HTTPS ingress는 catalog GET/HEAD, 공개 cards/:id GET/HEAD, 위 guest 경로만 좁혀 공개한다. 기존 `/v1/profile`, 회원 `/v1/wallet`, 명함 생성/철회, SMTP/auth는 열지 않는다. guest proxy secret 생성·배포와 nginx 운영 변경은 root가 수행하며 앱서버 구현 PR만으로 운영 반영됐다고 표시하지 않는다.

## 명함 공유 — 변경 이력 (2026-10-06 승인 → 2026-10-18 삭제 → 2026-10-19 "명함 교환"으로 대체)

현재 명함을 건네는 방법은 아래 "명함 교환"(2026-10-19) 한 곳에만 적는다. 옛 공유 기능(CardShare·GuestShare, `POST /v1/cards/:id/shares`, `GET /v1/shares/:id`, `PUT /v1/guest/shares/:id`·`PUT /v1/wallet/shares/:id`, `GET /v1/wallet`·`GET /v1/guest/cards`의 `shares`, 웹 `/s/:shareId`와 `/saved`의 `전체 | 활동별` 보기, 앱 QR 표시·OS 공유·앱 안 스캐너·유니버설 링크/App Links)은 2026-10-18 사용자 결정으로 서버·웹·앱에서 지웠고 복원하지 않는다. 운영 DB의 `card_shares`·`wallet_shares`·`guest_card_shares`는 같은 날 V19로 지웠다.

- 변경 이력: 2026-10-06 승인 계약 [`6636e9a6`](https://github.com/fixabley/dearby/blob/6636e9a6/shared/contracts/native-v1.md#명함-공유-기록과-게스트-공유-정보-저장--2026-10-06-승인) · 2026-10-18 삭제 기록 [`a60a0ca7`](https://github.com/fixabley/dearby/blob/a60a0ca7/shared/contracts/native-v1.md) · 2026-10-19 재설계 [`f6f440e6`](https://github.com/fixabley/dearby/blob/f6f440e6/shared/contracts/native-v1.md#명함-교환--2026-10-19-사용자-결정).

## 모바일 실제 연결 경계 — 2026-10-06 사용자 결정 반영

사용자 결정: "QR 공유는 실제 동작까지", "활동 신청이 쉽고 명함 제작·교환이 간결해야 한다", "비로그인 사용자는 세션 기준으로 저장이 유지되어야 한다", "유니버설 링크를 적용한다". 이 절은 iOS·Android 오프라인 프로토타입 범위([모바일 프로토타입](../../docs/context/mobile-ui-prototype.md)) 중 아래 항목만 실제 서비스로 바꾼다. 나머지 화면은 예시로 남는다. 근거 조사: `docs/research/activity-and-qr-friction-2026-10-06.md`(PR #94).

### 실제로 바꾸는 것

| 영역 | 동작 | 사용 API |
| --- | --- | --- |
| 발견·활동 상세 | 공개 카탈로그를 보여 주고, 신청 CTA는 활동의 공식 신청 URL을 외부 브라우저로 연다. 실패하면 오류와 다시 시도를 보여 주며 예시 활동을 섞지 않는다. | GET /v1/activities (2026-10-14부터, 아래 "활동 조회") |
| 로그인 | 패스키(2026-10-10 결정, 아래 "패스키 로그인"). 명함 발행과 받은 명함 보기를 할 때만 요구하고, 발견·신청은 로그인 없이 쓴다. access·refresh 토큰은 OS 보안 저장소에 둔다. | /v1/auth/* |
| 명함 제작 | 프로필 입력과 공개할 연락처·이력 선택을 한 흐름으로 마치고 바로 발행한다. 고치기는 2026-10-20부터 같은 명함을 `PUT /v1/cards/:id`로 수정한다(아래 "명함 고치기는 같은 명함을 수정"). | /v1/profile, POST·PUT /v1/cards |
| 명함 교환 | 명함별 QR(공개 주소 + 활동) → 웹에서 보고 `dearby://cards/…`로 앱 저장 또는 브라우저 저장, 단방향은 30일 뒤 회수. 아래 "명함 교환"(2026-10-19). 옛 QR 공유·스캔은 2026-10-18 삭제(위 "명함 공유 — 변경 이력"). 유니버설 링크·App Links는 2026-10-22 "유니버설 링크·App Links·활동 공유·오픈그래프"로 `/cards/*`·`/activities/*`에 다시 둔다 | PUT /v1/wallet/cards/:id, POST …/reciprocate, GET /v1/wallet |

- 앱은 `/s/*` 유니버설 링크를 받지 않는다(2026-10-18 삭제). 2026-10-22부터 웹 도메인의 `/cards/*`·`/activities/*`를 유니버설 링크·App Links로 받는다(아래 "유니버설 링크·App Links·활동 공유·오픈그래프"). 커스텀 스킴 `dearby://`(애플 콜백, 명함 교환)도 그대로다.

### 비로그인 사용자의 저장 유지

2026-10-06 사용자 결정: 비로그인 사용자는 앱을 설치하지 않았고 로그인하지 않은 사람이다. 공개 명함 링크 `/cards/:id`(명함 교환 QR의 주소, 2026-10-18까지는 `/s/<shareId>`)가 기본 브라우저에서 열린다. 서버가 만료 없는 게스트 세션 ID를 발급하고, 저장 데이터는 DB에 두며 API가 세션 ID로 조회한다. 브라우저에는 세션 ID 쿠키만 둔다.

- 웹: 위 "웹 비로그인 명함 저장" 계약의 서버 게스트 세션(`__Host-dearby_guest` 쿠키)을 그대로 쓴다. 서버 세션은 만료되지 않는다. 브라우저는 쿠키 수명을 최대 400일로 제한하므로, 방문할 때마다 Max-Age를 갱신한다(기존 규칙). 1년 넘게 방문하지 않거나 사용자가 쿠키를 지우면 접근이 끊긴다.
- 홈 화면 추가: Android Chrome은 바로가기·설치 모두 Chrome과 같은 쿠키를 쓴다. iOS는 '웹 앱으로 열기'가 기본으로 켜져 있고 사이트가 바꿀 수 없어서, 그대로 추가하면 Safari와 쿠키가 분리된다(2026-10-06 iOS 26.5 시뮬레이터 확인). 사용자 결정으로 아래 "홈 화면 세션 잇기"를 둔다. manifest `display`는 Android 설치 버튼을 위해 `standalone`을 유지한다. iOS는 공유 메뉴에서 직접 추가해야 하므로 1번 클릭 설치는 불가하고 안내로 제공한다.
- 홈 화면 바로가기 안내(2026-10-26 사용자 요청): `/cards/:id`에서 브라우저 저장에 성공하면 카드 아래 작은 안내 카드를, `/saved`에서는 기존 안내 시트를 띄운다. 브라우저가 `beforeinstallprompt`를 주면(Android Chrome·삼성 인터넷) `홈 화면에 추가` 버튼(`prompt()`), 아니면 메뉴 단계(iOS Safari·iOS Chrome: 공유 › 홈 화면에 추가, Android Chrome: ⋮ › 홈 화면에 추가, 삼성 인터넷: ≡ › 현재 페이지 추가 › 홈 화면). `/cards/:id`의 카드는 버튼이 없으면 `저장한 명함 보기`(전체 페이지 이동)에서 추가하라고만 안내한다: 바로가기가 목록을 가리키고, iOS가 저장 뒤 불러온 manifest의 1회용 코드(위 "홈 화면 세션 잇기")를 읽게 하려는 것이다. iOS Chrome으로 만든 홈 화면 앱이 Chrome의 쿠키를 넘겨받는지는 확인되지 않아 "목록이 비어 보이면 Safari에서 저장" 한 줄을 붙인다. 홈 화면에서 연 경우(`display-mode: standalone`)·방금 설치·닫기(이번 방문)·`다시 보지 않기`(localStorage)면 숨긴다. manifest `display`는 위 이유로 `standalone` 그대로이고, 이름은 `Dearby 명함함`이다.
- 홈 화면 세션 잇기(2026-10-06 사용자 결정): 홈 화면 앱이 처음 열릴 때 1회용 코드로 Safari와 같은 게스트 세션 ID 쿠키를 받는다. 이후 두 곳은 같은 세션을 쓰며, 한쪽에서 세션을 지우면 다른 쪽도 끊긴다.
  - 발급: 웹 manifest는 요청마다 만든다. 게스트 쿠키가 있고 저장한 명함이 1개 이상이면 Next가 `POST /v1/guest/handoffs`(게스트 프록시)로 코드를 받아 `start_url`을 `/saved?handoff=<code>`로 넣는다. 아니면 `/saved`. manifest 응답은 `Cache-Control: private, no-store`와 `Vary: Cookie`이다.
  - API `POST /v1/guest/handoffs`: 기존 토큰 필요, 201 `{code, expiresAt}`. 코드는 32바이트 이상 무작위 base64url, 수명 10분, 1회용. 세션당 유효 코드는 1개이며 새로 발급하면 이전 코드는 무효다. DB에는 코드의 SHA-256 digest·세션·만료·사용 시각과, 발급 요청의 게스트 토큰을 코드에서 파생한 키(HKDF)로 AES-256-GCM 암호화한 값만 둔다. DB만으로는 토큰을 복원할 수 없고, 사용·만료 시 암호문을 지운다.
  - API `POST /v1/guest/handoffs/redeem`: body `{code}`, 게스트 프록시, 토큰 없이 호출. 200 `{guestToken}`(코드로 복호화한, Safari와 같은 토큰). 없음·만료·사용됨·세션 폐기는 모두 404 `NOT_FOUND`(구분하지 않음). 성공하면 즉시 사용 처리한다. 요청 제한은 게스트 카운터와 같고, 실패도 센다.
  - 웹 `/saved?handoff=<code>`: 게스트 쿠키가 없을 때만 교환한다. 성공하면 쿠키를 설정하고, 성공·실패 모두 303으로 쿼리 없는 `/saved`로 보낸다. 이미 쿠키가 있으면 교환하지 않고 바로 보낸다. 페이지는 `Referrer-Policy: no-referrer`를 쓰고, 서버 로그에서 `handoff` 쿼리를 가린다.
  - 홈 화면 앱의 빈 목록(2026-10-26 사용자 동의): iOS 홈 화면 웹 앱(`display-mode: standalone`·`navigator.standalone`)에서 연 `/saved`가 비어 있으면(코드 만료로 못 이은 경우 등) "브라우저에서 저장한 명함을 불러오지 못했어요"와 "Safari에서 <호스트>/saved를 열면 다시 이어져요", 주소 복사 버튼, Safari에서 연 뒤 바로가기를 다시 추가하라는 안내를 보인다. 홈 화면 앱은 Safari 쿠키가 없어 코드를 다시 받을 수 없고, 코드 유효 시간은 늘리지 않는다. Android는 브라우저와 쿠키를 함께 쓰므로 평소 빈 목록 그대로다.
  - 위험: 코드는 유효 시간 동안 게스트 세션 전체(저장한 명함)를 넘겨받는 열쇠다. 다른 기기에서의 사용을 막을 수 없다. 1회용·10분·digest 저장·요청 제한·manifest 캐시 금지로 줄인다. 마이그레이션은 운영 DB 변경이므로 별도 PR로 사용자 승인 뒤 병합한다.
- 앱의 게스트 세션·게스트 API는 만들지 않는다. 앱은 `dearby://cards/<id>` 스킴으로 받은 명함을 계정 지갑에 저장한다("명함 교환").
- 2026-10-03 이전 기기에 남은 앱 저장 데이터는 읽거나 지우거나 이관하지 않는다.

### 연결 설정 — 도메인 기반, 환경값으로 지정

2026-10-06 사용자 결정: 모든 연결은 도메인 기반이며 환경값으로 지정한다. 코드에 운영 도메인·IP·포트를 고정하지 않는다.

| 환경값 | 쓰는 곳 | 의미 |
| --- | --- | --- |
| `DEARBY_API_ORIGIN` | 웹 서버, iOS·Android 빌드 | API origin(scheme+도메인, `/v1` 제외). 예: `https://api.dearby.wid.io.kr` |
| `DEARBY_WEB_ORIGIN` | iOS·Android 빌드 | 웹 origin. 명함 교환 QR 주소(`<웹 origin>/cards/<id>`)에 쓴다. 기본 `https://dearby.wid.io.kr`(2026-10-18 삭제 뒤 2026-10-19 다시 도입) |
| `GOOGLE_IOS_CLIENT_ID`·`GOOGLE_IOS_URL_SCHEME`(iOS), `GOOGLE_SERVER_CLIENT_ID`(Android) | 앱 빌드 | 구글 네이티브 로그인(아래 "구글 로그인"). 비어 있으면 버튼 숨김 |

`DEARBY_APPLE_TEAM_ID`·`DEARBY_IOS_BUNDLE_IDS`·`DEARBY_ANDROID_PACKAGE`·`DEARBY_ANDROID_CERT_SHA256`(웹 `/.well-known` 연결 파일)은 2026-10-18 공유 삭제 뒤 쓰지 않는다.

- 운영·배포 빌드는 `https`와 도메인 host만 허용한다. IP 주소 host, `http`, 포트 지정, 경로가 있는 값은 빌드 또는 시작 시 실패시킨다. 개발 구성만 `http://localhost`·`127.0.0.1`·Android 에뮬레이터 `10.0.2.2`를 허용한다.
- iOS: 빌드 환경값을 Info.plist 키(`DearbyAPIOrigin`)로 넣는다. 값이 없으면 Release 빌드는 실패하고, Debug는 로컬 개발 기본값을 쓴다.
- Android: Gradle이 같은 환경값(또는 같은 이름의 Gradle 속성)을 읽어 `BuildConfig`로 넣는다. 값이 없으면 release 빌드는 실패한다.
- 웹은 요청이 들어온 자기 origin을 기준으로 동작하므로 웹 도메인 환경값이 따로 필요 없다. API 서버의 `HOST`·`PORT`는 내부 바인딩이며, 외부에서는 ingress 도메인으로만 접근한다.

### 앱에서 받은 명함 저장 (회원 wallet)

2026-10-19부터 `PUT /v1/wallet/cards/:cardId`와 `POST /v1/wallet/cards/:cardId/reciprocate`가 Receipt를 만든다(아래 "명함 교환"). 2026-10-06의 `PUT /v1/wallet/shares/:id`·`GET /v1/wallet`의 `shares`는 2026-10-18 삭제했다(위 "명함 공유 — 변경 이력").

### 신청 확인과 기기 캘린더 겹침 — 2026-10-06 사용자 결정

- **신청 확인:** 활동 상세나 빠른 신청에서 공식 신청 URL을 외부 브라우저로 연 뒤, 앱이 처음 다시 활성화되면 그 활동에 대해 한 번 "신청하셨나요?"를 묻는다. 선택지는 `신청했어요`(내 활동에 신청함으로 표시) / `아직이에요`(표시하지 않음, 다음에 신청 URL을 열면 다시 물음) / `다시 묻지 않기`(이 활동은 묻지 않음)다. 신청 URL을 열지 않았거나 이미 신청함인 활동은 묻지 않는다. 신청 표시는 사용자 직접 기록이며 주최 측 접수 확인이 아니다. 저장은 기존처럼 기기 메모리에 둔다.
- **기기 캘린더 겹침:** 활동 상세의 `겹치는 시간 확인`을 누를 때만 캘린더 읽기 권한을 요청한다(iOS EventKit 전체 접근 `requestFullAccessToEvents`, Android `READ_CALENDAR`). 활동 일정(`schedules`의 시작·끝·`timeZone`)과 기기 일정의 겹침을 기기 안에서만 계산해 보여 준다. 일정 제목·시각은 서버로 보내거나 앱에 저장하지 않으며, 화면을 떠나면 버린다. 권한을 거부하면 이유와 설정 열기를 안내하고 신청 흐름은 막지 않는다. 시작·끝 시각이 없는 활동은 버튼을 숨긴다. 캘린더에 쓰기(일정 추가)는 하지 않는다. 2026-10-19 구현(iOS EventKit `requestFullAccessToEvents`, Android `CalendarContract.Instances`): 겹침은 시작·끝 시각이 있는 기기 일정만 보고 **종일 일정은 겹침에 넣지 않는다**; 겹친 일정의 제목은 화면에만 보이고 저장·전송하지 않는다; 권한 요청 전에 무엇을 읽고 어디까지 쓰는지 안내 화면을 먼저 보인다.

### 예시로 남는 것

참여 확정 표시(사용자 직접 표시이며 주최 측 확인이 아님), 명함 서버 간 직접 전달(POST /v1/exchanges)은 이번 범위가 아니다. 예시 화면은 실제 동작으로 표현하지 않는다.

### 운영 의존

운영 경로 공개 #88, 인증 메일 #89, 연결 파일 값 #90, 마이그레이션·배포 승인 #91, 외부 도달성 #65. 해소 전에는 로컬 API와 테스트 데이터로 검증하며 운영 동작 완료로 표시하지 않는다.

## 패스키 로그인 — 2026-10-10 사용자 결정

이메일 인증번호 로그인은 없앤다. 패스키로 로그인한다. 기기를 잃으면 OS의 패스키 동기화(iCloud 키체인·Google 비밀번호 관리자)에 의존하며, 별도 계정 복구 경로는 없다.

- **2026-10-25 사용자 결정 — 패스키 가입 제거**: 새 계정은 애플·구글 로그인으로만 만든다. 앱(iOS·Android)의 로그인 화면에서 "새 패스키로 시작"을 없앴고, 시작 화면의 "패스키로 시작하기"는 "패스키로 로그인"이 됐다. 이미 패스키로 만든 계정의 "패스키로 로그인"과 설정의 패스키 추가·이름 바꾸기·삭제(로그인한 계정에 패스키를 더함)는 그대로다. 서버의 Bearer 없는 `registration/options`·`registration`(새 계정 생성)은 이미 배포된 앱 버전이 쓰므로 이번에는 지우지 않는다. 새 클라이언트는 이 경로를 Bearer 없이 부르지 않는다.
- 변경 이력: 2026-10-10 결정은 "계정은 패스키로만 만들고 로그인한다. 처음 패스키를 만들 때 계정도 함께 생기고, 이후에는 그 패스키로 로그인한다."였다(애플·구글 로그인 추가 뒤에도 패스키 가입은 2026-10-25까지 남아 있었다).

### 신뢰 당사자와 연결

- RP ID는 `wid.io.kr`(API `WEBAUTHN_RP_ID`). 바꾸면 등록된 패스키를 모두 쓸 수 없다.
- `https://wid.io.kr/.well-known/apple-app-site-association`에 `webcredentials.apps`(`<TeamID>.<bundle ID>`)를, `https://wid.io.kr/.well-known/assetlinks.json`에 `delegate_permission/common.get_login_creds`(패키지·서명 SHA-256)를 둔다. `wid.io.kr`은 웹(Vercel)이 아니라 운영 ingress(nginx)가 내준다. 유니버설 링크 `/s/*`의 `applinks`는 기존대로 웹 도메인에 둔다.
- iOS entitlements에 `webcredentials:wid.io.kr`, Android는 Credential Manager를 쓴다.
- API 허용 origin(`WEBAUTHN_ALLOWED_ORIGINS`)에 iOS의 `https://wid.io.kr`과 Android의 `android:apk-key-hash:<서명 SHA-256의 base64url>`을 넣는다. 웹에서 패스키를 쓰면 웹 origin(`https://dearby.wid.io.kr`)도 넣는다(2026-10-26 "웹 로그인"; `.env.prod.example`에는 이미 있다).

### API

브라우저 세션 쿠키에 기대지 않는다. 옵션 요청이 `challengeId`를 돌려주고, 완료 요청이 그 값을 다시 보낸다. challenge는 한 번만 쓸 수 있고 5분 뒤 만료된다. `options`와 `credential`은 WebAuthn 표준 JSON 형식(`PublicKeyCredentialCreationOptionsJSON`·`RequestOptionsJSON`, `RegistrationResponseJSON`·`AuthenticationResponseJSON`, base64url)이다.

| 메서드·경로 | 인증 | 요청 | 성공 응답 |
| --- | --- | --- | --- |
| POST /v1/auth/passkeys/registration/options | 없음(가입, 배포된 옛 앱만) 또는 Bearer(패스키 추가) | `{displayName?}` | 200 `{challengeId, options}` |
| POST /v1/auth/passkeys/registration | 없음(가입, 배포된 옛 앱만) 또는 Bearer(패스키 추가) | `{challengeId, credential}` | 201 TokenPair. Bearer 없으면 이때 계정이 생긴다(2026-10-25부터 새 앱은 쓰지 않음) |
| POST /v1/auth/passkeys/authentication/options | 없음 | 없음 | 200 `{challengeId, options}` (`allowCredentials`는 비움, 기기에 있는 패스키 중 고름) |
| POST /v1/auth/passkeys/authentication | 없음 | `{challengeId, credential}` | 200 TokenPair |
| POST /v1/auth/refresh | 없음 | `{refreshToken}` | 200 TokenPair. 쓴 refresh token은 폐기된다(회전) |
| POST /v1/auth/logout | 없음 | `{refreshToken}` | 204. 이미 무효여도 204 |

- TokenPair: `{accessToken, refreshToken, user: {id, roles}}`(2026-10-15 사용자 결정으로 user 추가, 이전 10-10 결정은 토큰만). `user`는 클라이언트가 화면 권한 등에 쓰는 값이고 서버는 토큰만 믿는다. 역할이 바뀌면 다음 refresh 응답에 반영된다. access token은 API가 RS256으로 서명한 JWT이고, 사용자 id는 `sub`, 역할은 `roles`, 만료는 `exp` 클레임에 있다. refresh는 받은 refresh token을 폐기하고 새 access·refresh token을 준다.
- 실패: challenge 없음·만료·재사용, 서명 검증 실패, 모르는 패스키는 401. 형식이 틀린 요청은 400. 오류 본문은 RFC 9457 ProblemDetail이고 `detail`이 사용자에게 보여도 되는 문장이다(2026-10-11 통일). 앱은 상태 코드로 분기하고 `detail`은 안내 문구로만 쓴다.
- `displayName`은 OS 패스키 목록에 계정 이름으로 보이는 이름이다(패스키의 `user.name`·`user.displayName` 둘 다 이 값). 없으면 `Dearby`(2026-10-15 사용자 결정, 이전에는 `user.name`이 내부 UUID라 목록에 UUID가 보였다). 같은 이름을 여러 계정이 써도 된다. 패스키의 `user.id`(user handle)는 서버가 정하는 사용자 UUID 16바이트이고, 로그인할 때 이 값으로 계정을 찾는다. 앱은 정하지 않는다.

### 앱 동작

- 처음 실행할 때 한 번만(2026-10-14 사용자 결정) 로고와 로그인 버튼(애플·구글·"패스키로 로그인", 2026-10-25 전에는 "패스키로 시작하기")·"둘러보기"가 있는 시작 화면을 보여 준다. 로그인은 선택이며, 로그인에 성공하거나 둘러보기를 누르면 다시 보이지 않는다. 이미 로그인되어 있으면 건너뛴다. 매 실행의 OS 스플래시에는 로고만 둔다.
- 로그인이 필요한 동작(명함 발행, 받은 명함 보기)에서 로그인 화면을 띄운다. 애플·구글·"패스키로 로그인"을 고른다. 두 플랫폼 모두 애플·구글이 위 주 버튼, "패스키로 로그인"은 그 아래 보조 버튼이다(2026-10-25, Android도 iOS 순서로). 새 계정은 애플·구글로만 만든다(2026-10-25, 이전에는 "새 패스키로 시작"으로 가입했다). 이 기기에 패스키가 없으면 애플·구글로 시작하라고 안내한다.
- 로그인 시트 맨 위에는 락업 이미지 한 장(가로 워드마크 + 그 아래 캐치프레이즈 "대외활동, 매일 맞춤으로", 원본 `shared/assets/brand/sign-in-lockup.html`, 1240×480 투명 PNG 라이트·다크 두 장)을 둔다. 시트는 두 플랫폼 모두 화면 절반 높이로 고정하고(iOS `.presentationDetents([.medium])`, Android 부분 펼침 없이 본문 높이 화면의 50%; 작은 화면에서는 위쪽 내용이 스크롤되고 버튼은 보인다), 이미지는 높이 96pt/dp·비율 유지(시트의 약 1/4)·글자 배율과 무관한 고정 크기로 시트 높이의 약 1/4, 접근성 라벨 "Dearby, 대외활동, 매일 맞춤으로"(2026-10-25 사용자 요청; 이전에는 심볼 아래 앱 이름 텍스트). 다크 변형은 워드마크 #3FB3B4·문구 밝은 회색. 시작 화면도 두 플랫폼 모두 로그인 시트와 같게 한다(2026-10-25 사용자 승인, 이전에는 심볼 + 앱 이름과 큰 제목 "활동에서 만난 사람과 명함을 주고받아요"): 같은 락업 이미지만 두고(큰 제목·설명 없음), 버튼은 애플 → 구글 → 로그인 고지 한 줄(아래 "애플 로그인"의 "로그인 고지") → "패스키로 로그인"·"둘러보기" 보조(텍스트) 버튼. 로그인 시트도 본문 큰 제목·설명 없이 락업 아래 버튼 묶음이다(애플·구글이 없는 빌드는 "패스키로 로그인" 제목·설명과 주 버튼).
- access token이 만료돼 401을 받으면 refresh를 한 번 시도한다. refresh가 401로 거절되면 토큰을 지우고 로그인 화면으로 돌아간다. 네트워크 오류·5xx면 토큰을 남기고 그 요청만 실패시킨다(불안정한 연결에서 로그아웃되지 않게). refresh token이 회전하므로 동시에 여러 요청이 401을 받아도 refresh는 한 번만 한다.
- 로그아웃은 `/v1/auth/logout` 뒤 기기 토큰을 지운다. 네트워크 실패여도 기기 토큰은 지운다.
- 기존 이메일 로그인 화면·저장 토큰(`sessionToken`)은 쓰지 않는다. 이전 앱 저장 데이터는 읽거나 지우지 않는다는 저장소 원칙을 따른다.

## 프로필·명함 v2 — 2026-10-16 사용자 결정

TypeScript API를 지운 뒤 비어 있던 프로필·명함·지갑 경로를 Kotlin `api`가 다시 제공한다. 위 "모델"·"첫 수직 구현 API"의 Profile·Card 설명은 이 섹션이 대체한다. 오류는 ProblemDetail(400·401·403·404·409)이며 옛 422는 400이 됐다. id는 모두 UUID 문자열이고(조직·프로그램·활동·역할만 숫자), 연락처·이력 id는 클라이언트가 만든 UUID를 서버가 그대로 둔다.

### 프로필과 버전 이력

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| GET /v1/profile | Bearer | — | 200 Profile. 아직 없으면 빈 프로필(`version` 0) |
| PUT /v1/profile | Bearer, `If-Match: <version>` 선택 | `{name, jobs[], introLink?, contacts[], histories[]}`(옛 앱의 `introduction`은 받고 무시) | 200 Profile(`version`+1). `If-Match`가 현재 버전과 다르면 409 |
| GET /v1/profile/versions | Bearer | `cursor?`, `size?` | 200 `{items: [{version, createdAt}], nextCursor}` 최신순 |
| GET /v1/profile/versions/:n | Bearer | — | 200 `{version, createdAt, profile}` 그때의 프로필 |
| POST /v1/profile/versions/:n/restore | Bearer | — | 200 Profile. 그 버전 내용으로 새 버전을 만든다(이력은 지우지 않는다) |

- Profile: `{id(=사용자 id), name, jobs: ProfileJob[], job, introduction(폐지 예정, 늘 ""), introLink, contacts: Contact[], histories: History[], version, updatedAt, photoUrl}`. `photoUrl`은 아래 "프로필 사진"(없으면 null, 버전 스냅샷에는 싣지 않는다). `job`은 승인된 직무 이름을 " · "로 이은 읽기 전용 문자열(명함의 `job`과 같다)이고 PUT에서는 무시한다.
- 직무(2026-10-17 사용자 결정, 이전 "직함" 문자열을 대체): 직무는 조직처럼 계층을 가진 활동 정보이고 프로필은 여러 개를 태그로 둔다. ProfileJob: `{id(클라이언트 UUID), job: {id, name} | null, jobName, approval: approved|pending|rejected, approvalReason}`. PUT에는 `{id, jobId?, jobName?}`를 보낸다(둘 다 보내면 `jobId`). 검색에 없으면 `jobName`만 적는 직접 입력이고 이력과 같은 규칙으로 관리자 승인 전(`pending`)에는 명함 `job`에 들어가지 않는다. 직접 입력 값이 그대로면 승인·거절 결과가 유지되고 바뀌면 다시 `pending`. 최대 10개, 같은 직무 중복 400. 검색은 `GET /v1/jobs`(아래 "이력"의 선택 창 검색과 같은 형식, `q?`, `parentId?`, `rootsOnly?`, `cursor?`, `size?`; 항목에 바로 위 직무 `parent: {id, name}`). V18 이전 버전 스냅샷의 `job` 문자열은 승인된 직접 입력 직무로 읽힌다.
- 프로필은 **데이터 입력**만 한다(2026-10-16 사용자 결정). 자기소개 글(`introduction`)은 2026-10-26 폐지되어 이름 옆 소개 링크(`introLink`)가 대신한다(아래 "소개 링크·소개 글 폐지").
- 저장할 때마다 전체 프로필 스냅샷을 버전으로 남긴다(노션처럼 이전 상태를 보고 되돌린다). 같은 내용이면 저장하지 않고 현재 프로필을 돌려준다.
- 연락처 규칙은 그대로다: phone·email 각각 하나 이상, phone은 숫자·`+`·`-`·공백만(숫자 8~15자리), email 기본 형식, 값에 제어 문자 금지, URL은 https만. 어기면 400.

### 이력 (History) — 단계별 입력

사용자는 토스 퍼널처럼 단계별로 고른다: **조직인지 활동인지 → 어떤 조직 → 어떤 프로그램 → (활동이면) 어떤 활동 → 역할 → 기간**. 조직·프로그램·활동·역할은 등록된 활동 정보에서 검색해 고르고, 없으면 직접 입력한다.

- History: `{id, kind: "organization"|"activity", organization: Ref|null, organizationName, program: Ref|null, programName, activity: Ref|null, activityName, role: Ref|null, roleName, startDate, endDate, description, approval, approvalReason, verification, evidence: Evidence[]}`
  - Ref는 `{id(숫자), name}`. 등록된 활동 정보에서 고르면 Ref와 이름이 함께 오고, 직접 입력이면 Ref가 null이고 `*Name`만 있다. 요청에서는 `organizationId`·`programId`·`activityId`·`roleId`(숫자, 선택)와 `organizationName`·`programName`·`activityName`·`roleName`(직접 입력, 선택)을 보낸다. 둘 다 보내면 id를 쓴다.
  - `kind=organization`은 조직(선택으로 프로그램)과 역할, `kind=activity`는 활동(소속 프로그램·조직 포함)과 역할. 조직 또는 조직 이름은 필수, 역할 또는 역할 이름은 필수. `startDate` 필수, `endDate`는 진행 중이면 null, 시작보다 앞설 수 없다.
  - 2026-10-18 사용자 지시(입력 퍼널 분리): 조직 이력 퍼널은 조직 → 역할 → 기간 → 설명만 묻고 프로그램·활동은 묻지 않는다. 활동 이력 퍼널은 활동(전체 활동 검색, 소속 조직 표시) → 역할 → 기간 → 설명만 묻는다. 활동을 활동 정보에서 골랐으면 서버가 `activityId`로 소속 프로그램·조직을 채우므로 요청에 `organizationId`를 넣지 않아도 된다(넣으면 그 값이 활동의 소속과 달라도 거절하지 않고 활동의 소속으로 맞춘다). 활동을 직접 입력했을 때만 조직(필수)·프로그램(선택) 단계를 뒤에 묻는다. 활동 검색 응답(`GET /v1/activities` 선택 창)에는 `organization`과 `program` Ref를 함께 싣는다.
  - `approval`: `approved` | `pending` | `rejected`. 직접 입력(`*Name`만 있는 값)이 하나라도 있으면 `pending`으로 저장되고 **관리자가 승인해야 명함에 넣을 수 있다**. 등록된 활동 정보 값만 쓰면 바로 `approved`. 승인된 뒤 직접 입력 값을 바꾸면 다시 `pending`. 거절되면 `approvalReason`에 사유가 온다.
  - `verification`: `unverified` | `pending` | `verified` | `rejected`. 인증자료를 하나라도 올리면 `pending`, 관리자가 확인하면 `verified`. 앱은 `verified`가 아니면 **미확인 배지**를 붙인다. 미인증이어도 명함에 넣을 수 있다.
- 검색(로그인, 앱 선택 창용): `GET /v1/organizations?q&parentId&rootsOnly&cursor&size`, `GET /v1/programs?q&organizationId&cursor&size`, `GET /v1/roles?q&cursor&size`, 활동은 기존 `GET /v1/activities`. 응답은 admin과 같은 keyset 페이지(`{items, nextCursor}`), 항목은 `{id, name, nameEn?}`(조직은 `parentOrganizationId`, 프로그램은 `organization` 포함).

### 인증자료 (Evidence)

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| POST /v1/profile/histories/:id/evidence | Bearer | multipart `file` | 201 Evidence. 이력당 3개, 파일당 5MB, `image/jpeg`·`image/png`·`image/webp`·`application/pdf`만 |
| GET /v1/profile/histories/:id/evidence/:eid | Bearer(소유자) | — | 200 파일 |
| DELETE /v1/profile/histories/:id/evidence/:eid | Bearer | — | 204 |

- Evidence: `{id, fileName, contentType, size, uploadedAt}`. 파일은 DB에 둔다(bytea). 명함에는 실리지 않는다.
- 올리면 `verification`이 `pending`이 된다(이미 `verified`면 유지). 건너뛰고 나중에 올릴 수 있다. 다 지워도 `verified`는 유지된다.

### 프로필 사진 — 2026-10-26 사용자 결정

사용자: 프로필 사진을 올릴 수 있게 한다. 보이는 곳은 내 프로필과 명함(명함 면·웹 `/cards/:id`). 명함을 만들 때 "사진 넣기"를 고른다(기본 켬). 자르기는 사용자가 위치·크기를 직접 조절하고 앱이 정사각형으로 잘라 올리며, 화면에는 원형으로 보인다. 선택 항목이며 처리방침에 "프로필 사진(선택)"을 적어야 한다.

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| PUT /v1/profile/photo | Bearer | multipart `file` | 200 `{photoUrl}`. 있으면 바꾼다. `image/jpeg`·`image/png`만, 5MB, 한 변 4096px까지. 어기거나 읽을 수 없으면 400 |
| GET /v1/profile/photo | Bearer(본인) | — | 200 `image/jpeg`, `Cache-Control: private, max-age=31536000, immutable`. 없으면 404 |
| DELETE /v1/profile/photo | Bearer | — | 204. 없어도 204 |

- **앱이 올리는 것**: 사용자가 위치·크기를 고른 **정사각형** 크롭을 회전(EXIF 방향)이 적용된 픽셀 그대로, 한 변 1024px 이하로 줄여 JPEG(또는 PNG)로 올린다. HEIC·WebP는 서버가 읽지 못하므로 앱이 JPEG로 바꿔 올린다(iOS 사진 앱 기본 HEIC). 서버는 EXIF 방향을 읽지 않는다.
- **서버가 하는 것**: 파일 종류(요청 `Content-Type`)·크기를 확인하고 JDK ImageIO로 실제로 풀어 본다(헤더만 맞고 내용이 이미지가 아니거나 CMYK JPEG처럼 읽지 못하면 400). 크기를 먼저 읽어 한 변 4096px을 넘으면 풀지 않고 400. 정사각형이 아니면 가운데 정사각형으로 자른다. 한 변 512px로 줄이고(더 작으면 키우지 않는다) 흰 바탕(투명 PNG)에 JPEG(품질 0.85)로 **다시 인코딩**한다. 픽셀만 새로 쓰므로 EXIF(위치·기기)·ICC 등 메타데이터는 남지 않는다. 원본은 저장하지 않는다.
- **저장**: V58 `profile_photos(user_id pk → users on delete cascade, data bytea, updated_at timestamptz)`. 인증자료처럼 DB에 두고 사용자당 하나다. 프로필 버전(`profile_versions`)·버전 되돌리기와 무관하다.
- **`photoUrl`**: `GET`·`PUT /v1/profile`·`restore` 응답과 `PUT /v1/profile/photo` 응답에 `"/v1/profile/photo?v=<바뀐 시각 epoch ms>"` 또는 null. API 경로라 앱이 API base를 붙인다. 사진을 바꾸면 `v`가 바뀌므로 앱은 `v`가 같으면 캐시를 그대로 쓴다. 조회는 Bearer가 필요해 시스템 이미지 로더(`AsyncImage`·Coil 기본)로 바로 못 열고, 앱의 인증 HTTP 클라이언트로 받는다.
- **동의**: `PUT`·`DELETE`는 다른 프로필 쓰기처럼 현재 버전 필수 동의가 없으면 403 `CONSENT_REQUIRED`("개인정보 동의"). `GET`은 막지 않는다.
- **계정 삭제**: `users` cascade로 함께 지운다(아래 "계정 삭제").
- **명함에 싣기**:
  - 요청: `POST /v1/cards`·`PUT /v1/cards/:id`에 `includePhoto?: boolean`(생략하면 true). 제작 화면의 "사진 넣기" 스위치(기본 켬, 프로필 사진이 없으면 스위치 대신 "프로필에서 사진을 넣을 수 있어요"). 프로필 사진이 없으면 true여도 사진 없는 명함이 된다.
  - **발행 시점 복사**: 명함은 발행 시점 스냅샷이므로 프로필 사진을 참조하지 않고 그때의 바이트를 명함 쪽에 복사한다. V59 `business_card_photos(card_id pk → business_cards on delete cascade, data bytea)`와 `business_cards.photo_updated_at`(복사한 프로필 사진의 `updated_at`, 없으면 null). 프로필 사진을 바꾸거나 지워도 이미 낸 명함의 사진은 그대로이고, 바꾸려면 명함을 고치거나(`PUT /v1/cards/:id`가 그때 프로필 사진으로 다시 복사, `includePhoto=false`면 비움) 지운다. 앱은 프로필 사진을 지울 때 "이미 만든 명함의 사진은 명함을 고치거나 지워야 바뀌어요" 안내를 둔다(사용자 확인 필요한 결과).
  - 응답: Card(내 명함 목록·발행·고치기·공개 조회·지갑·게스트 목록)에 `photoUrl: "/v1/cards/:id/photo?v=<ms>" | null`. `GET /v1/cards/:id/photo`는 `GET /v1/cards/:id`와 같은 조회 권한(주인·받은 명함·게스트 저장·유효한 `?code=`, "명함 QR 코드")이고 실패도 같은 403·404다(사진이 없으면 404). `image/jpeg`, `Cache-Control: private, max-age=31536000, immutable`.
  - 명함 지우기·어드민 내리기는 내용과 함께 사진도 바로 지운다(위 "명함 지우기", V56 규칙과 같다).
  - 웹 `/cards/:id`: 로드 뒤 주소에서 `code`를 지우므로 `<img>`가 코드를 실을 수 없다. 웹 프록시(`/api/cards/:id`)가 명함을 조회할 때 `photoUrl`이 있으면 사진도 같은 권한(코드·게스트 쿠키)으로 받아 응답에 `photo: "data:image/jpeg;base64,…"`로 넣고, 카드 면은 이니셜 원 대신 원형 사진을 그린다. 사진을 못 받으면 이니셜 그대로. `/saved` 목록 행은 이니셜 그대로다. 오픈그래프에는 넣지 않는다("오픈그래프" — 이름·사진 미포함 그대로).
  - 신고 스냅샷: 사칭·부적절 신고의 근거라 신고 시점 명함 사진도 V59 `card_report_photos(report_id pk → card_reports on delete cascade, data bytea)`에 복사해 둔다(`card_snapshot`과 같은 수명: 신고일 1개월 뒤 삭제, 내 명함에 대한 신고는 계정 삭제 때 삭제). 24시간 안 재신고(기존 신고 200)는 다시 복사하지 않는다. 어드민 `GET /v1/admin/card-reports/:id/photo`(ADMIN, `image/jpeg`, `no-store`, 없으면 404).

### 명함·지갑

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| GET /v1/cards | Bearer | — | 200 `{items: Card[]}` 철회 안 한 내 명함, 만든 순 |
| POST /v1/cards | Bearer | `{name, description, contactIds[], historyIds[], includePhoto?}` | 201 Card. 내 프로필의 id가 아니거나 `approval`이 `approved`가 아닌 이력이면 400. 옛 앱이 보내는 `template`은 받기만 하고 무시한다(아래 "템플릿") |
| GET /v1/cards/:id | 없음 | — | 200 Card. 없거나 철회면 404 |
| DELETE /v1/cards/:id | Bearer(소유자) | — | 204 철회(지우기, 아래 "명함 지우기"). 남의 것 403 |
| GET /v1/wallet | Bearer | — | 200 `{items: Receipt[]}`. Receipt 형태와 저장·맞교환 경로는 아래 "명함 교환" |

- Card: `{id, ownerId, name, description, template, introduction, introLink, profileName, job, contacts: Contact[], histories: History[], photoUrl, createdAt}`. 발행 시점 스냅샷이며 프로필을 고쳐도 바뀌지 않는다. `introLink`는 발행(고치기) 시점 프로필 소개 링크의 복사본(`includeIntroLink: false`면 null), `introduction`은 **폐지 예정**(2026-10-26, 늘 빈 문자열; 아래 "소개 링크·소개 글 폐지"). `template`은 **폐지 예정**(2026-10-26): 옛 앱 호환으로 주인 조회에만 남고 새 스냅샷은 빈 문자열이다. 이력에는 `evidence`가 없고 `verification`만 남는다. 공개 조회(`GET /v1/cards/:id`)에는 `template`을 싣지 않는다. `photoUrl`(`includePhoto`)은 위 "프로필 사진".
- **명함 지우기**(2026-10-25 사용자 결정): `DELETE /v1/cards/:id`(어드민 내리기 `POST /v1/admin/cards/:id/withdraw`도 같다)는 철회와 함께 서버에서 그 명함의 내용(`name`·`description`·`template`·`introduction`·`introLink`·`profileName`·`job`·`contacts`·`histories`)을 바로 비운다. 행과 `id`·`ownerId`·`createdAt`·`updatedAt`은 남는다(QR 코드·받은 명함·비로그인 저장·신고가 이 id를 가리킨다). 지운 명함은 지금처럼 모든 조회에서 404·목록 제외라 빈 값이 화면에 나가지 않는다. 신고 시점 스냅샷(`card_reports.card_snapshot`)은 신고 근거라 그대로 두고 신고일로부터 1개월 뒤 지운다("명함 신고·차단"). V56이 이미 지운 명함의 내용도 비웠다. 이미 받은 사람 기기의 캐시는 원격으로 지우지 못한다.
- Receipt: 아래 "명함 교환"(2026-10-19)의 형태. 새 Receipt는 `PUT /v1/wallet/cards/:cardId`와 `reciprocate`가 만든다.
- 웹 비로그인 저장(`/v1/guest/*`, 홈 화면 세션 잇기)은 2026-10-17 Kotlin API에 다시 구현했다(위 "웹 비로그인 명함 저장"·"홈 화면 세션 잇기" 계약 그대로; `GET /v1/guest/cards`의 항목 형태와 `activityId`는 아래 "명함 교환"). 오류 본문은 다른 경로와 같은 ProblemDetail이며 웹 프록시는 상태 코드만 옮긴다(401 `GUEST_SESSION_INVALID`, 그 외 `REQUEST_FAILED`). API의 `GUEST_PROXY_SECRET`이 비어 있으면 `/v1/guest/*`만 503이다.
- 아직 없는 것: `POST /v1/wallet/import`, `POST /v1/exchanges`. 앱이 쓰지 않아 만들지 않는다.

### 템플릿

**2026-10-26 폐지(사용자 결정).** 템플릿·토큰 채우기는 없다. 같은 날 명함 본문(`introduction`, 프로필 자기소개 복사)도 폐지되었다(아래 "소개 링크·소개 글 폐지") — 아래 본문 관련 줄은 그 전까지의 규칙이다.

- 앱은 명함 제작에서 이름·공개 연락처·이력만 고르고 `template`을 보내지 않는다. 제작 화면은 자기소개를 읽기 전용 미리보기로 보인다. 이름·직무·연락처는 카드 면(아래 "명함 표현")이 따로 그린다.
- 서버는 `POST`·`PUT /v1/cards`의 `template`을 선택값으로 받고 **무시**한다(이미 배포된 옛 앱이 보내므로 400을 내지 않는다). 응답 Card의 `template`(주인 조회만)은 옛 앱 호환으로 당분간 남기며 새 스냅샷은 빈 문자열이다. DB 열 `business_cards.template`은 이번에 지우지 않는다.
- 발행한 명함은 바뀌지 않는다. 명함을 고치면 앱이 같은 명함을 `PUT /v1/cards/:id`로 수정하고(2026-10-20; 전에는 새 명함을 발행했고) 이전 명함은 사용자가 지울 때까지 남는다(공개 링크 `/cards/:id` 유지). 고치면 본문도 그때의 자기소개로 바뀐다.
- 마크다운 부분집합(제목·굵게·기울임·목록·링크, 원시 HTML 금지, `https`·`mailto`·`tel` 링크만)으로 렌더한다. 웹 `/cards/:id`는 모든 폭에서 `introduction`만 보인다.
- 변경 이력: 2026-10-18 실시간 꾸미기 편집기와 웹 버전(`webTemplate`·`webIntroduction`, V20)을 같은 날 추가·삭제(V21로 열 삭제, [`cb456363`](https://github.com/fixabley/dearby/blob/cb456363/shared/contracts/native-v1.md#템플릿-명함-꾸미기), 사용자 "실시간 편집 이런 퀄리티면 그냥 빼는 게 좋을 것 같아") · 2026-10-18 기본 템플릿을 `# 이름 / 직무 / 소개 / - 라벨: 연락처`에서 `{{profile.introduction}}`만으로 바꿈(사용자 "이미 제공되는 정보인데 명함에 텍스트로 표시되는 거 지워줘"). 옛 기본 템플릿으로 발행된 명함은 V27·V28이 같은 모양으로 정리했고(V28은 `# `·빈 줄이 없던 더 이른 기본 템플릿 포함), 토큰만으로 된 기본 템플릿 모양이 아닌 옛 명함은 그대로다. · 2026-10-26 템플릿 폐지(사용자 결정): 앱 내장 템플릿 `{{profile.introduction}}` 하나뿐이라 본문을 자기소개 복사로 바꾸고 앱·서버의 템플릿 코드를 지움. 계기는 Android 앱 템플릿 정규식(`}}` 미이스케이프)이 Android 17 ICU에서 `PatternSyntaxException`으로 명함 만들기를 열자마자 앱을 끝낸 일. 폐지 전 토큰 규칙은 [`2c01befa`](https://github.com/fixabley/dearby/blob/2c01befa/shared/contracts/native-v1.md#템플릿).

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
  - 2026-10-17 사용자 결정(iOS·Android, 서버 변경 없음): 202 직후 앱이 먼저 이 기기의 Dearby 패스키만 조용히 묻는다(iOS `preferImmediatelyAvailableCredentials`, Android `setPreferImmediatelyAvailableCredentials`). 있으면 그 패스키로 로그인해 바로 연결하고, 없으면 묻지 않고 새 계정으로 시작한다. 사용자가 패스키 프롬프트를 닫았을 때만 위 선택 화면을 보여 준다. 웹은 조용히 확인할 수단이 없어 그대로 묻는다.
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
- 웹: 2026-10-26 사용자 결정으로 웹에도 로그인을 만든다. Apple JS(Services ID, popup)로 받은 `id_token`을 `POST /v1/auth/apple`에 보낸다(아래 "웹 로그인"). 그 전까지는 사용자 웹에 로그인해서 쓰는 기능이 없었다.
- 애플 토큰
  - iOS는 credential의 `authorizationCode`를 함께 보낸다. 웹 방식은 콜백의 `code`를 서버가 쓴다.
  - 서버는 `.p8` 키로 서명한 client secret으로 code를 애플 refresh token으로 바꿔 `user_identities`에 둔다. 아래 "계정 삭제" 때 폐기하기 위해서다.
  - 키가 설정되지 않았거나 교환이 실패해도 로그인은 막지 않는다. 다시 로그인하면 새 토큰으로 바꾼다.
- **로그인 고지(2026-10-25 사용자 결정, 구글 로그인에도 적용)**: 로그인 화면(로그인 시트·시작 화면)의 Apple·Google 버튼 바로 아래에 작은 보조 글씨 한 줄 `로그인하면 계정 식별을 위해 Apple·Google 계정 정보(이메일 등)를 개인정보처리방침에 따라 처리해요.`를 둔다(`개인정보처리방침`은 `https://dearby.wid.io.kr/privacy` 링크). 별도 동의 단계는 아니다(아래 "광고 목적 개인정보 제3자 제공 동의(선택)"의 "동의 시점 원칙(JIT) 점검"). iOS 로그인 시트는 같은 날 본문 큰 제목·설명을 없애고(내비게이션 제목 `로그인`과 락업은 유지) `패스키로 로그인`을 고지 아래 보조(텍스트) 버튼으로 낮춘다.

## 계정 삭제 — 2026-10-16 사용자 결정

App Store 심사 기준 5.1.1(v)에 따라 앱 안에서 계정을 지울 수 있어야 한다.

| 메서드·경로 | 인증 | 응답 |
| --- | --- | --- |
| DELETE /v1/me | Bearer | 204. 없는 토큰·무효 토큰은 401 |

- 되돌릴 수 없다. 앱은 내 프로필 탭 맨 아래 "계정 삭제"에서 확인 대화상자("계정을 삭제할까요?" / "명함과 받은 명함 등 모든 정보가 지워지고 되돌릴 수 없어요.")를 거쳐 부른다. 성공하면 저장한 토큰과 메모리의 계정 데이터를 지우고 로그아웃 상태로 돌아간다. 401 뒤 refresh도 거절되면 로그아웃된 것으로 본다.
- 지우는 것
  - 사용자, 역할, 연락처, 프로필, 프로필 사진(2026-10-26), 명함과 명함 하위 행, 활동 이력, 참여 기록, 기관 멤버·프로그램 스태프 행
  - refresh token. 이미 발급한 access token은 수명(1분)이 지나면 끝난다.
  - 패스키 user entity와 credential. 기기에 남은 패스키는 앱이 지울 수 없어 안내만 한다(iOS "설정 > 암호", Android "비밀번호 관리자").
  - 외부 로그인 연결
- 애플 계정이 연결돼 있으면 저장한 애플 refresh token을 먼저 폐기한다(`appleid.apple.com/auth/revoke`). 토큰이 없거나 폐기가 실패해도 삭제는 진행하고 서버 로그에 남긴다.

## 활동 조회 — 2026-10-14 사용자 결정

`GET /v1/catalog`(전체 카탈로그 한 번에)를 없애고 `GET /v1/activities`로 바꾼다. 카탈로그 원본은 Kotlin DB다. 앱·웹은 아직 `/v1/catalog`를 부르므로 이 계약에 맞춰 바꿔야 한다.

| 메서드·경로 | 인증 | 요청 | 성공 응답 |
| --- | --- | --- | --- |
| GET /v1/activities | 없음 | query `q?`, `organizationId?`, `programId?`, `cursor?`, `size?`(1–100, 기본 20) | 200 `{items: Activity[], nextCursor}` |
| GET /v1/activities/:id | 없음 | 숫자 id | 200 Activity(목록 항목과 같은 형태). 없거나 소속 프로그램·기관이 없으면 404, 숫자가 아니면 400 |

- 게시된(`PUBLISHED`) 활동만 보인다. 수집 배치가 넣은 초안(`DRAFT`)은 관리자가 게시하기 전까지 목록·검색·단건 조회에 나오지 않는다(2026-10-15).
- 순서는 활동 id 오름차순이다. 다음 페이지는 `nextCursor`를 `cursor`로 보낸다. `nextCursor`가 null이면 마지막 페이지다. `size`가 범위를 벗어나면 400.
- 언어(2026-10-15 사용자 결정): 요청 헤더 `Accept-Language`에서 지원 언어(`ko`, `en`) 중 가장 선호하는 것으로 `name`·`description`·`organization.name`·`program.name`을 하나씩 준다. 영문 값이 없는 항목은 한글로 채운다. 헤더가 없거나 읽을 수 없거나 지원 언어가 없으면 한국어. 응답에 `Content-Language`(`ko`·`en`)와 `Vary: Accept-Language`를 붙인다. 앱은 기기 언어 순서(iOS `Locale.preferredLanguages`, Android `LocaleList`, 최대 5개, 순서대로 q 값)를, 웹 프록시는 브라우저의 `Accept-Language`를 활동 조회에만 그대로 보낸다(언어 목록 형식이 아니면 버린다). 앱 화면 자체는 아직 한국어다.
- `q`는 활동 이름·설명(한글·영문 모두)에서 찾는다. 대소문자 없는 부분 일치(`%`·`_`도 글자 그대로) 또는 단어 유사도(PostgreSQL `pg_trgm` `word_similarity` 0.4 이상)면 결과에 넣는다. 그래서 오타("워크샵"→"워크숍")·띄어쓰기 차이("개발환경")도 찾는다. 순서는 여전히 id 순이다(유사도 순 정렬은 하지 않음).
- id는 숫자(bigint)다. 기존 카탈로그 UUID는 쓰지 않는다.
- Activity: `{id, name, description, organization{id,name}, program{id,name}, officialUrl, applicationUrl, imageUrl, application, activitySchedule, schedules[], eligibility}`. 2026-10-14 사용자 결정으로 원래 도메인 필드에 공식 안내 링크(`officialUrl`)와 이미지(`imageUrl`)만 더한다. 2026-10-18 `eligibility`(대상자 조건 JSON, 없으면 null)를 더한다(아래 "대상자 조건"). 2026-10-19 `applicationUrl`(신청 링크 `activities.application_url`, 없으면 null)을 더한다(아래 "신청 확인 조건 완화·공개 applicationUrl"). 모집 상태·참여 방식·확인 시각·장소·비용·대상·자격요건(JSON)·역할·안내 문구는 같은 `activities` 행(+`activity_roles`)에 있지만 운영자용 도메인 `ActivityCuration`이 다루고 공개 API는 내보내지 않는다. 같은 DB를 쓰는 운영자(admin) 모듈용이다.
  - 모집 여부는 `application`(모집 기간: 시작·끝 중 아는 쪽)으로 판단한다. 신청은 `applicationUrl ?? officialUrl`을 연다(2026-10-19 밤 "신청 확인 조건 완화").
  - 한 항목만으로 목록·상세를 그릴 수 있어 기관·프로그램을 따로 조회하지 않는다.
  - `application`(모집 기간, 없으면 null), `activitySchedule`(활동 기간·날짜 문구), `schedules[]`(개별 일정)는 Schedule이다.
- Schedule: `{id, title, kind, status, startAt, endAt, timeZone, startDate, endDateExclusive, dateLabel, address, conferenceUrl}`(2026-10-26 `hasTimetable` 추가, 아래 "트랙·세션 시간표"; 2026-10-26 `phase: "SELECTION"|"ACTIVITY"` 추가, 아래 "선발 과정·활동 일정 분리"). 시각 일정은 `startAt`·`endAt` 중 하나 이상과 `timeZone`이 있고, 시각은 그 시간대 오프셋으로 표기한다(예: `2026-10-08T00:00:00+09:00`). 종일 일정은 `startDate`·`endDateExclusive`. 날짜 미정은 모두 null이고 `dateLabel`만 있다.

## 태그(얻을 수 있는 것) — 2026-10-19 사용자 결정

사용자: "프로그램이랑 활동에 태그가 달렸으면 좋겠어. 창업, 창의성, 개발지식, 프로젝트 개발, 협업, 멘토링처럼 사용자가 얻을 수 있는 요소들을 표현하게 해서 검색 결과에 같이 노출". 종류(category)가 "무엇인가"라면 태그는 "무엇을 얻는가"다.

- **어휘는 통제한다**: 자유 입력이 아니라 운영자가 관리하는 태그 사전 `tags(id, name 한글, name_en 영문 nullable, sort_order)`(V34). 사전은 어드민 편집 폼에서 **검색해 고르거나 바로 새 이름을 적어 추가**할 수 있다(2026-10-19 사용자 "편집시 직접 검색 및 직접입력"): 태그 선택은 검색형 다중 선택이고, 사전에 없는 이름을 적으면 저장 때 `POST /v1/admin/tags {name}`으로 만들어 연결한다(영문 이름은 비워 두고 뒤에 채운다, 띄어쓰기만 다른 같은 이름은 기존 태그로 합친다). 수집기는 영문 이름이 비어 있어도 한글 이름으로 고른다. 초기 사전: 창업 startup, 창의성 creativity, 개발지식 engineering, 프로젝트 개발 projectBuilding, 협업 collaboration, 멘토링 mentoring, 네트워킹 networking, 취업 career, 포트폴리오 portfolio, 발표 presentation, 글로벌 global, 장학·지원금 funding, 리더십 leadership, 문제해결 problemSolving. 어드민 `GET/POST/PATCH/DELETE /v1/admin/tags`(이름 변경은 연결 유지, 삭제는 연결도 지움). 공개 `GET /v1/tags` → `{items: Tag[]}`, Tag = `{id, name, nameEn}`.
- **연결**: `program_tags(program_id, tag_id)`, `activity_tags(activity_id, tag_id)`. 프로그램 태그는 운영자가 지정하고(`PATCH /v1/admin/programs/:id {tagIds, applyTagsToActivities?}`, 종류와 같은 일괄 규칙: 운영자가 태그를 지정하지 않은 활동에 합집합으로 더한다), 활동 태그는 수집기가 사전에서 고르고(지시문에 사전을 넘기고 사전 밖 이름은 버린다) 운영자가 바꾼다(`PATCH /v1/admin/activities/:id {tagIds}`, `POST`도 `tagIds?`, 운영자 지정은 `curated_fields`의 `tags`로 보호). 수집 결과 태그가 비면 프로그램 태그를 기본으로 쓴다(종류와 같다).
- **노출**: `Activity.tags: Tag[]`, `AdminActivity.tags`, `ProgramSummary.tags: Tag[]`(프로그램 태그가 있으면 그것, 없으면 **그 프로그램의 게시 활동 전체(최근 3년 창)** 태그의 합집합 — 2026-10-20 사용자 "활동 탭에서 프로그램에 태그가 안 보여" 지적으로 "열린 활동만"에서 넓힘; 열린 활동이 없는 프로그램(우아콘처럼 마감된 행사)도 태그가 보여야 한다 — 최대 6개, 사전 순). 발견 프로그램 행에 태그 칩 최대 3개 + `+n`, 펼친 활동 행에 태그 칩, 활동 상세에 전체. 어드민 활동·프로그램 행과 편집 폼에 태그 선택(검색 + 직접 입력으로 새 태그 추가, 다중).
- **검색**: `GET /v1/activities`·`GET /v1/discovery/programs`의 `q`가 활동 이름 외에 **태그 이름(한글·영문)** 에도 맞는다(`matchedActivityIds`에 포함). 필터 `tagIds?`(쉼표 목록, 모두 포함 AND). 발견 탭 검색창 아래 태그 칩 줄은 2026-10-19 밤 사용자 결정으로 없앴고(위 "프로그램 로고·태그 칩 줄 제거" 절) 태그는 `q` 검색으로만 쓴다. `tagIds`는 API에 남는다.
- 표기: 코드 없이 사전 id를 쓰고 이름은 한글+영문 쌍(2026-10-18 어휘 규칙). JSON 키는 camelCase.

## 개인정보 동의 — 2026-10-19 사용자 결정

사용자: "프로필 입력하기 전에 개인정보동의 필수로 구할 수 있게". 프로필(이름·연락처·이력)을 처음 저장하기 전에 **개인정보 수집·이용 동의**와 **이용약관 동의**를 필수로 받고 서버에 기록한다.

- 버전: 서버 설정 `dearby.consent.privacy-version`·`terms-version`(웹 `/privacy`·`/terms` 시행일과 같다. 처음 `2026-10-19`, 2026-10-22부터 둘 다 `2026-10-22`, 2026-10-26부터 처리방침만 `2026-10-26`(사진·소개 링크·웹 사용 통계·광고 선택 동의 고지 — 약관은 2026-11-01 시행이라 `2026-10-22` 유지), 2026-10-28부터 처리방침 `2026-10-28`(연락처 인증·교환 세션·웹 로그인 쿠키, PR #439; iOS 앱은 `ConsentVersions.privacy`를 먼저 올렸고 서버 `CONSENT_PRIVACY_VERSION`은 테스터가 새 빌드를 받은 뒤 올린다 — 그 사이 새 앱의 동의는 400 `CONSENT_VERSION` 문장을 그대로 보이고, 이미 `2026-10-26`에 동의한 계정은 `consentRequired=false`라 다시 묻지 않는다)). 문서를 바꾸면 버전을 올리고, 버전이 다른 사용자는 다시 동의한다.
- 저장: V37 `user_consents(user_id pk → users on delete cascade, privacy_version, terms_version, consented_at timestamptz)`. 계정 삭제와 함께 지워진다.
- API: `POST /v1/me/consents {privacyVersion, termsVersion}`(Bearer) → 204. 현재 버전과 다르면 400 `CONSENT_VERSION`. `GET /v1/profile` 응답에 `consent: {privacyVersion, termsVersion, consentedAt}|null`과 `consentRequired: boolean`(기록이 없거나 버전이 다르면 true)을 더한다. **`PUT /v1/profile`은 `consentRequired`이면 403 `CONSENT_REQUIRED`**(이력 인증자료 업로드 등 프로필 하위 쓰기도 같다). 명함 발행·받은 명함·활동 신청 표시는 프로필이 있어야 하므로 자연히 뒤에 온다.
- 앱(iOS·Android): 첫 프로필 퍼널의 첫 단계 앞, 그리고 프로필 화면을 열었을 때 `consentRequired`이면 **동의 화면**을 전체 화면으로 보인다. 제목 "시작하기 전에 동의가 필요해요", 필수 체크 두 개(`개인정보 수집·이용에 동의합니다 (필수)`, `이용약관에 동의합니다 (필수)`), 각 항목의 `보기`는 `https://dearby.wid.io.kr/privacy`·`/terms`를 인앱 브라우저(iOS `SFSafariViewController`, Android Custom Tab)로 연다. 둘 다 체크해야 `동의하고 계속`이 켜지고, 누르면 `POST /v1/me/consents` 뒤 원래 흐름으로 간다. 동의 전에는 프로필 입력 단계로 갈 수 없다. 서버가 403 `CONSENT_REQUIRED`를 돌려주면 같은 화면을 보인다. 로그아웃 상태에서는 보이지 않는다(로그인 뒤 프로필에서).
- 웹: 프로필 입력이 없으므로 바뀌지 않는다(비로그인 저장 쿠키 안내는 `/privacy`에 이미 있다).

- 2026-10-20: iOS는 동의 화면을 프로필 탭 위의 **시트**로 띄우고 닫을 수 있다(닫으면 미동의, 저장하면 다시 뜸; "터치 영역·검색 경험·시트 닫기" 절). Android는 전체 화면이되 뒤로 가기로 나간다.
- 2026-10-25 사용자 결정(버그 보고 "약관 동의하지 않고 뒤로가기 눌렀을 때 프로필 생성 퍼널로 넘어가" 뒤): 동의는 프로필 탭이 아니라 **앱 전체**에서 묻는다. 로그인됨 + 현재 버전 필수 동의 없음이면 (1) 앱을 시작할 때·포그라운드로 돌아올 때마다 동의 화면(iOS 앱 루트 시트, Android 전체 화면)을 다시 띄우고, (2) 계정 데이터를 쓰는 기능(프로필·명함 탭은 "동의가 필요해요 / 동의하기" 안내로 대신, 즐겨찾기 하트·알림 목록·맞춤 대기열·추천 설정·지원 자격 정보·명함 만들기·받은 명함 저장 링크)은 그 기능 대신 동의 화면을 띄운다. 발견 둘러보기·활동 상세·내 활동(기기 저장)은 그대로 쓴다. 동의 없이 닫거나 뒤로 가면 **로그인을 유지**하고 다음에 다시 묻는다. 동의하면 하던 곳으로 이어지고, 가입 직후(프로필 버전 0)면 프로필 만들기로 간다. 동의 전에는 앱이 계정 데이터를 서버로 보내거나 동기화하지 않는다(내 활동 서버 저장·즐겨찾기·알림 목록·푸시 토큰·추천 설정·대기열 신호, 사용 통계 `signed_in` 속성은 동의 뒤 `yes`). 동의 화면의 `동의하고 계속` 아래 보조 버튼: 가입 직후 계정은 **`가입 취소`**(확인 "가입을 취소할까요? / 가입을 취소하면 방금 만든 계정이 삭제돼요." 뒤 기존 계정 삭제 `DELETE /v1/me`, Apple 토큰 해지 등 서버 삭제 흐름 그대로, 로그아웃 상태로 그 화면에 남음), 이미 프로필이 있어 새 버전 재동의를 하는 계정은 **`로그아웃`**(계정 삭제는 설정에서만). 서버가 동의를 강제하는 쓰기는 지금 프로필과 그 하위(`PUT /v1/profile`·되돌리기·인증자료 올리기·지우기)뿐이고, 내 활동·즐겨찾기·푸시 기기·알림 설정·추천 설정·대기열 신호는 앱만 막는다(서버 보강은 조율 세션 판단).
- 2026-10-26: 이전 버전에 동의한 계정(`GET /v1/profile`의 `consent`가 null 아님)에 다시 물을 때는 동의 화면 제목을 `개인정보 처리방침이 바뀌었어요`로 쓴다(처음 동의는 기존 제목). 옛 버전으로 보낸 동의의 400 `CONSENT_VERSION` 문장은 `동의 문서 버전이 바뀌었어요. 앱을 업데이트한 뒤 다시 동의해 주세요.`(옛 앱이 이 문장을 그대로 보인다).

## 발견 카드 리뉴얼 — 2026-10-19 사용자 결정

사용자: "활동탐색 UI 리뉴얼. One UI 최신 **알림 UI** 애니메이션을 참고해 카드를 리뉴얼. 로고 좌측, 프로그램 이름 상단, 하위 활동 개수 우측, 태그는 프로그램 아래. 누르면 자연스럽게 펼쳐지고, 펼친 뒤 우측에 숫자 대신 화살표 버튼, 누르면 접힘. 활동 카드에는 로고 없이 활동명·모집 일정·활동 일정·활동 장소·모집 중 여부를 우상단에." 시안(https://claude.ai/artifact/TbCWAzRadSgpKm6tp88qE9) 승인("매우 좋아요") + "모집 기간은 지난 게 아니면 강조".

- **프로그램 카드(접힘)**: 둥근 흰 카드(모서리 20, 그림자 없음, 1px 구분선). 왼쪽 **로고 자리** 44: 프로그램 로고가 있으면 그것, 없으면 프로그램 이름 첫 글자의 둥근 아바타(민트 바탕·청록 글자). 프로그램 로고는 `ProgramSummary.logoUrl`(아래 "프로그램 로고" 절, 2026-10-19 밤). 가운데 맨 위 **종류 줄**(2026-10-26 사용자 결정, 아래 "종류 아이콘 라벨") → **프로그램 이름**(굵게) → 조직 이름(보조색) → **태그 칩**(최대 3개 + `+n`, 없으면 줄 생략). 오른쪽 위 **활동 개수 배지**: 모집 중 + 모집 예정 수(청록 원형). 그 아래 보조 글자 한 줄: 모집 중이 있으면 `M월 d일 마감`(모집 마감이 없는 열린 활동뿐이면 `nearestRecruitmentEndAt`이 null; 2026-10-26 오전의 `nearestIsActivityStart` → `M월 d일 행사` 분기(2026-10-26 실기기 점검 "모집 미정인데 행사 날짜를 마감으로 표시")는 같은 날 "모집 일정 없는 활동은 마감"으로 없앴고 서버가 항상 false라 쓰이지 않는다), 예정만 있으면 `M월 d일 시작`, 둘 다 없으면 `최근 yyyy.M`.
- **펼치기**: 카드 전체를 누르면 펼쳐진다. 펼쳐지면 개수 배지 자리가 **위쪽 화살표 버튼**(44 터치 영역, 접근성 이름 `접기`)으로 바뀌고 누르면 접힌다(카드 머리를 다시 눌러도 접힌다). 애니메이션은 One UI 알림 전개 느낌: 높이는 스프링(Android `animateContentSize(spring(dampingRatio 0.8, stiffness MediumLow))`, iOS `.spring(response 0.35, dampingFraction 0.85)`), 내용은 짧게 페이드 + 위에서 살짝 밀려 들어옴(`expandVertically + fadeIn` / `.opacity + .move(edge: .top)`), 화살표 180° 회전. 동작 줄이기에서는 즉시 전환. 웹은 `grid-template-rows 0fr→1fr` + opacity 전환(320ms, 약간 오버슈트하는 베지어), 동작 줄이기에서 전환 없음.
- **활동 카드(펼친 안)**: 로고 없음. 왼쪽 위 **활동명**(굵게), 오른쪽 위 **모집 상태 칩**(`모집 중` 청록 채움 / `모집 예정` 민트 / `마감` 회색). 아래 라벨 + 값 세 줄: `모집` 모집 기간 문구(없으면 `미정`; 기간과 D-day 배지는 아래 "모집 날짜 표기 — 2026-10-26"), `활동` 활동 기간 문구, `장소` 장소 이름(없으면 줄 생략). **강조 규칙**: 모집이 지나지 않았으면(모집 중·모집 예정) `모집` 줄의 값을 청록 굵은 글씨로 쓰고 줄 전체에 옅은 민트 바탕(모서리 8)을 깐다; 지난 모집은 보조색 그대로. 종류 소제목과 `모집 중`/`모집 예정`/`지난 활동` 묶음 규칙은 그대로(소제목은 작은 보조 글자). 카드를 누르면 활동 상세.
- **API**: 공개 `Activity`에 `location: string|null`(기존 문구)과 `place: {name, address, lat, lng}|null`(아래 "활동 장소")을 더한다.
- 색·글꼴은 기존 토큰. 접근성: 프로그램 카드는 `펼침/접힘` 상태를 가진 버튼, 활동 카드는 상태 칩을 라벨에 포함.
- **종류 아이콘 라벨(2026-10-26 사용자 결정)**: 사용자 "각 프로그램마다 부트캠프 컨퍼런스 같은 것도 아이콘라벨로 해서 카드 좌상단에 추가해줄 수 있을까?". 접힌 프로그램 카드(발견·즐겨찾기·프로그램 상세 머리·알림 목록 머리 — 같은 머리를 쓰는 곳 전부)의 이름 위 작은 한 줄에 `ProgramSummary.category`(프로그램 기본 종류)를 **아이콘 + 종류 이름**(예: 부트캠프)으로 둔다. 캡슐·바탕 없이 caption 보조색(iOS `Label(이름, systemImage:)` `.font(.dearby(.caption))` `.secondary`, Android `labelSmall`/`onSurfaceVariant`, 웹 `text-caption text-secondary-label` + `Icon`). 캡슐로 두지 않는 이유: 바로 아래 태그 칩이 캡슐이라 같은 모양이면 종류가 태그처럼 읽히고 카드 높이도 더 는다. 아이콘은 "아이콘 통일"의 의미별 표의 활동 종류 항목. `category`가 null이거나 모르는 코드면 줄을 그리지 않는다(`미분류`라고 쓰지 않는다). 접근성: 카드 라벨의 맨 앞에 종류 이름("부트캠프, SSAFY, …"). 활동 상세의 종류 칩도 같은 아이콘을 붙인다. 펼친 활동 행에는 넣지 않는다(종류 소제목으로 이미 묶인다).

## 알림 목록(발견 우상단 종) — 2026-10-25 사용자 결정

사용자: "알림도 우상단에 있는데, 눌렀을 때 알림 목록 화면으로 넘어가고, 되도록 프로그램 활동 목록과 비슷한 느낌을 받았으면 좋겠어요."

- **진입**: 발견 우상단 종 버튼 → 알림 목록 화면(내비게이션 푸시). 읽지 않은 알림이 있으면 종에 숫자 배지(9 넘으면 `9+`; iOS `.badge`, Android `BadgedBox`). 비로그인에서는 로그인 시트(위 "비로그인에서 만들기·추천을 막음").
- **화면 모양(발견 프로그램 목록과 같은 느낌)**: 알림을 **프로그램 카드로 묶는다** — 카드 머리는 발견 프로그램 카드와 같은 로고·프로그램 이름·조직 이름, 오른쪽 위에 그 프로그램의 읽지 않은 수 배지(발견의 개수 배지 자리·모양 그대로). 카드를 누르면 발견처럼 펼쳐지고, 안쪽 행은 발견 활동 행 모양: 활동 이름(굵게) + 알림 종류 칩(`새 모집`·`모집 시작`·`마감 D-1`·`오늘 마감`·`발표`·`면접`·`행사`·`활동 시작`) + 받은 시각(`오늘 08:00`·`어제`·`10월 21일`) + 읽지 않은 행 왼쪽 청록 점. 행을 누르면 활동 상세(그 알림 읽음). 카드 순서는 가장 최근 알림 순, 최근 7일은 `이번 주`, 그 전은 `지난 알림` 소제목 아래. 위 오른쪽 `모두 읽음`. 비어 있으면 `즐겨찾기한 프로그램·활동의 소식이 여기에 모여요`. 자동 펼침 없음(기존 원칙).
- **알림 목록은 푸시와 별개로 쌓인다**: 서버가 매일 만든 알림 거리(위 "프로그램·활동별 알림 수준" — 신규·신청일·전체)를 사용자 시간대 08:00 기준으로 목록에 넣는다. 푸시 권한이 없거나 `새 모집 알림`을 꺼도 목록에는 쌓인다(알림 수준 `없음`인 항목만 빠진다). 같은 사용자·활동·종류·날짜는 하나. 90일 지난 알림은 지운다.
- **API**: `GET /v1/me/notifications?cursor=` → `{groups: [{program: ProgramSummary, unread, items: [{id, kind: NEW|APPLICATION_START|DEADLINE_EVE|DEADLINE|ANNOUNCEMENT|INTERVIEW|EVENT|ACTIVITY_START, activity: {id, name}, createdAt, readAt|null}]}], nextCursor}`(그룹은 최근 알림 순, 한 번에 20그룹), `GET /v1/me/notifications/unread-count` → `{count}`, `POST /v1/me/notifications/{id}/read`, `POST /v1/me/notifications/read-all`. 저장 `user_notifications(id, user_id, activity_id, program_id, kind, local_date, created_at, read_at)` (사용자 삭제 cascade). 푸시 발송은 이 행을 근거로 기기마다(위 기기별 중복 규칙) 보낸다. 푸시를 눌러 열면 그 알림을 읽음으로 한다(페이로드에 `notificationId` 추가).
- **서버 구현(2026-10-25)**: 배치가 매시 돌며 사용자 시간대(가장 최근에 올린 기기의 시간대, 기기가 없으면 `Asia/Seoul`)와 각 기기 시간대가 8시면 그 날짜의 거리를 쌓는다. `NEW`는 활동마다 한 번이고, 알림 수준 `전체`의 그 밖의 일정은 일정 종류(발표 `ANNOUNCEMENT`·면접 `INTERVIEW`·행사 `EVENT`, 신청 기간 밖의 마감 일정은 `DEADLINE`, 활동 기간 시작 `ACTIVITY_START`)로 남긴다. 푸시는 기기 시간대 8시에 그 기기 날짜의 행과 하루 안에 만든 `NEW` 중 그 기기로 아직 안 보낸 것을 보낸다(`push_deliveries(notification_id, device_token)`, V55). 묶음 알림(3개 초과)에는 `notificationId`가 없다. `POST …/{id}/read`는 이미 읽었으면 그대로 204, 내 알림이 아니면 404. `items`는 그 프로그램의 알림 전부(최근 순, 90일 안). 게시 활동이 하나도 남지 않은 프로그램의 묶음은 목록에서 빠진다(읽지 않은 수에는 남는다).
- 통계: `menu_open(menu=notifications_inbox)`, 행 누름 `activity_view(source=inbox)`(analytics-v1.md에 더한다).

## 즐겨찾기(하트)·새 모집 푸시 알림 — 2026-10-25 사용자 결정

사용자: "조직 프로그램 활동에 하트버튼을 두는 건 어떨까? 내 활동탭에서 상단탭바로 즐겨찾기에 추가해주는 식으로" → 하트 대상 "활동 + 프로그램"(조직은 조직 화면이 생기면), 새 모집 알림 "푸시 알림까지 지금".

- **하트(로그인만)**: 활동 상세 머리·발견의 펼친 활동 행·맞춤 대기열 오른쪽 ♥, 프로그램 카드(발견)·프로그램 상세 머리. 비로그인에서 누르면 로그인 시트(위 "비로그인에서 만들기·추천을 막음"). 채워진 하트 = 즐겨찾기됨, 접근성 이름 `즐겨찾기`/`즐겨찾기 해제`. iOS `heart`/`heart.fill`, Android `FavoriteBorder`/`Favorite`. 웹은 로그인이 없어 하트를 두지 않는다(2026-10-26 웹 로그인 2단계 A부터 웹도 하트 — 아래 "웹 화면 — 2단계 A").
- **`+` 내 활동에 추가(2026-10-25 사용자 "내활동에 추가하는 플로우도 상세보기 우상단에서 제외해주고 하트 옆에 + 아이콘 추가해줘")**: 활동 상세 내비게이션 우상단의 `내 활동에 추가` 동작을 없애고, 활동 하트가 있는 모든 자리(활동 상세 머리, 펼친 활동 행, 맞춤 대기열 오른쪽 줄)에서 하트 바로 옆에 `+` 버튼을 둔다(iOS `plus.circle`/추가됨 `checkmark.circle.fill`, Android `AddCircleOutline`/`CheckCircle`, 접근성 `내 활동에 추가`/`내 활동에 있음`). 누르면 기존 내 활동 추가 흐름(신청 확인 시트 등)을 그대로 연다. 이미 내 활동에 있으면 체크 아이콘이고 누르면 내 활동의 그 항목을 연다. 비로그인에서도 기존 규칙대로 기기 저장으로 동작한다(내 활동은 비로그인 허용). 프로그램에는 `+`를 두지 않는다. 하단 신청 CTA는 그대로.
- **저장·API**: `user_favorites(user_id, target_type ACTIVITY|PROGRAM, target_id, created_at)` PK(user_id, target_type, target_id), 사용자 삭제 cascade. `GET /v1/me/favorites` → `{programs: [{program: ProgramSummary, favoritedAt, newRecruitment: boolean}], activities: [{activity: Activity, program: ProgramSummary, favoritedAt}]}`(활동은 마감 임박 순, 지난 활동은 뒤). `PUT /v1/me/favorites/{programs|activities}/{id}`·`DELETE` 같은 경로(멱등, 204; 게시되지 않은 활동·없는 프로그램 404). 로그인 응답의 `Activity.favorited`·`ProgramSummary.favorited: boolean`(비로그인 false; 공개 `GET /v1/activities`·`/v1/activities/{id}`·`/v1/discovery/programs`는 Bearer가 오면 채우고 `Vary: Authorization`). 서버 구현(2026-10-25): 프로그램은 즐겨찾기한 최근 순, 게시 활동이 하나도 없는 프로그램과 지워졌거나 게시되지 않은 활동은 목록에서 뺀다(행은 남긴다). 즐겨찾기 활동의 `Activity`는 기존 모양 그대로라 `application`·`activitySchedule`·`schedules`로 캘린더를 그린다.
- **추천과 하나로**: 활동 하트 = 맞춤 대기열의 `LIKE` 신호(대기열 ♥도 즐겨찾기에 넣고, 즐겨찾기 해제 = `UNLIKE`). 프로그램 하트는 조직 친화도에 프로그램 신호 +0.6(반감기 같음)으로 더한다. 앱은 활동 하트(대기열 ♥ 포함)에 즐겨찾기 API만 부르고 `LIKE`/`UNLIKE` 신호를 따로 보내지 않는다: 서버가 `PUT …/activities/{id}`에서 `LIKE`를 남기고(관심 없어요는 지움, 이미 있으면 시각을 새로 하지 않음) `DELETE`에서 지운다(멱등). 예전 앱이 보내는 `LIKE`·`UNLIKE`·`DISMISS` 신호와 되돌리기도 즐겨찾기에 같이 반영한다.
- **내 활동 탭 상단 탭**: `내 활동 | 즐겨찾기` 두 칸(iOS 기본 세그먼트 `Picker(.segmented)`, Android Material 3 `PrimaryTabRow`). 즐겨찾기 화면은 아래 캘린더 보기 하나다(2026-10-26 목록 보기 폐지). 비어 있으면 `하트를 누르면 여기에 모여요`. 즐겨찾기 해제는 하트로 한다(목록과 함께 밀어서 해제·되돌리기 알림도 없앴다).
- **즐겨찾기 활동 캘린더 보기(2026-10-25 사용자 "즐겨찾기한 활동들은 … 모집기준 날짜순으로 캘린더앱 형식으로도 볼수있게")**: 2026-10-26 사용자 결정으로 보기 전환 `목록 | 캘린더`와 목록 보기를 없애고 캘린더만 둔다(예전에 기기에 저장한 보기 선택은 읽지도 지우지도 않는다). 캘린더는 위에 월 달력(iOS `UICalendarView`를 SwiftUI로 감싸 기본 장식 점, Android는 Material 3 색·글꼴의 7열 월 격자 — 같은 역할의 기본 컴포넌트가 없어 직접 그림), 날짜 아래 점: 모집 마감일 청록 채운 점, 모집 시작일 민트 빈 점(같은 날 둘 다면 둘 다). 아래는 시안 "컨퍼런스 일정표"(캔버스 3번)처럼 월별 묶음의 날짜 목록: 왼쪽 큰 날짜 숫자+요일, 오른쪽 활동 이름·프로그램·`마감`/`모집 시작` 라벨, 마감 3일 안이면 빨간 라벨. 달력에서 날짜를 누르면 목록이 그 날로 스크롤되고, 달을 넘기면 그 달 묶음으로. 기준 날짜는 모집 기간(APPLICATION 슬롯)의 시작·끝이고, 모집 기간이 없는 활동(컨퍼런스 등)은 행사 시작일을 `행사` 라벨로 둔다. 날짜 목록 아래 `날짜 없는 즐겨찾기` 묶음: 즐겨찾기한 프로그램 전부(프로그램은 캘린더에 오르지 않는다; 로고·이름·다음 모집 상태·`새 모집` 배지, 발견처럼 펼쳐 활동을 보고 열면 `새 모집`이 꺼진다)와 날짜를 하나도 모르는 활동(모집 기간도 행사 시작일도 없는 활동, 일정이 모두 날짜 미정일 때)을 발견의 기존 프로그램·활동 행으로 보여 하트·알림 종을 그대로 쓴다. 오늘 이전 날짜는 흐리게. 기기 캘린더에 쓰지 않는다(앱 안 보기만).
- **새 모집**: 즐겨찾기한 프로그램에 즐겨찾기 이후 게시된 활동이 열린 활동(기존 판정)이 되면 `newRecruitment=true`(사용자가 즐겨찾기 화면에서 그 프로그램을 열면 false로, `POST /v1/me/favorites/programs/{id}/seen` → 204). 서버: `activities.published_at`(처음 게시한 시각) > max(즐겨찾기 시각, 마지막으로 연 시각)이고 지금 열린 활동. 신규 알림도 같은 규칙.
- **푸시 알림**: 기기 토큰 `push_devices(token PK, user_id, platform IOS|ANDROID, time_zone, app_version, updated_at)`, `PUT /v1/me/push-devices {platform, token, timeZone?, appVersion?}` → 204(`timeZone`은 IANA 이름, 없으면 `Asia/Seoul`, 모르는 이름·플랫폼 400; 같은 토큰을 다른 계정이 올리면 그 계정으로 옮김)·`DELETE /v1/me/push-devices/{token}` → 204(없어도)(로그아웃·알림 끄기). 알림 설정 `GET /v1/me/notification-settings` → `{newRecruitment: boolean}`(기본 true), `PUT` 같은 본문 → 같은 본문. 알림 권한은 앱 시작 때 묻지 않고 **처음 프로그램 하트를 누를 때** 시스템 권한 창을 띄운다(거절하면 다시 묻지 않고 프로필 `알림` 행에서 설정으로 안내). 밤 수집·대기열 배치 뒤 서버가 "즐겨찾기 프로그램의 새 모집"을 사용자·활동마다 한 번만 보낸다: 제목 `{프로그램} 새 모집이 열렸어요`, 본문 `{활동 이름} · {M월 d일 마감}`, 누르면 활동 상세(유니버설 링크 경로 `/activities/{id}`). 하루 최대 3건, 22:00~08:00(서울)에는 보내지 않고 다음 아침으로 미룬다. 보낸 기록 `push_deliveries(user_id, device_token, activity_id, kind, delivery_date, sent_at)`(아래 알림 거리마다 한 번). 프로필 `알림` 행에서 `새 모집 알림` 켜기/끄기(서버 `notification_settings`).
- **프로그램·활동별 알림 수준(2026-10-25 사용자 "프로그램, 활동 별 알림 설정도 할 수 있게 해줘. 유튜브처럼 모든 활동 일정 + 신규 항목(각 나라별 아침 8시), 혹은 활동 신청일만 알림 처럼 지정")**: 즐겨찾기한 프로그램·활동마다 종 버튼(하트 옆, 즐겨찾기했을 때만 보임; iOS `bell`/`bell.fill`/`bell.slash`, Android `NotificationsNone`/`NotificationsActive`/`NotificationsOff`)을 누르면 유튜브처럼 시트에서 셋 중 하나를 고른다: `전체`(모든 활동 일정 + 신규 항목) · `신청일만`(모집 시작·모집 마감) · `없음`. 기본값은 하트를 누를 때 `신청일만`. 저장: `user_favorites.notify_level ALL|APPLICATION|NONE`, `PUT /v1/me/favorites/{programs|activities}/{id} {notifyLevel?}`(본문 없으면 기본값, 이미 있으면 수준만 바꿈), GET 응답 항목에 `notifyLevel`.
  - 알림 거리(서버가 매일 만든다): 신규 항목 = 즐겨찾기 프로그램에 새로 게시되어 열린 활동(`전체`·`신청일만` 모두, 프로그램 단위). 신청일 = 모집 시작 당일·모집 마감 하루 전·마감 당일. 전체 = 신청일 + 그 밖의 일정(발표·면접·행사·활동 시작 등 `schedules`) 당일. 활동 단위 수준이 프로그램 수준보다 앞선다(활동을 따로 `없음`으로 두면 그 활동은 조용히).
  - 보내는 시각: **사용자 기기 시간대의 아침 8시**에 그날 거리를 보낸다(`push_devices.time_zone` IANA 이름, 앱이 토큰과 함께 보냄; 기기가 여럿이면 각 기기 시간대 8시). 같은 날 거리가 3개를 넘으면 하나로 묶어 `오늘 일정 n개` 알림(누르면 즐겨찾기 캘린더). 앞의 "22:00~08:00 미룸·하루 3건" 규칙은 이것으로 바꾼다.
  - 설정 `알림` 섹션(2026-10-25 프로필에서 옮김)의 `새 모집 알림`은 전체 스위치(`notification-settings.newRecruitment`가 꺼지면 모두 보내지 않음)로 남긴다.
  - 서버 구현(2026-10-25): 배치가 매시 정각에 기기 시간대가 8시인 기기를 골라 보낸다(밤 수집·대기열 잡과 따로). 중복 방지는 기기마다(2026-10-25 사용자 결정): 같은 (사용자, 기기 토큰, 활동, 종류, 그 기기 날짜)는 한 번, 신규는 기기마다 날짜와 상관없이 한 번. 기기 시간대가 여럿이면 기기마다 그 시간대 8시에 그 날짜의 거리를 받고, 3개를 넘으면 묶는 규칙도 기기마다. 신규가 있는 날은 같은 활동의 모집 시작 알림을 따로 보내지 않는다. `push_deliveries`에 `device_token`(V54). 문구: 신규 `{프로그램} 새 모집이 열렸어요`/`{활동} · {M월 d일 마감}`, 모집 시작 `{활동} 모집이 시작됐어요`/`{프로그램} · {M월 d일 마감}`, 마감 전날 `{활동} 내일 마감`, 마감 당일 `{활동} 오늘 마감`, 일정 `{활동} 오늘 {일정 제목}`(본문 `{프로그램}`), 묶음 `오늘 일정 n개`/`{활동} · {활동} 외 n개`. 수동 실행 `--send-push-now`는 기기 시각과 상관없이 지금 보낸다(같은 날 다시 보내지 않음).
  - **페이로드**: APNs `{"aps":{"alert":{"title","body"},"sound":"default"},"activityId":<number>,"notificationId":<number>,"url":"https://dearby.wid.io.kr/activities/{id}"}`(`apns-push-type: alert`, topic `io.wid.dearby`). FCM HTTP v1 `message{token, notification{title, body}, data{activityId: "<id>", notificationId: "<id>", url: "<같은 url>"}, android{priority: HIGH, notification{channel_id: "new_recruitment"}}}`(data 값은 문자열). 묶음 알림은 `activityId`·`notificationId`가 없고 `url`은 `dearby://favorites?view=calendar`(앱이 즐겨찾기 캘린더를 연다; 2026-10-26부터 앱은 `view`를 읽지 않고 `view=list`나 없는 값도 캘린더로 연다). APNs 410·FCM `UNREGISTERED`면 그 토큰을 지운다.
- **발송 경로와 인증 정보(사용자 준비)**: iOS는 APNs 토큰 인증(.p8 키·Key ID·Team ID, 운영 `.env.prod`에만), Android는 FCM HTTP v1(Firebase 프로젝트, `google-services.json`, 서버 발송 자격). 인증 정보가 없으면 서버는 발송만 건너뛰고(로그 한 줄) 나머지 기능은 동작한다. 배치 환경값: `APNS_KEY_P8`(.p8 PEM 내용 또는 파일 경로)·`APNS_KEY_ID`·`APNS_TEAM_ID`·`APNS_TOPIC`(기본 `io.wid.dearby`)·`APNS_ENV`(`production` 기본, 개발 빌드 토큰은 `development`), `FCM_PROJECT_ID`·`FCM_CREDENTIALS_PATH`(서비스 계정 JSON 파일 경로). 토큰·인증 정보는 로그에 남기지 않는다. 개인정보처리방침에 기기 토큰(플랫폼·시간대·앱 판 포함)·알림 기록을 더한다(계정 삭제 때 함께 지움).

## 비로그인에서 만들기·추천을 막음 — 2026-10-25 사용자 결정

사용자: "명함과 프로필, 지원 자격, 관심 분야, 활동 종류 생성은 비로그인 시점에서 지원하지 말아줘" + "오늘의 맞춤대기열도 비로그인 시점에서 제공하지 말아줘". 아래 규칙이 이 문서의 앞선 비로그인 문구(추천 설정 기기 저장, 비로그인 추천 쿼리, 비로그인 대기열)를 대신한다.

- **로그인해야 하는 것**: 명함 만들기·고치기, 프로필 입력·고치기, 지원 자격 정보, 추천 설정 퍼널(관심 분야·활동 종류·얻고 싶은 것), 오늘의 맞춤 대기열과 그 신호.
- **앱(iOS·Android)**: 비로그인일 때 발견 맨 위 `오늘의 맞춤 대기열` 행은 `로그인하면 나에게 맞는 활동을 골라 드려요` 보조 글자와 함께 보이고, 누르면 기존 로그인 시트를 연다(로그인하면 퍼널 → 대기열 기존 흐름). 퍼널은 비로그인에서 저절로 뜨지 않는다. 명함·프로필 탭의 만들기·편집 버튼은 비로그인에서 로그인 시트를 연다(지금 기기에 쓰던 비로그인 초안 화면은 열지 않는다). 2026-10-25 사용자 "로그인 전엔 추천 기능을 제공하지 않기 때문에, 비로그인일 때 내 프로필에서 추천은 없어도 되는 섹션 같아": 비로그인 내 프로필에는 `추천` 묶음(지원 자격 정보·관심 분야·활동 종류)을 보이지 않는다(로그인하면 다시 보임). 둘러보기·활동 상세·받은 명함 보기(공개 링크)·내 활동(기기 저장)은 그대로.
- **웹**: 로그인이 없으므로 추천 설정 퍼널, `/queue`, 발견의 `오늘의 맞춤 대기열` 행을 없앤다. 웹 비로그인 명함 저장(받은 명함 보관, `/v1/guest/*`)은 만들기가 아니므로 그대로 둔다.
- **서버**: `GET /v1/discovery/recommendations`는 로그인만 받는다(비로그인 401). `/v1/me/*`는 원래 로그인만.
- **기존 기기 데이터**: 비로그인일 때 기기에 저장했던 추천 설정·대기열 반응은 읽지도 지우지도 않고 더 쓰지 않는다(AGENTS.md 기존 기기 데이터 원칙). 로그인할 때 기기 추천 설정을 서버로 올리던 동작도 없앤다.

## 분야·성격 태그와 아이콘 — 2026-10-24 사용자 결정

사용자: "개발지식 지워줘. 나눠서 보여줘. 대신 분야는 가장 연관있는 2개만 보여줘. 태그는 아이콘으로 표현할 수 있으면 그렇게 해줘." 분야 순서는 "수집할 때 연관도를 측정해줘", 성격은 "아이콘+글자, 목록에선 아이콘만", 배치는 "분야 태그는 로고아이콘 바로밑에 수직으로 배치해줘". 같은 날 태그 사전 정리(운영 데이터): 포트폴리오·창의성·취업·발표·문제해결·리더십·글로벌·개발지식·협업 삭제(협업은 프로젝트 활동의 96%·멘토링 활동의 93%에 함께 붙어 구분력이 없어 사용자 "협업태그 지워줘"), `프로젝트 개발`→`프로젝트`(project), 분야 태그 13개 추가.

- **태그 종류**: `tags.kind` = `FIELD`(분야, 무엇을 하나) | `TRAIT`(성격, 무엇을 얻나). V50에서 기존 태그는 `TRAIT`, 이후 운영 데이터 단계에서 분야 태그를 `FIELD`로 바꾼다. 공개 Tag = `{id, name, nameEn, kind, parentId}`(`parentId`는 아래 "하위 분야"), 어드민 태그 만들기·고치기에 `kind?`(기본 `TRAIT`), 어드민 태그 목록·선택에 종류 표시.
- **연관도**: `activity_tags.relevance smallint null`(0~100). 수집기는 지시문에 사전을 **종류별로 나눠** 넘기고, 분야 태그마다 그 활동과의 연관도(0~100)를 함께 답한다(성격 태그는 연관도 없음, null). 사전 밖 이름은 지금처럼 버린다. 운영자가 활동 태그를 지정하면 연관도는 null(운영자 지정은 순서로 본다, 아래).
- **프로그램 분야 순서**: `program_tags.position smallint not null default 0`(운영자가 적은 순서, 작을수록 앞). 프로그램의 분야 태그 순서 = 그 프로그램 게시 활동(최근 3년 창, 기존 태그 합집합 규칙과 같다)의 수집 연관도 평균 내림차순 → 연관도가 없는 태그는 뒤에서 `position` 순 → `sort_order`. 지금은 활동에 분야 태그가 없으므로 당분간 `position`이 실제 순서다. 프로그램에 분야 태그가 없으면 게시 활동 분야 태그의 합집합(연관도 순).
- **응답 모양**: `ProgramSummary`·`Activity`에 `fieldTags: Tag[]`(서버가 정렬, **최대 2개**)와 `traitTags: Tag[]`(성격 전부, 사전 순)를 더한다. 기존 `tags`는 이전 앱 호환으로 남긴다(분야+성격, 기존 최대 6개 규칙). 활동 상세용 `Activity.fieldTagsAll: Tag[]`(분야 전부, 같은 순서)도 준다. 활동 분야 순서는 연관도 내림차순 → 사전 순이고, 분야 태그가 없는 활동은 프로그램 분야 순서를 쓴다. 프로그램 `traitTags`는 프로그램 성격 태그, 없으면 게시 활동(최근 3년 창) 성격 태그의 합집합(분야와 따로 판단한다).
- **발견 프로그램 카드**: 로고 자리(44) **바로 밑에 분야 태그를 세로로**(최대 2개) 둔다. 폭이 44라 글자 대신 **아이콘 배지**(지름 22, 민트 바탕·청록 아이콘, 간격 4)로 그리고 접근성 이름은 태그 이름("분야: 데이터·AI"). 조직 이름 아래 태그 칩 줄은 **성격 아이콘 줄**(아이콘 16, 보조색, 간격 8, 글자 없음, 접근성 이름 "얻을 수 있는 것: 프로젝트, 멘토링")로 바꾼다. 펼친 활동 행도 같은 성격 아이콘 줄.
- **활동 상세**: `분야` 소제목 아래 분야 전부를 아이콘+글자 칩으로, `얻을 수 있는 것` 아래 성격 전부를 아이콘+글자 칩으로 나눠 보인다(없는 묶음은 생략).
- **하위 분야**(2026-10-24 사용자 "하위 분야를 만들어줄 수 있을까?" → "그렇게 해주세요"): V50 `tags.parent_id`(→ tags, 지우면 null). 분야 태그만 상위 분야를 가질 수 있고 상위 분야는 하위 분야가 없는 분야 태그다(한 단계). 어드민 `POST/PATCH /v1/admin/tags`의 `parentId?`(PATCH 생략은 유지, null은 해제)가 어긋나면 400(성격 태그·상위 분야가 있는 태그를 상위로·하위 분야가 있는 태그에 상위 지정·하위 분야가 있는 태그를 성격으로). 공개·어드민 Tag에 `parentId`. 운영 데이터 가져오기는 `tagParent: {"<하위>": "<상위>"}`.
  - 저장은 가장 구체적인 분야로 한다(상위 분야는 하위 분야가 함의). 분야 순서에서 하위 분야와 그 상위 분야가 함께 있으면 상위 분야를 `fieldTags`·`fieldTagsAll`에서 뺀다. 나머지는 위 연관도 → `position` → `sort_order` 규칙.
  - 검색: `q`가 상위 분야 이름(한글·영문)에 맞으면 그 하위 분야를 가진 활동·프로그램도 맞고, `tagIds`에 상위 분야 id를 주면 하위 분야를 가진 활동도 맞는다.
  - 수집기 사전은 `IT > 데이터·AI, 클라우드, 보안; 봉사`처럼 계층을 보이고 가장 구체적인 분야를 고르게 한다.
  - 운영 계획(배포 뒤 운영 데이터): IT > 데이터·AI, 클라우드, 보안, 웹·앱, 백엔드(2026-10-25), 게임, 오픈소스; 공학(engineering) > 반도체, 기계, 토목·건설; 경제·경영 > 마케팅·광고, 금융·투자; 상위 없음: 봉사, 독서·토론, 영상.
- **아이콘**: 각 플랫폼 기본 아이콘(iOS SF Symbols, Android `material-icons-extended`, 웹은 같은 모양의 인라인 SVG). 매핑은 `nameEn`으로 하고 각 플랫폼 `shared/ui`에 둔다. 모르는 `nameEn`(운영자가 새로 만든 태그)은 기본 아이콘(SF `tag` / `Sell`)이고 상세에서는 글자로 구분된다.

| nameEn | 태그 | iOS | Android |
|---|---|---|---|
| startup | 창업 | `lightbulb` | `Lightbulb` |
| project | 프로젝트 | `hammer` | `Build` |
| mentoring | 멘토링 | `bubble.left.and.bubble.right` | `Forum` |
| networking | 네트워킹 | `point.3.connected.trianglepath.dotted` | `Hub` |
| funding | 장학·지원금 | `wonsign.circle` | `Payments` |
| it | IT | `laptopcomputer` | `Computer` |
| dataAi | 데이터·AI | `sparkles` | `AutoAwesome` |
| cloud | 클라우드 | `cloud` | `Cloud` |
| semiconductor | 반도체 | `cpu` | `Memory` |
| mechanical | 기계 | `gearshape.2` | `PrecisionManufacturing` |
| civilEngineering | 토목·건설 | `building.2` | `Domain` |
| security | 보안 | `lock.shield` | `Security` |
| business | 경제·경영 | `chart.bar` | `BarChart` |
| marketing | 마케팅·광고 | `megaphone` | `Campaign` |
| investing | 금융·투자 | `chart.line.uptrend.xyaxis` | `TrendingUp` |
| volunteering | 봉사 | `heart` | `VolunteerActivism` |
| reading | 독서·토론 | `book` | `MenuBook` |
| video | 영상 | `video` | `Videocam` |
| engineering | 공학 | `wrench.and.screwdriver` | `Engineering` |
| webApp | 웹·앱 | `apps.iphone` | `Devices` |
| game | 게임 | `gamecontroller` | `SportsEsports` |
| openSource | 오픈소스 | `chevron.left.forwardslash.chevron.right` | `Code` |
| backend | 백엔드 | `server.rack` | `Dns` |

백엔드(`backend`, IT 하위 분야)는 2026-10-25 사용자 "IT 하위에 백엔드도 추가해줘"로 운영 데이터에 더했다.

## 추천 설정 퍼널·지원 자격 정보·추천 — 2026-10-24 사용자 결정

사용자: "탐색탭을 처음 들어갈 때, 추천해줄 수 있게 설정에 필요한 항목들을 토스의 퍼널처럼 만들어줄 수 있을까?" → "개인정보는 동의받고 프로필에서 입력하게 해줘" → "지원자격 정보는 토스처럼 입력하는 곳에서 동의를 구해줘" → "그렇게 해줘". 벡터 RAG 제안은 지금 규모(프로그램 약 100·활동 약 300, 태그·대상자 조건이 구조화됨)에서 규칙 점수가 더 정확하고 이유를 설명할 수 있어 1단계는 규칙 기반으로 하고, 자유 문장 입력·"비슷한 활동"이 필요해지면 pgvector 의미 검색을 점수에 더한다. 시안: https://claude.ai/artifact/EPFgtMjufqq5aDMea4LN7y (F·E 줄).

- **추천 설정 퍼널(발견 탭 첫 진입)**: 한 화면에 질문 하나, 진행 막대·`건너뛰기`, 아래 큰 버튼. 0 시작(`나에게 맞는 활동을 찾아 드릴게요`, `시작하기`/`나중에`) → 1 관심 분야(분야 태그를 상위 분야별 묶음 칩, 다중) → 2 찾는 활동 종류(부트캠프 `BOOTCAMP`·연합동아리 `CLUB`·대회 `CONTEST`·해커톤 `HACKATHON`·컨퍼런스 `CONFERENCE`·스터디 `STUDY`, 다중. 멘토십은 종류 코드가 없어 뺐다) → 3 얻고 싶은 것(성격 태그, 다중, 선택) → 4 결과(`지금 지원할 수 있는 활동 n개를 찾았어요` + `맞춤 대기열 보러 가기`(대기열을 연다, 2026-10-25), 지원 자격 정보가 없으면 `지원 자격까지 맞추려면 프로필 › 지원 자격 정보를 입력해요`). 개인정보는 묻지 않는다. `나중에`·끝까지 건너뛰기도 완료로 기록해 다시 띄우지 않는다. 다시 바꾸기는 프로필 `관심 분야·활동 종류`와 발견 탭 `오늘의 맞춤 대기열` 줄의 `설정`. iOS는 기본 목록·`.borderedProminent` 큰 버튼, Android는 Material 3, 웹은 시안 모양. 사용자 웹은 로그인이 없어 2026-10-25에 퍼널을 없앴다("비로그인에서 만들기·추천을 막음").
- **추천 설정 저장**: `RecommendationPreferences = {fieldTagIds: number[], categories: string[], traitTagIds: number[], completedAt: ISO-8601|null}`. 로그인: `GET/PUT /v1/me/recommendation-preferences`. 비로그인 기기 저장과 로그인 때 올리기는 2026-10-25에 없앴다(이미 저장된 기기 값은 읽지도 지우지도 않는다, "비로그인에서 만들기·추천을 막음").
- **지원 자격 정보(프로필, 동의 필요)**: 프로필 `추천` 묶음의 `지원 자격 정보` 행. 처음 누르면 아래에서 동의 시트: 제목 `지원할 수 있는 활동만 보여 드리려면 이 정보가 필요해요`, 요약 네 줄(모으는 정보 재학 상태·학년·태어난 해 / 쓰는 곳 지원 자격 거르기와 활동 추천 / 보관 내 계정에만, 다른 사람에게 보이지 않음 / 프로필에서 지우면 바로 삭제), `[필수] 지원 자격 정보 수집·이용 동의`(전문 보기), `동의하고 입력하기`, `닫기`. 동의 뒤 상황(재학 중 → `educationStatus=enrolled`+`employment=student`, 휴학 중 → `onLeave`+`student`, 졸업 예정 → `graduating`+`student`, 졸업했어요 → `graduated`(employment null), 직장에 다녀요 → `employment=employed`(educationStatus null)) → 학년(재학·휴학·졸업 예정만, 1~5+ → `schoolYear`) → 태어난 해(휠 → `birthYear`, 나이는 서버가 계산) 한 화면씩. 완료 뒤 행 보조 글자 `재학 중 · 3학년 · 2003년생`, 다시 누르면 고치기·`지원 자격 정보 지우기`.
  - API: `GET /v1/me/eligibility-profile` → `{consent: {version, agreedAt}|null, educationStatus, employment, schoolYear, birthYear}`(없으면 null들). `PUT`은 `{consentVersion, educationStatus?, employment?, schoolYear?, birthYear?}` — `consentVersion`이 현재 동의 문서 버전(서버 상수 **`2026-10-24`**, 앱이 같은 값을 보낸다)과 다르면 400 `CONSENT_VERSION`. 값은 영문 어휘(`educationStatus` enrolled·onLeave·graduating·graduated, `employment` student·unemployed·employed·founder, `schoolYear` 1~5, 5는 5학년 이상)이고 응답은 GET과 같은 모양이다. `DELETE`는 값과 동의 기록을 모두 지운다. 회원 탈퇴 때 함께 지운다. 비로그인은 입력할 수 없다(로그인 유도).
  - 저장: `profile_eligibility(user_id pk, education_status, employment, school_year, birth_year, consent_version, consented_at, updated_at)`. 명함·공개 프로필·운영자 화면 어디에도 노출하지 않는다(서버 로그에도 값을 남기지 않는다). 약관 버전이 바뀌면 다음 입력 때 다시 동의 시트.
  - 개인정보처리방침에 항목·목적·보관(삭제·탈퇴 시 즉시)을 더한다. 문구 게시는 사용자 확인 뒤.
- **추천 API**: `GET /v1/discovery/recommendations?size=10` (로그인만, 저장된 설정·지원 자격 정보로. 비로그인 401 — "비로그인에서 만들기·추천을 막음"). 대상은 열린 활동(기존 판정). 지원 자격 정보가 있으면 대상자 조건 1·2차 판정으로 **맞지 않는 활동을 뺀다**. 점수: 분야 일치(활동 분야 태그가 고른 분야이거나 그 하위면, 수집 연관도 0~100을 가중치로, 하위 분야 일치가 상위 일치보다 높다) + 종류 일치 + 성격 일치 수 + 마감 임박(14일 안이면 가산). 응답 항목 `{activity: Activity, program: ProgramSummary, reasons: string[]}` — `reasons`는 사람이 읽는 짧은 이유(`데이터·AI`, `부트캠프`, `11월 7일 마감`), 점수 숫자는 내보내지 않는다. 같은 프로그램의 활동은 최대 2개.
- **구현 세부(2026-10-24 서버)**: `PUT /v1/me/recommendation-preferences` 본문은 `{fieldTagIds, categories, traitTagIds}`(통째로 바꿈, 분야 자리에 성격 태그 등 종류가 틀리거나 없는 id는 400, 각 50개까지)이고 응답은 GET과 같다. 처음 저장한 시각이 `completedAt`이며 빈 배열 저장(건너뛰기)도 완료다. 추천 응답은 `{items: [{activity, program, reasons}], total}`(`total`은 판정·설정 일치를 지난 후보 수로 프로그램당 2개·`size`로 자르기 전, 퍼널 결과의 "n개"), `size` 1~50(기본 10). 쿼리 `fieldTagIds`·`categories`·`traitTagIds`는 2026-10-25에 없앴고(보내도 무시), 저장된 설정이 없으면 빈 설정으로 본다. 설정이 하나라도 있으면 분야·종류·성격 중 하나도 맞지 않는 활동은 내보내지 않고, 설정이 모두 비면 열린 활동을 마감 순으로 준다. 분야 가중: 고른 분야와 같음 1, 고른 분야의 하위 0.6 × 연관도(수집값, 운영자 지정 70, 프로그램 분야를 물려받은 활동 30). 나이는 서울 기준 올해 − 태어난 해. 추천의 대상자 판정은 조건 트리(2차)만 본다: SQL 1차는 요청 필드마다 맞는 조건을 요구해 `any` 그룹을 너무 엄하게 거르므로(재학 중 = `enrolled`+`student`인 사람에게서 "재학생 또는 미취업자" 활동을 뺀다) 여러 필드를 함께 보내는 추천에는 쓰지 않는다. 직장(`employment=employed`, educationStatus null)인 사람은 서버가 재학상태를 `graduated`로 보고 판정한다(클라이언트는 그대로, 2026-10-24 결정). 그래서 `any(재학, 미취업)` 활동에서 빠진다. 읽을 수 없는 요청 본문은 400 `요청 본문을 읽을 수 없습니다`이고, 파서 메시지(보낸 값)는 응답·로그에 남기지 않는다. `program`의 `openActivityCount`·`nearestRecruitmentEndAt`은 추천 후보(판정을 지난 열린 활동)로 센다. `reasons`는 한국어이고 종류 이름은 "활동 종류" 절의 이름(`CONTEST` 공모전)이다.
- **지원 자격을 모르는 활동**: 지금 규칙은 조건이 없으면 제한 없음으로 통과한다. 그러나 2026-10-24 운영 데이터에서 게시 활동 59개 중 대상자 조건이 구조화된 것은 21개뿐이고(원문 `audience`만 있는 것 21, 둘 다 없는 것 17) 조건 없음이 "제한 없음"인지 "아직 모름"인지 구분되지 않는다. 그래서 `activities.eligibility_checked boolean not null default false`를 둔다(운영자가 확인했거나 수집기가 원문에서 조건을 읽었으면 true, 원문에 제한이 없다고 명시돼도 true). 추천은 지원 자격 정보가 있을 때 `eligibility_checked=false` 활동을 빼지 않되 `reasons`에 `지원 자격 확인 필요`를 붙이고 같은 점수에서 뒤로 보낸다. 수집기는 원문 대상 구절이 있으면 조건을 채우고 `eligibility_checked`를 true로 한다. 마이그레이션은 `eligibility`가 있는 활동을 true로 채우고, 2026-10-24 사용자 결정("셋 다 그렇게 해주세요")으로 대상 문구가 없는 **종류 CONFERENCE** 활동은 누구나 등록하는 공개 행사로 보아 true로 한다(운영 데이터 단계, 대회가 함께 열리는 행사의 대회 활동은 제외).
- **발견 탭 노출(2026-10-25 사용자 결정 "추천은 없애고 맞춤대기열 진입만 남겨줘")**: 발견 목록 맨 위에는 `추천` 묶음 없이 `오늘의 맞춤 대기열` 한 줄만 두고, 퍼널을 건너뛰었거나 마치지 않았으면 같은 줄이 퍼널을 먼저 연 뒤 대기열로 간다.

## 맞춤 대기열(스토리형 쇼츠)·조직 친화도·활동 벡터 — 2026-10-24 사용자 결정

사용자: "배치를 돌릴 때, 사용자가 지정한 조건과 실제 신청한 항목들을 가지고 추론해서 각 사용자별로 10개 정도씩 맞춤대기열을 생성" → "스팀의 맞춤대기열도 참고" → "소개팅앱 카드처럼" → "상하 스와이프로 카드를 넘길 수 있게" → 쇼츠형이되 콘텐츠가 영상이 아니라는 고민 → 스토리형 쇼츠(시안 Q3) → "추천대기열 생성시 벡터필드를 활용" → "지원하거나, 관심표시, 참여한 활동 조직도 가중치에 영향" → "내 활동에 추가도 포함해서 해주세요". 시안: https://claude.ai/artifact/EPFgtMjufqq5aDMea4LN7y (Q3). LLM으로 사용자별 추론은 하지 않는다(개인정보 외부 전송·비용); 사용자 데이터는 서버 밖으로 나가지 않는다.

- **화면(스토리형 쇼츠)**: 발견 목록 맨 위 `오늘의 맞춤 대기열` 한 줄로 들어간다(2026-10-25 사용자 "추천은 없애고 맞춤대기열 진입만 남겨줘": 추천 묶음은 없앴고, 퍼널을 마치지 않았으면 퍼널을 먼저 연다). 전체 화면 세로 페이지(iOS 세로 `ScrollView` + `.scrollTargetBehavior(.paging)`, Android `VerticalPager`, 웹 `scroll-snap-type: y mandatory`), 위로 넘기면 다음 활동. 활동 한 장은 서버가 만든 장면(`scenes`, 2~5개, 아래 "구현 세부")을 4초마다 자동으로 넘긴다(위 진행 막대, 오른쪽 탭 다음·왼쪽 탭 이전 장면). 2026-10-24 사용자 "장면개수는 유동적으로": 한 줄 소개 → 핵심 줄 → 대상 → 혜택 → 마감까지(늘 마지막) 중 내용이 있는 것만. 마지막 장면이 끝나면 카드는 멈춰 있고 다음 활동으로 저절로 넘어가지 않는다(2026-10-24 사용자 "멈춰줘"; 위로 넘기거나 `다음`). 배경은 커버 이미지에 느린 확대·이동(켄 번즈), 글자는 아래에서 차례로 떠오름. 동작 줄이기에서는 움직임 없이 장면만 바뀐다. 오른쪽 버튼 줄: ♥ `관심`(토글), `자세히`, `다음`, `⋯`(시트: `관심 없어요`·`이 분야는 덜 보기`·`이 프로그램 숨기기`·`지원 자격이 안 돼요`, 고른 뒤 `되돌리기` 알림). 두 번 탭은 관심 있어요(하트 효과). 화면 읽기에서는 버튼과 사용자 지정 동작으로 같은 일을 한다. 10장을 다 보면 `오늘 대기열을 다 봤어요` + `더 받기`(남은 후보로 다시 만들기). 마지막 카드와 끝 화면 사이의 광고 카드 1장은 아래 "맞춤 대기열 끝 광고(AdMob, 비개인화)" 절(2026-10-25).
- **콘텐츠**: `activities.hook`(30자 안팎 한 줄 소개)·`activities.highlights`(짧은 문장 3개, `{icon, text}` — icon은 `cost`·`period`·`team`·`mentor`·`award`·`place`·`talk`·`ticket` 중 하나)를 수집 배치가 활동 설명(공개 정보)으로 만든다. 운영자가 어드민에서 고칠 수 있고 고친 값은 `curated_fields`로 보호한다. 없으면 장면 ①은 활동 이름, ②는 비용·기간·장소에서 만든다.
- **커버 이미지**: `program_covers(program_id, content_type, data, source_page, source_image, enabled, updated_at)`(세로 4:5, 1080×1350, WebP ≤350KB, 로고와 같은 업로드 검사). 공개 `/v1/programs/{id}/cover?v=`(로고와 같은 캐시 규칙), `ProgramSummary.coverUrl`. 어드민에서 올리기·끄기(주최 측 요청 시 바로 내린다). 없으면 앱이 로고 + 브랜드 색 그라데이션 커버를 그린다. 상표 규칙은 로고와 같다.
- **신호**: `recommendation_signals(user_id, activity_id, kind, created_at)` — `LIKE`(관심 있어요)·`UNLIKE`(관심 취소, LIKE를 지운다)·`DISMISS`(관심 없어요)·`LESS_FIELD`(분야 덜 보기, tag_id 함께)·`HIDE_PROGRAM`·`INELIGIBLE`·`COMPLETE`(마지막 장면까지 봄)·`SKIP_EARLY`(장면 ①에서 넘김)·`OPEN_DETAIL`. `POST /v1/me/recommendation-signals {activityId, kind, tagId?}`(로그인만, 비로그인은 보내지 않는다). 머문 시간은 저장하지 않는다. 그 밖에 이미 있는 데이터를 신호로 읽는다: `user_activities`(내 활동에 추가), 사용자 일정 상태(지원 단계 `통과`=지원, 활동 단계에 이르고 `탈락`이 없음=참여, `탈락`), 내 활동에서 지움.
- **가중치(시작값, 운영하며 조정)**: 참여 +1.0, 지원 +0.7, 내 활동에 추가 +0.5, 관심 있어요 +0.4, 끝까지 봄·자세히 +0.1, 장면 ①에서 넘김 −0.05, 관심 없어요 −0.3, 내 활동에서 지움 −0.2, 이 분야는 덜 보기(그 분야 −0.5), 이 프로그램 숨기기(그 프로그램 제외), 지원 자격이 안 돼요(그 활동 제외). 최근 신호일수록 크게: 반감기 90일.
- **조직 친화도**: 신호를 활동 → 프로그램(×1.0) → 조직(×0.6) → 상위 조직(×0.3)으로 올려 더한다. 하위 조직이 10개를 넘는 넓은 상위 조직(예: `연합동아리`)으로는 올리지 않는다. `user_org_affinity(user_id, organization_id, score, updated_at)`, 0~1로 맞춘다. 같은 프로그램의 새 기수는 가산(이유 `지난 기수에 지원했어요`/`내 활동에 담았던 프로그램`), 지금 참여 중인 같은 프로그램의 기간이 겹치는 기수는 뺀다.
- **활동 벡터**: `activity_embeddings(activity_id, model, embedding halfvec(1024), …)`(pgvector; 처음 적은 `activities.embedding` 열 대신 대상·모델마다 한 행, 아래 "구현 세부"). 임베딩 글 = 프로그램 이름·조직 이름·활동 이름·종류·분야·성격 태그 이름·`hook`·`highlights`·대상 원문·지역(시·도)·설명 전체(2026-10-24 사용자: 앞 1,500자가 아니라 전체, 모델 최대 입력에서만 자른다; 설명을 맨 뒤에 둬 잘리면 설명 끝이 잘린다). 날짜·마감·비용 숫자·나이 조건·URL·연락처는 넣지 않는다(규칙이 처리). 모델은 배치 컨테이너 안의 모델이고 외부 API를 쓰지 않는다(2026-10-24 벤치마크 뒤 확정은 아래 "구현 세부"). 내용이 바뀐 활동만 다시 임베딩. 태그 이름 사전도 임베딩해 둔다. 인덱스는 지금 만들지 않는다(활동 1만~5만 개쯤부터 halfvec HNSW, 수백만 개에서만 binary 양자화 1차 + 정밀 비교).
- **사용자 취향 벡터**: 긍정 신호 활동 벡터의 가중 평균 − 부정 신호 활동 벡터 가중 평균(같은 가중치·반감기). 신호가 없으면 추천 설정의 분야·성격 태그 이름 벡터 평균. `user_taste(user_id, embedding halfvec(1024), updated_at)`. 글을 임베딩하지 않고 활동 벡터로만 계산한다.
- **대기열 만들기(밤 배치, 수집 뒤)**: 사용자마다 후보 = 열린 활동 − 지원 자격이 맞지 않는 것 − 이미 내 활동에 있는 것 − 숨긴 프로그램·`관심 없어요`·`지원 자격이 안 돼요` 활동. 점수 = 규칙 점수(추천 API와 같다) + 0.25 × 취향 유사도 + 0.15 × 조직 친화도(+ 새 기수 가산) − 분야 덜 보기 감점. 다양성: 이미 고른 카드와 코사인 유사도가 높으면 깎는다(MMR, λ=0.7). 10개, 프로그램당 최대 2개. `discovery_queue(user_id, date, position, activity_id, reasons jsonb, generated_at)`. 신호·설정·지원 자격이 바뀌면 그 사용자만 다시 만든다. `GET /v1/me/discovery-queue` → `{date, items: [{activity, program, reasons}], seen}`(읽을 때 그사이 마감·조건 변경을 한 번 더 거른다), `POST /v1/me/discovery-queue/refill`(`더 받기`). 비로그인 대기열은 2026-10-25에 없앴다("비로그인에서 만들기·추천을 막음").
- **이유 문구**: 규칙 이유 + `관심 표시한 {활동}과 비슷해요`(취향 유사도가 가장 큰 긍정 활동) + `{조직}에 관심을 보였어요`/`같은 {상위 조직} 그룹`.
- **구현 세부(2026-10-24 서버)**:
  - API(로그인, Bearer). `GET /v1/me/discovery-queue` → `{date: "YYYY-MM-DD"(서울), items: [{activity: Activity, program: ProgramSummary, reasons: string[], scenes: Scene[], mySignal: "LIKE"|null}], seen: number}`. 오늘 대기열이 없으면 그 자리에서 만든다. 읽을 때 열린 활동·지원 자격·내 활동·숨김·관심 없어요·지원 자격이 안 돼요로 다시 거른다. `seen`은 남은 항목 중 오늘(서울 0시 뒤) 반응한(`LIKE`·`DISMISS`·`HIDE_PROGRAM`·`INELIGIBLE`·`LESS_FIELD`·`COMPLETE`·`SKIP_EARLY`) 항목 수이고 앱은 처음 반응하지 않은 카드부터 이어 본다. `POST /v1/me/discovery-queue/refill`(본문 없음)은 오늘 받은 활동을 빼고 남은 후보로 최대 10개를 뒤에 붙이고 같은 모양을 돌려준다(남은 후보가 없으면 그대로).
  - `POST /v1/me/recommendation-signals {activityId, kind, tagId?}` → 204. `kind`는 위 신호 이름(`REMOVE` 제외) 또는 `UNLIKE`(LIKE를 지운다). `LESS_FIELD`는 분야 태그 `tagId`가 필요하다(아니면 400). 모르는 kind 400, 게시되지 않은 활동 404. (사용자, 활동, 종류)마다 한 행이라 같은 신호는 시각만 새로 한다. `LIKE`와 `DISMISS`는 서로를 지운다. 되돌리기는 `DELETE /v1/me/recommendation-signals?activityId=&kind=` → 204(없어도). 내 활동에서 지우면 서버가 `REMOVE`를 남기고 다시 넣으면 지운다.
  - 암묵 신호: 내 활동마다 가장 강한 것 하나(시각은 `user_activities.updated_at`) — 참여(모집 기간이 아닌 활동 기간 일정에 탈락 아닌 상태가 있고 어디에도 탈락이 없음) 1.0, 지원(모집 기간 일정 `PASSED` 또는 어디든 `FAILED`) 0.7, 그 밖 추가 0.5.
  - 점수: 규칙 점수(추천 API와 같은 점수, 100으로 나눠 0~1) + 0.25 × 코사인(취향, 활동) + 0.15 × (조직 친화도 + 같은 프로그램 가산) − 0.5 × 분야 덜 보기(그 분야 또는 그 하위 분야를 가진 활동, 감쇠한 신호 합). 추천 설정이 맞지 않는 활동도 후보다(추천 API와 다름). 조직 친화도: 프로그램별 감쇠 가중치 합 P, 조직 0.6·P, 상위 조직 0.3·P(하위 조직 10개 초과면 올리지 않음), 1 − e^(−합). 같은 프로그램 가산 1 − e^(−P). 후보의 친화도 = max(조직, 상위 조직) + 같은 프로그램 가산. MMR은 λ·점수 − (1−λ)·이미 고른 카드와의 최대 코사인(벡터 없으면 0), 동점은 id.
  - 다시 만들기: 설정·지원 자격·내 활동·신호가 바뀌면 오늘 대기열이 있는 사용자만, 오늘 반응한 카드는 앞에 그대로 두고 나머지를 새로 고른다(전체 길이 유지, 최소 10). 밤 배치는 수집 뒤 추천 설정·신호·내 활동·대기열이 있는 사용자 전부. 지난 날짜 대기열은 지운다.
  - 이유: 규칙 이유 → `관심 표시한 {활동}과/와 비슷해요`(관심 있어요 한 활동과 코사인 0.75 이상) → `지난 기수에 지원했어요`(같은 프로그램에 지원·참여) / `내 활동에 담았던 프로그램` / `{조직}에 관심을 보였어요` / `같은 {상위 조직} 그룹`(친화도 0.3 이상) 중 하나 → `지원 자격 확인 필요`(맨 뒤).
  - `Scene`(2026-10-24 사용자 "장면개수는 유동적으로", 이 순서로 내용이 있을 때만, 앱은 모르는 type을 건너뛴다): `{type:"HOOK", hook, name}`(늘, hook이 없으면 활동 이름) · `{type:"HIGHLIGHTS", items:[{icon, text}]}`(highlights, 없으면 비용·활동 기간 문구·장소 이름이 두 줄 이상일 때) · `{type:"ELIGIBILITY", text, items:[{icon, text}]}`(지원 자격을 확인한 활동만. `items`는 아래 "대상 아이콘 라벨", `text`는 대상 원문이고 없으면 라벨을 ` · `로 이은 글; 원문도 라벨도 없으면 장면을 뺀다) · `{type:"BENEFIT", text}`(비용 문구에 무료·전액 지원, 없으면 장학·지원금 태그면 `장학·지원금이 있어요`) · `{type:"DEADLINE", dday, label, deadlineText, reasons}`(늘 마지막. `dday`는 서울 날짜로 남은 날 정수, 0 = 당일, 음수 = 지남, 모르면 null. `label`은 `마감까지`, 컨퍼런스·밋업·강연은 행사 날짜로 `행사까지`. `deadlineText` 예 `11월 7일 마감`/`11월 29일 행사`. 앱이 D-day를 꾸민다). 추천 API 항목에도 같은 `scenes`를 싣는다(비로그인 대기열 화면용이었으나 그 화면은 2026-10-25에 없앴다).
  - **대상 아이콘 라벨**(2026-10-25 사용자 "맞춤 대기열에서 참여대상이 문자열로 나오는데 아이콘 라벨로"): 대상자 조건 트리에서 조건 하나가 항목 하나(`all` 그룹은 펼침), `any` 그룹은 ` 또는 `으로 이은 한 항목(아이콘은 가장 중요한 조건의 것). 중요도 순 나이 > 학력·재학 > 취업 > 학년 > 지역 > 그 밖(전공·국적·경력·팀·기타)으로 최대 4개, 나머지는 버린다. 기타는 메모가 20자 이하일 때만. 글 예: `만 34세 이하`·`만 19~34세`·`2000~2006년생`, `재학·휴학생`·`졸업예정자`·`대학·대학원 학력`, `미취업자`·`재직자`, `3학년 이상`, `서울·경기`, `컴퓨터 전공`/`전공 무관`, `대한민국 국적`, `경력 2년 이하`, `2~4인 팀`. 앱은 아이콘 + 글로 그리고 모르는 icon은 `other` 아이콘.

| icon | 조건 | iOS | Android |
|---|---|---|---|
| age | 나이·출생연도 | `birthday.cake` | `Cake` |
| education | 재학상태·학력 | `graduationcap` | `School` |
| schoolYear | 학년 | `number` | `Numbers` |
| employment | 취업상태 | `briefcase` | `Work` |
| region | 지역 | `mappin.and.ellipse` | `Place` |
| major | 전공 | `book` | `MenuBook` |
| nationality | 국적 | `globe` | `Public` |
| experience | 경력연수 | `clock` | `Schedule` |
| team | 팀규모 | `person.3` | `Groups` |
| other | 기타 | `info.circle` | `Info` |

  - 응답 필드 추가: `Activity.hook: string|null`, `Activity.highlights: [{icon, text}]`, `Activity.cost: string|null`(장면 ② 대체용 비용 문구), `ProgramSummary.coverUrl: string|null`(켜진 커버만), `ProgramSummary.brandColor: "#RRGGBB"|null`(보일 로고(계승 포함)의 흰색·검정·회색이 아닌 대표색, 로고를 저장·가져올 때 서버가 계산해 `program_logos`·`organization_logos.brand_color`에 두고 밤 배치가 빈 것을 채운다; 앱은 커버가 없을 때 그라데이션에 쓴다).
  - 커버: 공개 `GET /v1/programs/{id}/cover?v={updatedAt epoch 초}`(인증 없음, 로고와 같은 `public, max-age=31536000, immutable`), 없거나 꺼졌으면 404·`coverUrl` null. 끄면 `updated_at`이 바뀌어 새 주소가 된다(이미 받은 사본은 기기 캐시에 남을 수 있다). PNG·JPEG·WebP 350KB 이하(4:5·1080×1350은 권장, 서버가 픽셀을 검사하지 않는다).
  - 벡터 저장: `activities.embedding` 열 대신 `activity_embeddings(activity_id, model, embedding halfvec(1024), source_hash, updated_at)`·`tag_embeddings(tag_id, …)`(대상과 모델마다 한 행; activities 행을 읽을 때 벡터를 끌고 다니지 않고, 모델을 바꿀 때 새 벡터를 다 만든 뒤 읽기를 넘긴다). `source_hash = sha256(모델 id + 임베딩 글)`이 같으면 다시 임베딩하지 않는다. 지금 읽는 모델은 `embedding_settings.active_model` 한 행이고, 배치가 새 모델로 게시 활동·태그를 실패 없이 모두 만든 뒤에만 바꾸며 그때 다른 모델의 활동·태그 벡터와 `user_taste`를 지운다. 비교는 같은 모델 id 벡터끼리만. 마이그레이션 V52가 `create extension if not exists vector`(운영 DB에 아직 확장이 없다).
  - 임베딩 글 규칙: 줄마다 `프로그램:`·`조직:`·`활동:`·`종류:`·`분야:`·`얻을 수 있는 것:`·`소개:`·`핵심:`·`대상:`·`지역:`·`설명:`. 대상·설명·소개·핵심에서 URL·이메일·전화·날짜(2026.11.01, 11월 1일)·시각·금액·나이 조건(만 19세 이상)을 지운다. 지역은 구조화된 장소 주소(활동 기간 일정의 주소)의 시·도만이고 자유 문구 `location`은 쓰지 않는다. 태그 사전 글은 `이름 / 영문 이름`.
  - 모델(2026-10-24 벤치마크): 기본 `nlpai-lab/KURE-v1`(MIT, bge-m3 구조, dense 1024, CLS 풀링 후 L2 정규화) revision `8b418a58414668e75532ed045c22d9ca018ae2b2`, `model.safetensors` sha256 `c18156e80caf8ff45eb84a24a853130c3bca03087ccb41b051f86e7556bae02c`. batch 이미지 빌드가 그 판을 받아 체크섬을 확인하고 ONNX로 바꾼다(optimum, 버전 고정). 실행은 batch JVM의 ONNX Runtime **CPU 실행기만**(macOS CoreML 실행기는 RSS 16.7GB까지 늘었다). ONNX Runtime에서는 fp32가 느려 기본 가중치는 int8 동적 양자화(`EMBEDDING_VARIANT=int8`, 품질 −0.01~0.03); fp32도 고를 수 있다. 벡터에 붙는 모델 id는 `nlpai-lab/KURE-v1@8b418a5:int8`(판·양자화가 바뀌면 id도 바꾼다). 최대 입력 8192토큰. 384차원 소형으로 바꿀 때(`dearby.embedding.dimensions=384`, multilingual-e5-small은 모든 글에 `passage: `/`query: ` 접두어 — `text-prefix`)는 halfvec 열 차원을 바꾸는 마이그레이션이 함께 필요하고, 그 모델에는 binary 양자화를 쓰지 않는다. 로컬(Apple Silicon, 4스레드, 토큰 자르기 없음) 측정: int8 모델 읽기 2.1초, 390~2,500자 글 300개 151초(0.5초/개), 최대 RSS 2.2GB. batch 이미지 +약 740MB(모델 586MB, onnxruntime 133MB, 토크나이저 18MB). 모델을 바꾸는 빌드 단계 이미지는 약 12GB로 빌드 캐시에만 남는다. 내보낸 `tokenizer.json`은 최대 길이를 512로 적고 있어 서버가 `modelMaxLength`를 설정값(8192)으로 덮어쓴다.
  - 커지면 켤 경로(지금은 만들지 않는다): 활동 1만~5만 개쯤 `create index on activity_embeddings using hnsw (embedding halfvec_cosine_ops) where model = '<모델 id>'`, 수백만 개면 `create index on activity_embeddings using hnsw ((binary_quantize(embedding)::bit(1024)) bit_hamming_ops) where model = '<모델 id>'`로 `order by binary_quantize(embedding)::bit(1024) <~> binary_quantize(:q) limit 200` 1차 후보를 뽑고 `embedding <=> :q`로 다시 줄 세운다(384차원 e5 계열에는 쓰지 않는다). 지금은 수백 개라 메모리에서 계산한다.
- **개인정보처리방침**: "앱 안의 활동 기록(내 활동, 관심 표시, 대기열 반응)을 맞춤 추천에 이용하고 외부로 보내지 않는다"를 더한다(게시 전 사용자 확인).

## 맞춤 대기열 끝 광고(AdMob, 비개인화) — 2026-10-25 사용자 결정

사용자: 한국에만 출시하고, 맞춤 대기열을 끝까지 본 뒤 AdMob 네이티브 광고 1장을 인스타그램 스토리 광고처럼 보여 준다. 비개인화 광고만, 직접 계약 광고는 하지 않는다. 원하지 않는 광고는 AdMob 콘솔의 분류 차단으로 막는다(교육·채용 성격만 남기는 방향, 콘솔 작업은 별도). 첫 출시부터 넣는다. 대기열 본문은 위 "맞춤 대기열(스토리형 쇼츠)" 절이고, 스토어·데이터 공개 답안은 [광고 스토어 답안](../../docs/store/ads-2026-10-25.md).

전제: 스토어 배포 국가는 **한국만**이다. UMP 동의 창을 두지 않는 근거가 이것이므로(Google EU 사용자 동의 정책은 EEA·영국·스위스 사용자에게 인증 CMP를 요구한다) 배포 국가를 넓히면 이 절을 다시 정한다.

- **위치**: 대기열 마지막 활동 카드 다음, 끝 화면(`오늘 대기열을 다 봤어요` + `더 받기`) 앞에 광고 카드 1장. 대기열을 한 번 열 때(`queue_open` 한 번)마다 최대 1장이다. `더 받기`로 뒤에 붙은 카드 뒤에는 다시 넣지 않는다(같은 열람). 대기열을 닫았다가 같은 날 다시 열면 새 열람이라 다시 1장까지 보일 수 있다. 대기열은 로그인 전용이므로 광고도 로그인 사용자에게만 보인다. 발견 목록·활동 상세·그 밖의 화면에는 광고가 없다.
- **모양(스토리 광고)**: 활동 카드와 같은 전체 화면 카드. 위쪽에 `광고` 배지(한국어 고정 문구, 15px 이상, 앱이 그린다), AdChoices 아이콘(SDK가 그림, CTA·닫기와 먼 모서리), 광고주 이름(`advertiser`, 없으면 생략)·헤드라인·미디어(`MediaView`, 고정 크기로 둔다)·본문·CTA 버튼. 모든 자산은 네이티브 광고 뷰(iOS `NativeAdView`, Android `NativeAdView`) 안에 둔다. AdMob 네이티브 정책상 자산을 변형하지 않는다(이미지를 늘리거나 찌그러뜨리지 않음, 가로 대칭 10%까지만 자름, 헤드라인 25자·본문 90자까지 자르지 않음, 글자는 배경과 대비 충분). 미디어 비율은 기본 `ANY`로 요청하고 세로 카드 안에 맞춘다(세로만 요청하면 채워지는 광고가 줄어든다고 Google이 경고한다; 바꾸려면 사용자 결정).
- **활동 카드와 다른 점(정책: 광고 배경은 눌리지 않고 광고를 앱 콘텐츠·이동 버튼처럼 꾸미지 않는다)**: 4초 자동 넘김·장면 진행·켄 번즈·글자 떠오름 없음. 왼쪽·오른쪽 탭 영역(이전·다음 장면)과 두 번 탭(관심 있어요)을 끈다. 오른쪽 버튼 줄(♥ `관심`·`자세히`·`다음`·`⋯`)을 숨긴다 — 광고 뷰와 겹치면 잘못 누른 클릭이 되기 때문이다. 넘김은 상하 스와이프(대기열과 같은 넘김; Google 전체 화면 네이티브 정책 "If the app's content naturally uses a swipe-to-dismiss action, this option may remain unchanged")와 대기열 닫기 버튼(광고 뷰 밖)으로 한다. 신호(`recommendation-signals`)는 보내지 않는다. 진행 막대는 대기열 전체가 아니라 한 카드 안의 장면 막대이므로 광고 카드에는 진행 막대를 두지 않는다(막대가 없는 것으로 활동 카드와 구분된다, Android PR #367 구현). 화면 읽기는 `광고` 배지를 먼저 읽고 헤드라인·본문·CTA 순서. 동작 줄이기에서도 광고 동영상 재생은 SDK 기본을 따른다.
- **로드**: 광고에 동의한 계정은 대기열 화면을 열 때(대기열 응답을 받은 뒤) 광고 1개를 미리 요청한다(미동의 계정은 아래 "묻는 시점"대로 마지막 카드에서 묻고 그 열람에는 광고가 없다). 마지막 활동 카드에서 위로 넘길 때 광고가 로드돼 있지 않거나(아직 요청 중 포함) 실패했으면 광고 카드 없이 바로 끝 화면으로 간다(사용자를 기다리게 하지 않는다). 다시 요청하지 않는다. 화면을 닫으면 받은 광고를 버린다(`destroy`). 광고 단위는 이 자리 전용 하나(Google 권장: 자리마다 다른 광고 단위).
- **요청 설정(두 플랫폼 같음)**: 비개인화 — 요청마다 네트워크 extras `npa = "1"`(Android `AdMobAdapter` extras 번들, iOS `GADExtras.additionalParameters`). 최대 광고 콘텐츠 등급 PG(Android `RequestConfiguration.MAX_AD_CONTENT_RATING_PG`, iOS `GADMaxAdContentRating.parentalGuidance`). 게시자 자사 ID(Publisher first-party ID, SDK 기본 켬; Google 설명 "deliver more relevant and personalized ads")는 끈다(iOS `setPublisherFirstPartyIDEnabled(false)`, Android `MobileAds.putPublisherFirstPartyIdEnabled(false)`를 초기화 뒤 호출 — 판단, 되돌릴 수 있음). 게시자 개인정보 처리(PPT, `publisherPrivacyPersonalizationState = disabled`) API는 Beta라 지금 쓰지 않는다(쓰면 npa와 함께, 더 엄격한 쪽이 적용된다). UMP 동의 창 없음(위 전제).
- **선택 동의 먼저(2026-10-25 사용자 결정)**: 아래 "광고 목적 개인정보 제3자 제공 동의(선택)"에 동의한 사용자에게만 SDK를 시작하고 광고를 요청한다. 이 절의 로드·ATT·요청 설정은 모두 동의 뒤의 일이고, 미동의·철회면 광고 카드 없이 끝 화면으로 간다.
- **iOS ATT(2026-10-25 사용자 결정)**: 광고가 켜진 빌드에서 광고 동의를 받은 뒤(미동의면 ATT 창도 띄우지 않는다) 맞춤 대기열의 첫 광고 요청 전에 ATT 권한 창을 한 번 띄운다(`ATTrackingManager.trackingAuthorizationStatus == .notDetermined`일 때만; 이미 답했으면 묻지 않는다). 응답을 받은 뒤 광고를 요청하며 거절이어도 그대로 진행한다(거절이면 IDFA는 0). 허용해도 `npa=1`을 유지하므로 개인 맞춤 광고가 아니다 — IDFA는 광고 노출 횟수 제한과 광고 성과 측정에만 쓰인다. 광고가 꺼진 빌드(Release에 ID 없음, 또는 아래 광고 끄기 플래그)는 창을 띄우지 않는다. `NSUserTrackingUsageDescription` 문구(목적: 광고 노출 횟수 제한과 광고 성과 측정, 초안): `같은 광고를 너무 자주 보여 드리지 않고 광고 성과를 측정하는 데 쓰여요. 허용해도 맞춤 광고는 보여 드리지 않아요.` 결과는 선택 이벤트 `att_prompt_result`([analytics-v1.md](analytics-v1.md)).
- **광고 끄기 — 초기 테스트 기간은 꺼 둠(2026-10-26 사용자 결정, iOS)**: 맞춤 대기열 끝 광고 노출과 그 직전의 광고 제3자 제공 동의 요청을 **기본 꺼짐**으로 둔다. 빌드 설정 값 `DEARBY_ADS_ENABLED`(`generate_project.rb` 환경 변수 → Info.plist `DearbyAdsEnabled`, 기본 `false`; Debug·Release 둘 다)가 `true`일 때만 광고 단위와 함께 `QueueAdLoader.enabled`가 참이다. 꺼져 있으면 광고 카드·동의 시트·ATT 창·설정의 광고 동의 스위치가 모두 없고 `queue_ad_shown`·`queue_ad_skipped_unloaded`·`att_prompt_result`도 보내지 않는다. 코드·동의 흐름·SDK·Info.plist 키(`GADApplicationIdentifier`·`SKAdNetworkItems`·`NSUserTrackingUsageDescription`)는 그대로 둔다(스토어 라벨·처리방침 답안은 SDK가 들어 있는 한 유지). **다시 켤 때**(사용자 결정): 배포 워크플로 `ios-testflight.yml`·`android-play-release.yml`의 빌드 단계 환경에 `DEARBY_ADS_ENABLED=true`를 넣어야 한다(지금 워크플로에는 없고, 이 절은 워크플로를 고치지 않는다; `native.yml` 검사 빌드는 꺼진 채로 둔다). 꺼져 있으면 **설정의 광고 동의 스위치도 숨긴다** — 부작용으로 꺼진 동안 이미 서버에 남은 광고 동의를 앱 안에서 철회할 수 없다(켜면 다시 보인다). Android는 같은 이름의 플래그로 PR #429가 맞췄다.
- **식별자 주입**: AdMob 앱 ID와 네이티브 광고 단위 ID는 플랫폼별 빌드 설정으로 넣고 값은 GitHub 저장소 변수(iOS `ADMOB_IOS_APP_ID`·`ADMOB_IOS_QUEUE_NATIVE_UNIT_ID`, Android `ADMOB_ANDROID_APP_ID`·`ADMOB_ANDROID_QUEUE_NATIVE_UNIT_ID`; 비밀값이 아니라 `vars`)에 둔다. 저장소에 실제 ID를 커밋하지 않는다. Debug는 Google 공식 테스트 ID([Android](https://developers.google.com/admob/android/test-ads)·[iOS](https://developers.google.com/admob/ios/test-ads), 2026-10-25 확인): 앱 ID Android `ca-app-pub-3940256099942544~3347511713`·iOS `ca-app-pub-3940256099942544~1458002511`, 네이티브 단위 Android `ca-app-pub-3940256099942544/2247696110`·iOS `ca-app-pub-3940256099942544/3986624511`(동영상 확인은 각 페이지의 Native Video 단위). Release 빌드에 값이 없으면 SDK를 시작하지 않고 광고 카드를 늘 생략한다(앱은 정상 동작, 끝 화면으로 바로).
  - 크래시 주의(각 플랫폼이 처리): Android 레거시 SDK는 매니페스트 `com.google.android.gms.ads.APPLICATION_ID` 메타데이터가 없으면 앱 시작 때 자동 초기화 ContentProvider가 예외를 던진다 — `initialize`를 부르지 않는 것만으로는 막히지 않는다. 값이 없는 빌드는 그 provider를 매니페스트에서 빼거나(`tools:node="remove"`) 다른 방법을 쓴다(빈 문자열·자리표시자 동작은 **확인 필요**, 실기기로). iOS는 `GADApplicationIdentifier`가 없을 때 SDK를 시작하거나 광고를 요청하면 예외가 난다고 알려져 있어 값이 없으면 `MobileAds.shared.start`·광고 요청 코드를 아예 타지 않게 한다(**확인 필요**). Android SDK는 레거시(유지 모드)와 GMA Next-Gen 중 플랫폼이 고른다(데이터 공개 내용은 두 판이 같다).
- **Android 매니페스트**: `com.google.android.gms.permission.AD_ID`의 `tools:node="remove"`를 해제해 권한을 유지한다(Play 콘솔 "광고 ID 사용: 예"와 맞춘다). `ACCESS_ADSERVICES_AD_ID`·`ACCESS_ADSERVICES_ATTRIBUTION`·`ACCESS_ADSERVICES_TOPICS`(광고 SDK가 병합; 제거 목록에 더한다)는 Privacy Sandbox 개인화·기여 측정용이라 비개인화와 맞지 않으므로 **계속 제거**한다(사용자 결정 전 기본값; 되돌릴 수 있음). Firebase Analytics 쪽 `google_analytics_adid_collection_enabled=false`·`google_analytics_ssaid_collection_enabled=false`·`google_analytics_default_allow_ad_personalization_signals=false`는 그대로다(사용 통계는 계속 광고 ID를 쓰지 않는다). `apps/android/README.md`의 "AD_ID 권한 제거" 설명을 함께 고친다.
- **iOS Info.plist**: `GADApplicationIdentifier`(빌드 설정 변수), Google이 요구하는 `SKAdNetworkItems`(목록은 [iOS 빠른 시작](https://developers.google.com/admob/ios/quick-start)·[iOS 개인정보 전략](https://developers.google.com/admob/ios/privacy/strategies)의 최신 목록을 넣을 때 그대로 옮기고 확인 날짜를 커밋에 적는다). `NSUserTrackingUsageDescription`(위 "iOS ATT" 문구). Firebase용 `GOOGLE_ANALYTICS_*` 끔 키는 그대로. 광고 SDK의 개인정보 매니페스트(13.11.0 `PrivacyInfo.xcprivacy`)가 들어오므로 아카이브의 Privacy Report로 라벨을 맞춘다.
- **통계**: [analytics-v1.md](analytics-v1.md) "맞춤 대기열 도달"에 `queue_ad_shown`·`queue_ad_skipped_unloaded`를 더한다. 클릭·노출 수익은 AdMob 보고서로 본다. 이 이벤트는 설정 `사용 통계 보내기`를 따르지만 AdMob 자체의 광고 노출·측정은 그 설정과 무관하다(설정 문구가 광고까지 끄는 것처럼 읽히지 않게 한다).
- **바꿔야 할 문서·답안(게시 전 사용자 확인)**:
  - 개인정보처리방침: 제3자 제공 Google(AdMob)(처리 위탁이 아니라 제3자 제공으로 적는다, 2026-10-25 사용자 결정) — 항목 기기·광고 식별자(Android 광고 ID·앱 세트 ID, iOS 추적 허용 시 IDFA, 그 밖에는 IDFV), IP 주소(대략적 위치 추정), 광고 상호작용(노출·클릭·동영상 재생), 진단 정보 / 목적 비개인화 광고 표시·빈도 제한·광고 보고·부정 클릭 방지 / 거부 방법 Android 설정의 광고 ID 재설정·삭제, iOS ATT 창에서 거부하거나 설정 › 개인정보 보호 및 보안 › 추적에서 Dearby 끄기(IDFA를 보내지 않음, IDFV는 막지 못함). 위 "맞춤 대기열" 절의 처리방침 추가와 함께 게시한다.
  - 스토어: Play "앱에 광고 포함" 예, "광고 ID" 선언 예(광고), 데이터 보안 공유 예(Google AdMob); App Store 개인정보 라벨에 제3자 광고 용도 항목, 기기 ID "추적에 사용: 예"(2026-10-25 사용자 결정) — 표와 근거는 [광고 스토어 답안](../../docs/store/ads-2026-10-25.md). 스토어 등록 문안의 "광고 식별자·추적 없음"·심사 노트의 사용 통계 문장을 "사용 통계는 광고 ID를 쓰지 않음, 광고는 비개인화 AdMob"으로 고친다.
  - 운영: `https://dearby.wid.io.kr/app-ads.txt`에 AdMob 게시자 줄을 게시하고, 스토어 게시 뒤 AdMob 콘솔에서 앱을 스토어와 연결·검토 받는다. 콘솔 분류 차단(교육·채용만 남김)은 별도 작업.

## 광고 목적 개인정보 제3자 제공 동의(선택) — 2026-10-25 사용자 결정

사용자: "AdMob 광고는 Google LLC에 대한 개인정보 제3자 제공으로 보고 선택 동의를 받는다. 동의한 사용자에게만 광고를 요청·표시하고, 미동의·철회 사용자는 광고 SDK를 시작하지도 요청하지도 않는다(서비스 이용에 불이익 없음)." + "모든 동의 항목은 실제로 그 정보가 수집되는 지점 바로 직전에 구한다(Just-in-time)." 위 "맞춤 대기열 끝 광고" 절의 로드·ATT·요청 설정은 모두 이 동의 **뒤에만** 일어난다.

- **성격**: 선택 동의. 동의하지 않거나 철회해도 모든 기능을 그대로 쓰고, 맞춤 대기열 끝 광고 카드만 없다(마지막 활동 카드 다음 바로 끝 화면). 광고 동의는 프로필 필수 동의("개인정보 동의")와 묶지 않는다(서로의 전제가 아니다).
- **묻는 시점(JIT)**: 맞춤 대기열에서 **마지막 활동 카드를 넘길 때(광고 자리 직전)** 묻는다(2026-10-26 사용자 결정, 이전에는 대기열 응답 직후 첫 카드에서 물었다). 순서: 대기열 응답 → `GET /v1/me/consents/ad-third-party` → `agreed`이면 지금처럼 SDK 시작 → (iOS) ATT `.notDetermined`이면 ATT 창 → 광고 미리 요청. `agreed=false`이면 대기열을 열 때는 아무것도 묻지 않고 SDK·ATT·광고 요청도 없다. 그 뒤 마지막 활동 카드에서 넘겨 끝 화면으로 갈 때, 이 기기에서 이 계정·이 버전으로 거부한 기억이 없으면 끝 화면 위에 동의 시트 → `동의하고 계속`이면 `PUT` 뒤 SDK 시작 → (iOS) ATT. 그 열람은 광고를 기다리지 않고 끝 화면 그대로이며(지나간 광고 자리에 쓰지 않을 광고는 요청하지 않는다) 다음 열람부터 광고가 나온다. `동의하지 않음`·시트 닫기면 SDK 시작·ATT 창·광고 요청 모두 없이 끝 화면을 그대로 본다. 수집 직전 원칙은 그대로다 — 광고 요청은 동의 뒤에만 일어난다. 거부는 기기에만 기억해(계정·버전별) 다시 띄우지 않고, 바꾸려면 프로필 스위치를 쓴다(서버에 거부 기록을 남기지 않는다; 다른 기기에서는 한 번 더 물을 수 있다). GET이 실패하면 미동의로 보고 광고 없이 진행한다(사용자를 기다리게 하지 않는다). 앱 시작·로그인·발견 탭에서는 묻지 않는다. 광고가 꺼진 빌드(Release에 광고 ID 없음, 또는 위 "광고 끄기" 플래그 — 2026-10-26부터 기본)는 시트도 띄우지 않는다.
- **시트 문구 요소(문구 게시는 사용자 확인 뒤)**: 제목 `광고를 보여 드려도 될까요?`, 한 줄 설명 `맞춤 대기열 끝에 광고 1장을 보여 드리려면 아래 정보를 Google에 제공해야 해요. 맞춤 광고는 아니에요.` 그리고 표 다섯 줄:
  - 제공받는 자: **Google LLC**(AdMob)
  - 제공 목적: 앱 안 광고 표시(비개인화), 같은 광고 노출 횟수 제한, 광고 성과 측정, 부정 클릭 방지
  - 제공 항목: 광고 식별자(Android 광고 ID·앱 세트 ID, iOS는 추적 허용 시 IDFA, 그 밖에는 IDFV), IP 주소(대략적 위치 추정), 기기 정보(모델·OS·언어), 광고 상호작용(노출·클릭·동영상 재생), 진단 정보
  - 보유·이용 기간: **제공받는 자(Google)의 개인정보처리방침에 따름**(`전문 보기`에 Google 정책 링크)
  - 국외 이전: 미국(Google LLC), 광고를 요청할 때 네트워크로 전송 — 개인정보 보호법 국외 이전 고지 항목(이전받는 자·국가·시기와 방법·항목·목적·기간·거부 방법)을 처리방침 `광고` 항목에 함께 둔다(판단: 같은 데이터가 미국으로 가므로 함께 고지; 법률 검토는 확인 필요)
  - 거부 권리: `동의하지 않을 수 있어요. 동의하지 않아도 모든 기능을 그대로 쓸 수 있고, 광고가 표시되지 않을 뿐이에요.`
  - 버튼: `동의하고 계속`(기본)·`동의하지 않음`(같은 크기, 같은 눈에 띄는 정도 — 거부를 숨기지 않는다). `[선택]` 표시와 `전문 보기`(웹 `/privacy`의 광고 항목을 인앱 브라우저로). iOS는 기본 시트(`.presentationDetents`), Android Material 3 `ModalBottomSheet`.
- **버전**: `2026-10-25`(서버 상수 `AdConsent.VERSION`, 앱이 같은 값을 보낸다). 시트 문구(제공받는 자·목적·항목)를 바꾸면 버전을 올리고, 옛 버전 기록은 `agreed=false`가 되어 다음 대기열에서 다시 묻는다(그전까지 광고 없음). 처리방침 버전(`dearby.consent.privacy-version`)과 따로 움직인다(지원 자격 정보 동의 `2026-10-24`와 같은 방식).
- **철회·다시 켜기**: 프로필 설정에 스위치 `광고 목적 정보 제공`(보조 글자 `끄면 광고가 보이지 않아요`, iOS `Toggle`, Android Material 3 `Switch`). 켜면 위 시트 내용을 다시 보여 주고 `동의하고 계속`일 때 `PUT`, 끄면 바로 `DELETE`(되돌리기 없이). 끄면 그 실행에서 더 광고를 요청하지 않고 받아 둔 광고를 버린다(`destroy`). 이미 시작한 SDK는 앱 프로세스가 끝날 때까지 남을 수 있고 다음 실행부터 시작하지 않는다(SDK에 끄는 API가 없다). iOS의 ATT 답은 OS 설정이라 이 스위치가 바꾸지 않는다(스위치를 끄면 광고 요청 자체가 없어 IDFA도 가지 않는다).
- **SDK를 시작하지 않기(구현)**: 앱 시작 때 `MobileAds.initialize`/`MobileAds.shared.start`를 부르지 않고 위 순서에서만 부른다. 광고 SDK가 명시 시작 전에 앱 측정을 시작하지 않게 iOS Info.plist `GADDelayAppMeasurementInit = YES`(Google 문서 "delay app measurement until you explicitly initialize the Mobile Ads SDK or load an ad", 2026-10-25 검색 확인). Android는 앱이 동의 전 `MobileAds.initialize`를 부르지 않는 것으로 충분하다(SDK의 자동 ContentProvider는 초기화·네트워크 요청을 하지 않는다, PR #375 조사; 근거 없는 `DELAY_APP_MEASUREMENT_INIT` 메타데이터는 두지 않는다). 실기기에서 동의 전 `googleads`·`app-measurement` 요청이 없는지 네트워크로 확인한다. 미동의 상태에서 광고 관련 네트워크 요청이 0건이어야 한다.
- **서버 API(Bearer, 로그인만)**: `GET /v1/me/consents/ad-third-party` → `{consent: {version, agreedAt}|null, agreed: boolean}`(`agreed`는 기록의 `version`이 현재 버전일 때만 true). `PUT` 같은 경로 `{version}` → 같은 모양(현재 버전과 다르면 400 `CONSENT_VERSION`; 같은 버전에 다시 동의하면 처음 `agreedAt`을 지킨다). `DELETE` 같은 경로 → 204(없어도 204). 비로그인 401. `GET /v1/profile`에는 싣지 않는다(대기열에서 읽는 값이고 프로필 탭과 시점이 다르다).
- **저장**: V57 `user_ad_consents(user_id pk → users on delete cascade, version, consented_at timestamptz)`. 철회하면 행을 지운다(철회 시각은 남기지 않는다; 필요해지면 `withdrawn_at`을 더한다). 계정 삭제와 함께 지워진다.
- **바꿔야 할 문서(게시 전 사용자 확인)**: 처리방침의 Google(AdMob) 제3자 제공 항목에 "선택 동의를 받은 경우에만 제공, 거부해도 불이익 없음, 철회는 프로필 설정"과 국외 이전 고지를 더한다(열린 PR #344 문구를 이것에 맞춘다). Play 데이터 보안의 광고 데이터 항목은 "사용자가 선택할 수 있음"으로, 스토어 심사 노트에 "광고는 앱 안 선택 동의 뒤에만"을 더한다.
- **동의 시점 원칙(JIT) 점검**: 처리방침·약관 필수 동의는 프로필 첫 저장 전(이름·연락처·이력 수집 직전), 지원 자격 정보 동의는 그 입력 직전, 알림 권한은 첫 프로그램 하트(토큰은 권한 허용 뒤에만 서버로), 기기 캘린더는 겹침 확인·`캘린더에 추가`에서 OS 권한·시스템 화면으로(기기 안에서만 비교, 서버로 보내지 않음), 광고는 이 절. 로그인 때 저장되는 계정 식별자(Apple·Google `sub`, 이메일, 애플 이름)와 Firebase 사용 통계(앱 시작부터 기본 켬)는 동의 없이 처리방침 고지로 처리한다 — 근거와 남은 판단은 2026-10-25 동의 점검 기록(PR 본문).

## 태그 아이콘+라벨·분야 1개·자동 펼침 없음 — 2026-10-24 사용자 결정

사용자(실기기 확인 뒤): "목록에서 자동으로 아코디언 펼치지 말았으면 좋겠어. 아이콘이 직관적이지 않아. 태그를 아이콘라벨로 바꿔줘. 분야는 1개만 보여줘." 위 "분야·성격 태그와 아이콘" 절의 표시 규칙을 이렇게 바꾼다(데이터·API·아이콘 매핑은 그대로).

- **발견 프로그램 카드**: 로고 밑 세로 분야 배지를 없앤다. 조직 이름 아래 한 줄에 **아이콘+글자 칩**으로 분야 **1개**(`fieldTags[0]`, 민트 바탕·청록 글자) 다음 성격 칩(보조색 바탕) 순서. 한 줄에 들어가는 만큼만 보이고 넘치면 `+n`(줄바꿈하지 않음). 성격 아이콘만 있는 줄은 없앤다. 펼친 활동 행도 같은 칩 줄(분야 1개 + 성격). 접근성은 칩 글자를 그대로 읽는다.
- **활동 상세**: 그대로(분야·얻을 수 있는 것 두 묶음, 아이콘+글자). 분야는 상세에서만 전부 보인다.
- **자동 펼침 없음**: 발견 목록은 어떤 경우에도 프로그램 카드를 저절로 펼치지 않는다(첫 진입·검색 결과·추천·딥링크 복귀 포함). 펼침은 사용자가 카드를 누를 때만. 검색이 활동 이름에 맞은 프로그램은 펼치지 않고 카드에 `맞는 활동 n개` 보조 글자로 알린다.
- **추천 퍼널 분야 단계(상위 → 하위, 2026-10-24 사용자 "상위 분야에서 복수선택을 하고 각각의 퍼널을 추가" → "네")**: 1) `어떤 분야에 관심 있어요?` — 상위 분야(하위가 있는 분야: IT·경제·경영·공학)와 상위가 없는 분야(봉사·독서·토론·영상)를 한 화면에서 여러 개 고른다(아이콘+글자 칩). 2) 고른 상위 분야마다 하위 질문 한 화면 `{상위} 중에서는 어떤 분야요?` — 맨 위 `{상위} 전부 좋아요`(기본 선택, 상위 태그 id로 저장) + 하위 분야들(고르면 `전부 좋아요`가 풀리고 그 하위 id들로 저장). 하위가 없는 분야는 하위 질문이 없다. 상위 분야를 3개 이상 고르면 하위 질문을 한 화면에 상위별 묶음으로 모은다. 진행 막대는 늘어난 질문 수에 맞춘다. 다시 열면 저장된 id로 상위·하위 선택을 복원한다(하위 id만 있으면 그 상위를 1단계에서 고른 것으로 본다). 저장 모양(`fieldTagIds`)과 서버는 바뀌지 않는다. (2026-10-24 iOS 실기기에서 분야 화면에 IT 묶음만 보이던 결함은 이 흐름으로 화면이 바뀌어도 원인을 기록한다.)

## 조직 로고와 로고 계승 — 2026-10-22 사용자 결정

사용자: "각 프로그램별 로고 추가해 주고 없으면 상위 조직 로고 계승해 줘."

- `ProgramSummary.logoUrl`(발견 프로그램 묶음·활동 상세의 프로그램)은 **가장 가까운 로고**다: 프로그램 로고 → 프로그램의 조직 로고 → 그 조직의 상위 조직 로고 → … 순으로 처음 있는 것. 모두 없으면 null(앱은 지금처럼 첫 글자 아바타). 앱·웹 코드는 바뀌지 않는다.
- 조직 로고 공개 주소: `/v1/organizations/{id}/logo?v={updatedAt epoch 초}`(API origin 기준, 프로그램 로고와 같은 캐시 규칙, 인증 없음, 없으면 404).
- 상위 조직 사슬은 최대 5단계까지만 따라간다(순환 방지).

## 프로그램 로고·태그 칩 줄 제거·탭 제목 통일 — 2026-10-19 밤 사용자 결정

사용자: "로고도 어드민에서 추가할 수 있게 해줘. 태그는 직접 보여주지 말고 검색 조건으로 활용되게 해줘(검색 밑에 표시되는 공간 얘기)." "위쪽 탭바에 여백이 너무 많아서 활동 둘러보기가 탭바 위치로 갔으면 좋겠어. 되도록 노치 아래 여백이 작았으면 해." "각 페이지마다 좌상단에 노출되는 메뉴 이름이 통일성이 없어. 내 명함 페이지를 기준으로 맞춰줘."

- **프로그램 로고**: `ProgramSummary.logoUrl: string | null`. 값은 API origin 기준 경로 `/v1/programs/{id}/logo?v={updatedAt epoch 초}`이고 로고가 없으면 null. `GET /v1/programs/{id}/logo`는 인증 없는 공개 이미지 응답(`Content-Type`은 저장한 종류, `Cache-Control: public, max-age=31536000, immutable` — `v`가 바뀌어 캐시를 깨뜨린다, 없으면 404). 저장·업로드는 admin-v1.md "프로그램 로고". 발견 카드 로고 자리(44, 모서리 14)에 꽉 채워(aspect fill) 보이고 로딩 중·실패·null은 이름 첫 글자 아바타. iOS `AsyncImage`, Android Coil `AsyncImage`, 웹 `<img>`(Next 프록시에 공개 경로 `programs/:id/logo`를 허용하고 바이너리와 캐시 헤더를 그대로 넘긴다). 프로그램이 바뀌지 않는 한 같은 URL이라 목록 재조회 때 다시 받지 않는다.
- **태그 칩 줄 제거**: 발견 검색창 아래 태그 칩 줄(`GET /v1/tags` 다중 선택 → `tagIds`)을 iOS·Android·웹에서 없앤다. 태그는 **검색 조건**으로만 쓴다: 검색어 `q`가 태그 이름(한글·영문)에 맞으면 그 프로그램·활동이 나온다(이미 서버 규칙, 변경 없음). `tagIds` 파라미터와 `GET /v1/tags`는 API에 남기고 앱·웹은 보내지 않는다. 프로그램 카드와 활동 상세의 태그 칩은 그대로 둔다(사용자: 카드 얘기가 아니다).
- **탭 제목 통일·머리 간소화**: 다섯 탭(발견·내 활동·내 명함·받은 명함·프로필)의 첫 화면은 모두 **내 명함 페이지의 제목 줄**과 같다: 왼쪽 정렬 굵은 제목(iOS `.title.bold()`, Android는 내 명함과 같은 스타일), 좌우 여백 20, 안전 영역(노치·상태 표시줄) 바로 아래 위 여백 8, 제목 줄 뒤 바로 내용(발견은 검색창·토글). 큰 제목 영역·브랜드 머리·중복 머리글은 없앤다. 각 플랫폼 `shared/ui`의 공통 제목 컴포넌트 하나로 다섯 탭을 그린다(iOS 새 `PageTitle`, Android 기존 `ScreenHeader` 정리). 발견 탭 제목은 `활동 둘러보기`. 뒤로 가기가 있는 하위 화면(활동 상세·QR·이력 입력 등)은 기존 inline 내비게이션 제목을 유지한다. 웹은 변경 없음.

- **색 체계(2026-10-19 밤, 사용자 "프라이머리·포인트 색이 아이폰 테마 색과 조합이 좋지 않다" → 추천안 A 승인 "적용해줘")**: 중립색은 플랫폼 시스템에 맡기고 청록(#007F80, 로고 색)은 **하나의 액센트**로만 쓴다. 직접 정한 중립 토큰(배경 #F3F5F6·선 #E3E8EA·보조 글자 #657078·본문 #172027·민트 #F0FCFA·soft)은 없앤다.
  - iOS: 배경 `Color(.systemGroupedBackground)`, 카드·입력 면 `Color(.secondarySystemGroupedBackground)`, 글자 `.primary`/`.secondary`, 구분선 `Color(.separator)`, 비활성 칩·마감 배지 `Color(.tertiarySystemFill)`, 청록은 `.tint(DearbyStyle.teal)`과 채워진 강조에만. 민트 바탕은 `DearbyStyle.teal.opacity(0.12)`(글자 청록). 다크 모드는 시스템 색이 따라오므로 앱이 라이트 모드를 강제하던 설정이 있으면 유지 여부를 그대로 둔다(이번 범위 밖).
  - Android: `lightColorScheme`/`darkColorScheme`을 청록 시드(#007F80)의 Material 3 톤으로 정의한다(primary 청록, primaryContainer 연청록, surface/background·surfaceContainer·outlineVariant·onSurfaceVariant는 시드에서 나온 중립 톤). 상수 `Mint`·`Soft`·`Line`·`Quiet`·`Ink`는 `colorScheme.primaryContainer`·`surfaceContainer`·`outlineVariant`·`onSurfaceVariant`·`onSurface`로 바꾸고 상수를 지운다. `Teal`은 `colorScheme.primary`로 읽는다. 다이내믹 컬러는 켜지 않는다(브랜드 유지).
  - **청록 채움 면 위 글자는 흰색(사용자 2026-10-19 밤 "진한 청록색이 배경인 경우 폰트색을 흰색이 되도록")**: 버튼·배지·칩·아바타처럼 청록으로 채운 면 위의 글자·아이콘은 라이트·다크 모두 **흰색**이다. 다크 모드에서도 채움 면은 진한 청록 `#007F80`(토큰 `tealFill`, 두 모드 같은 값; 흰 글자 대비 4.8:1)을 쓰고, 어두운 배경 위의 글자·아이콘·테두리 액센트만 밝은 청록 `#3FB3B4`(`teal`)를 쓴다. 이전의 다크 `onTeal #0C4848`(어두운 글자)·Android 다크 `onPrimary #0C4848`은 폐기한다. iOS: `DearbyStyle.tealFill`(고정 #007F80) + `onTeal = .white`, 채움 버튼은 `.tint(tealFill)`. Android: `colorScheme.primary`는 텍스트·아이콘 액센트(라이트 #007F80, 다크 #3FB3B4)로 두고, 채움 컴포넌트(`DearbyButton`, `Badge`, 채운 칩, `InitialAvatar` filled)는 `TealFill`(#007F80)·`Color.White`를 명시한다(다크 `onPrimary`는 더 쓰지 않음).
  - **다크 모드(사용자 "다크모드도 호환되게 해줘")**: 앱이 라이트 모드를 강제하던 설정은 지우고 시스템 설정을 따른다. 하드코딩한 `.white`/`.black`·#FFFFFF 면은 시스템·테마 색으로 바꾼다. 청록은 다크에서 밝은 변형(라이트 #007F80 → 다크 #3FB3B4)을 쓴다: iOS는 Asset Catalog 색 또는 `UIColor { trait in … }`로 `DearbyStyle.teal`에 두 값을 넣고, Android는 `darkColorScheme`의 primary에 둔다. 상태 표시줄·탭 막대·시트는 시스템 모양을 따른다. 로고 심볼은 청록 단색이라 다크에서 밝은 청록으로 그린다. 웹도 `prefers-color-scheme: dark`에서 토큰(배경·카드·글자·선·청록 변형)을 재정의하되 디자인은 바꾸지 않는다.

- **활동 상세 이미지 섹션 제거(2026-10-19 밤, 사용자 "UI는 만족스러워. 활동 상세 페이지에 이미지 섹션 없애줘도 좋을 것 같아")**: 활동 상세의 머리 이미지(iOS `ActivityArtwork`, Android `ActivityImage`, 웹 `.detail-image`)를 iOS·Android·웹에서 없앤다. 제목·조직·상태 칩이 바로 위에 온다. API `Activity.imageUrl`은 그대로 두고 클라이언트가 보여 주지 않을 뿐이다. 목록·다른 화면에서 쓰지 않게 된 이미지 컴포넌트는 지운다.

## 발견 탭 스크롤 동작 — 2026-10-19 밤 사용자 결정

사용자: "활동 둘러보기 아래로 스크롤할 때 활동 둘러보기랑 알림 아이콘은 고정해 주고, 검색창·마감 숨기기는 위로 숨겨졌다가 아래로 스크롤할 때 보이도록 해줘. 최상단에서 풀투 리프레시 지원해줘. 플로팅 버튼으로 최상단으로 즉시 스크롤할 수 있게 해줘." iOS·Android. 웹은 변경 없음.

- **알림 아이콘은 다섯 탭 모두 우상단 고정(사용자 2026-10-19 밤 "알림 표시는 탭바로 접근할 수 있는 모든 곳에서 우상단에 고정")**: 발견·내 활동·내 명함·받은 명함·프로필의 제목 줄(PageTitle/ScreenHeader) 오른쪽 끝에 같은 알림 버튼(`bell`/`Notifications`, 44 터치 영역, 접근성 이름 `알림`)을 둔다. 다른 보조 행동(내 활동 `예시` 배지, 프로필 `변경 이력`·`명함 만들기`)은 알림 버튼 왼쪽에 둔다. 제목 줄은 스크롤과 무관하게 고정이므로 알림도 항상 보인다. 알림 화면은 아직 자리만(기존 안내 문구).
- **고정 제목 줄**: `활동 둘러보기` 제목과 알림 아이콘(PageTitle/ScreenHeader)은 스크롤과 무관하게 항상 위에 고정된다. 배경은 페이지 배경과 같고, 내용이 밑으로 지나가면 얇은 구분선(iOS `Color(.separator)`, Android `surfaceContainerHighest`)이 나타난다.
- **접히는 검색 영역**: 검색창과 `마감 숨기기` 토글 묶음은 아래로 스크롤하면 위로 밀려 접히고(높이 0, 투명도 0), 위로 스크롤하기 시작하면(방향 전환) 즉시 다시 펼쳐진다 — 맨 위까지 올라가지 않아도 보인다(One UI·Material "enter always" 방식). 애니메이션은 스크롤 오프셋에 비례하되 짧은 스프링으로 마무리하고, 동작 줄이기에서는 즉시 전환. 검색창에 포커스가 있는 동안은 접지 않는다. iOS는 `ScrollView` + `onScrollGeometryChange`(iOS 18)로 방향·오프셋을 읽고, Android는 `NestedScrollConnection`으로 소비 오프셋을 누적한다(`TopAppBar`의 `enterAlwaysScrollBehavior`와 같은 규칙; 제목 줄은 고정이라 TopAppBar 자체는 쓰지 않는다).
- **풀투 리프레시**: 맨 위에서 당기면 프로그램 목록을 처음부터 다시 조회한다(펼침 상태는 유지하되 펼친 프로그램의 활동도 다시 받는다). iOS `.refreshable`, Android Material 3 `PullToRefreshBox`. 당기는 동안 제목 줄은 움직이지 않고 표시기는 검색 영역 아래에 나온다. 실패하면 기존 오류 표시 규칙을 따른다.
- **맨 위로 버튼**: 첫 화면 높이 이상 내려가면 오른쪽 아래(탭 막대 위 16, 오른쏙 20)에 둥근 플로팅 버튼(iOS: 시스템 원형 버튼 `.borderedProminent` + `.buttonBorderShape(.circle)` + `.controlSize(.large)`에 `arrow.up` — 사용자 2026-10-19 밤 "iOS는 기본 플로팅 버튼 UI 활용"으로 직접 그린 원·그림자는 쓰지 않는다; Android: Material 3 `SmallFloatingActionButton`, `Icons.Rounded.ArrowUpward`)이 페이드로 나타나고, 누르면 애니메이션 없이 즉시 맨 위로 간다(검색 영역도 펼쳐진다). 접근성 이름 `맨 위로`. 맨 위 근처에서는 숨긴다. iOS의 상태 표시줄 탭으로 맨 위 가기는 그대로 둔다.

## 폼은 플랫폼 기본 컴포넌트 — 2026-10-19 밤 사용자 결정

사용자: "폼 같은 경우 각 플랫폼 기본 폼을 사용해줘. 뭔가 높이나 폰트가 앱에서 누르기는 힘들어 보여." 직접 만든 입력 컨트롤(iOS `DearbyInlineField`의 밑줄형 작은 필드, Android `Controls.kt`의 사용자 정의 필드 등)을 없애고 플랫폼 기본 폼을 쓴다. 범위: 프로필 입력 퍼널·프로필 편집, 이력 입력 퍼널, 명함 만들기/고치기, 연락처 추가, 직무 선택, 로그인 시트, 동의 화면, 발견 검색창. 웹은 변경 없음.

- **iOS**: 입력 화면은 `Form`(또는 `List`) + `.insetGrouped` 섹션으로 구성하고 행에는 `TextField`·`SecureField`·`Toggle`·`Picker`·`DatePicker`·`LabeledContent`를 그대로 둔다. 글꼴은 시스템 텍스트 스타일(`.body`, Pretendard 매핑은 크기 17 유지), 행 최소 높이 44, 섹션 제목은 `Section("…")`, 도움말은 `footer`. 여러 줄 입력은 `TextField(axis: .vertical)` + `lineLimit(3...)`. 검색창은 `.searchable` 대신 발견 페이지의 고정 제목 구조를 지키므로 `TextField`를 `.textFieldStyle(.roundedBorder)` 대신 **둥근 `Color(.tertiarySystemFill)` 바탕 높이 44의 시스템 검색 모양**(`magnifyingglass` + 지우기 버튼)으로 둔다. 주 버튼은 `.borderedProminent` + `.controlSize(.large)`(높이 50). 키보드 종류·`submitLabel`·`textContentType`을 지정한다. 오류 문구는 행 아래 `.footnote` 빨강.
- **Android**: Material 3 `OutlinedTextField`(기본 높이 56dp, `bodyLarge`, `label`·`supportingText`·`isError`), 여러 줄은 `minLines`, 선택은 `ExposedDropdownMenuBox`, 날짜는 `DatePickerDialog`, 스위치는 `Switch` + `ListItem`, 주 버튼은 `Button` 기본 높이 48 이상(`ButtonDefaults`), `KeyboardOptions`/`KeyboardActions` 지정. 사용자 정의 높이·폰트 크기를 두지 않는다. 검색창은 `OutlinedTextField`가 아니라 Material 3 `SearchBar`의 입력 필드 모양(`SearchBarDefaults.InputField`, 높이 56, 둥근 바탕)을 쓴다.
- 공통: 터치 영역은 iOS 44pt·Android 48dp 이상, 라벨은 항상 보이는 라벨(placeholder만으로 설명하지 않음), 필수 표시는 라벨 끝 `*`가 아니라 도움말 문장. 기존 입력값·검증·저장 흐름은 바꾸지 않는다.
- **키보드 내리기(사용자 "키보드가 올라왔을 때 폼을 제외한 다른 곳 클릭 시 내려가게")**: 입력 필드 밖을 누르면 키보드가 내려간다. 스크롤로도 내려간다. iOS: 화면 루트에 `.scrollDismissesKeyboard(.interactively)`와, 빈 곳 탭 때 `@FocusState`를 `nil`로 만드는 탭 제스처(버튼·링크·필드의 탭은 그대로 전달, `simultaneousGesture` 아님 — 배경 전용). 앱 전체 입력 화면에 공통 modifier(`shared/ui`)로 적용한다. Android: 입력 화면 루트 `Modifier.pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }`(필드·버튼은 자기 탭을 먼저 받으므로 영향 없음) + LazyColumn/Column 스크롤에 `shared/ui`의 `dismissKeyboardOnScroll()`(손가락이 터치 슬롭 넘게 끌리면 `clearFocus()`, 이벤트는 소비하지 않음; 길게 누른 뒤 끌기는 제외). `imePadding`은 앱 셸이 한 번만 적용한다. `imeNestedScroll`은 쓰지 않는다 — 목록 끝에서 더 끌어올리면 포커스 없이도 키보드가 올라와 탭 바가 사라졌다(2026-10-26 사용자 보고). 포커스된 `DearbyField`는 키보드가 올라오는 동안 행 전체(라벨·칸·도움말)를 키보드와 하단 막대 위로 보이게 한다. `CardComposer`는 명함 템플릿 제거 PR 뒤 같은 modifier로 바꾼다. 발견 검색창도 같다.

## 뒤로 가기 — 2026-10-25 사용자 결정

사용자: "뒤로가기 버튼은 어지간하면 지원해줄 수 있을까? 프로필 만들기 퍼널 등." 화면을 쌓은 곳은 플랫폼 뒤로가 이전 화면으로 간다(iOS 내비게이션 바 뒤로·가장자리 스와이프, Android 상단 바 뒤로 화살표·시스템 뒤로 제스처).

- **퍼널**(한 화면에 질문 하나: 추천 설정·지원 자격 정보·이력 입력): 뒤로 = 이전 질문, 입력값은 그대로. iOS는 단계를 퍼널 자신의 `NavigationStack(path:)`에 쌓는다. Android는 상단 화살표와 시스템 뒤로가 같은 함수(`BackHandler`)로 이전 질문에 가고, 퍼널 전체를 닫지 않는다. 첫 질문(저장된 이력은 요약)에서 뒤로 = 닫기이며 바꾼 답이 있으면 "작성 중인 내용을 버릴까요?"(버리기·취소, iOS `confirmationDialog`·Android `AlertDialog`)를 먼저 묻는다. iOS 시트는 바꾼 답이 있는 동안 끌어내려 닫기를 막는다.
- **프로필 만들기**는 탭 첫 화면의 아코디언(2026-10-17 결정)이라 쌓인 화면이 없다: `다음` 옆 `이전`과 Android 시스템 뒤로가 앞 단계를 다시 연다. 첫 단계에서는 그 자리가 `나중에`이고 Android 뒤로도 같다: "프로필 만들기를 그만할까요? / 프로필은 나중에 프로필 탭에서 만들 수 있어요."(나중에 하기·계속 만들기) 뒤 발견 탭으로 가며 로그인과 입력값은 그대로다(프로필 없이도 로그인 상태로 앱을 쓸 수 있다; 명함 만들기는 "먼저 프로필을 적어 주세요").
- **스토리형 전체 화면**(맞춤 대기열): 뒤로 = 닫기(`queue_exit` 그대로). iOS 전체 화면 표시는 스와이프 닫기가 없으므로 `닫기` 버튼을 쓴다.

## 신청 확인 조건 완화·공개 applicationUrl — 2026-10-19 밤 사용자 결정

사용자: "활동 탐색에서 신청 사이트 들어간 후에 신청 여부를 확인하는 메시지를 표시해줘. 했다면 지난 기획처럼 내 활동에서 볼 수 있게 해줘." 기존 "신청 확인"(2026-10-06)은 **모집 중(open)일 때만** 버튼이 신청 링크가 되고 그때만 "신청하셨나요?"를 물었다. 운영 활동은 모집 기간이 없거나(우아콘·DAN) 예정이라 버튼이 `공식 안내 보기`로만 열려 질문이 한 번도 뜨지 않았다. 바꾼다:

- **공개 API**: `Activity.applicationUrl: string | null`을 더한다(운영자가 어드민에서 지정한 신청 링크 `activities.application_url`; 없으면 null). 수집기가 넣은 값도 그대로 노출한다. `https`만 허용(iOS·Android의 기존 `safeURL` 규칙).
- **버튼**: 상세 화면 CTA는 `applicationUrl ?? officialUrl`을 연다. 라벨은 모집 상태가 `closed`(모집 마감이 지났거나 활동이 끝남)면 `공식 안내 보기`, 그 외(`open`·`upcoming`·모집 정보 없음)는 `신청 사이트 열기`(applicationUrl이 있을 때) 또는 `공식 사이트에서 신청`(officialUrl만 있을 때).
- **질문 조건**: 모집 상태가 `closed`가 아니면 CTA를 눌러 외부 브라우저로 나간 뒤 앱이 다시 활성화될 때 "신청하셨나요?" 시트를 띄운다(open·upcoming·정보 없음 모두). closed면 묻지 않는다. 선택지·`다시 묻지 않기`·내 활동 표시 규칙은 2026-10-06 그대로. 이미 신청함인 활동은 묻지 않는다.
- **내 활동**: `신청했어요`를 고르면 2026-10-06 규칙대로 내 활동 탭의 진행 트랙에 들어간다(사용자 기록, 주최 측 확인 아님). 변경 없음.
- 웹: 변경 없음(웹에는 신청 확인 흐름이 없다).

## 일정 표시 우선순위 — 2026-10-19 밤 사용자 지적

사용자: "운영자 지정 날짜가 있음에도 클라이언트에서 수집 원문이 노출되고 있어." 원인: 세 클라이언트가 활동 기간·모집 기간을 그릴 때 `dateLabel`(수집 원문 문구)이 있으면 그것을 먼저 쓰고, 운영자가 지정한 구조화 날짜(`startDate`/`endDateExclusive`, `startAt`/`endAt`)를 뒤로 미뤘다. 서버는 운영자 지정 뒤에도 원문 `dateLabel`을 그대로 돌려준다(어드민 `기간 확인` 판정과 수집 피드백에 필요).

- **규칙(iOS·Android·웹 공통)**: Schedule을 글자로 보일 때 **구조화 날짜가 있으면 그것을 형식화**해 보인다(종일: `M월 d일 (요일)`·범위는 `~`, 연도는 **오늘(서울) 연도와 다른 날짜에만** 붙인다(`12월 30일 (수) ~ 2027년 1월 2일 (토)`); 시각: `M월 d일 (요일) HH:mm`, 같은 날 범위는 `M월 d일 (요일) 14:00 ~ 18:00`으로 날짜를 한 번만; 끝만 있으면 모집 기간은 `~ M월 d일 (요일) HH:mm 마감`, 활동 기간·세부 일정은 `~ M월 d일 (요일) HH:mm까지`). 모집 기간에 시작·끝이 모두 있을 때의 표기는 아래 "모집 날짜 표기 — 2026-10-26"이 대체한다. `dateLabel`은 **구조화 날짜가 전혀 없을 때만** 보이고, 그것도 없으면 `미정`/`일정 미확인`. 발견 활동 카드의 `모집`·`활동` 줄, 활동 상세의 `개최 일시`·모집 기간, 내 활동 행·진행 트랙 단계, 받은 명함·이력 선택의 활동 표시, 웹 활동 카드·상세 모두 같은 규칙.
- 서버·어드민은 바꾸지 않는다. 어드민은 원문과 지정값을 둘 다 보이는 기존 규칙 유지.

## 모집 날짜 표기 — 2026-10-26 사용자 결정

사용자: 모집 기간이 있으면 "모집 10.1 – 10.20 · D-3"처럼 기간과 D-day 배지만 보이고, 별도의 "10월 20일 마감" 줄은 없앤다. 마감일만 있으면 기존 "10월 20일 마감". 위 "일정 표시 우선순위"의 모집 기간 문구를 이 절이 대체한다(활동 기간·세부 일정 문구는 그대로).

- **모집 기간에 시작·끝이 모두 있으면**: `M.d – M.d`(en dash 앞뒤 공백, **시각은 빼고** 날짜만). 시각 일정은 그 시간대, 종일 일정은 서울 날짜이고 끝은 `endDateExclusive` 하루 전. 오늘(서울) 연도와 다른 날짜만 `yyyy.M.d`(`12.20 – 2027.1.5`). 시작과 끝이 같은 날이면 그 날 하나(`10.20`).
- **D-day 배지**: 모집 중(열린 활동 판정)일 때만, 서울 날짜로 오늘부터 마지막 날까지 `D-n`, 당일은 `D-DAY`. 모집 예정·마감·확인 필요에는 이 마감 배지가 없다(모집 예정은 상태 배지 옆에 아래 "모집 예정 시작 D-day"의 `시작 D-n`). 배지는 채운 청록 캡슐(흰 글자)로 문구 바로 뒤에 둔다(강조된 민트 줄 위에서도 보이게).
- **끝만 있으면**: 기존 문구 그대로(`~ 10월 20일 (화) 18:00 마감`), 배지 없음. 시작만 있거나 구조화 날짜가 없으면(`dateLabel`) 기존 규칙 그대로.
- **표기 위치**: 발견·즐겨찾기·프로그램 상세의 활동 카드 `모집` 줄(값 + 배지), 앱 활동 상세 `행사 일정`의 모집 행(기간이 있으면 제목 `모집` + 기간 + 배지, 끝만 있으면 지금처럼 `모집 마감` + 마감 문구 — 별도 마감 행은 두지 않는다; `캘린더에 추가`는 마감 시점 그대로), 웹 활동 카드 `모집` 줄, 웹 활동 상세 `모집 기간`, 웹 활동 오픈그래프 이미지(한 시간 캐시라 배지 없이 기간만).
- **바꾸지 않는 곳**: 접힌 프로그램 카드의 보조 줄(`M월 d일 마감` — `ProgramSummary`에 모집 시작이 없어 기간을 만들 수 없다), 내 활동 진행 트랙의 `지원` 단계(단계 날짜가 곧 마감), 맞춤 대기열 `DEADLINE` 장면(서버 `deadlineText`와 큰 D-day), 알림 문구.
- 대가: 시각을 빼서 카드·상세에서 마감 시각(예 18:00)이 보이지 않는다. 마감 시각은 공식 안내와 캘린더에 추가한 일정에서 확인한다.
- iOS·웹 2026-10-26 구현(`ActivityModel.recruitmentDate`, 웹 `recruitmentDate`). Android는 같은 규칙으로 따로 구현한다.

## 모집 예정 시작 D-day — 2026-10-26 사용자 결정

사용자: "모집 예정 활동에 시작 D-5 배지를 보여줘."

- **대상**: 활동 상태가 `RECRUITING_UPCOMING`(모집 예정)이고 모집 시작이 있는 활동. 모집 시작은 `application.startAt`(시각 일정) 또는 `application.startDate`(종일 일정)이다 — 모집 예정 판정 자체가 둘 중 하나로 나므로 둘 다 대상이다. 상태가 없는 옛 서버 응답은 기존 판정(위 "활동 상태")으로 모집 예정이면 같은 규칙.
- **표기**: `시작 D-n`. n은 **서울 날짜**로 오늘부터 모집 시작 날까지의 날수(시각 일정도 서울 날짜로 센다; 시간대는 보지 않는다). 시작 날이 오늘(서울)이면 `오늘 시작`(0 이하도 같다 — 서버 상태와 기기 시계가 어긋난 경우).
- **모양**: 기존 `모집 예정` 상태 배지 옆의 별도 미니 캡슐. 모집 중의 `D-n`(마감) 배지와 같은 글자 크기·굵기·숫자 폭(caption2 세미볼드, 고정폭 숫자)이되 색은 모집 예정 배지와 같은 **청록 글자 + 민트 틴트**(iOS `DearbyMiniBadge` 기본, 웹 `Badge variant="tinted"`, Android 마감 D-n과 같은 `Badge`에 `primaryContainer`). 배지 안 보조 글자가 아니라 별도 캡슐로 둔 이유: 모집 중 `D-n`과 같은 컴포넌트를 그대로 쓰고, 세 자리(카드·상세) 모두 한 표현으로 맞춘다.
- **자리**(iOS·Android·웹 공통): 발견 펼친 활동 카드·즐겨찾기 활동 카드에서는 `모집 예정` 칩 **왼쪽**(제목 줄 오른쪽 끝에 `시작 D-5` → `모집 예정` 순), 활동 상세 머리(상태 배지·종류 칩 줄)에서는 상태 배지 **오른쪽**(`모집 예정` → `시작 D-5` → 종류 칩). iOS는 내 활동 `진행 단계` 화면 위의 활동 카드(발견 카드와 같은 컴포넌트)에도 같이 보인다. 접근성은 자리와 상관없이 상태 뒤에 같은 글자를 읽는다(`… 모집 예정, 시작 D-5 …`; Android는 `모집 예정 · 시작 D-5`).
- **바꾸지 않는 곳**: 접힌 프로그램 카드 보조 줄은 기존 `M월 d일 시작` 그대로. 활동 상세 `모집 상태` 정보 행·`선발 과정`의 모집 행, 웹 오픈그래프 이미지(한 시간 캐시), 맞춤 대기열, 알림 문구에는 넣지 않는다.
- **모집 시작이 없으면**(`startAt`·`startDate` 모두 null): 배지만 `모집 예정`. 모집 중·마감·진행 중·종료에는 이 배지가 없다.
- 2026-10-26 구현: iOS `ActivityModel.upcomingStartDDay`, 웹 `upcomingStartDday`, Android `ApplicationWindow.startCount`(PR #423).

## 모집 일정 없는 활동은 마감 — 2026-10-26 사용자 결정

- 모집 일정(`application`의 구조화 날짜)이 없는 활동은 종류와 상관없이 **마감**으로 본다(서버 `feat/open-requires-recruitment`). 서버 반영 뒤 `ProgramSummary.nearestIsActivityStart`는 늘 false이고 `nearestRecruitmentEndAt`은 모집 마감만이다.
- iOS·웹(2026-10-26): 모집 상태는 `모집 마감` 칩, 활동 카드 `모집` 줄은 `모집 일정 미정`(같은 날 "활동 상태" 구현에서 `일정 없음`을 바꿈), `모집 예정` 묶음에 넣지 않는다(활동 시작으로 대신하지 않음). 접힌 프로그램 보조 줄의 `M월 d일 행사`(열린 이벤트의 행사 시작 대체 표시)는 없앴다 — 옛 서버가 `nearestIsActivityStart: true`를 보내면 그 날짜를 마감으로 보이지 않고 버린다. 이벤트 탭의 최근 행사 날짜(`M월 d일 행사`/`최근 yyyy.M`)는 그대로.

## 비스크롤 화면 배치·CTA — 2026-10-19 밤 사용자 결정

사용자: "리스트처럼 스크롤 요소가 있는 게 아닌 경우 되도록 요소를 중앙에 배치해줘. 카드 없이. 클릭 요소는 최대한 CTA처럼 배치해줘." iOS·Android.

- **대상**: 목록·긴 폼처럼 스크롤이 본질인 화면이 아닌 화면 — 빈 상태(내 활동 없음, 내 명함 없음, 받은 명함 없음, 검색 결과 없음), 로그인 시트, 동의 화면, 시작(인트로) 화면, QR 보이기, 캘린더 겹침 결과, 신청 확인 시트, 오류 화면, 계정 연결 시트.
- **카드는 섹션을 나눌 때만(사용자 "카드 디자인은 섹션을 나눌 필요가 없다면 굳이 사용하지 않아도 괜찮아")**: 둥근 카드·테두리 상자는 한 화면 안에서 서로 다른 묶음을 구분해야 할 때(발견 프로그램 카드, 프로필의 연락처/이력 섹션, 받은 명함 목록 면)만 쓴다. 내용이 한 묶음뿐인 화면(상세의 본문, 이력·프로필 입력 폼, 명함 만들기, 빈 상태, 시트)은 카드 없이 배경 위에 바로 그린다. 폼은 플랫폼 기본 inset grouped/OutlinedTextField 그대로.
- **배치**: 내용(아이콘/심볼·제목·설명)을 화면(또는 시트) 가운데에 세로·가로 중앙 정렬한다. 둥근 카드·테두리 상자로 감싸지 않고 배경 위에 바로 놓는다(동의 체크 행처럼 폼 요소는 플랫폼 기본 폼 그대로). 글자는 중앙 정렬, 설명은 보조색, 좌우 여백 24.
- **클릭 요소는 CTA**: 그 화면의 행동 버튼은 화면(시트) 아래쪽에 전체 너비 주 버튼(iOS `.borderedProminent` + `.controlSize(.large)`, Android `Button` 기본 높이 이상 전체 너비)으로 두고, 보조 행동은 그 아래 텍스트 버튼(iOS `.borderless`, Android `TextButton`)으로 둔다. 인라인 링크·작은 아이콘 버튼으로 숨기지 않는다. 안전 영역 아래 여백 16. 주 버튼이 둘 이상이면 세로로 쌓는다(가로 분할 금지).
- **글자 크기 통일(사용자 "이런 경우가 많을 경우 해당 요소들의 폰트 크기를 통일감 있게")**: 이 화면들은 한 가지 글자 계층만 쓴다 — 상태 아이콘은 "아이콘 통일"의 64 원 + 28 글리프, 제목 iOS `.title2.bold()`·Android `headlineSmall`(굵게), 설명 iOS `.body`·Android `bodyLarge`(보조색, 최대 3줄), 주 버튼은 플랫폼 기본 버튼 라벨, 보조 텍스트 버튼도 기본 라벨. 화면마다 다른 크기(`.title`·`.headline`·`titleMedium`·`SemiBold` 등)를 섞지 않는다. 공통 컴포넌트 하나(iOS `shared/ui` `CenteredMessage(symbol:title:message:primary:secondary:)`, Android `shared/ui` `CenteredMessage(...)`)로 그려 크기가 어긋날 여지를 없앤다.
- 큰 글씨·작은 화면에서 내용이 넘치면 가운데 정렬을 유지한 채 스크롤이 생긴다(`ScrollView`/`verticalScroll` + 최소 높이 = 화면). 동작 줄이기 영향 없음. 웹은 변경 없음.

## 아이콘 통일 — 2026-10-19 밤 사용자 결정 (토스 참고)

사용자: "아이콘 사용도 통일감 있게 해줘. 필요시 mobbin에서 토스 디자인 참고해줘." 토스 참고 화면(Mobbin): 상태 화면은 가운데 큰 단색 원형 아이콘 하나 + 제목 + 설명 + 하단 CTA([채움 완료](https://mobbin.com/screens/5faf70d5-f007-4bfa-86ff-55802a53c121), [알림 동의 시트](https://mobbin.com/screens/280b81aa-61ec-45f3-9761-61d79670fac8)), 목록 행은 왼쪽 작은 둥근 글리프 + 문장([혜택 목록](https://mobbin.com/screens/fd918402-6e06-4328-9aef-2ea45dd70805)). 현황: iOS는 SF Symbols 30여 종을 굵기·렌더링 지정 없이 섞어 쓰고, Android는 `Icons.Outlined` 61곳·`Rounded` 2곳이 섞여 있다.

- **한 가족만**: iOS는 SF Symbols만(사용자 PNG 아이콘 금지, 브랜드 심볼·GitHub 마크만 예외), Android는 **`Icons.Rounded`** 한 가족만(`Outlined`·`Filled`·`Default` 금지, `AutoMirrored.Rounded`는 허용). 색은 iOS `.foregroundStyle(.secondary)`/`DearbyStyle.teal`, Android `onSurfaceVariant`/`primary` 두 가지만.
- **세 가지 크기**: ① 인라인(글자 옆·버튼 안): 글자 크기 따름(iOS `.imageScale(.medium)`, Android 20dp), 선형(outline 계열 SF 이름, Rounded는 그대로). ② 행 앞 글리프(목록·정보 행): 24, 보조색, 같은 열에 맞춤. ③ 상태 아이콘(빈 상태·완료·오류·시트 머리): **청록 채움 원 64 안의 흰 글리프 28**(토스의 파란 체크 원처럼; 흰 글자 규칙과 같음), 오류는 `danger` 채움 원. `CenteredMessage`의 `symbol`이 이 형태를 그린다. iOS 큰 상태 심볼은 `.fill` 변형, 인라인은 선형.
- **의미별 고정 이름**(둘 다 이 표만 쓴다): 일정·달력 `calendar`/`Event`, 시각 `clock`/`Schedule`, 장소 `mappin.and.ellipse`/`Place`, 외부 링크 `arrow.up.right`/`AutoMirrored.Rounded.OpenInNew`, 수정 `pencil`/`Edit`, 삭제 `trash`/`Delete`, 추가 `plus`/`Add`, 제거 `minus.circle`/`RemoveCircle`, 검색 `magnifyingglass`/`Search`, 필터(2026-10-26 "필터 시트") `line.3.horizontal.decrease.circle`/`FilterList`, 지우기 `xmark.circle.fill`/`Cancel`, 닫기 `xmark`/`Close`, 뒤로 `chevron.left`/`AutoMirrored.Rounded.ArrowBack`, 펼침 `chevron.down`/`ExpandMore`, 성공·확인 `checkmark.circle`(상태는 `.fill`)/`CheckCircle`, 인증(연락처 인증됨, 2026-10-26) `checkmark.seal.fill`/`Verified`, 오류 `exclamationmark.triangle`/`Warning`, 정보 `info.circle`/`Info`, 알림 `bell`/`Notifications`, QR `qrcode`/`QrCode`, 명함 `person.text.rectangle`/`Badge`, 프로필 `person.crop.circle`/`AccountCircle`, 활동 둘러보기 `safari`/`Explore`, 내 활동 `checklist`/`Checklist`, 받은 명함 `tray.full`/`Inbox`, 공유 `square.and.arrow.up`/`Share`, 지도 `map`/`Map`, 전화 `phone`/`Call`, 메일 `envelope`/`Mail`, 첨부 `paperclip`/`AttachFile`, 사진 `photo`/`Image`, 눈 `eye`/`Visibility`, 잠금 `lock`/`Lock`, 기기 `iphone`/`Smartphone`, 위로 `arrow.up`/`ArrowUpward`, 조직 `building.2`/`Business`, 변경 이력 `clock.arrow.circlepath`/`History`, 단계 종류(마감 `flag`/`Flag`, 발표 `megaphone`/`Campaign`, 면접 `person.wave.2`/`RecordVoiceOver`, 기타 `pencil.line`/`EditNote`), 연락처 종류(채팅 `bubble.left`/`ChatBubble`, 사진 `camera`/`CameraAlt`, 링크 `link`/`Link`), 선택 안 됨 `circle`/`RadioButtonUnchecked`, 설정 `gearshape`/`Settings`, 잠금 안내 `lock`/`Lock`. 보내기·신청 `paperplane`/`Send`, 진행 단계 종류는 위 단계 항목을 따른다. 활동 종류(2026-10-26, "종류 아이콘 라벨"): 부트캠프 `graduationcap`/`School`, 컨퍼런스 `person.3`/`Groups`, 워크샵 `pencil.and.ruler`/`DesignServices`, 스터디 `books.vertical`/`LibraryBooks`, 동아리 `person.2`/`Group`, 강연 `mic`/`Mic`, 해커톤 `keyboard`/`Keyboard`, 공모전 `trophy`/`EmojiEvents`, 밋업 `cup.and.saucer`/`LocalCafe`, 기타 `square.grid.2x2`/`Category`, 시간표(트랙·세션 시간표 있음·시간표 보기, 2026-10-26) `tablecells`/`ViewTimeline`. 워크샵·스터디·해커톤은 처음 제안(`hammer`·`book`·`laptopcomputer`)이 같은 카드의 태그 아이콘(프로젝트·독서·IT, "분야·성격 태그와 아이콘")과 겹쳐 바꿨다(웹은 같은 모양의 인라인 SVG). 표에 없는 뜻이 생기면 표에 먼저 더한다.
- **탭 막대**: 다섯 탭은 위 표의 활동 둘러보기·내 활동·명함·받은 명함·프로필 아이콘을 쓰고, 선택 상태는 iOS 기본(`.fill` 자동), Android `NavigationBar` 기본.
- 적용 시점: 비스크롤 화면 배치·흰 글자 규칙과 같은 묶음(현재 PR 병합 뒤).

## 설정 — 2026-10-19 밤 사용자 결정

사용자: "설정 버튼도 (알림) 옆에 같이 뒀으면 해. 거기서 계정 설정, 연결된 OAuth2, 패스키 관리할 수 있게." iOS·Android. 웹은 이번 범위 밖.

- **진입**: 다섯 탭 제목 줄 우상단, 알림 버튼 왼쪽에 설정 버튼(`gearshape`/`Settings`, 44 터치 영역, 접근성 이름 `설정`). 누르면 `설정` 화면(푸시, inline 제목 `설정`, 뒤로 가기). 로그인하지 않았으면 설정 화면은 `로그인` CTA와 개인정보·약관·버전만 보인다.
- **화면 구성(플랫폼 기본 그룹 목록: iOS `List(.insetGrouped)`, Android `ListItem` 섹션)**:
  1. `계정`: 가입일(`createdAt`), 로그인 수단 수 요약. 행 `로그아웃`(확인 시트 → `POST /v1/auth/logout` + 토큰 삭제), `계정 삭제`(기존 "계정 삭제" 흐름으로 이동, destructive).
  2. `연결된 로그인`: Apple·Google 두 행. 연결됨이면 이메일(가린 이메일 그대로)·연결일과 `연결 해제`, 아니면 `연결하기`(기존 네이티브 로그인 → `POST /v1/me/identities/{apple|google}` 링크 흐름 재사용). **마지막 로그인 수단은 해제할 수 없다**(패스키 0개이고 다른 OAuth 연결이 없으면 해제 버튼 비활성 + 설명).
  3. `패스키`: 이 계정의 패스키 목록(이름 `label`, 만든 날, 마지막 사용, 기기 동기화 여부 `backupState`). 행을 밀거나 누르면 `이름 바꾸기`·`삭제`. 맨 아래 `패스키 추가`(기존 등록 흐름 `POST /v1/auth/passkeys/registration/options` → `registration`, 로그인 상태에서 호출). 마지막 로그인 수단이면 삭제 불가(위와 같은 규칙).
  4. `정보`: 개인정보 처리방침·이용약관(인앱 브라우저), 앱 버전·빌드.
  - 2026-10-25 사용자 결정: `정보` 위에 `알림`·`개인정보` 섹션을 둔다(프로필의 `알림` 묶음은 없앤다). `알림`: `새 모집 알림` 스위치와, 기기 알림 권한이 꺼져 있으면 안내 행(설정 앱 열기). 푸시는 계정 기능이라 로그인 + 동의 뒤에만 보인다(비로그인·동의 전 숨김). `개인정보`: `사용 통계 보내기`(설명 "어떤 화면과 기능을 쓰는지 모아 앱을 고치는 데 써요. 이름·연락처는 보내지 않아요.", 기기 저장)로 비로그인에서도 보인다. 광고 제3자 제공 동의 철회 스위치가 나중에 이 섹션에 들어간다. 비로그인 설정은 `로그인` CTA·`개인정보`·`정보`.
- **API(`auth` 모듈, Bearer)**:
  | 메서드·경로 | 응답 |
  | --- | --- |
  | GET /v1/me | 200 `{id, createdAt, signInMethods: {passkeys: n, apple: bool, google: bool}}` |
  | GET /v1/me/identities | 200 `{items: [{provider: "apple"|"google", email, displayName, linkedAt}]}` |
  | DELETE /v1/me/identities/{provider} | 204. 마지막 수단이면 409 `LAST_SIGN_IN_METHOD`. 애플은 refresh token 폐기(계정 삭제와 같은 경로), 구글은 행 삭제 |
  | GET /v1/me/passkeys | 200 `{items: [{id(credentialId), label, createdAt, lastUsedAt, backedUp, transports[]}]}` |
  | PATCH /v1/me/passkeys/{id} | `{label}`(1~60자) → 200 항목 |
  | DELETE /v1/me/passkeys/{id} | 204. 마지막 수단이면 409 `LAST_SIGN_IN_METHOD`. 남이 가진 id면 404 |
  "마지막 수단" 판정: 패스키 수 + 연결된 OAuth 수가 1이면 그 하나는 지울 수 없다. 이미 있는 `POST /v1/auth/passkeys/registration/options`·`registration`은 Bearer가 있으면 그 계정에 패스키를 더한다(없으면 기존 가입 흐름 — 2026-10-25부터 배포된 옛 앱만 쓴다).
- 저장: `users.created_at`(V40, 기존 행은 가장 이른 로그인 수단·프로필 생성 시각으로 채움). `user_credentials`·`user_identities`는 그대로.
- 구현 메모(2026-10-20): `POST /v1/auth/passkeys/registration/options`는 Bearer가 있으면 그 계정에 패스키를 더한다(응답은 기존처럼 201 TokenPair). 만료·잘못된 Bearer는 401(조용히 새 가입으로 넘어가지 않음). 로그인 상태 options에는 기존 패스키를 `excludeCredentials`로 넣어 키체인이 덮어쓰지 않게 하고, 요청 `displayName`이 새 패스키의 `label`이 된다(기본 `Dearby`). 경로의 `provider`는 소문자(apple|google), 연결돼 있지 않으면 404. PATCH label은 trim 후 1~60자(아니면 400). 다른 사용자의 패스키 id는 404(존재 여부를 구분하지 않음).

## 터치 영역·검색 경험·시트 닫기·안전 영역·토글 라벨 — 2026-10-20 사용자 결정

사용자(2026-10-20 00시): "터치 가능한 건 각 플랫폼별 Touchable 컴포넌트로 표현돼야 해. 영역이 묶인 경우 모든 곳에 터치가 돼야 해. 일정 추가 퍼널처럼 텍스트만 터치가 되면 안 됨. 일정 추가 퍼널의 검색도 활동 둘러보기에서 검색하는 것과 같은 경험을 줘야 해. 약관 동의 바텀시트는 닫을 수 있어야 해. safe area도 배경색이 적용돼야 해. 토글 버튼의 라벨은 바로 근처에 있어야 해." iOS·Android.

- **터치 컴포넌트**: 누를 수 있는 것은 전부 플랫폼 터치 컴포넌트다 — iOS `Button`·`NavigationLink`·`Link`·`Toggle`(`Text.onTapGesture` 금지), Android `Button`·`TextButton`·`IconButton`·`ListItem(modifier = Modifier.clickable)`·`Card(onClick)`·`Switch`(`Text(Modifier.clickable)`처럼 글자만 누르게 하지 않음). 행·카드·칩처럼 묶인 영역은 **영역 전체**가 터치 대상이다: iOS는 라벨에 `.frame(maxWidth: .infinity, alignment: .leading)` + `.contentShape(Rectangle())`, Android는 행 전체 `Modifier.clickable`(또는 `ListItem`을 감싼 `Surface(onClick)`). 터치 피드백(iOS 눌림 효과, Android 리플)은 기본값을 쓴다. 최소 44pt/48dp.
- **퍼널 검색은 발견 검색과 같은 경험**: 이력 입력 퍼널·프로필 편집의 조직/프로그램/활동/직무 검색(`ActivityInfoPicker`, `JobTagPicker`)은 발견 탭 검색창과 같은 컴포넌트(iOS `DearbySearchField` 시스템 검색 모양, Android `SearchBarDefaults.InputField`)와 같은 동작: 입력 즉시 결과 갱신(디바운스 동일), 지우기 버튼, 결과 행 전체 터치, 결과 없음은 `CenteredMessage` 형태의 빈 상태, 키보드는 필드 밖 탭·스크롤로 내림, 검색어에 맞는 항목이 없으면 맨 아래에 `"<검색어>" 직접 입력` 행.
- **동의 시트 닫기**: 약관·개인정보 동의 바텀시트(ConsentPage가 시트로 뜨는 경우)는 아래로 끌거나 닫기 버튼으로 닫을 수 있다(iOS `.interactiveDismissDisabled(false)`, 상단 `닫기`; Android `ModalBottomSheet`는 `onDismissRequest`로 닫힘). 닫으면 동의하지 않은 것이고 프로필 저장은 막힌 채로 남는다(다시 저장하면 다시 뜬다). 전체 화면 ConsentPage도 뒤로 가기로 나간다.
- **안전 영역 배경**: 페이지 배경색이 상태 표시줄·홈 인디케이터·노치 영역까지 채운다. iOS는 배경 `Color`를 `.ignoresSafeArea()`로 깔고 내용만 안전 영역 안에 둔다(시트·`Form`·`List`는 시스템이 알아서 함). Android는 `enableEdgeToEdge()` 아래에서 루트 `Surface(color = colorScheme.background)`가 창 전체를 채우고 inset padding은 그 안쪽 내용에만 준다. 키보드 영역도 같은 배경.
- **토글 라벨은 바로 옆**: `Switch`/`Toggle`의 라벨은 같은 행의 바로 왼쪽에 두고(iOS `Toggle("라벨", isOn:)` 기본 배치, Android `ListItem(headlineContent = 라벨, trailingContent = Switch)`), 라벨과 스위치 사이에 다른 요소를 두지 않는다. 발견 탭 `마감 숨기기`는 라벨과 스위치가 붙은 한 묶음이며 묶음 전체를 눌러도 토글된다. 라벨을 스위치 바로 옆에 두면 배치가 어색한 곳(짧은 라벨이 넓은 행 오른쪽 끝에 홀로 가는 경우, 동의 항목처럼 여러 줄 라벨)은 스위치 대신 **체크박스**(iOS는 `Toggle` + `.toggleStyle(.checkbox)`가 없으므로 `Label`+`checkmark.circle(.fill)` 버튼, Android `Checkbox` + `ListItem`)로 바꾼다(사용자 2026-10-20 "바로 근처에 있을 때 별로라면 체크박스로 변경"). 발견 `마감 숨기기`는 검색창 아래 한 줄에 라벨+스위치를 왼쪽 정렬로 붙여 둔다.
- **폼 라벨·입력 크기(사용자 2026-10-20 "프로필의 라벨이 너무 작아, 토스 참고해서 키워줘")**: 토스 입력 화면([계좌 입력](https://mobbin.com/screens/bb17bf31-062b-4b19-94e4-08f8c8d2d615), [거래 추가](https://mobbin.com/screens/6963fb34-5544-4c53-96fa-19fa70dbc5ae))처럼 **라벨을 입력 위에 쌓고** 입력 글자를 크게 둔다. 프로필·이력·명함 폼의 행은 세로 스택: 라벨 iOS `.subheadline`(15) 중간 굵기 보조색 / Android `labelLarge`(14) `onSurfaceVariant` → 입력 iOS `.title3`(20) / Android `titleLarge`(22) 본문색, 행 최소 높이 64, placeholder는 입력 크기 그대로 보조색. 섹션 머리·도움말은 `.footnote`(13)/`bodyMedium`가 아니라 `.subheadline`/`bodyMedium`(14) 이상. `LabeledContent`의 가로 라벨(왼쪽 라벨·오른쪽 값)은 쓰지 않는다. Android `OutlinedTextField`는 `textStyle = titleLarge`, `label`은 기본(포커스 시 떠오름) 대신 위에 별도 `Text`로 둔다(같은 쌓기).
- **연락처 행은 라벨 대신 왼쪽 아이콘(사용자 2026-10-20 "연락처의 경우 전화번호·이메일 같은 라벨 대신 좌측에 아이콘으로")**: 프로필 편집·퍼널의 연락처 입력 행과 명함·프로필의 연락처 표시 행은 위에 쌓는 글자 라벨을 두지 않고 행 왼쪽에 24 보조색 글리프(전화 `phone`/`Call`, 메일 `envelope`/`Mail`, 카카오톡 `bubble.left`/`ChatBubble`, Instagram `camera`/`CameraAlt`, GitHub `chevron.left.forwardslash.chevron.right`/`Code`, Behance `paintpalette`/`Palette`, 기타 `link`/`Link`)를 둔다(GitHub·Instagram·Behance·카카오톡 네 종류는 2026-10-26부터 아래 "연락처 아이콘·바로가기"의 단색 브랜드 글리프). 입력 글자는 `.title3`/`titleLarge` 그대로, placeholder가 종류 이름("전화번호", "이메일", "사용자 이름" 등)을 대신 말한다. 접근성 라벨은 종류 이름을 유지한다. 행 전체 탭 → 포커스 규칙 동일.
- **플로팅 라벨·필수 표시(사용자 2026-10-20 "필수 항목은 빨간 *로 표시. placeholder에 label을 넣어 주고 클릭 시 placeholder가 작아지면서 라벨 위치로 가는 효과")**: 위 "폼 라벨·입력 크기"의 고정 쌓기 대신 **플로팅 라벨**로 바꾼다. 비어 있고 포커스가 없을 때는 라벨 글자가 입력 자리에 placeholder처럼 크게(`.title3`/`titleLarge`, 보조색) 보이고, 포커스가 가거나 값이 있으면 짧은 애니메이션(iOS `.snappy(duration: 0.2)`, Android 기본 라벨 전환)으로 작아져 입력 위 라벨 위치(`.subheadline`/`bodySmall`, 보조색·포커스 시 청록)로 올라간다. **입력 칸은 검색창처럼 글자 한 줄만 담는 작은 칸이고, 라벨은 칸 바깥 좌상단으로 이동한다**(사용자 2026-10-20 "바깥 좌상단으로 하고 영역은 검색창처럼 글자만 들어가도 되는 크기"): 칸은 발견 검색창과 같은 모양(둥근 10, `tertiarySystemFill`/`surfaceContainerHighest` 채움, 높이 44/48, 테두리 없음, 여러 줄 입력은 3줄 높이)이며 포커스·값에 따라 모양이 바뀌지 않는다. 비어 있고 포커스 없으면 라벨이 칸 안에 placeholder로(`.body`/`bodyLarge` 보조색) 보이고, 포커스가 가거나 값이 있으면 라벨이 칸 **위 왼쪽**(바깥)으로 작아지며 올라간다(`.subheadline`/`bodySmall` 보조색, 포커스 시 청록). 행은 처음부터 라벨 줄(20) + 간격 4 + 칸 높이를 확보해 레이아웃이 튀지 않는다. 둘 다 플랫폼 기본 플로팅 라벨이 칸 안/테두리로 뜨므로 쓰지 않고, iOS `DearbyFormRow`·Android `DearbyField`가 `TextField`/`BasicTextField` 위에 라벨 `Text`를 애니메이션(`scaleEffect`+`offset` / `animateFloatAsState`로 위치·크기)으로 옮겨 구현한다(SwiftUI·Compose만). 필수 항목은 라벨 끝에 **빨간 `*`**(`Text("*").foregroundStyle(.red)` 이어 붙임 / Android 라벨 `AnnotatedString`에 `error` 색 `*`)를 붙이고, 접근성 라벨에는 ", 필수"를 넣는다. 2026-10-19의 "필수 표시는 도움말 문장" 규칙은 이것으로 대체한다. 연락처 행처럼 왼쪽 아이콘이 있는 행도 같은 플로팅 라벨을 쓴다(아이콘 + 플로팅 라벨 입력).
- **입력 영역 터치(사용자 "텍스트에어리어랑 연락처 추가 등 영역이 터치가 잘 안 돼")**: 입력 행은 행 어디를 눌러도 그 필드에 포커스가 간다(iOS 행 `.contentShape(Rectangle())` + 탭 → `@FocusState`, Android `Modifier.clickable { focusRequester.requestFocus() }` 또는 필드가 행 전체를 채움). 여러 줄 입력은 최소 3줄 높이·행 너비 전체. `연락처 추가`·`이력 추가`·`인증자료 추가` 같은 추가 행은 행 전체가 `Button`/`ListItem(clickable)`이며 아이콘+라벨이 왼쪽, 높이 48 이상.
- **키보드 내리기와 터치 충돌 점검(사용자 2026-10-20)**: 점검 결과 — 버튼·링크·스위치는 자식 제스처가 우선이라 루트 탭 제스처와 충돌하지 않는다. 충돌은 **버튼이 아닌 영역**에서 난다: iOS 루트 `.onTapGesture`가 행의 라벨 글자·여백 탭을 받아 첫 응답자를 내려 버리고(그래서 필드 옆을 누르면 포커스가 가는 대신 키보드가 닫힘), Android 루트 `detectTapGestures`도 `clickable`이 아닌 라벨 `Text` 탭에서 `clearFocus()`를 부른다. 조치: ① 위 "입력 영역 터치" 규칙대로 입력 행 전체가 그 필드에 포커스를 주면 루트 제스처가 받을 영역이 사라진다. ② iOS는 **SwiftUI만** 쓴다(사용자 2026-10-20 "SwiftUI만 사용"): 루트 탭으로 키보드를 내리는 제스처를 아예 두지 않고, `.scrollDismissesKeyboard(.interactively)` + 키보드 툴바 `완료` 버튼 + 입력 행 전체 포커스로 해결한다(애플 기본 앱과 같은 방식; 빈 곳 탭으로 내리기는 iOS 관례가 아니다). `DearbyDismissKeyboard`는 scroll dismiss와 툴바만 남긴다. ③ Android는 **Jetpack Compose만** 쓴다(사용자 2026-10-20; 현재도 `AndroidView`·XML 레이아웃·Fragment·appcompat 없음): 루트 제스처는 Compose `pointerInput`+`LocalFocusManager`로 유지하되 모든 행이 `clickable`이면 도달하지 않는다. 앞으로도 View 기반 컴포넌트를 섞지 않는다.
- **하단 저장 막대는 키보드와 함께 올라가지 않는다(사용자 2026-10-20 "프로필에서 원래대로 저장 버튼은 키보드가 올라올 때 제자리에")**: iOS 프로필 편집기의 `저장`/`원래대로` 막대는 `.ignoresSafeArea(.keyboard, edges: .bottom)`으로 화면 아래 제자리에 두고 키보드가 덮는다(키보드 툴바 `완료`로 내린 뒤 저장). Android는 셸 `imePadding` 규칙 그대로(Material 관례).
- **키보드의 내리기 버튼(사용자 "키보드 내리기 버튼을 키보드에 추가해줄 수 있나")**: iOS는 모든 입력 화면에 키보드 툴바 `.toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button("완료") { focus = nil } } }`를 둔다(시스템 관례). Android는 시스템 뒤로/내리기 키가 있으므로 툴바 없이 마지막 필드 `ImeAction.Done` → `clearFocus()`, 중간 필드는 `ImeAction.Next`, 여러 줄 입력은 `ImeAction.Default`(줄바꿈)로 둔다.
- 중복·이미 반영된 요청은 다시 바꾸지 않는다.

## 명함 탭 통합 — 2026-10-20 사용자 결정

사용자: "내 명함과 받은 명함을 명함 메뉴로 통합해야 해." 탭은 **네 개**: 발견(`활동 둘러보기`) · 내 활동 · 명함 · 프로필. iOS·Android.

- **명함 탭**: 제목 `명함`(PageTitle/ScreenHeader, 우상단 설정·알림). 제목 바로 아래 두 구역 전환기 — iOS `Picker`(`.pickerStyle(.segmented)`, `내 명함` / `받은 명함`), Android Material 3 `PrimaryTabRow`(탭 둘). 기본은 `내 명함`. 선택은 앱 실행 중 메모리에만 둔다. 전환기는 제목 줄과 함께 고정이고 아래 내용만 스크롤한다.
- **내 명함 구역**: 기존 내 명함 페이지 내용 그대로(격자·고른 명함·QR 보이기·고치기·지우기·빈 상태). **받은 명함 구역**: 기존 받은 명함 페이지 내용 그대로(검색창·색인 목록·상세·빈 상태). 두 구역의 검색창·빈 상태는 "폼은 플랫폼 기본"·"비스크롤 화면 배치" 규칙을 따른다.
- **탭 아이콘·이름**: 명함 탭은 `person.text.rectangle`/`Badge`, 이름 `명함`. 받은 명함 탭 아이콘(`tray.full`/`Inbox`)은 더 쓰지 않는다. 딥링크·앱 내 이동에서 받은 명함 상세로 가는 경로(명함 교환 저장 뒤 등)는 명함 탭의 받은 명함 구역을 연 뒤 상세를 민다.
- 계약의 "다섯 탭" 표현은 이 절 이후 **네 탭**으로 읽는다(알림·설정 버튼 규칙, 탭 제목 통일, 색 체계 등). AGENTS.md의 탭 수 서술도 함께 고친다.

## iOS 상단 바는 시스템 내비게이션 바(리퀴드 글래스) — 2026-10-20 사용자 결정

사용자: "상단 탭바 iOS 기본으로 하면 리퀴드 글래스 플로팅으로 만들 수 있어? 지금 스티키로 처리되니까 너무 부자연스러워." Xcode 27 SDK로 빌드하므로 iOS 26 이상 기기에서는 시스템 내비게이션 바·툴바·검색이 자동으로 리퀴드 글래스(반투명 떠 있는 막대)로 그려진다. 직접 그린 고정 제목 줄(`PageTitle`)은 이 효과를 막으므로 iOS는 **시스템 내비게이션 바**로 돌아간다. Android도 같은 방향(사용자 2026-10-20 "이거 진행해줘"): 네 탭 루트의 `ScreenHeader`를 Material 3 `TopAppBar`(제목 `titleLarge`, `actions`에 설정·알림 `IconButton`, `TopAppBarDefaults.pinnedScrollBehavior()`로 고정, 스크롤하면 시스템이 바 색을 `surfaceContainer`로 올려 구분)로 바꾼다. 발견의 접히는 검색 영역은 그대로 두되 `TopAppBar` 바로 아래에 붙인다. 명함 탭은 `TopAppBar` 아래 `PrimaryTabRow`. 하위 화면(뒤로 가기)은 `TopAppBar(navigationIcon = 뒤로)`. `ScreenHeader`는 지운다.

- **네 탭 루트**: `NavigationStack` 안에서 `.navigationTitle("활동 둘러보기" | "내 활동" | "명함" | "내 프로필")` + `.navigationBarTitleDisplayMode(.inline)`(노치 아래 여백을 작게 두자는 2026-10-19 결정 유지). 우상단 액세서리는 `.toolbar { ToolbarItemGroup(placement: .topBarTrailing) { 설정; 알림 } }`(아이콘 버튼, 시스템이 글래스 캡슐로 묶음). `PageTitle`·`.toolbar(.hidden, for: .navigationBar)`는 지운다.
- **발견 검색·접힘**: 직접 만든 접히는 검색 영역 대신 `.searchable(text:placement: .navigationBarDrawer(displayMode: .automatic), prompt: "프로그램·활동·태그 검색")` — **검색창은 처음부터 보이고 아래로 스크롤하면 가려진다**(사용자 2026-10-20 "탐색 탭에서 검색 메뉴는 처음에 보이게 해 주고 스크롤 내렸을 때 가려줘"). 처음 진입·새로 고침 뒤 맨 위 상태에서 검색창이 접혀 있으면 안 되므로, 스크롤 위치 0에서는 드로어가 펼쳐진 상태로 시작하도록 보장한다(`.automatic`이 처음에 숨긴다면 맨 위에서는 `.always`, 사용자가 스크롤을 시작하면 `.automatic`으로 전환) 전환은 한 방향뿐이다(2026-10-22: 스크롤 위치로 `.always`↔`.automatic`을 오가게 했더니 드로어가 위치 기준 inset을 바꿔 다시 뒤집는 반복이 생겨 펼침·접힘·백그라운드에서 멈춤·종료됨). — 아래로 스크롤하면 시스템이 검색 막대를 접고 위로 당기면 다시 보이는 iOS 기본 동작이 "enter always" 요구를 대신한다. `마감 숨기기` 토글은 목록의 **첫 행**(전체 행 터치, 라벨+스위치)으로 내려가 내용과 함께 스크롤한다. 풀투 리프레시·맨 위로 버튼·구분선 규칙은 그대로(구분선은 시스템 바가 알아서 그림).
- **명함 탭 전환기**: 세그먼트 피커는 내비게이션 바 아래 첫 고정 요소가 아니라 `.toolbar { ToolbarItem(placement: .principal) { Picker(.segmented) } }`로 제목 자리에 둔다(iOS 기본 패턴). 제목은 접근성 이름으로만 남긴다.
- **하위 화면**: 기존 inline 제목 유지. 시트는 `.presentationDetents` 기본.
- 시스템 바 아래 내용은 `.scrollEdgeEffectStyle`(iOS 26)을 기본값으로 두어 글래스 가장자리 흐림을 쓴다. 다크 모드·안전 영역 배경 규칙은 시스템 바가 처리하므로 추가 작업 없음.

## 웹에도 같은 규칙 — 2026-10-20 사용자 결정

사용자: "웹버전도 수정해줘." 앞선 절에서 "웹은 변경 없음"으로 둔 시각 규칙을 웹(`apps/web`)에도 적용한다. 로그인·프로필·설정·탭이 없는 웹의 성격상 아래 범위만.

- **비스크롤 화면 배치·CTA·글자 통일**: 빈 상태(검색 결과 없음·프로그램 없음), 오류, 비로그인 저장 완료/만료, 공개 명함 안내 같은 단일 메시지 화면은 공통 `CenteredMessage` 컴포넌트(64px 청록 원 + 28px 흰 아이콘, 제목 22/700, 설명 17 보조색 최대 3줄, 전체 너비 CTA + 텍스트 보조 버튼, 카드 없음)로 그린다.
- **카드는 섹션 구분에만**: 상세 본문·안내 문구를 감싼 둥근 상자는 지우고 배경 위에 바로 둔다. 발견 프로그램 카드·명함 면은 유지.
- **아이콘 통일**: 인라인 SVG `Icon` 하나의 세트로, 크기는 인라인(1em)·행 24·상태 28(원 안) 셋, 색은 보조색·청록 둘. 의미별 이름은 모바일 표와 같은 뜻으로 맞춘다(달력·시각·장소·외부 링크·검색·지우기·닫기·펼침·확인·오류·정보·QR·명함·공유·지도).
- **터치·클릭 영역**: 누를 수 있는 것은 `<button>`/`<a>`이고 행·카드·칩은 영역 전체가 대상(패딩 포함, 최소 44px 높이). 글자만 클릭되는 곳은 없앤다. 포커스 링은 기본 `:focus-visible` 유지.
- **폼 라벨·입력**: 비로그인 저장·검색 등 입력은 라벨을 위에 쌓고(14/500 보조색) 입력 글자 17 이상, 행 높이 48 이상, placeholder는 보조색. 토글은 라벨 바로 옆(`<label>`로 묶어 전체 클릭).
- **청록 채움 흰 글자**: `--on-teal`은 라이트·다크 모두 흰색, 채움 면은 `--teal-fill: #007F80`(두 모드 같은 값), 다크 모드 액센트 글자·테두리만 `--teal` #3FB3B4.
- **발견 검색·토글**: 검색창은 시스템 검색 입력 모양(`type="search"`, 돋보기·지우기), `마감 숨기기`는 검색 아래 한 줄 라벨+스위치(전체 클릭). 머리(사이트 로고 줄)는 고정하지 않고 보통 흐름.

## 내 활동 직접 추가 — 2026-10-20 사용자 결정

사용자: "모집 마감이더라도 사용자가 내 활동에 추가할 수 있는 플로우가 있었으면 해. 우아콘 2026 신청하고 확정됐는데 지금 들어가도 내 활동에 추가할 방법이 없어." iOS·Android. 웹은 내 활동이 없어 해당 없음.

- **활동 상세의 보조 행동**: CTA(신청/공식 안내) 아래에 모집 상태와 무관하게 `내 활동에 추가` 보조 버튼(iOS `.bordered` + `.controlSize(.large)`, Android `OutlinedButton` 전체 너비, 아이콘 `checklist`/`Checklist`)을 둔다. 누르면 그 활동을 신청함(`applied`)으로 표시해 내 활동 탭에 넣는다(기존 "신청하셨나요? → 신청했어요"와 같은 기록, 주최 측 확인 아님이라는 안내 문구 한 줄). 이미 내 활동에 있으면 버튼은 `내 활동에서 보기`(내 활동 탭의 그 활동 상세로 이동)로 바뀌고, 길게 누르거나 메뉴에서 `내 활동에서 빼기`(확인 뒤 제거). 
- **참여 확정 표시**: 추가한 뒤 내 활동 상세의 진행 트랙에서 기존 `통과`/`참여 확정` 표시 규칙 그대로 쓴다(2026-10-22: 참여 확정은 없앴고 일정 상태로 추론한다 — "내 활동 서버 저장" 절)(새 상태 없음). 지난 활동(활동이 끝난 것)을 추가하면 `끝난 활동` 묶음에 들어간다.
- 신청 확인 시트(외부 브라우저에서 돌아올 때)는 그대로 둔다. 둘 다 같은 기기 저장소를 쓴다.
- **빼기는 눈에 보이게(사용자 2026-10-20 "내 활동에 추가하고 삭제가 가능해야 해")**: 길게 누르기·작은 펼침 아이콘 같은 숨은 동작에만 두지 않는다. ① 활동 상세: 이미 내 활동에 있으면 `내 활동에서 보기` 버튼 아래에 텍스트 버튼 `내 활동에서 빼기`(빨강, iOS `.borderless` role destructive / Android `TextButton` error 색)를 항상 보이게 두고, 누르면 확인("내 활동에서 뺄까요? 단계 표시와 참여 확정도 함께 지워져요.") 뒤 뺀다. ② 내 활동 탭 목록: 각 행에서 빼기 — iOS 왼쪽으로 밀기 `.swipeActions` `빼기`(목록이 `List`가 아니면 행 `contextMenu`와 함께 상세의 버튼으로 충분하도록 ③을 둔다), Android 행 끝 `MoreVert` → `DropdownMenu` `내 활동에서 빼기`. ③ 진행 단계 상세(내 활동 행을 눌렀을 때): 맨 아래 `내 활동에서 빼기` 텍스트 버튼(같은 확인). 뺀 뒤 그 화면은 닫고 목록으로 돌아간다. 실수 방지 확인은 항상 둔다.

## 연락처 아이콘·바로가기 — 2026-10-26 사용자 결정

사용자: GitHub·Instagram·Behance·카카오톡 연락처 아이콘을 각 회사 공식 브랜드 마크로 바꾸고, 연락처를 누르면 그 서비스로 바로 간다. 같은 날 이어서 "SVG 아이콘이 적용이 안 됐는데? https://expo.dev/blog/supporting-iphone-duo 이런 거(페이지 하단 GitHub·X·Discord·Bluesky처럼) 단색 아이콘 차용해서 색깔 적용해 주면 돼", "외부 링크는 직접 치게 하지 말고, 태그나 사용자명을 입력하면 링크를 생성해 주도록 해". 위 "아이콘 통일"의 브랜드 심볼 예외이고, "연락처 행은 라벨 대신 왼쪽 아이콘"의 해당 네 종류 글리프를 대신한다(전화·메일·기타는 그대로).

- **아이콘 원본**: [Simple Icons](https://simpleicons.org) 16.36.0(저장소 CC0-1.0)의 `github`·`instagram`·`behance`·`kakaotalk` SVG를 바꾸지 않고 `shared/assets/prototype/contact-icons/`에 둔다. 출처·라이선스·브랜드 가이드·SHA256은 그 폴더 README. 마크 자체는 각 회사 상표이며 CC0가 상표권을 주지 않는다 — 모양을 바꾸지 않는다.
- **색·크기(2026-10-26 사용자 결정, 브랜드 고유색 규칙 대체)**: 네 마크는 **단색 글리프**이고 색은 앱이 정한다 — 연락처 글리프 자리의 보조색(iOS `.secondary`, Android `onSurfaceVariant`; 라이트·다크 자동)으로 전화·메일 기호와 같은 색. 크기는 글리프 열(24) 안에 20(글자 배율 따름)으로 SF Symbols·Material 기호와 같은 여백. 참고: expo.dev 블로그 하단 소셜 글리프. 이전 브랜드 색 규칙(GitHub·Instagram 검정/흰색, Behance `#1769FF`, 카카오톡 `#FFCD00`)은 [`006b118a`](https://github.com/fixabley/dearby/blob/006b118a/shared/contracts/native-v1.md#연락처-아이콘바로가기--2026-10-26-사용자-결정).
- **적용 위치**: 연락처를 그리는 모든 곳 — 명함 면·받은 명함 상세·내 프로필 카드(보기 모드), 프로필 수정의 연락처 입력 행, `연락처 추가` 목록, 명함 만들기의 연락처 선택 행. 한 함수(iOS `ContactModel.glyph(kind:)` → `DearbyGlyphView`, Android `contactIcon(kind)`)가 모두에 쓰인다. 웹 `/cards/:id`도 같은 Simple Icons 경로를 채운 단색 글리프(`Icon` 청록)로 그리고, 사용자명 값으로 같은 링크를 만든다(옛 URL 값은 사용자명으로 읽음).
- **플랫폼**: iOS는 asset catalog 벡터(`preserves-vector-representation`, Render As Template, `Image(...).renderingMode(.template)` + `foregroundStyle`). Android는 같은 경로를 vector drawable로 옮기고(28.8 viewport 안에 2.4 이동해 24dp에 20으로) `Icon(tint = …)`.
- **사용자명 입력(2026-10-26 사용자 결정)**: GitHub·Instagram·Behance·카카오톡은 링크를 치지 않고 **사용자명(ID)만** 입력한다. 링크는 앱이 만든다.
  - 허용: 영문·숫자·`.`·`_`·`-`. 길이 GitHub 39·Instagram 30(각 서비스 규칙), Behance 64·카카오톡 20(공식 상한을 확인하지 못해 넉넉히 둔 값). 앞의 `@`와 공백은 지운다.
  - 입력 칸: GitHub·Instagram·Behance는 라벨이 떠 있을 때 값 앞에 고정 접두어 `github.com/`·`instagram.com/`·`behance.net/`를 보조색으로 보인다(카카오톡은 없음). 입력·붙여넣기는 허용 문자만 남기고 길이에서 자르며, 그 서비스의 프로필 URL(`[https://][www.|m.]host/이름[/…]`)을 붙여 넣으면 이름만 남긴다. 카카오톡 칸에 URL을 넣으면 ID가 아니므로 비운다(오픈채팅 URL 입력 지원 없음). placeholder `사용자 이름`/`카카오톡 ID`.
  - 서버(`PUT /v1/profile`): 같은 규칙으로 정규화(프로필 URL → 사용자명, `@`·공백 제거)해 사용자명만 저장하고, 정규화 뒤 형식이 틀리면 400(`사용자명은 영문·숫자·.·_·-로 N자까지입니다`). 옛 앱이 URL을 보내도 400이 아니라 정규화. 이미 저장된 값과 같은 값(같은 연락처 id·종류)은 다시 검사하지 않는다 — 2026-10-26 전에 저장된 카카오톡 오픈채팅 URL이 변경 없는 저장을 막지 않게. 버전 되돌리기에서 지금 프로필에 없는 옛 오픈채팅 URL은 400이 될 수 있다. 저장된 값의 일괄 마이그레이션은 하지 않는다(운영 DB 변경은 사용자 승인 필요).
  - 이미 저장된 URL 형태의 값: 앱은 표시·링크에서 사용자명으로 읽고(프로필 수정 폼도 사용자명으로 채움), 카카오톡 오픈채팅 URL은 그대로 보이고 연다.
  - 웹사이트처럼 본래 URL인 종류는 지금 연락처 종류(phone/email/kakao/instagram/github/behance)에 없다.
- **바로가기(행 전체 터치)**: 값의 앞뒤 공백을 지우고 —
  - 전화 `tel:`(숫자와 `+`만), 이메일 `mailto:`.
  - GitHub `https://github.com/{id}`, Instagram `https://instagram.com/{id}`, Behance `https://www.behance.net/{id}`. `{id}`는 위 사용자명이며 규칙에 맞지 않으면 링크 없이 값만. 앱이 설치돼 있으면 시스템이 앱으로 연다(유니버설 링크·App Links).
  - 카카오톡: ID는 공개 링크로 열 수 없으므로 **복사**하고(앞 `@` 지움) "ID를 복사했어요"를 잠깐 보인다(iOS 행 끝 문구 + VoiceOver 알림, Android 스낵바 또는 시스템 복사 알림). 2026-10-26 전에 저장된 오픈채팅 URL(`http`/`https`, 또는 스킴 없는 `도메인/경로`에 `https://`)은 연다. 그 밖의 스킴(`javascript:` 등)은 열지 않는다.
  - 행 끝 표시: 열기 `arrow.up.right`/`AutoMirrored.Rounded.OpenInNew`, 복사 `doc.on.doc`/`ContentCopy`, 동작이 없으면 표시 없음. 접근성 힌트: 전화 "전화를 걸어요", 메일 "메일을 보내요", 링크 "링크를 열어요", 복사 "ID를 복사해요".

## 연락처 인증 — 2026-10-26 사용자 결정

사용자: "깃헙·인스타처럼 앱 로그인을 지원하는 것들은 연락처에 등록할 때 로그인으로 등록하도록", "이메일도 Spring Mail 활용해서 인증 기능 추가". 프로필 연락처 중 **GitHub**은 GitHub 로그인(OAuth)으로, **이메일**은 인증번호로 그 연락처가 본인 것임을 확인하고 `Contact.verified`로 표시한다. 수동 입력은 계속 허용한다(인증은 선택). 조율 판단으로 이번 범위는 둘뿐이다 — Instagram은 2024-12 Basic Display API 종료로 비즈니스 계정만 가능, 카카오 로그인은 카카오톡 ID·오픈프로필 URL을 주지 않음, LinkedIn OIDC는 이름만, Behance API는 신규 발급 중단. 그 종류들은 수동 입력만 남는다.

### 데이터

- `Contact.verified: Boolean`, `verifiedAt: String?`(ISO 8601, 인증 시각). 프로필·명함·받은 명함·웹 `/cards/:id`·버전 스냅샷·신고 스냅샷의 `contacts[]`에 모두 실린다(V64 전 스냅샷·명함은 `false`·`null`로 읽힌다). 저장: `contacts.verified`·`verified_at`(V64).
- **클라이언트는 `verified`를 보내지 못한다.** `PUT /v1/profile`의 연락처에 `verified`를 넣어도 무시된다. 서버는 같은 연락처 id의 종류·값이 그대로면(이메일은 대소문자 무시, 사용자명 종류는 정규화 뒤 비교) 인증 상태를 유지하고, 값을 고치면 `verified=false`·`verifiedAt=null`로 되돌린다. 라벨만 바꾸면 유지된다. 버전 되돌리기도 같은 규칙(그때 값이 지금 인증된 값과 같을 때만 유지).
- 인증이 성공하면 서버가 그 연락처를 고쳐 **프로필 버전이 1 오른다**(`PUT /v1/profile`과 같은 저장·스냅샷). 응답은 새 Profile이다. 앱은 응답의 `contacts`·`version`으로 수정 중인 폼을 갱신해야 다음 `PUT`의 `If-Match`가 409가 되지 않는다. 바꾸는 항목: 같은 값의 그 종류 항목이 있으면 그것, 없으면 그 종류의 첫 항목 값을 인증된 값으로 덮어쓴다(수동 입력값 대체, id·라벨 유지), 그 종류가 없으면 끝에 `label=""`로 더한다. 이미 같은 값으로 인증돼 있으면 버전을 올리지 않는다.
- 프로필이 아직 없으면(버전 0) 인증을 끝낼 수 없다: 400 `code: PROFILE_REQUIRED`("프로필을 먼저 저장해 주세요"). 동의 전(`consentRequired`)이면 403 `CONSENT_REQUIRED`(프로필 쓰기와 같다).

### API — GitHub 연결 (Bearer, 본인)

애플 웹 방식(`/auth/apple/web/*`)과 같은 start → 콜백 → 1회용 code → complete. **콜백만으로 끝내지 않는다**: 남이 시작한 인가 주소를 내가 승인하면 내 GitHub login이 남의 프로필에 인증됨으로 붙을 수 있어(OAuth 계정 연결 CSRF), 결과 code를 가져가는 `complete`의 Bearer가 `start`를 부른 계정과 같아야 한다. 누구나 앱 주소로 code를 보낼 수는 있지만 서버가 그 code가 그 사람 것인지 확인한다. 운영에 `GITHUB_CLIENT_ID`·`GITHUB_CLIENT_SECRET`·`GITHUB_CALLBACK_URL`이 없으면 아래 `/github/*`는 503.

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| POST /v1/me/contacts/github/start | Bearer | `{platform: ios\|android\|web}` | 200 `{authorizeUrl}`. 인가 주소의 `state`는 10분, 한 번만. 다른 platform 값은 400 |
| GET /v1/me/contacts/github/callback | 없음(GitHub이 사용자 브라우저를 돌려보냄) | `code`, `state` 또는 `error` | 303 → ios·android `dearby://contacts/github?code=<1회용>` 또는 `?error=cancelled\|failed`, web `https://dearby.wid.io.kr/profile?code=…` 또는 `?error=…`. 주소에 login·토큰을 싣지 않는다. state를 모르면 앱 주소로 `error=failed` |
| POST /v1/me/contacts/github/complete | Bearer | `{code}` | 200 Profile(GitHub 항목 `value=login`·`verified=true`). code가 없거나 만료(10분)됐거나 다른 사람 것이면 모두 400 `code: GITHUB_LINK_EXPIRED`(구분하지 않는다) |

- 서버는 GitHub `code`를 액세스 토큰으로 바꿔 `GET https://api.github.com/user`의 `login`만 읽는다. **scope 없음**(공개 프로필), **액세스 토큰은 저장하지 않는다**. 저장되는 것은 login(사용자명)뿐이고 프로필 URL `https://github.com/{login}`은 앱이 만든다("연락처 아이콘·바로가기"). 사용자가 GitHub에서 취소하면(`error=access_denied`) `cancelled`, 그 밖의 실패(state 만료·토큰 교환 실패·login 형식 오류)는 `failed`.
- GitHub OAuth App(사용자가 만든다): github.com → Settings → Developer settings → OAuth Apps → New OAuth App. Authorization callback URL = `https://api.dearby.wid.io.kr/v1/me/contacts/github/callback`(OAuth App 하나에 콜백 URL 하나라 로컬 개발은 `http://localhost:8080/...`로 앱을 따로 만든다). env `GITHUB_CLIENT_ID`·`GITHUB_CLIENT_SECRET`·`GITHUB_CALLBACK_URL`(선택 `GITHUB_WEB_REDIRECT_URI`, 기본 `https://dearby.wid.io.kr/profile`).

### API — 이메일 인증번호 (Bearer, 본인)

Spring Mail(SMTP)로 한글 평문 한 통을 보낸다. 제목 `[Dearby] 이메일 인증번호 123456`, 본문에 인증번호·10분 안내·요청하지 않았으면 무시. 운영에 `MAIL_HOST`나 `MAIL_FROM`이 없으면 아래 `/email/*`는 503.

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| POST /v1/me/contacts/email/challenges | Bearer | `{email}` | 200 `{challengeId, expiresAt}`. 6자리 숫자, 10분. 형식 오류 400, 프로필 없음 400 `PROFILE_REQUIRED`, 같은 이메일(대소문자 무시) 1분당 1회·하루 10회를 넘으면 429, 메일을 보내지 못하면 503(그 시도는 횟수에 세지 않는다) |
| POST /v1/me/contacts/email/verify | Bearer | `{challengeId, code}` | 200 Profile(이메일 항목 `value=입력한 이메일`·`verified=true`). 틀리면 400 `code: CODE_MISMATCH`(detail에 남은 횟수), 5번 틀리면 폐기되어 400 `CHALLENGE_EXPIRED`; 없거나 만료·이미 사용·다른 사람 것도 400 `CHALLENGE_EXPIRED` |

- 인증번호는 해시로만 저장하고(`email_contact_challenges`, V64), 발송 횟수를 세기 위해 하루 동안 남긴다. 성공한 challenge는 다시 쓸 수 없다.
- 메일 발신: env `MAIL_HOST`·`MAIL_PORT`(기본 587 STARTTLS)·`MAIL_USERNAME`·`MAIL_PASSWORD`·`MAIL_FROM`(예 `Dearby <no-reply@wid.io.kr>`). 보내는 도메인에 SPF·DKIM(가능하면 DMARC)을 설정하지 않으면 받는 쪽이 스팸으로 분류한다. SMTP 공급자(Amazon SES·Resend·Mailgun·Postmark·Google Workspace 등)는 사용자가 고른다. 로컬은 mailpit(`docker run -p 1025:1025 -p 8025:8025 axllent/mailpit`, `MAIL_PORT=1025`·`MAIL_STARTTLS=false`).

### 화면 (iOS·Android·웹, 다음 단계)

- **연락처 편집(프로필 수정·퍼널)**: GitHub 행에는 입력 칸 옆(또는 아래 보조 행)에 `GitHub으로 연결` 버튼(iOS `ASWebAuthenticationSession`, Android Custom Tabs, 웹 새 창/탭). 앱은 `start`(platform)를 불러 `authorizeUrl`을 열고, `dearby://contacts/github?code=…`로 돌아오면 `complete`를 불러 응답 Profile로 폼·버전을 갱신한다. `error=cancelled`는 조용히 닫고, `failed`는 "GitHub 연결에 실패했어요. 다시 시도해 주세요" 안내. 이메일 행에는 `인증번호 받기` 버튼 → 보낸 뒤 같은 행 아래에 6자리 숫자 입력 칸(숫자 키패드, 자동 완성 `oneTimeCode`)과 `확인`·`다시 받기`(1분 뒤 활성, 남은 초 표시). 429·400의 detail 문구를 그대로 보인다. 둘 다 프로필을 아직 저장하지 않았으면(버전 0) 버튼을 비활성하고 "프로필을 먼저 저장하면 인증할 수 있어요"를 보인다(서버는 400 `PROFILE_REQUIRED`).
- **수동 입력도 계속 허용**: 인증 없이 적은 값은 지금처럼 저장되고 표시만 다르다. 인증된 항목의 값을 고치면 체크가 사라진다는 것을 입력 칸 아래 한 줄로 알린다("값을 바꾸면 인증이 풀려요").
- **표시(명함 면·받은 명함·내 프로필 보기·웹 `/cards/:id`·명함 만들기 연락처 선택 행)**: `verified`가 true인 행은 값 끝에 작은 체크(iOS `checkmark.seal.fill` 16 청록, Android `Verified` 16 `primary`, 웹 같은 모양 인라인 SVG)를 붙이고 접근성 라벨에 `인증됨`을 더한다(위 "아이콘 통일" 표의 `인증`). 미인증은 표시 없음(배지 없음). 바로가기·복사 동작은 바뀌지 않는다.
- 503(기능 비활성)이면 그 버튼을 숨긴다(앱은 `start`/`challenges`의 503을 받으면 "지금은 쓸 수 없어요"로 알리고 수동 입력으로 안내).
- iOS 구현 확인(2026-10-26, `feat/verified-contacts-ios`): 프로필 수정의 연락처 섹션(`ProfileContactFields`)에 이메일 행 아래 `인증번호 받기` → 같은 행의 6자리 칸(숫자 키패드·`oneTimeCode`)·`확인`·`다시 받기 (n초)`(1분), GitHub 행 아래 `GitHub으로 연결`(SwiftUI `webAuthenticationSession`, 콜백 scheme `dearby`; 앱의 `IncomingLink`는 `dearby://contacts/*`를 무시). 성공 응답의 Profile은 `ProfileForm.absorbVerification`이 `version`과 그 종류 행만 받아 다른 수정 중 값을 지킨다. 400·429 detail은 그대로, `CHALLENGE_EXPIRED`는 받기 상태로 되돌림, 503은 그 종류 버튼을 숨김. 인증된 행은 입력 칸 안 값 끝에 `checkmark.seal.fill`(`DearbyVerifiedMark`), 포커스가 가거나 값이 달라지면 "값을 바꾸면 인증이 풀려요". 명함 면·받은 명함·내 프로필 보기·명함 만들기 선택 행은 같은 체크(`ContactModel.verified`). 버전 0 편집은 iOS에서 단계 퍼널(`ProfileFunnelPage`)이라 인증 버튼이 없고 첫 저장 뒤 수정 화면에서 인증한다(비활성 버튼·안내 문구는 두지 않았다).

### 개인정보처리방침 반영(사용자 확인 후, 웹 `/privacy`)

- 수집 항목 "프로필" 행에 `연락처 인증 상태(인증 여부·시각)`, GitHub 연결 시 `GitHub 사용자명(login)`을 더한다. 제3자(외부 서비스) 절에 "GitHub 연결: 사용자가 연결하면 GitHub OAuth에서 공개 프로필의 사용자명을 받는다(액세스 토큰은 저장하지 않음). GitHub에 Dearby 사용자 정보를 보내지 않는다"를 더한다. 이메일 인증번호 발송은 처리 위탁(SMTP 공급자, 선택한 회사 이름)으로 적는다. `CONSENT_PRIVACY_VERSION`을 올릴지는 사용자 결정.

## 소개 링크·소개 글 폐지 — 2026-10-26 사용자 결정

사용자: "소개 노션 같은 링크를 이름 다음에 추가할 수 있게 해 줘." 이어서 "소개 글 기능 전체 삭제 — 자기소개는 이름 옆 소개 링크로 대신", "내 프로필의 활동 이력을 명함처럼". iOS·Android·웹·API.

- **소개 링크(`introLink`, 선택, 하나)**: 노션·포트폴리오 같은 외부 문서 주소. 위 "외부 링크는 직접 치지 않는다"(연락처 사용자명 입력)의 예외 — 사용자명으로 만들 수 없는 링크라 URL을 입력한다.
  - 서버(`PUT /v1/profile` `introLink`): 앞뒤 공백 제거, 비면 null, 스킴이 없으면 `https://`를 붙이고 `https` 주소(호스트에 `.`, 공백 없음)만, 500자까지. 그 밖(`http:`·`javascript:` 등)은 400 `소개 링크는 https 주소만 쓸 수 있습니다`. 프로필 버전 스냅샷에 들어가고 되돌리기도 따른다. 저장: `profiles.intro_link`·`business_cards.intro_link`(V60).
  - 입력: 프로필 수정의 이름 칸 바로 아래 `소개 링크` 칸(URL 키보드, 자동 고침·대문자 끔, placeholder `notion.so/…`), 선택. 형식 오류 문구는 서버 문구와 같다. 처음 입력 퍼널에는 넣지 않는다(수정에서 추가).
  - 표시: 프로필 보기 카드·명함 면·받은 명함·웹 `/cards/:id`에서 **이름 바로 옆**(같은 줄, 넘치면 다음 줄)에 작은 링크 버튼 — 글리프 + `소개`. 호스트가 `notion.so`·`notion.site`(하위 도메인 포함)면 Notion 단색 글리프(Simple Icons `notion` 16.36.0, `shared/assets/prototype/contact-icons/notion.svg`, 연락처 브랜드 글리프와 같은 단색 규칙), 그 밖은 링크 글리프(`link`/`Link`). 색은 청록(iOS `DearbyStyle.teal`, Android `primary`, 웹 `text-teal`), 캡슐 옅은 배경(iOS `.buttonStyle(.bordered)` 소형 + `.tint(teal)`, Android `AssistChip` 또는 `FilledTonalButton` 소형, 웹 둥근 버튼). 누르면 시스템 브라우저/앱으로 연다(iOS `openURL`, Android `CustomTabs`/`ACTION_VIEW`, 웹 `target=_blank rel=noopener`). 접근성 라벨 `소개 링크 열기`. `introLink`가 없으면 버튼 없음.
  - 명함: 발행·고치기 요청에 `includeIntroLink: Boolean`(생략 시 true). 명함 만들기에서 프로필에 소개 링크가 있으면 `소개 링크 넣기` 스위치(기본 켬, 사진 넣기와 같은 모양). 명함 지우기·어드민 내리기는 비운다. 신고 스냅샷에 들어간다.
- **소개 글(`introduction`) 폐지**: 앱·웹에서 소개 입력·표시를 모두 없앤다 — 프로필 수정의 `소개` 칸, 처음 입력 퍼널의 `소개` 단계, 프로필 보기의 `소개` 절, 명함 만들기의 본문 미리보기, 명함 면·받은 명함·웹 `/cards/:id`의 소개 절(이미 발행된 명함의 옛 본문도 보이지 않는다). 마크다운 렌더러는 쓰는 곳이 없어 지운다.
  - 서버: `PUT /v1/profile`의 `introduction`은 받고 무시(옛 앱 호환, 400 없음), 명함 발행·고치기는 본문을 비운다(`business_cards.introduction` = `""`). 응답의 `introduction`(Profile·Card)은 **폐지 예정**으로 당분간 늘 `""`. 버전 스냅샷의 옛 `introduction`은 읽지 않는다(되돌려도 돌아오지 않는다). DB 열 `profiles.introduction`·`business_cards.introduction`과 그 안의 기존 값은 지우지 않는다(운영 데이터 일괄 삭제는 사용자 승인 필요). 2026-10-26 전 신고 스냅샷의 소개 글은 어드민 신고 상세에 `소개 글(폐지 전)`로 남는다.
- **내 프로필 활동 이력 = 명함 이력**: 프로필 보기 모드의 활동 이력 목록은 명함 면(명함 상세·받은 명함)의 이력 행 컴포넌트를 그대로 쓴다(기간·조직 · 프로그램 · 활동·역할·`확인됨`/`미확인` 배지·설명).
- 처리방침: 수집 항목에서 `소개`(자기소개 글)를 빼고 `소개 링크(선택)`를 더해야 한다(#344와 별도, 사용자 확인 후).

## 프로필 읽기/수정 모드·상단 바 메뉴 — 2026-10-20 사용자 결정

사용자: "내 프로필에서 수정이 바로 가능하게 하지 말고, 상단 탭바에 수정 버튼을 눌렀을 때 노출되도록. 수정과 설정·알림 버튼을 제외하고 알림 버튼 오른쪽에 햄버거 메뉴 버튼을 눌렀을 때 드롭다운으로 라벨과 함께 표시." iOS·Android.

- **프로필 탭은 읽기 모드가 기본**: 이름·직무·소개·연락처·활동 이력을 읽기 전용 행(명함 본문과 같은 표현: 연락처는 왼쪽 아이콘 + 값, 이력은 기간·제목·역할·확인 배지)으로 보인다. 입력 칸·저장 막대는 보이지 않는다. 프로필이 아직 없으면(버전 0) 기존 입력 퍼널이 그대로 뜬다.
- **수정 모드**: 상단 바의 `수정`(`pencil`/`Edit`, 텍스트 버튼 또는 아이콘, 접근성 `수정`)을 누르면 같은 화면이 편집 폼(플로팅 라벨 입력·이력 추가·연락처 추가)으로 바뀌고 아래에 `저장`/`원래대로` 막대가 나타난다. 상단 `수정`은 `완료`로 바뀌며 변경이 없으면 읽기 모드로 돌아가고, 변경이 있으면 저장 여부를 묻는 확인(저장 / 버리기 / 취소). 저장 성공 시 읽기 모드로 돌아간다. 모드는 앱 실행 중 메모리.
- **상단 바 구성(네 탭 공통)**: 오른쪽에 [탭 고유 주 행동(프로필의 `수정`만)] [설정] [알림] [**메뉴** `line.3.horizontal`/`Menu`] 순. 메뉴를 누르면 드롭다운(iOS `Menu` → 항목은 `Label(제목, systemImage:)`, Android `DropdownMenu` → `DropdownMenuItem(leadingIcon + text)`)에 나머지 보조 행동이 **아이콘 + 라벨**로 나온다: 프로필 — `변경 이력`, `명함 만들기`; 내 활동 — 항목 없음(예시 데이터셋이 없어 `예시` 배지는 지우고 메뉴도 숨긴다; 2026-10-20 구현 확인); 발견·명함 — 지금은 항목이 없으면 메뉴 버튼을 숨긴다. 설정·알림·수정은 메뉴에 넣지 않는다.
- iOS는 `ToolbarItemGroup(.topBarTrailing)` 안에 순서대로, Android는 `TopAppBar.actions` 안에 순서대로. 메뉴 아이콘 접근성 이름 `더 보기`.
- **보기 모드 = 명함 디자인(2026-10-26 사용자 결정)**: 보기 모드 맨 위는 명함 면 컴포넌트를 그대로 쓴 카드(사진·이름·직무·연락처, 연락처는 아래 "연락처 아이콘·바로가기"대로 누르면 열림), 그 아래 `활동 이력` 목록(명함의 이력 행과 같은 컴포넌트·인증 배지; `소개` 절은 2026-10-26 폐지), 그 아래 `이 내용으로 명함 만들기`(주 버튼, 명함 만들기 시트)·`수정`(보조 버튼, 상단 `수정`과 같다). 수정 모드 입력 화면은 그대로이며 맨 위에 `사진` 칸("프로필 사진")이 더해진다. 변경 이력의 버전 상세도 같은 표현(버전에는 사진이 없어 머리글자).

## 명함 고치기는 같은 명함을 수정 — 2026-10-20 사용자 결정

사용자: "명함을 고쳤는데 명함이 발행돼 버렸어. 명함을 고치면 수정되도록 변경해야 하지 않아?" 2026-10-18 "이 명함 고치기 = 같은 꾸밈으로 다시 발행(새 id)" 규칙을 바꾼다. iOS·Android·API. 웹은 공개 `/cards/:id`가 현재 내용을 보이므로 변경 없음.

- **API**: `PUT /v1/cards/:id`(Bearer, 소유자만, 철회된 명함 404) `{name, description, contactIds, historyIds}` — `POST /v1/cards`와 같은 검증으로 **같은 id의 명함 스냅샷을 현재 프로필 기준으로 다시 만든다**. 200 Card(`id`·`createdAt` 유지, `updatedAt` 갱신). 공개 `GET /v1/cards/:id`·받은 명함 목록·교환 링크(`dearby://cards/:id`, 웹 `/cards/:id`)는 같은 id를 가리키므로 고친 내용이 바로 보인다. 받은 쪽에 저장된 사본이 있다면 사본은 그대로 두고 목록은 현재 명함을 참조한다(기존 규칙 유지).
- **앱**: 내 명함의 `이 명함 고치기`는 명함 만들기 화면을 그 명함의 이름·설명·선택으로 채워 열고, 발행 버튼 라벨은 `고친 내용 저장`, 완료 시 `PUT /v1/cards/:id`를 부른다(새 명함을 만들지 않음). `새 명함`만 `POST`. 고친 뒤 내 명함 목록은 같은 칸이 갱신되고 선택이 유지된다. 명함 QR·링크는 바뀌지 않는다.
- 어드민: 변경 없음.
## 활동 이력에서 단기 행사 제외 — 2026-10-20 사용자 결정

사용자: "활동 이력에 컨퍼런스·워크숍 등 일주일 이내인 활동은 제외해줘." 하루짜리 컨퍼런스·워크숍·밋업은 경력 이력이 아니다.

- **규칙**: 활동 기간(ACTIVITY 슬롯)이 알려져 있고 **7일 미만**(끝 − 시작 < 7일, 종일이면 `endDateExclusive − startDate < 7`, 시각 일정이면 `endAt − startAt < 7일`, 끝이 없으면 하루로 봄)이면 이력 후보에서 뺀다. 기간을 모르면(날짜 없음) 종류가 `CONFERENCE`·`WORKSHOP`·`MEETUP`·`LECTURE`일 때만 뺀다. 그 밖(부트캠프·스터디·동아리·해커톤·공모전·기타, 기간 미상)은 남긴다. 시각 일정에 시작이 없고 끝만 있으면 기간 미상으로 본다(종류 규칙만). 이미 같은 활동으로 저장돼 있는 이력은 다시 검사하지 않는다(기존 프로필 저장·스냅샷 복원이 막히지 않게; 2026-10-20 조율 결정).
- **API**: `GET /v1/activities?forHistory=true`(기존 `q`·`cursor`·`size`와 함께)가 위 규칙으로 걸러 준다. 이력 쓰기(`PUT /v1/profile` histories의 `activityId`, 이력 퍼널 저장)는 제외 대상 활동이면 400 `HISTORY_ACTIVITY_TOO_SHORT`("일주일 미만 행사는 활동 이력에 넣을 수 없어요"). 직접 입력한 활동 이름(`activityName`)은 제한하지 않는다.
- **앱**: 이력 퍼널·프로필 편집의 활동 선택 창은 `forHistory=true`로 검색한다. 검색 결과가 없을 때의 `직접 입력` 행은 그대로.
- 웹·어드민 변경 없음.

## 내 활동 서버 저장·일정 상태·겹침 확인 시간 — 2026-10-22 사용자 결정

사용자: "활동 상세에서 참여 후 상단 문구가 너무 길어. 체크 아이콘과 '이미 추가된 항목입니다'로 CTA 위쪽을 확장해 표현. 내 활동 목록에서 dismiss로 지울 수 있게. 겹치는 기간 보기를 안드로이드에도, 활동 상세에서도. 일부 일정에는 안 되는데 어드민에서 조정할 수 있게. 진행 단계의 통과·대기·탈락 같은 항목도 활동별로 어드민에서 추가·제거. 활동 - 사용자 활동, 활동 - 일정 - 일정 상태, 사용자 활동(활동 논리 FK) - 사용자 활동 일정 상태(사용자 활동 논리 FK, 일정 상태 논리 FK)."

### 데이터(사용자가 준 모델)

- `schedule_statuses(id, schedule_id, name, kind, sort_order, created_at)` — 일정마다 고를 수 있는 상태. `kind`: `WAITING`(대기)·`PASSED`(통과)·`FAILED`(탈락)·`OTHER`. 일정에 행이 없으면 기본 세 개(대기·통과·탈락)를 쓴다(서버가 응답에 채워 준다, 저장은 하지 않음). 어드민이 일정별로 더하고 이름을 바꾸고 지운다.
- `user_activities(id uuid, user_id, activity_id(논리 FK), created_at, updated_at)` — 사용자가 내 활동에 넣은 활동. `(user_id, activity_id)` 유일.
- 참여 확정 표시는 없앤다(2026-10-22 사용자): 일정별 상태로 추론한다.
- `user_activity_schedule_statuses(user_activity_id(논리 FK), schedule_id, schedule_status_id(논리 FK), updated_at)` — PK `(user_activity_id, schedule_id)`. 기본 상태를 고르면 `schedule_status_id`는 null이고 `default_kind`에 기본 종류를 둔다(기본 행은 저장된 id가 없으므로).
- 논리 FK: DB 제약 없이 코드가 확인한다. 활동이 지워지면 사용자 활동은 남되 목록에서 "삭제된 활동"으로 보인다.

### API(로그인, Bearer)

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/me/activities | — | 200 `{items: [{activityId, createdAt, deleted, statuses: [{scheduleId, statusId?, kind}]}]}`. `deleted`: 활동이 지워졌거나 게시가 내려감("삭제된 활동") |
| PUT /v1/me/activities/{activityId} | 없음 또는 `{}` | 200 항목(없으면 만들고 있으면 그대로). 게시되지 않은 활동 404 |
| DELETE /v1/me/activities/{activityId} | — | 204(일정 상태도 함께 지움). 없어도 204 |
| PUT /v1/me/activities/{activityId}/schedules/{scheduleId} | `{statusId}` 또는 `{kind}`(기본 상태) | 200 항목. 그 활동의 일정이 아니거나 그 일정의 상태가 아니면 400 |
| POST /v1/me/activities/import | `{items: [{activityId, statuses: [{scheduleId, kind}]}]}` | 200 목록. 기기에만 있던 기록을 로그인 뒤 한 번 올린다(이미 있으면 서버 값 유지) |

- 공개 `Schedule`에 `statuses: [{id?, name, kind}]`(어드민 지정 또는 기본 세 개)와 `overlapCheck: {startTime, endTime} | null`(서울 `HH:mm`)을 더한다. 세부 일정(`schedules[]`)뿐 아니라 모집 기간(`application`)·활동 기간(`activitySchedule`)도 같은 필드를 가지며, 그 id도 "그 활동의 일정"으로 `PUT …/schedules/{scheduleId}`에 쓸 수 있다.

### 겹침 확인 시간

- 겹침 확인은 시각이 있는 일정에서만 됐다(종일 일정 제외 규칙). 운영 활동 대부분이 날짜만 있어 버튼이 안 보였다. 어드민이 일정마다 **겹침 확인 시간**(`overlap_start_time`, `overlap_end_time`, 서울 시각 HH:mm)을 둘 수 있다: 날짜만 있는 일정도 그 시간대로 기기 캘린더와 비교한다. 시각 일정은 자기 시각을 쓴다. 둘 다 없으면 그 일정은 비교하지 않는다.
- 활동 상세: 비교할 수 있는 일정이 하나라도 있으면 `겹치는 시간 확인하기` 버튼을 iOS·Android 모두 보인다. 없으면 버튼 대신 한 줄 "이 활동은 시간이 정해진 일정이 없어 겹침을 확인할 수 없어요".
- 어드민 세부 일정 편집기: 일정마다 "겹침 확인 시간" 두 칸과, 목록에 "겹침 확인 불가" 표시.

### 앱 화면

- **활동 상세의 추가 상태**: 위쪽의 긴 신청함 배너를 없앤다. 이미 내 활동에 있으면 CTA 묶음 위쪽을 넓혀 `checkmark.circle.fill`/`CheckCircle`(청록) + "이미 추가된 항목입니다" 한 줄을 두고, 그 아래 `내 활동에서 보기`·`내 활동에서 빼기`. 추가 전에는 `내 활동에 추가`.
- **활동 상세 내 활동 행동 정리(2026-10-22 사용자 "내 활동에 추가 버튼은 '추가' 텍스트 버튼을 공유 액션 옆에. 내 활동에서 보기·내 활동에서 빼기 플로우는 제외. 이미 추가된 항목입니다 우측에 '내 활동으로 >' 링크")**: 위 줄과 "내 활동 직접 추가"의 상세 보조 버튼·"빼기는 눈에 보이게" ①을 대체한다.
  - 추가 전: 상단 바 공유 행동 옆(iOS 툴바 `ShareLink` 옆 `ToolbarItem` 텍스트 버튼, Android `TopAppBar` actions의 `TextButton`)에 `추가`. 누르면 지금처럼 신청함으로 기록해 내 활동에 넣는다. CTA 아래의 `내 활동에 추가` 보조 버튼은 없앤다. 접근성 라벨 "내 활동에 추가".
  - 추가 후: 상단 바 `추가`를 숨기고, CTA 위 "이미 추가된 항목입니다" 줄 오른쪽 끝에 `내 활동으로` + chevron(iOS `chevron.right`, Android `AutoMirrored.Rounded.KeyboardArrowRight`) 링크 버튼(청록 글자). 누르면 내 활동 탭의 그 활동으로 간다(기존 `내 활동에서 보기` 동작). `내 활동에서 보기`·`내 활동에서 빼기` 버튼은 없앤다.
  - 상세 화면에서는 빼지 않는다. 내 활동 목록의 밀어서 빼기와 진행 화면의 빼기는 그대로다.
  - 웹은 내 활동이 없어 해당 없음.
- **내 활동 목록에서 밀어서 지우기**: iOS `List` + `.swipeActions(edge: .trailing)` `빼기`(또는 `onDelete`), Android Material 3 `SwipeToDismissBox`(끝에서 시작 방향), 확인 대화 후 삭제. 기존 더 보기 메뉴 빼기는 유지.
- **진행 단계 상태**: 단계마다(2026-10-26부터 선발 단계만 — `phase = SELECTION`, 아래 "선발 과정·활동 일정 분리") 일정의 `statuses`를 선택지로 보인다(기본 대기·통과·탈락, 어드민이 바꾸면 그 이름). `FAILED` 종류를 고르면 기존 탈락 규칙(끝난 활동으로).
- **저장**: 로그인했으면 서버(`/v1/me/activities`), 로그인 안 했으면 지금처럼 기기 안. 로그인하는 순간 기기 기록을 import로 올리고 기기 사본은 지운다.

### 내 캘린더에 추가

- 사용자: "내 캘린더에 추가 버튼도 있으면 좋겠어." 활동 상세(활동 기간·모집 마감·세부 일정 각각)와 진행 단계에 `캘린더에 추가`(`calendar.badge.plus`/`EventAvailable`) 버튼. 누르면 **시스템 일정 추가 화면**을 미리 채워 연다(제목 "활동명 · 일정 제목", 시각 일정은 그 시각, 날짜만 있으면 종일, 장소, 메모에 공식 링크). 저장 여부는 사용자가 그 화면에서 정한다.
- iOS: EventKitUI `EKEventEditViewController`(iOS 17+ 쓰기 전용 접근, 전체 캘린더 권한 불필요)를 SwiftUI 시트로 감싼다(SwiftUI에 같은 역할의 기본 컴포넌트가 없어 이 경우만 UIKit 브리지). Android: `Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)`와 `EXTRA_EVENT_BEGIN_TIME`·`END_TIME`·`ALL_DAY`·`TITLE`·`EVENT_LOCATION`·`DESCRIPTION`(권한 불필요). 캘린더 앱이 없으면 안내 문구.
- 앱은 기기 캘린더에 직접 쓰지 않는다(시스템 화면이 쓴다). 개인정보 고지의 "캘린더에 일정을 쓰지 않습니다"는 "사용자가 `캘린더에 추가`를 누르면 기기의 일정 추가 화면을 열 뿐 앱이 직접 쓰지 않는다"로 바꾼다.

### 개인정보

- 지금까지 "신청함·참여 확정·단계 표시는 기기에만"이라고 고지했다. 로그인 사용자는 서버에 저장되므로 `/privacy` 3절과 2절을 고치고 문서 시행일·동의 버전을 `2026-10-22`로 올린다(로그인 사용자는 다음 저장 때 다시 동의). 기기 캘린더 일정은 여전히 서버로 보내지 않는다.

### 지난 선발 단계 통과 제안 — 2026-10-26

사용자: "지난 선발 단계 자동 통과 제안 넣어줘." 우아콘처럼 선발이 끝난 행사를 내 활동에 추가하는 사람은 거의 통과자인데, `신청 기간`·`추첨 결과 발송` 단계가 기본 `대기`로 남아 `참여 중`이 되지 않았다. 자동으로 표시하지 않고 **한 번 묻는다**(표시는 여전히 사용자가 남기는 것).

- **시점**: 활동을 내 활동에 넣는 모든 흐름(`+`, `내 활동에 추가`, 신청 확인 `신청했어요`) 직후. 신청 확인 시트에서 넣었으면 그 시트가 닫힌 뒤 띄운다(두 시트를 한 번에 바꾸지 않는다).
- **조건·대상**: 그 활동의 선발 단계(`phase: SELECTION`, 옛 서버는 기존 판정)를 트랙 순서로 보아, **첫 미래 날짜 선발 단계 앞까지**의 단계가 대상이다 — 끝 시각(없으면 시작 시각)이 이미 지난 날짜 단계와 그 사이의 날짜 없는 단계(예: 날짜 미정 발표). 미래 단계 뒤의 날짜 없는 단계는 제외. "지남"은 `now` 기준 시각 비교(날짜만인 일정은 끝 날짜 다음 서울 0시, 즉 그 날이 끝나면 지남)로, 트랙의 현재 단계 판정(서울 날짜)과 달리 오늘 오전에 끝난 발표도 지난 것이다. 아직 표시가 없는 단계(기본 `대기`)만 대상이고, 그 일정의 `statuses`에 `PASSED` 종류가 없으면(어드민이 바꿈) 뺀다. 지난 날짜 단계가 하나도 없으면 묻지 않는다.
- **묻지 않는 경우**: 활동 상태가 `ENDED`(끝난 활동 묶음으로 가므로), 트랙이 이미 끝난 활동(모든 단계가 지남), 대상이 없는 활동, 그리고 **이미 한 번 물은 활동**(묻는 시점에 활동마다 기록, 답과 무관; iOS `UserDefaults` `dearby.mine.autopassAsked` 활동 id 목록 — 시트를 끌어내려도, 빼고 다시 넣어도 다시 묻지 않는다).
- **시트**: 신청 확인 시트와 같은 배치(iOS `CenteredMessage`, Android Material 시트). 제목 `지난 선발 단계를 통과로 표시할까요?`. 본문은 둘 중 하나 — 대상이 (아직 통과 표시가 없는) 선발 단계 전부면 `{n}개 단계(지원, 입장권 추첨 결과 발송)가 이미 지났어요. 통과로 표시하면 바로 참여 중으로 보여요.`, 남는 선발 단계가 있으면 `{n}개 단계(지원, 서류 발표)가 이미 지났어요. 통과로 표시해요. 남은 단계(면접)는 그대로 둬요.` 단계 이름은 트랙 표기 그대로(모집 슬롯은 `지원`). 버튼 `통과로 표시`(기본·강조, iOS 식별자 `autopass-mark`) / `그대로 두기`(텍스트, `autopass-keep`). 끌어내려 닫으면 `그대로 두기`.
- **통과로 표시**: 포함된 단계마다 그 일정의 `PASSED` 상태(어드민 지정이면 그 id, 기본이면 `{kind: PASSED}`)를 기존 단계 표시 API `PUT /v1/me/activities/{activityId}/schedules/{scheduleId}`로 하나씩 보낸다. iOS는 먼저 `PUT /v1/me/activities/{activityId}`(멱등)로 추가가 서버에 닿았음을 확인한 뒤 순서대로 보내고, 실패한 단계는 그대로 둔다(기존 실패 안내 뒤 목록을 다시 읽어 성공한 단계는 남는다). 비로그인은 같은 규칙으로 기기 기록(iOS는 지금처럼 모델 메모리, 로그인 때 import).
- **사용 통계**: 표시한 단계마다 `stage_mark(kind=passed, via=autopass_prompt)`([analytics-v1.md](analytics-v1.md) `stage_mark`). 진행 단계 화면에서 고른 것은 `via` 없음.
- 구현: iOS `ActivityProgress.autopassCandidates`·`AutopassSuggestion`·`ActivityViewModel.autopass`(`feat/autopass-ios`; 신청 확인 시트와 함께 `promptLayer`의 맨 위 층에서 띄우므로 맞춤 대기열 전체 화면과 그 위 상세 시트에서도 뜬다), Android PR #435(`feat/autopass-android`)가 같은 규칙.

## 웹 리디자인 — iOS 앱과 같은 표현 — 2026-10-22 사용자 결정

사용자: "웹 리디자인해 줘. iOS랑 비슷했으면 좋겠어. shadcn을 활용해도 좋아." 웹(`apps/web`)의 기능·경로·데이터·프록시는 그대로 두고 표현만 iOS 앱에 맞춘다.

- **기반**: Tailwind CSS v4 + shadcn/ui(필요한 컴포넌트만 `src/shared/ui/`에 복사: Button, Input, Switch, Tabs(세그먼트), Card, Sheet, Dialog, Badge, Separator, Skeleton). 기존 `globals.css`의 화면별 클래스는 걷어내고 토큰만 남긴다.
- **토큰(iOS 대응)**: 배경 `systemGroupedBackground`(#F2F2F7 / 다크 #000), 카드 면 `secondarySystemGroupedBackground`(#FFF / #1C1C1E), 채움 `tertiarySystemFill`(rgba(118,118,128,.12) / .24), 구분선 `separator`, 글자 `label`/`secondaryLabel`, 액센트 청록(글자·아이콘 #007F80 / 다크 #3FB3B4), 채움 면 `tealFill` #007F80 + 흰 글자(두 모드). 글꼴: `-apple-system, BlinkMacSystemFont, "Pretendard", system-ui, sans-serif`, iOS 크기 체계(largeTitle 34/700, title2 22/700, headline 17/600, body 17, subheadline 15, footnote 13). 모서리 카드 20, 컨트롤 10. 다크 모드 `prefers-color-scheme`.
- **셸**: iOS 내비게이션 바처럼 상단 고정 반투명 막대(`backdrop-filter: blur`, 바탕 흐림, 스크롤하면 아래 hairline). 페이지 첫 화면은 큰 제목(largeTitle)이 막대 아래에 있다가 스크롤하면 막대 가운데 작은 제목으로 바뀐다. 오른쪽에 저장한 명함 링크. 내용 폭은 모바일 기준(최대 720px 가운데, 좌우 16).
- **발견**: 검색 입력은 iOS 검색 필드(높이 36, 둥근 10, 채움, 돋보기·지우기), `마감 숨기기`는 iOS 스위치 행. 프로그램 카드는 iOS 앱 카드와 같은 구조(로고 44 둥근 14, 이름·조직·태그 칩, 오른쪽 개수 배지 ↔ 펼침 화살표, 부드러운 펼침), 활동 행은 inset grouped 카드 안의 행.
- **활동 상세**: 상단 정보 묶음, inset grouped 섹션(모집 기간·활동 기간·장소·대상·일정), 하단 고정 CTA(전체 너비 `tealFill` 버튼 + 보조 텍스트 버튼, 안전 영역 여백).
- **공개 명함 `/cards/:id`·저장한 명함 `/saved`**: 앱 명함 면과 같은 표현, 저장 CTA는 하단 고정. 목록은 inset grouped.
- **상태 화면**: 기존 `CenteredMessage` 규칙(64 원·제목·설명·CTA) 유지.
- 접근성: 터치 영역 44, 포커스 링, `prefers-reduced-motion`. 성능: 클라이언트 JS는 필요한 곳(펼침·스위치·시트)만.

## 반복 일정과 겹침 결과 묶음 — 2026-10-22 사용자 결정

사용자: "ICS로 추가할 때 매주 이 시간처럼 묶어서 할당할 수 있어? 부트캠프는 평일 오전 9:00~17:00 6개월간 이런 식으로 표현되기도 해. 그런데 겹치는 시간 찾기 때 이미 그런 일정이 있으면 충돌 메시지가 너무 많을 것 같아." → "반복 일정끼리 겹치면 중복 제거 후 하나만" → "그렇게 해 주세요."

### 데이터·API

- `schedules.recurrence varchar(200) null`: RFC 5545 RRULE 본문(접두사 `RRULE:` 없이). 허용 부분집합: `FREQ=DAILY|WEEKLY`, `BYDAY`(MO..SU 목록), `INTERVAL`(1~4), `UNTIL`(yyyyMMdd 또는 yyyyMMddTHHmmssZ) 또는 `COUNT`(1~400) 중 하나 필수. 그 밖은 400.
- 반복 일정의 한 회차 시간: 시각 일정이면 `startAt`~`endAt`이 첫 회차(서울 시간대 기준 반복), 날짜만 있는 일정이면 `overlapCheck`(HH:mm 범위)가 매 회차 시간이고 `startDate`가 첫 날, `UNTIL`/`COUNT`가 끝. 반복이 있으면 겹침 확인이 날마다 창을 만들던 동작(종일 + overlapCheck)을 대신한다.
- 공개 `Schedule.recurrence: string | null`. 어드민 일정 PATCH/POST에 `recurrence`(null 지움), 활동 PATCH/POST의 `application`/`activitySchedule` 안에도 같은 키. 어드민 응답에도 포함.
- ICS 구독 피드는 없다. 기기 캘린더로 들어가는 길은 앱의 "캘린더에 추가" 하나다.
- 수집기 `WORKFLOW.md`·제출 스키마에 `recurrence`를 더한다: "평일 09:00~17:00, 6개월"처럼 규칙적인 시간표가 본문에 있으면 RRULE로 쓰고, 근거 구절에 그 문장을 인용한다. 확실하지 않으면 쓰지 않는다.

### 어드민

- 세부 일정 편집기와 모집·활동 기간 블록에 "반복" 항목: 끔 / 매일 / 매주(요일 체크박스 월~일, 기본 월~금), 간격(매 n주), 끝(날짜 또는 횟수). 미리보기 문구("평일 09:00~17:00, 2027년 3월 11일~2027년 8월 31일, 128회"). 저장은 RRULE 문자열.

### 앱 — 캘린더에 추가

- 반복이 있으면 시스템 일정 추가 화면에 반복 규칙을 붙여 연다: iOS `EKRecurrenceRule`(요일·간격·끝), Android `ACTION_INSERT`에 `CalendarContract.Events.RRULE` 추가(시작 시각은 첫 회차, `ALL_DAY` false). 한 번에 반복 일정 하나로 들어간다.
- 메모(iOS `notes`·`url`, Android `DESCRIPTION`)에 활동 링크 `https://dearby.wid.io.kr/activities/{id}`를 넣는다. 겹침 확인은 메모·URL에 이 링크가 있는 기기 일정을 **비교에서 뺀다**(자기 자신과 겹치지 않게).

### 앱 — 겹침 확인과 결과 묶음

- 활동 일정 쪽: 반복이 있으면 규칙을 풀어 회차 창을 만든다(최대 400회, 6개월 넘는 반복도 400회까지만). 반복이 없으면 지금 규칙(시각 일정, 또는 날짜 + overlapCheck 날마다) 그대로.
- 기기 일정 쪽 세기(요약용):
  - 같은 기기 일정의 회차는 하나로 센다 — iOS `calendarItemIdentifier`(분리된 예외 회차 포함), Android `Instances.EVENT_ID`(예외는 `ORIGINAL_ID`).
  - 다른 캘린더에 동기화된 같은 일정(제목·시작·끝 같음)은 하나로 합친다.
  - 맨 위 요약: "n일 겹침 · 반복 일정 a개 · 단일 일정 b개". 겹침이 없으면 지금처럼 "겹치는 일정이 없어요".
- 결과 목록은 아래 "겹침 결과 시간표 연달아"(2026-10-26)를 따른다. 2026-10-22~10-26에는 기기 일정별 묶음 줄(반복 일정 "제목 · 매주 화 10:00~11:00 — n번 겹침", 단일 일정 5개 뒤 "더 보기")을 누르면 날짜·시간표가 펼쳐졌다.
- 겹침 하나의 시간표(2026-10-26 사용자 요청): 보이는 구간은 겹침 앞 활동 최대 1시간~겹침 끝 30분 뒤이고, 왼쪽 눈금과 두 블록(활동·기기 일정)·겹침 띠는 같은 분당 높이로 비례 배치한다. 눈금 간격은 15분·30분·1시간·2시간·3시간·4시간·6시간·12시간 중 구간을 6칸 이하(라벨 약 6개)로 나누는 가장 작은 값이고, 활동 시간대의 자정 기준 정시에 맞춘다. 전체 높이는 최소·최대를 두고(Android 192~288dp, iOS 190~280pt), 아주 짧은 겹침 띠·블록도 라벨이 읽히는 최소 높이를 갖는다. 구간 밖으로 이어지는 블록은 구간에 맞춰 자르고 '이전부터 계속'/'이후에도 계속'을 붙인다. 이 구간에는 두 블록이 모두 없는 긴 빈 구간이 생기지 않으므로 접기는 두지 않는다.
- 기기 일정 제목은 여전히 화면에만 보이고 저장·전송하지 않는다.

### 겹침 결과 시간표 연달아 — 2026-10-26 사용자 결정

사용자: "겹치는 일정 찾기는 항목마다 하기가 너무 불편해서 초기처럼 연달아 보여 줬으면 좋겠어."

- 겹침이 둘 이상이면 요약 아래에 **모든 겹침의 시간표를 날짜순으로 차례로 이어서** 바로 보인다. 누르거나 펼칠 것이 없다(초기 2026-10-06 판은 한 화면에 하나씩 `확인했어요 · 다음 일정`으로 넘겼다).
- 겹침마다 머리글 두 줄: "날짜(요일) · 기기 일정 제목", "시간 범위 · n분 겹침 · 반복 문구"(반복 기기 일정만; 문구는 기기 일정 규칙에서, 읽을 수 없으면 "반복 일정"). 그 아래 "겹침 하나의 시간표" 그대로.
- 처음 10개만 보이고 `더 보기 (n개 남음)`을 누를 때마다 10개씩 더 보인다(긴 부트캠프와 주간 일정처럼 수백 번 겹칠 수 있음).
- 겹침이 하나면 지금처럼 그 문장과 시간표, 없으면 "겹치는 일정이 없어요".

## 트랙·세션 시간표 — 2026-10-26 사용자 결정

사용자: 컨퍼런스처럼 **트랙(강연장)이 나뉘어 같은 시간에 여러 세션이 동시에 진행**되는 활동을 표현한다(참고 https://woowacon.com/sessions). 정정: "전체 강연 일정은 (우아콘 기준) 10:50~12:30 오전 세션, 12:30~13:50 쉬는시간, 13:50~17:30 오후 세션으로 수집하고, 트랙이 따로 준비돼 있으면 오전 세션 시간표·오후 세션 시간표 버튼을 만들고 지정할 수 있게 해 줘." 추가: "트랙이 없는 경우에도 상세 시간표는 볼 수 있게 해 줘." 이번 단계는 계약·서버·어드민 API·수집기이고 앱·웹 화면은 다음 단계다.

### 구조 — 일정 블록의 하위 시간표

- 활동의 기존 일정(`activitySchedule`·`schedules[]`)은 그대로 **큰 블록**(오전 세션·쉬는시간·오후 세션, 여러 날이면 날마다 블록)이다. 트랙 시간표는 **블록 하나에 딸린 하위 시간표**다. 기존 일정 데이터는 바뀌지 않고 이관도 없다. 일차(여러 날)는 블록이 날마다 따로 있으므로 시간표에 일차 개념을 두지 않는다.
- 시간표를 붙일 수 있는 블록: 활동 기간(`activitySchedule`)과 세부 일정(`schedules[]`) 중 **시작·끝 시각이 있는 시각 일정** 또는 종일 일정(서울 자정 기준 그 날짜들). 모집 기간·반복 일정(`recurrence`)·날짜 미정 일정에는 붙이지 않는다(어드민 400).
- 트랙 Track `{id, name, room, position}`: 이름(≤50, 블록 안에서 유일), 방(강연장 이름, 선택 ≤100), 표시 순서 `position`(0부터). **색은 서버가 저장하지 않는다**: 앱·웹이 `position` 순서로 자기 팔레트에서 고른다(같은 순서면 같은 색). 트랙은 0~20개이고, **0개면 단일 진행**이다.
- 세션 Session `{id, scheduleId, trackId, kind, title, description, startAt, endAt, speakers: [{name, affiliation}], tags: Tag[], favorited}`:
  - `kind`: `TALK`(일반) · `KEYNOTE` · `BREAK`(쉬는시간) · `MEAL`(점심 등) · `OTHER`.
  - `trackId: null` = **모든 트랙에 걸친 전폭 세션**(키노트·쉬는시간·점심). 트랙이 없는 시간표는 모든 세션이 `null`이다.
  - 시각은 부모 블록의 시간대 오프셋(종일 블록은 `Asia/Seoul`)이고 **반드시 부모 블록의 시간 범위 안**이다. 같은 트랙의 세션끼리, 그리고 전폭 세션과 다른 모든 세션은 시간이 겹치지 않는다(서버 검증).
  - `tags`는 기존 태그 사전(`GET /v1/tags`)에서 고른 분야 태그, 보낸 순서, 최대 5개. 연사 최대 10명(이름 ≤100, 소속 선택 ≤100). 제목 ≤200, 설명 선택 ≤2000. 세션 제목·연사는 한국어 원문만 있다(`Accept-Language`와 상관없음).
- 저장(V61): `schedule_tracks(id, schedule_id → schedules cascade, name, room, position)`, `schedule_sessions(id, schedule_id → schedules cascade, track_id → schedule_tracks cascade null, kind, title, description, start_at, end_at, speakers jsonb)`, `schedule_session_tags(session_id, tag_id, position)`, `user_session_favorites(user_id → users cascade, session_id → schedule_sessions cascade, created_at)`.
- **재수집과 id 유지**: 수집은 운영자 지정이 아닌 세부 일정을 매번 지웠다 다시 넣는다. 그래서 세부 일정 블록에 시간표가 붙으면(어드민·수집·가져오기 모두) 서버가 그 블록을 운영자 지정(`schedules.curated = true`)으로 올려 재수집이 지우거나 다시 쓰지 않는다 — 세션 id와 찜이 남는다. 대가로 그 블록의 시각은 이후 수집이 고치지 않는다(어드민에서 고친다).

### 공개 API

| 메서드·경로 | 인증 | 성공 응답 |
| --- | --- | --- |
| GET /v1/activities/:id/schedules/:scheduleId/timetable | 없음(Bearer가 오면 `favorited`를 채우고 `Vary: Authorization`) | 200 `{activityId, schedule: Schedule, tracks: Track[], sessions: Session[]}`(세션은 시작 순). 게시되지 않은 활동, 그 활동의 블록이 아니거나 지운 블록, 모집 기간, 세션이 없는 블록은 404 |
| PUT /v1/me/session-favorites/:sessionId | Bearer | 204(멱등). 게시되지 않은 활동이나 지운 블록의 세션, 없는 세션은 404 |
| DELETE /v1/me/session-favorites/:sessionId | Bearer | 204(없어도) |
| GET /v1/me/session-favorites | Bearer | 200 `{items: [{activityId, activityName, scheduleId, scheduleTitle, schedule: Schedule, track: Track\|null, session: Session, favoritedAt}]}` — 내 시간표, 세션 시작 순. 게시되지 않았거나 지운 블록의 세션은 목록에서 뺀다(행은 남긴다). `schedule`(부모 블록 전체: 시작·끝·시간대)·`track`(전폭 세션이면 null)은 2026-10-26 추가: 앱이 활동·시간표를 다시 조회하지 않고 그린다 |

- `Schedule.hasTimetable: boolean`(공개 Activity의 `activitySchedule`·`schedules[]`, 어드민 일정 응답에도): 그 블록에 세션이 하나라도 있으면 true. 활동 응답에는 시간표 본문을 싣지 않는다(세션이 수백 개일 수 있어 위 별도 조회).
- 목록 배지용(2026-10-26 추가, 필드 추가만): `Activity.hasTimetable: boolean`(`activitySchedule`·`schedules[]` 중 하나라도 `hasTimetable`), `ProgramSummary.hasTimetable: boolean`(그 묶음의 **모집 중·모집 예정** 활동 — `kind`·`category`·유형·태그·대상자 필터를 반영한 같은 활동들 — 중 하나라도 시간표가 있으면 true; 마감된 활동만 있으면 false). 접힌 프로그램 카드와 활동 행에 "시간표" 배지를 단다. iOS(2026-10-26): 발견·즐겨찾기·프로그램 상세의 접힌 프로그램 머리에 `ProgramSummary.hasTimetable`로 `시간표` 배지(펼치면 활동 카드가 각자 단다), 활동 카드(발견·즐겨찾기)는 일정 블록의 `hasTimetable`로 단다(`Activity.hasTimetable`과 같은 값).
- 비로그인: 시간표 보기는 되고, 세션 하트를 누르면 기존 로그인 시트(위 "비로그인에서 만들기·추천을 막음"). 동의 전 계정은 앱이 동의 화면으로 막는다(기존 즐겨찾기와 같이 앱만 막고 서버는 프로필 하위만 강제).

### 세션 찜과 다른 기능의 관계

- 세션 찜은 **활동 즐겨찾기·알림·추천·내 활동과 따로**다(부수 효과 없음). 세션을 찜해도 활동 하트가 켜지지 않고, 맞춤 대기열 `LIKE`·푸시 알림 거리(`push_deliveries`는 `schedules`만 본다)도 바뀌지 않는다. 대안(세션 찜 = 활동 자동 즐겨찾기)은 알림 수준·추천 신호까지 끌고 와 고르지 않았다.
- 개인정보: 세션 찜은 계정 기록이다(사용자·세션·찜한 시각). 계정 삭제 때 users cascade로 지워진다. 처리방침의 "즐겨찾기" 항목에 세션 찜을 더해야 한다(조율 세션 판단, 이번 변경은 서버만).

### 겹침

- **찜 겹침**: 앱이 `GET /v1/me/session-favorites`(또는 열린 시간표의 `favorited`)로 계산한다 — 찜한 세션끼리 `a.startAt < b.endAt && b.startAt < a.endAt`이면 겹침(같은 블록의 다른 트랙에서 동시간에 찜한 경우가 대부분). 서버는 겹침을 막지 않는다.
- **기기 캘린더 겹침**(위 "내 활동 서버 저장·일정 상태·겹침 확인 시간"·"반복 일정과 겹침 결과 묶음"): 지금처럼 **블록 단위**로 비교한다. 세션은 블록 안에 있으므로 블록 비교로 충분하고 세션을 따로 비교하지 않는다. 캘린더에 추가도 블록 단위 그대로다.

### 앱·웹 화면(다음 단계)

- **모달 시트(2026-10-26 사용자 결정 "그럼 차라리 모달로 보여줘")**: 활동 상세의 일정 블록 중 `hasTimetable`인 행은 **행 전체를 누르면** 그 블록의 시간표가 모달 시트로 뜬다. 행은 발견 목록 행처럼 오른쪽에 `시간표` 표시와 chevron(iOS `chevron.right`)을 두고, `캘린더에 추가`는 따로 누른다. 상세 안에 제자리로 펼치면 표의 가로 넘기기와 상세의 세로 스크롤이 겹치므로 시트로 띄운다. 시트(iOS `.sheet`, `.large` detent·drag indicator 보임): 상단 제목 `{활동 이름} · {블록 제목}`과 보조 줄에 블록의 날짜·시간, `닫기` 버튼, 그 아래 `전체 | 내 시간표` 전환과 표(표현은 아래 "`내 시간표(찜한 세션)`"). 시트가 열릴 때 시간표를 불러오고, 그동안 로딩, 실패하면 `다시 시도`. (같은 날 처음 판의 별도 버튼 → 시간표 화면 push는 행 전체 버튼 + 모달로 바뀌었다.)
- **웹 시간표 모달(2026-10-26)**: `hasTimetable` 블록 행 전체가 버튼이고 오른쪽에 `시간표`와 chevron이 있다. 누르면 모달이 열린다. 모바일 폭은 거의 전체 높이 바텀 시트, `md`(768px) 이상은 가운데 큰 dialog. 머리글은 제목 `{활동 이름} · {블록 제목}`, 그 아래 시간 범위, 닫기 버튼. 본문은 두 방향으로 스크롤하고 시간 열과 트랙 머리를 고정한다. 웹은 로그인이 없어 하트와 `전체 | 내 시간표` 전환이 없고, 하단에 "세션 찜은 Dearby 앱에서 할 수 있어요." 한 줄을 둔다. 모달을 열 때마다 불러오고, 로딩 중에는 skeleton, 실패하면 다시 시도. (2026-10-26 웹 로그인 2단계 A부터 웹도 하트와 `전체 | 내 시간표` 전환을 두고 안내 줄은 비로그인에만 — "웹 화면 — 2단계 A")
- **표(우아콘 오후 시간표 캡처 기준, 2026-10-26)**: **행 = 시간대**(왼쪽 시간 칸 "13:50 – 14:30"), **열 = 트랙**(`position` 순서 고정: 트랙 A, B, C…). 각 칸은 세션 카드: 트랙 배지(트랙 색), 분야 태그 최대 2개, 제목, 연사(이름·소속), 하트(iOS `heart`/`heart.fill`, Android `FavoriteBorder`/`Favorite`, 접근성 `찜`/`찜 해제`).
  - 그 시간대에 세션이 없는 트랙 칸은 **비워 둔다**(빈 어두운 칸). 열 정렬을 지키고 다른 세션을 당겨 채우지 않는다.
  - 전폭 세션(`trackId` null: 키노트·쉬는시간·점심)은 **한 행 전체 폭** 한 장. `BREAK`·`MEAL`은 "쉬는시간"·"점심" 띠 모양.
  - 행 나누기: 기본은 세션들의 **고유한 (시작, 끝) 쌍** 하나가 한 행이고 시작 순이다. 트랙마다 경계가 달라 쌍이 부분적으로 겹치면(예: A 13:50–14:30, B 13:50–14:50) 그 구간의 모든 경계 시각으로 행을 잘게 나누고, 세션 카드는 자기 시간 범위에 해당하는 연속 행을 함께 차지한다(행 높이 합만큼, 표의 rowspan). 행의 시간 칸은 그 행의 시작–끝.
  - 트랙이 0개면 열이 하나인 표: 가로 넘기기 없이 시간대 행 + 세션 카드 한 장씩.
  - 트랙 색: 서버는 순서만 준다. 앱·웹이 `position` 순서로 자기 팔레트에서 고르고(같은 순서면 같은 색), 라이트·다크 모두 배지 글자와 배경의 대비를 확보한다.
- **모바일 "트랙 가로 넘기기"(2026-10-26 사용자 확정)**: **표 전체를 한 번에 가로 스크롤**(모든 행이 같이 움직여 열 정렬을 지키고, 왼쪽 시간 칸은 고정)이다. 행마다 따로 넘기면 빈 칸·열 정렬이 깨지기 때문이다(사용자 표현 "시간대마다 트랙 카드를 가로로 넘기기"를 이렇게 구현하기로 확정). 웹·태블릿처럼 넓은 화면은 가로 스크롤 없이 전체 열을 보인다.
- 찜 겹침은 카드에 "같은 시간 찜 n개" 같은 표시(문구는 앱 담당).
- **`내 시간표(찜한 세션)`(2026-10-26 사용자 "세션 시간표에서 찜했다면 해당 찜한 시간표만 따로 볼 수도 있게 해 줘"; 같은 날 사용자 결정 "찜한 세션 대신 내 시간표라고 표현을 변경해줘" — 사용자에게 보이는 이름은 `내 시간표`, 코드 식별자·API·사용 통계 이름(`session_favorites`, `favorite_add type=session` 등)은 그대로)**: 표에서 빈 칸만 남기지 않고, 내 시간표에 담은(찜한) 세션만 **시간순 한 줄**(가로 넘기기 없음)로 보인다. 카드마다 시간과 **트랙 이름·방(room)을 크게**(이동할 강연장 안내), 세션 사이에 빈 시간(`40분 비어 있어요`)과 다른 트랙·방으로 바뀌면 이동 표시(`이동 › 트랙 B · 201호`), 같은 시간대에 찜이 겹치면 그 묶음 앞에 경고 띠(`같은 시간대에 세션이 겹쳐요`, 2026-10-26 문구 확정). 비로그인이면 로그인 안내(`내 시간표는 계정에 모여요`), 찜이 없으면 빈 안내(제목 `내 시간표가 비어 있어요` + 설명 `세션에 하트를 누르면 내 시간표에 모여요.`; 즐겨찾기 › 내 시간표 화면도 같은 문구). 하트의 접근성 라벨 `찜`/`찜 해제`는 그대로다. 쉬는시간·점심은 찜(하트)이 없다.
- iOS 구현 메모(2026-10-26): 행 높이는 고정(세션 행 168pt·쉬는시간/점심만 있는 행 52pt, 글자 크기 배율을 따름)이고 긴 제목은 카드에서 3줄로 줄이고 시트에 전부 보인다. 트랙 색은 시스템 색 팔레트(파랑·주황·보라·초록·분홍·남색·갈색·청록·빨강·민트, 노랑 제외)를 `position` 순서로 돌려 쓰고, 배지는 그 색 18% 바탕 + 기본 글자색(라이트·다크 대비). 열이 화면에 다 들어가면(아이패드·가로) 열을 넓혀 가로 넘기기가 생기지 않는다.
- **활동 밖 내 시간표(2026-10-26 사용자 결정)**: 내 활동 탭 › 즐겨찾기(캘린더) 화면 위에 찜이 하나라도 있으면 `내 시간표 · 세션 n개` 행(보조 문구 `시간순으로 모아 보기`; 열리는 화면 제목 `내 시간표`, 2026-10-26 표현 변경) → `GET /v1/me/session-favorites`를 **활동별, 그 안에서 날짜별**로 묶어 각 날을 위 내 시간표 모양으로 보인다(트랙 이름·방과 시간대는 목록 항목의 `track`·`schedule`로 그린다 — 2026-10-26 서버 #401 뒤로 iOS는 블록별 시간표를 다시 읽지 않는다; `track`이 null이거나 옛 응답이면 트랙 배지 없이, 시간대는 서울). 즐겨찾기 캘린더의 날짜 칸에는 내 시간표 세션이 있는 날에 분홍 점 하나를 더한다(VoiceOver·TalkBack은 `, 내 시간표 세션 있음`)(아래 날짜 목록은 활동 그대로).
- **내 활동(2026-10-26 사용자 "내 활동에서도 시간표를 볼 수 있으면 좋겠어", 같은 날 "진행단계에서 시간표 있는 일정은 시간표 아이콘 표시해주고 펼쳤을때 시간표보기 버튼이 있으면 좋겠어")**: 내 활동 목록 행은 `Activity.hasTimetable`이면 활동 이름 옆에 `시간표` 배지. 진행 단계 화면(위 "진행 단계 화면 정리")에서는 일정 블록이 `hasTimetable`인 단계 행 오른쪽(상태 표시 왼쪽)에 시간표 아이콘(`tablecells`/`ViewTimeline`, 보조색)을 보이고 접근성 라벨에 `시간표 있음`을 넣는다. 그 행을 눌러 열리는 메뉴의 맨 위에 `시간표 보기`가 있고, 누르면 활동 상세와 같은 시간표 모달 시트. 같은 날 앞선 판의 별도 `시간표` 섹션은 중복이라 지웠다(iOS·Android). 내 활동의 Activity는 공개 활동 응답(`Schedule.hasTimetable` 포함)을 그대로 쓰므로 다시 읽지 않는다(필드가 없는 옛 응답과 `false`는 앱에서 구별되지 않아 시간표 없음으로 본다).

### 하루 시간표 — 2026-10-26 사용자 결정

사용자: "우아콘을 예시로 들면 10/28일 누르면 시간표보기 일정추가 이런식으로 나오고 시간표보기 누르면 지금 여러 트랙 나뉜 시간표처럼 보여주고. 트랙 나뉜 곳은 찜하기로 고를 수 있게 해주고." 데이터·API는 바꾸지 않고 **표현**만 바꾼다(블록·세션·찜은 위 그대로).

- **날짜 묶음 행(활동 상세·내 활동 진행 단계의 `활동 일정`)**: 같은 서울 날짜에 시각 블록이 둘 이상이면 날짜 행 하나(`10월 28일 (수)` · `10:00 ~ 17:30 · 블록 4개`, 블록 중 하나라도 `hasTimetable`이면 `시간표` 표시)로 접는다. 묶는 블록은 시작·끝 시각이 있고 **같은 서울 날짜 안**에 있는 `schedules[]` 블록만이다 — 활동 기간(`activitySchedule`), 종일·날짜 미정·날짜를 넘는 블록은 기존 행 그대로. 날짜에 블록이 하나뿐이면 기존 행 그대로. 날짜 행은 그 날 첫 블록 자리에 놓인다.
  - **펼침(메뉴가 아님), 기본 접힘**: 날짜 행을 누르면 그 아래로 `시간표 보기`(그 날 블록 중 하나라도 `hasTimetable`일 때)와 `캘린더에 추가`(그 날 전체 범위: 제목 `{활동 이름} · 10월 28일 (수)`, 가장 이른 시작~가장 늦은 끝, 장소·링크는 블록 추가와 같음), 그 아래 블록 목록(기존 행 — 시간표 블록 행은 그대로 블록 시트를 열고 `캘린더에 추가`도 블록별로 그대로). 진행 단계 화면에서는 블록 행의 메뉴·상태 표시(단계 표시는 블록 단위)가 그대로 안에 들어간다. iOS는 상세에서 `chevron.down`이 돌아가는 접기 행, 진행 단계 `List`에서는 `DisclosureGroup`(기본 컴포넌트).
  - 상세의 `겹치는 시간 확인`은 그대로(블록 단위 비교).
- **하루 시간표 시트**: `시간표 보기`는 그 날의 모든 시각 블록을 **하나의 표**로 보인다. 제목 `{활동 이름} · 10월 28일 (수)`, 보조 줄 `10:00 ~ 17:30 · 블록 4개`, `닫기`, `전체 | 내 시간표` 전환(그 날 전체 기준), 표는 위 "표"·"모바일 트랙 가로 넘기기"와 같다.
  - **트랙 열 합치기**: 그 날 블록들의 트랙을 **이름**(앞뒤 공백 무시)으로 합친 집합이다 — 우아콘은 오전·오후 모두 트랙 A~G라 7열, 이름이 같으면 같은 열(방은 먼저 나온 블록의 것). 열 순서는 블록 순서 → 블록 안 `position` 순서로 처음 나온 차례이고, 색은 그 합친 순서(0부터)로 팔레트에서 고른다 — 블록 시트와 같은 트랙이 다른 색일 수 있다(블록마다 트랙 구성이 다를 때만). 세션의 `trackId`는 같은 이름의 열로 보낸다.
  - **시간표 없는 블록**(`hasTimetable` false, 또는 true였지만 404, 또는 세션이 비어 있음)은 **전폭 띠**(`trackId` null 세션 중 쉬는시간·점심과 같은 띠 모양, 블록 제목; 제목에 쉬는/휴식/브레이크·점심/저녁/식사가 있으면 그 띠의 아이콘)로 넣는다. 하트·세션 상세 없음. 그 자리의 식별자는 앱 안에서만 쓰고(음수) 어디에도 보내지 않는다.
  - **읽기**: 블록마다 기존 `GET /v1/activities/:id/schedules/:scheduleId/timetable`을 **한 번에 함께** 읽어 합친다(로딩 한 번). **실패한 블록**은 전폭 띠에 `{블록 제목} · 불러오지 못했어요`로 들어가고, 하나도 읽지 못했으면(모두 실패) 시트 전체가 `다시 시도`, 읽을 것이 없으면(모두 404) `시간표가 없어요`. 하트·찜은 기존 세션 찜 그대로(트랙이 있는 세션마다), `내 시간표`의 빈 시간·이동 표시·찜 겹침은 그 날 전체 기준.
- **블록 시트의 `이 날 전체 보기`**: 기존 블록 단위 시간표 시트(상세의 시간표 블록 행, 진행 단계 행 메뉴의 `시간표 보기`, 오늘 카드, 이동 알림 탭 라우팅)는 그대로 두고, 그 블록과 같은 날에 다른 시각 블록이 있을 때만 시트 상단에 작은 버튼 `이 날 전체 보기`(iOS는 내비게이션 바 오른쪽)를 둬 같은 시트 안에서 하루 시간표로 바꾼다(다시 띄우지 않음; `전체 | 내 시간표` 선택은 유지). 돌아가는 버튼은 두지 않는다(닫고 다시 열면 블록).
- **사용 통계**: 새 이벤트 없음. 앱 화면 `timetable`(하루 시트도 같은 화면 이름, 열 때 한 번)·웹 `timetable_view`(하루 모달도 열 때 `activity_id`로 한 번) — [analytics-v1.md](analytics-v1.md).
- **웹 차이(PR #447, 2026-10-26)**: 로그인이 없어 `캘린더에 추가` 대신 **`.ics 내려받기`**(그 날 블록마다 VEVENT 하나, 블록 주소 = `LOCATION`, 활동 상세 링크 = `URL`). 블록 모달 머리의 `이 날 전체 보기`는 그 날 블록이 둘 이상일 때만. 시간표 없는 블록·404·빈 블록은 전폭 띠, 실패 블록은 `불러오지 못했어요` + `다시 시도`. 웹 하루 모달의 하트는 웹 2단계 A(웹 로그인·세션 찜)가 아직 없어 그 PR에서 붙는다.
- Android(feat/day-timetable-android)는 같은 규칙(묶음 조건·펼침·합치기·전폭 띠·실패 처리·`이 날 전체 보기`)을 따른다.

### 어드민·수집

- 어드민 API: admin-v1.md "트랙·세션 시간표". 화면(일정 블록마다 `시간표` 편집)은 다음 단계.
- 수집기: 큰 블록을 기본으로 `schedules[]`에 뽑고, 트랙·세션 정보가 본문에 있으면 그 블록의 `timetable`로 뽑는다(`WORKFLOW.md`). 서버는 블록 시간 안·겹침 없음·세션 제목이 원문에 있음을 확인하고, 어긋나면 그 블록의 시간표만 버린다. 시간표는 블록이 처음 수집될 때만 붙는다(붙은 블록은 운영자 지정이 되어 재수집이 다시 쓰지 않음).

## 찜한 세션 이동 알림 — 2026-10-26 사용자 결정

사용자: "컨퍼런스 참여 시 중간에 이동 알림". 1단계는 **내 시간표(찜한 세션; 2026-10-26 표현 변경, 절 제목은 참조 anchor로 유지)의 기기 로컬 알림**이다(서버 변경 없음). 우아콘(10/28)에 맞춰 이 범위만 먼저 만든다. 다음 단계(주최 측 안내·현장 공지 등)는 따로 정한다.

- **대상·내용**: 로그인 계정의 내 시간표 세션(`GET /v1/me/session-favorites`, 앱이 이미 읽는 목록). 세션 시작 **10분 전**에 로컬 알림 하나. 제목 `10분 뒤 · {트랙 이름}{방이 있으면 ' · '+room}`, 본문 세션 제목. 트랙이 null(전폭 세션, 또는 `track`이 없는 옛 응답)이면 트랙 부분을 생략해 제목 `10분 뒤`. **같은 시각**(시작 시각이 같은 instant)에 찜이 겹치면 알림 하나로 묶고 본문에 줄을 바꿔 `같은 시간에 내 시간표 세션 n개`를 덧붙인다. 묶음의 제목·본문·식별자·열리는 활동은 세션 id가 가장 작은 것을 따른다(다른 활동의 세션이 같은 시각이면 그 하나로 묶여 한 활동만 열린다 — 드문 경우라 받아들인다).
- **식별자·예약 방식**: 알림 식별자 `session-<sessionId>`(묶음은 가장 작은 id). 찜 목록이 바뀔 때(하트 on/off, 로그인 뒤 목록 읽음), 설정 스위치를 바꿀 때, 알림 권한을 묻고 난 뒤, 앱이 포그라운드로 올 때 **전체 다시 예약**한다: 대기 중인 `session-*` 요청을 모두 지우고 10분 전 시각이 아직 미래인 것만 시작 시각 순으로 다시 넣는다. 단, 이번 실행에서 찜 목록을 아직 한 번도 읽지 못했으면(막 켜졌거나 읽기 실패) 지난 실행의 예약을 지우지 않고 그대로 둔다 — 현장에서 네트워크 없이 앱을 열어도 알림이 사라지지 않게. 스위치 끔은 그와 상관없이 바로 모두 지운다. 목록을 읽은 뒤(비어 있거나 서버에 세션 찜이 없는 404 포함) 처음 예약하면서 묵은 것이 정리된다. 지난 세션은 그렇게 빠지고, 로그아웃·탈퇴(목록이 빔)·스위치 끔은 모두 제거로 끝난다. iOS 로컬 알림 64개 한도: **가까운 60개**(묶음 기준)까지만 예약한다. 트리거는 절대 시각(시간대 변경에 흔들리지 않음), 기기 안에서 처리하며 서버에 아무것도 보내지 않는다.
- **권한**: 기존 알림 권한 흐름을 그대로 쓴다 — 권한을 앱 시작 때 묻지 않고, **첫 세션 하트**(첫 프로그램 하트와 같은 규칙)와 설정 `세션 이동 알림` 켬(새 모집 알림 켬과 같은 규칙)이 한 번 묻는다. 권한이 없으면 예약하지 않는다(거절했으면 설정 › 알림의 `기기 알림이 꺼져 있어요` 행이 설정 앱을 연다).
- **설정**: 설정 › `알림` 섹션에 `새 모집 알림` 아래 `세션 이동 알림` 스위치(설명 `내 시간표의 세션 10분 전에 이 기기에서 알려요`, 기본 켬, 기기 저장 iOS `UserDefaults` `dearby.sessionReminders.enabled`). 끄면 모두 제거. 섹션과 같이 로그인 + 동의 뒤에만 보인다(세션 찜 자체가 로그인 기능).
- **알림을 누르면**: 그 활동의 상세를 열고(발견 탭 push, `activity_view` source `push`) 그 세션의 블록 시간표 모달 시트를 **`내 시간표`** 보기로 바로 띄운다. 알림의 `userInfo`는 `kind: session_reminder`, `activityId`, `scheduleId`, `sessionId`. 활동이 지워졌거나(404) 블록에 시간표가 없어진 경우 상세만 열린다. 서버 푸시가 아니므로 `push_open`은 보내지 않는다.
- **사용 통계**: `session_reminder_scheduled`(`count` = 예약한 알림 수, 다시 예약해 실제로 넣을 때마다) 하나. [analytics-v1.md](analytics-v1.md).
- **개인정보**: 찜 목록은 이미 계정 기록이고 알림은 기기 안에서만 만들어지므로 처리방침 변경은 없다.
- Android(feat/session-reminder-android)는 같은 규칙(식별자·묶음·60개·설정 스위치·탭 라우팅)을 따른다.

## 활동 장소 — 2026-10-19 사용자 결정

사용자: "장소는 어드민에서 추가할 수 있으면 좋겠고 geocode, 주소 검색해서 좌표 가지고 올 수 있으면". 활동의 구조화된 장소를 둔다.

- 저장: ACTIVITY 슬롯 일정의 기존 열 `address_line1`(장소 이름), `address_line2`(도로명 주소), `lat`, `lng`(DB 제약: 넷 다 있거나 넷 다 없음). `activities.location`(수집 문구)은 그대로 두고, 장소가 지정되면 공개 응답의 `location`도 장소 이름으로 채운다.
- 공개 `Activity.place: {name, address, lat, lng}|null`. 앱·웹 활동 상세의 `장소` 절에 이름·주소를 보이고 누르면 지도 앱으로 연다(iOS `maps://?q=`, Android `geo:lat,lng?q=`, 웹 `https://maps.google.com/?q=`). 발견 활동 카드의 `장소` 줄은 이름만.
- 어드민: 활동 편집·추가 폼의 `장소` — 검색창에 장소명이나 주소를 적으면 `GET /v1/admin/geocode?q=`로 후보(이름·주소·좌표)를 받아 고른다. 고르면 이름·주소·좌표가 채워지고(이름은 고친 뒤 저장 가능), `지우기`로 비운다. `PATCH /v1/admin/activities/:id {place: {name, address, lat, lng} | null}`, `POST`도 `place?`. 운영자 지정(`schedules.curated`는 기간 전용이므로 `curated_fields`에 `place`)으로 재수집이 덮어쓰지 않는다.
- 지오코딩 서버: `GET /v1/admin/geocode?q=<문자열>` → 200 `{items: [{name, address, lat, lng, source}]}`(최대 10). 공급자는 `KAKAO_REST_API_KEY`가 있으면 Kakao Local(키워드 장소 검색 + 주소 검색 합침, `source: "kakao"`), 없으면 OpenStreetMap Nominatim(`source: "osm"`, 1초에 1회·`User-Agent: Dearby-admin` 규칙 준수). 키는 서버 `.env.prod`에만. 어드민 응답에 `source`를 보여 어느 공급자인지 알 수 있게 한다.

## 수집기 장소 — 2026-10-22 사용자 결정

사용자: "앞으로 장소도 가능하면 수집해 줘." 위 "활동 장소"의 구조화된 장소(`place`)를 수집기도 채운다. 어드민 지정 장소가 우선이다.

- 수집기 제출에 `place?: {name, address?: string|null, online: boolean}`과 근거 구절. `name`은 건물·홀·캠퍼스처럼 **한 곳**을 가리키는 이름(예: "코엑스 그랜드볼룸", "POSTECH C5"). 지역만 있거나("서울 송파구") 여러 곳이면("서울, 대전, 광주") `place`를 비우고 지금처럼 `location` 문구만 둔다. 온라인이면 `{online: true}`만(좌표 없음). 확실하지 않으면 쓰지 않는다.
- 서버(batch)가 지오코딩한다(어드민 장소 검색과 같은 공급자: Kakao Local, 키가 없으면 Nominatim). 주소가 있으면 주소 검색, 없으면 장소 키워드 검색. **확실할 때만** 저장한다: 주소 검색 결과가 정확히 하나이거나, 키워드 검색 첫 결과의 장소 이름이 제출한 이름을 포함(공백·대소문자 무시)하고 제출 주소가 있으면 그 시·군·구가 같을 때. 아니면 `place`를 저장하지 않고 `location` 문구만 둔다. 저장하는 장소 이름은 제출한 `name`, 주소·좌표는 공급자 값.
- 어드민이 장소를 지정했거나 지운 활동(`curated_fields`의 `place`)은 수집이 장소를 바꾸지 않는다. 재수집이 같은 장소를 다시 찾으면 그대로 둔다.
- 온라인: `place`는 null이고 `location`은 "온라인"(지금과 같음).
- 수집은 이미 있는 장소를 지우지 않는다(재수집에서 장소를 못 찾아도 그대로).
- 기존 활동 일괄 채우기(2026-10-22 사용자 "일정에 장소가 없는 곳들은 검색해서 채워 줘"): 장소가 없는 활동마다 공식 페이지·수집 문구로 한 곳을 확인하고 위 "확실할 때만" 규칙으로 지오코딩해 한 번 채운다. 운영자 지정이 아니므로 `curated_fields`에 `place`를 넣지 않는다. 지역만·여러 곳·온라인·확인 불가는 비워 두고 사유를 기록한다. 대상은 활동 기간 슬롯의 장소(공개 `place`)이며 세부 일정의 장소는 계약에 표시 규칙이 없어 채우지 않는다.

## 활동 종류(category) — 2026-10-19 사용자 결정

사용자: "활동 종류도 추가해줬으면 좋겠어. 부트캠프, 컨퍼런스, 워크샵, 스터디, 강연 등으로 구분하고 프로그램 하위에서 그룹화되도록". 활동에 **종류** 하나를 둔다.

- 값(API 코드 → 한글 이름): `BOOTCAMP` 부트캠프, `CONFERENCE` 컨퍼런스, `WORKSHOP` 워크샵, `STUDY` 스터디, `CLUB` 동아리(연합동아리 등), `LECTURE` 강연, `HACKATHON` 해커톤, `CONTEST` 공모전, `MEETUP` 밋업, `OTHER` 기타. 없으면 `null`(미분류). 저장은 `activities.category varchar(20) null`(V31, check 제약). 코드는 `participationType`처럼 대문자 영문이고 한글 이름은 클라이언트가 붙인다.
- **발견 `프로그램 | 이벤트` 전환(2026-10-26 사용자 결정)**: 발견 탭 맨 위 세그먼트(iOS 내비게이션 바 제목 자리 `Picker(.segmented)`, 내 활동 탭과 같은 관용구), 기본 `프로그램`, 고른 값은 기기에 남긴다(iOS `UserDefaults` 새 키 `dearby.discovery.kind`). **이벤트 = 종류 `CONFERENCE`·`MEETUP`·`LECTURE`·`WORKSHOP`**, 프로그램 = 그 밖의 종류와 미분류(해커톤은 프로그램). 앱은 종류로 거르지 않고 `GET /v1/discovery/programs?kind=program|event`를 보낸다(서버가 판정하고 요약 날짜도 그 kind 기준; `kind`를 모르는 서버는 무시하고 전부 준다). 이벤트 카드 머리의 보조 글자는 모집 마감 대신 행사 날짜(`latestActivityAt`이 오늘 이후면 `M월 d일 행사`, 지났으면 `최근 yyyy.M`; `nearestIsActivityStart` 분기는 서버가 항상 false라 쓰이지 않는다, 2026-10-26). 펼친 활동 조회에도 같은 `kind`를 보낸다, 펼친 활동 카드는 `hasTimetable` 블록이 있으면 `시간표` 배지(두 모드 공통). 맞춤 대기열 행·추천은 두 모드에서 그대로. 사용 통계 `discovery_mode`.
- **종류 필터(2026-10-26 사용자 결정)**: 사용자 "마찬가지로 저런 것들도 필터링 옵션에 추가해줘"(위 "종류 아이콘 라벨"에 이어). **조작은 2026-10-26부터 아래 "필터 시트"의 `종류` 섹션**(같은 날 오전에는 `활동 상태` 행 아래 같은 관용구의 `종류` 다중 선택 행 — iOS `Menu` + 체크 `Toggle`, 웹 `MultiSelect`, Android 같은 컴포넌트 — 이었고 요약은 `전체`·`부트캠프·동아리`·`부트캠프 외 2`였다). 항목은 **프로그램 쪽 종류만**, 계약 순서, 항목마다 종류 아이콘 + 이름: 부트캠프·스터디·동아리·해커톤·공모전·기타. **이벤트 모드에서는 종류 섹션을 숨긴다**(2026-10-26 사용자 결정: 운영 이벤트가 전부 컨퍼런스라 거를 의미가 없다). 이벤트 쪽에 저장된 종류 선택(iOS `dearby.discovery.categories.event`, 웹 `?category=`)은 읽지도 요청에 보내지도 않고 그대로 둔다. **이벤트 종류가 둘 이상 들어오면 다시 켠다**(이벤트 = 컨퍼런스·워크샵·강연·밋업, 서버 `kind=event` 집합). **아무것도 안 고르면 전체**(미분류 포함, `category`를 보내지 않음), 고른 종류는 칩으로 보인다. 고르면 `category`(코드 쉼표 목록, 계약 순서; 서버는 이미 지원, `kind`·`status`와 함께)를 프로그램 묶음과 펼친 활동(`GET /v1/activities?programId=…&category=`) 둘 다에 보낸다. 고른 종류만 보면 미분류 활동은 빠진다. **선택 기억**: kind마다 따로 — iOS `UserDefaults` `dearby.discovery.categories.program`·`dearby.discovery.categories.event`(쉼표 코드), kind를 바꾸면 그 kind의 선택으로 바뀐다. 웹은 URL `?category=`(그 kind의 코드만 읽고 나머지는 버림)이며 URL 하나라 `프로그램 | 이벤트`를 바꾸면 종류 선택이 지워진다(검색·`활동 상태` 변경은 유지). 즐겨찾기 프로그램 상세는 종류 필터를 쓰지 않는다(모든 종류). **빈 결과**: 검색어가 없고 종류를 골랐으면 `고른 조건의 프로그램이(이벤트가) 없어요` + `종류 필터 지우기`(전체로 되돌림), 검색어가 있으면 기존 검색 빈 안내. `활동 상태`만 기본값에서 바꾼 경우의 빈 제목도 `고른 조건의 …`으로 통일한다(2026-10-26, 옛 `고른 상태의 …`). 사용 통계 `categories`는 전체로 되돌리면 빈 문자열. 사용 통계 `discovery_category_filter`(analytics-v1.md).
- 공개 `Activity`(활동 조회·발견의 펼친 활동)와 `AdminActivity`에 `category: string|null`을 더한다. `GET /v1/activities`·`GET /v1/discovery/programs`에 `category?` 필터(코드 하나 또는 쉼표 목록)를 더한다.
- 수집: 추출 지시문이 `category`를 함께 뽑는다(본문의 성격으로 판단, 모르면 null). 어드민이 바꾼 값은 `curated_fields`의 `category`로 보호된다.
- 화면: 프로그램 하위에서 종류별로 묶는다. **어드민**은 펼친 프로그램 안에서 종류 소제목(부트캠프 (n) …, 미분류는 맨 뒤) 아래에 활동 행을 둔다. **앱·웹 발견**은 펼친 프로그램 안의 활동을 같은 순서의 종류 소제목으로 묶고(종류가 하나뿐이면 소제목 생략), 활동 행에는 종류를 다시 쓰지 않는다. 활동 상세에는 종류 칩 하나(2026-10-26부터 종류 아이콘을 붙인다, "종류 아이콘 라벨"). 종류 순서는 위 값 순서, 각 종류 안의 활동 순서는 "발견 프로그램 묶음"의 정렬 키(모집 마감 → 활동 시작 → 없음, 이름순)다.
- 프로그램 기본 종류: `programs.category`(V33)를 운영자가 지정하면 수집·운영자 생성에서 종류가 비었을 때 기본값으로 쓰고, 운영자가 지정하지 않은 활동에 일괄 적용할 수 있다(admin-v1.md "프로그램 기본 종류"). `ProgramSummary.category`로 접힌 프로그램 카드 이름 위 종류 줄에 보인다("종류 아이콘 라벨" 2026-10-26).
- 변경 이력: 2026-10-19 최초 9종 [`11ae3025`](https://github.com/fixabley/dearby/blob/11ae3025/shared/contracts/native-v1.md#활동-종류category--2026-10-19-사용자-결정) → 같은 날 `CLUB` 동아리 추가(사용자 "연합동아리 같은 동아리 활동도 있어"), 정렬 규칙 두 번 보완(위 정렬 키).

## 발견 프로그램 묶음 — 2026-10-18 사용자 결정 (2026-10-19 보완)

발견 탭은 활동 카드 목록 대신 **프로그램 단위**로 보여 주고, 누르면 아코디언처럼 펼쳐져 지금 모집 중인 활동이 보인다. iOS·Android·웹 공통. 활동 상세와 `내 활동`은 바뀌지 않는다.

### 규칙

- **열린 활동 판정은 서버 한 곳에서만 한다**(2026-10-19). 게시 활동이고 (a) 모집 기간이 시각 일정이면 `start_at ≤ now`(없으면 열림) 이고 `end_at > now`(없으면 열림), (b) 모집 기간이 날짜 일정이면 서울 날짜로 `start_date ≤ today < end_date_exclusive`. **모집 일정(모집 시작 또는 마감)이 없는 활동은 종류와 상관없이 마감으로 본다**(2026-10-26 사용자 결정 "모두 마감 취급": 모집 기간 없이 행사 날짜만 있는 컨퍼런스 등은 `마감 숨기기`를 켠 발견에서 빠진다; 이전 규칙 (c) "모집 기간이 없으면 활동 시작이 `now`보다 뒤면 열림"은 폐지). 열린 활동 = 아래 "활동 상태"의 `RECRUITING`, 모집 예정 = `RECRUITING_UPCOMING`. 같은 규칙이 `GET /v1/activities?open=true`에도 적용된다. 앱·웹은 열린 활동을 스스로 걸러내지 않고, 활동 상세의 `모집 중/마감/예정` 라벨도 날짜 일정을 같은 규칙으로 계산한다(시각이 없다고 `확인 필요`로 두지 않음).
- **활동 기간이 끝난 활동은 열리지 않는다**(2026-10-25, 운영 NDC 2026·CODEGATE 2026 CTF 보고 뒤 사용자 "해주세요"). 위 (a)·(b) 중 어느 것으로 열려도 활동 기간(ACTIVITY 슬롯)의 끝(시각 일정은 `end_at`, 날짜 일정은 `end_date_exclusive` 서울 0시, 끝이 없으면 활동 시작)이 `now` 이하이면 열린 활동도 모집 예정도 아니다. 활동 기간이 없으면 지금처럼 모집 기간만 본다. 모집 기간에 시작만 있고 끝이 없어 지난 행사가 계속 열려 있던 문제를 막는다. 발견 묶음·`open=true`·추천·맞춤 대기열(만들 때와 읽을 때 다시 거르기)이 모두 이 판정을 쓴다.
- **정렬 키**(활동 공통): 모집 마감(APPLICATION 슬롯의 끝 시각·날짜) → 모집 마감을 알 수 없으면(모집 기간이 없거나 끝이 비어 있으면) 활동 시작(ACTIVITY 슬롯의 시작 시각·날짜) → 둘 다 없으면 맨 뒤, 같으면 이름순. SQL은 `coalesce(app.end_at, app.end_date_exclusive 서울 0시, 활동 시작)`. 프로그램 순서(`nearestRecruitmentEndAt`), 펼친 활동, 어드민 묶음 보기가 모두 이 키를 쓴다.
- **마감 숨기기** 토글(기본 켬, 메모리; 2026-10-26 iOS·웹·Android 화면에서는 아래 "활동 상태" 다중 선택으로 대체, 서버 `openOnly`는 옛 앱 호환으로 남음). 2026-10-19 밤 사용자 지적 "마감 숨기기인데 모집 예정은 왜 숨겨?"로 뜻을 바로잡음: 켜면 **마감된 활동만 숨긴다**. 즉 `openOnly=true`는 **모집 중이거나 모집 예정**(모집 기간이 아직 시작되지 않은; 2026-10-26부터 모집 기간이 없으면 모집 예정도 아니다) 활동이 있는 프로그램을 돌려주고, 마감된 활동뿐인 프로그램만 뺀다. `ProgramSummary`에 `upcomingActivityCount`(모집 예정 수)와 `nearestRecruitmentStartAt`(모집 예정 활동의 가장 이른 모집 시작)을 더하고, 순서는 모집 중 프로그램(정렬 키) → 모집 예정 프로그램(가장 이른 시작) → (끄면) 나머지 최근 활동 순. 머리글은 모집 중이 있으면 `모집 중 n개 · 마감`, 없고 모집 예정이 있으면 `모집 예정 n개 · M월 d일 시작`, 둘 다 없으면 `최근 활동 yyyy.M`. 펼치면 `모집 중`(open=true)과 `모집 예정` 묶음은 토글과 무관하게 항상 보이고, `지난 활동`(최근 3년)은 토글을 껐을 때만 보인다. 자동 펼침(열린 활동 1개)은 그대로. 끄면 끄면 **최근 3년 안에 활동이 있었던 프로그램**(게시 활동의 활동 시작·모집 마감·생성 시각 중 하나가 `now − 3년` 이후)을 모두(2026-10-19 저녁 사용자 결정으로 2년 → 3년). 순서는 열린 프로그램 먼저(정렬 키), 그 뒤는 가장 최근 활동 시작이 늦은 순.
- **머리글**: 프로그램 이름, 조직 이름, 종류 칩(프로그램 기본 종류가 있을 때), `모집 중 n개 · M월 d일 (요일) 마감`(열린 활동이 있을 때) 또는 `최근 활동 yyyy.M`(`latestActivityAt`). 접근성에서 `펼침`/`접힘` 상태를 가진 버튼으로 읽힌다.
- **펼침**: 머리글을 누르면 `GET /v1/activities?programId=<id>&open=true`로 그 프로그램의 모집 중 활동을 불러와 기존 활동 카드로 보인다. 종류별 소제목으로 묶고(아래 "활동 종류", 종류가 하나뿐이면 소제목 생략) 각 묶음 안은 정렬 키 순. 그 아래 **다른 활동**(같은 `GET /v1/activities?programId=`, 20개, 종류 소제목 없음)을 `모집 예정`(2026-10-26부터 `status`가 `RECRUITING_UPCOMING`인 것, 가까운 순; 그 전에는 모집 시작 또는 활동 시작이 아직 오지 않은 것)과 `지난 활동`(최근 3년, 활동 시작 최근 순) 두 묶음으로 보인다. `모집 예정`은 토글과 무관하게 항상, `지난 활동`은 토글을 **껐을 때만** 보인다(2026-10-19 밤 정정; 그 전에는 켜진 상태에서 모집 중이 없을 때만 두 묶음을 보였다). 모집 중·모집 예정·지난 활동이 모두 비면 "지금 모집 중인 활동이 없어요"만 남긴다. 빈 묶음은 생략한다. 기본은 접힘이며, 토글이 켜진 상태에서 열린 활동이 1개인 프로그램과 검색어가 활동 이름에 맞은 프로그램은 펼친 채로 시작한다. 펼침 상태는 앱 실행 중 메모리에만 두고, 동작 줄이기에서는 애니메이션 없이 바뀐다.
- **검색**: 검색어는 프로그램 이름·영문 이름, 조직 이름, 열린 활동 이름에 부분 일치(대소문자 무시). 활동 이름으로만 맞은 프로그램도 목록에 나온다. 태그 이름 검색과 태그 필터는 "태그" 절(설계 완료, 구현 예정).
- **유형(참가등록·선발)**: 세 플랫폼의 발견 화면에는 유형 칩이 없다. API의 `participationType` 필터는 칩을 다시 넣을 때 쓰도록 둔다. 칩이 생기면 펼친 활동과 열린 활동 수에 함께 적용하고 맞는 활동이 없는 프로그램은 숨긴다.
- **프로그램·이벤트 전환**(2026-10-26 사용자 결정: 발견 화면에 `프로그램 | 이벤트` 전환). `kind?` query(`program`\|`event`, 대소문자 무시, 모르는 값 400, 생략하면 전부 — 옛 앱 호환). `event`는 종류 `CONFERENCE`·`WORKSHOP`·`MEETUP`·`LECTURE`(서버의 단기 행사 종류, "활동 이력에서 단기 행사 제외"와 같은 목록), `program`은 그 밖의 모든 종류와 **미분류(`category` null)**. `category` 목록으로 거르면 미분류가 어느 쪽에도 안 나오므로 전환에는 `kind`를 쓴다. 활동 행 자체를 거르므로 프로그램 묶음은 그 kind의 활동(모집 중·모집 예정, `openOnly=false`면 최근 3년)이 하나라도 있는 프로그램만 나오고, `openActivityCount`·`upcomingActivityCount`·`nearestRecruitmentEndAt`·`nearestIsActivityStart`(2026-10-26부터 항상 false)·`nearestRecruitmentStartAt`·`latestActivityAt`·`matchedActivityIds`(검색 포함)가 모두 그 kind의 활동 기준이다. 펼친 활동(`GET /v1/activities?programId=…`)에도 같은 `kind`를 넘긴다. 묶음 머리의 `category`(프로그램 기본 종류)와 태그는 kind와 무관하다.

### API

| 메서드·경로 | 인증 | query | 응답 |
| --- | --- | --- | --- |
| GET /v1/discovery/programs | 없음 | `q?`(≤100자), `participationType?`(REGISTRATION\|SELECTION), `category?`(종류 코드, 쉼표 목록), `kind?`(program\|event, 위 "프로그램·이벤트 전환"), 대상자 필터(아래 "대상자 조건"), `openOnly?`(기본 `true`), `status?`(아래 "활동 상태", 쉼표 목록; 있으면 `openOnly` 대신), `cursor?`(정수 오프셋, 기본 0), `size?`(1–100, 기본 20) | 200 `{items: ProgramSummary[], nextCursor: number\|null}` |
| GET /v1/activities | 없음 | 기존 query에 `open?`(기본 false: 전부, `true`: 위 열린 활동만), `category?`, `kind?`, `status?`(아래 "활동 상태", 쉼표 목록) | 200 `{items: Activity[], nextCursor}` |

- 경로가 `/v1/programs`가 아닌 이유: `/v1/programs`는 이력 선택 창의 활동 정보 검색(로그인)이 이미 쓴다. `nextCursor`는 정렬 결과의 정수 오프셋이다(활동 조회의 id 커서와 다르다).
- ProgramSummary: `{id, name, nameEn: string|null, organization: {id, name}, category: string|null, openActivityCount, nearestRecruitmentEndAt: ISO-8601(UTC)|null, latestActivityAt: ISO-8601|null, matchedActivityIds: number[]}`. `openActivityCount`는 열린 활동 수(`participationType`·`category`·대상자 2차 판정까지 통과한 것)이고 `openOnly=false`에서는 0일 수 있다. `nearestRecruitmentEndAt`은 열린 활동의 **모집 마감** 최솟값(활동 시작으로 대체하지 않는다, 2026-10-26; 모집 마감이 없는 열린 활동뿐이면 null). 프로그램 순서는 그대로 정렬 키(모집 마감 → 활동 시작) 최솟값. `nearestIsActivityStart: boolean`은 **폐지 예정, 항상 false**(옛 앱 호환으로 남김; 2026-10-26 오전에 "정렬 키가 활동 시작으로 대체됐는지"로 넣었으나 같은 날 모집 일정 없는 활동을 마감으로 바꾸며 의미가 없어졌다). `statusCounts: {[status]: number}`(2026-10-26)는 조회 범위(필터·`status`·`openOnly` 반영) 안 게시 활동의 상태별 수(0은 빠진다), `latestActivityAt`은 조회 범위 안 게시 활동의 가장 최근 활동 시작. `matchedActivityIds`는 `q`가 활동 이름에 맞은 열린 활동 id(없으면 빈 배열).
- 펼쳤는데 모집 중 활동이 없으면 "지금 모집 중인 활동이 없어요" 아래에 그 프로그램의 다른 활동(`GET /v1/activities?programId=`, 20개)을 `모집 예정`(2026-10-26부터 `status`가 `RECRUITING_UPCOMING`, 가까운 순)과 `지난 활동`(최근 3년, 활동 시작 최근 순) 두 묶음으로 보인다. 둘 다 없으면 안내만(2026-10-19 PR #272, SK AIGEN 1기처럼 모집이 11/1에 시작하는 활동이 어느 묶음에도 들지 않던 문제).
- `Cache-Control: no-store`·오류 형식은 활동 조회와 같다.
- 변경 이력: 2026-10-18 최초(서버는 시각 모집 기간만 보고 열림 판정, 펼친 활동은 클라이언트가 걸러냄, 열린 활동 1개 이상인 프로그램만) [`b14acca1`](https://github.com/fixabley/dearby/blob/b14acca1/shared/contracts/native-v1.md#발견-프로그램-묶음--2026-10-18-사용자-결정) · 2026-10-19 정렬 키에 활동 시작 대체(사용자 "모집 기간이 미정인 활동들도 활동 시작일을 모집 종료일로 취급") [`6e2834e0`](https://github.com/fixabley/dearby/blob/6e2834e0/shared/contracts/native-v1.md) · 2026-10-19 날짜 모집 기간을 서버가 "없음"으로, 앱이 `확인 필요`로 다뤄 펼친 활동이 비던 버그(운영 활동 126)를 계기로 판정 일원화·`open=true`·`openOnly`·`지난 활동` [`352f20b7`](https://github.com/fixabley/dearby/blob/352f20b7/shared/contracts/native-v1.md#발견-프로그램-묶음--2026-10-18-사용자-결정) · 2026-10-26 `kind`(프로그램·이벤트 전환, 미분류는 프로그램 쪽) · 2026-10-26 모집 일정이 없는 활동은 마감(이전: 활동 시작 전이면 열림), `nearestRecruitmentEndAt`은 모집 마감만·`nearestIsActivityStart` 폐지 예정, 활동 상태·`status` 필터.

## 활동 상태 — 2026-10-26 사용자 결정

서버가 활동마다 상태를 계산해 내려 준다(서울 시각 기준). 앱·웹은 상태를 스스로 계산하지 않는다. 열린 활동 판정(위 "발견 프로그램 묶음")은 이 상태로 표현된다: 열린 활동 = `RECRUITING`, 모집 예정 = `RECRUITING_UPCOMING`. 추천·맞춤 대기열·즐겨찾기 새 모집 알림의 후보는 그대로 열린 활동이다.

- **값과 판정 순서**(위에서 먼저 맞는 것):
  1. `ENDED` 종료: 활동 기간(ACTIVITY 슬롯)의 끝(시각 일정 `end_at`, 날짜 일정 `end_date_exclusive` 서울 0시, 끝이 없으면 활동 시작)이 `now` 이하.
  2. `RECRUITING` 모집 중: 모집 기간이 지금을 포함(위 열린 활동 (a)·(b)). 활동 기간과 겹쳐도 모집 중이 앞선다.
  3. `RECRUITING_UPCOMING` 모집 예정: 모집 시작 전(시각 `start_at > now`, 날짜 `start_date > 서울 오늘`).
  4. `IN_PROGRESS` 진행 중: 활동 시작 ≤ `now` (끝은 1에서 이미 걸렀다).
  5. 모집 기간이 있으나 끝났으면: 활동 기간이 있으면 `RECRUITMENT_CLOSED` 모집 마감(활동 시작 전), **활동 기간이 없으면 `ENDED`**.
  6. `NO_RECRUITMENT` 모집 정보 없음: 모집 일정이 없고 활동이 아직 시작 전(활동 기간도 없으면 이것).
- **한국어 라벨**: 모집 예정·모집 중·모집 마감·모집 정보 없음·진행 중·종료.
- **응답**: `Activity.status: string`(위 값, 목록·상세·펼친 활동·즐겨찾기·추천·맞춤 대기열 모두). `ProgramSummary.statusCounts`(위 "발견 프로그램 묶음" API).
- **필터**: `GET /v1/discovery/programs`와 `GET /v1/activities`에 `status?`(값 쉼표 목록, 대소문자 무시, 모르는 값 400). 발견 묶음에서 `status`가 있으면 `openOnly` 대신 그 상태의 활동으로 묶고 세며(`openActivityCount`·`upcomingActivityCount`·`statusCounts`·`hasTimetable`·`matchedActivityIds`가 그 활동 기준), `RECRUITING`·`RECRUITING_UPCOMING`이 아닌 활동은 최근 3년 창(위 `openOnly=false`와 같다) 안만. 대상자 필터와 함께 써도 같은 상태 기준. 생략하면 기존 `openOnly` 동작(옛 앱 호환). `status=RECRUITING_UPCOMING,RECRUITING`은 `openOnly=true`와 같은 결과다. 서버 파라미터는 enum 단위이고 앱이 아래 묶음을 펼쳐 보낸다.
- **앱·웹 UI**(iOS·웹 2026-10-26 `feat/status-filter-ios-web`, Android PR #406): `마감 숨기기` 토글 대신 **다중 선택, 세 묶음**: `지원 가능`(`RECRUITING_UPCOMING`+`RECRUITING`, 기본 선택), `진행·예정`(`IN_PROGRESS`+`RECRUITMENT_CLOSED`+`NO_RECRUITMENT`), `지난 활동`(`ENDED`). **묶음 재정의(2026-10-26 사용자 결정 "미정이더라도 모집 중엔 넣지 않아도 활동 일정에 따라 분류")**: 처음에는 `진행 중`(`IN_PROGRESS`)·`지난 활동`(`RECRUITMENT_CLOSED`+`NO_RECRUITMENT`+`ENDED`)이었으나, 판정 순서상 `ENDED`가 먼저이므로 `RECRUITMENT_CLOSED`·`NO_RECRUITMENT`는 **활동이 끝나지 않은 활동**(모집은 닫혔거나 미정이지만 활동은 앞에 있음)이라 `지난 활동`에 넣으면 아직 열리지도 않은 활동이 "지난" 것으로 보였다. 그래서 모집 여부가 아니라 **활동 일정**으로 묶는다: 지원할 수 있음 / 활동이 진행 중이거나 앞에 있음 / 활동이 끝남. 묶음 id는 `available`·`in_progress`·`past`로 **그대로**(저장값·URL·사용 통계 공통, 기억된 선택과 옛 링크가 그대로 통함; `in_progress`는 이제 `진행·예정`의 id). 배지 문구(`모집 마감`·`모집 일정 미정`·`진행 중`·`종료`)는 바뀌지 않는다. 펼친 활동의 `진행·예정` 묶음은 진행 중인 것(활동 시작이 지난 것) 먼저, 그 뒤 활동 시작이 가까운 순, 날짜 없는 것 마지막; `지난 활동`의 최근 3년 창은 `ENDED`에만 남고 `진행·예정`에는 창이 없다(날짜 없는 `NO_RECRUITMENT`도 남는다). 하나도 안 고르면 `지원 가능`으로 되돌린다. 선택 기억: iOS UserDefaults `dearby.discovery.statusGroups`(쉼표 id), 웹 URL `?status=`(기본이면 생략, 검색·`kind` 전환이 유지). **조작은 2026-10-26부터 아래 "필터 시트"**(`필터` 버튼 하나 → 시트, 적용된 묶음은 칩). 그 전(같은 날 오전)에는 iOS 발견 목록 첫 행의 `활동 상태` 시스템 `Menu`(체크 `Toggle`)와 웹 같은 자리의 공개 버튼 + 체크박스 묶음이었고 버튼에 요약(`지원 가능·진행 중`, 셋 다면 `전체`)을 보였다. 프로그램 묶음과 펼친 활동(`GET /v1/activities?programId=&kind=&status=&size=100`, 한 번 요청)에 같은 `status`를 넘기고, 펼친 활동은 서버 `status`로 `모집 중`(종류별 소제목)·`모집 예정`·`진행·예정`·`지난 활동` 묶음으로 보인다. **`GET /v1/activities`의 `status`에는 기간 제한이 없으므로 앱·웹은 `지난 활동`을 활동 시작이 최근 3년 안인 것으로 거르고, 날짜 없는 활동은 남긴다.** 즐겨찾기 프로그램 상세는 세 묶음 모두. **배지**: 모집 예정·모집 중·모집 마감·진행 중·종료를 보이고(진행 중은 시스템 파랑 틴트; 모집 예정에 모집 시작이 있으면 옆에 `시작 D-n` — 위 "모집 예정 시작 D-day"), `NO_RECRUITMENT`는 배지 없이 날짜 자리(카드 `모집` 줄, 상세 모집 상태·모집 기간)에 `모집 일정 미정`. 신청 CTA(신청 링크·"신청하셨나요?")는 모집 중·모집 예정일 때만이고, 그 밖에는 `공식 안내 보기`. 상태가 없는 옛 서버 응답은 모집 기간으로 모집 중·모집 예정·모집 마감만 가린다. **접힌 프로그램 카드**: 배지 수는 `statusCounts` 합(없으면 옛 `openActivityCount + upcomingActivityCount`), 보조 줄은 기존 순서(`M월 d일 마감` → `M월 d일 시작`)에 모집 중·모집 예정이 없고 진행 중이 있으면 `진행 중 n개`, 그 밖에는 `최근 yyyy.M`. 상태별 수 요약 줄("모집 중 2 · 모집 예정 1")은 넣지 않는다(한 줄 보조 글자에 날짜가 더 쓸모 있고, iOS 보이스오버는 배지 라벨로 상태별 수를 읽는다).
- 변경 이력: 2026-10-26 최초(같은 날 "모집 일정이 없으면 마감" 결정과 함께) · 2026-10-26 묶음 재정의(`진행 중`→`진행·예정`에 `RECRUITMENT_CLOSED`·`NO_RECRUITMENT`, `지난 활동`은 `ENDED`만; id 유지).

## 필터 시트 — 2026-10-26 사용자 결정

사용자: 발견 상단에는 `프로그램 | 이벤트` 전환과 검색만 남기고, 상태·종류 조작을 **`필터` 버튼 하나 → 시트**로 옮긴다. iOS·Android·웹. 위 "활동 상태"·"종류 필터"의 데이터·요청·선택 기억·사용 통계 규칙은 그대로이고 이 절은 조작 UI만 바꾼다.

- **`필터` 버튼**: 발견 목록 첫 행(옛 `활동 상태`·`종류` 두 행 자리)의 왼쪽. 아이콘 `line.3.horizontal.decrease.circle`/`FilterList`(웹 같은 모양의 인라인 SVG `filter`), 라벨 `필터`, 적용된 필터가 있으면 수를 붙인다(`필터 2`; 수 = 아래 칩 수). iOS `.bordered` 캡슐 `Button`(검색은 내비게이션 바 서랍이라 검색창 옆에 둘 자리가 없어 목록 첫 행), Android `FilterChip`/`AssistChip` 계열 또는 `OutlinedButton`, 웹 `Button` tinted. 로딩 중에도 행은 남는다(적용하면 목록이 로딩으로 바뀌는데 행이 사라지면 시트 아래가 튄다).
- **적용된 필터 칩**: 버튼 오른쪽 같은 줄에 가로 스크롤. 순서는 고른 `활동 상태` 묶음(계약 순서; **기본 `지원 가능`만이면 묶음 칩 없음**, 둘 이상 골랐으면 `지원 가능`도 칩), 그 뒤 고른 종류(계약 순서, 종류 아이콘 + 이름). 칩마다 × — 누르면 그 하나만 해제해 바로 적용(묶음을 모두 해제하면 `지원 가능`으로 되돌림). 접근성 이름 `<이름> 필터 지우기`. 기본 상태는 칩이 없다.
- **시트**: iOS `.sheet` + `.presentationDetents([.medium, .large])`, inline 제목 `필터`, 왼쪽 `닫기`; Android Material 3 `ModalBottomSheet`; 웹 기존 `shared/ui` 바텀 시트(`Sheet`). 안은 섹션별 다중 선택: `활동 상태`(세 묶음) 그리고 **`kind == program`일 때만** `종류`(프로그램 종류 여섯; 위 "종류 필터" 이벤트 숨김). 행은 체크 표시 행(iOS `checkmark.circle.fill`/`circle`, 위 "토글 라벨은 바로 옆"의 체크박스 규칙; Android `Checkbox` 행; 웹 네이티브 체크박스)이고 행 전체가 터치 대상. 시트 안의 선택은 **초안**이라 목록에 바로 반영되지 않는다.
- **하단 버튼 두 개**(같은 줄, iOS `.bordered` `초기화` + `.borderedProminent` `적용`): `적용`은 초안을 한 번에 적용하고(상태·종류가 한 요청으로 다시 읽힘, 둘 중 바뀐 것만 사용 통계) 시트를 닫는다. `초기화`는 초안을 기본(`지원 가능`만, 종류 전체)으로 되돌리기만 하고 적용하지 않는다(사용자가 `적용`을 눌러야 함; 판단 — 실수로 초기화를 눌러도 목록이 바뀌지 않게). 시트를 닫거나 끌어 내리면 초안을 버리고 목록은 그대로다. 아무 묶음도 안 고르고 `적용`하면 `지원 가능`.
- **빈 결과의 버튼**(`지난 활동도 보기`·`종류 필터 지우기`)은 시트 없이 바로 적용한다(위 "종류 필터" 빈 결과 규칙 그대로).
- **사용 통계**: `discovery_status_filter`·`discovery_category_filter`는 시트 `적용`과 칩 ×, 빈 결과 버튼에서 **실제로 바뀐 것만** 보낸다([analytics-v1.md](analytics-v1.md)). 시트 안에서 체크만 바꾸는 동안은 보내지 않는다.
- 접근성 식별자(iOS): 버튼 `discovery-filter`, 시트 행 `discovery-filter-status-<id>`·`discovery-filter-category-<code 소문자>`, 버튼 `discovery-filter-reset`·`discovery-filter-apply`. 옛 `discovery-status-filter`·`discovery-category-filter`는 없다.
- 변경 이력: 2026-10-26 최초(같은 날 오전의 두 행 메뉴를 대체).

## 구글 로그인 — 네이티브 SDK (2026-10-18 결정, 2026-10-19 네이티브로 전환)

애플 로그인·패스키와 함께 쓰는 세 번째 로그인 수단. 앱이 SDK로 받은 구글 ID 토큰을 서버가 검증한다. 같은 구글 계정(`sub`)은 같은 Dearby 계정이 되며 계정은 이메일로 합치지 않는다. 서버에 허용 client id(`GOOGLE_CLIENT_IDS`)가 없으면 `/auth/google/*`는 503이고 앱은 버튼을 숨긴다.

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| POST /v1/auth/google/nonce | 없음 | — | 200 `{nonce}`(애플 `/auth/apple/nonce`와 같은 1회용·10분 만료 규칙). 허용 client id가 없으면 503 |
| POST /v1/auth/google | 없음 | `{idToken, nonce}` | 200 TokenPair(이미 연결된 구글 계정) 또는 202 `{linkToken, email, displayName}`(처음 보는 계정). 검증 실패 401 |
| POST /v1/auth/google/register | 없음 | `{linkToken}` | 201 TokenPair. 새 Dearby 계정(USER)을 만들고 구글 계정을 연결한다 |
| POST /v1/me/identities/google | Bearer | `{linkToken}` | 204. 로그인한 계정에 구글 계정을 연결한다. 이미 다른 구글 계정이 연결돼 있으면 400 |

- **ID 토큰 검증**: JWKS `https://www.googleapis.com/oauth2/v3/certs` 서명, `iss`가 `https://accounts.google.com` 또는 `accounts.google.com`, 만료, `nonce`가 발급값과 같고 1회만, `aud`는 **허용 목록** `GOOGLE_CLIENT_IDS`(쉼표 구분) 중 하나. Android(Credential Manager, `serverClientId` = 웹 클라이언트 id)는 `aud`가 웹 클라이언트 id이고 iOS SDK는 iOS 클라이언트 id이므로 둘을 모두 넣는다. `email_verified`가 false면 email은 저장하지 않고 `name`을 `display_name`에 남긴다. refresh token은 받지 않으므로 계정 삭제 때 폐기할 구글 토큰이 없다(애플과 다른 점).
- **처음 보는 구글 계정(202)**: 애플과 같다. 앱이 이 기기의 Dearby 패스키를 조용히 묻고(있으면 로그인 후 `/me/identities/google`로 연결), 없으면 `register`로 새 계정, 사용자가 프롬프트를 닫았을 때만 선택 화면. `linkToken`은 10분, 한 번만.
- **DB**: `user_identities.provider`에 `GOOGLE`(V22). nonce 상태는 애플의 상태 저장소(`apple_sign_in_states`, 10분 TTL)를 같이 쓴다.
- **iOS**: `GoogleSignIn-iOS`(Swift Package, `https://github.com/google/GoogleSignIn-iOS`, 10.x — `nonce` 매개변수는 9.0부터)를 `generate_project.rb`가 원격 패키지로 추가한다. 빌드 설정 `GOOGLE_IOS_CLIENT_ID`(비어 있으면 버튼 숨김)와 역순 표기 `GOOGLE_IOS_URL_SCHEME`(`com.googleusercontent.apps.<id>`)을 `Info.plist`의 `GIDClientID`·`CFBundleURLSchemes`로 넣는다(`dearby` 스킴 유지. 이때 `CFBundleURLTypes`가 아예 없어 `dearby://cards/…`가 앱을 열 수 없던 것을 함께 등록했다). 흐름: `/auth/google/nonce` → `GIDSignIn.sharedInstance.signIn(withPresenting:hint:additionalScopes:nonce:)` → `idToken`을 `/auth/google`로. 결과(200/202)는 애플 `ASAuthorizationController` 처리와 같은 자리에서 다룬다.
- **Android**: `androidx.credentials` + `com.google.android.libraries.identity.googleid:googleid`로 `GetGoogleIdOption(serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID, nonce, filterByAuthorizedAccounts=false)` → `GoogleIdTokenCredential.idToken` → `/auth/google`. 빌드 설정 `GOOGLE_SERVER_CLIENT_ID`(Gradle 속성 또는 환경 변수, 비어 있으면 버튼 숨김). `dearby://auth/google` 인텐트 필터는 없다(애플의 `dearby://auth/apple`만 남는다).
- **앱 화면**: 로그인 시트와 시작 화면에 애플 버튼 옆(iOS는 아래)에 `Google로 계속하기`. 구글 브랜드 가이드의 흰 바탕·회색 테두리·"G" 로고 버튼을 따르되 로고 자산은 공식 파일을 받아 `shared/assets/prototype`에 둔 뒤 적용한다(자산이 없는 동안은 글자 버튼). `GOOGLE_SIGN_IN_ENABLED` 같은 별도 플래그는 두지 않고 빌드 값이 비거나 서버가 503이면 숨긴다(애플 버튼의 `appleSignInEnabled` 방식과 같게).
- **운영 준비(사용자)**: Google Cloud 콘솔 OAuth 클라이언트 세 개 — ① 웹 애플리케이션(리디렉션 URI 없음) → 서버 `GOOGLE_CLIENT_IDS`와 Android `GOOGLE_SERVER_CLIENT_ID`, ② iOS(번들 `io.wid.dearby`) → `GOOGLE_IOS_CLIENT_ID`와 서버 허용 목록, ③ Android(패키지 `io.wid.dearby`, 디버그 keystore SHA-1; 출시 서명 SHA-1은 출시 서명을 정한 뒤). 값은 채팅에 적지 않고 서버 `.env.prod`와 로컬 `~/.dearby-deploy/mobile.env`(`GOOGLE_IOS_CLIENT_ID`, `GOOGLE_IOS_URL_SCHEME`, `GOOGLE_SERVER_CLIENT_ID`)에만 둔다. 기기 설치 빌드는 그 파일을 읽어 넘기고 CI는 값 없이 빌드한다(버튼 숨김).
- 변경 이력: 2026-10-18 웹 방식(서버 코드 흐름 `web/start`·`callback`·`web/exchange`, `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`GOOGLE_CALLBACK_URL`, iOS `ASWebAuthenticationSession`·Android Custom Tab, 운영에 설정된 적 없음) [`fe52a2f9`](https://github.com/fixabley/dearby/blob/fe52a2f9/shared/contracts/native-v1.md#구글-로그인--2026-10-18-사용자-결정) → 2026-10-19 구글 개발자 계정 인증 완료로 네이티브 SDK로 교체(웹 방식 엔드포인트·시크릿 삭제) [`416d38ac`](https://github.com/fixabley/dearby/blob/416d38ac/shared/contracts/native-v1.md#구글-로그인--2026-10-19-네이티브-sdk로-전환).

## 웹 로그인 — 2026-10-26 사용자 결정

사용자: "웹도 로그인이랑 관련 기능 추가해줘." 사용자 웹(`https://dearby.wid.io.kr`, Next.js)에 앱과 같은 세 로그인 수단과 로그인 기능(동의·프로필·내 활동·즐겨찾기·세션 찜·알림·받은 명함)을 만든다. 서버 API는 앱이 쓰는 것을 그대로 쓰고, 웹은 지금처럼 same-origin `/api/*` 프록시(`apps/web/src/lib/proxy.ts`, 허용 경로 명시)만 거친다. 이 절은 서버 준비(2026-10-26 PR)와 웹이 따를 규칙이다. 웹 화면은 별도 PR.

### 로그인 수단

- **패스키(WebAuthn)**: 기존 `/v1/auth/passkeys/*`를 그대로 쓴다. RP ID `wid.io.kr`은 웹 origin `dearby.wid.io.kr`의 등록 가능한 상위 도메인이므로 앱에서 만든 패스키를 웹에서, 웹에서 더한 패스키를 앱에서 쓸 수 있다. 서버는 `clientDataJSON.origin`을 `WEBAUTHN_ALLOWED_ORIGINS`와 대조하므로 그 목록에 `https://dearby.wid.io.kr`이 있어야 한다(`.env.prod.example`에는 있고, 운영 `.env.prod` 값은 배포 때 확인한다; 프록시를 거쳐도 origin은 브라우저 값이다). `options`는 WebAuthn 표준 JSON(base64url)이라 `PublicKeyCredential.parseCreationOptionsFromJSON`·`parseRequestOptionsFromJSON`에 그대로 넣고, 결과는 `credential.toJSON()`을 `credential`로 보낸다(구형 브라우저는 같은 형식의 polyfill). 가입은 없고(2026-10-25) "패스키로 로그인"과 설정의 패스키 추가(`registration/options`·`registration`에 Bearer)만 있다. `/v1/auth/**` 중 Bearer를 읽는 경로는 `registration/options` 하나이므로 프록시는 거기에만 토큰을 붙이면 된다.
- **애플**: Apple JS(`appleid.auth.init({clientId: <Services ID>, scope: "name email", redirectURI, usePopup: true, nonce})`)로 받은 `authorization.id_token`을 `POST /v1/auth/apple`의 `identityToken`으로 보낸다. nonce는 `POST /v1/auth/apple/nonce`의 값을 해시하지 않고 그대로 넣고 `challengeId`를 함께 보낸다. Services ID는 `APPLE_CLIENT_IDS`에 이미 있는 `io.wid.dearby.web`(Android 웹 방식과 같은 것). 처음 인가 때만 오는 `user.name`은 `displayName`으로 보낸다(popup은 서버 콜백을 거치지 않아 서버가 읽을 수 없다). `authorization.code`를 `authorizationCode`로 보내면 서버가 애플 refresh token으로 바꿔 두는데(계정 삭제 때 폐기용), Services ID로 받은 code의 교환에는 서버가 `APPLE_CALLBACK_URL`을 `redirect_uri`로 넣으므로 Apple JS의 `redirectURI`도 그 값(`https://api.dearby.wid.io.kr/v1/auth/apple/callback`)이어야 교환이 된다. Apple JS가 페이지와 다른 호스트의 `redirectURI`를 받는지는 확인하지 않았다; 거절하면 웹은 `authorizationCode`를 보내지 않는다(교환이 안 돼도 로그인은 되고 애플 토큰만 저장되지 않는다는 기존 규칙). 202(처음 보는 계정)는 앱과 같이 "새 계정으로 시작"(`register`) 또는 "기존 계정에 연결"(패스키로 로그인한 뒤 `/me/identities/apple`)을 묻는다(웹은 조용히 패스키를 확인할 수단이 없다).
- **구글**: Google Identity Services(`google.accounts.id.initialize({client_id, nonce, callback})` + `renderButton`)가 돌려주는 `credential`(ID 토큰)을 `POST /v1/auth/google {idToken, nonce}`에 보낸다. nonce는 `POST /v1/auth/google/nonce`(10분, 1회용)이며 401이나 만료 뒤에는 nonce를 새로 받아 `initialize`를 다시 한다. 서버는 허용 `aud`에 **웹 클라이언트 id**를 더하는 `GOOGLE_WEB_CLIENT_ID`를 읽는다(`dearby.google.web-client-id`, `GOOGLE_CLIENT_IDS`와 합쳐 허용 목록이 된다). 비어 있으면 서버는 그 토큰을 401로 거절하고, 웹은 자기 빌드 값(`NEXT_PUBLIC_GOOGLE_CLIENT_ID`)이 비어 있으면 버튼을 숨긴다 — 두 값은 같아야 한다. 값은 사용자가 Google Cloud에서 만든다(아직 없음): OAuth 클라이언트 "웹 애플리케이션", 승인된 JavaScript 원본 `https://dearby.wid.io.kr`(개발용 `http://localhost:3000`), 리디렉션 URI 없음. 기존 ①(Android `serverClientId`) 클라이언트에 JavaScript 원본을 더해 같은 id를 쓰는 것도 가능하며 그러면 `GOOGLE_WEB_CLIENT_ID`에 같은 값을 넣는다. 202 처리는 애플과 같다.
- 로그인 고지 한 줄(위 "애플 로그인"의 "로그인 고지")은 웹 로그인 화면에도 둔다.

### 토큰 보관 — 쿠키는 Next 서버만

- 서버 변경은 없다. TokenPair(`accessToken`·`refreshToken`)는 브라우저 JS에 두지 않는다. 로그인 응답을 받은 Next 라우트 핸들러가 두 토큰을 `HttpOnly; Secure; SameSite=Lax; Path=/`인 `__Host-` 쿠키에 넣고(게스트 쿠키와 같은 규칙, Domain 없음), 프록시가 요청마다 쿠키의 access token을 `Authorization: Bearer`로 바꿔 붙인다. 브라우저 응답 JSON·로그에 토큰을 내지 않는다.
- **refresh 회전**: access token은 1분이고 refresh token은 한 번 쓰면 폐기된다(회전). Next는 인스턴스가 여럿이고 공유 잠금이 없어 프록시가 요청마다 알아서 refresh하면 같은 refresh token을 동시에 두 번 써서 한쪽이 401로 끝난다. 그래서 프록시는 **대신 refresh하지 않고 401을 그대로 돌려준다**. 브라우저는 앱과 같은 규칙으로 전용 라우트 `POST /api/auth/refresh` 하나를 Web Locks로 탭 사이에서 한 번만 부른 뒤 원래 요청을 다시 보낸다. 그 라우트는 쿠키의 refresh token으로 `/v1/auth/refresh`를 부르고 새 쌍을 쿠키에 쓴다. refresh가 401이면 쿠키를 지우고 로그아웃 상태로, 네트워크 오류·5xx면 쿠키를 남기고 그 요청만 실패시킨다.
- 로그아웃은 `/v1/auth/logout` 뒤 쿠키를 지운다(실패해도 지운다). 계정 삭제(`DELETE /v1/me`) 뒤도 같다.
- **CSRF**: 쿠키로 인증하므로 프록시의 mutation(POST·PUT·PATCH·DELETE)은 게스트 저장과 같은 규칙 — `Origin`을 현재 웹 origin과 정확히 대조하고 JSON 또는 custom header를 요구하며 누락·불일치는 거부한다. SameSite만으로 대신하지 않는다.
- **CORS**: 웹이 same-origin 프록시만 쓰므로 API에 CORS를 열지 않는다(`SecurityConfig`의 `cors { disable() }` 유지). 브라우저가 `api.dearby.wid.io.kr`을 직접 부르는 경로는 없다.

### 비로그인 저장 → 계정 합치기

게스트 쿠키로 저장해 둔 명함이 있는 사람이 로그인하면 웹이 "브라우저에 저장한 명함 n개를 계정으로 옮길까요?"를 묻고, 동의하면 Next가 게스트 쿠키의 토큰을 본문에 넣어 부른다(토큰은 브라우저 JS에 보이지 않는다). 자동으로 옮기지 않는다.

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| POST /v1/wallet/import-guest | Bearer | `{guestToken}` | 200 `{imported: n}`(새로 만든 Receipt 수). 토큰 형식이 틀리거나 모르는·폐기된 세션은 400 `GUEST_SESSION_INVALID`(Bearer 경로라 401을 쓰지 않는다 — 프록시가 로그인 만료로 오해하지 않게) |

- 세션의 회수 전 저장(철회된 명함 제외)마다 계정 지갑에 Receipt를 만든다. **받은 날은 게스트 저장 시각**(`savedAt`)이고 활동 맥락(`activityId`)도 그대로 옮긴다(활동이 지워졌으면 `null`). 게스트는 저장할 때 QR 코드를 이미 확인했으므로 코드를 다시 요구하지 않는다.
- 이미 그 명함의 Receipt가 있으면 그대로 둔다(받은 날 유지, 활동이 비어 있었고 게스트 쪽에 있으면 채운다). 내 명함과 내가 차단한 주인의 명함은 건너뛴다. 둘 다 `imported`에 세지 않는다.
- 옮긴 뒤 그 세션의 저장은 모두 `recalled_at`로 닫아(행은 보존) 게스트 목록이 빈다. 세션 자체는 두므로 다시 부르면 `{imported: 0}`이고, 웹은 쿠키를 지우려면 기존 `DELETE /v1/guest/session`을 따로 부른다. 단방향 30일 회수 규칙은 옮긴 Receipt에도 그대로 적용된다(받은 날 기준이라 옮기기 전 지난 날이 포함된다).
- 게스트 세션의 요청 제한 카운터는 세지 않고(Bearer 경로), 프록시 키도 보지 않는다.

### 웹에서 쓰는 로그인 기능 — Bearer만으로 되는 것

2026-10-26 점검: 동의(`/v1/me/consents*`), 프로필(`/v1/profile*`, 사진·인증자료 multipart 포함), 내 활동(`/v1/me/activities*`), 즐겨찾기(`/v1/me/favorites*`), 세션 찜(`/v1/me/session-favorites*`), 알림 목록(`/v1/me/notifications*`), 알림 설정(`/v1/me/notification-settings`), 받은 명함·차단(`/v1/wallet*`), 내 명함(`/v1/cards*`), 계정·연결·패스키(`/v1/me`, `/v1/me/identities*`, `/v1/me/passkeys*`)는 모두 `@AuthenticationPrincipal Jwt`만 본다. 프록시는 `If-Match`(프로필 PUT), `Accept-Language`(즐겨찾기·알림·대기열), multipart 본문을 그대로 넘겨야 한다.

- 웹에서 쓰지 않는 것: `PUT /v1/me/push-devices`는 `platform`이 `IOS`·`ANDROID`만이라(웹 푸시 없음) 웹은 부르지 않는다. 맞춤 대기열(`/v1/me/discovery-queue*`)·광고 동의는 앱 전용 화면이다.

### 웹 화면 — 1단계(로그인·세션·동의·설정, 2026-10-26 웹 PR)

계정 기능(즐겨찾기·세션 찜·내 활동·받은 명함·프로필)은 다음 PR이다. 이 PR이 정한 웹 쪽 규칙:

- **라우트**: `POST /api/auth/{passkey/options, passkey, apple/nonce, apple, apple/register, google/nonce, google, google/register, refresh, logout}`, `GET /api/auth/session`(로그인 여부 + `GET /v1/profile`의 `consent`·`consentRequired`·`version`; access가 만료됐으면 401 `SIGNED_OUT`). 프록시 `/api/*`의 Bearer 허용 경로는 `me`, `profile`(GET, 동의 필드만 돌려줌), `me/consents`, `me/passkeys[/:id]`, `me/identities/{apple|google}`, `auth/passkeys/registration[/options]`, `wallet/import-guest`. 응답은 zod로 걸러 토큰은 절대 내지 않고, 업스트림 ProblemDetail은 아는 `code`(`CONSENT_REQUIRED`·`CONSENT_VERSION`·`LAST_SIGN_IN_METHOD`·`GUEST_SESSION_INVALID`)와 `detail`만 넘긴다.
- **쿠키**: `__Host-dearby_access`(Max-Age 60)·`__Host-dearby_refresh`(90일), HttpOnly·Secure·SameSite=Lax·Path=/; 개발은 `dearby_*_dev`. 프록시는 access 쿠키가 없거나 업스트림이 401이면 401 `SIGNED_OUT`을 그대로 돌려주고, 브라우저가 `POST /api/auth/refresh`를 Web Locks(`dearby-auth-refresh`)로 한 번 부른 뒤 재시도한다. 그 라우트는 access 쿠키의 `exp`가 10초 넘게 남아 있으면(다른 탭이 방금 회전) 회전하지 않고 `{refreshed:false}`, 거절(401·400)이면 두 쿠키를 지우고 401, 네트워크·5xx면 502에 쿠키를 남긴다. 로그아웃·계정 삭제(204)는 쿠키를 지운다.
- **로그인 시트**(하단 시트, `닫기`): 락업 이미지(`shared/assets/brand/sign-in-lockup-{light,dark}.png` 96px) → `Apple로 계속하기`(검정/다크 흰색, Apple 로고 — Apple JS popup) → Google GIS 버튼(`text: continue_with`, `locale: ko`, 라이트 `outline`·다크 `filled_black`) → 로그인 고지 한 줄 → `패스키로 로그인`(텍스트 버튼). 빌드 값 `NEXT_PUBLIC_APPLE_SERVICES_ID`·`NEXT_PUBLIC_GOOGLE_CLIENT_ID`가 비었거나 nonce 요청이 503이면 그 버튼을 숨긴다. Apple JS `redirectURI`는 `NEXT_PUBLIC_APPLE_REDIRECT_URI`(API 콜백 URL)이고, 비어 있으면 페이지 origin을 쓰고 `authorizationCode`를 보내지 않는다. nonce는 시트가 열릴 때(그리고 시도마다) 미리 받아 두어 팝업이 클릭 안에서 열린다. 202는 "처음 보는 {애플|구글} 계정이에요" + `새 계정으로 시작`·`기존 패스키 계정에 연결`(패스키 로그인 뒤 `/me/identities/{provider}`)·`다른 방법으로 로그인`. 진입점: 상단 바 `로그인`, `/settings`의 로그인 CTA, 로그인이 필요한 동작의 공통 훅 `useAuth().requireAccount(action)`(로그인·동의 뒤 action 실행).
- **동의**: 로그인 직후와 페이지를 불러올 때마다 `consentRequired`이면 동의 시트(앱 문구 그대로, 재동의 제목 `개인정보 처리방침이 바뀌었어요`, `동의하고 계속`, 프로필 version 0은 `가입 취소`(계정 삭제), 아니면 `로그아웃`). 닫으면 로그인은 유지되고 다음에 다시 묻는다. 동의 전에는 `/settings`가 "동의가 필요해요 / 동의하기"만 보이고 `requireAccount`는 동의 시트를 연다. 버전은 `features/auth/model/consent-versions.ts`(`2026-10-26`·`2026-10-22`).
- **비로그인 저장 → 계정 합치기**: 로그인·동의가 끝나면 `GET /api/guest/cards` 수가 1 이상일 때 "브라우저에 저장한 명함 n개를 계정으로 옮길까요?"(나중에·옮기기). 옮기기는 `POST /api/wallet/import-guest {}` → Next가 게스트 쿠키의 토큰을 본문에 넣음 → 성공하면 `DELETE /api/guest/session`으로 게스트 쿠키를 지우고 "저장해 둔 명함 n개를 계정으로 옮겼어요" 토스트.
- **상단 바**: 비로그인 `로그인` 버튼, 로그인은 계정 아이콘(`person`, 접근성 이름 `설정`) → `/settings`. `/settings`: 비로그인은 로그인 CTA·`정보`, 로그인은 `계정`(가입일·로그인 수단·로그아웃·계정 삭제 — 앱과 같은 확인 문구)·`패스키`(목록·삭제·`패스키 추가`, 마지막 로그인 수단 규칙)·`정보`. 알림·연결된 로그인·패스키 이름 바꾸기는 다음 PR.
- **사용 통계**: `signed_in` 사용자 속성을 웹도 보낸다(analytics-v1.md "웹"); 동의 뒤에만 `yes`.

### 웹 화면 — 2단계 B(내 활동, 2026-10-26 웹 PR)

사용자 "웹에 나머지 기능도 구현" 중 **내 활동**. 저장은 로그인 계정(`/v1/me/activities*`)만이고 웹에는 기기 저장·import가 없다(로그아웃하면 웹의 내 활동은 비어 보인다). 규칙은 위 "내 활동 서버 저장·일정 상태", "선발 과정·활동 일정 분리", "지난 선발 단계 통과 제안", "진행 단계 화면 정리", "오늘 내 행사 카드"를 따르고, 웹에서 다른 점만 적는다.

- **프록시**: Bearer 허용 경로에 `GET me/activities`, `PUT`·`DELETE me/activities/:id`(PUT 본문 `{}`), `PUT me/activities/:id/schedules/:scheduleId`(본문 `{statusId}` 또는 `{kind}` 하나만, 둘·빈 본문은 422), `GET me/session-favorites`(읽기만 — 오늘 카드의 다음 찜한 세션; 찜은 앱에서)를 더한다. 계정 mutation의 `If-Match`는 형식(`*` 또는 entity-tag 목록)이 맞으면 그대로 올린다(프로필 PUT용, 담당 A). 공개 `Schedule`은 `kind`·`statuses`를, `Activity`는 `hasTimetable`을 더 읽는다. refresh-once 래퍼(`accountRequest`·`accountSend`)는 `lib/client.ts`로 옮겨 로그인·내 활동이 함께 쓴다.
- **상태 공유**: `MineProvider`(`features/mine`)가 로그인·동의 뒤 한 번 `GET /api/me/activities` → 항목마다 `GET /api/activities/:id`(404는 "삭제된 활동") → `GET /api/me/session-favorites`를 읽고, `+`/체크·`/mine`·진행 단계·오늘 카드가 같은 값을 쓴다(오늘 카드는 추가 요청 없음). 단계 판정은 iOS `ActivityStages`·`TodayEvent`와 같은 규칙의 `features/mine/model/stages.ts`.
- **추가·체크**: 활동 카드(발견)와 활동 상세의 상태 캡슐 오른쪽 끝에 `+`(접근성 이름 `내 활동에 추가`, 웹은 아직 하트가 없어 하트 자리; 2단계 A 병합 뒤에는 하트 오른쪽 옆). `PUT`이 끝나면 체크 아이콘(`내 활동으로`) → `/mine/:id`. 비로그인·동의 전은 `requireAccount`로 로그인·동의 뒤 추가한다. 카드는 활동 이름 링크를 카드 전체로 늘려 `+`가 링크 안에 들어가지 않게 했다(`action` 슬롯; 하트도 같은 자리). 추가 직후 **통과 제안**은 iOS 알림 모양 `Dialog`로 같은 제목·두 본문·`통과로 표시`/`그대로 두기`(바깥 누르기·ESC = 그대로 두기), 물은 활동은 `localStorage` `dearby.mine.autopassAsked`에 기록(저장이 막힌 브라우저는 다시 물을 수 있다). 활동 상세에서 빼기는 없다(앱과 같음).
- **`/mine`**(상단 바 `내 활동`, 로그인 때만): 진행 트랙 행(활동 이름 + `시간표` 배지, 프로그램 · 조직, 세그먼트 4칸+`+N`, 배지 `D-3 · 면접`/`D-DAY`/`참여 중`/`지남`/`탈락`/`날짜 미확인 · …`), 다음 단계 가까운 순, `끝난 활동`은 네이티브 `<details>`로 접힘. 삭제된 활동 행은 `빼기`만. 비로그인은 로그인 CTA, 동의 전은 `동의하기`. 밀어서 빼기는 없고(웹) 진행 단계 화면의 빼기로 충분하다.
- **`/mine/:id`**(진행 단계): 머리 활동 카드(태그 줄·`+` 없음, 누르면 상세) → `선발 과정`·`활동 일정` 두 inset 그룹(빈 쪽은 숨김). 행은 종류 아이콘(지남 보조색·현재 청록·예정 옅음·탈락 빨강) · 이름 · `날짜 · 지남/진행 중/예정`. 선발 단계만 오른쪽에 **네이티브 `<select>`**(그 일정의 `statuses` 이름; 통과 청록·탈락 빨강) — 웹은 행 메뉴 대신 폼 기본 컴포넌트. 시간표 있는 블록은 `시간표` 버튼(상세와 같은 시간표 모달). 상태 배지는 첫 그룹 머리글 오른쪽, 선발이 모두 통과면 `활동 일정` 머리글에 `참여 중`만. 맨 아래 `내 활동에서 빼기` → 확인("내 활동에서 뺄까요? 단계 표시도 함께 지워져요.") → `/mine`. `캘린더에 추가`·`겹치는 시간 확인`은 웹에 기기 캘린더가 없어 없다.
- **오늘 내 행사**: 발견 제목 아래(검색 중·비로그인·로딩 중·해당 없음이면 없음). 같은 고르기·본문 규칙(찜한 세션은 `GET /me/session-favorites`로 읽기만). 오늘 + 시간표 블록이면 시간표 모달(웹은 `찜한 세션` 탭이 없어 전체), 그 밖은 `/mine/:id`. `명함 QR`은 웹 명함 기능이 없어 없다. 분 단위로 다시 그린다.
- **사용 통계**(analytics-v1.md "웹"): `myactivity_add`(`source` = `discovery` 카드·`link` 상세), `stage_mark`(`kind`, 제안이면 `via=autopass_prompt`), `today_card_tap`(`target` = `timetable`·`progress`). 웹은 `field`를 보내지 않는다.

### 웹 화면 — 2단계 A(즐겨찾기·내 시간표, 2026-10-26 웹 PR)

사용자 결정(2026-10-26) "웹에 나머지 기능도 구현"의 첫 묶음. 위 "즐겨찾기(하트)"·"트랙·세션 시간표"의 규칙을 웹에 적용하며, 그 절들의 "웹은 로그인이 없어 하트를 두지 않는다/전환이 없다" 문장은 이 PR부터 더 이상 맞지 않는다. 다른 2단계 묶음(B·C)은 같은 절에 소절을 더한다.

- **공개 조회에 Bearer**: 프록시의 `GET /api/discovery/programs`·`/api/activities`·`/api/activities/:id`·`…/schedules/:id/timetable`은 access 쿠키의 `exp`가 남아 있을 때만 그 토큰을 `Authorization: Bearer`로 붙여 `Activity.favorited`·`ProgramSummary.favorited`·`Session.favorited`를 받는다(API는 만료된 Bearer를 공개 경로에서도 401로 거절하므로 `exp`로 거른다). access 쿠키는 없고 refresh 쿠키만 있으면 계정 경로처럼 401 `SIGNED_OUT`을 돌려 브라우저가 `POST /api/auth/refresh`를 한 번 부르고 재시도한다(`publicRead`; refresh가 거절돼 쿠키가 지워졌으면 익명으로 다시 읽는다 — 세션이 끝난 방문자도 둘러보기는 되어야 한다). 쿠키가 하나도 없으면 지금처럼 익명. 응답은 이미 `private, no-store`이고 `Vary: Cookie`라 `Authorization`을 Vary에 더하지 않는다. 오픈그래프 미리보기(`lib/preview.ts`)는 쿠키 없이 그대로 캐시한다. 로그인·로그아웃이 일어나면(`useAuth().epoch`) 발견 목록·활동 상세·열린 시간표를 다시 읽어 하트가 계정을 따른다(첫 로드에는 다시 읽지 않는다).
- **프록시 Bearer 허용 경로 추가**: `GET me/favorites`(브라우저 `Accept-Language`를 올리고 `Vary`에 더함), `PUT`·`DELETE me/favorites/{programs|activities}/:id`(숫자 id, 본문 `{}` — 알림 수준은 API 기본값; 웹은 종 버튼·알림 수준을 두지 않는다), `POST me/favorites/programs/:id/seen`, `GET me/session-favorites`, `PUT`·`DELETE me/session-favorites/:id`. 응답은 zod로 걸러 `notifyLevel`은 버린다.
- **하트 자리**: 발견 프로그램 카드 오른쪽 열(활동 수 배지 왼쪽), 펼친 활동 카드의 상태 캡슐 오른쪽, 활동 상세 머리의 캡슐 줄 오른쪽 끝, 시간표 모달 세션 카드의 배지 줄 오른쪽(쉬는시간·점심은 없음). 2단계 B의 `+` 내 활동 버튼은 활동 카드·상세에서 하트 바로 오른쪽에 나란히 둔다(앱의 "하트 옆 `+`"와 같은 배치). 활동 카드는 이름의 stretched link로 바꿔 하트 버튼이 앵커 안에 들어가지 않는다. 글리프 `heart`/`heart-fill`(채우면 teal), 44px 목표(카드 안은 36px), `aria-pressed`, 접근성 이름 `즐겨찾기`/`즐겨찾기 해제`(세션은 `찜`/`찜 해제`). 비로그인에서 누르면 `useAuth().requireAccount`로 로그인 시트가 열리고 로그인·동의 뒤 그 하트가 켜진다. 낙관적으로 바꾸고 실패하면 되돌리며 토스트로 알린다(401은 로그인 만료 처리).
- **시간표 모달**(블록·하루 시간표 둘 다): 머리글 아래 `전체 | 내 시간표` 세그먼트(열 때마다 `전체`; 하루 시간표의 `내 시간표`는 그 날 모든 블록의 찜을 한 줄로). `내 시간표`는 위 "`내 시간표(찜한 세션)`" 규칙을 iOS `MyTimetable.entries`와 같은 알고리즘으로 그린다 — (시작, 끝) 순 정렬, 앞 묶음의 가장 늦은 끝보다 먼저 시작하면 같은 묶음, 묶음 사이에 `n분 비어 있어요`(1시간 넘으면 `1시간 20분`)와 트랙이 바뀌면 `이동 › 트랙 B · 201호`, 둘 이상인 묶음 앞에 `같은 시간에 찜한 세션 n개 · 하나만 들을 수 있어요` 띠. 카드는 시간과 트랙·방 배지를 크게. 비로그인이면 "로그인하면 세션에 하트를 눌러 내 시간표를 만들 수 있어요" + `로그인`, 찜이 없으면 "세션에 하트를 누르면 내 시간표에 모여요". 모달 안에서 누른 하트는 바로 `내 시간표`에 반영된다. 하단 "내 시간표는 Dearby 앱에서 만들 수 있어요." 줄은 비로그인일 때만 남긴다.
- **`/favorites`**(상단 바 로그인 상태의 하트 아이콘 링크 `즐겨찾기`, 계정 아이콘 왼쪽; `robots: noindex`): 비로그인은 로그인 CTA, 동의 전은 `/settings`와 같은 `동의하기`. 로그인은 `GET /me/favorites`와 `GET /me/session-favorites`를 함께 읽어 — 위 `내 시간표 · 세션 n개` 펼침 행(찜이 있을 때만; 펼치면 활동별 → 날짜별 묶음, 머리글 `{활동 이름}` + `M월 d일 (요일)`, 각 날을 나만의 시간표 모양으로; 목록 항목의 `track`·`schedule`로 그리고 시간표를 다시 읽지 않는다) → `프로그램`(즐겨찾기한 최근 순, 발견 프로그램 행 그대로 — 펼치면 지원 가능 활동, `새 모집` 배지는 처음 펼칠 때 `POST …/seen`으로 끈다) → `활동`(서버 순서 = 마감 임박 순, 지난 활동 뒤; 발견 활동 카드에 프로그램 이름 부제). 캘린더 보기는 웹에 두지 않는다(목록만). 하트로 해제하면 그 자리에서 빈 하트가 되고 다음 로드에 빠진다(되돌리기 알림 없음). 셋 다 비면 "하트를 누르면 여기에 모여요" + `활동 둘러보기`. 웹 푸시·알림 수준·종 버튼은 없다.
- **사용 통계**: `favorite_add`·`favorite_remove`(`type` = `program`·`activity`·`session`, `id`, 세션은 `activity_id`, 활동·프로그램은 `category`) — analytics-v1.md "웹".

## 대상자 조건(eligibility) — 2026-10-18 사용자 결정

수집된 활동의 "대상자"를 자유 문장(`audience`)만으로 두지 않고, **중첩 가능한 JSON** `eligibility`로 규격화한다. 원문 `audience`는 그대로 남긴다. 사용자 지시: 필드 어휘는 한글이고 영문 버전도 함께 저장하며, 범위는 `from`·`to`로 표현하고, 검색할 수 있어야 한다. 표기 규칙(2026-10-18 사용자 지시): snake_case는 데이터베이스 열 이름에만 쓰고, JSON 키·영문 어휘·쿼리 키는 기존 API처럼 camelCase다.

### 구조

```json
{ "op": "all", "items": [
  { "field": "나이", "fieldEn": "age", "from": 19, "to": 34 },
  { "op": "any", "items": [
    { "field": "재학상태", "fieldEn": "educationStatus", "values": ["재학"], "valuesEn": ["enrolled"] },
    { "field": "취업상태", "fieldEn": "employment", "values": ["미취업"], "valuesEn": ["unemployed"] } ] },
  { "field": "국적", "fieldEn": "nationality", "values": ["대한민국"], "valuesEn": ["KR"] },
  { "field": "경력연수", "fieldEn": "experienceYears", "to": 2 },
  { "field": "기타", "fieldEn": "other", "note": "2년 풀타임 참여 가능", "noteEn": "Able to attend full-time for 2 years" } ] }
```

- 그룹: `op`는 `all`(모두 충족) 또는 `any`(하나 이상), `items`는 조건이나 그룹. 최상위는 그룹이다. 깊이 제한 3.
- 조건: `field`(한글 어휘)·`fieldEn`(영문 어휘)은 필수. 범위는 `from`·`to`(둘 중 하나 생략 가능, 포함 범위, 숫자) 또는 집합 `values`(한글)·`valuesEn`(영문, 같은 길이) 중 하나. `note`·`noteEn`은 선택이며 원문 구절·보충 설명이다. `기타`는 `note`만 가진다.
- 어휘(한글 → 영문): 나이 → `age`(만 나이), 출생연도 → `birthYear`, 학년 → `schoolYear`, 학력 → `educationLevel`(값: 고졸 `highSchool`·대학 재학/졸업 `university`·대학원 `graduateSchool`), 재학상태 → `educationStatus`(재학 `enrolled`·졸업 `graduated`·휴학 `onLeave`·졸업예정 `graduating`), 전공 → `major`, 지역 → `region`, 취업상태 → `employment`(학생 `student`·미취업 `unemployed`·재직 `employed`·창업 `founder`), 경력연수 → `experienceYears`, 국적 → `nationality`(ISO 3166-1 alpha-2), 팀규모 → `teamSize`, 기타 → `other`. 어휘에 없는 조건은 `기타`+`note`로 둔다. 서버는 어휘 밖의 `field`를 거절(400)한다.
- 저장: `activities.eligibility jsonb`(null 가능). 검색용으로 서버가 저장할 때 조건을 DB 표 `activity_eligibility_terms(activity_id, field_en, from_value, to_value, value_en)`(열 이름은 DB라서 snake_case)로 펼친다(그룹 구조는 무시하고 조건만 나열).

### 검색

- `GET /v1/activities`와 `GET /v1/discovery/programs`에 필터를 더한다. 쿼리 키는 영문 어휘(camelCase)다: 숫자 필드는 값 하나(`age=24`, `schoolYear=3`, `experienceYears=1`, `birthYear=2002`, `teamSize=4`), 집합 필드는 영문 값 하나(`educationStatus=enrolled`, `employment=student`, `nationality=KR`, `educationLevel=university`, `major=…`, `region=…`).
- 1차 판정(SQL): 요청한 필드마다 그 활동의 조건 중 하나라도 맞으면 통과(숫자는 `from ≤ 값 ≤ to`, 없는 쪽은 열림; 집합은 포함). **조건이 없는 활동(`eligibility` null 또는 그 필드의 조건 없음)은 제한이 없는 것으로 보아 통과**한다.
- 2차 판정(서버, JSON): 1차를 통과한 활동에 대해 `op`를 그대로 평가해 거른다. 요청하지 않은 필드의 조건은 참으로 본다. 프로그램 묶음의 열린 활동 수와 가장 가까운 마감은 2차까지 통과한 활동으로 센다.

### 수집·어드민·앱

- 수집 배치: 추출 스키마에 `eligibility`를 더하고 지시문에 위 어휘와 예시를 넣는다. 모델이 어휘 밖 필드를 내면 서버가 `기타`로 바꿔 저장한다. `audience` 원문도 계속 뽑는다.
- 어드민: `AdminActivity`에 `eligibility`를 싣고 `PATCH /v1/admin/activities/:id`에 `eligibility`(JSON, null로 지우기)를 받는다. 어드민 웹 활동 화면에서 JSON으로 보고 고친다(검증 오류는 서버 400 메시지 그대로).
- 공개 `Activity`(활동 조회)에 `eligibility`를 싣는다. iOS·Android·웹 활동 상세는 "대상" 절에 조건을 한글로 칩처럼 보인다: 범위는 `나이 19~34세`·`경력 2년 이하`, 집합은 `재학상태 재학`, 그룹 `any`는 `또는`으로 잇는다. `기타`는 `note`를 그대로. 조건이 없으면 기존 `audience` 문장을 보인다. 필터 UI는 이번 범위가 아니다(API만).

## 받은 명함 목록 표시 — 2026-10-18 사용자 결정

Mobbin의 Outlook 연락처 목록을 참고해 검색하기 쉬운 색인 목록으로 바꾼다(iOS·Android 공통. 웹 `/saved`는 이번 범위가 아니다).

- 묶음: 이름 첫 글자로 묶는다. 한글은 초성(ㄱ ㄴ ㄷ ㄹ ㅁ ㅂ ㅅ ㅇ ㅈ ㅊ ㅋ ㅌ ㅍ ㅎ, 된소리는 기본 자음으로: ㄲ→ㄱ, ㄸ→ㄷ, ㅃ→ㅂ, ㅆ→ㅅ, ㅉ→ㅈ), 영문은 대문자 A–Z, 그 밖은 `#`. 묶음 순서는 ㄱ…ㅎ, A…Z, `#`이고 묶음 안은 이름순이다. 묶음 머리글은 옅은 회색 띠에 초성/글자 하나이며 접근성에서 머리글로 읽힌다.
- 행: 이니셜 아바타(기존) · 이름(굵게) · 아래 줄에 직무 · 소속이 있으면 민트 배경의 작은 칩(가장 최근 승인 이력의 조직 이름). 전체가 눌리는 한 줄이고 오른쪽 화살표는 두지 않는다.
- 색인 레일: 오른쪽 가장자리에 묶음 글자를 세로로 나열한다. 누르거나 끌면 그 묶음으로 이동한다. 비어 있는 글자는 보이지 않는다. 접근성에서는 각 글자가 `ㄱ으로 이동` 버튼으로 읽히고, 동작 줄이기에서는 이동 애니메이션이 없다. 묶음이 6개 이하이면 레일을 숨긴다.
- 검색: 기존 검색 칸을 유지하고 검색 중에도 묶음을 유지한다(빈 묶음은 숨김).
- 내 명함 탭은 바꾸지 않는다.

## 명함 교환 — 2026-10-19 사용자 결정

2026-10-18 지운 공유 기능을 대신하는 새 설계. 사용자 결정(2026-10-19): 배부(배포) 단위 관리는 두지 않는다. 받은 명함에는 **어느 프로그램(활동)에서 교환했는지만** 표시한다(2026-10-26부터 그 활동 안의 **일정 블록·세션**까지 서버가 저장 시각으로 자동 판정해 남긴다 — 아래 "교환 세션 맥락"). **단방향**으로 받은 명함은 받은 날부터 30일 뒤 회수한다. 옛 흐름(제작 → 발행 → 공유 화면)이 부자연스러웠다는 이유로 주인 쪽은 "명함 고르기 → QR 보이기" 한 번으로 끝나야 한다. 발행 스냅샷·`/cards/:id` 공개 조회·비로그인 저장 계약은 그대로다.

### 흐름

- **주인(앱)**: 내 명함 탭에서 고른 명함 아래 `QR 보이기`. 전체 화면 QR과 함께 활동 칩이 보인다. 칩은 오늘 진행 중인 내 활동(내 활동 진행 트랙의 `진행 중` 중 현재 단계가 가장 가까운 것)이 자동으로 골라지고, 눌러서 신청한 다른 활동이나 `활동 없이`로 바꿀 수 있다. QR 내용은 `https://<웹 origin>/cards/<cardId>?activity=<activityId>`(활동 없이면 `activity` 생략). 화면을 보는 동안 밝기를 올리고, 닫으면 되돌린다. 배부 기록·수신자 목록·활동별 관리 화면은 만들지 않는다.
- **받는 사람(웹)**: OS 카메라로 QR을 읽으면 웹 `/cards/:id?activity=…`가 열린다. 카드 위에 `<프로그램 이름> <활동 이름>에서 받은 명함`(활동이 없으면 생략) 한 줄. 카드 아래 버튼 둘: `Dearby 앱에 저장`(`dearby://cards/<cardId>?activity=<activityId>` 커스텀 스킴, 앱이 없으면 아무 일도 없으므로 그 아래 "앱이 없으면 브라우저에 저장할 수 있어요" 안내)과 기존 `브라우저에 저장`(비로그인 저장, `activityId`를 함께 보낸다). 앱 안 카메라 스캔은 쓰지 않는다. 앱이 설치되어 있으면 2026-10-22부터 QR 주소가 유니버설 링크·App Links로 앱에서 바로 열린다(아래 "유니버설 링크·App Links·활동 공유·오픈그래프"). 2026-10-26부터 웹은 저장 버튼 아래(그리고 `/saved` 상단·로그인 시트 고지 아래)에 `비공개 테스트로 앱 받기`를 둔다: iOS는 TestFlight 링크로 바로, Android·데스크톱은 시트에서 Google 그룹 가입 → Play 테스트 참여 두 단계(링크는 웹 `NEXT_PUBLIC_TESTFLIGHT_URL`·`NEXT_PUBLIC_PLAY_GROUP_URL`·`NEXT_PUBLIC_PLAY_TEST_URL`, 비어 있으면 그 플랫폼은 숨김; 이벤트 `tester_cta_tap`, analytics-v1.md "웹").
- **받는 사람(앱)**: 스킴 `dearby://cards/<cardId>?activity=<activityId>`를 받으면 로그인 상태에서 `PUT /v1/wallet/cards/:cardId {activityId}`로 계정 지갑에 저장하고 받은 명함 상세를 연다. 상세 아래 `내 명함도 건네기` 버튼: 내 명함이 하나면 바로, 여럿이면 고른 뒤 `POST /v1/wallet/cards/:cardId/reciprocate {myCardId}`. 성공하면 "맞교환했어요" 배지. 로그아웃 상태면 로그인 시트를 먼저 띄우고 이어서 저장한다. 자기 명함을 받으면 "내 명함이에요" 안내만.
- **회수**: 받은 날(`receivedAt`/`savedAt`)부터 30일이 지나도 맞교환이 아니면 서버가 회수한다. 받은 명함 목록에서 사라지고(`recalled_at` 기록, 행은 보존), 그 사람의 지갑에서만 빠진다. 공개 `/cards/:id`는 그대로 열린다(배부 단위를 두지 않은 결정의 결과이며, 주소를 적어 둔 사람까지 막지는 않는다). 명함 철회(`DELETE /v1/cards/:id`)는 지금처럼 모두에게서 사라진다.
- **맞교환(reciprocal)**: 받은 명함 R(받는 사람 U, 명함 C, 주인 O)은 "O가 U의 명함(어느 것이든) 하나를 회수·철회되지 않은 상태로 갖고 있으면" 맞교환이다. 계정 쌍 기준이라 활동이 달라도 성립한다. 맞교환이던 것이 상대 명함의 철회·회수로 깨지면 다시 단방향이 되고, 받은 날 기준 30일이 이미 지났으면 다음 회수 때 빠진다. 비로그인 저장자는 명함이 없어 맞교환할 수 없으므로 항상 30일 뒤 회수된다(의도된 결과: 앱 설치를 유도한다).

### API

| 경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| PUT /v1/wallet/cards/:cardId | Bearer | `{activityId?: number}` | 첫 저장 201 Receipt, 이미 있으면 200 Receipt(받은 날은 유지, 활동이 비어 있었고 새로 왔으면 그때 맥락을 판정해 채운다). `activityId`가 오면 서버가 지금 시각으로 블록·세션까지 판정한다(아래 "교환 세션 맥락"). 없는/철회된 명함 404, 자기 명함 409 `OWN_CARD` |
| POST /v1/wallet/cards/:cardId/reciprocate | Bearer | `{myCardId}` | 201 `{reciprocal: true}`. 호출자가 `cardId`를 회수되지 않은 상태로 갖고 있어야 하고(아니면 403), `myCardId`는 호출자의 미철회 명함(아니면 404). 주인의 지갑에 호출자 명함의 Receipt를 같은 맥락(활동·블록·세션을 원 Receipt에서 복사, 다시 판정하지 않음)으로 만든다(이미 있으면 그대로 201) |
| GET /v1/wallet | Bearer | — | 200 `{items: Receipt[]}`. 회수된 것·철회된 명함 제외, `seq` 순 |
| GET /v1/guest/cards | 게스트 토큰 | — | 200 `{items: GuestItem[]}`(2026-10-19부터 `Card[]`가 아니다) |
| PUT /v1/guest/cards/:id | 게스트 토큰 선택 | `{activityId?: number}` | 기존 응답 그대로(201/200). 맥락은 처음 저장할 때만 기록하며 활동이 오면 진행 중 블록까지 판정한다(비로그인은 찜이 없어 세션은 늘 null) |
| POST /v1/wallet/import-guest | Bearer | `{guestToken}` | 200 `{imported: n}`. 웹 게스트 저장을 계정 지갑으로 옮긴다(2026-10-26 "웹 로그인"). 맥락(활동·블록)은 게스트 저장 때 것을 그대로 옮기고 다시 판정하지 않는다 |

- Receipt: `{id, card, context: ExchangeContext, receivedAt, reciprocal: boolean, expiresAt: string|null}`. `expiresAt`은 서버가 계산한 회수 예정 시각(`receivedAt` + 30일), 맞교환이면 `null`. 클라이언트는 다시 계산하지 않는다.
- GuestItem: `{card, context: ExchangeContext, savedAt, expiresAt}`(`expiresAt`은 항상 `savedAt` + 30일).
- ExchangeContext(2026-10-26 확장): `{activityId: number|null, activityName: string|null, programName: string|null, scheduleId: number|null, scheduleTitle: string|null, sessionId: number|null, sessionTitle: string|null, trackName: string|null, room: string|null}`. id가 있지만 활동·블록·세션이 지워졌으면 그 이름만 `null`(id는 남는다). 전폭 세션(트랙 없음)은 `trackName`·`room`이 `null`. 2026-10-26 전에는 앞의 셋만 있었다.
- 회수 작업: batch 모듈이 매일 04:00(Asia/Seoul)에 돌린다. 조건은 "회수되지 않았고 받은 날 + 30일 < 지금 이고 맞교환이 아님"(`receipts`), "회수되지 않았고 저장일 + 30일 < 지금"(`guest_cards`). `--recall-now` 플래그로 즉시 실행할 수 있다(`--collect-now`와 같은 방식). 결과는 로그에 건수로 남긴다.
- 저장: V29. `receipts.recalled_at timestamptz null`, `receipts.context`는 기존 jsonb에 `{"activityId": <number>}`만 쓴다(`label`은 쓰지 않는다). `guest_cards`에 `activity_id bigint null`, `saved_at timestamptz not null default now()`, `recalled_at timestamptz null`. 기존 행의 `saved_at`은 마이그레이션 시각이 되어 30일 뒤 회수된다.
- `activityId`는 활동의 숫자 id다(`GET /v1/activities/:id`). 모르는 id는 400이 아니라 무시하고 `null`로 저장한다(QR을 만든 뒤 활동이 지워진 경우).

### 교환 세션 맥락 — 2026-10-26

사용자 결정(2026-10-26, 우아콘 10/28 전 반영): 받은 명함에 "어느 프로그램·활동"뿐 아니라 **어느 일정 블록·세션(트랙·방)에서 받았는지**를 서버가 **저장 시각**으로 자동 판정해 남긴다. 사용자는 아무것도 고르지 않는다. 예: `우아콘 2026 · 오후 세션 · 트랙 B · 그랜드볼룸 B · "비 오는 날…"`.

- **판정 규칙**: `PUT /v1/wallet/cards/:cardId {activityId}`·`PUT /v1/guest/cards/:id {activityId}`에 활동이 오면 서버가 **지금 시각**(`now`)으로 그 활동 안에서 순서대로 고른다.
  1. 받는 사람(로그인)의 **내 시간표(찜한 세션)**(위 "트랙·세션 시간표") 중 `startAt <= now < endAt`인 것 → `sessionId`와 그 세션의 블록 `scheduleId`. 같은 시간에 찜이 여럿이면(다른 트랙) 시작이 빠른 것, 같으면 id가 작은 것 하나. 비로그인(게스트)은 찜이 없어 이 단계가 없다.
  2. 없으면 **진행 중인 시각 일정 블록** 하나 → `scheduleId`. 후보는 `activitySchedule`·`schedules[]` 중 시작·끝 시각이 있는 시각 일정(`startAt <= now < endAt`)이고 종일 일정·반복 일정·날짜 미정·모집 기간·지운 블록은 뺀다(여러 달짜리 종일 활동 기간이 모든 명함에 붙지 않게). 활동 기간이 세부 블록을 품어 둘 다 맞으면 **창이 짧은 것**(세부 블록), 같으면 id가 작은 것.
  3. 없으면 활동만(`scheduleId`·`sessionId` null). 모르는 활동 id는 기존처럼 셋 모두 null.
- 시각 비교는 절대 시각(instant)으로 하므로 블록의 `timeZone`은 판정에 쓰지 않는다(표시는 앱이 블록·세션의 시간대로 한다).
- **한 번만 판정**: 이미 저장된 Receipt·GuestItem은 다시 저장해도 바꾸지 않는다. 활동이 비어 있다가 나중에 `activityId`가 오면 **그때** 판정한다(그 시각 기준). `reciprocate`는 원 Receipt의 `activityId`·`scheduleId`·`sessionId`를 그대로 복사하고 다시 판정하지 않는다.
- **저장**: `receipts.context` jsonb에 `{"activityId", "scheduleId", "sessionId"}`(마이그레이션 없음, 옛 행은 둘이 없어 null). `guest_cards`에 `schedule_id bigint null`·`session_id bigint null`(V63, 논리키).
- **응답**: `Receipt.context`·`GuestItem.context`에 `scheduleId: number|null, scheduleTitle: string|null, sessionId: number|null, sessionTitle: string|null, trackName: string|null, room: string|null`을 더한다(위 ExchangeContext). 블록·세션이 지워졌으면 id는 남고 이름만 null(활동과 같은 규칙). 운영자가 블록을 지운(`curated_deleted`) 경우도 지운 것으로 본다. `scheduleId`·`sessionId`는 결정문에는 없고 서버가 더한 것이며 **클라이언트는 지금 읽지 않는다**(뒤에 받은 명함 상세에서 그 블록의 시간표 `GET /v1/activities/:id/schedules/:scheduleId/timetable`를 바로 열 때 쓸 수 있게 둔다).
- **화면(조율 세션 결정 2026-10-26, iOS #434 구현 기준)**: 받은 명함 **목록 행**은 프로그램 이름 칩을 그대로 두고 그 아래 캡션 한 줄 `프로그램 · 블록(scheduleTitle) · 트랙(trackName) · 방(room)`을 있는 값만 ` · `로 이어 더한다(모두 없으면 캡션 생략). **상세**는 같은 첫 줄에 둘째 줄로 세션 제목(`sessionTitle`, 없으면 생략). 활동 이름은 기존처럼 상세의 `<프로그램> <활동>에서 받음 · <받은 날>` 줄에 있다.

### 화면

- **받은 명함 목록(iOS·Android·웹 `/saved`)**: 기존 색인 목록 그대로. 행의 칩 줄에 프로그램 이름 칩(활동 이름은 상세에서)과 상태 배지 하나: 맞교환이면 `맞교환`, 아니면 `N일 뒤 사라져요`(`expiresAt` 기준, 당일은 `오늘 사라져요`). 활동별 필터·묶음은 만들지 않는다.
- **받은 명함 상세**: 카드 위에 `<프로그램> <활동>에서 받음 · <받은 날>`, 카드 아래 `내 명함도 건네기`(맞교환이면 `맞교환했어요` 배지로 대체).
- **내 명함 QR 화면**: 위 흐름대로. 색·글꼴은 기존 토큰. QR은 iOS CoreImage `CIQRCodeGenerator`, Android `com.google.zxing:core`(인코딩만, CameraX·카메라 권한 없음)로 그린다. 웹 origin은 `DEARBY_WEB_ORIGIN` 빌드 설정(기본 `https://dearby.wid.io.kr`)이다.
- 접근성: QR 이미지는 "내 명함 QR 코드, 카메라로 읽으면 명함을 받아요"로 읽히고, 활동 칩은 버튼이다. 배지는 행 접근성 라벨에 포함한다.

## 명함 QR 코드 — 2026-10-22 사용자 결정

사용자: "OAuth2 로그인처럼 QR 생성 시 코드를 발급해 쿼리스트링에 넣고 QR을 렌더링하고, 닫을 때 그 코드를 만료시키는 거야. 웹에서 들어갈 때 세션과 명함이 매칭돼 있지 않으면 쿼리스트링의 코드가 맞을 때만 볼 수 있게… 저장하면 매칭 정보를 넣어 다시 들어왔을 때 통과" → "여러 명이 QR을 쓸 수 있어야 하니 일회용은 안 되고, 닫거나 QR 새로고침할 때 API로 만료." 목적은 명함을 공개 게시물이 아니라 직접 만난 사람끼리의 교환으로 한정하는 것(App Store 1.2 대응의 일부, 신고·차단은 별도 결정). 위 "명함 교환"의 "공개 `/cards/:id`는 그대로 열린다"와 아래 "발행 스냅샷·공개 조회" 중 조회 권한 부분을 이 절이 대체한다.

### 코드

- 주인 앱이 QR 화면을 열 때 `POST /v1/cards/:id/qr-codes`(Bearer, 주인) → 201 `{id, code, expiresAt}`. `code`는 32바이트 무작위 base64url(43자). 서버는 원문을 저장하지 않고 SHA-256 해시만 저장한다(조회·저장 때 받은 코드를 해시해 비교)(`card_qr_codes(id uuid, card_id, code_hash, created_at, expires_at, revoked_at)`, V47).
- 여러 명이 쓸 수 있다(일회용 아님). 만료(2026-10-22 사용자 확정):
  - 최대 수명: 발급 5분 뒤(`expiresAt`, 2026-10-22 사용자 "30분 너무 길어 5분 정도로"). 열려 있어도 이 시각이 지나면 만료된다. 앱 강제 종료로 닫힘 신호가 오지 않아도 여기서 끝난다.
  - 닫기: QR 화면을 닫을 때 `DELETE /v1/cards/:id/qr-codes/:codeId`(204, 이미 만료여도 204)를 부르면 서버가 `expiresAt`을 **지금 + 2분**으로 당긴다(이미 더 이르면 그대로). 닫은 직후에 늦게 스캔한 사람도 받을 수 있게 하기 위해서다.
  - 새로고침: 새 코드를 발급하고 이전 코드에 같은 DELETE(2분 뒤 만료)를 부른다.
  - 자동 새로고침: QR 화면이 열린 채 `expiresAt` 1분 전이 되면 앱이 스스로 새로고침한다.
- 미리 받기: 명함 탭(내 명함 구역)이 보일 때, 그리고 고른 명함이 바뀔 때 그 명함의 코드를 미리 발급해 메모리에 둔다(행사장처럼 연결이 약한 곳 대비). QR 화면을 열 때 미리 받은 코드가 그 명함 것이고 남은 시간이 1분을 넘으면 그대로 쓰고, 아니면 새로 발급한다. 미리 받았다가 쓰지 않은 코드는 이전 것을 DELETE로 정리하지 않고 5분 최대 수명으로 끝나게 둔다(닫힘과 무관하므로). 저장하지 않으며 앱을 다시 켜면 새로 받는다.
- 명함 철회(`DELETE /v1/cards/:id`)는 그 명함의 코드를 모두 만료한다. 활동 칩을 바꿔도 코드는 그대로다(주소의 `activity`만 바뀐다).
- 발급 실패(네트워크·5xx)면 QR 대신 "연결을 확인하고 다시 시도해 주세요"와 다시 시도 버튼. QR은 코드 없이 그리지 않는다.
- QR 내용: `https://<웹 origin>/cards/<cardId>?activity=<activityId>&code=<code>`(활동 없이면 `activity` 생략). 앱 스킴도 같은 쿼리: `dearby://cards/<cardId>?activity=…&code=…`.

### 조회·저장 권한

- `GET /v1/cards/:id`는 다음 중 하나면 200: 주인(Bearer), 회수되지 않은 Receipt를 가진 계정(Bearer), 회수되지 않은 게스트 저장을 가진 게스트 토큰, 유효한 `?code=`. 아니면 403 ProblemDetail `type` 끝 `card-code-required`(코드 없음) 또는 `card-code-expired`(만료·모르는 코드), 철회된 명함은 기존대로 404. 코드는 그 명함의 것이어야 한다.
- `PUT /v1/wallet/cards/:cardId`·`PUT /v1/guest/cards/:id` 본문에 `code?: string`. 이미 저장(회수 전)돼 있으면 코드 없이 200. 처음 저장이면 유효한 코드가 필요하고 없거나 만료면 403(같은 type). 회수된 뒤 다시 저장하려면 새 코드가 필요하다.
- 맞교환 `POST /v1/wallet/cards/:cardId/reciprocate`는 코드가 필요 없다(호출자가 이미 받은 명함이 근거, 상대 지갑에 넣는 것은 호출자 자신의 명함).
- 회수·철회 뒤에는 위 조건이 깨지므로 웹 주소로도 볼 수 없다.

### 웹

- `/cards/:id`는 서버에서 코드·게스트 쿠키와 함께 조회한다. 로드 직후 `history.replaceState`로 주소에서 `code`를 지운다(복사·공유로 코드가 따라가지 않게). 코드는 페이지 메모리에만 두고 `브라우저에 저장`·`Dearby 앱에 저장`(스킴 주소에 `code`) 때 쓴다. `Referrer-Policy: no-referrer`.
- 403이면 카드 대신 가운데 정렬 안내: 제목 "명함을 볼 수 없어요", 본문 "명함 주인에게 QR을 다시 보여 달라고 하세요." 저장해 둔 명함은 `/saved`에서 그대로 열린다.
- 서버·프록시 로그에 `code` 쿼리를 남기지 않는다. 운영 nginx는 `code` 쿼리가 있는 요청만 쿼리 없는 형식(`$uri`)으로 기록하고, 다른 요청은 기존 형식 그대로 둔다(2026-10-22 사용자 "모든 요청에 대해서 그러진 말고 code qs가 있을 경우만"). 웹 호스팅(Vercel)의 첫 요청 로그는 막을 수 없고, 코드는 5분 안에 끝난다.

### 앱

- iOS·Android QR 화면: 열 때 발급, 닫을 때(시트 dismiss·뒤로) 만료 호출(실패해도 닫힘은 막지 않음), 오른쪽 위 새로고침 버튼(네이티브 아이콘 `arrow.clockwise`/`Refresh`, 접근성 "QR 새로 만들기"), 자동 새로고침. 발급 중에는 QR 자리에 시스템 진행 표시.
- 스킴으로 받은 `code`를 `PUT /v1/wallet/cards/:cardId`에 그대로 보낸다. 403이면 "명함 주인에게 QR을 다시 보여 달라고 하세요" 알림.

## 명함 신고·차단 — 2026-10-22 사용자 결정

사용자: "받은 명함 상세와 웹 명함 페이지에 '신고하기'를 둬요. 신고는 서버에 기록돼 어드민에서 볼 수 있어요." "받은 명함을 지우면서 그 사람의 명함을 다시 받지 않게 해요." App Store 1.2(신고·차단·연락처 공개) 대응.

### 신고

- `POST /v1/cards/:id/reports` 인증: Bearer 또는 게스트 토큰(웹 프록시) 또는 없음(웹에서 코드로 본 사람, 본문에 `code`). 명함을 볼 권한이 있는 사람만(위 "명함 QR 코드" 조회 권한과 같음, 아니면 403). 본문 `{reason: SPAM|IMPERSONATION|INAPPROPRIATE|OTHER, detail?: string(≤500), code?: string}`. 201. 같은 신고자(계정·게스트)가 같은 명함을 24시간 안에 다시 신고하면 200으로 기존 신고를 돌려준다(중복 방지). 자기 명함은 400.
- 사유 표시: 스팸·광고 / 다른 사람 사칭 / 부적절한 내용 / 기타(기타는 설명 필수).
- 저장: `card_reports(id uuid, card_id, reporter_user_id null, reporter_guest_id null, reason, detail, created_at, status OPEN|RESOLVED|DISMISSED, resolved_at, resolved_by, note)`(V48). 신고 시점의 명함 스냅샷(이름·직무·소개 링크·연락처; 2026-10-26 전 신고는 소개 글)을 `card_snapshot jsonb`로 함께 둔다(주인이 고치거나 지워도 근거가 남게).
- 보유 기간(2026-10-25 사용자 결정): 신고 기록은 신고일(`created_at`)로부터 1개월이 지나면 상태(`OPEN` 포함)와 관계없이 지운다(batch 매일 04:00 명함 회수와 함께). 차단(`card_blocks`) 보유 기간은 아래 "차단".
- 화면: 받은 명함 상세(iOS 툴바 `ellipsis.circle` Menu, Android TopAppBar 더 보기 메뉴)와 웹 `/cards/:id`·`/saved` 상세의 카드 아래 작은 글자 버튼 `신고하기`. 사유 고르기(iOS confirmationDialog 또는 시트의 Picker, Android 라디오 목록 다이얼로그, 웹 shadcn Dialog) → 선택 설명 → 보내기. 성공하면 "신고를 받았어요. 24시간 안에 확인할게요." 신고만으로 명함이 사라지지는 않는다(차단은 따로).

### 차단

- 받은 명함 상세의 메뉴 `삭제하고 차단하기`(파괴적 역할, 확인 대화상자 "<이름>님의 명함을 지우고, 앞으로 이 사람의 명함을 받지 않아요."). 차단 단위는 명함 주인(계정)이다.
- `POST /v1/wallet/blocks {cardId}`(Bearer) → 201 `{id, name, blockedAt}`: 내 지갑에서 그 주인의 모든 명함 Receipt를 지우고(행 삭제가 아니라 `recalled_at`), 차단을 기록한다. 이후 그 주인의 명함을 `PUT /v1/wallet/cards/:cardId`하면 409 `BLOCKED`("차단한 사람의 명함이에요"), 그 주인이 `reciprocate`로 내 지갑에 넣으려 하면 조용히 201(상대에게 차단 사실을 알리지 않는다)이지만 넣지 않는다. 상대 지갑의 내 명함은 그대로 둔다.
- 게스트(웹 `/saved` 상세): `POST /v1/guest/blocks {cardId}`(게스트 토큰)로 같은 동작(`guest_cards` 회수 + 게스트 차단 기록, 이후 `PUT /v1/guest/cards/:id` 409).
- `GET /v1/wallet/blocks` → `{items:[{id, name, blockedAt}]}`, `DELETE /v1/wallet/blocks/:id` 204(차단 해제, 지운 명함은 돌아오지 않는다). `name`은 차단할 때 그 명함의 이름 스냅샷. 앱 설정 화면에 `차단한 사람` 행 → 목록과 밀어서/편집으로 해제. 게스트는 목록·해제 없음(쿠키를 지우면 초기화).
- 저장: `card_blocks(id uuid, blocker_user_id null, blocker_guest_id null, blocked_user_id, name, created_at)`(V48, 둘 중 하나만).
- 보유 기간(2026-10-25 사용자 결정): 게스트 차단은 차단일(`created_at`)로부터 1년이 지나면 지우고, 게스트 세션이 지워졌으면(`DELETE /v1/guest/session`) 기간과 관계없이 지운다(batch 매일 04:00 명함 회수와 함께, 세션 삭제 뒤 다음 실행까지 남을 수 있다). 계정 차단은 해제할 때까지 둔다.

### 어드민

- `GET /v1/admin/card-reports?status=OPEN` → 목록(명함 스냅샷 이름, 사유, 설명, 신고 시각, 같은 명함 신고 수, 상태). `PATCH /v1/admin/card-reports/:id {status, note?}`. `POST /v1/admin/cards/:id/withdraw {reportId?, note}` → 운영자가 명함을 내린다(주인의 `DELETE /v1/cards/:id`와 같은 효과, 모두에게서 사라짐, 연결된 신고는 RESOLVED). 어드민 웹 `신고` 메뉴: 표(열린 신고가 위), 행을 열면 스냅샷·사유와 `처리 완료`·`무시`·`명함 내리기`.

### 연락처 공개

- 운영자: 팀 WID, 연락 이메일 fixabley@naver.com, 전화 010-7503-5692(2026-10-22 사용자 제공). 처리방침·약관의 운영자·연락처 빈칸을 채우고, 웹 `/support`(지원 페이지: 운영자·이메일·전화·신고 방법·계정 삭제 방법)를 만든다. App Store 지원 URL과 Play 계정 삭제 URL은 `/support`(삭제 안내 절에 바로 가는 `#delete-account`).

## 내용 로딩은 스켈레톤 — 2026-10-22 사용자 결정

사용자: "앱에서 현재 모집중이거나 예정인 활동이 없을경우 로딩 대신에 지난 활동이 없다는 메세지가 떴다가 조회되는 경우가 있어. 혹시 로딩을 스켈레톤으로 일괄 변경해줄 수 있을까?"

- **범위**: 화면이나 구역의 **내용을 불러오는 동안**(발견 프로그램 목록·펼친 프로그램의 활동, 활동 상세, 내 활동, 명함 탭의 내 명함·받은 명함, 받은 명함·공유받은 명함 상세, 프로필·이력·버전, 설정 계정 구역, 차단 목록, 활동 정보 고르기 등)은 스피너와 `불러오는 중이에요` 대신 **그 내용 모양의 스켈레톤**을 보인다. 웹·iOS·Android 같다.
- **스피너로 남는 것**: 사용자가 누른 동작의 진행(저장·삭제·로그인·QR 만들기·신고 보내기 등 버튼 안·위의 진행 표시), 풀투 리프레시 표시기, 목록 끝 다음 페이지 표시. 이미 내용이 있는 화면을 덮지 않는다.
- **표현**: iOS는 실제 행 뷰에 예시 값을 넣고 `.redacted(reason: .placeholder)`(시스템 자리 표시자), Android는 Material 3 색(`surfaceContainerHighest`) 둥근 블록, 웹은 기존 `Skeleton`. 은은한 맥박 애니메이션은 허용하되 동작 줄이기(reduce motion)에서는 멈춘다. 스켈레톤 행 수는 화면을 대략 채우는 정도(목록 3~5행, 펼친 프로그램 카드 1~2개)로 고정한다.
- **접근성**: 스켈레톤 묶음은 하나의 요소로 `불러오는 중` 라벨(iOS `accessibilityLabel`, Android `contentDescription`, 웹 `role="status"` + `aria-busy`)을 갖고, 자리 표시 글자는 읽히지 않는다.
- **빈 상태는 모두 받은 뒤에만**: 한 구역이 요청 여러 개로 채워지면(펼친 프로그램의 모집 중 + 모집 예정·지난 활동) 모두 도착하기 전에는 빈 상태 문구(`지금 모집 중인 활동이 없어요` 등)를 보이지 않고 스켈레톤을 유지한다. 2026-10-22 iOS가 모집 중 목록만 먼저 넣어 빈 문구가 잠깐 보이던 문제.

## 유니버설 링크·App Links·활동 공유·오픈그래프 — 2026-10-22 사용자 결정

사용자: "유니버설 링크 적용해줘. Dearby 설치된 곳에선 명함 링크 조회시 앱에서 들어가 지도록. 활동 상세보기에서 공유는 Dearby 링크를 보내도록 하고 싶어. 오픈그래프도 만들어줘". 2026-10-18의 "유니버설 링크·App Links는 만들지 않는다"를 이 절이 대체한다. 옛 `/s/*` 공유 기록은 그대로 복원하지 않는다.

- **앱으로 여는 경로**: 웹 origin `https://dearby.wid.io.kr`의 `/cards/*`와 `/activities/*`만. `/`·`/saved`·`/privacy`·`/terms`·`/support`·`/api/*`는 웹에 남는다.
- **연결 파일(웹, Vercel, 정적 값·환경값 없음)**:
  - `/.well-known/apple-app-site-association`: `{"applinks":{"details":[{"appIDs":["4V9FVN3VQF.io.wid.dearby"],"components":[{"/":"/cards/*"},{"/":"/activities/*"}]}]}}`. `Content-Type: application/json`, 리다이렉트 없이 200.
  - `/.well-known/assetlinks.json`: `delegate_permission/common.handle_all_urls`, 패키지 `io.wid.dearby`, `sha256_cert_fingerprints` = 업로드 키 `32:B6:2B:E3:CD:56:FC:B5:0C:67:CC:09:67:3B:D3:20:D2:33:10:3F:8F:EC:5C:0C:D8:3A:C6:A2:61:0D:C6:85`, 개발 기기 확인용 디버그 키 `91:E3:35:92:3F:21:75:61:A3:D1:7A:D0:F7:E8:5E:EA:BC:86:41:01:EA:48:96:70:DF:CA:DF:1B:9B:20:69:46`. **Play 앱 서명 키는 미정**: 첫 AAB를 Play에 올린 뒤 Play Console(앱 무결성)의 SHA-256을 추가한다. 그 전까지 Play 설치본은 링크를 브라우저로 연다.
  - 패스키용 `wid.io.kr` 파일(nginx)은 그대로 둔다.
- **iOS**: `Dearby.entitlements`·`Dearby-Debug.entitlements`의 associated-domains에 `applinks:dearby.wid.io.kr`를 더한다. Debug는 `applinks:dearby.wid.io.kr?mode=developer`(개발자 모드 기기에서 Apple CDN 캐시 없이 확인). 링크는 기존 `.onOpenURL`로 받는다.
- **Android**: `MainActivity`에 `android:autoVerify="true"` https intent-filter(host `dearby.wid.io.kr`, pathPrefix `/cards/`·`/activities/`).
- **앱이 링크를 받으면**:
  - 지원 화면(사용자 2026-10-22 "활동 상세보기, 공유받은 명함 보기, 명함 - 받은 명함 탭 - 받은 명함 상세 지원했으면 좋겠어"): **활동 상세**, **공유받은 명함 보기**, **명함 탭 › 받은 명함 › 받은 명함 상세**.
  - `/activities/<id>`: 발견 탭으로 가서 그 활동 상세를 push한다. 없는 활동은 상세의 기존 없는 활동 표시를 따른다.
  - `/cards/<cardId>?activity=<n>&code=<code>` — 이미 받은 명함에 있는 명함이면(로그인, `GET /v1/wallet`에 그 카드): 명함 탭 › 받은 명함 구역으로 가서 그 명함의 **받은 명함 상세**를 연다(저장 요청 없음).
  - 그 밖(아직 받지 않았거나 로그아웃): **공유받은 명함 보기**를 연다. 공개 명함 조회(`GET /v1/cards/:id`, 웹 `/cards/:id`가 쓰는 것과 같은 요청에 `code`)로 받은 명함 상세와 같은 명함 표현을 보여 주고, 위에 `<프로그램 이름> <활동 이름>에서 받은 명함` 한 줄(활동이 있을 때), 아래 주 버튼 `받은 명함에 저장`. 누르면 기존 `dearby://cards/…` 저장 흐름(로그아웃이면 로그인 뒤 이어서, 코드 검증·만료 안내 "명함 주인에게 QR을 다시 보여 달라고 하세요", 내 명함·차단 안내 그대로)을 타고, 저장되면 명함 탭 › 받은 명함 › 그 명함 상세로 간다. 내 명함이면 저장 버튼 대신 기존 내 명함 안내. 조회 실패(코드 만료·회수·없음)는 화면 안 오류 표시와 같은 안내 문구. 신고는 받은 명함 상세·웹과 같은 `신고하기`를 둔다.
  - `dearby://cards/…`(웹의 `Dearby 앱에 저장`)는 사용자가 저장을 고른 것이므로 지금처럼 바로 저장하고 받은 명함 상세를 연다. 두 입구는 링크 해석 하나를 함께 쓰고 진입 방식만 다르다.
  - 그 밖의 모양은 무시한다(앱만 열린다).
- **웹 명함 페이지의 `Dearby 앱에 저장`(dearby://)은 유지**: iOS는 같은 도메인 안 이동과, 사용자가 브라우저로 열기를 고른 뒤에는 유니버설 링크를 열지 않는다. 그때의 통로다.
- **활동 공유**: 활동 상세 상단 공유 버튼은 공식 안내 링크 대신 `<웹 origin>/activities/<id>`를 보낸다(앱의 웹 origin 설정, 캘린더 링크와 같은 주소). 활동이 불러와지면 늘 보인다. iOS `ShareLink(item: url, subject: 활동 이름)`, Android `ACTION_SEND` text/plain chooser(본문 = URL, `EXTRA_TITLE`·`EXTRA_SUBJECT` = 활동 이름). 접근성 라벨 `활동 공유`. 공식 안내 링크는 상세 본문의 출처 행에 그대로 있다.
- **오픈그래프(웹)**:
  - 공통: `metadataBase` = 웹 origin, `og:site_name` `Dearby`, `og:locale` `ko_KR`, `twitter:card` `summary_large_image`, 기본 이미지 1200×630(앱 아이콘과 `Dearby` 글자, 밝은 배경).
  - `/activities/:id`: 서버에서 `GET /v1/activities/:id`로 메타를 만든다. `og:title` = 활동 이름, `og:description` = `<프로그램 이름> · <모집 문구>`(모집 중 `M월 d일 마감`, 모집 예정 `M월 d일 모집 시작`, 그 밖은 `모집 마감`; 장소가 있으면 ` · <장소>`), `og:image` = 활동별 1200×630 생성 이미지(`next/og`, Pretendard: 프로그램 로고가 있으면 로고, 활동 이름, 프로그램·주최, 모집 기간). 조회 실패·404면 기본 메타. 1시간 캐시.
  - `/cards/:id`: **개인정보를 미리보기에 싣지 않는다**. 고정 `og:title` `Dearby 명함`, `og:description` `Dearby에서 명함을 확인하고 저장하세요.`, 기본 이미지. 이름·사진·소속·연락처는 넣지 않는다(메신저 미리보기 서버가 캐시해 QR 코드 없이도 퍼진다). `robots noindex` 유지.
- **하지 않는 것**: 앱 안 카메라 스캔, 배부 단위 관리, 미설치 사용자를 스토어로 보내는 중간 페이지(앱이 없으면 웹 페이지가 그대로 열린다).

## 명함 표현 — 2026-10-18 사용자 결정

Mobbin 참고(Instagram 프로필 카드, Telegram·Luma 연락처 행, Mindvalley 직무 칩, Airbnb 자격 목록)를 바탕으로 명함 본문의 표현을 세 플랫폼에서 같게 맞춘다. 데이터·계약은 바뀌지 않는다(`Card` 그대로).

- **카드 면**: 흰 바탕, 모서리 20, 1px 구분선 색 테두리, 안쪽 여백 20. 웹은 최대 너비 480px 가운데 정렬. 명함은 "한 장의 카드"로 보이고 행동 버튼(고치기·저장)은 카드 바깥 아래에 둔다.
- **머리**: 가운데 정렬. 이니셜 아바타 56(민트 바탕·청록 글자) → 이름(title 굵게) → 직무 칩(`job`을 ` · `로 나눈 각 항목을 옅은 배경 칩으로, 없으면 생략) → 소속(가장 최근 승인 이력의 조직 이름, 보조색 작은 글자, 없으면 생략).
- **소개**: 2026-10-26 폐지(절 자체를 그리지 않음, 이미 발행된 명함의 옛 본문도). 이름 옆 소개 링크가 대신한다(아래 "소개 링크·소개 글 폐지").
- **연락처**: 절 제목 `연락처`. 행마다 왼쪽 종류 아이콘(전화·메일·카카오톡·Instagram·GitHub·Behance), 라벨(보조색 작은 글자: `label`이 있으면 그것, 없으면 종류 이름), 값(본문색), 열 수 있으면 오른쪽 외부 링크 아이콘. 행 전체가 눌려 기존 동작(전화·메일·링크 열기)을 한다. 연락처가 없으면 `공개된 연락처가 없어요`.
- **활동 이력**: 절 제목 `활동 이력`. 행마다 기간(보조색 작은 글자, `2026.03 – 진행 중`), 제목(굵게, 조직 · 프로그램 · 활동), 역할과 인증 배지(`확인됨`/`미확인`, 기존 배지). 설명이 있으면 아래 한 줄. 이력이 없으면 `공개된 활동 이력이 없어요`.
- **내 명함 탭**: 가로 스크롤 타일 대신 2열 격자. 첫 칸 `+ 새 명함`, 각 칸은 명함 이름(굵게)·프로필 이름·직무 한 줄. 선택한 칸은 민트 바탕·청록 테두리. 격자 아래에 선택한 명함의 카드(위 규칙)와 `이 명함 고치기` 버튼, 그 아래 위험 색 `이 명함 지우기`(확인 대화상자 "공개 링크도 더 열리지 않아요. 되돌릴 수 없어요." → `DELETE /v1/cards/:id`, 2026-10-18 사용자 요청 "내 명함 삭제가 없어"), 그리고 `QR 보이기`("명함 교환"). 지운 뒤 다음 명함(가장 새 것)을 고르고, 마지막 명함이면 첫 명함 안내로 돌아간다. 명함이 없을 때의 안내 카드는 유지.
- **받은 명함 상세**: 같은 카드. 카드 위 받은 맥락 줄과 카드 아래 `내 명함도 건네기`는 "명함 교환"(2026-10-19)을 따른다.
- **웹 `/cards/:id`**: 같은 카드 구성, 저장 버튼은 카드 아래. `/saved` 목록은 바꾸지 않는다.
- 색·여백·글꼴은 기존 토큰(청록 `#007F80`, 민트 `#F0FCFA`, 구분선 `#E3E8EA`, 보조 `#657078`, Pretendard)만 쓴다. 동작 줄이기 영향 없음(애니메이션 없음).

## 내 활동 진행 트랙 — 2026-10-18 사용자 결정

신청한 활동의 선발·진행 단계를 `내 활동` 탭에서 한눈에 본다(사용자 제안, "NEXT STEP" 참고 이미지의 세그먼트 트랙 방식). 별도 탭은 두지 않고 `내 활동` 탭을 바꾼다. iOS·Android 공통.

### 단계 만들기

- 활동의 `schedules`에서 단계를 만든다. APPLICATION 슬롯(모집 마감)은 맨 앞의 `지원` 단계, ACTIVITY/APPLICATION 슬롯이 아닌 일정(수집된 세부 일정과 운영자 지정 일정)을 시작 시각·시작 날짜 순으로 정렬한 것이 가운데 단계들, ACTIVITY 슬롯(활동 기간)은 맨 뒤의 `활동` 단계다(제목이 있으면 그 제목). 세부 일정이 없는 활동도 `지원 → 활동` 두 단계를 가지므로, 지원 마감이 지나도 활동이 끝나기 전까지는 `진행 중`에 남는다. 날짜가 없는 단계는 날짜 있는 단계 뒤에 API 순서로 둔다. 종류(`kind`)는 아이콘·배지 문구에 쓴다: DEADLINE `마감`, ANNOUNCEMENT `발표`, INTERVIEW `면접`, EVENT는 일정 제목.
- 날짜가 없는 단계(문구만)는 순서를 유지하되 D-day를 계산하지 않고 `날짜 미확인`으로 보인다. 단계가 하나도 없는 활동은 지금처럼 `신청함`/`참여 확정` 표시만 보인다(2026-10-22: 참여 확정은 없앴고 일정 상태로 추론한다 — "내 활동 서버 저장" 절).

### 현재 단계와 사용자 표시

- 자동 판정: 오늘(서울 시간) 기준으로 끝나지 않은 가장 이른 단계가 현재 단계다. 끝난 단계는 지난 것으로 본다.
- 사용자 표시: 단계마다(2026-10-26부터 **선발 단계만**, 아래 절) `통과`·`대기`·`탈락`을 직접 표시할 수 있다(기본 `대기`). `통과`로 표시하면 그 단계는 지난 것으로 보고 다음 단계가 현재가 된다. `탈락`이면 활동이 `끝난 활동` 묶음으로 내려간다. 표시는 사용자가 스스로 남기는 것이며 주최 측 결과가 아니라는 문구를 상세에 둔다(참여 확정 표시와 같은 원칙).
- 저장: 신청 표시와 같이 기기 안에만 둔다(앱 실행 간 유지 여부도 신청 표시와 같은 규칙을 따른다). 서버·계정 동기화는 이번 범위가 아니다.

### 통과 여부는 선발 단계에만 — 2026-10-26 사용자 결정 (대체됨)

> 같은 날 아래 "선발 과정·활동 일정 분리"로 대체됐다. 선발 단계 판정은 서버의 `Schedule.phase`로 한다. 이 절의 추론은 서버 판정 규칙(필드가 비었을 때)과 `phase`가 없는 옛 서버 응답의 앱 대체 판정으로만 남는다.

- 통과·대기·탈락(일정의 `statuses` 선택)은 **선발 단계**에만 둔다. 그 밖의 단계는 날짜와 `예정`/`지남`(시간 판정)만 보이고 선택지가 없다.
- 선발 단계: `지원`(모집 슬롯), 세부 일정 중 `kind`가 `DEADLINE`(서류 등 마감)·`ANNOUNCEMENT`(결과 발표)·`INTERVIEW`(면접)인 것, 그리고 `EVENT`라도 **어드민이 그 일정의 상태를 직접 지정한 것**(`statuses`에 `id`가 있는 항목이 있음 — 서버가 채운 기본 세 개는 `id`가 없다). 활동 기간(`activitySchedule`) 단계는 늘 선발 단계가 아니다.
- 이유·한계: 수집 규칙(`WORKFLOW.md`)이 코딩테스트·과제 같은 테스트를 행사·교육과 같은 `EVENT`로 넣어 `kind`만으로는 구분되지 않는다. 테스트 일정에 통과 표시가 필요하면 어드민이 그 일정에 상태(대기·통과·탈락)를 지정한다. 서버에 선발 여부 필드(예 `selection: boolean` 또는 `kind: TEST`)를 두면 이 추론을 대체할 수 있다(조율 세션 판단, 이번 변경은 앱만).
- 판정: 선발 단계가 아닌 단계는 저장된 표시를 읽지 않는다(지우지도 않는다 — 기본 `대기`로 본다). 그래서 `통과`로 넘어가거나 `탈락`으로 끝나는 것은 선발 단계의 표시뿐이다. `참여 중`은 선발 단계가 모두 `통과`이고 그 뒤에 다른 단계(적어도 활동 기간)가 남았을 때다.
- iOS 2026-10-26 구현(`ActivityStages.isSelection`; 같은 날 `phase`가 없을 때의 대체 판정으로만 남음). Android는 같은 규칙으로 따로 구현한다.

### 화면

- 목록 행: 활동 이름(굵게) + 프로그램 · 조직(보조색) / 아래에 세그먼트 트랙: 지난 단계는 채움(청록), 현재 단계는 빗금 또는 강조 테두리, 다음 단계는 옅은 회색. 트랙 아래 단계 이름(현재 단계는 굵게, 길면 말줄임). 오른쪽 끝에 배지 `D-3 · 면접`(현재 단계의 시작까지 남은 날과 종류/제목. 진행 중인 날은 `D-DAY`, 모든 단계가 지났으면 `지남`, 탈락 표시면 `탈락`(위험 색), 날짜 없으면 `날짜 미확인 · 종류`). 단계가 4개를 넘으면 트랙은 4칸까지만 보이고 `+N`을 붙인다.
- 묶음: `진행 중`(기본), `끝난 활동`(탈락 표시했거나 모든 단계가 지난 활동). 끝난 활동은 접힌 묶음으로 아래에 둔다.
- 상세(행을 누름) — 2026-10-18~10-26 판: 세로 타임라인. 단계마다 종류 아이콘·이름·날짜(또는 문구)·상태 칩과 `통과`/`대기`/`탈락` 선택(선발 단계만; 그 밖의 단계는 칩이 `예정`/`지남`). 맨 위에 활동 이름과 활동 상세로 가는 버튼, 맨 아래에 "표시는 내가 남긴 것이며 주최 측 결과가 아니에요" 안내. 날짜가 있는 단계는 기존 `겹치는 시간 확인`을 그 단계 시각으로 열 수 있다.
- **진행 단계 화면 정리 — 2026-10-26 사용자 결정**("진행단계 페이지가 너무 난잡한데 활동탐색 카드처럼 깔끔하게", "마진이 너무 크고 항목별 경계가 뚜렷하지 않아. 개수가 적어도 스크롤이 길고 한눈에 파악이 어려워"): 위 타임라인을 대신한다. 정보는 지우지 않고 배치·표현만 바꾼다.
  - 플랫폼 기본 목록(iOS `List` `.insetGrouped`, Android 같은 역할의 Material 목록)의 기본 행 높이·간격·구분선을 쓴다. 직접 만든 큰 패딩·카드 테두리·연결선은 없다. 섹션마다 작은 머리글로 경계를 둔다.
  - `활동 정보`: 발견 활동 카드(제목·상태 배지·`모집`·`활동`·`장소`, 하트·종)를 그대로 쓰고 누르면 활동 상세. 태그 칩 줄은 2026-10-26 사용자 결정으로 이 카드에서만 빼고, 발견·즐겨찾기 카드는 그대로다(iOS: 카드에 태그를 넘기지 않음). 프로그램 · 조직은 섹션 아래 보조 줄.
  - `진행 단계`: 머리글 오른쪽에 지금 상태 배지(목록 행과 같은 `D-3 · 면접`/`참여 중`/`지남`/`탈락`). 단계마다 한 줄: 종류 아이콘(지난 단계 보조색, 현재 청록, 다음 옅은 색, 탈락 위험 색) + 이름(현재 단계는 굵게) + 아래 줄 `날짜 · 지남/진행 중/예정`(진행 중은 선발 단계만, 기존 칩 규칙). 오른쪽에는 선발 단계만 표시한 상태(`대기`/`통과`/`탈락` 또는 어드민 이름; 통과 청록, 탈락 위험 색). 일정 블록에 시간표가 있으면 상태 왼쪽에 시간표 아이콘(보조색, 2026-10-26). 고를 것이나 할 일이 있는 행을 누르면 메뉴: `시간표 보기`(시간표 있는 블록만, 맨 위), 선발 단계의 상태 선택, `캘린더에 추가`, `겹치는 시간 확인`(그 단계 시각). 안내 "표시는 내가 남긴 것이며 주최 측 결과가 아니에요."는 섹션 아래 보조 줄.
  - 별도 `시간표` 섹션은 2026-10-26 같은 날 단계 행 아이콘·메뉴 `시간표 보기`로 통합되며 없어졌다(아래 "트랙·세션 시간표" 내 활동).
  - 맨 아래 섹션에 `내 활동에서 빼기`(destructive 텍스트 버튼).
  - 목표: 단계가 5개 이하면 카드부터 진행 단계 끝까지 한 화면에 들어온다.
- 검색·정렬: 기존 내 활동 검색 규칙 유지. 정렬은 다음 단계가 가까운 순, 날짜 없는 활동은 뒤.
- 접근성: 트랙은 하나의 요소로 "5단계 중 3단계 1차 면접 진행 중"처럼 읽힌다. 동작 줄이기에서 트랙 애니메이션 없음.

## 선발 과정·활동 일정 분리 — 2026-10-26 사용자 결정

사용자: "선발 프로세스랑 실제 활동이랑 분리하고 싶어" → 추천안 승인 "그렇게 해주세요 내활동페이지 프로세스에도 반영해주세요". 일정마다 선발 과정인지 활동 일정인지를 서버가 내려 주고, 활동 상세와 내 활동 진행 단계가 두 섹션으로 나눠 보인다. 위 "통과 여부는 선발 단계에만"의 `kind`·상태 지정 추론을 대체한다.

### 데이터

- `Schedule.phase: "SELECTION" | "ACTIVITY"`(공개 Activity의 `application`·`activitySchedule`·`schedules[]`, 어드민 일정 응답 모두). 응답에는 늘 값이 있다(null 없음).
  - `SELECTION`(선발 과정): 붙기 전의 단계 — 서류 접수·마감, 코딩테스트·과제, 면접, 결과·합격자 발표 등 합격자 OT 전까지.
  - `ACTIVITY`(활동 일정): 붙은 뒤의 활동 — OT·오리엔테이션, 세션·교육·과정 단계, 행사 본 일정, 수료·데모데이. 선발이 없는 컨퍼런스·행사의 일정도 활동이다.
- 고정: `application`(모집 슬롯)은 늘 `SELECTION`, `activitySchedule`(활동 기간 슬롯)은 늘 `ACTIVITY`. DB 제약(V62 `schedules_slot_phase_check`)이 슬롯 행에 다른 값을 막는다.
- 저장: `schedules.phase varchar(20) null`(check `SELECTION`·`ACTIVITY`, V62). 값이 없는 행은 서버가 읽을 때 옛 판정으로 채운다: `kind`가 `DEADLINE`·`ANNOUNCEMENT`·`INTERVIEW`이거나 운영자가 상태를 지정한(`schedule_statuses` 행이 있는) 일정은 `SELECTION`, 그 밖은 `ACTIVITY`. 서버 구현 `SchedulePhase.resolve`.
- V62 채우기(조율 세션 결정 2026-10-26): 슬롯 행과 `DEADLINE`·`ANNOUNCEMENT`·`INTERVIEW` 세부 일정만 채우고, 그 밖의 세부 일정(`EVENT` 등)은 null로 남긴다. 그래서 `EVENT` 일정은 운영자가 상태를 지정하면 예전 앱 판정처럼 저절로 `SELECTION`이 된다. 운영자가 어드민에서 `phase`를 직접 정한 행만 저장값이 우선이고(상태 지정보다 앞선다), 수집기가 `phase`를 준 행도 저장값으로 남는다.
- 어드민·수집: 세부 일정 POST·PATCH가 `phase?`를 받는다(고치면 기존 규칙대로 `curated`라 재수집이 덮어쓰지 않는다). 수집기 제출 `schedules[].phase`(모르면 null), 큐레이션 가져오기 `schedules[]` 항목도 `phase?`. 모집·활동 기간 행의 `phase`는 고칠 수 없다(400).

### 화면(iOS·Android·웹)

- **활동 상세**: 일정을 `선발 과정`과 `활동 일정` 두 섹션으로 나눈다(앱의 `행사 일정` 섹션, 웹 활동 상세의 일정 목록을 이 두 섹션으로). `선발 과정`은 모집 행(기존 "모집 날짜 표기" 규칙) + `SELECTION` 세부 일정, `활동 일정`은 활동 기간 행 + `ACTIVITY` 세부 일정. 섹션 안의 순서·행 모양·`캘린더에 추가`·`겹치는 시간 확인`은 그대로다. 시간표 블록(`hasTimetable`, "트랙·세션 시간표")은 `phase`와 상관없이 `활동 일정` 섹션에 둔다.
- **내 활동 진행 단계 화면**("진행 단계 화면 정리" 위에서): `진행 단계` 섹션을 `선발 과정`과 `활동 일정` 두 섹션으로 나눈다. 행 모양·메뉴는 그대로이고, 지금 상태 배지(`D-3 · 면접`/`지남`/`탈락`)는 첫 섹션 머리글 오른쪽에 둔다(`참여 중`은 아래 규칙대로 `활동 일정` 머리글에만). `시간표` 섹션은 `활동 일정` 다음에 둔다.
  - 통과·대기·탈락(일정의 `statuses` 선택)은 `SELECTION` 단계에만 붙인다. `ACTIVITY` 단계는 날짜와 `예정`/`지남`만 보이고 선택지가 없다(기존 판정: 선발이 아닌 단계는 저장된 표시를 읽지 않고 지우지도 않는다).
  - 선발 단계가 모두 `통과`이면(선발 단계가 하나도 없는 활동은 해당 없음) `활동 일정` 섹션을 "참여 중"으로 강조한다: 첫 머리글의 상태 배지는 비우고 `활동 일정` 머리글에 `참여 중` 배지(청록) 하나만 둔다(조율 세션 결정 2026-10-26, 같은 글자가 두 번 보이지 않게; iOS #421·Android #420). 지금 상태 배지 `참여 중` 규칙(선발이 모두 통과이고 뒤에 활동 단계가 남음)과 같은 조건이다.
  - 탈락(선발 단계의 `FAILED`)이면 기존대로 `끝난 활동`이고 `활동 일정` 섹션은 강조하지 않는다.
- 내 활동 목록의 세그먼트 트랙·배지·정렬·`끝난 활동` 판정은 바뀌지 않는다(단계 순서는 기존 "단계 만들기" 그대로, 선발 단계 판정만 `phase`로).
- 한쪽이 비면 그 섹션을 숨긴다(예: 선발이 없는 컨퍼런스는 `활동 일정`만).
- 옛 서버(응답에 `phase` 없음): 앱은 위 "통과 여부는 선발 단계에만"의 판정(`kind`·상태 지정)으로 나눈다. 모집 슬롯은 선발, 활동 기간 슬롯은 활동.
- 접근성: 섹션 머리글은 제목(heading)으로 읽힌다. "참여 중" 강조는 색만으로 나타내지 않고 배지 글자를 둔다.
- iOS 2026-10-26 구현: `ActivityStageModel.phase`(없으면 nil) → `ActivityStages.isSelection`(phase 우선, 없으면 옛 판정), 섹션 소속 `ActivityStage.inActivitySection`(시간표 블록은 늘 활동 일정). 활동 상세 `ActivityInformationView`는 `행사 일정` 제목 대신 같은 모양의 `선발 과정`·`활동 일정` 제목 두 개(heading 특성), 진행 단계 `ActivityProgressPage`는 두 `Section`. 내 활동 목록 행·배지·정렬은 그대로.

## 오늘 내 행사 카드 — 2026-10-26 사용자 결정

사용자(우아콘 10/28 전): 컨퍼런스 당일 경험을 앱의 첫 인상으로 삼는다. 발견 탭 맨 위(맞춤 대기열 행 위)에 `오늘 내 행사` 카드를 두고, 받은 명함에 어느 세션에서 교환했는지를 보인다. 이 절은 iOS 구현(feat/today-card-ios)이 쓰고 Android(feat/today-card-android)가 같은 규칙을 따른다. 서버 변경은 없다.

### 대상과 고르기

- 내 활동(진행 트랙)에 있는 활동 중 **활동 일정 블록**이 **오늘**이거나 **7일 안**(서울 날짜 기준 1~7일 뒤)에 시작하는 활동. 끝난 활동·탈락 표시 활동·단계 없는 활동은 뺀다.
- 활동 일정 블록 = "선발 과정·활동 일정 분리"의 `활동 일정` 섹션에 드는 일정(`phase: ACTIVITY` 세부 일정, `activitySchedule`, 그리고 `phase`와 상관없이 시간표 블록; `phase`가 없는 옛 서버는 옛 판정) 중 시작이 있는 것. **길이 7일을 넘는 블록은 세지 않는다**(부트캠프의 몇 달짜리 종일 활동 기간이 매일 카드를 띄우는 것을 막는다; 구현 판단, 조율 세션이 바꿀 수 있다). 반복 일정은 첫 회만 본다.
- 오늘 판정은 블록이 덮는 서울 날짜(시각 블록은 시작 날~끝 날, 종일 블록은 `startDate`~`endDateExclusive` 전날)에 오늘이 들어가는지. 당일 모든 블록이 끝난 뒤에도 그 날은 오늘로 남는다(끝난 뒤 명함 교환이 이어진다).
- 여러 활동이면 오늘인 것(가장 이른 블록) → 가장 가까운 D-n 순으로 **하나**만 보이고 나머지 수를 `외 n개`로 적는다.
- 비로그인(또는 동의 전)·해당 없음·검색 중·목록 첫 로딩 중이면 카드가 없다. 로그아웃 상태에서 메모리에만 있는 내 활동 표시로는 띄우지 않는다.
- 데이터는 이미 읽는 내 활동(`GET /v1/me/activities` + 활동 조회)과 세션 찜(`GET /v1/me/session-favorites`)을 재사용하고 **추가 서버 호출이 없다**. 시간표 본문은 시트를 열 때 기존대로 읽는다.

### 모양과 동작

- 발견의 다른 카드와 같은 카드(iOS secondarySystemGroupedBackground·모서리 20). 머리에 청록 채움 배지 `오늘`/`D-n` 다음 활동 이름(접근성은 `오늘 · 우아콘 2026`으로 읽음), 오른쪽에 `외 n개`.
- **오늘**: 본문은 **다음 세션** — 이 활동의 내 시간표(찜한 세션) 중 오늘 `endAt > now`인 첫 것, `14:50 · 트랙 B · 그랜드볼룸 B · 세션 제목`(블록 시간대, 트랙·방은 있을 때만), 진행 중이면 `지금 · …`. 찜이 없으면 **진행 중 또는 다음 블록**(끝이 지난 블록만 남으면 마지막 블록) `오후 세션 13:50–17:30`(종일이면 `활동 · 종일`). 카드를 누르면 그 블록의 시간표 시트("트랙·세션 시간표" 모달 시트와 같은 것)가 **그 블록에 내 시간표 세션이 있으면 `내 시간표` 탭, 없으면 `전체`**로 열린다. 블록에 시간표가 없으면 진행 단계 화면. 보조 버튼 `명함 QR`(SF `qrcode`)은 명함 탭 › 내 명함의 **QR 보이기**를 선택한 명함으로 열고 활동 칩을 이 활동으로 미리 고른다(명함이 없거나 로딩 실패면 그 상태 화면만 보인다).
- **7일 안**: `D-2` 배지 + 활동 이름, 본문은 첫 블록 제목과 날짜·시간(`오전 세션 · 10월 28일 (수) 10:50 ~ 12:30`, 기존 "일정 표시 우선순위" 표기). 누르면 진행 단계 화면(iOS는 `내 활동으로`와 같이 내 활동 탭으로 옮겨 push). `명함 QR`은 없다.
- 카드는 분 단위로 다시 그려 `지금`과 다음 세션이 바뀐다. 접근성 식별자 `today-card`·`today-card-qr`(UI 테스트의 `activity-` 접두어와 겹치지 않게).
- 사용 통계 `today_card_tap` — `target` = `timetable`·`progress`·`qr`, 활동 매개변수(`activity_id`·`field`·`category`). analytics-v1.md "메뉴별 접근률".

### 받은 명함의 교환 세션 맥락

- 서버 규칙·필드는 위 "명함 교환" › "교환 세션 맥락"(feat/receipt-session-context)이 기준이다. iOS는 `ExchangeContext`의 `scheduleTitle`·`sessionTitle`·`trackName`·`room`을 선택 디코딩한다(없으면 null; `scheduleId`·`sessionId`는 아직 쓰지 않는다).
- iOS 화면: 받은 명함 상세의 `{프로그램} {활동}에서 받은 명함` 줄은 값이 하나라도 있으면 `우아콘 2026 · 오후 세션 · 트랙 B · 그랜드볼룸 B`처럼 **있는 값만 ` · `로 이어** 첫 줄에 두고 세션 제목은 둘째 줄, `2026.10.28에 받음`은 셋째 줄이다(사용자 지시 "세션 제목은 둘째 줄"; 서버 절의 한 줄 예시는 같은 내용의 줄 나눔). 목록 행은 프로그램 칩을 그대로 두고 같은 첫 줄을 셋째 줄(캡션)로 보인다. 값이 없으면 기존 `…에서 받음 · 날짜` 그대로다. 공유받은 명함 보기(`/cards/:id` 링크)는 링크에 세션 정보가 없어 그대로다.
