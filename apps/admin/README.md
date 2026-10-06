# Dearby 탐색 어드민

> 현재 Vercel/cloud 배포 준비와 검증 경계는 [배포 인계](../../docs/context/admin-web-deployment.md)를 따른다. 아래 로컬 구축·과거 검증 기록은 당시 환경 기준이다.

React + Refine 5 + 공식 Supabase data provider + Ant Design 5. 조직/프로그램/활동 생성·편집, 행사 일정, 게시/숨김, 공식 확인, 제목 검색/게시 필터/페이지 이동, 변경 기록을 제공한다. 데이터는 로컬 Supabase Postgres에 저장하며 기존 API `/v1/catalog`가 동일한 데이터에서 공개 DTO를 만든다. 앱의 계정·명함 데이터는 기존 SQLite에 남는다.

## 로컬 실행

Node24와 Docker/OrbStack이 필요하다. 저장소 root에서:

```sh
npm ci --prefix apps/admin
npm ci --prefix apps/dearby-api
npm exec --yes --package=supabase@2.118.0 -- supabase start
node supabase/scripts/bootstrap-local.mjs
npm run dev --prefix apps/admin
```

다른 터미널에서:

```sh
cd apps/dearby-api
node --env-file=.env.admin-local --import tsx src/server.ts
```

- 어드민: http://127.0.0.1:5173
- 기존 API의 Supabase 연결 인스턴스: http://127.0.0.1:58765/v1/catalog
- Supabase Studio: http://127.0.0.1:54323
- 로그인: `supabase/.env.credentials.json`에 생성한 로컬 전용 이메일·비밀번호. 파일은 Git에서 제외한다. 일반 공개 회원가입/권한 부여 UI는 없다.
- bootstrap은 localhost만 허용하며 과거 공식 출처30건을 **초안/미확인**으로 가져온다. 같은 ID가 있으면 관리자 수정 내용을 덮어쓰지 않는다. 현행 모집이나 실시간 수집 결과가 아니다.
- 종료는 각 dev 서버 Ctrl-C, `npm exec --yes --package=supabase@2.118.0 -- supabase stop`. 데이터 삭제를 뜻하는 reset은 일상 종료에 사용하지 않는다.

## 운영 흐름

1. 조직 → 프로그램을 등록하고 활동을 만든다. 기존 스냅샷 활동은 바로 편집할 수 있다. `/organizations`는 조직 → 하위 조직(최대 4단계) → 프로그램 → 활동 트리이며, 조직·프로그램의 이름·소개·상위 조직을 행에서 바로 고친다. 수집 설정이 있는 프로그램 생성·편집은 `/programs`에서 한다. 계층 마이그레이션(`catalog_organization_tree`) 적용 전에는 조직이 평면 목록으로 보이고 상위 조직 편집을 숨기며 `parent_id`를 보내지 않는다.
2. 행사/모집 시각·지원 조건·공식 및 신청 URL을 입력한다. 미확인 시각은 비워 둔다. 시각 입력은 브라우저의 현재 시간대이며 UTC로 저장한다. 행사 시간대는 앱의 표시 기준이다.
3. 저장 후 ‘공식 안내 열기’로 실제 원문을 확인한다. ‘공식 정보 확인 기록’에 모집 상태·마감·행사 일정의 내부 근거를 남긴다(방문자에게 보이지 않음). 방문자에게 보일 문장은 활동 편집의 ‘방문자에게 보일 확인 안내’에 쓰며, 바꾸면 공식 확인이 해제된다. 버튼이 원문을 자동 검사하는 것은 아니다.
4. 게시로 저장한다. **게시 + 현재 모집 중 + 최대24시간 이내의 유효한 확인 + 아직 지나지 않은 마감**만 탐색에 노출된다. 활동 본문/일정/URL/모집 상태 변경은 기존 확인을 해제한다.
5. 숨김은 공개 API에서 제외한다. 기본 어드민은 물리 삭제를 제공하지 않는다. 변경 기록은 서버에서 자동 기록하며 클라이언트 수정/삭제는 금지한다.

