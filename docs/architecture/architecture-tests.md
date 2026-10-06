# 아키텍처·스타일 검사 운영

2026-10-06 사용자 요청으로 iOS ArchitectureTests(Harmonize/SwiftSyntax FSD 경계 검사)를 포함한 자동 테스트를 모두 지웠다. 사용자와 하나씩 다시 만든다. 이전 규칙과 검증 범위는 [삭제 전 문서](https://github.com/fixabley/dearby/blob/7295aa5/docs/architecture/architecture-tests.md)와 [삭제 전 ArchitectureTests](https://github.com/fixabley/dearby/tree/7295aa5/apps/ios/tests/ArchitectureTests)에 남아 있다.

## 지금 남은 검사

- SwiftLint: `bash apps/ios/scripts/setup_swiftlint.sh`로 고정 버전(0.65.1, SHA256 확인)을 설치하고 `bash apps/ios/scripts/run_swiftlint.sh`로 `Sources`를 검사한다. 규칙은 `apps/ios/.swiftlint.yml`이다.
- Android FSD: `python3 apps/android/scripts/check-fsd.py`.
- CI(`.github/workflows/native.yml`): API typecheck·lint·build, 수집 워커 구문 검사, Android lint·assemble, iOS SwiftLint·앱 빌드(`iOS build`), Supabase 마이그레이션 재생(`Supabase migrations`). 필수 검사는 `iOS build`, `Supabase migrations`다. 웹·어드민은 CI job이 없어 로컬에서 lint·typecheck·build를 돌린다.
- `apps/ios/architecture/*.json`(공개 API·순수 UI 목록)은 삭제한 ArchitectureTests가 읽던 규칙 목록이다. 지금은 아무 검사도 읽지 않으며, 구조 검사를 다시 만들 때 쓸지 정한다.
