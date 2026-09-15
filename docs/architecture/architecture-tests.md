# 아키텍처 검사 운영

Swift 아키텍처 검사는 `bash apps/ios/tests/run_architecture.sh`로 실행한다. iOS 앱을 실행하거나 Simulator를 준비할 필요 없이 macOS 호스트에서 소스를 검사한다. Harmonize가 처리하는 구조 규칙과 기존 FSD 참조 검사를 함께 실행하며 하나라도 위반하면 0이 아닌 종료 코드를 반환한다. 상세 규칙과 지원 범위는 `apps/ios/tests/ArchitectureTests/README.md`를 따른다.

## SwiftLint

일반 Swift 코드 스타일은 SwiftLint, 레이어·슬라이스·순수 UI·폴더명 경계는 Harmonize/SwiftSyntax 검사가 담당한다. 두 검사를 하나로 대체하지 않는다. SwiftLint는 앱 런타임 의존성이 아닌 개발 도구다.

로컬과 CI는 같은 고정 버전을 사용한다. `bash apps/ios/scripts/setup_swiftlint.sh`로 공식 portable 배포의 SHA256을 확인하여 checkout의 Git 제외 build 디렉터리에 설치한 뒤 `bash apps/ios/tests/run_swiftlint.sh`로 검사한다. 실행 스크립트는 도구 누락/버전 불일치/규칙 위반을 성공으로 처리하지 않는다. 구체적 규칙과 적용 범위는 앱 `.swiftlint.yml`과 README를 따른다.

[SwiftLint 공식 설치·설정 문서](https://github.com/realm/SwiftLint), [고정 릴리스 0.65.1](https://github.com/realm/SwiftLint/releases/tag/0.65.1).

## PR 검사

SwiftLint 설치·검사를 기존 필수 `iOS architecture` job에 추가한다. PR23부터 같은 필수 job에서 `run_standalone.sh`, `run_busy_calendar.sh`, `run_detail_presentations.sh`도 실행한다. 구조 검사 이후 즐겨찾기·영속 캐시·스냅샷 롤백·캘린더 동의/취소·정확한 겹침·일정 표시 회귀를 확인한다. 이 테스트들은 mock/임시 저장소를 사용하며 실제 기기 캘린더를 읽거나 저장하지 않는다. Simulator UI 검증은 별도이며 이 호스트 검사로 대체했다고 주장하지 않는다. 별도 `iOS simulator build` job은 macOS26/Xcode26.6에서 실제 앱을 서명 없이 Simulator 대상으로 빌드한다. 도구 경로는 [공식 runner 이미지 목록](https://github.com/actions/runner-images/blob/main/images/macos/macos-26-Readme.md#xcode)에 맞춰 고정했다.

`.github/workflows/ios-architecture.yml`의 고정 job 이름은 `iOS architecture`다. 모든 PR과 main push에 실행하며, 필수 검사가 변경 경로 필터 때문에 영원히 pending으로 남지 않도록 paths 필터를 두지 않는다. read-only 토큰과 SHA 고정 checkout v6(Node24)을 사용하고 checkout에 자격 증명을 남기지 않는다. 코드 실행에 pull_request_target을 사용하지 않는다.

Harmonize는 테스트 전용 의존성이다. 프로덕션 앱에 링크하거나 화면·상태 소유 구조를 변경하지 않는다. AST 기반 검사도 Swift 컴파일러의 전체 타입 해석을 대신하지 않으며 기존 lexical FSD 검사의 한계도 남는다. 규칙별 정상·위반 fixture와 빈 소스 집합 검증으로 검사 자체의 무력화를 탐지한다.

## 규칙 변경

새 규칙은 정상 코드와 실제 위반 코드를 함께 검증한다. 같은 정책의 구현을 바꿀 때는 대체 검사의 동등한 범위를 입증한다. 사용자 결정으로 정책 자체가 바뀌면 이전 금지와 새 허용의 이유를 기록하고 두 방향 fixture를 갱신한다. 파일 이동이나 이름 변경만으로 규칙을 우회하지 못하는지 확인하며, 단순 전달 함수·주석·문자열이 참조로 오인되지 않는지도 범위에 맞춰 검증한다.

GitHub의 필수 검사 설정은 workflow 파일과 별개다. 2026-09-15 main에 `iOS architecture` 필수 검사, 최신 base 요구(strict), 관리자 포함(enforce_admins)을 API로 적용·재조회했다. workflow 추가와 저장소 설정은 각각 확인한다. 워크플로·규칙 자체를 수정하는 PR도 검토해야 하며 검사 코드를 변경할 권한까지 차단하는 정책은 아니다.

## FSD 리팩터링 검증 (2026-09-16 진행)

이번 작업은 [FSD 목표 규칙](fsd-domain-rules-draft.md)을 실제 Harmonize 검사와 iOS 코드에 적용한다. 기존 PR18의 기본 검사 통과를 새 구조의 검증 결과로 재사용하지 않는다.

- 의존성: 정상 하위 참조/동일 슬라이스 참조와 위반 상향/형제 슬라이스 참조를 쌍으로 검사한다.
- 순수 UI: Entity의 자기 Model 참조는 허용하고 Repository·OS 부수 효과는 금지한다. 연결 Widget/Page UI의 자기 ViewModel 참조는 허용한다.
- 공개 진입점: Swift 접근 제어와 FSD 노출 목록을 구별한다. 내부 저장 레코드·codec 접근을 실제로 탐지하는지 검사한다.
- 민감도: 현재 checkout에 임시 위반을 넣어 전체 runner가 실패하는 것을 확인하고 제거 후 다시 통과시킨다. 임시 파일은 커밋하지 않는다.
- 사용자 동작: 공고 페이징·저장·목록·상세 연결, 캐시/즐겨찾기 복원, 일정·장소·캘린더 정책의 기존 테스트와 Simulator 검증을 변경 범위에 맞게 실행한다.

실패가 발견되면 해당 기능을 수정하고 관련 회귀를 다시 실행한다. 통과한 검사는 새 변경이나 미해결 우려가 있을 때 다시 넓힌다. 구현 완료 후 사용자 피드백에 따른 후속 변경에도 같은 절차를 사용한다. 실제 실행 결과·기기·로그·미검증 범위는 플랫폼 검증 보고서에 기록한다.
