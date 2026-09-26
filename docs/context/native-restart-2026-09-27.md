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
