# 문서·파일 정리 기록

2026-10-04. 요청을 **Paperthin 28개 스킬을 각각 검토·적용하여 현재 안내를 단순화하고, 참조 없는 파일을 정리하는 작업**으로 이해했다. 제품 기능 변경이나 배포·게시된 이력 재작성은 포함하지 않는다.

현재 모바일 범위는 `mobile-ui-prototype.md`, 실행 방법은 각 앱 README, 검증 진입점은 `verification-and-local-devices.md`, 작업 상태는 조율 문서가 맡는다. 두 독립 검토에서 목차를 따라가면 과거 실제 캘린더 작업으로 되돌아가는 문제를 확인했다. 오래된 기록은 삭제 전 Git 버전으로 찾아갈 수 있게 하고 현재 설명과 구분한다.

## 삭제·보존 판단

삭제 전 버전은 아래 Git 고정 링크에서 확인할 수 있다. 원본 시안·실행 소스·현재 캡처·운영 자료는 유지했다.

| 제거한 파일 | 이유·대체 위치 |
| --- | --- |
| [이전 iOS 인계](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/ios-implementation-and-handoff.md) | 과거 실제 캘린더 범위. 현재 `ios-ui-prototype.md`로 안내 |
| [이전 Android 인계](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/android-implementation-and-handoff.md) | 같은 범위 중복. 현재 `android-ui-prototype.md`로 안내 |
| [이전 복잡성 검토 정책](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/coordinator-ponytail-review-policy.md) | 설치 이력과 중복 실행 지침. 현행 규칙은 루트 AGENTS |
| [이전 통합·워크트리 기록](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/coordinator-architecture-merged-and-worktrees-cleaned.md) | 9월 상태를 현재 통합 기준처럼 안내. 현행 기록은 `orca-sessions-and-worktrees.md` |
| [캘린더 HTTP 테스트 서버](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/tests/fixtures/calendar-flow-server.py) | 이전 실제 연동 검사용. 현재 모바일은 고정 예시이고 실행 코드·자동 검사에서 참조 없음 |

## Paperthin 적용 결과

28개 모두 본문과 적용 조건을 확인했다. ‘적용’은 실제 정리에 사용했고, ‘점검’은 읽기 전용 검토이며, ‘미실행’은 조건이 맞지 않는 실행 절차를 수행하지 않았다는 뜻이다. 전부 완주했다고 주장하지 않는다.

