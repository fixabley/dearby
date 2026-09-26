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
