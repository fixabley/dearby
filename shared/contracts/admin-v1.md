# Admin API v1 — 2026-10-15

관리자가 카탈로그(조직·프로그램·활동)를 찾고 프로그램과 수집 설정을 넣고 수집 초안을 게시하는 API다. Kotlin `admin` 모듈(별도 컨테이너)이 제공하고, 어드민 웹(`apps/admin`)이 쓴다. 외부 BaaS는 쓰지 않는다.

## 연결

- 운영: `https://api.dearby.wid.io.kr/v1/admin/*` (nginx가 admin 컨테이너로 보낸다). 어드민 웹은 같은 출처 프록시(`/api/admin/*`, `/api/auth/*`)로 부른다. CORS는 열지 않는다.
- 로그인은 패스키다(공통 auth 모듈, `native-v1.md` "패스키 로그인"). 운영은 `/v1/auth/*`가 api 컨테이너로 간다. 응답의 `user: {id, roles}`로 화면 권한을 정하고, 받은 access token을 `Authorization: Bearer`로 보낸다. access token이 만료돼 401이면 refresh를 한 번 시도하고, refresh가 401이면 로그인 화면으로 돌아간다.
- 모든 경로는 `roles` 클레임에 `ADMIN`이 있어야 한다. 토큰이 없거나 무효면 401, `ADMIN`이 아니면 403. 첫 관리자는 패스키로 가입한 뒤 DB `user_roles`에 `ADMIN`을 직접 넣는다(관리자 지정 화면은 없다).
- 오류 본문은 RFC 9457 ProblemDetail(`detail`이 사용자용 문장).

## 목록 공통

`GET` 목록은 `q?`, `cursor?`, `size?`(1–100, 기본 20)를 받고 `{items, nextCursor}`를 돌려준다. id 오름차순이고 `nextCursor`를 다음 요청의 `cursor`로 보낸다(그 id 다음부터). `nextCursor`가 null이면 마지막 페이지. `q`는 한글·영문 이름에서 대소문자 없이 부분 일치로 찾는다(활동은 이름·설명, 단어 유사도 포함 — `/v1/activities`와 같다). id는 숫자다.

## 다국어 — 2026-10-15 사용자 결정

- 조직·프로그램·활동은 한글 이름(`name`, 필수)과 영문 이름(`nameEn`, 선택)을, 활동은 한글 설명(`description`)과 영문 설명(`descriptionEn`, 선택)도 가진다. 응답은 둘 다 준다. 공개 API는 요청 언어 값 하나만 준다(`native-v1.md` "활동 조회").
- 만들기에서 `nameEn`을 비우거나 보내지 않으면 null. PATCH에서 보내지 않으면 그대로, 빈 문자열이면 지운다. 앞뒤 공백은 지운다.
- 영문 이름도 같은 계위에서 영문 이름끼리 아래 "이름 중복" 규칙을 따른다(V13 유일 인덱스).
- 수집 초안은 수집 때 모델이 영문 이름·설명을 번역해 넣는다(검토 대상). 초안은 다시 수집하면 덮어써지므로, 고친 내용을 지키려면 게시한다.

## 조직

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/admin/organizations | `q?`, `parentId?`, `rootsOnly?`, `cursor?`, `size?` | 200 `{items: Organization[], nextCursor}` |
| POST /v1/admin/organizations | `{name, nameEn?, parentOrganizationId?}` | 201 Organization. 이름이 비었거나 상위 조직이 없으면 400 |
| PATCH /v1/admin/organizations/:id | `{name?, nameEn?, parentOrganizationId?, clearParent?}` | 200 Organization. 없으면 404. 자기 자신이나 자기 하위 조직을 상위로 지정하면 400 |
| DELETE /v1/admin/organizations/:id | — | 204. 하위 조직이나 프로그램이 있으면 400, 없으면 404 |

- Organization: `{id, name, nameEn, parentOrganizationId, childCount, programCount, ancestors}`. `childCount`·`programCount`는 트리에서 펼칠 것이 있는지 보여 주는 값이다. `ancestors`는 최상위부터 바로 위 조직까지의 `{id, name}` 목록(최상위 조직은 `[]`)으로, 검색 결과의 상위 이름 표시와 경로 자동 펼침에 쓴다.
- 조직도(트리): `rootsOnly=true`로 최상위 조직을, `parentId=<id>`로 그 조직의 하위 조직을 받는다. 프로그램은 `GET /programs?organizationId=`, 활동은 `GET /activities?programId=`로 펼친다(2026-10-15 사용자 요청으로 이전 어드민의 펼침 조직도 복원).
- PATCH는 보낸 필드만 바꾼다. 최상위로 옮기려면 `clearParent: true`.
- 이름 중복(2026-10-15 사용자 결정): 같은 계위에서 띄어쓰기만 다른 이름도 중복으로 보고 만들기·이름 변경·이동을 400으로 거부한다(대소문자는 구분). 조직은 같은 상위 조직 아래(최상위끼리 포함), 프로그램은 같은 조직 아래가 같은 계위다. DB 유일 인덱스(V12)도 같은 규칙이다.
- 삭제는 되돌릴 수 없다(실제 삭제). 지우려면 안쪽부터(활동 → 프로그램 → 하위 조직 → 조직) 지운다.

## 프로그램

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/admin/programs | `q?`, `organizationId?`, `cursor?`, `size?` | 200 `{items: Program[], nextCursor}` |
| POST /v1/admin/programs | `{organizationId, name, nameEn?, collection?: CollectionSettingsInput}` | 201 Program. 이름이 비었거나 조직이 없으면 400 |
| PATCH /v1/admin/programs/:id | `{name?, nameEn?, organizationId?, category?, applyCategoryToActivities?}` | 200 Program(+`appliedActivityCount`). 없으면 404, 조직이 없으면 400, 모르는 종류 코드 400. `category`·일괄 적용은 아래 "프로그램 기본 종류" |
| DELETE /v1/admin/programs/:id | — | 204. 활동이 있으면 400. 수집 설정·수집 기록도 함께 지운다 |
| GET /v1/admin/programs/:id/collection-settings | — | 200 CollectionSettings. 프로그램이 없으면 404 |
| PUT /v1/admin/programs/:id/collection-settings | CollectionSettingsInput | 200 CollectionSettings |

