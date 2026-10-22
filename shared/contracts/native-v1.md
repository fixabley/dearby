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
| PUT /cards/:id | `{name,description,template?,contactIds,historyIds}`·소유자만 | 200 Card(같은 id, `updatedAt` 갱신; 2026-10-20 "명함 고치기는 같은 명함을 수정"). 남의 명함·철회된 명함 404, 검증 400은 POST와 같음. `template`을 비우거나 빼면 기존 템플릿 유지 |
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
| 명함 교환 | 명함별 QR(공개 주소 + 활동) → 웹에서 보고 `dearby://cards/…`로 앱 저장 또는 브라우저 저장, 단방향은 30일 뒤 회수. 아래 "명함 교환"(2026-10-19). 옛 QR 공유·스캔·유니버설 링크는 2026-10-18 삭제(위 "명함 공유 — 변경 이력") | PUT /v1/wallet/cards/:id, POST …/reciprocate, GET /v1/wallet |

- 앱은 `/s/*` 유니버설 링크를 받지 않고(2026-10-18 삭제), 웹도 `/.well-known/apple-app-site-association`·`assetlinks.json`을 만들지 않는다(패스키용 `wid.io.kr` 정적 파일은 별도이며 유지). 앱이 링크를 받는 통로는 커스텀 스킴 `dearby://`(애플 콜백, 명함 교환)만이다. #90·#91은 이 결정으로 닫았다.

### 비로그인 사용자의 저장 유지

2026-10-06 사용자 결정: 비로그인 사용자는 앱을 설치하지 않았고 로그인하지 않은 사람이다. 공개 명함 링크 `/cards/:id`(명함 교환 QR의 주소, 2026-10-18까지는 `/s/<shareId>`)가 기본 브라우저에서 열린다. 서버가 만료 없는 게스트 세션 ID를 발급하고, 저장 데이터는 DB에 두며 API가 세션 ID로 조회한다. 브라우저에는 세션 ID 쿠키만 둔다.

- 웹: 위 "웹 비로그인 명함 저장" 계약의 서버 게스트 세션(`__Host-dearby_guest` 쿠키)을 그대로 쓴다. 서버 세션은 만료되지 않는다. 브라우저는 쿠키 수명을 최대 400일로 제한하므로, 방문할 때마다 Max-Age를 갱신한다(기존 규칙). 1년 넘게 방문하지 않거나 사용자가 쿠키를 지우면 접근이 끊긴다.
- 홈 화면 추가: Android Chrome은 바로가기·설치 모두 Chrome과 같은 쿠키를 쓴다. iOS는 '웹 앱으로 열기'가 기본으로 켜져 있고 사이트가 바꿀 수 없어서, 그대로 추가하면 Safari와 쿠키가 분리된다(2026-10-06 iOS 26.5 시뮬레이터 확인). 사용자 결정으로 아래 "홈 화면 세션 잇기"를 둔다. manifest `display`는 Android 설치 버튼을 위해 `standalone`을 유지한다. iOS는 공유 메뉴에서 직접 추가해야 하므로 1번 클릭 설치는 불가하고 안내로 제공한다.
- 홈 화면 세션 잇기(2026-10-06 사용자 결정): 홈 화면 앱이 처음 열릴 때 1회용 코드로 Safari와 같은 게스트 세션 ID 쿠키를 받는다. 이후 두 곳은 같은 세션을 쓰며, 한쪽에서 세션을 지우면 다른 쪽도 끊긴다.
  - 발급: 웹 manifest는 요청마다 만든다. 게스트 쿠키가 있고 저장한 명함이 1개 이상이면 Next가 `POST /v1/guest/handoffs`(게스트 프록시)로 코드를 받아 `start_url`을 `/saved?handoff=<code>`로 넣는다. 아니면 `/saved`. manifest 응답은 `Cache-Control: private, no-store`와 `Vary: Cookie`이다.
  - API `POST /v1/guest/handoffs`: 기존 토큰 필요, 201 `{code, expiresAt}`. 코드는 32바이트 이상 무작위 base64url, 수명 10분, 1회용. 세션당 유효 코드는 1개이며 새로 발급하면 이전 코드는 무효다. DB에는 코드의 SHA-256 digest·세션·만료·사용 시각과, 발급 요청의 게스트 토큰을 코드에서 파생한 키(HKDF)로 AES-256-GCM 암호화한 값만 둔다. DB만으로는 토큰을 복원할 수 없고, 사용·만료 시 암호문을 지운다.
  - API `POST /v1/guest/handoffs/redeem`: body `{code}`, 게스트 프록시, 토큰 없이 호출. 200 `{guestToken}`(코드로 복호화한, Safari와 같은 토큰). 없음·만료·사용됨·세션 폐기는 모두 404 `NOT_FOUND`(구분하지 않음). 성공하면 즉시 사용 처리한다. 요청 제한은 게스트 카운터와 같고, 실패도 센다.
  - 웹 `/saved?handoff=<code>`: 게스트 쿠키가 없을 때만 교환한다. 성공하면 쿠키를 설정하고, 성공·실패 모두 303으로 쿼리 없는 `/saved`로 보낸다. 이미 쿠키가 있으면 교환하지 않고 바로 보낸다. 페이지는 `Referrer-Policy: no-referrer`를 쓰고, 서버 로그에서 `handoff` 쿼리를 가린다.
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
| GET /v1/wallet | Bearer | — | 200 `{items: Receipt[]}`. Receipt 형태와 저장·맞교환 경로는 아래 "명함 교환" |

- Card: `{id, ownerId, name, description, template, introduction, profileName, job, contacts: Contact[], histories: History[], createdAt}`. 발행 시점 스냅샷이며 프로필을 고쳐도 바뀌지 않는다. `template`은 발행할 때 보낸 마크다운 템플릿, `introduction`은 그것을 **채운 결과**(공개 본문)다. 이력에는 `evidence`가 없고 `verification`만 남는다. 공개 조회(`GET /v1/cards/:id`)에는 `template`을 싣지 않는다.
- Receipt: 아래 "명함 교환"(2026-10-19)의 형태. 새 Receipt는 `PUT /v1/wallet/cards/:cardId`와 `reciprocate`가 만든다.
- 웹 비로그인 저장(`/v1/guest/*`, 홈 화면 세션 잇기)은 2026-10-17 Kotlin API에 다시 구현했다(위 "웹 비로그인 명함 저장"·"홈 화면 세션 잇기" 계약 그대로; `GET /v1/guest/cards`의 항목 형태와 `activityId`는 아래 "명함 교환"). 오류 본문은 다른 경로와 같은 ProblemDetail이며 웹 프록시는 상태 코드만 옮긴다(401 `GUEST_SESSION_INVALID`, 그 외 `REQUEST_FAILED`). API의 `GUEST_PROXY_SECRET`이 비어 있으면 `/v1/guest/*`만 503이다.
- 아직 없는 것: `POST /v1/wallet/import`, `POST /v1/exchanges`. 앱이 쓰지 않아 만들지 않는다.

