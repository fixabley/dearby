# 웹 구현

## 컨퍼런스 후속 완료 — 2026-09-24 01:08 KST

새 Dispatch `ctx_e9759a9998a5`(Task `task_3f3b5dfe71b8`, 세션 `term_db69f423-a4bc-45ae-910d-ede154eab9d9`)로 아래 대기 지시를 대체하여 컨퍼런스 샘플/한국어 포스터와 등록형 공고를 구현했다. 기존 탐색·공고 단위 필터·분리 스크랩을 유지하고 참가 대상/주제·분야/등록 마감/개최일/장소 및 등록형 문구를 표시한다.

자기 checkout 실제 검증: lint/typecheck/unit 13/build/production E2E 28/dev Chromium·WebKit 14 통과. 1440/390 카드·상세 이미지를 열어 확인하고 포스터 날짜 가림을 수정한 뒤 컨퍼런스 production 2개 재검증·재캡처, Ponytail 최종 Lean already. Ship. 완료. 3210/3211 listener 없음. 상세 근거는 [웹 인계](../context/web-implementation-and-handoff.md)의 최신 절에 있다.

메인 전달 검증은 이전 기능의 실제 Orca 저장·복원·console.error 0이며 이번 컨퍼런스 통합 검증과 구분한다. 이번 기능의 메인 통합/Orca 확인은 남는다. 완료 보고 후 사용자가 보도록 세션을 유지하고 다음 요청까지 대기한다. 아래는 직전 가시적 인계 기록이다.

## 현재 역할 — 2026-09-24 가시적 인계 후 대기

사용자가 완료 후에도 보이도록 유지한 웹 담당 세션이다. 다음 사용자/메인 요청을 기다리며 메인의 새 작업·변경 상태를 요약한다. 새 구현·빌드·commit·push·PR·merge·서버 시작은 하지 않고 다른 checkout은 수정하지 않는다. 종료된 Dispatch의 `worker_done`도 재전송하지 않는다. shadcn/ui는 허용만 되었으므로 설치하지 않는다.

메인 전달 기준: `81368c2`, `61f0035`, `36277a9`, `f3a0ba7`은 메인 `feat/web-rebuild`에 통합되었고 root lint/typecheck/unit 12/build/production E2E 26/dev Chromium·WebKit 12가 통과했다. 이는 이 세션의 재검증 결과가 아니다. root `http://127.0.0.1:3000` 개발서버가 실행 중이며 메인은 실제 Orca 브라우저 저장 버튼 최종 확인, 조율 문서 갱신, CI dev 회귀 연결 중이다. 아래 원본 구현 검증 기록과 상세 [웹 인계](../context/web-implementation-and-handoff.md)를 구분해 보존한다.

완료: 2026-09-24 00:53 KST. 담당 checkout `/Users/jominjun/Documents/dearby/dearby-web`, 기준 `be10837`, 담당 세션 `term_c0008d89-171d-424f-8ddc-122136963514`. 세션 ID는 검증 시점과 별개다.

Next/create-next-app stable 16.3.6을 npm registry에서 확인하고 공식 CLI scaffold를 root로 옮겼다. npm·App Router·TypeScript·시스템 한국어 폰트·로컬 SVG 포스터를 사용한다. 상세 실행과 인계는 [웹 인계](../context/web-implementation-and-handoff.md)에 기록한다.

구현 범위는 프로그램 탐색·검색·방향/경험 필터·유효 부분 조합·되돌리기·회차별 상세·조직/프로그램 스크랩이다. 데스크톱 56px 헤더, 224px 접이식 메뉴, 가로 칩과 16:9 포스터 그리드, 모바일 단일 열과 하단 탐색을 제공한다. 모든 데이터는 샘플이며 모집 상태 기준일은 2026-09-24다.

필터는 같은 현재 공고 안에서 방향 OR(모두 포함 시 AND)·경험 OR·유형간 AND를 판정한다. 속성은 같은 활동에서 결합한다. 부모·자식 중복은 가산하지 않되 현직자/동료 같은 별개 묶음은 구분한다. OR 그룹 일부 제거는 결과를 늘리지 못하므로 부분 조합 생성에서 제외하고 AND 방향만 부분집합을 만든다. 최다 선택 조건 테스트의 계산은 이 환경에서 약 3.4ms였다. 필터는 URL에 보존되어 상세에서 뒤로 돌아오기와 새로고침을 지원한다.

저장 Context의 안정된 객체를 각 소비자가 useSyncExternalStore로 구독한다. 서버 스냅샷은 늦은 Suspense hydration에도 동일하게 유지하고 구독 후 저장 값을 복원한다. 손상된 원본은 자동 덮어쓰기하지 않고 재시도·명시적 초기화로 복구한다. 읽기 차단과 쓰기 실패를 구별하고 저장 실패는 현재 화면에만 반영됨을 알린다. 프로그램/조직 ID는 별도이며 다른 탭의 clear 이벤트도 반영한다. 저장 버튼은 표시 컴포넌트로 분리했다.

최종 검증: lint, typecheck, production build, 단위 테스트 11개, Playwright 12개 통과. Playwright는 개인 브라우저와 분리한 Chromium으로 1440×1000 / 390×844에서 실행했고 목록·모달·상세 axe WCAG AA 검사도 포함했다. 최종 이미지 decode 후 스크린샷을 확인했다. 3210 테스트 서버는 종료되어 listener가 없다. 원격 CI·push·PR·merge·배포는 실행하지 않았다.

프로토타입 선택: 방향 5개와 경험 묶음 9개, 명시적 현재 회차, 유지 조건 수·결과 수 순 최대 4개 제안, 최우선 경험 다음 중복 제거한 경험 묶음 수를 정렬에 사용한다. 전체 어휘·최종 점수·프로필·추천 학습·자격 자동 판정·수집/인증/API는 구현하지 않았다. 과거 경험은 상세에만 표시한다.

## 2026-09-24 후속 검증

고등학생·대학생·취준생을 공고별 audience로 분리했고 한국어 활동 요약 포스터와 대표 공고의 직무·대상·마감·장소를 카드에 표시한다. 다른 직무 모집 안내는 대표 공고와 별도로 유지한다. 개발모드 최초 직접 진입 hydration 문제를 수정 전 재현하고 useSyncExternalStore 소비자별 serverSnapshot 계약으로 수정했다. lint/typecheck/unit 12/build/production E2E 26/dev Chromium+WebKit 12 통과, 1440/390 이미지 실제 확인과 Ponytail review 완료; 자세한 근거와 세션은 웹 인계의 최신 절에 있다. 메인의 실제 Orca WKWebView 직접 진입 최종 확인은 남는다.