- Program: `{id, name, nameEn, organization: {id, name}, category: string|null, collection: CollectionSettings}`
- **프로그램 기본 종류**(2026-10-19 사용자 제안 "종류는 프로그램에서 일괄지정할 수도 있게"): `programs.category varchar(20) null`(V33, 활동과 같은 코드 집합). `Program`·`ProgramListing`·`ProgramSummary`(공개 발견)에 `category: string|null`. `PATCH /v1/admin/programs/:id {category}`로 지정·해제하고, `applyCategoryToActivities: true`를 함께 보내면 그 프로그램의 활동 중 **종류가 비어 있거나 운영자가 지정하지 않은**(`curated_fields`에 `category`가 없는) 활동의 `category`를 같은 값으로 바꾼다(응답에 `appliedActivityCount`). 수집기는 추출 결과의 `category`가 null이면 프로그램 기본 종류를 쓰고, 운영자 생성(`POST /v1/admin/activities`)도 `category` 생략 시 프로그램 값을 기본으로 한다. 어드민 프로그램 편집 모달에 `기본 종류` 선택과 `기존 활동에도 적용(운영자 지정 제외)` 체크(기본 켬)를 두고, 프로그램 목록·활동 화면의 프로그램 행에 종류 칩을 보인다.
- CollectionSettingsInput: `{officialUrl?, allowedHosts: string[], intervalDays, enabled, renderPages?: boolean}`
  - **브라우저 렌더링(2026-10-19 사용자 결정, 우와콘 사례)**: `renderPages`(기본 `false`, `program_collection_settings.render_pages` V35). 켜면 수집기가 그 프로그램의 페이지를 받을 때 서버 HTML 대신 **헤드리스 Chromium(Playwright)** 으로 페이지를 실행한 뒤 `document.body.innerText`를 본문으로 쓴다. 자바스크립트로 내용을 그리는 단일 페이지 앱(예 `woowacon.com`, 서버 HTML에 글자 121자)을 위한 것이며, 켜지 않은 프로그램은 지금과 같은 HTML 받기다. 경계는 같다: https만, 허용 호스트가 있으면 페이지와 그 하위 요청 모두 허용 호스트 밖은 차단(사설 IP도 차단), 탐색 15초·네트워크 유휴 대기 최대 5초, 본문 3,000,000자, 이미지·글꼴·미디어 요청은 막아 시간을 줄인다. 렌더링 실패(시간 초과·브라우저 오류)는 지금의 받기 실패와 같이 거절 이유에 `렌더링 실패: …`로 남는다. 어드민 수집 설정 폼에 `브라우저 렌더링 필요` 체크와 안내("자바스크립트로 내용을 그리는 사이트에서만 켜요. 페이지당 시간이 늘어요"), 프로그램 목록 수집 열에 `렌더링` 태그. batch 이미지는 Chromium을 포함해 커진다(api·admin 이미지는 그대로).
  - `officialUrl`은 https만. `allowedHosts`는 정확한 호스트 이름(예: `letswift.kr`, 스킴·경로·포트 없음, 소문자로 저장). `intervalDays` ≥ 1. 어기면 400.
  - **주소 토큰(2026-10-19 사용자 결정)**: 회차마다 주소가 바뀌는 프로그램(예 `https://toss.im/slash-23`, `slash-24`)은 `officialUrl`에 토큰을 넣는다. `{year}`(네 자리 연도), `{yy}`(두 자리 연도), `{round}`(기수 번호). 수집 때 `{year}`·`{yy}`는 **올해와 내년**(Asia/Seoul) 두 값으로, `{round}`는 **같은 프로그램의 가장 최근 활동 이름에서 읽은 기수 번호(`(\d+)\s*기` 또는 `roundKey` 숫자) + 1과 그 번호** 두 값으로 펼친다. 토큰이 여럿이면 조합한다(최대 4개). 기수 번호를 읽을 수 없으면 `{round}`가 든 주소는 펼치지 않고 수집 보고의 거절 이유에 `기수 번호를 알 수 없음`으로 남긴다. 펼친 주소는 기존 시작 주소 자리에 차례로 들어가며, 없는 주소(404·오류)는 지금처럼 거절 이유에만 남고 수집은 계속된다. 토큰 없는 주소는 그대로 하나다. 검증: 알 수 없는 `{…}` 토큰은 400, 토큰을 샘플 값으로 바꾼 뒤 https 주소여야 한다.
- CollectionSettings: `{configured, officialUrl, allowedHosts, intervalDays, enabled, officialOnly, lastSucceededOn, previousRound, previewUrls, lastFetchedUrls}, renderPages}`
  - `lastFetchedUrls`(2026-10-19 사용자 요청 "수집 url은 현시점 수집페이지를 기입하도록"): 가장 최근 수집(결과가 SUCCEEDED인 마지막 `collection_runs` 행)에서 **실제로 받아 읽은 페이지 주소** 목록. 수집기가 `collection_runs.fetched_urls varchar[]`(V30)에 받은 순서로 남긴다(받기 실패한 주소는 제외). 없으면 `[]`. 어드민 웹은 설정 폼과 프로그램 목록에 "현시점 수집 페이지: …"로 읽기 전용으로 보이고, 템플릿 주소(`officialUrl`)는 바꾸지 않는다.
  - `previousRound`(2026-10-19): 같은 프로그램의 가장 최근 활동에서 읽은 기수 번호, 없으면 `null`. `previewUrls`: 저장된 `officialUrl`을 지금 수집한다면 보게 될 주소 목록(서버가 위 규칙으로 펼침, 토큰이 없으면 그 주소 하나, 주소가 없으면 `[]`). 어드민 웹은 입력 중인 값은 같은 규칙으로 클라이언트에서 미리 계산해 보여 주고(`previousRound` 사용), 저장 뒤에는 서버 값을 쓴다.
  - `configured`: 설정 행이 있는지. 없으면 기본값(`officialUrl` null, `allowedHosts` [], `intervalDays` 1, `enabled` true)을 돌려준다.
  - 어드민 웹 수집 설정 폼(2026-10-19): 주소 입력 위에 토큰 태그 버튼 `{year}`·`{yy}`·`{round}`를 두고 누르면 커서 자리에 토큰이 들어간다. 입력 아래 안내 문구 "회차마다 주소가 바뀌면 토큰을 넣어요. {year} 네 자리 연도, {yy} 두 자리 연도, {round} 기수 번호(이전 활동 이름의 ‘N기’에서 읽음). 올해·내년, 다음 기수·현재 기수로 펼쳐 차례로 봅니다."와 미리보기 "지금 수집하면 이 주소들을 봅니다: …"를 보인다. 기수 번호를 읽을 수 없으면 미리보기에 "기수 번호를 알 수 없어 {round} 주소는 건너뜁니다"를 쓴다.