정확한 시작/종료가 모두 있는 일정만 기기 캘린더 비교에 사용할 수 있다. 시각이 없는 날짜 안내를 임의의 00:00으로 채우지 않는다. 과거/마감 활동도 게시 상태라면 상세/참조를 위해 API에는 남지만 탐색에서는 제외된다. 숨김/초안은 공개 DTO에서 제거되므로 기존 기기의 참조가 해당 활동을 더 이상 찾지 못할 수 있다.

## 권한과 데이터 경계

- 로그인 후 DB의 `auth.users.raw_app_meta_data.catalog_admin=true`가 확인된 관리자만 관리 테이블에 접근한다. 사용자가 바꿀 수 있는 user_metadata는 권한 근거가 아니다.
- 실제 RLS가 관리자 읽기/쓰기와 감사 기록 읽기를 제한한다. 권한 해제는 DB에서 즉시 적용한다. UI 접근 제한만으로 보호하지 않는다.
- 브라우저는 공개 키만 사용한다. 서비스/비밀 키는 bootstrap과 테스트가 로컬에서만 사용하며 번들/커밋에 넣지 않는다.
- 공개 RPC는 게시한 활동과 참조 부모만 단일 DB 스냅샷으로 제공한다. API는 공개 키로 RPC를 읽고 기존 `atTime` 규칙을 다시 적용한다.
- Supabase 연결 장애는 API503이며 SQLite/빈 배열로 조용히 대체하지 않는다. 설정하지 않은 API는 기존 SQLite 모드로 유지된다.
- 기존 SQLite 수집 명령은 Supabase 모드에서 거부한다. 이 첫 구현은 수동 검토/관리이며 자동 수집기의 Supabase 저장 전환이나 정기 재확인은 포함하지 않는다.

## 검증

```sh
npm run build --prefix apps/admin
npm test --prefix apps/admin
node supabase/tests/local.mjs  # 로컬 Supabase와 API58765 실행 필요
cd apps/admin
npx playwright install chromium
npm run test:e2e             # 어드민5173·API58765·bootstrap 필요
```

조직 트리 회귀(`organizations.spec.ts`)는 스스로 관리자·조직·프로그램·활동을 만들고 끝나면 지운다. 어드민 dev 서버와 같은 로컬 Supabase를 지정해야 하며, localhost가 아니면 실행을 거부하고 환경값이 없으면 건너뛴다.

```sh
ADMIN_TEST_URL=http://127.0.0.1:5173 ADMIN_TEST_SUPABASE_URL=http://127.0.0.1:54321 \
ADMIN_TEST_SERVICE_ROLE_KEY=<로컬 supabase status의 SERVICE_ROLE_KEY> npm run test:e2e -- organizations.spec.ts
docker exec -i supabase_db_dearby psql -U postgres -X -v ON_ERROR_STOP=1 < ../../supabase/tests/organization-tree.sql
```

브라우저 회귀는 명시적인 로컬 테스트 조직/프로그램/활동을 만들고 활동을 숨김으로 남긴다. 운영 환경을 대상으로 실행하지 않는다. RLS 테스트의 임시 사용자와 레코드는 테스트 종료 시 정리하며 감사 기록은 보존한다. [실제 화면·검증 기록](docs/evidence/README.md).

## 추후 클라우드 연결

새 Supabase 프로젝트 생성, 비밀 키 설정, 운영 데이터 이관 및 배포는 별도다. 검토한 migration을 해당 프로젝트에 적용하고 신뢰할 수 있는 서버/SQL 관리 경로로 관리자 권한을 부여한다. 브라우저에 `VITE_SUPABASE_URL`/공개 키, 기존 API에 `CATALOG_BACKEND=supabase`/`SUPABASE_URL`/`SUPABASE_ANON_KEY`를 설정한다. URL은 로컬을 제외하고 HTTPS여야 한다. 관리자 권한과 공개 DTO를 검증한 뒤 트래픽을 전환한다. 이 저장소의 로컬 bootstrap은 원격 프로젝트에 사용할 수 없다.

