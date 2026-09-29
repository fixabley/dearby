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
- 배포 성공: Actions run 36521245623, https://fixabley.github.io/dearby/ 및 공개 slides.md 원본 바이트 일치 확인. Pages 환경은 기존 main과 발표 브랜치만 배포 허용.
- 최초 배포의 Actions Node20 폐기 경고를 확인해 각 공식 저장소의 최신 메이저(checkout7/configure-pages6/upload-pages-artifact5/deploy-pages5)로 갱신.

- 공개 사이트 검증: Playwright로 22장·이미지·노트·확대·모바일 검사를 다시 실행해 오류/경계 넘침 없음.

## 용어 가독성 수정

- 사용자 요청에 따라 발표 본문·노트의 영문 약어를 풀어 쓰고, 14개 슬라이드에 학술 용어 각주를 추가했다. 제품명·코드 문법·출처 주소는 유지했다.
- 각주도 slides.md에서 편집하며 기본 발표 화면 하단에 항상 표시한다. 원본 시안 안의 약어는 이미지 변경 없이 각주로 설명한다.
- 검증: 22장 렌더링, 본문·각주 겹침 없음, 이미지·노트·확대 동작, 모바일 가로 넘침 없음. 주요 변경 슬라이드 시각 확인. ponytail-review: 각주 유무에 따른 클래스 한 개와 CSS만 추가, 별도 파서 없음.

## 수집 담당 완료 보고 반영

- 사용자 전달 완료 보고와 별도 checkout 인계 문서, 변경 제안 58번의 최신 8302f2b 및 관리자·서버 자동 검사 성공을 읽기 확인했다.
- 슬라이드 5~6을 로컬 예약 실행·당일 28개 작업 등록·실검색 초안 저장 확인으로 갱신했다. 등록과 전체 수집 성공, 별도 브랜치 완료와 기본 브랜치 통합, 최초 실행과 장기 운영은 구분했다.
- 개선 순서 1번을 실패 출처 보완·장기 운영 검증으로 갱신했다. 수집 브랜치 코드는 root에 통합하지 않았다.
- 22장 렌더링·이미지·노트·모바일 검사 통과, 수정된 수집 화면 시각 확인. 기존 Markdown 구조만 수정해 추가 구현 복잡성 없음.