- `officialOnly` = `allowedHosts`가 비어 있지 않음. **false면 매일 밤 "프로그램 이름 + 주최 기관"으로 웹 전체를 검색한다**(공식 도메인 수집보다 토큰이 약 5배, 공식 출처가 아닌 페이지가 섞인다). 어드민 웹은 비어 있을 때 이 점을 보여 준다.
  - `lastSucceededOn`: 마지막으로 수집에 성공한 한국 날짜(없으면 null). 이 날짜 + `intervalDays`가 지나야 다시 수집한다.

## 프로그램 로고 — 2026-10-19 밤 사용자 결정

사용자: "로고도 어드민에서 추가할 수 있게 해줘." 발견 카드 로고 자리(native-v1.md "프로그램 로고")에 쓴다.

- 저장: `program_logos(program_id bigint primary key references programs on delete cascade, content_type varchar(100), data bytea, updated_at timestamptz)`(V38). 인증자료처럼 DB에 둔다(프로그램 수가 적고 백업이 함께 된다).
- `PUT /v1/admin/programs/:id/logo` — multipart `file`. 허용 `image/png`·`image/jpeg`·`image/webp`, 1MB 이하. 그 외 400 `로고는 PNG·JPEG·WebP 1MB 이하여야 합니다`. 있으면 바꾼다. 200 `{logoUrl}`(native-v1 규칙의 `/v1/programs/{id}/logo?v=…`).
- `DELETE /v1/admin/programs/:id/logo` — 204. 없어도 204.
- `GET /v1/admin/programs/:id/logo` — ADMIN 인증으로 같은 이미지(어드민 미리보기용; 공개 `GET /v1/programs/:id/logo`는 api 앱이 낸다). 없으면 404.
- `AdminProgram.logoUrl: string | null`(목록·상세 모두).
- 어드민 화면: 프로그램 편집 폼에 `로고` 항목 — 현재 로고 미리보기(44, 둥근 14; 어드민 GET을 `api`로 blob으로 받아 object URL), `업로드`(Ant `Upload`, 허용 종류·크기를 선택 전에 검사), `지우기`. 업로드·지우기는 저장 버튼과 무관하게 바로 반영된다. admin 앱 `spring.servlet.multipart.max-file-size`는 2MB.

## 활동

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/admin/activities | `q?`, `organizationId?`, `programId?`, `publicationStatus?`(`DRAFT`·`PUBLISHED`), `cursor?`, `size?` | 200 `{items: AdminActivity[], nextCursor}`. 초안 포함 |
| POST /v1/admin/activities | ActivityCreateInput | 201 AdminActivity(2026-10-19 사용자 요청 "활동 추가 플로우가 어드민에 없어"). 프로그램이 없으면 400, 같은 프로그램에 띄어쓰기만 다른 같은 이름이 있으면 400 |
| PATCH /v1/admin/activities/:id | `{name?, nameEn?, description?, descriptionEn?, eligibility?, officialUrl?, applicationUrl?, category?, activitySchedule?, application?}` | 200 AdminActivity. 보낸 필드만 바꾼다(아래 "운영자 지정 필드"·"일정 직접 지정"). 없으면 404, 한글 이름을 비우면 400, 공식 안내 URL은 https 필수, 신청 링크는 빈 문자열이면 삭제 |
| PATCH /v1/admin/activities/:id/publication-status | `{publicationStatus}` | 200 AdminActivity. 없으면 404 |
| DELETE /v1/admin/activities/:id | — | 204. 없으면 404. 일정·역할·연락처도 함께 지워진다. 수집으로 생긴 활동이면 그 출처(`source_id`)를 남겨 다음 수집에서 다시 만들지 않고, 이름을 수집 피드백 `DRAFT_DISMISSED`로 남긴다(아래 "수집 피드백") |
| POST /v1/admin/activities/:id/schedules | ScheduleItemInput | 201 Schedule(2026-10-19 사용자 요청 "활동에 스케줄들도 편집, 구글캘린더 느낌"). 세부 일정(슬롯 없음) 추가, `curated = true` |
| PATCH /v1/admin/activities/:id/schedules/:scheduleId | ScheduleItemInput(부분) | 200 Schedule. 슬롯 일정(모집·활동 기간)은 기존 PATCH 활동의 `application`·`activitySchedule`로만 고친다(여기서는 400) |
| DELETE /v1/admin/activities/:id/schedules/:scheduleId | — | 204. 슬롯 일정은 400. 수집으로 생긴 일정을 지우면 다음 수집이 같은 제목·시각의 일정을 다시 만들 수 있으므로, 지운 일정은 `curated_deleted`로 남겨 수집이 다시 넣지 않는다(V36) |

- **어드민 웹 활동 화면**(2026-10-19 사용자 요청 "탐색메뉴처럼 프로그램 하위에 활동들이 표시되는 형태"): 앱의 발견 탭과 같이 프로그램이 상위 행이고 프로그램 행은 **프로그램 바로 위 조직(최하위 조직)별 소제목** 아래에 묶이고(2026-10-19 사용자 요청 "최하위 조직별로 그루핑, 필터도"), 조직 필터는 그 조직들을 고를 수 있다. 펼치면 그 프로그램의 활동이 종류별 소제목(native-v1.md "활동 종류", 미분류는 맨 뒤) 아래에 정렬 키(native-v1.md "발견 프로그램 묶음": 모집 마감 → 활동 시작 → 없음, 이름순) 순으로 나온다(`GET /v1/admin/programs` 목록 + 펼칠 때 `GET /v1/admin/activities?programId=`를 지연 로딩, 50개씩 `더 보기`). 활동 이름 검색어가 있을 때만 기존 평면 목록을 결과로 보인다. 행 동작(편집·게시·삭제·중복 지정·기간 확인)은 그대로다. API 변경 없음.
- **ActivityCreateInput**(2026-10-19 사용자 요청 "활동 추가 플로우가 어드민에 없어"): `{programId, name, nameEn?, description, descriptionEn?, officialUrl, applicationUrl?, category?(native-v1.md "활동 종류" 코드), participationType?("REGISTRATION"|"SELECTION", 기본 REGISTRATION), activitySchedule?: ScheduleInput|{dateLabel}, application?: ScheduleInput, eligibility?, audience?, location?, cost?}`. 운영자가 직접 만드는 활동이다. 저장 규칙: `publicationStatus = DRAFT`(기존 게시 전환으로 공개), `source_id` 없음(수집이 다시 만들거나 덮어쓰지 않는다), `official_source = true`, `source_note = "운영자 등록"`, `source_checked_at` = 지금, 보낸 텍스트 필드는 모두 `curated_fields`에, 보낸 기간은 `schedules.curated = true`로 저장한다. `officialUrl`·`applicationUrl`은 https, `activitySchedule`은 날짜 범위 또는 원문 문구(`dateLabel`) 중 하나라도 있어야 한다(둘 다 없으면 400). 어드민 웹은 활동 목록 위 `활동 추가` 버튼으로 모달을 열고(프로그램 검색 선택, 이름·영문 이름·설명·영문 설명, 공식 안내 URL·신청 링크, 참여 방식, 모집 기간·활동 기간 블록과 활동 기간 원문, 대상자 조건 편집기·대상 원문·장소·비용), 만든 뒤 목록을 다시 불러온다.

