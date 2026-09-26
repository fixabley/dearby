# 현재 작업과 결정

2026-09-24 KST. **발견 홈·활성 모집공고 노출 통합과 메인 검증 완료.** 사용자 검토 대기.

## 최신 통합

웹 사용자 직접 승인 후속 `e7767bc`를 root `dc688d2`로 충돌 없이 통합했다. App Store형 좌우 스크롤 발견 홈, 전체 그리드(view=all), 모집중·예정 목록(view=available)을 제공한다. 기존 모집중 메뉴는 open만 유지한다. 28프로그램·30공고·26조직 데이터는 보존하며 화면 공고는 open/scheduled만 표시한다. 종료·미확인 프로그램도 계속 검색·스크랩 가능하다. 분야/경험 검색은 대표 회차 근거이며 현재 모집 조건과 동일하다는 의미가 아니다.

## 메인에서 실행한 검증

lint/typecheck/unit15/build/prodE2E42/dev Chromium12+WebKit12 모두 첫 실행 통과. worker의 과거 실패 수정 기록과 별개 결과다. root3000 Orca에서 발견 홈·상단5개·다음 버튼 실제 scrollLeft698.5·모두보기→available5개 전환 확인. 모바일 전체 캡처와 Orca 캡처를 실제 검토했다. console error 없음, 카카오 이미지 LCP eager 권고warning은 재현된다. 물리 터치·트랙패드 미검증.

Ponytail: Lean already. Ship. 새 삭제 후보 없음. 모바일 긴 섹션 제목과 긴 프로그램명의 어중간한 줄바꿈은 가독성 개선 후보이며 기능 차단은 아니다. 제목 단축·단어 단위 줄바꿈을 추천하되 담당 구현 범위를 이번 통합에서 임의 변경하지 않았다. LCP 권고와 함께 사용자·웹 세션에 알린다.

원격 push/PR/merge/배포 미수행. 이번은 사용자 직접 후속으로 새 Dispatch/lifecycle 재전송 없음. 웹 세션에 통합 완료를 전달하고 retain 유지한다. 상세 직전 결과는 [보관본](archive/2026-09-24-before-discovery-integration/coordinator-current-task-and-decisions.md)에 있다.

## 목적·한계·다음 행동

고등학생·대학생·취준생의 프로그램 탐색·필터·상세·조직/프로그램 스크랩을 YouTube처럼 읽기 쉬운 웹으로 제공한다. 기업 주최기관 로고와 Spring Camp KSUG 로고는 통합 완료다. 공식 출처 수동 확인 스냅샷이며 자동 갱신·API·인증·신청/결제·개인화·기기간 동기화는 미구현이다. 후보 조사 목록은 전체 행사를 빠짐없이 수록했다는 의미가 아니다.

사용자 화면 검토를 기다린다. 추가 자료 검증·정보 갱신 정책·개인화/서버는 후속 범위다. GitHub main의 과거 iOS 필수 체크는 향후 웹 PR 전에 조율해야 하며 가짜 체크로 우회하지 않는다.

Root는 `feat/web-rebuild`, 웹 담당은 별도 checkout·세션을 유지한다. 실제 핸들은 [Git·Orca 운영](orca-sessions-and-worktrees.md)을 따른다. child 자동 AGENTS diff는 미커밋 보존하며 건드리지 않는다.

이전 실행코드는 `/Users/jominjun/Documents/dearby-backups/2026-09-24-before-web-rebuild/`와 main `f1d9a63` 이력에 보존했다. dearby-ir은 범위 밖이다. 상세 통합 이력은 [이번 정리 전 보관본](archive/2026-09-24-before-clubs-integration/coordinator-current-task-and-decisions.md)에 있다.