공식 문서: [Refine Supabase data provider](https://refine.dev/core/docs/data/packages/supabase/), [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), [로컬 개발](https://supabase.com/docs/guides/local-development).

## 구조화된 참가 조건

활동 편집의 참가 대상·지원 자격·모집 역할은 `catalog_activities.criteria` JSONB에 저장한다. 숫자, 문자열, 참/거짓, null, 날짜(YYYY-MM-DD 문자열), 중첩 객체/배열을 선택해 입력한다. 문자열 배열은 태그 UI를 제공한다. ‘범위 객체 만들기’는 `부터`와 `까지` 숫자 항목을 만드는 편의 기능이며 단일 숫자도 허용한다. 한쪽 항목을 제거하면 최소/최대만 표현할 수 있다.

기존 대상/자격 문장은 추가 설명으로 유지하고 기존 역할 배열을 JSON으로 복사한다. JSON과 기본 정보는 같은 행에서 원자적으로 저장하고 기존 수정 충돌·공식 확인 무효화·감사 기록 규칙을 적용한다. 네이티브 API의 audience/qualification/roles 형태는 유지하며 서버에서 JSON을 표시 문자열로 변환한다. 원본 JSON은 관리자 DB에서 보존한다.

자동완성은 같은 분류·중첩 경로의 저장된 키를 부분 일치/pg_trgm 유사도로 추천한다. 문자열·문자열 배열의 기존 값도 추천한다. 없는 이름은 직접 입력할 수 있고 비슷한 이름을 자동 병합하지 않는다. 같은 객체 내 공백/NFC/대소문자 중복 키를 방지한다. 서로 다른 활동의 동일한 키가 다른 타입을 가지면 ‘여러 형식’으로 표시한다. 추천 RPC는 관리자만 호출할 수 있다.

각 분류 기준 중첩 8단계, 객체 30개 키, 배열 100개 원소, 키 80자, 문자열 2,000자, 전체 64KB, 숫자 절댓값 9,007,199,254,740,991까지 지원한다. 임의 정밀도 숫자·Date 객체는 JSON 호환성을 위해 지원하지 않는다. 깊은 구조는 긴 폼이 되므로 실무 항목은 간결하게 유지한다.

JSON 포함 검색용 GIN 인덱스가 있다. 예: `criteria @> '{"qualification":{"연차":3}}'`. 이 인덱스는 모든 숫자 범위·정렬·키 유사도 검색을 자동 가속하지 않는다. 자주 쓰는 숫자/날짜 필터가 정해지면 해당 경로의 타입을 통일하거나 표현식 인덱스를 추가한다. 추천은 현재 규모에 맞춰 JSON에서 계산하며 대규모 부하 검증은 하지 않았다.

## 활동 수집 관리

프로그램 편집에서 `매일 활동 수집`과 `공식 출처 호스트`를 설정한다. `활동 수집` 메뉴에서 오늘 수집을 등록하고 작업 상태·오류·시도별 CLI 사용량·후보를 펼쳐 볼 수 있다. 호스트는 URL 전체가 아닌 정확한 소문자 도메인이고, 신규 프로그램의 자동 수집은 기본 OFF다.

후보의 원문과 제안 JSON을 검토한 뒤 `활동 편집`으로 이동한다. 관리자 내용을 자동 덮어쓰거나 자동 게시·공식 확인하지 않는다. 구독 인증·한도 오류가 발생하면 원인을 해소한 뒤 작업의 `원인 해소 후 재시도`를 누른다. 이 동작은 전역 구독 일시정지도 해제한다. 일일 등록·로컬 Mac 설치/해제·검증 한계는 [워커 안내](../catalog-worker/README.md)를 따른다.
