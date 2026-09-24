# Dearby 웹 구현 인계

## GitHub 목록 참고 카탈로그 보강 — 2026-09-24 16:44 KST

사용자가 이 하위세션에 직접 요청한 후속 작업: 한국 IT 컨퍼런스 GitHub repository를 참고해 보강. 기준95fead2이며 새 Dispatch 없이 사용자 소유 작업으로 수행했다. 종료된Task/Dispatch lifecycle을 재전송하지 않는다.

참고한 저장소:
- https://github.com/brave-people/Dev-Event : 컨퍼런스 외 교육/대회/밋업도 있어 범위를 선별해야 한다. UbuCon·MiniDebConf 후보를 발견했다.
- https://github.com/hibuz/dev-conf-replay : 다시보기 중심 목록으로 과거회차/기업별 행사를 찾는 데 유용하다. OpenInfra/KCD, REAL Summit, SK AI Summit 후보를 발견했다. 저장소의 과거 날짜를 최신회차로 옮기지 않고 공식페이지로 재확인했다.

4프로그램/4공고/4조직 추가로22프로그램·24공고·20조직. UbuCon×MiniDebConf2026 및 KCD×Ceph×OpenInfra2026 공동행사는 각1카드로 중복제거했다. REAL Summit2026은 삼성전자와 다른 삼성SDS 조직, SK AI Summit은 확인된2025회차다. 모두 종료행사로 전체탐색에 나타나고 모집·등록중에는 제외된다. Linux분야를 추가했으며 미확인 비용/자격은null 유지한다. 공식OG4개와 SK/삼성SDS 기업로고2개를 로컬저장, 원본/확인일은 public/conferences/SOURCES.md와 public/organizations/SOURCES.md에 기록했다. 자동수집·저장소복제·데이터동기화는 없다.

핵심 재확인: UbuCon최종8/25 0시 마감은 이전8/16안내보다 우선하고 AWS센터필드18층 최신확정장소를 사용한다. OpenInfra의 남은등록중버튼은9/1종료행사로 해석한다. REAL9/8코엑스종료·체험프로그램은 공식SDS보도자료로 확인했으며 가격은추정하지 않았다. SK는2025년11/3–4일코엑스·온라인중계이며2026개최를추정하지 않는다. 출처별 사실근거는 공고sources에 기록했다.

담당검증: lint/typecheck/unit13/build(22프로그램SSG)/productionE2E36/dev Chromium9+WebKit9=18 통과. 기존18개 기대값을22개로 갱신하고 공동행사중복방지/최종공식공지우선/모집상태/삼성SDS조직분리/Linux검색의 의미규칙unit1개를 추가했다. 추가4개 모두1440×1000/390×844에서 검색→상세→종료표시·공식링크→스크랩새로고침복원과console.error/pageerror0·overflow없음 확인. 실제 `test-results/desktop-added-openinfra.png`, `desktop-added-real.png`, `mobile-added-ubucon.png`를 열어 썸네일/로고를 검토했다(추가4개×2viewport캡처,Git제외). 기존E2E의저장오류/hydration/axe검사도 통과했다.

Ponytail diff: **Lean already. Ship.** 정적데이터와분야1개만 추가하고 새추상화/의존성없음. git diff --check 통과,3210/3211종료,AGENTS자동diff미커밋보존. root3000/다른checkout/Orca page는 건드리지 않았으며 메인통합·실제앱반영은 별도다. 이번4개가한국전체목록을완성한것은아니며 공식정보를확인한보강분이다.

## Spring Camp 주최 KSUG 로고 추가 — 2026-09-24 11:12 KST

Task `task_7bdc3443d670`, Dispatch `ctx_49877680dea4`, 세션 `term_db69f423-a4bc-45ae-910d-ede154eab9d9`, 기준 `2373bc5`. https://ksug.org/ 의 한국 스프링 사용자 모임 소개와 상단 조직 로고를 직접 확인했다. 원본 https://ksug.org/assets/images/logo.png 를 `public/organizations/ksug.png`로 그대로 저장하고 org-ksug.logo만 추가했다. Spring 프레임워크/Pivotal/행사 포스터 대신 KSUG / KOREA SPRING USER GROUP 마크를 사용한다. 출처·확인일은 public/organizations/SOURCES.md에 기록했다.

