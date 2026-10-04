# 네이티브 재착수 — Seed 확정 — 2026-09-27 KST

## 사용자 요청과 진행 상태

- 최신 대화의 iOS·Android 앱 개발, 최신 스택 검토, 이전 개발지침 적용, Orca 하위 세션·GitHub 이슈/PR 관리 요청.
- 웹 제거 승인에 따라 Next.js 실행 코드·의존성·웹 CI를 제거하고 조사 데이터·이미지 출처는 shared에 보존했다.
- 대화 기반 요구사항→테스트→시뮬레이터/에뮬레이터 캡처→수정의 검증 루프 요청.
- Cycle 4 Seed와 완료 조건을 확정하고 3개 플랫폼 담당 작업을 배정했다.

## 실제 실행한 준비

- 깨끗한 feat/web-rebuild(0560e66)에서 feat/native-rebuild 브랜치 생성.
- gh 활성 계정을 기존 로그인된 fixabley로 전환해 fixabley/dearby 조회 성공. 일반 Git credential은 다른 계정을 사용해 실패했고, 명령 한정 gh auth git-credential을 지정한 fetch 성공. 토큰을 파일에 기록하지 않았다.
- 착수 시 origin/main 대비 36 커밋 앞이었다. 현재 Git 상태는 재개 시 다시 확인한다. 신규 작업 이슈 #37/#38/#39 생성.
- Orca Run run_3f92ae81b333, coordinator term_1cbabda3-9f12-4f38-9655-ef8a0a3548fd. iOS ctx_741102b1b057 / Android ctx_8fdb85641ada / API ctx_ce111f22ff1f 배정 및 입력 수락 확인. 런타임 연결은 재개 시 재확인.
- 기존 웹 retained 세션은 보존. 신규 앱/API 구현은 각 하위 worktree에서 진행 중.
- Xcode 27.0 build 27A266a / Swift 6.4 설치 확인. Android Studio와 SDK 경로 존재 확인. 정확한 Android 버전 조합·최소 지원 OS 미확정.

## 심포지움 처리 주의

.symposium/scratch/socrates.md의 top-level Seed는 확정 Cycle 4다. 과거 Cycle 3는 보존 이력이다. 최신 대화의 명시적 결정을 재질문하지 않는다. 실제 인증·서버 교환·푸시·외부 폼 자동입력과 데모를 구분한다.

## 최신 결정

사용자가 목표·완료 조건에 “맞아”로 동의하여 Cycle 4 Seed를 확정했다. 개발 중 차단 사항은 GitHub 이슈로 기록하고 의존하지 않는 작업을 계속한다. 질문 대기를 전체 개발 중단으로 전파하지 않는다. 공통 계약 shared/contracts/native-v1.md를 작성하고 플랫폼별 구현을 배정했다. 완료 보고를 검토·통합하고 실제 검증 결과와 남은 범위를 PR에 기록한다.

확인된 차단 이슈: [#36 if(kakao) 실제 폼 검증](https://github.com/fixabley/dearby/issues/36). APPLY-01 실환경 검증에만 영향을 주며 명함·프로필·QR·기기 저장 및 APPLY-02 작업을 막지 않는다. Seed·개발 지침·정본 갱신 후 `git diff --check` 통과. 앱 빌드·기능 테스트는 이번 문서 변경에서 실행하지 않았다.

통합 초안: https://github.com/fixabley/dearby/pull/40 (Draft, main 미병합). 남은 전체 서비스 요구는 #41에서 추적한다. 과거 조사 스냅샷 이관 후 28개 프로그램·30개 공고·26개 조직의 참조·ID·이미지 경로 확인.

## 2026-09-27 06:10 KST 진행 체크포인트

API worker ctx_ce111f22ff1f의 성공 보고를 검토하고 retain했다. 8051170/00e7751/25d2311/b8ace11/88f8895를 메인 작업 checkout에 cherry-pick했고 통합 후 Node24의 ci·HTTP10·typecheck·lint·build가 통과했다. iOS/Android 두 Dispatch는 아직 active이며 완료로 간주하지 않는다. iOS는 SwiftLint/Harmonize 복원 및401복구·QR공통형식 검증, Android는 구조검사·에뮬레이터 검사 후 실제 API 연결을 수행 중이다.

로컬 통합 harness scripts/native-integration.mjs를 실행 중이다. 초기 origin http://127.0.0.1:53634 (Android emulator 10.0.2.2 동일 port). 서버 실행 session 7829. 테스트 메일은 임시 폴더의 private inbox이며 실제 이메일이 아니다. 토큰·코드를 로그/커밋하지 않는다. 두 앱이 통합 검증을 마칠 때까지 유지하고 이후 정확한 해당 서버만 종료·임시파일 정리 확인한다. 재개 시 살아 있다고 가정하지 않고 localhost 상태를 확인한다.

원격 PR40은 Draft, main 미병합. 통합된 최신 커밋의 push 및 native CI 실행은 두 앱 통합 뒤 수행한다. .github/workflows/native.yml은 작성·YAML 구문 검사 완료이나 원격 실행 미완료다. #42 운영 SMTP/HTTPS, #43 운영 공유 링크, #41 전체 서비스 후속, #36 외부 폼을 추적한다.

## 2026-09-27 06:37 KST 통합·인계

세 Dispatch 모두 정확한 Task/Dispatch의 succeeded 보고를 받아 통합했으며 사용자 요청대로 retained 처리했다. reclaimable 조회0, retained3이다. Android 최종 6ccf46f, iOS 최종 6f090fa까지 모든 소유 커밋을 순서대로 cherry-pick했다. main 병합/운영 배포는 하지 않았다.

조율 checkout: API HTTP10/typecheck/lint/build, iOS 단위17+guest UI1/구조16/SwiftLint/Simulator 테스트 빌드, Android JVM18/구조32파일+24자체회귀/Lint/Debug·unsigned Release 빌드 통과. 최종 QR 타일 변경 후 iOS UI/Android 빌드·Lint 재확인. 담당 checkout의 인증 UI·에뮬레이터11·양 플랫폼 실제 HTTP 교환 증거는 각 플랫폼 검증 문서에 있다. A/B/A 전송 재시도 덮어쓰기 수정·회귀도 통합했다. 밝은 액션 색 대비와 전체 큰 글자/VoiceOver/TalkBack은 #14 후속이다.

격리 통합 서버 PID96761의 명령을 확인한 뒤 SIGTERM으로 종료했고 임시 DB/메일 디렉터리 삭제를 확인했다. 조율 전용 Simulator 4712C750-BF32-42A8-8FBA-9AD2BA339EFC는 테스트 종료 뒤 Shutdown 상태다. 다른 기기/세션을 종료하지 않았다. 2026-10-04 정리에서 이 SQLite 기반 모바일 연동 스크립트는 제거했다. 현재 API는 Prisma PostgreSQL, 모바일은 오프라인 프로토타입이므로 당시 재현 방법을 적용하지 않는다. 당시 스크립트는 Git 커밋 `a385835630b4cf2e193a8f02d39a5cd6a98796b1`에 보존되어 있다.

PR40은 구현 범위/증거/남은 요구 중심으로 갱신했다. 첫 원격 CI run 36273266684 (b1dd81f): API 성공, iOS/Android 진행 중인 시점에 확인했다. 최종 원격 상태는 GitHub에서 재확인한다. #37/#38/#39 댓글에 조율 검증을 기록했다. 실제 운영 조건 #42/#43/#44, 전체 제품 #41, if(kakao) #36은 미완료다.
