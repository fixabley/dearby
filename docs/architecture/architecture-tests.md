# 아키텍처 검사 운영

Swift 아키텍처 검사는 `bash apps/ios/tests/run_architecture.sh`로 실행한다. iOS 앱을 실행하거나 Simulator를 준비할 필요 없이 macOS 호스트에서 소스를 검사한다. Harmonize가 처리하는 구조 규칙과 기존 FSD 참조 검사를 함께 실행하며 하나라도 위반하면 0이 아닌 종료 코드를 반환한다. 상세 규칙과 지원 범위는 `apps/ios/tests/ArchitectureTests/README.md`를 따른다.

## PR 검사

`.github/workflows/ios-architecture.yml`의 고정 job 이름은 `iOS architecture`다. 모든 PR과 main push에 실행하며, 필수 검사가 변경 경로 필터 때문에 영원히 pending으로 남지 않도록 paths 필터를 두지 않는다. read-only 토큰을 사용하고 checkout에 자격 증명을 남기지 않는다. 코드 실행에 pull_request_target을 사용하지 않는다.

Harmonize는 테스트 전용 의존성이다. 프로덕션 앱에 링크하거나 화면·상태 소유 구조를 변경하지 않는다. AST 기반 검사도 Swift 컴파일러의 전체 타입 해석을 대신하지 않으며 기존 lexical FSD 검사의 한계도 남는다. 규칙별 정상·위반 fixture와 빈 소스 집합 검증으로 검사 자체의 무력화를 탐지한다.

## 규칙 변경

새 규칙은 정상 코드와 실제 위반 코드를 함께 검증한다. 규칙을 제거할 때는 대체 검사의 동등한 범위가 입증되어야 한다. 파일 이동이나 이름 변경만으로 규칙을 우회하지 못하는지 확인하며, 단순 전달 함수·주석·문자열이 참조로 오인되지 않는지도 범위에 맞춰 검증한다.

GitHub의 필수 검사 설정은 workflow 파일과 별개다. 실제 저장소 설정 적용 결과는 작업 완료 인계에 기록하며 workflow 추가만으로 병합 차단이 켜졌다고 주장하지 않는다.