### 템플릿

명함 본문(`introduction`)은 발행할 때 서버가 `template`을 채운 결과다. 사용자가 템플릿을 고치는 UI는 없다.

- 앱은 명함 제작에서 이름·공개 연락처·이력만 고르고, `template`에는 **앱 내장 기본 템플릿** `{{profile.introduction}}` 하나를 그대로 보낸다. 제작 화면은 채운 결과를 읽기 전용 미리보기로 보인다. 이름·직무·연락처는 카드 면(아래 "명함 표현")이 따로 그리므로 본문에 다시 넣지 않는다.
- 토큰: `{{profile.name}}`, `{{profile.job}}`, `{{profile.introduction}}`, `{{contact:<연락처 id>}}`. 서버는 발행할 때 채운다. `{{contact:…}}`는 그 명함에 고른 연락처만 채우고, 고르지 않았거나 없는 연락처·모르는 토큰은 빈 문자열이 된다.
- 발행한 명함은 바뀌지 않는다. 명함을 고치면 앱이 같은 명함을 `PUT /v1/cards/:id`로 수정하고(2026-10-20; 전에는 새 명함을 발행했고) 이전 명함은 사용자가 지울 때까지 남는다(공개 링크 `/cards/:id` 유지).
- 마크다운 부분집합(제목·굵게·기울임·목록·링크, 원시 HTML 금지, `https`·`mailto`·`tel` 링크만)과 10,000자·제어 문자 규칙. 웹 `/cards/:id`는 모든 폭에서 `introduction`만 보인다.
- 변경 이력: 2026-10-18 실시간 꾸미기 편집기와 웹 버전(`webTemplate`·`webIntroduction`, V20)을 같은 날 추가·삭제(V21로 열 삭제, [`cb456363`](https://github.com/fixabley/dearby/blob/cb456363/shared/contracts/native-v1.md#템플릿-명함-꾸미기), 사용자 "실시간 편집 이런 퀄리티면 그냥 빼는 게 좋을 것 같아") · 2026-10-18 기본 템플릿을 `# 이름 / 직무 / 소개 / - 라벨: 연락처`에서 `{{profile.introduction}}`만으로 바꿈(사용자 "이미 제공되는 정보인데 명함에 텍스트로 표시되는 거 지워줘"). 옛 기본 템플릿으로 발행된 명함은 V27·V28이 같은 모양으로 정리했고(V28은 `# `·빈 줄이 없던 더 이른 기본 템플릿 포함), 토큰만으로 된 기본 템플릿 모양이 아닌 옛 명함은 그대로다.

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
- Activity: `{id, name, description, organization{id,name}, program{id,name}, officialUrl, applicationUrl, imageUrl, application, activitySchedule, schedules[], eligibility}`. 2026-10-14 사용자 결정으로 원래 도메인 필드에 공식 안내 링크(`officialUrl`)와 이미지(`imageUrl`)만 더한다. 2026-10-18 `eligibility`(대상자 조건 JSON, 없으면 null)를 더한다(아래 "대상자 조건"). 2026-10-19 `applicationUrl`(신청 링크 `activities.application_url`, 없으면 null)을 더한다(아래 "신청 확인 조건 완화·공개 applicationUrl"). 모집 상태·참여 방식·확인 시각·장소·비용·대상·자격요건(JSON)·역할·안내 문구는 같은 `activities` 행(+`activity_roles`)에 있지만 운영자용 도메인 `ActivityCuration`이 다루고 공개 API는 내보내지 않는다. 같은 DB를 쓰는 운영자(admin) 모듈용이다.
  - 모집 여부는 `application`(모집 기간: 시작·끝 중 아는 쪽)으로 판단한다. 신청은 `applicationUrl ?? officialUrl`을 연다(2026-10-19 밤 "신청 확인 조건 완화").
  - 한 항목만으로 목록·상세를 그릴 수 있어 기관·프로그램을 따로 조회하지 않는다.
  - `application`(모집 기간, 없으면 null), `activitySchedule`(활동 기간·날짜 문구), `schedules[]`(개별 일정)는 Schedule이다.
- Schedule: `{id, title, kind, status, startAt, endAt, timeZone, startDate, endDateExclusive, dateLabel, address, conferenceUrl}`. 시각 일정은 `startAt`·`endAt` 중 하나 이상과 `timeZone`이 있고, 시각은 그 시간대 오프셋으로 표기한다(예: `2026-10-08T00:00:00+09:00`). 종일 일정은 `startDate`·`endDateExclusive`. 날짜 미정은 모두 null이고 `dateLabel`만 있다.

## 태그(얻을 수 있는 것) — 2026-10-19 사용자 결정

사용자: "프로그램이랑 활동에 태그가 달렸으면 좋겠어. 창업, 창의성, 개발지식, 프로젝트 개발, 협업, 멘토링처럼 사용자가 얻을 수 있는 요소들을 표현하게 해서 검색 결과에 같이 노출". 종류(category)가 "무엇인가"라면 태그는 "무엇을 얻는가"다.

- **어휘는 통제한다**: 자유 입력이 아니라 운영자가 관리하는 태그 사전 `tags(id, name 한글, name_en 영문 nullable, sort_order)`(V34). 사전은 어드민 편집 폼에서 **검색해 고르거나 바로 새 이름을 적어 추가**할 수 있다(2026-10-19 사용자 "편집시 직접 검색 및 직접입력"): 태그 선택은 검색형 다중 선택이고, 사전에 없는 이름을 적으면 저장 때 `POST /v1/admin/tags {name}`으로 만들어 연결한다(영문 이름은 비워 두고 뒤에 채운다, 띄어쓰기만 다른 같은 이름은 기존 태그로 합친다). 수집기는 영문 이름이 비어 있어도 한글 이름으로 고른다. 초기 사전: 창업 startup, 창의성 creativity, 개발지식 engineering, 프로젝트 개발 projectBuilding, 협업 collaboration, 멘토링 mentoring, 네트워킹 networking, 취업 career, 포트폴리오 portfolio, 발표 presentation, 글로벌 global, 장학·지원금 funding, 리더십 leadership, 문제해결 problemSolving. 어드민 `GET/POST/PATCH/DELETE /v1/admin/tags`(이름 변경은 연결 유지, 삭제는 연결도 지움). 공개 `GET /v1/tags` → `{items: Tag[]}`, Tag = `{id, name, nameEn}`.
- **연결**: `program_tags(program_id, tag_id)`, `activity_tags(activity_id, tag_id)`. 프로그램 태그는 운영자가 지정하고(`PATCH /v1/admin/programs/:id {tagIds, applyTagsToActivities?}`, 종류와 같은 일괄 규칙: 운영자가 태그를 지정하지 않은 활동에 합집합으로 더한다), 활동 태그는 수집기가 사전에서 고르고(지시문에 사전을 넘기고 사전 밖 이름은 버린다) 운영자가 바꾼다(`PATCH /v1/admin/activities/:id {tagIds}`, `POST`도 `tagIds?`, 운영자 지정은 `curated_fields`의 `tags`로 보호). 수집 결과 태그가 비면 프로그램 태그를 기본으로 쓴다(종류와 같다).
- **노출**: `Activity.tags: Tag[]`, `AdminActivity.tags`, `ProgramSummary.tags: Tag[]`(프로그램 태그가 있으면 그것, 없으면 **그 프로그램의 게시 활동 전체(최근 3년 창)** 태그의 합집합 — 2026-10-20 사용자 "활동 탭에서 프로그램에 태그가 안 보여" 지적으로 "열린 활동만"에서 넓힘; 열린 활동이 없는 프로그램(우아콘처럼 마감된 행사)도 태그가 보여야 한다 — 최대 6개, 사전 순). 발견 프로그램 행에 태그 칩 최대 3개 + `+n`, 펼친 활동 행에 태그 칩, 활동 상세에 전체. 어드민 활동·프로그램 행과 편집 폼에 태그 선택(검색 + 직접 입력으로 새 태그 추가, 다중).
- **검색**: `GET /v1/activities`·`GET /v1/discovery/programs`의 `q`가 활동 이름 외에 **태그 이름(한글·영문)** 에도 맞는다(`matchedActivityIds`에 포함). 필터 `tagIds?`(쉼표 목록, 모두 포함 AND). 발견 탭 검색창 아래 태그 칩 줄은 2026-10-19 밤 사용자 결정으로 없앴고(위 "프로그램 로고·태그 칩 줄 제거" 절) 태그는 `q` 검색으로만 쓴다. `tagIds`는 API에 남는다.
- 표기: 코드 없이 사전 id를 쓰고 이름은 한글+영문 쌍(2026-10-18 어휘 규칙). JSON 키는 camelCase.

## 개인정보 동의 — 2026-10-19 사용자 결정

사용자: "프로필 입력하기 전에 개인정보동의 필수로 구할 수 있게". 프로필(이름·연락처·이력)을 처음 저장하기 전에 **개인정보 수집·이용 동의**와 **이용약관 동의**를 필수로 받고 서버에 기록한다.

- 버전: 서버 설정 `dearby.consent.privacy-version`·`terms-version`(기본 `2026-10-19`, 웹 `/privacy`·`/terms` 시행일과 같다). 문서를 바꾸면 버전을 올리고, 버전이 다른 사용자는 다시 동의한다.
- 저장: V37 `user_consents(user_id pk → users on delete cascade, privacy_version, terms_version, consented_at timestamptz)`. 계정 삭제와 함께 지워진다.
- API: `POST /v1/me/consents {privacyVersion, termsVersion}`(Bearer) → 204. 현재 버전과 다르면 400 `CONSENT_VERSION`. `GET /v1/profile` 응답에 `consent: {privacyVersion, termsVersion, consentedAt}|null`과 `consentRequired: boolean`(기록이 없거나 버전이 다르면 true)을 더한다. **`PUT /v1/profile`은 `consentRequired`이면 403 `CONSENT_REQUIRED`**(이력 인증자료 업로드 등 프로필 하위 쓰기도 같다). 명함 발행·받은 명함·활동 신청 표시는 프로필이 있어야 하므로 자연히 뒤에 온다.
- 앱(iOS·Android): 첫 프로필 퍼널의 첫 단계 앞, 그리고 프로필 화면을 열었을 때 `consentRequired`이면 **동의 화면**을 전체 화면으로 보인다. 제목 "시작하기 전에 동의가 필요해요", 필수 체크 두 개(`개인정보 수집·이용에 동의합니다 (필수)`, `이용약관에 동의합니다 (필수)`), 각 항목의 `보기`는 `https://dearby.wid.io.kr/privacy`·`/terms`를 인앱 브라우저(iOS `SFSafariViewController`, Android Custom Tab)로 연다. 둘 다 체크해야 `동의하고 계속`이 켜지고, 누르면 `POST /v1/me/consents` 뒤 원래 흐름으로 간다. 동의 전에는 프로필 입력 단계로 갈 수 없다. 서버가 403 `CONSENT_REQUIRED`를 돌려주면 같은 화면을 보인다. 로그아웃 상태에서는 보이지 않는다(로그인 뒤 프로필에서).
- 웹: 프로필 입력이 없으므로 바뀌지 않는다(비로그인 저장 쿠키 안내는 `/privacy`에 이미 있다).

- 2026-10-20: iOS는 동의 화면을 프로필 탭 위의 **시트**로 띄우고 닫을 수 있다(닫으면 미동의, 저장하면 다시 뜸; "터치 영역·검색 경험·시트 닫기" 절). Android는 전체 화면이되 뒤로 가기로 나간다.

## 발견 카드 리뉴얼 — 2026-10-19 사용자 결정

사용자: "활동탐색 UI 리뉴얼. One UI 최신 **알림 UI** 애니메이션을 참고해 카드를 리뉴얼. 로고 좌측, 프로그램 이름 상단, 하위 활동 개수 우측, 태그는 프로그램 아래. 누르면 자연스럽게 펼쳐지고, 펼친 뒤 우측에 숫자 대신 화살표 버튼, 누르면 접힘. 활동 카드에는 로고 없이 활동명·모집 일정·활동 일정·활동 장소·모집 중 여부를 우상단에." 시안(https://claude.ai/artifact/TbCWAzRadSgpKm6tp88qE9) 승인("매우 좋아요") + "모집 기간은 지난 게 아니면 강조".

- **프로그램 카드(접힘)**: 둥근 흰 카드(모서리 20, 그림자 없음, 1px 구분선). 왼쪽 **로고 자리** 44: 프로그램 로고가 있으면 그것, 없으면 프로그램 이름 첫 글자의 둥근 아바타(민트 바탕·청록 글자). 프로그램 로고는 `ProgramSummary.logoUrl`(아래 "프로그램 로고" 절, 2026-10-19 밤). 가운데 위 **프로그램 이름**(굵게) → 조직 이름(보조색) → **태그 칩**(최대 3개 + `+n`, 없으면 줄 생략). 오른쪽 위 **활동 개수 배지**: 모집 중 + 모집 예정 수(청록 원형). 그 아래 보조 글자 한 줄: 모집 중이 있으면 `M월 d일 마감`, 예정만 있으면 `M월 d일 시작`, 둘 다 없으면 `최근 yyyy.M`.
- **펼치기**: 카드 전체를 누르면 펼쳐진다. 펼쳐지면 개수 배지 자리가 **위쪽 화살표 버튼**(44 터치 영역, 접근성 이름 `접기`)으로 바뀌고 누르면 접힌다(카드 머리를 다시 눌러도 접힌다). 애니메이션은 One UI 알림 전개 느낌: 높이는 스프링(Android `animateContentSize(spring(dampingRatio 0.8, stiffness MediumLow))`, iOS `.spring(response 0.35, dampingFraction 0.85)`), 내용은 짧게 페이드 + 위에서 살짝 밀려 들어옴(`expandVertically + fadeIn` / `.opacity + .move(edge: .top)`), 화살표 180° 회전. 동작 줄이기에서는 즉시 전환. 웹은 `grid-template-rows 0fr→1fr` + opacity 전환(320ms, 약간 오버슈트하는 베지어), 동작 줄이기에서 전환 없음.
- **활동 카드(펼친 안)**: 로고 없음. 왼쪽 위 **활동명**(굵게), 오른쪽 위 **모집 상태 칩**(`모집 중` 청록 채움 / `모집 예정` 민트 / `마감` 회색). 아래 라벨 + 값 세 줄: `모집` 모집 기간 문구(없으면 `미정`), `활동` 활동 기간 문구, `장소` 장소 이름(없으면 줄 생략). **강조 규칙**: 모집이 지나지 않았으면(모집 중·모집 예정) `모집` 줄의 값을 청록 굵은 글씨로 쓰고 줄 전체에 옅은 민트 바탕(모서리 8)을 깐다; 지난 모집은 보조색 그대로. 종류 소제목과 `모집 중`/`모집 예정`/`지난 활동` 묶음 규칙은 그대로(소제목은 작은 보조 글자). 카드를 누르면 활동 상세.
- **API**: 공개 `Activity`에 `location: string|null`(기존 문구)과 `place: {name, address, lat, lng}|null`(아래 "활동 장소")을 더한다.
- 색·글꼴은 기존 토큰. 접근성: 프로그램 카드는 `펼침/접힘` 상태를 가진 버튼, 활동 카드는 상태 칩을 라벨에 포함.

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
- **키보드 내리기(사용자 "키보드가 올라왔을 때 폼을 제외한 다른 곳 클릭 시 내려가게")**: 입력 필드 밖을 누르면 키보드가 내려간다. 스크롤로도 내려간다. iOS: 화면 루트에 `.scrollDismissesKeyboard(.interactively)`와, 빈 곳 탭 때 `@FocusState`를 `nil`로 만드는 탭 제스처(버튼·링크·필드의 탭은 그대로 전달, `simultaneousGesture` 아님 — 배경 전용). 앱 전체 입력 화면에 공통 modifier(`shared/ui`)로 적용한다. Android: 입력 화면 루트 `Modifier.pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }`(필드·버튼은 자기 탭을 먼저 받으므로 영향 없음) + LazyColumn/Column 스크롤에 `imeNestedScroll`·`imePadding`. 발견 검색창도 같다.

## 신청 확인 조건 완화·공개 applicationUrl — 2026-10-19 밤 사용자 결정

사용자: "활동 탐색에서 신청 사이트 들어간 후에 신청 여부를 확인하는 메시지를 표시해줘. 했다면 지난 기획처럼 내 활동에서 볼 수 있게 해줘." 기존 "신청 확인"(2026-10-06)은 **모집 중(open)일 때만** 버튼이 신청 링크가 되고 그때만 "신청하셨나요?"를 물었다. 운영 활동은 모집 기간이 없거나(우아콘·DAN) 예정이라 버튼이 `공식 안내 보기`로만 열려 질문이 한 번도 뜨지 않았다. 바꾼다:

- **공개 API**: `Activity.applicationUrl: string | null`을 더한다(운영자가 어드민에서 지정한 신청 링크 `activities.application_url`; 없으면 null). 수집기가 넣은 값도 그대로 노출한다. `https`만 허용(iOS·Android의 기존 `safeURL` 규칙).
- **버튼**: 상세 화면 CTA는 `applicationUrl ?? officialUrl`을 연다. 라벨은 모집 상태가 `closed`(모집 마감이 지났거나 활동이 끝남)면 `공식 안내 보기`, 그 외(`open`·`upcoming`·모집 정보 없음)는 `신청 사이트 열기`(applicationUrl이 있을 때) 또는 `공식 사이트에서 신청`(officialUrl만 있을 때).
- **질문 조건**: 모집 상태가 `closed`가 아니면 CTA를 눌러 외부 브라우저로 나간 뒤 앱이 다시 활성화될 때 "신청하셨나요?" 시트를 띄운다(open·upcoming·정보 없음 모두). closed면 묻지 않는다. 선택지·`다시 묻지 않기`·내 활동 표시 규칙은 2026-10-06 그대로. 이미 신청함인 활동은 묻지 않는다.
- **내 활동**: `신청했어요`를 고르면 2026-10-06 규칙대로 내 활동 탭의 진행 트랙에 들어간다(사용자 기록, 주최 측 확인 아님). 변경 없음.
- 웹: 변경 없음(웹에는 신청 확인 흐름이 없다).

## 일정 표시 우선순위 — 2026-10-19 밤 사용자 지적

사용자: "운영자 지정 날짜가 있음에도 클라이언트에서 수집 원문이 노출되고 있어." 원인: 세 클라이언트가 활동 기간·모집 기간을 그릴 때 `dateLabel`(수집 원문 문구)이 있으면 그것을 먼저 쓰고, 운영자가 지정한 구조화 날짜(`startDate`/`endDateExclusive`, `startAt`/`endAt`)를 뒤로 미뤘다. 서버는 운영자 지정 뒤에도 원문 `dateLabel`을 그대로 돌려준다(어드민 `기간 확인` 판정과 수집 피드백에 필요).

- **규칙(iOS·Android·웹 공통)**: Schedule을 글자로 보일 때 **구조화 날짜가 있으면 그것을 형식화**해 보인다(종일: `M월 d일 (요일)`·범위는 `~`, 연도는 **오늘(서울) 연도와 다른 날짜에만** 붙인다(`12월 30일 (수) ~ 2027년 1월 2일 (토)`); 시각: `M월 d일 (요일) HH:mm`, 같은 날 범위는 `M월 d일 (요일) 14:00 ~ 18:00`으로 날짜를 한 번만; 끝만 있으면 모집 기간은 `~ M월 d일 (요일) HH:mm 마감`, 활동 기간·세부 일정은 `~ M월 d일 (요일) HH:mm까지`). `dateLabel`은 **구조화 날짜가 전혀 없을 때만** 보이고, 그것도 없으면 `미정`/`일정 미확인`. 발견 활동 카드의 `모집`·`활동` 줄, 활동 상세의 `개최 일시`·모집 기간, 내 활동 행·진행 트랙 단계, 받은 명함·이력 선택의 활동 표시, 웹 활동 카드·상세 모두 같은 규칙.
- 서버·어드민은 바꾸지 않는다. 어드민은 원문과 지정값을 둘 다 보이는 기존 규칙 유지.

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
- **의미별 고정 이름**(둘 다 이 표만 쓴다): 일정·달력 `calendar`/`Event`, 시각 `clock`/`Schedule`, 장소 `mappin.and.ellipse`/`Place`, 외부 링크 `arrow.up.right`/`AutoMirrored.Rounded.OpenInNew`, 수정 `pencil`/`Edit`, 삭제 `trash`/`Delete`, 추가 `plus`/`Add`, 제거 `minus.circle`/`RemoveCircle`, 검색 `magnifyingglass`/`Search`, 지우기 `xmark.circle.fill`/`Cancel`, 닫기 `xmark`/`Close`, 뒤로 `chevron.left`/`AutoMirrored.Rounded.ArrowBack`, 펼침 `chevron.down`/`ExpandMore`, 성공·확인 `checkmark.circle`(상태는 `.fill`)/`CheckCircle`, 오류 `exclamationmark.triangle`/`Warning`, 정보 `info.circle`/`Info`, 알림 `bell`/`Notifications`, QR `qrcode`/`QrCode`, 명함 `person.text.rectangle`/`Badge`, 프로필 `person.crop.circle`/`AccountCircle`, 활동 둘러보기 `safari`/`Explore`, 내 활동 `checklist`/`Checklist`, 받은 명함 `tray.full`/`Inbox`, 공유 `square.and.arrow.up`/`Share`, 지도 `map`/`Map`, 전화 `phone`/`Call`, 메일 `envelope`/`Mail`, 첨부 `paperclip`/`AttachFile`, 사진 `photo`/`Image`, 눈 `eye`/`Visibility`, 잠금 `lock`/`Lock`, 기기 `iphone`/`Smartphone`, 위로 `arrow.up`/`ArrowUpward`, 조직 `building.2`/`Business`, 변경 이력 `clock.arrow.circlepath`/`History`, 단계 종류(마감 `flag`/`Flag`, 발표 `megaphone`/`Campaign`, 면접 `person.wave.2`/`RecordVoiceOver`, 기타 `pencil.line`/`EditNote`), 연락처 종류(채팅 `bubble.left`/`ChatBubble`, 사진 `camera`/`CameraAlt`, 링크 `link`/`Link`), 선택 안 됨 `circle`/`RadioButtonUnchecked`, 설정 `gearshape`/`Settings`, 잠금 안내 `lock`/`Lock`. 보내기·신청 `paperplane`/`Send`, 진행 단계 종류는 위 단계 항목을 따른다. 표에 없는 뜻이 생기면 표에 먼저 더한다.
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
- **API(`auth` 모듈, Bearer)**:
  | 메서드·경로 | 응답 |
  | --- | --- |
  | GET /v1/me | 200 `{id, createdAt, signInMethods: {passkeys: n, apple: bool, google: bool}}` |
  | GET /v1/me/identities | 200 `{items: [{provider: "apple"|"google", email, displayName, linkedAt}]}` |
  | DELETE /v1/me/identities/{provider} | 204. 마지막 수단이면 409 `LAST_SIGN_IN_METHOD`. 애플은 refresh token 폐기(계정 삭제와 같은 경로), 구글은 행 삭제 |
  | GET /v1/me/passkeys | 200 `{items: [{id(credentialId), label, createdAt, lastUsedAt, backedUp, transports[]}]}` |
  | PATCH /v1/me/passkeys/{id} | `{label}`(1~60자) → 200 항목 |
  | DELETE /v1/me/passkeys/{id} | 204. 마지막 수단이면 409 `LAST_SIGN_IN_METHOD`. 남이 가진 id면 404 |
  "마지막 수단" 판정: 패스키 수 + 연결된 OAuth 수가 1이면 그 하나는 지울 수 없다. 이미 있는 `POST /v1/auth/passkeys/registration/options`·`registration`은 Bearer가 있으면 그 계정에 패스키를 더한다(없으면 기존 가입 흐름).
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
- **연락처 행은 라벨 대신 왼쪽 아이콘(사용자 2026-10-20 "연락처의 경우 전화번호·이메일 같은 라벨 대신 좌측에 아이콘으로")**: 프로필 편집·퍼널의 연락처 입력 행과 명함·프로필의 연락처 표시 행은 위에 쌓는 글자 라벨을 두지 않고 행 왼쪽에 24 보조색 글리프(전화 `phone`/`Call`, 메일 `envelope`/`Mail`, 카카오톡 `bubble.left`/`ChatBubble`, Instagram `camera`/`CameraAlt`, GitHub `chevron.left.forwardslash.chevron.right`/`Code`, Behance `paintpalette`/`Palette`, 기타 `link`/`Link`)를 둔다. 입력 글자는 `.title3`/`titleLarge` 그대로, placeholder가 종류 이름("전화번호", "이메일", "@아이디" 등)을 대신 말한다. 접근성 라벨은 종류 이름을 유지한다. 행 전체 탭 → 포커스 규칙 동일.
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
- **발견 검색·접힘**: 직접 만든 접히는 검색 영역 대신 `.searchable(text:placement: .navigationBarDrawer(displayMode: .automatic), prompt: "프로그램·활동·태그 검색")` — **검색창은 처음부터 보이고 아래로 스크롤하면 가려진다**(사용자 2026-10-20 "탐색 탭에서 검색 메뉴는 처음에 보이게 해 주고 스크롤 내렸을 때 가려줘"). 처음 진입·새로 고침 뒤 맨 위 상태에서 검색창이 접혀 있으면 안 되므로, 스크롤 위치 0에서는 드로어가 펼쳐진 상태로 시작하도록 보장한다(`.automatic`이 처음에 숨긴다면 맨 위에서는 `.always`, 사용자가 스크롤을 시작하면 `.automatic`으로 전환). — 아래로 스크롤하면 시스템이 검색 막대를 접고 위로 당기면 다시 보이는 iOS 기본 동작이 "enter always" 요구를 대신한다. `마감 숨기기` 토글은 목록의 **첫 행**(전체 행 터치, 라벨+스위치)으로 내려가 내용과 함께 스크롤한다. 풀투 리프레시·맨 위로 버튼·구분선 규칙은 그대로(구분선은 시스템 바가 알아서 그림).
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
- **참여 확정 표시**: 추가한 뒤 내 활동 상세의 진행 트랙에서 기존 `통과`/`참여 확정` 표시 규칙 그대로 쓴다(새 상태 없음). 지난 활동(활동이 끝난 것)을 추가하면 `끝난 활동` 묶음에 들어간다.
- 신청 확인 시트(외부 브라우저에서 돌아올 때)는 그대로 둔다. 둘 다 같은 기기 저장소를 쓴다.
- **빼기는 눈에 보이게(사용자 2026-10-20 "내 활동에 추가하고 삭제가 가능해야 해")**: 길게 누르기·작은 펼침 아이콘 같은 숨은 동작에만 두지 않는다. ① 활동 상세: 이미 내 활동에 있으면 `내 활동에서 보기` 버튼 아래에 텍스트 버튼 `내 활동에서 빼기`(빨강, iOS `.borderless` role destructive / Android `TextButton` error 색)를 항상 보이게 두고, 누르면 확인("내 활동에서 뺄까요? 단계 표시와 참여 확정도 함께 지워져요.") 뒤 뺀다. ② 내 활동 탭 목록: 각 행에서 빼기 — iOS 왼쪽으로 밀기 `.swipeActions` `빼기`(목록이 `List`가 아니면 행 `contextMenu`와 함께 상세의 버튼으로 충분하도록 ③을 둔다), Android 행 끝 `MoreVert` → `DropdownMenu` `내 활동에서 빼기`. ③ 진행 단계 상세(내 활동 행을 눌렀을 때): 맨 아래 `내 활동에서 빼기` 텍스트 버튼(같은 확인). 뺀 뒤 그 화면은 닫고 목록으로 돌아간다. 실수 방지 확인은 항상 둔다.

## 프로필 읽기/수정 모드·상단 바 메뉴 — 2026-10-20 사용자 결정

사용자: "내 프로필에서 수정이 바로 가능하게 하지 말고, 상단 탭바에 수정 버튼을 눌렀을 때 노출되도록. 수정과 설정·알림 버튼을 제외하고 알림 버튼 오른쪽에 햄버거 메뉴 버튼을 눌렀을 때 드롭다운으로 라벨과 함께 표시." iOS·Android.

- **프로필 탭은 읽기 모드가 기본**: 이름·직무·소개·연락처·활동 이력을 읽기 전용 행(명함 본문과 같은 표현: 연락처는 왼쪽 아이콘 + 값, 이력은 기간·제목·역할·확인 배지)으로 보인다. 입력 칸·저장 막대는 보이지 않는다. 프로필이 아직 없으면(버전 0) 기존 입력 퍼널이 그대로 뜬다.
- **수정 모드**: 상단 바의 `수정`(`pencil`/`Edit`, 텍스트 버튼 또는 아이콘, 접근성 `수정`)을 누르면 같은 화면이 편집 폼(플로팅 라벨 입력·이력 추가·연락처 추가)으로 바뀌고 아래에 `저장`/`원래대로` 막대가 나타난다. 상단 `수정`은 `완료`로 바뀌며 변경이 없으면 읽기 모드로 돌아가고, 변경이 있으면 저장 여부를 묻는 확인(저장 / 버리기 / 취소). 저장 성공 시 읽기 모드로 돌아간다. 모드는 앱 실행 중 메모리.
- **상단 바 구성(네 탭 공통)**: 오른쪽에 [탭 고유 주 행동(프로필의 `수정`만)] [설정] [알림] [**메뉴** `line.3.horizontal`/`Menu`] 순. 메뉴를 누르면 드롭다운(iOS `Menu` → 항목은 `Label(제목, systemImage:)`, Android `DropdownMenu` → `DropdownMenuItem(leadingIcon + text)`)에 나머지 보조 행동이 **아이콘 + 라벨**로 나온다: 프로필 — `변경 이력`, `명함 만들기`; 내 활동 — 항목 없음(예시 데이터셋이 없어 `예시` 배지는 지우고 메뉴도 숨긴다; 2026-10-20 구현 확인); 발견·명함 — 지금은 항목이 없으면 메뉴 버튼을 숨긴다. 설정·알림·수정은 메뉴에 넣지 않는다.
- iOS는 `ToolbarItemGroup(.topBarTrailing)` 안에 순서대로, Android는 `TopAppBar.actions` 안에 순서대로. 메뉴 아이콘 접근성 이름 `더 보기`.

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

## 활동 장소 — 2026-10-19 사용자 결정

사용자: "장소는 어드민에서 추가할 수 있으면 좋겠고 geocode, 주소 검색해서 좌표 가지고 올 수 있으면". 활동의 구조화된 장소를 둔다.

- 저장: ACTIVITY 슬롯 일정의 기존 열 `address_line1`(장소 이름), `address_line2`(도로명 주소), `lat`, `lng`(DB 제약: 넷 다 있거나 넷 다 없음). `activities.location`(수집 문구)은 그대로 두고, 장소가 지정되면 공개 응답의 `location`도 장소 이름으로 채운다.
- 공개 `Activity.place: {name, address, lat, lng}|null`. 앱·웹 활동 상세의 `장소` 절에 이름·주소를 보이고 누르면 지도 앱으로 연다(iOS `maps://?q=`, Android `geo:lat,lng?q=`, 웹 `https://maps.google.com/?q=`). 발견 활동 카드의 `장소` 줄은 이름만.
- 어드민: 활동 편집·추가 폼의 `장소` — 검색창에 장소명이나 주소를 적으면 `GET /v1/admin/geocode?q=`로 후보(이름·주소·좌표)를 받아 고른다. 고르면 이름·주소·좌표가 채워지고(이름은 고친 뒤 저장 가능), `지우기`로 비운다. `PATCH /v1/admin/activities/:id {place: {name, address, lat, lng} | null}`, `POST`도 `place?`. 운영자 지정(`schedules.curated`는 기간 전용이므로 `curated_fields`에 `place`)으로 재수집이 덮어쓰지 않는다.
- 지오코딩 서버: `GET /v1/admin/geocode?q=<문자열>` → 200 `{items: [{name, address, lat, lng, source}]}`(최대 10). 공급자는 `KAKAO_REST_API_KEY`가 있으면 Kakao Local(키워드 장소 검색 + 주소 검색 합침, `source: "kakao"`), 없으면 OpenStreetMap Nominatim(`source: "osm"`, 1초에 1회·`User-Agent: Dearby-admin` 규칙 준수). 키는 서버 `.env.prod`에만. 어드민 응답에 `source`를 보여 어느 공급자인지 알 수 있게 한다.

## 활동 종류(category) — 2026-10-19 사용자 결정

사용자: "활동 종류도 추가해줬으면 좋겠어. 부트캠프, 컨퍼런스, 워크샵, 스터디, 강연 등으로 구분하고 프로그램 하위에서 그룹화되도록". 활동에 **종류** 하나를 둔다.

- 값(API 코드 → 한글 이름): `BOOTCAMP` 부트캠프, `CONFERENCE` 컨퍼런스, `WORKSHOP` 워크샵, `STUDY` 스터디, `CLUB` 동아리(연합동아리 등), `LECTURE` 강연, `HACKATHON` 해커톤, `CONTEST` 공모전, `MEETUP` 밋업, `OTHER` 기타. 없으면 `null`(미분류). 저장은 `activities.category varchar(20) null`(V31, check 제약). 코드는 `participationType`처럼 대문자 영문이고 한글 이름은 클라이언트가 붙인다.
- 공개 `Activity`(활동 조회·발견의 펼친 활동)와 `AdminActivity`에 `category: string|null`을 더한다. `GET /v1/activities`·`GET /v1/discovery/programs`에 `category?` 필터(코드 하나 또는 쉼표 목록)를 더한다.
- 수집: 추출 지시문이 `category`를 함께 뽑는다(본문의 성격으로 판단, 모르면 null). 어드민이 바꾼 값은 `curated_fields`의 `category`로 보호된다.
- 화면: 프로그램 하위에서 종류별로 묶는다. **어드민**은 펼친 프로그램 안에서 종류 소제목(부트캠프 (n) …, 미분류는 맨 뒤) 아래에 활동 행을 둔다. **앱·웹 발견**은 펼친 프로그램 안의 활동을 같은 순서의 종류 소제목으로 묶고(종류가 하나뿐이면 소제목 생략), 활동 행에는 종류를 다시 쓰지 않는다. 활동 상세에는 종류 칩 하나. 종류 순서는 위 값 순서, 각 종류 안의 활동 순서는 "발견 프로그램 묶음"의 정렬 키(모집 마감 → 활동 시작 → 없음, 이름순)다.
- 프로그램 기본 종류: `programs.category`(V33)를 운영자가 지정하면 수집·운영자 생성에서 종류가 비었을 때 기본값으로 쓰고, 운영자가 지정하지 않은 활동에 일괄 적용할 수 있다(admin-v1.md "프로그램 기본 종류"). `ProgramSummary.category`로 발견 머리글에 보인다.
- 변경 이력: 2026-10-19 최초 9종 [`11ae3025`](https://github.com/fixabley/dearby/blob/11ae3025/shared/contracts/native-v1.md#활동-종류category--2026-10-19-사용자-결정) → 같은 날 `CLUB` 동아리 추가(사용자 "연합동아리 같은 동아리 활동도 있어"), 정렬 규칙 두 번 보완(위 정렬 키).

## 발견 프로그램 묶음 — 2026-10-18 사용자 결정 (2026-10-19 보완)

발견 탭은 활동 카드 목록 대신 **프로그램 단위**로 보여 주고, 누르면 아코디언처럼 펼쳐져 지금 모집 중인 활동이 보인다. iOS·Android·웹 공통. 활동 상세와 `내 활동`은 바뀌지 않는다.

### 규칙

- **열린 활동 판정은 서버 한 곳에서만 한다**(2026-10-19). 게시 활동이고 (a) 모집 기간이 시각 일정이면 `start_at ≤ now`(없으면 열림) 이고 `end_at > now`(없으면 열림), (b) 모집 기간이 날짜 일정이면 서울 날짜로 `start_date ≤ today < end_date_exclusive`, (c) 모집 기간이 없으면 활동 시작(시각 또는 서울 0시)이 `now`보다 뒤. 같은 규칙이 `GET /v1/activities?open=true`에도 적용된다. 앱·웹은 열린 활동을 스스로 걸러내지 않고, 활동 상세의 `모집 중/마감/예정` 라벨도 날짜 일정을 같은 규칙으로 계산한다(시각이 없다고 `확인 필요`로 두지 않음).
- **정렬 키**(활동 공통): 모집 마감(APPLICATION 슬롯의 끝 시각·날짜) → 모집 마감을 알 수 없으면(모집 기간이 없거나 끝이 비어 있으면) 활동 시작(ACTIVITY 슬롯의 시작 시각·날짜) → 둘 다 없으면 맨 뒤, 같으면 이름순. SQL은 `coalesce(app.end_at, app.end_date_exclusive 서울 0시, 활동 시작)`. 프로그램 순서(`nearestRecruitmentEndAt`), 펼친 활동, 어드민 묶음 보기가 모두 이 키를 쓴다.
- **마감 숨기기** 토글(기본 켬, 메모리). 2026-10-19 밤 사용자 지적 "마감 숨기기인데 모집 예정은 왜 숨겨?"로 뜻을 바로잡음: 켜면 **마감된 활동만 숨긴다**. 즉 `openOnly=true`는 **모집 중이거나 모집 예정**(모집 기간이 아직 시작되지 않았거나, 모집 기간이 없고 활동 시작이 아직 오지 않은) 활동이 있는 프로그램을 돌려주고, 마감된 활동뿐인 프로그램만 뺀다. `ProgramSummary`에 `upcomingActivityCount`(모집 예정 수)와 `nearestRecruitmentStartAt`(모집 예정 활동의 가장 이른 모집 시작, 없으면 활동 시작)을 더하고, 순서는 모집 중 프로그램(정렬 키) → 모집 예정 프로그램(가장 이른 시작) → (끄면) 나머지 최근 활동 순. 머리글은 모집 중이 있으면 `모집 중 n개 · 마감`, 없고 모집 예정이 있으면 `모집 예정 n개 · M월 d일 시작`, 둘 다 없으면 `최근 활동 yyyy.M`. 펼치면 `모집 중`(open=true)과 `모집 예정` 묶음은 토글과 무관하게 항상 보이고, `지난 활동`(최근 3년)은 토글을 껐을 때만 보인다. 자동 펼침(열린 활동 1개)은 그대로. 끄면 끄면 **최근 3년 안에 활동이 있었던 프로그램**(게시 활동의 활동 시작·모집 마감·생성 시각 중 하나가 `now − 3년` 이후)을 모두(2026-10-19 저녁 사용자 결정으로 2년 → 3년). 순서는 열린 프로그램 먼저(정렬 키), 그 뒤는 가장 최근 활동 시작이 늦은 순.
- **머리글**: 프로그램 이름, 조직 이름, 종류 칩(프로그램 기본 종류가 있을 때), `모집 중 n개 · M월 d일 (요일) 마감`(열린 활동이 있을 때) 또는 `최근 활동 yyyy.M`(`latestActivityAt`). 접근성에서 `펼침`/`접힘` 상태를 가진 버튼으로 읽힌다.
- **펼침**: 머리글을 누르면 `GET /v1/activities?programId=<id>&open=true`로 그 프로그램의 모집 중 활동을 불러와 기존 활동 카드로 보인다. 종류별 소제목으로 묶고(아래 "활동 종류", 종류가 하나뿐이면 소제목 생략) 각 묶음 안은 정렬 키 순. 그 아래 **다른 활동**(같은 `GET /v1/activities?programId=`, 20개, 종류 소제목 없음)을 `모집 예정`(모집 시작 또는 활동 시작이 아직 오지 않은 것, 가까운 순)과 `지난 활동`(최근 3년, 활동 시작 최근 순) 두 묶음으로 보인다. `모집 예정`은 토글과 무관하게 항상, `지난 활동`은 토글을 **껐을 때만** 보인다(2026-10-19 밤 정정; 그 전에는 켜진 상태에서 모집 중이 없을 때만 두 묶음을 보였다). 모집 중·모집 예정·지난 활동이 모두 비면 "지금 모집 중인 활동이 없어요"만 남긴다. 빈 묶음은 생략한다. 기본은 접힘이며, 토글이 켜진 상태에서 열린 활동이 1개인 프로그램과 검색어가 활동 이름에 맞은 프로그램은 펼친 채로 시작한다. 펼침 상태는 앱 실행 중 메모리에만 두고, 동작 줄이기에서는 애니메이션 없이 바뀐다.
- **검색**: 검색어는 프로그램 이름·영문 이름, 조직 이름, 열린 활동 이름에 부분 일치(대소문자 무시). 활동 이름으로만 맞은 프로그램도 목록에 나온다. 태그 이름 검색과 태그 필터는 "태그" 절(설계 완료, 구현 예정).
- **유형(참가등록·선발)**: 세 플랫폼의 발견 화면에는 유형 칩이 없다. API의 `participationType` 필터는 칩을 다시 넣을 때 쓰도록 둔다. 칩이 생기면 펼친 활동과 열린 활동 수에 함께 적용하고 맞는 활동이 없는 프로그램은 숨긴다.

### API

| 메서드·경로 | 인증 | query | 응답 |
| --- | --- | --- | --- |
| GET /v1/discovery/programs | 없음 | `q?`(≤100자), `participationType?`(REGISTRATION\|SELECTION), `category?`(종류 코드, 쉼표 목록), 대상자 필터(아래 "대상자 조건"), `openOnly?`(기본 `true`), `cursor?`(정수 오프셋, 기본 0), `size?`(1–100, 기본 20) | 200 `{items: ProgramSummary[], nextCursor: number\|null}` |
| GET /v1/activities | 없음 | 기존 query에 `open?`(기본 false: 전부, `true`: 위 열린 활동만), `category?` | 200 `{items: Activity[], nextCursor}` |

- 경로가 `/v1/programs`가 아닌 이유: `/v1/programs`는 이력 선택 창의 활동 정보 검색(로그인)이 이미 쓴다. `nextCursor`는 정렬 결과의 정수 오프셋이다(활동 조회의 id 커서와 다르다).
- ProgramSummary: `{id, name, nameEn: string|null, organization: {id, name}, category: string|null, openActivityCount, nearestRecruitmentEndAt: ISO-8601(UTC)|null, latestActivityAt: ISO-8601|null, matchedActivityIds: number[]}`. `openActivityCount`는 열린 활동 수(`participationType`·`category`·대상자 2차 판정까지 통과한 것)이고 `openOnly=false`에서는 0일 수 있다. `nearestRecruitmentEndAt`은 열린 활동의 정렬 키 최솟값(없으면 null), `latestActivityAt`은 조회 범위 안 게시 활동의 가장 최근 활동 시작. `matchedActivityIds`는 `q`가 활동 이름에 맞은 열린 활동 id(없으면 빈 배열).
- 펼쳤는데 모집 중 활동이 없으면 "지금 모집 중인 활동이 없어요" 아래에 그 프로그램의 다른 활동(`GET /v1/activities?programId=`, 20개)을 `모집 예정`(모집 시작 또는 활동 시작이 아직 오지 않음, 가까운 순)과 `지난 활동`(최근 3년, 활동 시작 최근 순) 두 묶음으로 보인다. 둘 다 없으면 안내만(2026-10-19 PR #272, SK AIGEN 1기처럼 모집이 11/1에 시작하는 활동이 어느 묶음에도 들지 않던 문제).
- `Cache-Control: no-store`·오류 형식은 활동 조회와 같다.
- 변경 이력: 2026-10-18 최초(서버는 시각 모집 기간만 보고 열림 판정, 펼친 활동은 클라이언트가 걸러냄, 열린 활동 1개 이상인 프로그램만) [`b14acca1`](https://github.com/fixabley/dearby/blob/b14acca1/shared/contracts/native-v1.md#발견-프로그램-묶음--2026-10-18-사용자-결정) · 2026-10-19 정렬 키에 활동 시작 대체(사용자 "모집 기간이 미정인 활동들도 활동 시작일을 모집 종료일로 취급") [`6e2834e0`](https://github.com/fixabley/dearby/blob/6e2834e0/shared/contracts/native-v1.md) · 2026-10-19 날짜 모집 기간을 서버가 "없음"으로, 앱이 `확인 필요`로 다뤄 펼친 활동이 비던 버그(운영 활동 126)를 계기로 판정 일원화·`open=true`·`openOnly`·`지난 활동` [`352f20b7`](https://github.com/fixabley/dearby/blob/352f20b7/shared/contracts/native-v1.md#발견-프로그램-묶음--2026-10-18-사용자-결정).

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
- **내 명함 탭**: 가로 스크롤 타일 대신 2열 격자. 첫 칸 `+ 새 명함`, 각 칸은 명함 이름(굵게)·프로필 이름·직무 한 줄. 선택한 칸은 민트 바탕·청록 테두리. 격자 아래에 선택한 명함의 카드(위 규칙)와 `이 명함 고치기` 버튼, 그 아래 위험 색 `이 명함 지우기`(확인 대화상자 "공개 링크도 더 열리지 않아요. 되돌릴 수 없어요." → `DELETE /v1/cards/:id`, 2026-10-18 사용자 요청 "내 명함 삭제가 없어"), 그리고 `QR 보이기`("명함 교환"). 지운 뒤 다음 명함(가장 새 것)을 고르고, 마지막 명함이면 첫 명함 안내로 돌아간다. 명함이 없을 때의 안내 카드는 유지.
- **받은 명함 상세**: 같은 카드. 카드 위 받은 맥락 줄과 카드 아래 `내 명함도 건네기`는 "명함 교환"(2026-10-19)을 따른다.
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