| 스킬 | 처리 | 이번 결과 |
| --- | --- | --- |
| aim | 적용 | 문서 수보다 현재 안내와 과거 요구의 충돌을 정리 대상으로 특정 |
| autobahn | 점검 | 별도 안전 경계로 분리할 요청 없음. 불필요한 승인 절차 추가 없음 |
| catchup | 적용 | 코드·Git·목차를 읽고 현재 범위와 과거 캘린더 지시의 충돌 설명 |
| debloat | 적용 | 범위 문서의 반복 문장과 인계의 중간 진행 로그 축약 |
| dedash | 적용 | 모바일 정리 제목의 대시 구분 1곳을 괄호로 변경. 날짜 범위·코드는 유지 |
| detool | 적용 | 종료된 세션·터미널 ID를 현재 안내에서 제거. 재현 명령·증거 경로는 유지 |
| factchk | 적용 | 실제 탭·메모리 상태 소스 및 GitHub PR73의 병합·검사 결과 대조 |
| feynman | 부분 점검 | 독립 검토자가 ‘삭제가 유일한 근거를 없애지 않는가’를 제기. 사용자의 설명을 시험하는 인터뷰는 실시하지 않음 |
| hate | 적용 | 무작정 28개 절차를 실행하면 정리보다 문서·설정이 늘어나는 반론을 반영 |
| macrothink | 적용 | 맥락을 분리한 두 검토가 현재 진입 경로와 서비스 보존 위험을 독립 평가 |
| mandela | 적용 | 모델 의견 일치·줄 수 감소를 정확성 증거로 삼지 않고 코드·링크·Git 검사와 구분 |
| modelchk | 점검 | 권장 standard·measured. 모델 변경 없음. 삭제 경계나 운영 영향이 불명확할 때만 검토 확대 |
| nba | 적용 | 현재 독자를 과거 작업으로 보내는 목차·조율 문서를 첫 수정 대상으로 선택 |
| prism | 적용 | 정확성·근거 시점과 가독성·탐색을 별도 관점으로 검토 |
| re0 | 적용 | ‘최신’ 단락 추가 대신 진입 문서를 현재 상태로 재작성 |
| re0-git | 미실행 | 기존 커밋은 게시된 이력. 메시지 정리를 이유로 재작성하지 않음 |
| re0-loop | 적용 | 범위 확정→수정→링크·독립 읽기→보완의 한 주기 수행 |
| re0-memo | 적용 | 아래 재발 방지 원칙을 기록 |
| re0-merge | 미실행 | 검토할 외부 기여가 없음. 자기 변경을 외부 기여로 승인하지 않음 |
| re0-plan | 미실행 | Paperthin 저장소 전용 반복 작업 폴더는 Dearby에 생성하지 않음 |
| re0-release | 미실행 | 패키지 출시 요청이 아니므로 버전·태그·배포 변경 없음 |
| re0-upgrade | 점검 | 전역 설치 lock의 Paperthin 28개와 각 SKILL.md 존재 확인. 재설치·훅 설정 없음 |
| re0-work | 적용 | 진입 문서만 재구성. 제품 계약·검증·코드·서비스는 보존 |
| readchk | 적용 | 수정 전에 이 문서에 요청 해석·범위 기록 |
| reorder | 적용 | 목차의 모바일 6행을 범위→구현→검증→상태 순서로 이동. 항목 집합 동일 확인 |
| shower | 적용 | 배경 맥락 없는 별도 검토자에게 README·목차 전문 전달. 출처 링크·검증 작성 위치 보완 |
| sip | 적용 | 수정 후 독립 읽기·사실·참조·중복 검토를 적용하고 지적 2건 보완 |
| ssotize | 적용 | 범위·명령·진행·검증의 보관 위치를 정하고 중복 안내를 링크로 교체. 사용자 정리 요청 범위에서 수행 |

## 확인과 한계

- 코드 검토에서 양쪽 다섯 탭·고정 예시·메모리 상태와 Android 권한 부재를 대조했다. 앱 동작은 바꾸지 않았다.
- GitHub에서 [PR73](https://github.com/fixabley/dearby/pull/73)의 병합과 [API·Android·iOS 검사 성공](https://github.com/fixabley/dearby/actions/runs/37164800909)을 확인했다. 이번 변경의 새 빌드 결과와 구분한다.
- 독립 읽기는 제품 범위와 진입 순서를 정확히 이해했다. 이미지 출처의 직접 링크와 검증 기록 작성 위치를 추가했다.
- 복잡성 검토(ponytail-review): 단계별 새 관리 파일 대신 이 기록 한 개만 유지했다. 실제 기능 코드에 새 추상화·의존성을 추가하지 않았다.
- 변경 문서의 로컬 링크 104개·앵커 4개·과거 Git 대상 12개를 확인했다. 추적된 Markdown 전체에서 이번 삭제가 새로 만든 깨진 링크는 0개였다. 스킬 결과 28행·삭제 서버의 참조 부재·실행 소스/자산/서비스 무변경·`git diff --check`를 확인했다. 네이티브 빌드·기기 재설치·운영 서버·배포는 이번 로컬 정리에서 재검증하지 않는다.
- 시작부터 있던 Xcode 프로젝트·scheme 삭제와 추적되지 않는 원본 시안 폴더는 그대로 보존한다.

## 다음 정리에서 지킬 원칙

‘최신’ 단락을 누적하지 말고 현재 안내를 교체한다. 과거 요구는 적용 시점을 표시한다. 파일 삭제는 이름이나 나이보다 실제 참조와 대체 근거로 판단한다. 검사 결과는 실행 날짜·대상 커밋·한계와 함께 기록한다. 도구를 사용했다는 사실을 제품 검증으로 세지 않는다.
