# 캡스톤 발표 자료 — 2026-09-29

- 사용자 요청: 자료구조 선택 → 수집·정제 → 검색 → IVF/HNSW/PQ 참고 글 비교·개선 → selected current flow → 겹침 A → 명함 화면. 내용 편집 가능한 MD 및 GitHub Pages 배포.
- 루트는 발표 자료 담당. 수집 구현은 `catalog-subscription-collector` Orca 하위 세션에 완전 인계했으며 이 작업에서 수정하지 않음.
- 발표 브랜치: `feat/capstone-presentation` (424aee4 인계 스냅샷 기반). 기존 `feat/discovery-admin` 유지.
- 자료: `docs/presentations/capstone/slides.md`, 22장. README에 편집·로컬 실행·발표 조작 안내.
- 사용자가 요청한 selected 원본 중 17개 화면 복사. 실제 앱 실행 캡처가 아닌 승인 시안임을 명시. 캘린더 A는 추후 승인 기록에 맞춰 포함. 수집은 구현 중, 벡터 검색은 향후 계획으로 구분.
- 검증: Playwright Chromium에서 22장 렌더링, 이미지 로딩, 본문 경계, JS 오류, 노트 열기/닫기, 원본 확대, 390px 모바일 가로 넘침 확인. 22장 렌더 결과 시각 검토. QA 산출물은 /tmp/dearby-deck-qa (배포 제외).
- ponytail-review: 발표 엔진은 Reveal.js 사용, 정적 Markdown·CSS·짧은 레이아웃 스크립트만 추가. 별도 앱 프레임워크나 빌드 파이프라인 없음. Lean already. Ship.
- Pages: `.github/workflows/presentation-pages.yml`에서 발표 폴더만 업로드. GitHub API로 workflow Pages 설정. 공개 범위/저장소 가시성 설정은 변경하지 않음.
- 배포 예상 URL: https://fixabley.github.io/dearby/ (배포 결과 확인 후 아래 기록).
