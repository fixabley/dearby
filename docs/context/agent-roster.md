# 에이전트 운영

2026-10-06 사용자 승인. UI·유저플로우·수집기·어드민을 각각 Orca worktree 상주 세션으로 운영한다. 메인 세션은 공통 결정·`AGENTS.md`·`shared/contracts`·root 설정·통합을 맡는다. 진행 상태는 이 문서가 아니라 각 인계 문서와 [현재 작업](coordinator-current-task-and-decisions.md)에 남긴다.

## 공통 규칙

- 담당 경로 밖의 파일이 필요하면 직접 고치지 말고 해당 담당이나 메인에 요청한다. 같은 화면을 UI와 플로우가 함께 바꿀 때는 UI 변경을 먼저 통합한다.
- 각 담당은 자기 worktree에서 작업하고, 기능 단위 PR로 main에 통합한 뒤 새 main에서 다음 작업을 시작한다.
- 일관성 기준표는 [디자인 적용표](../design/mobile-prototype-reference-map.md)다. UI 담당은 시각 열을, 플로우 담당은 화면 이동 절을 갱신한다. 한 플랫폼에만 반영한 변경은 다른 플랫폼을 `미반영`으로 표시한다. 완료로 숨기지 않는다.

## 웹 일관성 범위

웹은 보존 중인 서비스다. 세 플랫폼에 공통인 요소만 맞춘다.

- 맞춘다: 색·글꼴·간격·모서리·아이콘 같은 디자인 토큰, 활동 카드·배지·버튼·빈 화면 같은 공통 컴포넌트의 모양, 탐색 → 상세 → 신청 링크 흐름.
- 그대로 둔다: 웹의 스크랩·게스트 명함·실제 데이터 연결, 모바일의 다섯 탭·명함·QR 예시 흐름. 모바일에 서버 호출을 넣지 않고, 웹을 예시 데이터로 바꾸지 않는다.

## UI 담당

- 소유: `apps/ios/Sources/shared/ui`, `apps/ios/Sources/widgets`, `apps/android/.../nativeapp/shared/ui`, `apps/android/.../nativeapp/widgets`, `apps/web/src/shared/ui`, `apps/web/src/widgets`, 웹 `globals.css`의 토큰, `shared/assets/prototype`.
- 첫 작업: 웹 `apps/web/src/components`에서 상태 없는 표시 컴포넌트를 `shared/ui`·`widgets`로 분리한다. 그 전까지 플로우 담당은 웹 화면을 수정하지 않는다. 이어서 세 플랫폼의 토큰 값을 한 표로 비교해 기준표에 남긴다.
- 컴포넌트를 바꾸면 세 플랫폼에 함께 반영하거나 남은 플랫폼을 `미반영`으로 표시한다. 1440·390 웹, iOS·Android 시뮬레이터 캡처로 확인한다.

## 유저플로우 담당

- 소유: iOS·Android의 `app`·`pages`·`features`·`entities`, 웹의 `src/app`·`src/lib`와 상태를 가진 화면 컴포넌트.
- 탐색 → 상세 → 신청 링크의 단계·뒤로 가기·필터 유지 방식을 세 플랫폼에서 맞춘다. 모바일 예시 상태는 메모리에만 두고, 웹의 URL·저장 동작은 유지한다.
- 시각 변경이 필요하면 UI 담당에 요청한다. 화면 흐름 테스트(iOS UI 테스트, Android 계측 테스트, 웹 Playwright)로 확인한다.

## 수집기 담당

- 소유: `apps/catalog-worker`, [수집 워커 인계](catalog-subscription-worker-handoff.md). `supabase/migrations`의 수집 관련 변경은 담당이 작성하고 메인이 검토해 통합한다.
- 금지: 환경 파일·LaunchAgent·Supabase Cron·`~/.dearby-deploy` 배포 사본 변경, 사용자 승인 없는 실제 구독 실행. 실행 중인 워커는 배포 사본이므로 main 변경은 재배포 승인 전까지 운영에 반영되지 않는다.

## 어드민 담당

- 소유: `apps/admin`. 수집 화면 `collection.tsx`도 어드민 담당이 고친다. 수집 데이터 구조가 바뀌면 수집기 담당·메인과 맞춘다. 조직·프로그램·활동 카탈로그 테이블의 `supabase/migrations` 변경은 어드민 담당이 작성하고 메인이 검토해 통합한다.
- 금지: 환경 파일·배포 설정·운영 데이터 변경. 배포는 사용자 승인 후 진행한다. 실제 운영 데이터로 쓰기 테스트를 하지 않는다.

## 담당이 없는 영역

`apps/dearby-api`와 `shared/contracts`는 메인이 맡는다. 서버 작업이 늘면 별도 담당을 추가한다.
