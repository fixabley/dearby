# 현재 작업과 결정

2026-09-24 KST. **컨퍼런스·연합동아리 웹 통합과 메인 검증 완료.** 사용자 검토 대기.

## 최신 통합

웹 하위 세션의 사용자 직접 후속 승인 커밋 `6a1f74f`를 root `2433301`로 충돌 없이 cherry-pick했다. 이전 `6fbf5ce`는 이미 root `253daa0`에 통합되어 있다. 현재 28프로그램·30공고·26조직이다. SOPT·피로그래밍·COTATO·YAPP·디프만·Mash-Up 추가, 전체/컨퍼런스/연합동아리 유형 필터와 URL 복원·0건 대안, 지원조건·선발 과정·회비·활동 일정 표시를 포함한다.

디프만19기 10/2–8은 모집 예정, SOPT39기는 확인된 OB 서버 전형만 표시한다. 피로그래밍 연도·COTATO 기수·Mash-Up 비정상 날짜는 추정하지 않는다. 최신 제품 정본은 [웹 범위](../product/conference-first-web-2026-09.md), 조사 출처는 웹 인계와 public 이미지 SOURCES 문서에 있다.

## 메인에서 실행한 검증

- lint/typecheck/unit14/production build 통과.
- production E2E 최초 34통과·4실패(이미지 decode 대기 시간 초과). 코드 변경 없이 실패4개만 재실행하여 모두 통과. 이미지 요청 일부가 대기 중이었으나 원인은 확정하지 않았다. 첫 실행 전체 통과로 기록하지 않는다.
- dev Chromium10+WebKit10 모두 통과.
- root localhost3000 Orca 실제 연합동아리 필터·6개 결과·상태 표시·공식 이미지 캡처 확인, console 메시지 없음. 모바일 상세 캡처도 검토했다.
- diff/Ponytail 검토에서 추가 삭제 후보 없음. 카드에 활동·회비·선발을 모두 표시하여 길어진 점은 사용자에게 알렸으며 향후 선발 요약을 상세로 옮기는 대안이 있다. 이번에 임의 축소하지 않았다.

사용자 직접 후속이므로 새 Dispatch나 완료 lifecycle 재전송 없음. 유지 중인 웹 세션에 통합 결과를 직접 전달한다. 원격 push/PR/merge/배포는 이번 범위에서 수행하지 않았다.

## 목적·한계·다음 행동

고등학생·대학생·취준생의 프로그램 탐색·필터·상세·조직/프로그램 스크랩을 YouTube처럼 읽기 쉬운 웹으로 제공한다. 기업 주최기관 로고와 Spring Camp KSUG 로고는 통합 완료다. 공식 출처 수동 확인 스냅샷이며 자동 갱신·API·인증·신청/결제·개인화·기기간 동기화는 미구현이다. 후보 조사 목록은 전체 행사를 빠짐없이 수록했다는 의미가 아니다.

사용자 화면 검토를 기다린다. 추가 자료 검증·정보 갱신 정책·개인화/서버는 후속 범위다. GitHub main의 과거 iOS 필수 체크는 향후 웹 PR 전에 조율해야 하며 가짜 체크로 우회하지 않는다.

Root는 `feat/web-rebuild`, 웹 담당은 별도 checkout·세션을 유지한다. 실제 핸들은 [Git·Orca 운영](orca-sessions-and-worktrees.md)을 따른다. child 자동 AGENTS diff는 미커밋 보존하며 건드리지 않는다.

이전 실행코드는 `/Users/jominjun/Documents/dearby-backups/2026-09-24-before-web-rebuild/`와 main `f1d9a63` 이력에 보존했다. dearby-ir은 범위 밖이다. 상세 통합 이력은 [이번 정리 전 보관본](archive/2026-09-24-before-clubs-integration/coordinator-current-task-and-decisions.md)에 있다.
