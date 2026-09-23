# Dearby 웹

원하는 직무와 경험으로 교외 활동을 찾는 한국어 웹 프로토타입입니다. 상단 검색, 좌측 탐색, 가로 필터와 포스터 그리드, 회차별 상세를 제공합니다.

**모든 조직과 프로그램은 가상 샘플입니다.** 모집 상태는 2026-09-24 기준 fixture이며 실제 지원·API·로그인·수집·추천 학습 기능은 없습니다. 프로그램과 조직 스크랩은 현재 브라우저에만 저장됩니다.

## 실행

Node.js 22와 npm을 권장합니다.

```sh
npm ci
npm run dev -- --port 3000
```

http://localhost:3000 에서 탐색합니다. 프로덕션 실행은 `npm run build` 다음 `npm run start -- --port 3000`입니다. 외부 폰트나 이미지 다운로드 없이 로컬 포스터와 시스템 한국어 폰트를 사용합니다.

## 검증

```sh
npm run lint
npm run typecheck
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

Playwright는 별도 테스트 Chromium과 localhost:3210의 일회성 production 서버를 사용하고 종료 시 서버도 정리합니다. 데스크톱 1440×1000, 모바일 390×844에서 검색·필터·부분 조합·되돌리기·상세·스크랩·복원·저장 오류·탭 동기화와 axe 접근성 검사를 실행합니다. 스크린샷은 `test-results/`에 생성합니다. GitHub CI 설정은 추가했으며 원격 실행 여부는 별도입니다.

## 구조와 규칙

- `src/app`: App Router 경로와 공통 스타일.
- `src/features/catalog`: 샘플, 공고 단위 필터와 순위, URL 필터 상태, 탐색 UI.
- `src/features/saved`: 브라우저 저장 소유권, 검증, 오류 복구, 저장 버튼.
- `src/components`: 탐색 셸과 공통 아이콘.
- `public/posters`: 직접 구성한 로컬 SVG 포스터 12개.

방향 OR(모두 포함 선택 시 AND), 경험 OR, 두 유형 사이 AND를 **같은 현재 공고 안에서** 판정합니다. 경험 속성도 같은 활동에서 확인하며 지난 기수 근거는 검색에 합치지 않습니다. 결과와 제안의 개수는 고유 프로그램 수입니다. 필터와 최우선 경험은 URL에 보존되며 상세에서 뒤로 돌아오거나 새로고침해도 유지됩니다. 손상된 스크랩은 자동 덮어쓰지 않고 재시도·명시적 초기화로 복구합니다.

기획 정본은 [재기획 문서](docs/product/replanning-2026-09.md), 구현 선택과 한계는 [웹 인계](docs/context/web-implementation-and-handoff.md)에 기록합니다.
