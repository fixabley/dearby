# #2 iOS 검증 기록

2026-09-14, main `f963401` → `fixabley/dearby-ios-2`. Xcode 26.6 (17F113), Swift 6, iOS deployment 26.0 / Simulator runtime 26.5. 전용 `Dearby-Issue1-iOS` (`A434888F-4096-48AE-91B2-37A498233B55`)만 설치/실행/appearance·text size 변경에 사용했다. 사용자 `C38E28BE-C541-4209-B59C-F1F134B5A3FE`는 보존했고 기기 초기화/종료/실제 calendar save는 하지 않았다. 종료 시 전용 기기는 원래 light/large로 복원했다.

## 변경 전후

모든 PNG는 실제 앱 Simulator 캡처이며 Figma/목업이 아니다. before는 f963401 빌드, after는 이번 구현이다. 실행 중 저장 상태가 달라질 수 있으며 before 즐겨찾기의 기존 DB손해보험은 보존했다.

| 화면 | Before | After light | After dark / 큰 글자 |
| --- | --- | --- | --- |
| 발견 | [before](evidence/issue-02/before-discovery.png) | [light](evidence/issue-02/after-discovery-light.png) | [dark](evidence/issue-02/after-discovery-dark.png), [AX5 상단](evidence/issue-02/after-discovery-AX5-dark.png) |
| 상세 | [before](evidence/issue-02/before-detail.png) | [light](evidence/issue-02/after-detail-light.png), [기간·장소·출처](evidence/issue-02/after-detail-actions.png) | [dark](evidence/issue-02/after-detail-dark.png), [AX5](evidence/issue-02/after-detail-AX5-dark.png) |
| 즐겨찾기 | [before](evidence/issue-02/before-favorites.png) | [light](evidence/issue-02/after-favorites-light.png) | [dark](evidence/issue-02/after-favorites-dark.png), [AX5 최종 하단 배경](evidence/issue-02/after-favorites-AX5-dark.png) |

AX5에서는 첫 화면에 모든 버튼이 들어가지 않는다. 실제 작은 swipe 두 번으로 **같은 첫 카드**의 저장과 상세 버튼에 도달했다: [버튼 도달](evidence/issue-02/after-discovery-AX5-actions.png) → [상세 버튼 tap 후 같은 공고 sheet](evidence/issue-02/AX5-detail-from-discovery.png). 초기 paging가 짧은 swipe를 되돌려서 접근성 크기에서 snap을 생략하도록 수정한 뒤 재검증했다. 기본 크기는 시스템 paging 그대로다.

## 실제 실행 결과

- 각 기능 커밋 전 XcodeBuildMCP `build_sim` 성공. 최종 코드 `build_run_sim` 2026-09-14T14:24:38Z 성공, warnings/errors 없음. Shared light/dark/AX5/disabled/empty 및 widget 긴 제목 preview 선언도 이 빌드에서 컴파일됨.
- `bash apps/ios/tests/run_standalone.sh` exit 0. [전체 로그](evidence/issue-02/regression-tests.txt). favorites 저장/복원/삭제/중복, 독립 model·State 조합, L1→SwiftData→mock, disk reopen/rollback/snapshot 교체, 지도 값/요청과 calendar 날짜·원본 URL notes 회귀 포함. 로그의 CoreData `not-a-directory` 오류는 초기화 실패 시 보존을 검증하는 의도된 negative fixture이며 마지막 PASS까지 완료했다.
- `python3 apps/ios/tests/check_fsd_boundaries.py`: 최종 67 Swift files 및 positive/negative fixtures PASS. `git diff --check` PASS.
- 실제 터치 swipe로 1/4 → 2/4 → 1/4 확인. 일반 크기 저장 button tap → 한국농어촌공사 저장됨 → 즐겨찾기에 독립 section 표시. 재빌드/재실행 후 같은 저장 상태 유지 확인. 해당 테스트 추가 조직의 삭제 button tap → [DB손해보험만 남음](evidence/issue-02/after-delete.png) → 발견의 첫 카드 `저장 안 됨` 확인.
- AX5 저장 button의 멱등 재저장, 상세 button의 sheet 진입, 즐겨찾기 NavigationLink의 상세 push 및 Back 확인. 상세 화면 스크롤로 기간/지도/출처까지 표시 확인.
- 접근성 트리에서 저장 button label/value, 조직명이 포함된 삭제 label, native tab/NavigationLink 식별 확인. InformationRow label/value 결합, status text+symbol, title header trait 및 신청/활동 calendar 구별 label은 코드로 확인했다.
- 터치 정책: native bordered `.controlSize(.large)` 사용(기본 화면에서 약 50pt 높이), borderless calendar/map/link 및 목록 액션은 label minHeight 44pt. 실제 중앙 tap은 저장/상세/삭제에서 확인했다. 정밀 hit-test 경계 측정이나 VoiceOver 실제 낭독 검증은 수행하지 않았다.
- App/Entities model/API/Features/Resources 및 State/VM diff 없음: favorites key/ID/single owner, Notice/Organization model, cache, observation 수명, body diskIO와 지도/calendar mapper·flow 계약은 그대로다.

