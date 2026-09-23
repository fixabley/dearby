# Dearby 웹 구현 인계

## 최신 후속 작업 — 2026-09-24 01:02 KST

담당 세션 `term_f94fe84b-50b8-486d-90aa-0595ce675676`, Task `task_2c46a954b462`, Dispatch `ctx_a30c192f9930`; 아래 초기 구현 기록과 구분한다.

- 대학생 한정 조직 카피를 제거하고 Notice.audience에 고등학생·대학생·취준생의 서로 다른 허용 조합을 명시했다. 추가 조건은 qualification이며 카드와 상세가 동일 공고를 사용한다. 대상 자동판정·프로필·필터는 추가하지 않았다.
- 한국어 활동 요약 포스터 12개, 제목·조직 다음 모집직무/대상/마감·장소의 세 줄과 경험 라벨을 표시한다. 대표 공고는 현재 필터와 기존 점수에 맞는 하나로 선택한다. 선택 직무 종료·다른 직무 모집 중 안내는 별도로 유지했다.
- 수정 전 Chromium 개발모드 새 페이지 회귀 6개 중 5개 실패: 서버 disabled 속성이 그대로 남고 기존 저장 복원도 hydration 불일치했다. 수정 후 개발모드 Chromium/WebKit 각각 6개(총 12개) 통과, console.error/pageerror 없음, 저장 버튼 DOM enabled·aria-pressed·실제 localStorage 변경 확인. 홈/상세/스크랩에서 저장 없음·있음 모두 포함한다.
- lint, typecheck, 단위 12개, production build, production E2E 26개 통과. 기존 12개 흐름과 axe 두 viewport 검사를 유지했다. 생성 next-env.d.ts를 삭제한 뒤 typecheck가 재생성하고 통과함도 확인했다.
- Ponytail review에서 단일 필드 setter 래퍼 4개를 제거하고 snapshot patch를 직접 묶었다. 최종 검토: Lean already. Ship. 별도로 저장 오류/복구·다른 탭 동기화·조건 혼합 회귀를 검사했다.
- 1440×1000 / 390×844 목록·상세 스크린샷을 실제 열어 확인했다. 초기 포스터 3개의 전경색 오류를 발견·수정하고 재캡처했다. 메인 소유 3000 서버 및 Orca page는 조작하지 않았다. Playwright WebKit 검증은 실제 Orca WKWebView 검증을 대신하지 않으므로 메인 통합 후 직접 진입 확인이 남는다.
- Next dev가 AGENTS.md에 자동 추가한 내용은 커밋에 넣지 않는다. next-env.d.ts는 dev/prod 생성 경로가 바뀌므로 추적 제외했으며 typecheck는 next typegen을 먼저 실행한다. 개발 테스트 출력은 기존 ignore 아래 test-results/dev로 지정했다.

스크린샷: `/Users/jominjun/Documents/dearby/dearby-web/test-results/desktop-explore.png`, `desktop-explore-viewport.png`, `desktop-detail.png`, `desktop-detail-viewport.png`, `mobile-explore.png`, `mobile-explore-viewport.png`, `mobile-detail.png`, `mobile-detail-viewport.png` (모두 같은 디렉터리).

초기 구현의 상세 기록은 아래에 보존한다.

완료 기록: 2026-09-24 00:55 KST. 기준 커밋 `be10837e0c1ff498a13622ab9feb50b8f4338ea3`, 작업 브랜치 `fixabley/dearby-web`, checkout `/Users/jominjun/Documents/dearby/dearby-web`. 담당 세션은 `term_c0008d89-171d-424f-8ddc-122136963514`, Task `task_c5abe8d83ebe`, Dispatch `ctx_a529f998bad6`이다. 세션 식별자와 실제 검증 시각은 별개다.

## 구현 결과

