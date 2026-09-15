# 현재 작업과 결정

2026-09-16 01:29 KST: 사용자의 병합 요청에 따라 **PR19–25를 main에 통합하고 병합된 코드에 SwiftLint를 실행했다**. main merge commit은 `fb1214da96647efb29f12d23089f643201c78a5c`다. 추가 코드 수정은 필요하지 않았다.

## 완료와 검증

- 모든 PR의 검사 성공·충돌 없음 및 최종 head에 각 기능 head 포함을 확인했다. 누적 PR 대상을 main으로 정리한 뒤 [PR25](https://github.com/fixabley/dearby/pull/25)를 merge commit으로 병합했다. PR19–24도 동일 커밋의 main 포함으로 GitHub MERGED 상태를 확인했다. 기능별 커밋과 SHA를 보존했으며 보호 규칙 우회·강제 push·squash/rebase는 하지 않았다.
- 통합된 main의 파일 트리는 검증 완료 head `e7e682b`와 동일하다. 해당 head의 [CI run34994511738](https://github.com/fixabley/dearby/actions/runs/34994511738)는 SwiftLint 설치/실패 fixture/strict lint, Harmonize9 tests, 전체 standalone/busy/detail 및 Simulator 빌드 모두 성공했다. 병합 후 CI와 이전 검증을 혼동하지 않는다.
- **이번 병합 후 실행**: `bash apps/ios/tests/run_swiftlint.sh` exit0, 앱109+테스트18 = 127파일, 위반0/심각 위반0. 로그 `/tmp/dearby-main-swiftlint.log`. 수정할 위반이 없어서 자동수정에 따른 제품 변경은 없다.
- 후속 인계 문서 변경은 별도 문서 커밋으로 기록한다. 앱 코드를 추가로 변경하지 않는다. 최종 원격 브랜치/CI는 GitHub와 Git에서 재확인한다.

## 유지할 설계와 역할

[FSD 기준](../architecture/fsd-domain-rules-draft.md), [iOS 실제 구조](../../apps/ios/ARCHITECTURE.md), [SwiftLint 규칙](../../apps/ios/docs/SWIFTLINT.md)이 정본이다. 소스 폴더는 lowerCamelCase, 세그먼트는 ui/model/api이며 타입·파일명과 Xcode 규격 이름은 유지한다. SwiftLint의 명시61규칙과 FSD Harmonize 검사는 별개다.

도메인 NoticeModel/OrganizationModel과 화면 ViewModel/State, 단일 즐겨찾기 소유자, 공고·조직의 독립 L1→SwiftData→mock 캐시, 캘린더 동의/취소·원문 URL 계약을 유지한다. 기존 UI·전체 회귀 증거는 [검증 문서](verification-and-local-devices.md)와 앱 보고서를 따른다. 실제 개인 캘린더/전체 VoiceOver 검증은 별도 #13/#14/#15 범위다. Android/API에는 이번 변경을 적용하지 않았다.

Orca iOS 담당 작업은 완료 후 retained 상태다. 이 턴에서는 root가 Git 통합과 자기 checkout의 검증만 수행했다. 후속 코드 변경은 [운영 문서](orca-sessions-and-worktrees.md)의 담당을 런타임 재확인하여 새 Task/Dispatch로 배정한다. 사용자 변경 제안을 받으면 기능별 커밋과 관련 회귀 검증으로 반복한다.

이전 PR 분리·실행·상세 결정은 [병합 전 기록](archive/2026-09-16-fsd-rule-discussion/before-main-merge.md)에 보존했다. 과거의 미병합/CI 대기 문구를 현재 지시로 해석하지 않는다.
