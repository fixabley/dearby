# Admin API v1 — 2026-10-15

관리자가 카탈로그(조직·프로그램·활동)를 찾고 프로그램과 수집 설정을 넣고 수집 초안을 게시하는 API다. Kotlin `admin` 모듈(별도 컨테이너)이 제공하고, 어드민 웹(`apps/admin`)이 쓴다. Supabase는 쓰지 않는다.

## 연결

- 운영: `https://api.dearby.wid.io.kr/v1/admin/*` (nginx가 admin 컨테이너로 보낸다). 어드민 웹은 같은 출처 프록시(`/api/admin/*`, `/api/auth/*`)로 부른다. CORS는 열지 않는다.
- 로그인은 패스키다(공통 auth 모듈, `native-v1.md` "패스키 로그인"). 운영은 `/v1/auth/*`가 api 컨테이너로 간다. 응답의 `user: {id, roles}`로 화면 권한을 정하고, 받은 access token을 `Authorization: Bearer`로 보낸다. access token이 만료돼 401이면 refresh를 한 번 시도하고, refresh가 401이면 로그인 화면으로 돌아간다.
- 모든 경로는 `roles` 클레임에 `ADMIN`이 있어야 한다. 토큰이 없거나 무효면 401, `ADMIN`이 아니면 403. 첫 관리자는 패스키로 가입한 뒤 DB `user_roles`에 `ADMIN`을 직접 넣는다(관리자 지정 화면은 없다).
- 오류 본문은 RFC 9457 ProblemDetail(`detail`이 사용자용 문장).

## 목록 공통

`GET` 목록은 `q?`, `cursor?`, `size?`(1–100, 기본 20)를 받고 `{items, nextCursor}`를 돌려준다. id 오름차순이고 `nextCursor`를 다음 요청의 `cursor`로 보낸다(그 id 다음부터). `nextCursor`가 null이면 마지막 페이지. `q`는 이름에서 대소문자 없이 부분 일치로 찾는다(활동은 이름·설명, 단어 유사도 포함 — `/v1/activities`와 같다). id는 숫자다.

## 조직

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/admin/organizations | `q?`, `parentId?`, `rootsOnly?`, `cursor?`, `size?` | 200 `{items: Organization[], nextCursor}` |
| POST /v1/admin/organizations | `{name, parentOrganizationId?}` | 201 Organization. 이름이 비었거나 상위 조직이 없으면 400 |
| PATCH /v1/admin/organizations/:id | `{name?, parentOrganizationId?, clearParent?}` | 200 Organization. 없으면 404. 자기 자신이나 자기 하위 조직을 상위로 지정하면 400 |

- Organization: `{id, name, parentOrganizationId, childCount, programCount, ancestors}`. `childCount`·`programCount`는 트리에서 펼칠 것이 있는지 보여 주는 값이다. `ancestors`는 최상위부터 바로 위 조직까지의 `{id, name}` 목록(최상위 조직은 `[]`)으로, 검색 결과의 상위 이름 표시와 경로 자동 펼침에 쓴다.
- 조직도(트리): `rootsOnly=true`로 최상위 조직을, `parentId=<id>`로 그 조직의 하위 조직을 받는다. 프로그램은 `GET /programs?organizationId=`, 활동은 `GET /activities?programId=`로 펼친다(2026-10-15 사용자 요청으로 기존 Supabase 어드민의 펼침 조직도 복원).
- PATCH는 보낸 필드만 바꾼다. 최상위로 옮기려면 `clearParent: true`.

## 프로그램

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/admin/programs | `q?`, `organizationId?`, `cursor?`, `size?` | 200 `{items: Program[], nextCursor}` |
| POST /v1/admin/programs | `{organizationId, name, collection?: CollectionSettingsInput}` | 201 Program. 이름이 비었거나 조직이 없으면 400 |
| PATCH /v1/admin/programs/:id | `{name?, organizationId?}` | 200 Program. 없으면 404, 조직이 없으면 400 |
| GET /v1/admin/programs/:id/collection-settings | — | 200 CollectionSettings. 프로그램이 없으면 404 |
| PUT /v1/admin/programs/:id/collection-settings | CollectionSettingsInput | 200 CollectionSettings |

- Program: `{id, name, organization: {id, name}, collection: CollectionSettings}`
- CollectionSettingsInput: `{officialUrl?, allowedHosts: string[], intervalDays, enabled}`
  - `officialUrl`은 https만. `allowedHosts`는 정확한 호스트 이름(예: `letswift.kr`, 스킴·경로·포트 없음, 소문자로 저장). `intervalDays` ≥ 1. 어기면 400.
- CollectionSettings: `{configured, officialUrl, allowedHosts, intervalDays, enabled, officialOnly, lastSucceededOn}`
  - `configured`: 설정 행이 있는지. 없으면 기본값(`officialUrl` null, `allowedHosts` [], `intervalDays` 1, `enabled` true)을 돌려준다.
  - `officialOnly` = `allowedHosts`가 비어 있지 않음. **false면 매일 밤 "프로그램 이름 + 주최 기관"으로 웹 전체를 검색한다**(공식 도메인 수집보다 토큰이 약 5배, 공식 출처가 아닌 페이지가 섞인다). 어드민 웹은 비어 있을 때 이 점을 보여 준다.
  - `lastSucceededOn`: 마지막으로 수집에 성공한 한국 날짜(없으면 null). 이 날짜 + `intervalDays`가 지나야 다시 수집한다.

## 활동

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/admin/activities | `q?`, `organizationId?`, `programId?`, `publicationStatus?`(`DRAFT`·`PUBLISHED`), `cursor?`, `size?` | 200 `{items: AdminActivity[], nextCursor}`. 초안 포함 |
| PATCH /v1/admin/activities/:id/publication-status | `{publicationStatus}` | 200 AdminActivity. 없으면 404 |

AdminActivity: 공개 Activity(`native-v1.md` "활동 조회")의 필드 `{id, name, description, organization, program, officialUrl, imageUrl, application, activitySchedule, schedules}`에 운영자용 필드를 더한다.

- `publicationStatus`: `DRAFT`(수집 초안, 공개 API에 안 보임) / `PUBLISHED`
- `officialSource`: 출처가 프로그램의 공식 도메인인지. false면 웹 전체 검색에서 나온 것이라 출처를 확인하고 게시해야 한다
- `applicationUrl`, `location`, `cost`, `audience`, `sourceCheckedAt`: 수집이 원문에서 확인한 값(없으면 null)

## 아직 없는 것

활동 내용 편집, 프로그램·조직 삭제, 수집 기록(`collection_runs`) 조회, 감사 로그. 기존 Supabase 어드민의 자격요건 편집기·수집 작업 큐·재확인 화면은 이 API로 옮기지 않는다.