AdminActivity: 공개 Activity(`native-v1.md` "활동 조회")의 필드 `{id, name, nameEn, description, descriptionEn, organization, program, officialUrl, imageUrl, category, eligibility, application, activitySchedule, schedules}`에 운영자용 필드를 더한다. 일정(`application`·`activitySchedule`)에는 `curated: boolean`·`curatedNote: string|null`이 붙는다.

- `publicationStatus`: `DRAFT`(수집 초안, 공개 API에 안 보임) / `PUBLISHED`
- `officialSource`: 출처가 프로그램의 공식 도메인인지. false면 웹 전체 검색에서 나온 것이라 출처를 확인하고 게시해야 한다
- `applicationUrl`, `location`, `cost`, `audience`, `sourceCheckedAt`: 수집이 원문에서 확인한 값(없으면 null)

## 이력 검토·역할 — 2026-10-16 사용자 결정

사용자가 프로필 이력을 단계별로 입력할 때 등록된 활동 정보에 없는 조직·프로그램·활동·역할을 직접 적으면 관리자 승인 전까지 명함에 넣을 수 없다. 인증자료를 올리면 관리자가 확인한다(`native-v1.md` "프로필·명함 v2" → "관리자"). 화면에는 작성자의 프로필 이름만 보여 주고 계정 id는 싣지 않는다.

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/admin/histories | `approval?`(pending·approved·rejected), `verification?`(unverified·pending·verified·rejected), `cursor?`(이력 id), `size?` | 200 `{items: HistoryForReview[], nextCursor}` |
| POST /v1/admin/histories/:id/approval | `{decision: "approve"\|"reject", organizationId?, programId?, activityId?, roleId?, reason?}` | 200 HistoryForReview. 없는 활동 정보 id 400, 거절에 사유 없으면 400 |
| POST /v1/admin/histories/:id/verification | `{decision: "verify"\|"reject"}` | 200 HistoryForReview |
| GET /v1/admin/histories/:id/evidence/:eid | — | 200 파일(inline) |
| GET /v1/admin/roles | `q?`, `cursor?`, `size?` | 200 `{items: Role[], nextCursor}` |
| POST /v1/admin/roles | `{name, nameEn?}` | 201 Role. 띄어쓰기만 다른 이름도 중복 400 |
| PATCH /v1/admin/roles/:id | `{name?, nameEn?}` | 200 Role |
| DELETE /v1/admin/roles/:id | — | 204. 이력이 쓰면 400 |

- HistoryForReview: `native-v1.md`의 History에 `profileName`을 더한 것.
- 승인 때 id를 주면 직접 입력 값을 그 활동 정보 항목으로 바꾼다. 없는 조직·프로그램·역할은 기존 admin API로 먼저 만든다. 활동은 여기서 만들지 않으며, 직접 입력한 활동 이름을 그대로 승인할 수 있다.
- Role: `{id, name, nameEn}`.

## 직무 — 2026-10-17 사용자 결정

프로필의 직함 문자열을 직무 태그로 바꿨다(`native-v1.md` "프로필과 버전 이력"). 직무는 조직과 같은 계층·이름 중복 규칙을 따르고(같은 상위 아래 띄어쓰기만 다른 이름 400, 자기 자신·하위 아래로 이동 400), 시작 분류는 V18 마이그레이션이 넣었다. 사용자가 검색에 없는 직무를 직접 적으면 승인 전까지 명함에 들어가지 않는다.

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/admin/jobs | `q?`, `parentId?`, `rootsOnly?`, `cursor?`, `size?` | 200 `{items: Job[], nextCursor}` |
| POST /v1/admin/jobs | `{name, nameEn?, parentId?}` | 201 Job |
| PATCH /v1/admin/jobs/:id | `{name?, nameEn?, parentId?, clearParent?}` | 200 Job. 없으면 404 |
| DELETE /v1/admin/jobs/:id | — | 204. 하위 직무가 있거나 프로필이 쓰면 400 |
| GET /v1/admin/job-entries | `approval?`(pending·approved·rejected), `cursor?`(항목 id), `size?` | 200 `{items: JobEntry[], nextCursor}`. 직접 입력 항목만 |
| POST /v1/admin/job-entries/:id/approval | `{decision: "approve"\|"reject", jobId?, reason?}` | 200 JobEntry. 승인에 `jobId`를 주면 직접 입력을 그 직무로 바꾼다. 없는 직무 400, 거절에 사유 없으면 400 |

- Job: `{id, name, nameEn, parentId, childCount, ancestors: [{id, name}]}`(최상위부터 바로 위까지).
- JobEntry: `{id, profileName, job: {id, name} | null, jobName, approval, approvalReason}`. 계정 id는 싣지 않는다.

## 아직 없는 것

장소·비용·대상 원문의 PATCH 편집(운영자 생성에서만 받는다), 수집 기록(`collection_runs`) 화면(마지막 성공 날짜와 받은 페이지만 설정 응답으로 노출), 감사 로그, 운영자 지정 해제 API, 태그(native-v1.md "태그" — 설계 완료·구현 예정). 이전 어드민의 자격요건 편집기·수집 작업 큐·재확인 화면은 이 API로 옮기지 않는다.

## 일정 직접 지정·기간 확인·날짜 문구 피드백·조심 목록 — 2026-10-18 사용자 결정 (2026-10-19 보완)

### 활동 장소와 지오코딩 — 2026-10-19 사용자 결정