root Next.js App Router + TypeScript 웹이다. 2026-09-24 npm registry에서 Next/create-next-app stable `16.3.6`을 확인했고 `npx create-next-app@16.3.6 scaffold-web --ts --eslint --no-tailwind --app --src-dir --use-npm --import-alias '@/*' --yes`로 생성한 파일을 root로 이동했다. 기존 `.git`, AGENTS와 기획 문서는 보존했다. React `19.2.8`, npm lockfile을 사용한다. [공식 설치 가이드](https://nextjs.org/docs/app/getting-started/installation)를 확인했다.

- 56px desktop header, 224px sidebar 및 접기, 가운데 pill 검색, 가로 방향 칩, 경험 필터 dialog, 16:9 포스터와 아바타·제목·메타 그리드.
- 모바일 390px 단일 열, 두 줄 헤더와 하단 탐색. 경험 필터를 첫 칩에 배치했다. 메뉴 버튼은 작은 화면에서 실제 메뉴 표시/숨김으로 동작한다.
- 가상 조직 4개, 프로그램 12개, 현재/과거 및 직군 분리 공고 25개, 직접 구성한 로컬 SVG 포스터 12개. 모든 화면에 샘플과 2026-09-24 모집 상태 기준을 표시한다.
- 전체/모집 중/스크랩 탐색, 텍스트 검색, 방향 OR/모두 포함 AND, 경험 OR 및 방향-경험 AND, 초기화, 현재 공고 단위 필터, 결과 수 중복 제거.
- 경험의 행위/속성은 같은 활동에서 결합한다. 네트워킹 부모 선택은 하위 경험을 포함하되 하위 선택은 부모 전체 조건을 추가하지 않는다. 카드에서 행위별로 묶고 필터 부모 옆에 세부 선택 상태를 표시한다.
- `/programs/[id]` 공유 가능한 상세 12개: 큰 cover, 조직 정보, 구별되는 조직/프로그램 저장 버튼, 회차/직군별 지원 조건·날짜·제공 경험과 근거. 지난 기수 졸업생 교류는 이번 기수 미확인으로 별도 표시한다.
- 조직/프로그램 스크랩 저장·해제·목록·새로고침 복원. 조직 탭에서는 프로그램 필터를 숨기고 조직 개수를 표시한다. 저장은 추천 신호와 분리했다.
- URL에 검색·방향·경험·모두 포함·최우선 경험·모집 상태를 보존한다. 상세 후 브라우저 뒤로와 새로고침에서 복원된다. 모집 중 화면에서 전체 초기화하면 실제 URL과 메뉴도 전체 탐색으로 돌아간다.

## 도메인과 가역적인 프로토타입 선택

정본은 `docs/product/replanning-2026-09.md`, `tag-types-and-profile-draft.md`, `action-based-experience-tags-draft.md`의 최신 승인 절을 읽었다. 다음 세부는 확정 기획이 아니라 이번 프로토타입 선택이다.

- 방향 5개와 경험 묶음 9개만 제공한다. 최우선 경험은 현재 선택한 경험 중 하나다. 자유로운 속성 편집/전체 사전/프로필 설정은 구현하지 않았다.
- 현재 회차는 fixture의 `current` 값이다. 모집 상태는 벽시계와 독립적인 샘플 값이며 과거 회차의 경험은 검색에 합치지 않는다. 종료 프로그램도 지정된 현재 회차의 경험 근거로 검색한다.
- 모집 상태 → 최우선 경험 일치 → 부모/자식 중복을 제거한 경험 묶음 일치 개수 → 빠른 마감 → 최근 모집 시작 → ID 순서다. 서로 다른 현직자/동료 경험을 하나의 점수로 뭉치지 않는다. 최우선 이후 상세 점수와 대표 공고 선택은 운영 정책으로 확정한 것이 아니다.
- 부분 조합은 선택 조건의 일부만 해제한다. OR 그룹은 유지/전체 해제만 고려한다(일부 항목 삭제는 결과를 늘리지 못해 지배되는 후보). 모두 포함인 방향만 부분집합을 만든다. 현재 최대 입력에서 256개 이하 후보를 계산하며 단위 테스트의 최악 입력 두 번 계산은 이 환경에서 약 3.4ms였다.
- 결과가 있는 조합만 유지 조건 수 우선, 결과 수 차순으로 최대 4개 제안한다. 단일 조건도 허용하며 전체 무조건 조회는 제안 대신 초기화로 제공한다. 자동 완화하지 않는다. 선택 시 해제 조건과 즉시 적용, 되돌리기를 제공한다. 되돌리기 알림은 해당 적용 URL에서만 나타나며 추가 조작 시 해제한다.
- 프로그램 검색은 제목·설명·활동 유형 문자열을 대상으로 한다. 스크랩 조직 탭에 별도 조직 검색은 없다.

## 저장·접근성·구조

`src/features/saved/provider.tsx`가 상태를 소유하고 `save-button.tsx`는 표시한다. `dearby:saved:v1` 아래 프로그램/조직 ID 배열을 분리 저장한다. 각 소비자가 useSyncExternalStore로 구독하며 고정 serverSnapshot(빈 저장·ready=false)을 hydration 동안 사용한다. provider별 저장 객체의 첫 구독에서 localStorage를 읽고 이후 실제 스냅샷으로 갱신한다. 상위 effect 기반 복원과 lint 예외는 제거했다.

JSON/스키마 손상은 원문을 유지하며 저장을 차단하고 재시도/손상된 스크랩 초기화를 제공한다. 접근 차단도 구별하고 재시도를 제공한다. 알 수 없는 ID와 중복은 읽기 결과에서 제외한다. 쓰기 실패는 메모리에만 반영되었다고 명시하고 같은 상태로 저장 재시도를 제공한다. 다른 탭의 값 변경 및 `localStorage.clear()`의 `key=null` 이벤트를 반영한다. 여러 탭이 동시에 저장하는 충돌은 마지막 쓰기 우선이며 서버 동기화는 없다.

native dialog의 focus trap/Escape/트리거 focus 복귀, 명시적 라벨·aria-pressed·focus-visible·본문 바로가기·상태 안내·reduced motion을 사용했다. 대비 검사에서 회색 #777/#888을 더 진한 색으로 고쳤다. 최신 Chromium 자동 검사가 실제 보조 기술과 모든 브라우저 검증을 대신하지는 않는다.

`src/app`는 라우팅, `features/catalog`는 데이터·필터·URL·UI, `features/saved`는 저장, `components`는 셸/아이콘이다. Repository/DI/상태 라이브러리는 없다. `Object.groupBy` 대신 Map을 사용해 불필요한 ES2024 브라우저 하한을 피했다. typecheck/lint는 실제 src/tests/config로 한정해 메인에 통합한 뒤 하위 checkout을 중복 검사하지 않도록 했다.

## 실제 실행한 검증

2026-09-24 00:50~00:53 KST 최종 기능 검사, 별도 Playwright Chromium 153(Playwright 1.63.0), 개인 브라우저 미사용.

| 검사 | 결과 |
| --- | --- |
| `npm run lint` | 통과, warning 없음 |
| `npm run typecheck` | 통과 |
| `npm test` | 11개 통과: 논리 결합, 공고/활동 격리, 현재 근거, 중복, 상태 정렬, 최우선/마감/최근 순서, 부모/자식, 유효 부분조합/최악 입력, URL, 저장 파서 |
| `npm run build` | 통과: home, not-found, 프로그램 SSG 12개 |
| `npm run test:e2e` | 12개 통과: 1440×1000와 390×844의 탐색·검색·필터·빈 결과/되돌리기·상세·분리 저장/해제/새로고침·오류/재시도·다른 탭 clear·뒤로 복원·초기화 정합성 |
| axe WCAG 2 A/AA 및 2.1 AA | 두 viewport에서 목록·필터 dialog·상세 위반 0 |
| 키보드/반응형 | dialog Escape/focus 복귀, 메뉴 토글, reduced motion, 목록/상세 가로 overflow 없음 확인 |
| 이미지 | decode 완료 후 desktop/mobile 목록·상세·빈 결과 캡처, 실제 이미지 확인 |
| `git diff --check` | 통과 |

한 번의 뒤로 복원 테스트는 링크 전환 완료 전에 goBack을 호출해서 실패했으며 URL 도착을 기다리도록 테스트를 수정한 뒤 통과했다. 접근성 대비 실패도 수정 후 재검증했다. 모든 결과를 최초 실행부터 통과한 것으로 주장하지 않는다.

ponytail-review: `src/components/icon.tsx:L12: delete: 호출 없는 grid/close variant. Nothing replaces it.` → 정상 수정 단계에서 두 정의와 타입을 제거했다. 최종 재검토: **Lean already. Ship.** 정확성·저장·접근성은 위 별도 검사로 확인했다.

## 실행·스크린샷·통합

```sh
npm ci
npm run dev -- --port 3000
# production
npm run build
npm run start -- --port 3000
# isolated test server is automatically created and stopped on 3210
npx playwright install chromium
npm run test:e2e
```

사용한 테스트 서버는 종료했다. 2026-09-24 00:52 KST `lsof -nP -iTCP:3210 -sTCP:LISTEN` 결과 listener 없음. 메인에서 통합 후 유지할 서버를 시작하면 된다.

스크린샷 실제 경로(현재 checkout에 존재, Git 제외):

- `/Users/jominjun/Documents/dearby/dearby-web/test-results/desktop-explore.png`
- `/Users/jominjun/Documents/dearby/dearby-web/test-results/desktop-explore-viewport.png`
- `/Users/jominjun/Documents/dearby/dearby-web/test-results/desktop-detail.png`
- `/Users/jominjun/Documents/dearby/dearby-web/test-results/desktop-detail-viewport.png`
- `/Users/jominjun/Documents/dearby/dearby-web/test-results/desktop-empty.png`
- `/Users/jominjun/Documents/dearby/dearby-web/test-results/mobile-explore.png`
- `/Users/jominjun/Documents/dearby/dearby-web/test-results/mobile-explore-viewport.png`
- `/Users/jominjun/Documents/dearby/dearby-web/test-results/mobile-detail.png`
- `/Users/jominjun/Documents/dearby/dearby-web/test-results/mobile-detail-viewport.png`
- `/Users/jominjun/Documents/dearby/dearby-web/test-results/mobile-empty.png`

기능 커밋: `9e77e48` scaffold/domain, `c63a954` 저장, `e13797d` 탐색 UI, `9122010` 상세, `d167bf0` URL/부분조합, `f95ec0b` 저장 복구, `a15a641` E2E/CI. 이 문서 이후 인계 커밋까지 같은 브랜치에 있다. 게시 이력 재작성·원격 push·PR·merge·배포는 하지 않았다. CI는 설정만 추가했고 원격 실행하지 않았다.

시각 참조의 한계: worker가 직접 YouTube를 브라우저로 열어 대조한 것은 아니다. coordinator가 Orca tab 경로로 실제 비로그인 홈 헤더와 접힌 탐색을 확인해 전달한 `/tmp/dearby-youtube-reference.png`를 worker도 확인했다. 해당 화면에는 추천 그리드가 없어서 실제 그리드 대조는 하지 못했다. CUA IAB는 미연결이며 white UI와 grid는 요청된 구조를 기준으로 구현했다.

남은 제품 범위: 실제 데이터 수집·API·인증·추천 학습·프로필/자격 자동판정·신청/알림·운영 관리자·실서비스 약관/보관 정책은 미구현이다. Safari/Firefox/실기기·수동 스크린리더·배포환경 검증도 미실행이다. 메인은 자기 checkout에서 통합, 브라우저 재확인, 컨텍스트 목차 연결을 맡는다.
