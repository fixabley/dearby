# Dearby 탐색 어드민

React 19 + Ant Design 5 + TanStack Query + Vite. Kotlin 어드민 API([`shared/contracts/admin-v1.md`](../../shared/contracts/admin-v1.md))만 쓴다. 2026-10-15에 Supabase(데이터·로그인·RLS)와 Refine을 걷어냈다.

## 구조

| 파일 | 역할 |
| --- | --- |
| `src/api.ts` | 같은 화면의 저장·삭제를 한 번에 하나만 보내는 잠금(더블클릭·Enter 반복 방지), 토큰·`user{id, roles}`(메모리 + sessionStorage, roles에 ADMIN이 없으면 API를 부르지 않고 권한 없음 화면), `/api/admin/*` 호출, 401이면 refresh 한 번(동시 요청도 한 번만), 403이면 권한 없음 화면, 패스키 가입·로그인, 로그아웃 |
| `src/list.tsx` | `{items, nextCursor}` 목록(‘더 보기’), 트리 한 단계 전체 읽기(`nextCursor`를 끝까지, size 100), 조직·프로그램 이름 검색 선택 |
| `src/organizations.tsx` | 조직도: 조직 → 하위 조직 → 프로그램 → 활동(초안 포함)을 펼칠 때 읽음, 한글·영문 이름과 상위(운영) 조직을 행에서 수정, 활동 이름·설명 편집, 트리 안에서 하위 조직·프로그램 생성, 조직·프로그램·활동 삭제(확인 후, 서버가 거절하면 그 행에 사유 표시), 검색하면 `ancestors`로 맞는 조직까지 펼침 |
| `src/programs.tsx` | 프로그램 검색(조직 필터)·생성(영문 이름 선택), 수집 설정 조회·저장 |
| `src/activities.tsx` | 활동 검색(조직·프로그램·게시 상태, 초안 포함), 출처 표시, 게시/게시 취소, 한글·영문 이름·설명 편집, 삭제 |
| `src/main.tsx` | 로그인 · 권한 없음 · 관리 화면 |

브라우저는 같은 출처의 `/api/auth/*`·`/api/admin/*`만 부른다. CORS와 브라우저 환경변수는 없다.

| 경로 | 로컬(`vite.config.ts` 프록시) | 운영(`vercel.json` rewrites) |
| --- | --- | --- |
| `/api/auth/*` | `http://localhost:8080/v1/auth/*` (Kotlin api) | `https://api.dearby.wid.io.kr/v1/auth/*` |
| `/api/admin/*` | `http://localhost:8081/v1/admin/*` (Kotlin admin) | `https://api.dearby.wid.io.kr/v1/admin/*` |

나머지 경로는 `index.html`(SPA). Vercel 프로젝트 `dearby-admin`의 Root Directory는 `apps/admin`이고, `ignoreCommand`로 이 폴더가 바뀌지 않은 커밋은 빌드하지 않는다.

## 로컬 실행

Node 24. Kotlin api를 8080, admin을 8081에 띄운 뒤(`apps/dearby-api` 안내 참고):

```sh
npm ci
npm run dev
```

**http://localhost:5173**으로 연다. `127.0.0.1`로 열면 패스키가 동작하지 않는다(로컬 RP ID가 `localhost`라 IP 출처와 맞지 않음). 로컬 api의 `WEBAUTHN_ALLOWED_ORIGINS`에 `http://localhost:5173`이 있어야 한다.

## 로그인과 첫 관리자

1. ‘새 패스키로 시작’으로 계정을 만든다(Dearby 앱과 같은 패스키 계정, RP ID `wid.io.kr`). 이미 앱에서 만든 패스키가 있으면 ‘패스키로 로그인’을 쓴다.
2. 처음에는 ‘관리자 권한이 없어요’ 화면이 나온다. 화면에는 계정 ID·SQL 같은 내부 정보를 보여 주지 않는다(2026-10-15 사용자 결정). 운영자가 DB에서 방금 가입한 사용자를 찾아 역할을 넣는다. 어드민에 권한을 주는 화면은 없다.

   ```sql
   -- 최근 로그인한 사용자(로그인·가입마다 refresh token이 생긴다)
   select user_id, created_at from refresh_tokens order by created_at desc limit 5;
   insert into user_roles (user_id, role) values ('<사용자 UUID>', 'ADMIN');
   ```

3. ‘다시 확인’을 누르면 refresh로 새 토큰을 받아 역할을 다시 읽는다.

운영 API의 `WEBAUTHN_ALLOWED_ORIGINS`에 `https://admin.dearby.wid.io.kr`이 있어야 어드민에서 패스키를 쓸 수 있다.

## 운영 흐름

1. 조직도에서 조직 → 하위 조직 → 프로그램을 만든다(프로그램은 조직 행의 ‘프로그램 추가’ 또는 프로그램 화면). 프로그램의 수집 설정에 공식 안내 URL(https)과 **공식 출처 호스트**를 넣는다. 호스트가 비면 매일 밤 웹 전체를 검색해 토큰을 약 5배 쓰고 비공식 페이지가 섞인다.
2. 매일 밤 수집한 활동은 초안(`DRAFT`)으로 들어온다. 활동 화면에서 공식 안내를 열어 확인하고 게시한다. ‘웹 검색 출처’ 초안은 게시 전에 한 번 더 확인 창이 뜬다.
3. 게시를 취소하면 초안으로 돌아가 공개 API에서 빠진다.
4. 조직·프로그램·활동의 영문 이름(선택)과 활동 영문 설명은 비워 두면 없음으로 저장된다. 영문 이름도 아래 이름 중복 규칙을 따른다. 수집 초안은 다시 수집하면 덮어써지므로, 고친 이름·설명을 지키려면 게시한다.
5. 삭제는 되돌릴 수 없다. 하위 조직·프로그램이 있는 조직, 활동이 있는 프로그램은 서버가 거절한다. 프로그램을 지우면 수집 설정·기록도 지워지고, 수집으로 생긴 활동을 지우면 다시 수집되지 않는다. 같은 단계에서 공백만 다른 이름은 중복으로 거절된다.

활동의 이름·설명 외 내용(일정·링크 등) 편집, 수집 기록, 감사 로그는 API에 아직 없다(계약 ‘아직 없는 것’).

## 검증

```sh
npm run lint
npm run typecheck
npm run build
```

자동 테스트는 2026-10-06 사용자 요청으로 지웠다. [`docs/evidence`](docs/evidence/README.md)는 2026-09 Supabase 어드민 시절 화면 기록이다.

## 이력 검토·역할 (2026-10-16)

- `histories.tsx`: 사용자가 직접 적은 조직·프로그램·활동·역할을 카탈로그 항목에 매핑(검색 또는 새로 만들기)해 승인하거나 사유를 적어 거절한다(`POST histories/:id/approval`). 인증자료는 bearer를 붙여 blob으로 받아 새 탭에서 연다(`apiBlob`). 인증 확인 탭은 `POST histories/:id/verification`.
- `roles.tsx`: 역할 카탈로그 CRUD. 띄어쓰기만 다른 이름은 서버가 400으로 거부하고 그 사유를 그대로 보인다.
- 계정 id는 화면에 보이지 않는다(작성자 프로필 이름만).
