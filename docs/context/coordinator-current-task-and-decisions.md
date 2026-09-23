# 현재 작업과 결정

2026-09-24 KST. 사용자가 기존 실행 코드를 제거하고 웹으로 재구현하도록 요청했다. 첫 범위는 최근 재기획 중심 프로그램 탐색·필터·상세·조직/프로그램 스크랩이며 인터페이스는 YouTube 같은 가독성 있는 탐색 구조다.

## 최신 사용자 의도

- 고등학생·대학생·취준생 등 대외활동 경험을 찾는 사람들을 대상으로 한다. 대학생으로 한정하지 않는다.
- 영상 서비스를 재현하기보다 썸네일→제목→주최 조직→참여 판단에 필요한 정보 순서를 활용한다. 가짜 재생·조회수·로그인은 만들지 않는다.
- 현재 영문 장식 포스터 비중을 줄이고 활동 내용이 드러나는 한국어 포스터와 모집 직무·지원 대상·마감·장소를 카드에 보완한다. 공고별 자격을 표시하며 자격 미충족으로 자동 제외하지 않는다.
- 프로그램 카드와 상세의 회차/직무별 공고, 조직/프로그램 스크랩 분리는 유지한다. 실제 데이터 수집·API·인증·추천 학습·프로필/자격 자동 판정은 이번 프로토타입 구현 범위 밖이다.

## 완료한 초기 웹 구현·통합

- root feat/web-rebuild, 코드 통합 HEAD f7826df. 기존 실행코드 제거 be10837, root 조율 문서 366e91d/e0e0669, 웹 담당 기능 커밋 8개를 88f7ee0..f7826df로 cherry-pick했다. 원격 push/PR/merge/배포는 수행하지 않았다.
- root Next.js 16.3.6·TypeScript, 가상 조직 4개/프로그램 12개/공고 25개, 명시적인 2026-09-24 기준 샘플과 브라우저 저장이다.
- 검색·같은 현재 공고 단위 방향/경험 필터·결과 없음 부분 조합/되돌리기·URL 상태 복원·상세·독립 스크랩·저장 오류 복구를 구현했다. 상세 근거/제약은 web-implementation-and-handoff.md.
- 메인 checkout에서 npm ci, lint, typecheck, 단위 테스트 11개, production build, Playwright 12개(1440/390 및 axe)가 통과했다. root test-results/에 실제 캡처가 있다. 최초 담당 검증과 별개로 실행했다.
- npm ci: 취약점 0. ESLint 9 지원 종료 경고가 있다. npm registry의 최신 eslint-plugin-react peer 범위가 아직 9까지여서 ESLint 10 강제 설치를 하지 않았다. npm 12의 install-script 차단 경고도 있으나 실제 lint/test/build는 통과했다.
- 초기 Task task_c5abe8d83ebe / Dispatch ctx_a529f998bad6는 succeeded 보고를 검토하고 release/ack했다. 해당 worker terminal은 종료·출력 보관, checkout/branch는 유지했다.

## 후속 반영·검증 완료

고등학생/대학생/취준생 대상·한국어 활동 카드·대표 현재 공고·저장 hydration 개선을 root 00d7c29/502b207/a56c9a1에 통합했다. 메인에서 lint/typecheck/unit12/build/production E2E26/dev Chromium·WebKit12 통과를 재확인했다. 실제 Orca 새 페이지에서도 저장 클릭·새로고침 복원·활성 버튼·console error 0을 확인했다. 기존 탭 console에는 수정 전 기록이 남아 있어 새 탭에서 구분 검증했다.

저장 문제는 소비자별 useSyncExternalStore 서버 snapshot으로 수정했고 새 dev 회귀를 CI에 연결했다. CI 원격 실행은 아직 하지 않았다. Next dev가 생성하는 next-env.d.ts는 추적 제외하고 파일 없는 상태 typegen을 검증했다. Next 자동 AGENTS 안내는 메인에서 보존한다.

## 컨퍼런스 반영·최종 검증 완료

2026-09-24 01:10 KST. Task task_3f3b5dfe71b8 / Dispatch ctx_e9759a9998a5가 succeeded로 완료되었다. 담당 1dae183을 root c380756에 통합했다. 프로그램 13개·공고 26개·조직 4개이며 참가 대상·주제·등록 마감·개최일·장소를 표시하고 선발형 지원과 참가등록을 구분한다.

메인에서 이번 통합 후 lint/typecheck/unit13/build/production E2E28/dev Chromium·WebKit14를 실제 실행해 모두 통과했다. Orca 실제 페이지에서 컨퍼런스 상세·활성 스크랩 버튼·등록 정보·console error 0을 확인했다. 1440/390 스크린샷을 검토했으며 모바일 참가 정보가 설명 아래에 있어 스크롤이 필요한 점은 후속 가독성 개선 후보다. Ponytail 최종 diff 검토에서는 추가 삭제 후보가 없었다.

웹 담당 terminal term_db69f423-a4bc-45ae-910d-ede154eab9d9는 사용자 요청으로 명시적 retain 후 delivery ack했다. 완료 세션을 닫지 않는다. 진행 중 Dispatch와 reclaimable worker는 없다. root CI의 dev 회귀 연결 및 shadcn 선택권·세션 유지 정책은 e9866e8에 기록했다. shadcn은 허용 사항으로 아직 설치하지 않았다.

다음: 사용자 화면 검토 및 후속 변경. 실제 데이터 수집·API·인증·지원/등록 실행·배포는 미구현이며 이번 원격 push/PR/merge는 수행하지 않았다.

## 로컬 실행과 보존

- root 개발서버: http://127.0.0.1:3000, Orca terminal term_bbab9abc-a0a6-4916-9404-fb7f193bacf8. 2026-09-24 01:10 KST 재시작 후 실제 페이지 접근 확인. 최종 웹 코드를 표시한다.
- root Orca 새 page d26f381c-df01-4020-9115-53457b1007ba가 위 URL을 표시한다(기존 page b99a9e23-82b0-4349-a854-b6bfa204ef82도 보존). 처음 CUA IAB는 미연결 실패했으나 Orca 내장 브라우저로 우회했다. 실제 YouTube 비로그인 헤더·접힌 메뉴만 확인했고 추천 그리드는 없어서 전체 대조하지 못했다.
- 기존 apps/scripts/shared/root Node 설정·의존성과 잔여 dearby-ios는 /Users/jominjun/Documents/dearby-backups/2026-09-24-before-web-rebuild/에 이동 보존했다. repository.bundle 검증, 사용자 Xcode 변경 binary diff·원본, 700개 소스/로컬 파일 해시 대조를 보존했다. main f1d9a63에도 추적 코드가 남아 있다.
- 기획·발표 자료·Git 이력은 유지한다. dearby-ir은 별도 저장소이며 변경하지 않았다. 과거 네이티브 규칙은 archive로 보존하고 새 웹에 적용하지 않는다.
- GitHub main에는 기존 iOS architecture 필수 체크가 남아 있다. 향후 PR 병합 시 실제 web CI에 맞는 보호규칙 변경을 별도 조율하며 가짜 iOS 체크로 우회하지 않는다.