native-v1.md "활동 장소"를 따른다. `PATCH /v1/admin/activities/:id`·`POST /v1/admin/activities`에 `place?: {name, address, lat, lng} | null`(위도·경도 범위 검증, 이름 ≤200·주소 ≤300), `AdminActivity.place`, `curated_fields`에 `place`. `GET /v1/admin/geocode?q=`(ADMIN, `q` 2~100자, 공급자 Kakao → 없으면 Nominatim). 어드민 폼의 `장소` 검색·선택·지우기.

### 세부 일정 편집기 — 2026-10-19 사용자 결정

사용자: "어드민에서 활동에 스케줄들도 편집할 수 있으면 좋겠어. 구글캘린더 느낌으로". 모집·활동 기간(슬롯 일정) 외의 **세부 일정**(`schedules[]`: 테스트·면접·발표·세션 등)을 어드민에서 만들고 고치고 지운다.

- ScheduleItemInput: `{title, kind: DEADLINE|ANNOUNCEMENT|INTERVIEW|EVENT, status?: TENTATIVE|CONFIRMED|CANCELLED(기본 CONFIRMED), startAt?, endAt?, startDate?, endDate?(포함), timeZone?(기본 Asia/Seoul), dateLabel?, note?(사유 ≤300)}`. 날짜와 시각을 섞으면 400, 끝이 시작보다 앞서면 400, 둘 다 없고 `dateLabel`도 없으면 400. 운영자가 만든·고친 세부 일정은 `schedules.curated = true`(수집이 덮어쓰지 않음, 기존 규칙), 지운 일정은 `schedules.curated_deleted = true`로 남아 응답·공개 API에서 빠지고 수집이 같은 `title`(띄어쓰기 무시)·같은 시작의 일정을 다시 넣지 않는다.
- 응답 Schedule에 `curatedNote`(기존)·`curated`(기존) 그대로. `AdminActivity.schedules`는 세부 일정만, 시각·날짜 순.
- **어드민 화면**: 활동 편집기 안의 `일정` 탭(또는 상세의 `일정 편집` 버튼)에 두 가지 보기. ① **달력 보기**: 월 달력(Ant `Calendar`)에 모집 기간·활동 기간을 바탕 띠로, 세부 일정을 날짜 칸의 칩으로 그린다. 날짜 칸을 누르면 그 날짜가 채워진 `일정 추가` 폼이 열리고, 칩을 누르면 편집 폼이 열린다(끌어서 옮기기는 두지 않는다). ② **목록 보기**: 시각 순 표에 제목·종류·상태·기간·원문·사유, 행마다 편집·삭제. 폼은 기존 기간 블록(날짜/시각 전환, 시작 선택 비움 가능, 사유)을 재사용한다.
- 공개 API·앱은 바뀌지 않는다(세부 일정은 이미 `schedules[]`로 내려가고 진행 트랙이 쓴다).

### 일정 상태·겹침 확인 시간 — 2026-10-22 사용자 결정

native-v1.md "내 활동 서버 저장·일정 상태·겹침 확인 시간"의 어드민 쪽. 응답 Schedule(어드민·공개 공통)에 `statuses: [{id: number|null, name, kind: WAITING|PASSED|FAILED|OTHER}]`(어드민 지정 행을 순서대로, 없으면 기본 `대기`·`통과`·`탈락`, id null)과 `overlapCheck: {startTime, endTime}|null`(서울 `HH:mm`)이 더해진다.

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/admin/activities/:id/schedules/:scheduleId/statuses | — | 200 `{items: [{id, name, kind}]}`(지정 전이면 기본 세 개, id null) |
| PUT /v1/admin/activities/:id/schedules/:scheduleId/statuses | `{items: [{id?, name, kind}]}` | 200 같은 형태. 목록을 순서대로 통째로 바꾼다 |
| POST·PATCH /v1/admin/activities/:id/schedules(/:scheduleId) | ScheduleItemInput에 `overlapCheck?: {startTime, endTime}\|null` | 201·200 Schedule. 생략은 유지(만들 때는 없음), null은 없음·지움 |

- 상태 PUT: 모집·활동 기간을 포함한 모든 일정. 본문은 `{items: [...]}` 객체(배열만 보내지 않음). 0~8개이고 **빈 목록은 기본 세 개로 되돌린다**(그 일정의 행을 모두 지우고 선택은 아래 규칙대로 옮김). 이름은 앞뒤 공백을 떼고 1~20자이며 그 일정 안에서 겹치지 않음, `id`는 그 일정의 상태여야 함, `kind`는 넷 중 하나(어기면 400, 없는 일정 404). `id`가 있는 항목은 그 행을 고치고(사용자 선택 유지), 없으면 새로 만들고, 빠진 행은 지운다. 지운 상태를 고른 사용자 선택은 그 상태의 기본 종류(`default_kind`)로 옮기고, `OTHER`는 기본 종류가 없어 선택을 지운다. 기본 세 개를 id 없이 보내면 같은 이름의 지정 행이 된다(되돌리기는 빈 목록). 수집한 세부 일정에 상태를 두면 `curated = true`가 되어 다시 수집해도 지워지지 않는다.
- 겹침 확인 시간: 세부 일정 POST(만들기)·PATCH 모두 `overlapCheck`를 받고 `null`은 없음(PATCH에서는 지움)이다. 세부 일정은 다른 필드와 함께 고칠 수 있다(고치면 기존 규칙대로 `curated`). 모집·활동 기간 행은 두 길로 고친다: ① 활동 `PATCH`·`POST /v1/admin/activities`의 `activitySchedule`·`application` 객체 안 `overlapCheck`(어드민 웹이 쓰는 형태. 날짜와 함께 보내면 둘 다 바꾸고, 객체에 `overlapCheck`만 있으면 날짜는 그대로, `null`은 지움. 모집 기간이 없는데 값을 보내면 400) ② 일정 PATCH에 `overlapCheck`**만** 보낸 요청(다른 필드가 섞이면 기존처럼 400). 형식이 `HH:mm`이 아니거나 끝이 시작보다 늦지 않으면 400. DB: `schedules.overlap_start_time`·`overlap_end_time`(time, 둘 다 있거나 둘 다 없음, V45).
- 수집: 모집·활동 기간 행은 다시 수집할 때 지우고 새로 넣지 않고 제자리에서 고친다(id가 유지되어 상태·사용자 선택·겹침 확인 시간·장소가 남는다). 모집 기간이 사라지면 그 행은 지운다(그 일정의 상태도 함께).

### 일정 직접 지정

수집기는 날짜만 적힌 기간을 구조화하지 못하면 `dateLabel` 문구만 남긴다. 운영자가 원문 문구를 보면서 날짜를 직접 지정한다.

