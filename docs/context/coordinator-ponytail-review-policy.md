# 조율 — Ponytail 변경 후 리뷰 설정

2026-09-20 완료. 사용자는 Ponytail을 상시 구현 지침 대신 변경 후 과도한 설계를 검토하는 용도로 적용하도록 승인했다. 정본 규칙은 루트 AGENTS.md의 “Ponytail 변경 후 리뷰”다.

## 설치와 범위

- 원본: https://github.com/DietrichGebert/ponytail
- 고정 원본 commit: `e3ba2aa6f1e6f0bc4d69eb09c9f0d0a93af56156`
- 설치 대상: `skills/ponytail-review`만. 로컬 `/Users/jominjun/.codex/skills/ponytail-review/SKILL.md`.
- skill-installer의 `install-skill-from-github.py --repo DietrichGebert/ponytail --ref e3ba2aa6f1e6f0bc4d69eb09c9f0d0a93af56156 --path skills/ponytail-review`로 설치했다. upstream 스킬은 변경하지 않았다.
- 전체 plugin, 일반 ponytail 스킬, hooks, lite/full/ultra 상시 모드를 설치·활성화하지 않았다. 이번 작업 전 두 로컬 skill root에서 ponytail 이름의 설치 디렉터리가 없음을 확인했다. 다른 도구의 전역 설정까지 전수 검사했다는 뜻은 아니다.
- 설치 스킬은 다음 턴부터 사용 가능하다. 예: “이번 diff를 ponytail-review로 검토해줘.” 자동 주입 hook은 없으며, 프로젝트 AGENTS.md가 구현 후 리뷰 절차를 지정한다.

## 기존 규칙 재평가 추가 승인

사용자가 기존 제안을 무조건 고정하지 않고 근거를 다시 평가하는 방식을 승인했다. 기능/저장/수명 계약과 설계 수단을 구분하며 이번에는 현재 iOS 설계 규칙까지 검토 범위를 넓힌다. [재평가 보고서](../../architecture/reviews/2026-09-20-design-rules-reassessment.md)가 후보와 필요한 검증을 기록한다. 실행 검사 완화나 앱 리팩터링이 완료됐다는 뜻은 아니다. 평상시 변경 diff 리뷰 원칙과 구별한다.

## 적용 판단

FSD, 상태 원본/수명, 독립 Repository, cache-aside, 필수 회귀·구조검사·SwiftLint를 보존한다. upstream의 단일 구현체 추상화 제거 예제나 줄 수 절감 점수는 무조건적 삭제 기준으로 삼지 않는다. 후보만 보고하며 변경 적용은 기존 승인 범위와 담당 worktree 원칙을 따른다.

이번에는 공통 협업 규칙과 스킬만 설정했다. 앱 코드 수정, 설치 당시 기존 diff 리뷰와 앱 빌드/회귀 테스트, 성능 측정은 하지 않았다. 후속 설계 재평가의 완료 결과는 위 링크를 따른다. 기존 Xcode project/scheme 및 ArchitectureTests/.swiftpm 로컬 변경은 보존한다. 기존 플랫폼 세션/worktree는 다시 배정하거나 수정하지 않았다. AGENTS.md 변경은 다른 checkout에 자동 반영되지 않으므로 다음 플랫폼 배정 때 전달/통합해야 한다.

## 검증·다음 행동

이번 실행에서 설치 파일의 고정 upstream 원본 byte 일치, 일반 ponytail 스킬 미설치, 프로젝트 규칙/인계 파일 존재, git diff --check 통과를 확인했다. 설치/문서만 변경하여 앱 테스트는 필요하지 않다. 다음 실제 코드 변경을 마친 뒤 해당 diff에 대해 기존 정확성 리뷰와 함께 Ponytail 복잡성 리뷰를 수행한다. 과거 iOS 구현/CI 결과는 이번 설정 검증으로 간주하지 않는다.
