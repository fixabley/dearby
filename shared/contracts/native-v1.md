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

웹은 same-origin Next 서버 프록시를 사용한다. API origin은 서버 env `DEARBY_API_ORIGIN`; 브라우저는 API 키를 받지 않는다. Next와 API에 동일한 서버 전용 `GUEST_PROXY_SECRET`(최소 32자)을 root가 배포 시 설정한다. 모든 guest API에는 Next가 `X-Guest-Proxy-Key`를 넣고, 쿠키가 있으면 `X-Guest-Token`을 넣는다. 클라이언트가 보낸 동명 헤더는 전달하지 않고 서버 env/cookie로 다시 구성한다. API는 Cookie, Origin, forwarded IP를 방문자 인증으로 신뢰하지 않는다.

| 메서드·경로 | 성공 응답 | 의미 |
| --- | --- | --- |
| GET /v1/guest/cards | 200 `{items: GuestItem[]}` | 자기 세션의 미철회·미회수 공개 명함 목록(2026-10-19부터 GuestItem, 아래 "명함 교환") |
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

## 명함 공유 기록과 게스트 공유 정보 저장 — 2026-10-06 승인, 2026-10-18 삭제

2026-10-18 사용자 결정으로 명함 공유 기능을 서버·웹·앱에서 모두 지웠다. CardShare·GuestShare 모델, `POST /v1/cards/:id/shares`, `GET /v1/shares/:id`, `PUT /v1/guest/shares/:id`, `GET /v1/guest/cards`의 `shares`, 웹 `/s/:shareId`와 `/saved`의 `전체 | 활동별` 보기는 더 없다. `GET /v1/guest/cards`는 `{items: Card[]}`만 돌려준다. 운영 DB의 `card_shares`·`guest_card_shares` 테이블은 남아 있으며 삭제 마이그레이션은 별도 승인 PR로 한다. 당시 계약 전문은 [삭제 전 문서](https://github.com/fixabley/dearby/blob/6636e9a6/shared/contracts/native-v1.md#명함-공유-기록과-게스트-공유-정보-저장--2026-10-06-승인)에 있다.

## 모바일 실제 연결 경계 — 2026-10-06 사용자 결정 반영

사용자 결정: "QR 공유는 실제 동작까지", "활동 신청이 쉽고 명함 제작·교환이 간결해야 한다", "비로그인 사용자는 세션 기준으로 저장이 유지되어야 한다", "유니버설 링크를 적용한다". 이 절은 iOS·Android 오프라인 프로토타입 범위([모바일 프로토타입](../../docs/context/mobile-ui-prototype.md)) 중 아래 항목만 실제 서비스로 바꾼다. 나머지 화면은 예시로 남는다. 근거 조사: `docs/research/activity-and-qr-friction-2026-10-06.md`(PR #94).

### 실제로 바꾸는 것

| 영역 | 동작 | 사용 API |
| --- | --- | --- |
| 발견·활동 상세 | 공개 카탈로그를 보여 주고, 신청 CTA는 활동의 공식 신청 URL을 외부 브라우저로 연다. 실패하면 오류와 다시 시도를 보여 주며 예시 활동을 섞지 않는다. | GET /v1/activities (2026-10-14부터, 아래 "활동 조회") |
| 로그인 | 패스키(2026-10-10 결정, 아래 "패스키 로그인"). 명함 발행과 받은 명함 보기를 할 때만 요구하고, 발견·신청은 로그인 없이 쓴다. access·refresh 토큰은 OS 보안 저장소에 둔다. | /v1/auth/* |
| 명함 제작 | 프로필 입력과 공개할 연락처·이력 선택을 한 흐름으로 마치고 바로 발행한다. 수정은 새 발행본을 만든다(기존 스냅샷 규칙 유지). | /v1/profile, POST /v1/cards |
| ~~QR 공유 · 받기~~ | 2026-10-18 삭제. QR 표시·OS 공유·앱 안 스캐너·유니버설 링크/App Links·공유 명함 화면은 없다. QR 탭 자리에는 `내 명함` 탭(내 명함 목록·만들기·고치기)이 있다. 당시 계약은 [삭제 전 문서](https://github.com/fixabley/dearby/blob/6636e9a6/shared/contracts/native-v1.md#실제로-바꾸는-것) 참고. | — |

- 2026-10-18부터 앱은 `/s/*` 링크를 받지 않고, 웹도 `/.well-known/apple-app-site-association`·`assetlinks.json`을 만들지 않는다(패스키용 `wid.io.kr` 정적 파일은 별도이며 유지). #90·#91은 이 결정으로 닫는다.

### 비로그인 사용자의 저장 유지

2026-10-06 사용자 결정: 비로그인 사용자는 앱을 설치하지 않았고 로그인하지 않은 사람이다. 공개 명함 링크 `/cards/:id`가 기본 브라우저에서 열린다(2026-10-18까지는 QR의 `/s/<shareId>`였다). 서버가 만료 없는 게스트 세션 ID를 발급하고, 저장 데이터는 DB에 두며 API가 세션 ID로 조회한다. 브라우저에는 세션 ID 쿠키만 둔다.

- 웹: 위 "웹 비로그인 명함 저장" 계약의 서버 게스트 세션(`__Host-dearby_guest` 쿠키)을 그대로 쓴다. 서버 세션은 만료되지 않는다. 브라우저는 쿠키 수명을 최대 400일로 제한하므로, 방문할 때마다 Max-Age를 갱신한다(기존 규칙). 1년 넘게 방문하지 않거나 사용자가 쿠키를 지우면 접근이 끊긴다.
- 홈 화면 추가: Android Chrome은 바로가기·설치 모두 Chrome과 같은 쿠키를 쓴다. iOS는 '웹 앱으로 열기'가 기본으로 켜져 있고 사이트가 바꿀 수 없어서, 그대로 추가하면 Safari와 쿠키가 분리된다(2026-10-06 iOS 26.5 시뮬레이터 확인). 사용자 결정으로 아래 "홈 화면 세션 잇기"를 둔다. manifest `display`는 Android 설치 버튼을 위해 `standalone`을 유지한다. iOS는 공유 메뉴에서 직접 추가해야 하므로 1번 클릭 설치는 불가하고 안내로 제공한다.
- 홈 화면 세션 잇기(2026-10-06 사용자 결정): 홈 화면 앱이 처음 열릴 때 1회용 코드로 Safari와 같은 게스트 세션 ID 쿠키를 받는다. 이후 두 곳은 같은 세션을 쓰며, 한쪽에서 세션을 지우면 다른 쪽도 끊긴다.
  - 발급: 웹 manifest는 요청마다 만든다. 게스트 쿠키가 있고 저장한 명함이 1개 이상이면 Next가 `POST /v1/guest/handoffs`(게스트 프록시)로 코드를 받아 `start_url`을 `/saved?handoff=<code>`로 넣는다. 아니면 `/saved`. manifest 응답은 `Cache-Control: private, no-store`와 `Vary: Cookie`이다.
  - API `POST /v1/guest/handoffs`: 기존 토큰 필요, 201 `{code, expiresAt}`. 코드는 32바이트 이상 무작위 base64url, 수명 10분, 1회용. 세션당 유효 코드는 1개이며 새로 발급하면 이전 코드는 무효다. DB에는 코드의 SHA-256 digest·세션·만료·사용 시각과, 발급 요청의 게스트 토큰을 코드에서 파생한 키(HKDF)로 AES-256-GCM 암호화한 값만 둔다. DB만으로는 토큰을 복원할 수 없고, 사용·만료 시 암호문을 지운다.
  - API `POST /v1/guest/handoffs/redeem`: body `{code}`, 게스트 프록시, 토큰 없이 호출. 200 `{guestToken}`(코드로 복호화한, Safari와 같은 토큰). 없음·만료·사용됨·세션 폐기는 모두 404 `NOT_FOUND`(구분하지 않음). 성공하면 즉시 사용 처리한다. 요청 제한은 게스트 카운터와 같고, 실패도 센다.
  - 웹 `/saved?handoff=<code>`: 게스트 쿠키가 없을 때만 교환한다. 성공하면 쿠키를 설정하고, 성공·실패 모두 303으로 쿼리 없는 `/saved`로 보낸다. 이미 쿠키가 있으면 교환하지 않고 바로 보낸다. 페이지는 `Referrer-Policy: no-referrer`를 쓰고, 서버 로그에서 `handoff` 쿼리를 가린다.
  - 위험: 코드는 유효 시간 동안 게스트 세션 전체(저장한 명함)를 넘겨받는 열쇠다. 다른 기기에서의 사용을 막을 수 없다. 1회용·10분·digest 저장·요청 제한·manifest 캐시 금지로 줄인다. 마이그레이션은 운영 DB 변경이므로 별도 PR로 사용자 승인 뒤 병합한다.
- 앱의 게스트 세션·게스트 API는 만들지 않는다(2026-10-18 공유 삭제로 앱이 링크를 받는 경로도 없다).
- 2026-10-03 이전 기기에 남은 앱 저장 데이터는 읽거나 지우거나 이관하지 않는다.

### 연결 설정 — 도메인 기반, 환경값으로 지정

2026-10-06 사용자 결정: 모든 연결은 도메인 기반이며 환경값으로 지정한다. 코드에 운영 도메인·IP·포트를 고정하지 않는다.

| 환경값 | 쓰는 곳 | 의미 |
| --- | --- | --- |
| `DEARBY_API_ORIGIN` | 웹 서버, iOS·Android 빌드 | API origin(scheme+도메인, `/v1` 제외). 예: `https://wid.io.kr` |

2026-10-18 공유 삭제로 `DEARBY_WEB_ORIGIN`(앱)과 `DEARBY_APPLE_TEAM_ID`·`DEARBY_IOS_BUNDLE_IDS`·`DEARBY_ANDROID_PACKAGE`·`DEARBY_ANDROID_CERT_SHA256`(웹 `/.well-known` 연결 파일)은 쓰지 않는다.

- 운영·배포 빌드는 `https`와 도메인 host만 허용한다. IP 주소 host, `http`, 포트 지정, 경로가 있는 값은 빌드 또는 시작 시 실패시킨다. 개발 구성만 `http://localhost`·`127.0.0.1`·Android 에뮬레이터 `10.0.2.2`를 허용한다.
- iOS: 빌드 환경값을 Info.plist 키(`DearbyAPIOrigin`)로 넣는다. 값이 없으면 Release 빌드는 실패하고, Debug는 로컬 개발 기본값을 쓴다.
- Android: Gradle이 같은 환경값(또는 같은 이름의 Gradle 속성)을 읽어 `BuildConfig`로 넣는다. 값이 없으면 release 빌드는 실패한다.
- 웹은 요청이 들어온 자기 origin을 기준으로 동작하므로 웹 도메인 환경값이 따로 필요 없다. API 서버의 `HOST`·`PORT`는 내부 바인딩이며, 외부에서는 ingress 도메인으로만 접근한다.

### 앱에서 받은 공유 저장 (회원 wallet) — 2026-10-06, 2026-10-18 삭제

`PUT /v1/wallet/shares/:id`와 `GET /v1/wallet`의 `shares`(WalletShare)는 2026-10-18 삭제했다. `GET /v1/wallet`은 `{items: Receipt[]}`만 돌려주고, 새 Receipt를 만드는 경로는 현재 없다(받은 명함 탭은 기존 항목만 보인다). 운영 DB의 `wallet_shares` 테이블은 별도 승인 PR로 지운다. 당시 계약은 [삭제 전 문서](https://github.com/fixabley/dearby/blob/6636e9a6/shared/contracts/native-v1.md#앱에서-받은-공유-저장-회원-wallet--2026-10-06)에 있다.

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

- 처음 실행할 때 한 번만(2026-10-14 사용자 결정) 로고와 "패스키로 시작하기"·"둘러보기"가 있는 시작 화면을 보여 준다. 로그인은 선택이며, 로그인에 성공하거나 둘러보기를 누르면 다시 보이지 않는다. 이미 로그인되어 있으면 건너뛴다. 매 실행의 OS 스플래시에는 로고만 둔다.
- 로그인이 필요한 동작(명함 발행, 받은 명함 보기)에서 로그인 화면을 띄운다. "패스키로 로그인"이 기본이고, 패스키가 없거나 사용자가 고르면 "새 패스키로 시작"(가입)을 한다.
- access token이 만료돼 401을 받으면 refresh를 한 번 시도한다. refresh가 401로 거절되면 토큰을 지우고 로그인 화면으로 돌아간다. 네트워크 오류·5xx면 토큰을 남기고 그 요청만 실패시킨다(불안정한 연결에서 로그아웃되지 않게). refresh token이 회전하므로 동시에 여러 요청이 401을 받아도 refresh는 한 번만 한다.
- 로그아웃은 `/v1/auth/logout` 뒤 기기 토큰을 지운다. 네트워크 실패여도 기기 토큰은 지운다.
- 기존 이메일 로그인 화면·저장 토큰(`sessionToken`)은 쓰지 않는다. 이전 앱 저장 데이터는 읽거나 지우지 않는다는 저장소 원칙을 따른다.

## 프로필·명함 v2 — 2026-10-16 사용자 결정

TypeScript API를 지운 뒤 비어 있던 프로필·명함·지갑 경로를 Kotlin `api`가 다시 제공한다. 위 "모델"·"첫 수직 구현 API"의 Profile·Card 설명은 이 섹션이 대체한다. 오류는 ProblemDetail(400·401·403·404·409)이며 옛 422는 400이 됐다. id는 모두 UUID 문자열이고(조직·프로그램·활동·역할만 숫자), 연락처·이력 id는 클라이언트가 만든 UUID를 서버가 그대로 둔다.

### 프로필과 버전 이력

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| GET /v1/profile | Bearer | — | 200 Profile. 아직 없으면 빈 프로필(`version` 0) |
| PUT /v1/profile | Bearer, `If-Match: <version>` 선택 | `{name, jobs[], introduction, contacts[], histories[]}` | 200 Profile(`version`+1). `If-Match`가 현재 버전과 다르면 409 |
| GET /v1/profile/versions | Bearer | `cursor?`, `size?` | 200 `{items: [{version, createdAt}], nextCursor}` 최신순 |
| GET /v1/profile/versions/:n | Bearer | — | 200 `{version, createdAt, profile}` 그때의 프로필 |
| POST /v1/profile/versions/:n/restore | Bearer | — | 200 Profile. 그 버전 내용으로 새 버전을 만든다(이력은 지우지 않는다) |

- Profile: `{id(=사용자 id), name, jobs: ProfileJob[], job, introduction, contacts: Contact[], histories: History[], version, updatedAt}`. `job`은 승인된 직무 이름을 " · "로 이은 읽기 전용 문자열(명함의 `job`과 같다)이고 PUT에서는 무시한다.
- 직무(2026-10-17 사용자 결정, 이전 "직함" 문자열을 대체): 직무는 조직처럼 계층을 가진 활동 정보이고 프로필은 여러 개를 태그로 둔다. ProfileJob: `{id(클라이언트 UUID), job: {id, name} | null, jobName, approval: approved|pending|rejected, approvalReason}`. PUT에는 `{id, jobId?, jobName?}`를 보낸다(둘 다 보내면 `jobId`). 검색에 없으면 `jobName`만 적는 직접 입력이고 이력과 같은 규칙으로 관리자 승인 전(`pending`)에는 명함 `job`에 들어가지 않는다. 직접 입력 값이 그대로면 승인·거절 결과가 유지되고 바뀌면 다시 `pending`. 최대 10개, 같은 직무 중복 400. 검색은 `GET /v1/jobs`(아래 "이력"의 선택 창 검색과 같은 형식, `q?`, `parentId?`, `rootsOnly?`, `cursor?`, `size?`; 항목에 바로 위 직무 `parent: {id, name}`). V18 이전 버전 스냅샷의 `job` 문자열은 승인된 직접 입력 직무로 읽힌다.
- 프로필은 **데이터 입력**만 한다(2026-10-16 사용자 결정). `introduction`은 자기소개 글이고, 꾸미기(마크다운 템플릿)는 명함 제작에서 한다(아래 "템플릿").
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

### 명함·지갑

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| GET /v1/cards | Bearer | — | 200 `{items: Card[]}` 철회 안 한 내 명함, 만든 순 |
| POST /v1/cards | Bearer | `{name, description, template, contactIds[], historyIds[]}` | 201 Card. 내 프로필의 id가 아니거나 `approval`이 `approved`가 아닌 이력이면 400. `template`은 앱 내장 기본 템플릿(아래 "템플릿") |
| GET /v1/cards/:id | 없음 | — | 200 Card. 없거나 철회면 404 |
| DELETE /v1/cards/:id | Bearer(소유자) | — | 204 철회. 남의 것 403 |
| GET /v1/wallet | Bearer | — | 200 `{items: Receipt[]}` (2026-10-18부터 `shares` 없음; 2026-10-19부터 `context`·`reciprocal`·`expiresAt`, 아래 "명함 교환") |

- Card: `{id, ownerId, name, description, template, introduction, profileName, job, contacts: Contact[], histories: History[], createdAt}`. 발행 시점 스냅샷이며 프로필을 고쳐도 바뀌지 않는다. `template`은 발행할 때 보낸 마크다운 템플릿, `introduction`은 그것을 **채운 결과**(공개 본문)다. 이력에는 `evidence`가 없고 `verification`만 남는다. 공개 조회(`GET /v1/cards/:id`)에는 `template`을 싣지 않는다. (`webTemplate`·`webIntroduction`은 2026-10-18 같은 날 추가 뒤 삭제했다. 아래 "템플릿".)
- Receipt: 아래 "명함 교환"(2026-10-19)의 형태. 새 Receipt는 `PUT /v1/wallet/cards/:cardId`와 `reciprocate`가 만든다.
- 웹 비로그인 저장(`/v1/guest/*`, 홈 화면 세션 잇기)은 2026-10-17 Kotlin API에 다시 구현했다(위 "웹 비로그인 명함 저장"·"홈 화면 세션 잇기" 계약 그대로. 게스트 공유 정보 저장은 2026-10-18 삭제). 오류 본문은 다른 경로와 같은 ProblemDetail이며 웹 프록시는 상태 코드만 옮긴다(401 `GUEST_SESSION_INVALID`, 그 외 `REQUEST_FAILED`). API의 `GUEST_PROXY_SECRET`이 비어 있으면 `/v1/guest/*`만 503이다.
- 아직 없는 것: `POST /v1/wallet/import`, `POST /v1/exchanges`. 앱이 쓰지 않아 만들지 않는다.

### 템플릿 — 2026-10-18 앱 안 꾸미기 삭제

2026-10-18 사용자 결정: 명함 제작 화면의 실시간(렌더링 화면) 템플릿 편집기와 같은 날 추가한 "웹 버전"(`webTemplate`·`webIntroduction`, V20 열)을 모두 지웠다. 서버의 템플릿 채우기 구조는 남긴다.

- 앱은 명함 제작에서 이름·공개 연락처·이력만 고르고, `template`에는 **앱 내장 기본 템플릿**을 그대로 보낸다. 사용자가 템플릿을 고치는 UI는 없다. 제작 화면은 기본 템플릿을 채운 결과를 읽기 전용 미리보기로 보인다.
- 기본 템플릿은 `{{profile.introduction}}` 하나다(2026-10-18 사용자 결정 "이미 제공되는 정보인데 명함에 텍스트로 표시되는 거 지워줘"). 이름·직무·연락처는 카드 면(아래 "명함 표현")이 따로 그리므로 본문에 다시 넣지 않는다. 그 전 기본 템플릿(`# 이름 / 직무 / 소개 / - 라벨: 연락처`)으로 발행된 명함은 V27·V28이 같은 모양으로 정리했다(V28은 `# `·빈 줄이 없던 더 이른 기본 템플릿도 포함. 토큰만으로 된 기본 템플릿 모양이 아닌 옛 명함은 그대로).
- 토큰: `{{profile.name}}`, `{{profile.job}}`, `{{profile.introduction}}`, `{{contact:<연락처 id>}}`. 서버는 발행할 때 채운다. `{{contact:…}}`는 그 명함에 고른 연락처만 채우고, 고르지 않았거나 없는 연락처·모르는 토큰은 빈 문자열이 된다.
- 발행한 명함은 바뀌지 않는다. 명함을 고치면 앱이 새 명함을 발행하고 이전 명함은 사용자가 지울 때까지 남는다(공개 링크 `/cards/:id` 유지).
- 마크다운 부분집합(제목·굵게·기울임·목록·링크, 원시 HTML 금지, `https`·`mailto`·`tel` 링크만)과 10,000자·제어 문자 규칙은 그대로다. 기존에 발행된 명함의 `template`·`introduction`은 그대로 보인다.
- 웹 버전 삭제: V21로 `business_cards.web_template`·`web_introduction` 열을 지운다. 웹 `/cards/:id`는 모든 폭에서 `introduction`만 보인다.
- 삭제 전 계약(꾸미기 편집기·웹 버전)은 [`cb456363`](https://github.com/fixabley/dearby/blob/cb456363/shared/contracts/native-v1.md#템플릿-명함-꾸미기)에 있다.

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
- Activity: `{id, name, description, organization{id,name}, program{id,name}, officialUrl, imageUrl, application, activitySchedule, schedules[], eligibility}`. 2026-10-14 사용자 결정으로 원래 도메인 필드에 공식 안내 링크(`officialUrl`)와 이미지(`imageUrl`)만 더한다. 2026-10-18 `eligibility`(대상자 조건 JSON, 없으면 null)를 더한다(아래 "대상자 조건"). 모집 상태·참여 방식·확인 시각·장소·비용·대상·자격요건(JSON)·역할·신청 링크·안내 문구는 같은 `activities` 행(+`activity_roles`)에 있지만 운영자용 도메인 `ActivityCuration`이 다루고 공개 API는 내보내지 않는다. 같은 DB를 쓰는 운영자(admin) 모듈용이다.
  - 모집 여부는 `application`(모집 기간: 시작·끝 중 아는 쪽)으로 판단한다. 신청은 `officialUrl`을 연다.
  - 한 항목만으로 목록·상세를 그릴 수 있어 기관·프로그램을 따로 조회하지 않는다.
  - `application`(모집 기간, 없으면 null), `activitySchedule`(활동 기간·날짜 문구), `schedules[]`(개별 일정)는 Schedule이다.
- Schedule: `{id, title, kind, status, startAt, endAt, timeZone, startDate, endDateExclusive, dateLabel, address, conferenceUrl}`. 시각 일정은 `startAt`·`endAt` 중 하나 이상과 `timeZone`이 있고, 시각은 그 시간대 오프셋으로 표기한다(예: `2026-10-08T00:00:00+09:00`). 종일 일정은 `startDate`·`endDateExclusive`. 날짜 미정은 모두 null이고 `dateLabel`만 있다.

## 태그(얻을 수 있는 것) — 2026-10-19 사용자 결정

사용자: "프로그램이랑 활동에 태그가 달렸으면 좋겠어. 창업, 창의성, 개발지식, 프로젝트 개발, 협업, 멘토링처럼 사용자가 얻을 수 있는 요소들을 표현하게 해서 검색 결과에 같이 노출". 종류(category)가 "무엇인가"라면 태그는 "무엇을 얻는가"다.

- **어휘는 통제한다**: 자유 입력이 아니라 운영자가 관리하는 태그 사전 `tags(id, name 한글, name_en 영문 nullable, sort_order)`(V34). 사전은 어드민 편집 폼에서 **검색해 고르거나 바로 새 이름을 적어 추가**할 수 있다(2026-10-19 사용자 "편집시 직접 검색 및 직접입력"): 태그 선택은 검색형 다중 선택이고, 사전에 없는 이름을 적으면 저장 때 `POST /v1/admin/tags {name}`으로 만들어 연결한다(영문 이름은 비워 두고 뒤에 채운다, 띄어쓰기만 다른 같은 이름은 기존 태그로 합친다). 수집기는 영문 이름이 비어 있어도 한글 이름으로 고른다. 초기 사전: 창업 startup, 창의성 creativity, 개발지식 engineering, 프로젝트 개발 projectBuilding, 협업 collaboration, 멘토링 mentoring, 네트워킹 networking, 취업 career, 포트폴리오 portfolio, 발표 presentation, 글로벌 global, 장학·지원금 funding, 리더십 leadership, 문제해결 problemSolving. 어드민 `GET/POST/PATCH/DELETE /v1/admin/tags`(이름 변경은 연결 유지, 삭제는 연결도 지움). 공개 `GET /v1/tags` → `{items: Tag[]}`, Tag = `{id, name, nameEn}`.
- **연결**: `program_tags(program_id, tag_id)`, `activity_tags(activity_id, tag_id)`. 프로그램 태그는 운영자가 지정하고(`PATCH /v1/admin/programs/:id {tagIds, applyTagsToActivities?}`, 종류와 같은 일괄 규칙: 운영자가 태그를 지정하지 않은 활동에 합집합으로 더한다), 활동 태그는 수집기가 사전에서 고르고(지시문에 사전을 넘기고 사전 밖 이름은 버린다) 운영자가 바꾼다(`PATCH /v1/admin/activities/:id {tagIds}`, `POST`도 `tagIds?`, 운영자 지정은 `curated_fields`의 `tags`로 보호). 수집 결과 태그가 비면 프로그램 태그를 기본으로 쓴다(종류와 같다).
- **노출**: `Activity.tags: Tag[]`, `AdminActivity.tags`, `ProgramSummary.tags: Tag[]`(프로그램 태그가 있으면 그것, 없으면 열린 활동 태그의 합집합, 최대 6개, 사전 순). 발견 프로그램 행에 태그 칩 최대 3개 + `+n`, 펼친 활동 행에 태그 칩, 활동 상세에 전체. 어드민 활동·프로그램 행과 편집 폼에 태그 선택(검색 + 직접 입력으로 새 태그 추가, 다중).
- **검색**: `GET /v1/activities`·`GET /v1/discovery/programs`의 `q`가 활동 이름 외에 **태그 이름(한글·영문)** 에도 맞는다(`matchedActivityIds`에 포함). 필터 `tagIds?`(쉼표 목록, 모두 포함 AND). 발견 탭 검색창 아래에 태그 칩 줄(`GET /v1/tags`, 다중 선택)을 두고 선택하면 `tagIds`로 보낸다.
- 표기: 코드 없이 사전 id를 쓰고 이름은 한글+영문 쌍(2026-10-18 어휘 규칙). JSON 키는 camelCase.

## 활동 종류(category) — 2026-10-19 사용자 결정

사용자: "활동 종류도 추가해줬으면 좋겠어. 부트캠프, 컨퍼런스, 워크샵, 스터디, 강연 등으로 구분하고 프로그램 하위에서 그룹화되도록". 활동에 **종류** 하나를 둔다.

- 값(API 코드 → 한글 이름): `BOOTCAMP` 부트캠프, `CONFERENCE` 컨퍼런스, `WORKSHOP` 워크샵, `STUDY` 스터디, `CLUB` 동아리(연합동아리 등, 2026-10-19 같은 날 추가), `LECTURE` 강연, `HACKATHON` 해커톤, `CONTEST` 공모전, `MEETUP` 밋업, `OTHER` 기타. 없으면 `null`(미분류). 저장은 `activities.category varchar(20) null`(V31, check 제약). 코드는 `participationType`처럼 대문자 영문이고 한글 이름은 클라이언트가 붙인다.
- 공개 `Activity`(활동 조회·발견의 펼친 활동)와 `AdminActivity`에 `category: string|null`을 더한다. `GET /v1/activities`·`GET /v1/discovery/programs`에 `category?` 필터(코드 하나 또는 쉼표 목록)를 더한다.
- 수집: 추출 지시문이 `category`를 함께 뽑는다(본문의 성격으로 판단, 모르면 null). 어드민이 바꾼 값은 `curated_fields`의 `category`로 보호된다.
- 화면: 프로그램 하위에서 종류별로 묶는다. **어드민**은 펼친 프로그램 안에서 종류 소제목(부트캠프 (n) …, 미분류는 맨 뒤) 아래에 활동 행을 둔다. **앱·웹 발견**은 펼친 프로그램 안의 활동을 같은 순서의 종류 소제목으로 묶고(종류가 하나뿐이면 소제목 생략), 활동 행에는 종류를 다시 쓰지 않는다. 활동 상세에는 종류 칩 하나. 종류 순서는 위 값 순서. 각 종류 안의 활동 순서는 **모집 마감이 이른 순**이다(2026-10-19 사용자 "정렬은 모집일 순서로"). 정렬 키는 APPLICATION 슬롯의 끝 시각·날짜이고, 모집 마감을 알 수 없으면(모집 기간이 없거나 끝이 비어 있으면) **활동 시작**(ACTIVITY 슬롯의 시작 시각·날짜)을 모집 마감처럼 쓴다(같은 날 사용자 요청 "모집 기간이 미정인 활동들도 활동 시작일을 모집 종료일로 취급"). 둘 다 없으면 맨 뒤, 같으면 이름순. 어드민 펼친 프로그램 안과 `GET /v1/discovery/programs`의 `nearestRecruitmentEndAt`(프로그램 순서)도 같은 키를 쓴다.

## 발견 프로그램 묶음 — 2026-10-18 사용자 결정

사용자 제안: 발견 탭을 활동 카드 목록 대신 **프로그램 단위**로 보여 주고, 누르면 아코디언처럼 펼쳐져 지금 열린 활동이 보이게 한다. 세부 규칙은 메인 제안을 사용자가 그대로 승인했다. iOS·Android·웹 발견 화면에 함께 적용하며 활동 상세와 `내 활동`은 바뀌지 않는다.

### 규칙

- **열린 활동**: 기존 발견 노출 규칙과 같다(게시된 활동, 모집 중, 마감 전. 모집 상태는 모집 기간으로 추론). 열린 활동이 하나도 없는 프로그램은 목록에 나오지 않는다.
- **머리글**: 프로그램 이름, 조직 이름, 열린 활동 수, 가장 가까운 모집 마감(`M월 d일 (요일) 마감`, 없으면 생략). 정렬은 가장 가까운 마감이 이른 순, 마감 없는 프로그램은 뒤, 같으면 이름순.
- **펼침**: 머리글을 누르면 그 프로그램의 열린 활동을 기존 활동 카드로 보인다. 기본은 접힘이며, 열린 활동이 1개인 프로그램과 검색어가 활동 이름에 맞은 프로그램은 펼친 채로 시작한다. 펼침 상태는 앱 실행 중 메모리에만 둔다. 동작 줄이기에서는 애니메이션 없이 바뀐다. 머리글은 접근성에서 `펼침`/`접힘` 상태를 가진 버튼으로 읽힌다.
- **검색**: 검색어는 프로그램 이름·영문 이름, 조직 이름, 열린 활동 이름에 부분 일치(대소문자 무시). 활동 이름으로만 맞은 프로그램도 목록에 나온다.
- **유형(참가등록·선발)**: 2026-10-18 현재 세 플랫폼의 발견 화면에는 유형 칩이 없다(활동 모델에 `participationType`도 없다). API의 `participationType` 필터는 칩을 다시 넣을 때 쓰도록 둔다. 칩이 생기면 펼친 활동과 열린 활동 수에 함께 적용하고 맞는 활동이 없는 프로그램은 숨긴다.

### API

| 메서드·경로 | 인증 | query | 응답 |
| --- | --- | --- | --- |
| GET /v1/discovery/programs | 없음 | `q?`(≤100자), `participationType?`(REGISTRATION\|SELECTION), `cursor?`(정수 오프셋, 기본 0), `size?`(1–100, 기본 20) | 200 `{items: ProgramSummary[], nextCursor: number\|null}` |

- 경로가 `/v1/programs`가 아닌 이유: `/v1/programs`는 이력 선택 창의 활동 정보 검색(로그인)이 이미 쓴다. `nextCursor`는 정렬 결과의 정수 오프셋이다(활동 조회의 id 커서와 다르다).
- **모집 중인 프로그램만 보기(2026-10-19 사용자 결정 "당근의 판매중인 상품만 보기처럼")**: 발견 탭 상단에 토글 `모집 중만 보기`(기본 켬). `GET /v1/discovery/programs`에 `openOnly?`(기본 `true`). 켜면 지금과 같이 열린 활동이 있는 프로그램만, 끄면 **최근 2년 안에 활동이 있었던 프로그램**(게시 활동의 활동 시작·모집 마감·생성 시각 중 하나가 `now - 2년` 이후)을 모두 돌려준다. 순서: 열린 프로그램이 먼저(기존 정렬 키), 그 뒤는 가장 최근 활동 시작이 늦은 순. `ProgramSummary`에 `latestActivityAt: ISO-8601|null`(그 프로그램 게시 활동의 가장 최근 활동 시작)을 더하고 `openActivityCount`는 0일 수 있다. 펼치면 `GET /v1/activities?programId=&open=true`로 모집 중 활동을 보이고, 모집 중이 없으면 "지금 모집 중인 활동이 없어요" 아래에 `지난 활동`(같은 `GET /v1/activities?programId=`, 최근 2년, 활동 시작 최근 순, 20개)을 보인다. 열린 활동이 하나뿐일 때 자동으로 펼치는 규칙은 토글이 켜진 상태에서만 적용한다. 어드민은 바뀌지 않는다.
- **열린 활동 판정 일원화(2026-10-19 사용자 보고 "프로그램을 누르면 활동이 노출되지 않아")**: 원인은 서버가 시각 일정(TIMED)인 모집 기간만 보고 날짜 일정(ALL_DAY) 모집 기간을 "없음"으로 다뤘고, 앱은 시각이 없는 모집 기간을 `확인 필요`로 보고 걸러낸 것. 결정: 판정은 서버 한 곳에서만 한다. 규칙: 게시 활동이고 (a) 모집 기간이 시각 일정이면 `start_at ≤ now`(없으면 열림) 이고 `end_at > now`(없으면 열림), (b) 모집 기간이 날짜 일정이면 서울 날짜로 `start_date ≤ today < end_date_exclusive`, (c) 모집 기간이 없으면 활동 시작(시각 또는 서울 0시)이 `now`보다 뒤. `GET /v1/activities`에 `open=true` 필터를 더해 같은 규칙을 적용한다(기본은 전체). 정렬 키·`nearestRecruitmentEndAt`는 `coalesce(app.end_at, app.end_date_exclusive 서울 0시, 활동 시작)`. 앱·웹은 프로그램을 펼칠 때 `GET /v1/activities?programId=&open=true`를 부르고 **스스로 걸러내지 않는다**. 활동 상세의 `모집 중/마감/예정` 라벨은 날짜 일정도 같은 규칙으로 계산한다(시각이 없다고 `확인 필요`로 두지 않음).
- ProgramSummary: `{id, name, nameEn: string|null, organization: {id, name}, openActivityCount, nearestRecruitmentEndAt: ISO-8601(UTC)|null, matchedActivityIds: number[]}`. 열린 활동 판정은 서버가 처음으로 가진다: 게시된 활동의 APPLICATION 시각 일정이 `start_at ≤ now`(없으면 열림)이고 `end_at > now`(없으면 열림). 웹·iOS가 쓰던 클라이언트 규칙과 같다. `openActivityCount`는 위 규칙(과 `participationType`)에 맞는 열린 활동 수이고 1 이상인 프로그램만 돌려준다. `nearestRecruitmentEndAt`은 그 활동들 중 가장 이른 모집 마감(없으면 null). `matchedActivityIds`는 `q`가 활동 이름에 맞은 열린 활동 id(없으면 빈 배열).
- 펼칠 때는 기존 `GET /v1/activities?programId=<id>`를 쓰고, 클라이언트는 열린 활동만 남긴다.
- `Cache-Control: no-store`·오류 형식은 활동 조회와 같다.

## 구글 로그인 — 2026-10-19 네이티브 SDK로 전환

사용자 결정(2026-10-19): 구글 개발자 계정 인증이 끝나 OAuth 2.0 SDK를 쓸 수 있으므로, 아래 2026-10-18 **웹 방식**(서버 콜백·client secret·code 교환)을 **네이티브 방식**으로 바꾼다. 웹 방식 엔드포인트(`web/start`·`callback`·`web/exchange`)와 `GOOGLE_CLIENT_SECRET`·`GOOGLE_CALLBACK_URL`은 지운다(운영에 설정된 적이 없다). 계정 규칙(같은 `sub` = 같은 Dearby 계정, 이메일로 합치지 않음, 처음 보는 계정 202 → 패스키 조용히 묻기 → `register`/`/me/identities/google`)과 `user_identities.provider = GOOGLE`은 그대로다.

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| POST /v1/auth/google/nonce | 없음 | — | 200 `{nonce}`(애플 `/auth/apple/nonce`와 같은 1회용·만료 규칙). 서버에 허용 client id가 없으면 503 |
| POST /v1/auth/google | 없음 | `{idToken, nonce}` | 200 TokenPair(이미 연결된 구글 계정) 또는 202 `{linkToken, email, displayName}`(처음 보는 계정). 검증 실패 401 |
| POST /v1/auth/google/register | 없음 | `{linkToken}` | 201 TokenPair(그대로) |
| POST /v1/me/identities/google | Bearer | `{linkToken}` | 204(그대로) |

- **ID 토큰 검증**: JWKS 서명, `iss` 두 표기, 만료, `nonce` 일치는 그대로. `aud`는 **허용 목록** `GOOGLE_CLIENT_IDS`(쉼표 구분) 중 하나여야 한다. Android(Credential Manager, `serverClientId` = 웹 클라이언트 id)는 `aud`가 웹 클라이언트 id이고, iOS SDK는 `aud`가 iOS 클라이언트 id이므로 둘을 모두 넣는다. 설정 키는 `GOOGLE_CLIENT_IDS` 하나로 바꾸고 `GOOGLE_CLIENT_ID`는 더 읽지 않는다. 값은 서버 `.env.prod`에만 둔다.
- **iOS**: `GoogleSignIn-iOS`(Swift Package, `https://github.com/google/GoogleSignIn-iOS`, 10.x — `nonce` 매개변수는 9.0부터)를 `generate_project.rb`가 원격 패키지로 추가한다. 빌드 설정 `GOOGLE_IOS_CLIENT_ID`(비어 있으면 버튼 숨김)와 그 역순 표기 `GOOGLE_IOS_URL_SCHEME`(`com.googleusercontent.apps.<id>`)을 `Info.plist`의 `GIDClientID`·`CFBundleURLSchemes`로 넣는다(기존 `dearby` 스킴은 유지). 이 작업에서 iOS에 `CFBundleURLTypes`가 아예 없어 `dearby://cards/…`가 앱을 열 수 없던 것을 발견해 함께 등록했다(애플·구글 웹 콜백은 `ASWebAuthenticationSession`이 자체 처리해 드러나지 않았다). 흐름: `/auth/google/nonce` → `GIDSignIn.sharedInstance.signIn(withPresenting:hint:additionalScopes:nonce:)` → `idToken`을 `/auth/google`로. 서버가 503이면 버튼 숨김(기존 규칙). 애플 로그인의 `ASAuthorizationController` 처리와 같은 자리에서 결과(200/202)를 다룬다.
- **Android**: `androidx.credentials`(이미 있음) + `com.google.android.libraries.identity.googleid:googleid`로 `GetGoogleIdOption(serverClientId = BuildConfig.GOOGLE_SERVER_CLIENT_ID, nonce, filterByAuthorizedAccounts=false)` → `GoogleIdTokenCredential.idToken` → `/auth/google`. 빌드 설정 `GOOGLE_SERVER_CLIENT_ID`(Gradle 속성 또는 환경 변수, 비어 있으면 버튼 숨김). Custom Tab 흐름(`features/oauth`의 구글 부분, `dearby://auth/google` 인텐트 필터)은 지운다(애플이 아직 쓰는 부분은 남긴다).
- **운영 준비(사용자)**: Google Cloud 콘솔에서 OAuth 클라이언트 세 개. ① 웹 애플리케이션(리디렉션 URI 없음) → 서버 `GOOGLE_CLIENT_IDS`와 Android `GOOGLE_SERVER_CLIENT_ID`에 쓴다. ② iOS(번들 `com.dearby.dearby`) → `GOOGLE_IOS_CLIENT_ID`와 서버 허용 목록. ③ Android(패키지 `io.wid.dearby`, 디버그 keystore SHA-1; 출시 서명 SHA-1은 출시 서명을 정한 뒤 추가). 값은 채팅에 적지 않고 서버 `.env.prod`와 로컬 `~/.dearby-deploy/mobile.env`(`GOOGLE_IOS_CLIENT_ID`, `GOOGLE_IOS_URL_SCHEME`, `GOOGLE_SERVER_CLIENT_ID`)에 둔다. 기기 설치 빌드는 그 파일을 읽어 넘기고, CI는 값 없이 빌드한다(버튼 숨김).
- 버튼 모양·로고 자산 규칙은 2026-10-18 항목 그대로(자산이 들어올 때까지 글자 버튼).

## 구글 로그인 — 2026-10-18 사용자 결정 (웹 방식, 2026-10-19 네이티브로 대체됨 — 위 항목)

애플 로그인·패스키와 함께 쓰는 세 번째 로그인 수단이다. iOS·Android 모두 **웹 방식**(애플의 Android 방식과 같은 서버 코드 흐름)으로 하고 SDK는 넣지 않는다. 같은 구글 계정(`sub`)은 같은 Dearby 계정이 되며, 계정은 이메일로 합치지 않는다. 운영에 `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`GOOGLE_CALLBACK_URL`이 없으면 아래 `/auth/google/*`는 503이다.

| 메서드·경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| POST /v1/auth/google/web/start | 없음 | `{verifierHash}` | 200 `{authorizeUrl}` |
| GET /v1/auth/google/callback | 없음 | 구글 리디렉션(`code`, `state`, 또는 `error`) | 303 `dearby://auth/google?code=<1회용>` 또는 `?error=cancelled\|failed` |
| POST /v1/auth/google/web/exchange | 없음 | `{code, verifier}` | 200 TokenPair(이미 연결된 구글 계정) 또는 202 `{linkToken, email, displayName}`(처음 보는 계정). 401이면 처음부터 다시 |
| POST /v1/auth/google/register | 없음 | `{linkToken}` | 201 TokenPair. 새 Dearby 계정(USER)을 만들고 구글 계정을 연결한다 |
| POST /v1/me/identities/google | Bearer | `{linkToken}` | 204. 로그인한 계정에 구글 계정을 연결한다. 이미 다른 구글 계정이 연결돼 있으면 400 |

- 흐름(앱 공통): 무작위 verifier(43자 이상)의 SHA-256(base64url, 패딩 없음)을 `web/start`로 보내고, 받은 `authorizeUrl`을 iOS는 `ASWebAuthenticationSession`(callback scheme `dearby`), Android는 Custom Tab으로 연다. 구글은 `GOOGLE_CALLBACK_URL`(`https://api.dearby.wid.io.kr/v1/auth/google/callback`)로 돌려보내고, 서버는 `code`를 구글 토큰 엔드포인트에서 교환해 ID 토큰을 검증한 뒤 `dearby://auth/google?code=…`로 앱에 돌려보낸다. 앱은 그 code와 verifier로 `web/exchange`를 부른다. 결과 code·linkToken은 10분, 한 번만 쓴다.
- 인가 요청: `https://accounts.google.com/o/oauth2/v2/auth`, `response_type=code`, `scope=openid email profile`, `state`(서버가 만든 상태 id), `nonce`, `prompt=select_account`. `access_type=offline`은 쓰지 않고 refresh token은 저장하지 않는다(계정 삭제 때 폐기할 토큰이 없다. 애플과 다른 점).
- ID 토큰 검증: JWKS `https://www.googleapis.com/oauth2/v3/certs` 서명, `iss`가 `https://accounts.google.com` 또는 `accounts.google.com`, `aud = GOOGLE_CLIENT_ID`, 만료, `nonce`가 발급값과 같아야 한다. `email_verified`가 false면 email은 저장하지 않는다. `name`을 `display_name`에 남긴다.
- 처음 보는 구글 계정(202)의 처리는 애플과 같다: 앱이 이 기기의 Dearby 패스키를 조용히 묻고(있으면 로그인 후 `/me/identities/google`로 연결), 없으면 `register`로 새 계정, 사용자가 프롬프트를 닫았을 때만 선택 화면.
- DB: `user_identities.provider`에 `GOOGLE`을 허용한다(V22). 로그인 중간 상태는 애플의 상태 저장소와 같은 구조(provider 열 추가 또는 별도 테이블)로 둔다.
- 앱 화면: 로그인 시트와 시작 화면에 애플 버튼 옆(iOS는 아래)에 `Google로 계속하기` 버튼을 둔다. 구글 브랜드 가이드의 흰 바탕·회색 테두리·"G" 로고 버튼을 따르되, 로고 자산은 공식 파일을 받아 `shared/assets/prototype`에 둔 뒤 적용한다(자산이 없는 동안은 글자 버튼). 빌드에 `GOOGLE_SIGN_IN_ENABLED` 같은 별도 플래그는 두지 않고, 서버가 503이면 버튼을 숨긴다(애플 버튼이 `appleSignInEnabled`를 판단하는 방식과 같게).
- Android는 `dearby://auth/google` 인텐트 필터를, iOS는 `ASWebAuthenticationSession` 콜백으로 받는다. 애플 콜백 처리(`appleRedirect`)와 같은 자리에서 구글 콜백도 해석한다.
- 운영 준비(사용자): Google Cloud 콘솔에서 OAuth 클라이언트(종류 "웹 애플리케이션") 생성, 승인된 리디렉션 URI에 `https://api.dearby.wid.io.kr/v1/auth/google/callback` 추가, OAuth 동의 화면(앱 이름 Dearby, scope openid·email·profile). 클라이언트 ID·시크릿은 서버 `.env.prod`에만 둔다.

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

2026-10-18 지운 공유 기능을 대신하는 새 설계. 사용자 결정(2026-10-19): 배부(배포) 단위 관리는 두지 않는다. 받은 명함에는 **어느 프로그램(활동)에서 교환했는지만** 표시한다. **단방향**으로 받은 명함은 받은 날부터 30일 뒤 회수한다. 옛 흐름(제작 → 발행 → 공유 화면)이 부자연스러웠다는 이유로 주인 쪽은 "명함 고르기 → QR 보이기" 한 번으로 끝나야 한다. 발행 스냅샷·`/cards/:id` 공개 조회·비로그인 저장 계약은 그대로다.

### 흐름

- **주인(앱)**: 내 명함 탭에서 고른 명함 아래 `QR 보이기`. 전체 화면 QR과 함께 활동 칩이 보인다. 칩은 오늘 진행 중인 내 활동(내 활동 진행 트랙의 `진행 중` 중 현재 단계가 가장 가까운 것)이 자동으로 골라지고, 눌러서 신청한 다른 활동이나 `활동 없이`로 바꿀 수 있다. QR 내용은 `https://<웹 origin>/cards/<cardId>?activity=<activityId>`(활동 없이면 `activity` 생략). 화면을 보는 동안 밝기를 올리고, 닫으면 되돌린다. 배부 기록·수신자 목록·활동별 관리 화면은 만들지 않는다.
- **받는 사람(웹)**: OS 카메라로 QR을 읽으면 웹 `/cards/:id?activity=…`가 열린다. 카드 위에 `<프로그램 이름> <활동 이름>에서 받은 명함`(활동이 없으면 생략) 한 줄. 카드 아래 버튼 둘: `Dearby 앱에 저장`(`dearby://cards/<cardId>?activity=<activityId>` 커스텀 스킴, 앱이 없으면 아무 일도 없으므로 그 아래 "앱이 없으면 브라우저에 저장할 수 있어요" 안내)과 기존 `브라우저에 저장`(비로그인 저장, `activityId`를 함께 보낸다). 유니버설 링크·App Links·앱 안 카메라 스캔은 쓰지 않는다(#90 미정, 오늘 범위 밖).
- **받는 사람(앱)**: 스킴 `dearby://cards/<cardId>?activity=<activityId>`를 받으면 로그인 상태에서 `PUT /v1/wallet/cards/:cardId {activityId}`로 계정 지갑에 저장하고 받은 명함 상세를 연다. 상세 아래 `내 명함도 건네기` 버튼: 내 명함이 하나면 바로, 여럿이면 고른 뒤 `POST /v1/wallet/cards/:cardId/reciprocate {myCardId}`. 성공하면 "맞교환했어요" 배지. 로그아웃 상태면 로그인 시트를 먼저 띄우고 이어서 저장한다. 자기 명함을 받으면 "내 명함이에요" 안내만.
- **회수**: 받은 날(`receivedAt`/`savedAt`)부터 30일이 지나도 맞교환이 아니면 서버가 회수한다. 받은 명함 목록에서 사라지고(`recalled_at` 기록, 행은 보존), 그 사람의 지갑에서만 빠진다. 공개 `/cards/:id`는 그대로 열린다(배부 단위를 두지 않은 결정의 결과이며, 주소를 적어 둔 사람까지 막지는 않는다). 명함 철회(`DELETE /v1/cards/:id`)는 지금처럼 모두에게서 사라진다.
- **맞교환(reciprocal)**: 받은 명함 R(받는 사람 U, 명함 C, 주인 O)은 "O가 U의 명함(어느 것이든) 하나를 회수·철회되지 않은 상태로 갖고 있으면" 맞교환이다. 계정 쌍 기준이라 활동이 달라도 성립한다. 맞교환이던 것이 상대 명함의 철회·회수로 깨지면 다시 단방향이 되고, 받은 날 기준 30일이 이미 지났으면 다음 회수 때 빠진다. 비로그인 저장자는 명함이 없어 맞교환할 수 없으므로 항상 30일 뒤 회수된다(의도된 결과: 앱 설치를 유도한다).

### API

| 경로 | 인증 | 요청 | 응답 |
| --- | --- | --- | --- |
| PUT /v1/wallet/cards/:cardId | Bearer | `{activityId?: number}` | 첫 저장 201 Receipt, 이미 있으면 200 Receipt(받은 날은 유지, 활동이 비어 있었고 새로 왔으면 채운다). 없는/철회된 명함 404, 자기 명함 409 `OWN_CARD` |
| POST /v1/wallet/cards/:cardId/reciprocate | Bearer | `{myCardId}` | 201 `{reciprocal: true}`. 호출자가 `cardId`를 회수되지 않은 상태로 갖고 있어야 하고(아니면 403), `myCardId`는 호출자의 미철회 명함(아니면 404). 주인의 지갑에 호출자 명함의 Receipt를 같은 활동으로 만든다(이미 있으면 그대로 201) |
| GET /v1/wallet | Bearer | — | 200 `{items: Receipt[]}`. 회수된 것·철회된 명함 제외, `seq` 순 |
| GET /v1/guest/cards | 게스트 토큰 | — | 200 `{items: GuestItem[]}`(2026-10-19부터 `Card[]`가 아니다) |
| PUT /v1/guest/cards/:id | 게스트 토큰 선택 | `{activityId?: number}` | 기존 응답 그대로(201/200). 활동은 처음 저장할 때만 기록 |

- Receipt: `{id, card, context: {activityId: number|null, activityName: string|null, programName: string|null}, receivedAt, reciprocal: boolean, expiresAt: string|null}`. `expiresAt`은 서버가 계산한 회수 예정 시각(`receivedAt` + 30일), 맞교환이면 `null`. 클라이언트는 다시 계산하지 않는다. 활동 id가 있지만 활동이 지워졋으면 이름은 `null`.
- GuestItem: `{card, context: {activityId, activityName, programName}, savedAt, expiresAt}`(`expiresAt`은 항상 `savedAt` + 30일).
- 회수 작업: batch 모듈이 매일 04:00(Asia/Seoul)에 돌린다. 조건은 "회수되지 않았고 받은 날 + 30일 < 지금 이고 맞교환이 아님"(`receipts`), "회수되지 않았고 저장일 + 30일 < 지금"(`guest_cards`). `--recall-now` 플래그로 즉시 실행할 수 있다(`--collect-now`와 같은 방식). 결과는 로그에 건수로 남긴다.
- 저장: V29. `receipts.recalled_at timestamptz null`, `receipts.context`는 기존 jsonb에 `{"activityId": <number>}`만 쓴다(`label`은 쓰지 않는다). `guest_cards`에 `activity_id bigint null`, `saved_at timestamptz not null default now()`, `recalled_at timestamptz null`. 기존 행의 `saved_at`은 마이그레이션 시각이 되어 30일 뒤 회수된다.
- `activityId`는 활동의 숫자 id다(`GET /v1/activities/:id`). 모르는 id는 400이 아니라 무시하고 `null`로 저장한다(QR을 만든 뒤 활동이 지워진 경우).

### 화면

- **받은 명함 목록(iOS·Android·웹 `/saved`)**: 기존 색인 목록 그대로. 행의 칩 줄에 프로그램 이름 칩(활동 이름은 상세에서)과 상태 배지 하나: 맞교환이면 `맞교환`, 아니면 `N일 뒤 사라져요`(`expiresAt` 기준, 당일은 `오늘 사라져요`). 활동별 필터·묶음은 만들지 않는다.
- **받은 명함 상세**: 카드 위에 `<프로그램> <활동>에서 받음 · <받은 날>`, 카드 아래 `내 명함도 건네기`(맞교환이면 `맞교환했어요` 배지로 대체).
- **내 명함 QR 화면**: 위 흐름대로. 색·글꼴은 기존 토큰. QR은 iOS CoreImage `CIQRCodeGenerator`, Android `com.google.zxing:core`(인코딩만, CameraX·카메라 권한 없음)로 그린다. 웹 origin은 `DEARBY_WEB_ORIGIN` 빌드 설정(기본 `https://dearby.wid.io.kr`)이다.
- 접근성: QR 이미지는 "내 명함 QR 코드, 카메라로 읽으면 명함을 받아요"로 읽히고, 활동 칩은 버튼이다. 배지는 행 접근성 라벨에 포함한다.

## 명함 표현 — 2026-10-18 사용자 결정

Mobbin 참고(Instagram 프로필 카드, Telegram·Luma 연락처 행, Mindvalley 직무 칩, Airbnb 자격 목록)를 바탕으로 명함 본문의 표현을 세 플랫폼에서 같게 맞춘다. 데이터·계약은 바뀌지 않는다(`Card` 그대로).

- **카드 면**: 흰 바탕, 모서리 20, 1px 구분선 색 테두리, 안쪽 여백 20. 웹은 최대 너비 480px 가운데 정렬. 명함은 "한 장의 카드"로 보이고 행동 버튼(고치기·저장)은 카드 바깥 아래에 둔다.
- **머리**: 가운데 정렬. 이니셜 아바타 56(민트 바탕·청록 글자) → 이름(title 굵게) → 직무 칩(`job`을 ` · `로 나눈 각 항목을 옅은 배경 칩으로, 없으면 생략) → 소속(가장 최근 승인 이력의 조직 이름, 보조색 작은 글자, 없으면 생략).
- **소개**: `introduction`(기본 템플릿을 채운 결과 = 자기소개 글) 마크다운을 지금 렌더러로. 비어 있으면 절 생략. 이름·직무·연락처는 여기 다시 나오지 않는다(2026-10-18, 위 "템플릿").
- **연락처**: 절 제목 `연락처`. 행마다 왼쪽 종류 아이콘(전화·메일·카카오톡·Instagram·GitHub·Behance), 라벨(보조색 작은 글자: `label`이 있으면 그것, 없으면 종류 이름), 값(본문색), 열 수 있으면 오른쪽 외부 링크 아이콘. 행 전체가 눌려 기존 동작(전화·메일·링크 열기)을 한다. 연락처가 없으면 `공개된 연락처가 없어요`.
- **활동 이력**: 절 제목 `활동 이력`. 행마다 기간(보조색 작은 글자, `2026.03 – 진행 중`), 제목(굵게, 조직 · 프로그램 · 활동), 역할과 인증 배지(`확인됨`/`미확인`, 기존 배지). 설명이 있으면 아래 한 줄. 이력이 없으면 `공개된 활동 이력이 없어요`.
- **내 명함 탭**: 가로 스크롤 타일 대신 2열 격자. 첫 칸 `+ 새 명함`, 각 칸은 명함 이름(굵게)·프로필 이름·직무 한 줄. 선택한 칸은 민트 바탕·청록 테두리. 격자 아래에 선택한 명함의 카드(위 규칙)와 `이 명함 고치기` 버튼, 그 아래 위험 색 `이 명함 지우기`(확인 대화상자 "공개 링크도 더 열리지 않아요. 되돌릴 수 없어요." → `DELETE /v1/cards/:id`, 2026-10-18 사용자 요청 "내 명함 삭제가 없어"). 지운 뒤 다음 명함(가장 새 것)을 고르고, 마지막 명함이면 첫 명함 안내로 돌아간다. 명함이 없을 때의 안내 카드는 유지.
- **받은 명함 상세**: 같은 카드. 행동 버튼은 없음(공유는 나중에).
- **웹 `/cards/:id`**: 같은 카드 구성, 저장 버튼은 카드 아래. `/saved` 목록은 바꾸지 않는다.
- 색·여백·글꼴은 기존 토큰(청록 `#007F80`, 민트 `#F0FCFA`, 구분선 `#E3E8EA`, 보조 `#657078`, Pretendard)만 쓴다. 동작 줄이기 영향 없음(애니메이션 없음).

## 내 활동 진행 트랙 — 2026-10-18 사용자 결정

신청한 활동의 선발·진행 단계를 `내 활동` 탭에서 한눈에 본다(사용자 제안, "NEXT STEP" 참고 이미지의 세그먼트 트랙 방식). 별도 탭은 두지 않고 `내 활동` 탭을 바꾼다. iOS·Android 공통.

### 단계 만들기

- 활동의 `schedules`에서 단계를 만든다. APPLICATION 슬롯(모집 마감)은 맨 앞의 `지원` 단계, ACTIVITY/APPLICATION 슬롯이 아닌 일정(수집된 세부 일정과 운영자 지정 일정)을 시작 시각·시작 날짜 순으로 정렬한 것이 가운데 단계들, ACTIVITY 슬롯(활동 기간)은 맨 뒤의 `활동` 단계다(제목이 있으면 그 제목). 세부 일정이 없는 활동도 `지원 → 활동` 두 단계를 가지므로, 지원 마감이 지나도 활동이 끝나기 전까지는 `진행 중`에 남는다. 날짜가 없는 단계는 날짜 있는 단계 뒤에 API 순서로 둔다. 종류(`kind`)는 아이콘·배지 문구에 쓴다: DEADLINE `마감`, ANNOUNCEMENT `발표`, INTERVIEW `면접`, EVENT는 일정 제목.
- 날짜가 없는 단계(문구만)는 순서를 유지하되 D-day를 계산하지 않고 `날짜 미확인`으로 보인다. 단계가 하나도 없는 활동은 지금처럼 `신청함`/`참여 확정` 표시만 보인다.

### 현재 단계와 사용자 표시

- 자동 판정: 오늘(서울 시간) 기준으로 끝나지 않은 가장 이른 단계가 현재 단계다. 끝난 단계는 지난 것으로 본다.
- 사용자 표시: 단계마다 `통과`·`대기`·`탈락`을 직접 표시할 수 있다(기본 `대기`). `통과`로 표시하면 그 단계는 지난 것으로 보고 다음 단계가 현재가 된다. `탈락`이면 활동이 `끝난 활동` 묶음으로 내려간다. 표시는 사용자가 스스로 남기는 것이며 주최 측 결과가 아니라는 문구를 상세에 둔다(참여 확정 표시와 같은 원칙).
- 저장: 신청 표시와 같이 기기 안에만 둔다(앱 실행 간 유지 여부도 신청 표시와 같은 규칙을 따른다). 서버·계정 동기화는 이번 범위가 아니다.

### 화면

- 목록 행: 활동 이름(굵게) + 프로그램 · 조직(보조색) / 아래에 세그먼트 트랙: 지난 단계는 채움(청록), 현재 단계는 빗금 또는 강조 테두리, 다음 단계는 옅은 회색. 트랙 아래 단계 이름(현재 단계는 굵게, 길면 말줄임). 오른쪽 끝에 배지 `D-3 · 면접`(현재 단계의 시작까지 남은 날과 종류/제목. 진행 중인 날은 `D-DAY`, 모든 단계가 지났으면 `지남`, 탈락 표시면 `탈락`(위험 색), 날짜 없으면 `날짜 미확인 · 종류`). 단계가 4개를 넘으면 트랙은 4칸까지만 보이고 `+N`을 붙인다.
- 묶음: `진행 중`(기본), `끝난 활동`(탈락 표시했거나 모든 단계가 지난 활동). 끝난 활동은 접힌 묶음으로 아래에 둔다.
- 상세(행을 누름): 세로 타임라인. 단계마다 종류 아이콘·이름·날짜(또는 문구)·상태 칩과 `통과`/`대기`/`탈락` 선택. 맨 위에 활동 이름과 활동 상세로 가는 버튼, 맨 아래에 "표시는 내가 남긴 것이며 주최 측 결과가 아니에요" 안내. 날짜가 있는 단계는 기존 `겹치는 시간 확인`을 그 단계 시각으로 열 수 있다.
- 검색·정렬: 기존 내 활동 검색 규칙 유지. 정렬은 다음 단계가 가까운 순, 날짜 없는 활동은 뒤.
- 접근성: 트랙은 하나의 요소로 "5단계 중 3단계 1차 면접 진행 중"처럼 읽힌다. 동작 줄이기에서 트랙 애니메이션 없음.
