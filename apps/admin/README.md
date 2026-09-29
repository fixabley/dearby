# Dearby 탐색 어드민

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

1. 조직 → 프로그램을 등록하고 활동을 만든다. 기존 스냅샷 활동은 바로 편집할 수 있다.
2. 행사/모집 시각·지원 조건·공식 및 신청 URL을 입력한다. 미확인 시각은 비워 둔다. 시각 입력은 브라우저의 현재 시간대이며 UTC로 저장한다. 행사 시간대는 앱의 표시 기준이다.
3. 저장 후 ‘공식 안내 열기’로 실제 원문을 확인한다. ‘공식 정보 확인 기록’에 모집 상태·마감·행사 일정의 근거를 남긴다. 버튼이 원문을 자동 검사하는 것은 아니다.
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

브라우저 회귀는 명시적인 로컬 테스트 조직/프로그램/활동을 만들고 활동을 숨김으로 남긴다. 운영 환경을 대상으로 실행하지 않는다. RLS 테스트의 임시 사용자와 레코드는 테스트 종료 시 정리하며 감사 기록은 보존한다. [실제 화면·검증 기록](docs/evidence/README.md).

## 추후 클라우드 연결

새 Supabase 프로젝트 생성, 비밀 키 설정, 운영 데이터 이관 및 배포는 별도다. 검토한 migration을 해당 프로젝트에 적용하고 신뢰할 수 있는 서버/SQL 관리 경로로 관리자 권한을 부여한다. 브라우저에 `VITE_SUPABASE_URL`/공개 키, 기존 API에 `CATALOG_BACKEND=supabase`/`SUPABASE_URL`/`SUPABASE_ANON_KEY`를 설정한다. URL은 로컬을 제외하고 HTTPS여야 한다. 관리자 권한과 공개 DTO를 검증한 뒤 트래픽을 전환한다. 이 저장소의 로컬 bootstrap은 원격 프로젝트에 사용할 수 없다.

공식 문서: [Refine Supabase data provider](https://refine.dev/core/docs/data/packages/supabase/), [Supabase RLS](https://supabase.com/docs/guides/database/postgres/row-level-security), [로컬 개발](https://supabase.com/docs/guides/local-development).