## 검증 한계

실제 doubletap은 XcodeBuildMCP batch의 2회 연속 tap으로 시도했으나 저장 상태 전환을 확인하지 못했다(자동화 간격 제한인지 실제 제스처 문제인지 미확정). 기존 `.onTapGesture(count: 2)`와 accessibility save action은 그대로이고 명시적 저장 버튼 흐름은 검증했다. 실제 doubletap 재검증은 남는다.

지도 앱 실행과 EventKit 편집기 진입/취소는 이번 런에서 완료 증거를 얻지 못했다. calendar button 자동화 tap 후 편집기 화면을 확인하지 못했으므로 성공으로 집계하지 않는다. 기존 mapper/launcher 회귀와 callback 연결은 확인했으며 실제 일정은 저장하지 않았다. Canvas preview 렌더 세션, 물리 기기, iPad/회전, VoiceOver 낭독 및 Increase Contrast는 미검증이다. 현재 PNG는 시스템 기본 text size 또는 최대 AX5를 확인한 것으로 모든 중간 크기 조합 검증을 의미하지 않는다.

## 후속 OS 흐름 재검증 (c1045f1, 2026-09-14 14:32–14:38Z)

위 최초 한계 중 calendar 진입과 지도 실행, 실제 doubletap 콜백은 [후속 증거](evidence/issue-02/followup/README.md)로 추가 확인했다. 앱 코드 변경 없이 신청/활동 EventKit 편집기와 지도 핀이 열렸으며 single doubleclick primitive로 현재 카드 저장 피드백이 갱신되었다. EventKit의 remote UI가 AX tree에서 빠져 배경 ref만 노출되는 자동화 제약을 확인했다. 성공/환경 제약 및 활동 취소 중 외부 입력 provenance 한계는 후속 기록에 구별했다. 최초 기록은 당시 결과로 보존한다.

## 일정 이름·메타데이터·아이콘 후속 (최신)

[후속 증거와 성공/환경 제약 구분](evidence/issue-02/metadata/README.md). Shared MetadataRow/CompactPeriod와 State 투영을 적용하고 신청과 활동 단계 구분, 굵은 단계 이름, phase별 기간/장소/캘린더 action, native icon-only 액션을 실제 사용한다. 원문 조건·장소 불확실성 및 접근성 labels는 남는다.

최종 XcodeBuildMCP build/run 15:01:30Z 성공 및 전체 standalone suite exit0/FSD69파일 통과. 신규 독립 기기4156에서 실제 doubletap 미저장→저장, 즐겨찾기 표시/삭제, 신청·행사 calendar editor 진입→폐기→상세 복귀, 지도 앱 실행, 발견 swipe, 다단계와 주요 화면 light/dark/AX5를 확인했다. calendar 실제 save/VoiceOver 낭독/모든 단계 editor 개별 실행은 미검증이다. 기존 A434/C38E는 초기화·종료하지 않았다. c1045f1과 최신 양쪽에서 실제 편집기 진입이 가능해 List/Section의 calendar 진입 회귀는 재현되지 않았다.