- 요청: `PATCH /v1/admin/activities/:id`의 `activitySchedule?`·`application?`. 각 값은 `{startDate?, endDate?, startAt?, endAt?, note?}` 또는 `null`(지정 해제, `dateLabel`만 남김, 사유도 지움). `dateLabel`은 보존한다. `POST /v1/admin/activities`도 같은 형식(`activitySchedule`은 `{dateLabel}`만도 가능).
- 저장 규칙: 날짜만 둘 다 주면 종일 일정(`startDate`, 저장은 `endDateExclusive = endDate + 1일`); 시각까지 주면 시각 일정(`startAt`·`endAt`, Asia/Seoul 오프셋); **활동 기간도 시각 일정이 된다**(2026-10-19 밤 V39: 그 전까지 V2의 DB 체크가 활동 기간의 시각 일정을 막아 어드민 저장이 500으로 끝났다 — 사용자 "어드민에서 프로그램 편집이 안돼" 보고로 발견); 한 요청에 날짜와 시각을 섞으면 400, 끝이 시작보다 앞서면 400. **모집 기간은 시작 없이 끝만 줄 수 있다**: `endDate`만 오면 그날 23:59(Asia/Seoul)까지의 시각 일정 `Timed(start = null, end = endDate 23:59)`, `startDate`만 오면 `Timed(start = startDate 00:00, end = null)`(`ALL_DAY`는 DB 제약상 시작·끝이 모두 필요), `endAt`만·`startAt`만은 시점 일정. 응답에는 `startAt = null, endAt = …`로 나온다. 활동 기간은 시작·끝이 모두 필요하다.
- 사유: `note`(≤300자, 넘으면 400)는 `schedules.curated_note varchar(300)`(V32)에 저장하고 일정에 `curatedNote: string|null`로 내보낸다. "날짜 문구 피드백"의 해석 예시에 `(이유: …)`로 붙어 수집기가 판단 근거로 쓴다.
- 보호: 운영자가 지정한 일정은 `schedules.curated = true`로 표시하고, 수집기는 초안을 갱신할 때 `curated` 일정을 덮어쓰지 않는다(게시 활동은 원래 덮어쓰지 않는다). `AdminActivity`의 일정에 `curated: boolean`.
- 어드민 웹 활동 편집 창: "모집 기간"·"활동 기간" 블록(이 순서, 목록 표와 같게)마다 원문 `dateLabel`, 날짜 범위 선택기(모집 기간은 시작 칸을 비울 수 있음 `allowEmpty`), "시각 지정" 스위치, `사유(선택)` 입력(예: "원문의 6/9는 접수 마감이라 모집 종료로 지정"), 안내 "날짜만 고르면 종일 일정, 시각을 켜면 한국 시간으로 저장해요. 마감일만 고르면 그날까지로 저장해요. 비워 두면 원문 문구만 남아요. 지정한 기간은 다음 수집의 해석 예시로도 쓰여요." 비우고 저장하면 `null`.
- 변경 이력: 2026-10-18 최초(날짜·시각, 둘 다 필수) [`b8044ead`](https://github.com/fixabley/dearby/blob/b8044ead/shared/contracts/admin-v1.md#일정-직접-지정) · 2026-10-19 폼 순서를 모집 → 활동으로(사용자 "자꾸 헷갈리네") · 2026-10-19 마감만 지정(사용자 "모집기간에 마감일자만 넣었는데 입력되지 않아"; 원인은 선택기가 두 끝을 요구하고 서버 날짜 일정도 둘 다 요구한 것)·사유(사용자 "직접 입력했을 때 사유도 입력하게") [`d15ff181`](https://github.com/fixabley/dearby/blob/d15ff181/shared/contracts/admin-v1.md#일정-직접-지정).

### 기간 확인 태그·바꾸기 — 2026-10-19 사용자 보고

수집이 모집 기간과 활동 기간을 바꿔 넣는 경우가 있어(원인·사례는 [수집 배치 기록](../../docs/context/activity-collection-batch.md) "모집 기간·활동 기간 혼동"), 어드민이 의심 조건을 표시하고 운영자가 바로 바꿀 수 있게 한다. API 변경은 없다.

- 목록·상세·편집의 모집 기간 옆에 `기간 확인` 태그(주황): 모집 시작이 활동 종료보다 늦음, 모집 기간과 활동 기간이 같음, 활동 기간 원문(`dateLabel`)에 모집·접수·지원·신청 낱말이 있음 중 하나면 붙고 툴팁에 이유를 쓴다. 어드민이 두 일정 값만으로 계산한다.
- 편집 폼의 `모집 ↔ 활동 바꾸기`: 두 기간 블록의 값을 서로 바꾼다(활동 기간은 종일만 허용하므로 시각 지정은 끈다). 저장하면 기존 `PATCH /v1/admin/activities/:id`의 `application`·`activitySchedule`로 보내고 운영자 지정(`curated`)으로 보호된다.


### 날짜 문구 피드백 — 2026-10-19 사용자 결정 (2026-10-20 "수집 피드백"으로 대체)

> 2026-10-20부터 지정 일정을 지시문 예시 20개로 넘기지 않는다. 같은 정보가 수집 피드백 `DATE_CURATED`로 남고, 수집 에이전트는 요청의 최근 10건과 `search_feedback`으로 쓴다. 아래는 2026-10-19 규칙의 기록이다.

사용자: "날짜 문자열 같은 경우 내가 직접 지정했을 때 피드백으로 받아들여서 정규화된 날짜값으로 수집할 수 있게". 운영자가 어드민에서 기간을 직접 지정하면(`schedules.curated = true`, 원문 `date_label`이 비어 있지 않고 날짜·시각이 채워진 행) 그 쌍을 **수집 지시문의 해석 예시**로 쓴다. 새 테이블은 없다.

- 수집기는 추출 요청마다 최근 예시를 뽑는다: 전 프로그램에서 `curated`이고 `date_label <> ''`이며 `start_date` 또는 `start_at`이 있는 일정을 최신 `updated_at` 순으로 최대 20개, 같은 `date_label`은 하나만. 같은 프로그램의 예시를 앞에 둔다.
- 지시문에 "날짜 문구 해석 예시(운영자가 바로잡은 것)" 절을 넣어 `"2026년 11월 1일~15일" → startDate 2026-11-01, endDate 2026-11-15`(시각이 있으면 `startAt/endAt` ISO) 형식으로 나열하고, "비슷한 문구는 같은 방식으로 날짜 필드를 채운다. 연도·월이 생략된 문구는 본문의 다른 날짜로 알 수 있을 때만 채우고 모르면 null" 규칙을 함께 적는다. 예시의 슬롯(모집/활동)도 표기한다.
- 운영자가 지정한 일정 자체는 기존 규칙대로 수집이 덮어쓰지 않는다. 예시는 **다른 활동·다음 기수**의 비슷한 문구를 정규화하는 데 쓰인다.
- 수집 보고(`collection_runs`)에는 바뀐 것이 없다. 어드민은 지정 폼 안내 문구 "지정한 기간은 다음 수집의 해석 예시로도 쓰여요"만 더한다.


### 조심 목록(수집 제외·별칭)

운영자가 중복이나 잘못 분류된 활동을 지정하면 수집기가 다음부터 피한다.

| 메서드·경로 | 요청 | 응답 |
| --- | --- | --- |
| GET /v1/admin/programs/:id/watchlist | — | 200 `{items: WatchEntry[]}` |
| POST /v1/admin/programs/:id/watchlist | `{name, kind: "alias"\|"exclude", canonicalActivityId?, reason?}` | 201 WatchEntry. `alias`는 `canonicalActivityId` 필수(같은 프로그램의 활동), `exclude`는 없음 |
| DELETE /v1/admin/watchlist/:entryId | — | 204 |
| POST /v1/admin/activities/:id/duplicate | `{kind: "alias"\|"exclude", canonicalActivityId?, reason?}` | 204. 그 활동의 이름으로 WatchEntry를 만들고 활동을 지운다(기존 DELETE와 같이 `source_id`를 dismissed에 남긴다). `alias`면 정본은 다른 활동이어야 한다 |

- WatchEntry: `{id, programId, name, normalizedName, kind, canonicalActivityId, canonicalActivityName, reason, createdAt}`. `normalizedName`은 공백 제거·소문자. 같은 프로그램에 같은 `normalizedName`은 하나(409).
- 수집기: 프로그램을 수집할 때 조심 목록을 읽어 (1) 추출 요청에 "이 프로그램의 활동으로 만들지 않을 이름"(exclude)과 "이미 있는 활동의 다른 표기 → 정본 이름"(alias)을 넘기고, (2) 추출된 활동 이름을 `normalizedName`과 대조해 `exclude`면 저장하지 않고 제외 사유 `조심 목록: 제외 <이름>`을 기록, `alias`면 정본 활동의 초안을 갱신(정본이 게시 상태면 건너뛰고 사유 기록)한다. 이름 대조는 결정적 보장이고 지시문은 힌트다.
- 어드민 웹: 활동 행 메뉴 "중복으로 지정"(같은 활동이다 → 정본 선택 / 이 프로그램 활동이 아니다), 프로그램 화면에 "조심 목록" 표(이름·종류·정본·사유·지정일·삭제, 직접 추가).
- 필드 이름은 기존 어드민 API와 같은 camelCase다. DB 표 `collection_watchlist(id, program_id, name, normalized_name, kind, canonical_activity_id, reason, created_at)`와 `schedules.curated`만 snake_case.

## 운영자 지정 필드(curated_fields) — 2026-10-18 보완, 2026-10-19 확장

어드민이 바꾼 값은 수집이 덮어쓰지 않는다. 어드민 `PATCH`·`POST`로 바꾼(보낸) 필드 이름을 `activities.curated_fields jsonb`(V26)에 남기고 `AdminActivity.curatedFields: string[]`로 내보낸다.

- 대상 필드: `name`·`nameEn`·`description`·`descriptionEn`·`eligibility`(2026-10-18) · `officialUrl`(공식 안내 URL, https 필수, 비울 수 없음)·`applicationUrl`(신청 링크, https, 빈 문자열은 삭제)·`category`(활동 종류, 코드 또는 null)(2026-10-19 사용자 "공식 안내 url도 내가 수정할 수 있으면", "활동 종류") · 운영자 생성(`POST /v1/admin/activities`)이 보낸 `audience`·`location`·`cost`·`participationType`. 수집기는 이 가운데 `name`·`nameEn`·`description`·`descriptionEn`·`eligibility`·`officialUrl`·`applicationUrl`·`location`·`cost`·`category`를 보호하고(`audience`·`participationType`은 "이전 기수 정보 자동완성"의 resolve 규칙으로 운영자 값을 지킨다), 나머지는 페이지에서 뽑은 값으로 갱신한다(가져온 값도 새 수집값이 있으면 갱신). 활동 기간·모집 기간은 `schedules.curated`가 같은 역할을 한다.
- 표시: 어드민은 해당 필드 옆에 "운영자 지정" 태그를 보이고, 같은 필드를 다시 저장해도 태그는 유지된다. 비운 필드도 운영자 지정으로 본다(수집이 다시 채우지 않음). 운영자 지정을 해제하는 API는 없다.
- 종류·태그의 프로그램 단위 일괄 적용은 `curated_fields`에 `category`(또는 `tags`)가 없는 활동에만 닿는다.

## 이전 기수 정보 자동완성 — 2026-10-18 사용자 결정

수집한 활동에 비어 있는 정보가 있으면 같은 프로그램의 이전 기수 활동에서 가져와 채운다(사용자 요청: "참여조건 같은 것들은 비슷할 가능성이 높다").

- 대상 필드: `eligibility`(대상자 조건)와 `audience`(대상 원문), `participationType`(참여 방식). 기간·장소·비용·신청 링크처럼 기수마다 바뀌는 값은 가져오지 않는다.
- 출처 활동: 같은 프로그램에서 가장 최근에 만들어진 **다른** 활동(게시 활동 우선, 없으면 초안). 새 초안을 처음 저장할 때만 적용하고, 이미 있는 초안을 갱신할 때는 비어 있는 필드에만 적용한다. 운영자가 지정한 값(`curated` 또는 직접 편집)은 덮어쓰지 않는다.
- 표시: `activities.inherited_fields jsonb`(가져온 필드 이름 배열, 예 `["eligibility","audience"]`)에 남기고 `AdminActivity.inheritedFields: string[]`로 내보낸다. 어드민은 해당 필드 옆에 "이전 기수에서 가져옴" 표시를 보이고, 운영자가 그 필드를 편집·비우면 목록에서 빠진다. 공개 API에는 표시하지 않는다(값은 그대로 공개).
- 수집 지시문: 추출 요청에 이전 기수의 대상자 조건·대상 원문을 "참고(이전 기수)"로 넘겨, 본문에 같은 내용이 있으면 그대로 쓰고 본문에 없으면 null로 두게 한다. 본문에 없는 내용을 만들어 내지 않는다. 이후 서버가 비어 있는 필드를 위 규칙으로 채운다.

## 수집 피드백 — 2026-10-20 사용자 결정

사용자: 수집 에이전트의 판단에 운영자의 어드민 피드백을 RAG처럼 쓴다. 운영자가 바로잡은 것을 `collection_feedback`(V43)에 한 행씩 남기고, 수집 에이전트가 찾아 쓴다. 어드민 API·응답은 바뀌지 않는다(목록 화면은 아직 없다).

- 표: `collection_feedback(id, kind, program_id null, host null, source_text, corrected_value jsonb, reason null, activity_id null, created_at)`. `source_text`는 수집기가 본 원문(날짜 문구·활동 이름·수집 값)이고 pg_trgm GIN 색인으로 찾는다. `activity_id`는 참조 키가 아니다(지운 활동의 피드백도 남는다).
- 종류와 남기는 곳(수집으로 생긴 활동, 즉 `source_id`가 있는 활동만; 운영자가 직접 만든 활동은 남기지 않는다):

| kind | 남기는 경로 | source_text → corrected_value · reason |
| --- | --- | --- |
| `DATE_CURATED` | `PATCH /v1/admin/activities/:id`의 `activitySchedule`·`application`, `PATCH …/schedules/:scheduleId`의 날짜·시각 변경 | 고치기 전 원문 `dateLabel` → `{slot\|scheduleId·title, startDate, endDate}` 또는 `{…, startAt, endAt}` · `note` |
| `PERIOD_SWAP` | 같은 PATCH에서 새 활동 기간 = 이전 모집 기간이고 새 모집 기간 = 이전 활동 기간(어드민 "모집 ↔ 활동 바꾸기") | `"모집: <문구> / 활동: <문구>"` → `{application, activity}` · `note`. 이때 `DATE_CURATED`는 남기지 않는다 |
| `FIELD_CURATED` | PATCH의 `name`·`category`·`tagIds`·`place`·`eligibility`가 이전 값과 다를 때 | 이전 값(없으면 활동 이름) → `{field, from, to}` |
| `DRAFT_DISMISSED` | `DELETE /v1/admin/activities/:id` | 활동 이름 → `{}` |
| `WATCH_ALIAS`·`WATCH_EXCLUDE` | 조심 목록 추가(직접 추가·중복 지정). 항목을 지우면 이 행도 지운다 | 이름 → `{watchEntryId, canonicalActivityName?}` · `reason` |

- 채우기(V44, 여러 번 실행해도 같은 행을 두 번 넣지 않음): 지정 일정(`curated`, 지우지 않은 것, 문구와 날짜가 있는 것), 운영자 지정 필드(`curated_fields`의 name·category·eligibility·tags; 고치기 전 수집 값은 남아 있지 않아 `to`만), 조심 목록. 지운 초안은 이름이 남아 있지 않아 채울 수 없다.
- 수집기의 쓰임: (1) 요청에 그 프로그램의 최근 10건. (2) MCP 도구 `search_feedback {text, kind?}`: 유사도(0.3 이상) 상위 8건, 같은 프로그램 → 같은 호스트 → 나머지 순, 각 행에 kind·원문·운영자 값·사유·(다른 프로그램이면) 프로그램 이름. (3) 제출 판정: 같은 프로그램에서 이름(공백·대소문자 무시)이 `DRAFT_DISMISSED`·`WATCH_EXCLUDE`와 같으면 거절(`운영자 피드백 #id(kind): …`, 수집 보고 거절 이유에도), 날짜 문구가 `DATE_CURATED`와 유사도 0.6 이상인데 해석이 다르면 저장하되 주의를 돌려준다.
- 수집 보고: `collection_runs.agent_log jsonb`(V42)에 `workflowVersion`(작업 절차 파일 SHA-256 앞 12자리), `stopReason`·`capReached`·`turns`·`pages`·`usage`(캐시 나눔)·`costUsd`·`calls[{tool, url, result}]`와 피드백 id `feedbackInjected`(요청에 넣음)·`feedbackRetrieved`(검색으로 찾음)·`feedbackApplied`(거절·주의에 씀). 어드민 API로는 아직 내보내지 않는다.

## 명함 신고 — 2026-10-22

native-v1.md "명함 신고·차단"의 어드민 쪽. `GET /v1/admin/card-reports?status=OPEN|RESOLVED|DISMISSED`(생략 시 전체, 최신순, 열린 것 먼저) → `{items:[{id, cardId, cardSnapshot, reason, detail, createdAt, status, note, reportCount, cardWithdrawn}]}`. `PATCH /v1/admin/card-reports/:id {status, note?}` 200. `POST /v1/admin/cards/:id/withdraw {reportId?, note}` 204(이미 철회면 204). 모두 ADMIN 역할.
## 반복 일정 — 2026-10-22

native-v1.md "반복 일정과 겹침 결과 묶음"의 어드민 쪽. Schedule 응답(어드민·공개)에 `recurrence: string|null`(RRULE 본문, `RRULE:` 접두사 없음).

| 요청 | 본문 | 결과 |
|---|---|---|
| POST·PATCH /v1/admin/activities/:id/schedules(/:scheduleId) | ScheduleItemInput에 `recurrence?: string\|null` | 생략은 유지, null은 지움 |
| PATCH·POST /v1/admin/activities | `activitySchedule`·`application` 객체에 `recurrence?: string\|null` | 같은 규칙. 일정 PATCH에 `overlapCheck`·`recurrence`만 보낸 요청은 날짜를 유지한다 |

- 검증(400): FREQ는 DAILY·WEEKLY, BYDAY는 MO..SU, INTERVAL 1~4, UNTIL(yyyyMMdd 또는 yyyyMMddTHHmmssZ) 또는 COUNT(1~400) 중 정확히 하나, 그 밖의 키 금지, UNTIL은 시작 이후. 날짜만 있는 일정에 반복을 주려면 `overlapCheck`가 있어야 한다(회차 시간). 서버는 키 순서를 `FREQ;INTERVAL;BYDAY;UNTIL|COUNT`로 정규화해 저장한다.
- 반복을 고치면 기존 규칙대로 `curated`. 수집기 제출의 `recurrence`도 같은 검증을 거치고 실패하면 그 필드만 버린다.
- DB: `schedules.recurrence varchar(200)`(V46).