기존 OrganizationAvatar를 재사용해 카드·상세·스크랩에 반영되며 레이아웃/동작은 변경하지 않았다. 담당 실제검증: lint/typecheck/build 통과, 분리한 production3210 Chromium에서1440×1000/390×844 각각 카드/상세/스크랩 이미지 decode·조직저장/새로고침복원·console.error/pageerror0·가로overflow없음 확인. `test-results/desktop-ksug-card.png`, `mobile-ksug-saved.png`를 실제 열어 검토했다(카드/상세/스크랩 모두 양 viewport 캡처, Git제외). 원본 하단 영문은 작은아바타에서 작지만 KSUG 마크와 인접조직명으로 식별된다. 단순 데이터/자산 변경으로 새unit/전체E2E는 추가·재실행하지 않았다. Ponytail diff: **Lean already. Ship.**

3210 테스트서버 종료, 기존 AGENTS자동diff는 보존·미커밋. 다른checkout/root3000/Orca브라우저는 건드리지 않았다. 이전기업로고의 root1f9cbdc 통합검증완료는 메인 전달사항이며 이번 KSUG 변경의 메인통합/최종확인은 남는다. 완료보고 후 사용자요청대로 세션을 유지하고 대기한다.

## 기업 로고 아바타 완료 — 2026-09-24 10:24 KST

Task `task_9caee518b4c1`, Dispatch `ctx_beeec3876c97`, 세션 `term_db69f423-a4bc-45ae-910d-ede154eab9d9`, 자기 checkout 기준 `4a6df2d`. 사용자 기업 아바타 요청으로 이전 대기를 대체했다.

- 기업8개(우아한형제들/NAVER/Kakao/Toss/LY/AWS/ktcloud/Samsung) 공식 사이트의 기업 로고를 로컬 SVG6/PNG2로 저장했다. 행사 포스터나 임의 생성 텍스트가 아니다. 원본URL·추출위치·2026-09-24 확인일은 [로고 출처](../../public/organizations/SOURCES.md)에 있다. 우아한형제들 홈페이지의 진입 오류 때문에 최초HTML→공식CDN header-logo 모듈로 확인해 원본 path/clipPath/viewBox를 옮겼다. 재사용 권리를 확인했다고 주장하지 않는다.
- 공통 `OrganizationAvatar`를 카드·상세·스크랩 조직 목록 3곳에 적용했다. Organization의 optional logo 경로만 추가했다. 기존35/44px, 흰 배경·3px여백·contain 유지. 로고없음/이미지오류는 기존initial+조직색으로 표시한다. 인접 조직명이 있어 aria-hidden 및 빈alt로 중복읽기를 피한다. 커뮤니티/GDG는 회사로 오인해 로고를 붙이지 않았다. 새 dependency·범용디자인시스템·수집기는 없다.
- 담당 실제검증: lint/typecheck/unit12/build 통과. production E2E36(기존32+로고/실패회귀 각viewport2개), dev Chromium9+WebKit9=18 통과. 기업8개 naturalWidth 로드·커뮤니티fallback·이미지404fallback·조직/프로그램 독립저장·새로고침복원/해제 검사. 정상경로 console.error/pageerror0, 기존hydration/저장오류회귀 및 axe AA0·overflow없음 확인. 의도적인404 테스트에서는 네트워크에러 자체를 무오류라고 주장하지 않는다.
- 최초검사는 기존 포스터locator가 추가된 로고까지 선택해 prod2/dev2가 실패했고, 새 로고src 기대값이 Next의 절대URL 처리와 달라 prod2가 실패했다. 포스터 선택자를 `.cover-link img`로 구체화하고 로고원본 경로를 현재 origin의 절대URL로 비교한 뒤 모든 검사를 재통과했다. 검사완화나 구현복제unit은 추가하지 않았다.
- 실제 열어 본1440/390 캡처: `test-results/desktop-logos-cards.png`, `desktop-logos-saved.png`, `mobile-logos-saved.png`, `mobile-logos-detail.png`. 로컬 Git제외. 긴워드마크는 기존35px내에서 작게 보이지만 잘리지 않으며 인접조직명으로 보완한다. fullPage 모바일 고정하단탭은 viewport위치에 찍히는 캡처 특성이 있다.
- Ponytail diff review: **Lean already. Ship.** 실제3곳에서 공통컴포넌트를 사용하고 로고경로와 실패상태만 둔다. 정확성·접근성·저장은 별도 브라우저검사로 확인했다. `git diff --check` 통과. 10:24 KST 3210/3211 listener없음. AGENTS 자동diff는 보존·미커밋, 다른checkout/root3000/Orca page/제품정본/CI는 수정하지 않았다.

메인 전달상태는 별도: 이전 컨퍼런스 변경이 root703c136에 통합되고 root전체검증·실제Orca확인이 완료됐으며 최신root문서b8e6e7a라는 지시를 받았다. 이번 로고의 root통합·실제Orca최종확인은 메인에 남는다. 완료보고 후 사용자에게 보이는 세션으로 유지하고 다음 요청을 기다린다.

## 공식 컨퍼런스 카탈로그 완료 — 2026-09-24 01:43 KST

기능 커밋: `f0489a5` (기준 `1dae183` 다음). 인계 커밋 `d9a8627` 뒤 메인 후속 근거를 받아 Toss/DEVIEW 데이터를 보완했다. 보완 후 lint/typecheck/unit12/build/prod32/dev18 전부 재통과했으며 목록 캡처를 다시 열어 확인했다. Ponytail 재검토에서 추가 추상화/의존성은 없었다.

담당 checkout `/Users/jominjun/Documents/dearby/dearby-web`, 기준 `1dae183`, 세션 `term_db69f423-a4bc-45ae-910d-ede154eab9d9`, Task `task_11416b48d24f`, Dispatch `ctx_259771a9ca90`. 새 구현 요청으로 이전 대기를 대체했다. 아래 과거 기록은 당시의 구현/검증이며 현재 데이터는 이 절을 따른다.

- 실제 공식 출처 확인 컨퍼런스 18개·회차 20개·조직 16개로 가상 데이터를 교체했다. FEConf, WOOWACON, NAVER DAN, if(kakao), Toss Makers, Tech-Verse, PyCon Korea, Spring Camp, Let’Swift, GopherCon, AWS Summit Seoul, kt cloud summit, Droid Knights, DevFest Cloud x Seoul, DevFest Korea Android, Samsung AI Forum, Samsung Tech Conference, DEVIEW를 포함한다. if(kakao)2026/2025와 Spring Camp2026/2025를 분리했다. 2026이 확인되지 않은 행사는 확인된 과거 회차를 표시하며 2026 개최를 추정하지 않는다.
- 등록 중 3개: if(kakao)2026(9/28 낮12시 마감·만18세이상·무료·오프라인 선정), 우아콘2026(10/13 마감·무료·추첨), 드로이드나이츠2026(11/1 23:59 마감·일반69000원/개인후원150000원). FEConf2026은 공식 TICKET OPEN D-14 표시로 등록 예정, 비용/정확한 시작일 미확인이다. 삼성 AI 포럼2026은 현장 초청/확정 임직원 제한을 명시하고 등록기간 미확인으로 등록 중이라 추정하지 않았다. 나머지 종료행사도 탐색 가능하다. Toss는 공식 종료 보도자료를 추가 확인해 2025년7월23–25일 코엑스 그랜드볼룸·기획/디자인/데이터·현직자 네트워킹을 보완했다. 비용·자격은 null이다. DEVIEW는 공식 D2의 2024년 DAN 통합 설명과 출처를 추가해 2023 과거 아카이브로 구분했다. 이미지 권리 미확인은 개발 문서에 보존하고 제품에는 출처·확인일만 간결히 표시한다.
- 공식 OG14개·대표영역 캡처4개를 로컬 저장했다. [이미지 출처](../../public/conferences/SOURCES.md)에 원본URL/공식페이지/방식/확인일을 기록하고 개별 회차 sources에 정보 근거를 넣었다. 드로이드 OG404, SpringCamp/AWS 회차OG 미확보, GDG Android 범용OG는 캡처로 대체했다. 이미지 재사용 라이선스 허용을 주장하지 않는다. 일회성 2026-09-24 스냅샷이며 자동 수집기가 아니다.
- 16:9 이미지에 contain을 사용해 글자 잘림을 막았다. 카드 행사명·회차·조직·상태·개최일·장소·비용, 상세 참가 대상/조건·마감·공식 링크·주제/경험·출처확인일을 제공한다. 모바일은 참가 정보가 긴 설명보다 먼저 나온다. 회사/조직 검색, Android/AI/데이터/클라우드 등 12분야, 5등록상태를 지원한다. 연사 발표를 참가자의 발표 경험으로 넣지 않았다. 멘토링은 별도 선정/신청 조건을 명시한다.
- 같은 현재 공고/활동 안에서 필터를 결합하고 과거 회차 조건을 혼합하지 않는다. 12분야 AND 부분조합은 실제 공고가 지원하는 최대 선택 부분집합만 후보로 만들어 지수 조합을 피한다. 최대4개 대안과 되돌리기, URL상태, 독립 조직/프로그램 스크랩, 저장오류/탭동기화/SSR hydration은 유지했다. 가상 ID 스크랩은 기존 검증기로 걸러지며 실재 조직에 임의 이관하지 않는다.

담당 checkout에서 실제 최종 실행: `npm run lint`, `npm run typecheck`, `npm test`(12), `npm run build`(18프로그램 SSG), `npm run test:e2e`(32), `npm run test:dev`(Chromium9+WebKit9=18) 모두 통과. 단위 수 변경은 가상fixture를 실제 데이터 검증+합성 의미규칙 테스트로 재구성한 결과이며 같은 공고/활동, OR/AND, 정렬/중복가산, 대안, URL, 저장 검사를 유지했다. production/dev 흐름에서 console.error/pageerror0, hydration/스크랩 실제 DOM·localStorage 복원, overflow 없음, axe WCAG AA 위반0을 검사했다. 최초 잘못 입력한 `test:e2e:dev`는 없는 스크립트로 실패했으며 실제 `test:dev`로 실행했다. 원격CI·실기기·Orca WKWebView 검증은 이 worker에서 하지 않았다.

1440×1000/390×844 스크린샷을 실제 열어 검토했다: `test-results/desktop-explore.png`, `mobile-explore-viewport.png`, `desktop-official-detail.png`, `mobile-official-detail.png`, `mobile-official-detail-viewport.png` (로컬 Git 제외). GDG 캡처 쿠키창을 제거해 재캡처했고, 상세 캡처 전 scrollTo(0,0)로 고정헤더 위치를 바로잡았다. fullPage 캡처의 모바일 고정 하단탭은 원래 viewport 높이에 그려지므로 viewport 캡처도 별도 확인했다. 첫 줄 이미지 eager 로딩으로 개발모드 LCP 경고를 해소했다. 원본 아트워크의 작은 글자는 썸네일에서 작을 수 있어 카드 텍스트로 핵심정보를 반복한다.

Ponytail diff review: 호출 없는 recruitment helper, 이전 cover-status/open-text/notice-date 스타일, 미사용 가상 SVG13개를 제거했다. 새 의존성·범용 수집기·추상화 계층을 추가하지 않았다. 최종 **Lean already. Ship.** 정확성·접근성·저장은 위 별도 검사로 확인했다. `git diff --check` 통과. 01:43 KST 3210/3211 listener 없음; 테스트 서버는 종료됐다. root3000·Orca page·다른checkout·메인AGENTS/제품정본/조사/CI를 건드리지 않았다. Next 자동 AGENTS diff는 보존하고 커밋 제외한다.

메인 전달 검증(이 작업과 구분): 이전 `1dae183`이 root `c380756`에 통합되고 root lint/typecheck/unit13/build/prod28/dev14 및 실제 Orca 컨퍼런스 상세·스크랩·consoleerror0가 통과했다는 전달을 받았다. **이번 실제18개 전환의 root 통합/Orca 확인은 아직 남는다.** 메인 연구 문서60행에서 확인된 나머지 후보 확대는 다음 범위다. 결제/신청실행/API/인증/자동수집/프로필은 미구현이다. 완료 보고 후 세션을 종료하지 않고 사용자에게 보이는 인계 세션으로 유지하며 새 요청을 기다린다.

## 메인 통합 후 검증 — 2026-09-24 01:47 KST

Root `e829b74/b4ecc3a/703c136`에 통합했다. root에서 lint/typecheck/unit12/build/prod32/dev Chromium9+WebKit9 전부 통과했다. 1440/390 캡처 실제 확인, Orca 실제 목록→우아콘 상세→저장→새로고침 복원·해제·console error0 확인. http://127.0.0.1:3000 실행 중이고 세션은 사용자 요청으로 retain 완료. root 결과는 위 worker 결과와 별개 실행이다. 위의 “root 검증 남음”은 worker 완료 당시 기록이며 이 절이 최신이다.

이전 가상 데이터/초기 구현 기록은 [보관본](archive/2026-09-24-web-prototype/web-implementation-and-handoff.md)을 참고한다. 현재 상태·세션·다음 행동은 [조율 문서](coordinator-current-task-and-decisions.md), 제품 결정은 [컨퍼런스 우선 범위](../product/conference-first-web-2026-09.md)를 따른다.

## 기업 로고 메인 통합 검증 — 2026-09-24 10:26 KST

root1f9cbdc 통합 후 lint/typecheck/build 및 아바타 관련 데스크톱/모바일 브라우저4개 통과. 실제 이미지·Orca 표시를 확인했다. 담당 전체회귀 결과와 메인 부분회귀를 구분한다. localhost:3000 반영, 세션 retain 완료.
