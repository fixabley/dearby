# Git·Orca 현재 상태

확인: 2026-09-23 KST. origin은 https://github.com/fixabley/dearby.git 이다.

## 정리 결과

사용자가 Dearby 작업 세션·worktree의 커밋·push·병합 및 정리를 승인했다.

| 역할 | 상태 |
| --- | --- |
| 메인 | 현재 checkout 유지. 공통 문서 통합 담당. 사용자 Xcode 변경 보존 |
| iOS | PR27~33 반영 확인. 전체 apps/ios tree가 origin/main과 동일. 담당·과거 보존 세션 종료 및 worktree 제거 완료 |
| Android | PR34 반영 확인. 전체 apps/android tree가 origin/main과 동일. 담당 세션 종료 및 worktree 제거 완료 |
| 제품 기획 | 기획·발표 문서 PR35 push 완료. 원본·임시 산출물 백업과 해시 검증 후 담당 세션·worktree 제거 완료 |
| dearby-ir | 별도 저장소의 발표 후속 작업. 이번 정리 대상에서 제외 |

iOS 원본 branch feat/ios-design-simplification(086fd94), Android fixabley/dearby-android-design-simplification(da43846)은 원격에 push했다. 기존 main 반영은 cherry-pick 기반이므로 원본 branch를 다시 merge해 오래된 공통 문서를 덮어쓰지 않는다.

기획 문서·발표자료와 공통 인계 정리는 [PR35](https://github.com/fixabley/dearby/pull/35)로 통합한다. 최종 GitHub 검증·병합 상태는 PR에서 확인한다. 정리 후 Dearby 저장소에는 메인 worktree만 남았고, 별도 dearby-ir 세션은 보존했다.

## 로컬 백업

위치: `/Users/jominjun/Documents/dearby-backups/2026-09-23-cleanup/` (저장소 외부, GitHub에 업로드하지 않음).

- `branches.bundle`: 정리 전 Git refs와 전체 이력. git bundle verify 통과.
- `dearby-ios-architecture-tests.tar.gz`: 추적·미추적·ignored 파일을 포함한 작업 파일 백업. tar 29,797개 항목 읽기 확인.
- `dearby-android-design-simplification.tar.gz`: 같은 방식의 작업 파일 백업. tar 3,258개 항목 읽기 확인.
- `dearby-product-replanning.tar.gz`: 기획 checkout의 804개 파일 전체 백업. 임시 presentation-build 44개 파일 포함, 각 파일 SHA-256을 archive 내용과 대조했다. `product-files-sha256.json`에 목록과 해시를 보존했다.
- `product-final.bundle`: 기획 담당 최종 커밋 e1f5724까지의 Git 이력.
- `root-user-xcode.patch`: 사용자 Xcode project/scheme 변경 보존용 추가 사본. root 원본 변경도 그대로 유지한다.

복구가 필요하면 archive를 별도 디렉터리에 풀어 파일을 확인하고 branches.bundle에서 필요한 ref를 가져온다. archive의 .git 연결 파일은 포함하지 않았으므로 옛 worktree 경로를 그대로 재사용하지 않는다. 새 작업은 최신 main으로 Orca 하위 worktree를 생성한 후 필요한 파일만 복원한다.

## 세션 운영

이번 runtime은 d692ee72-2585-499c-bc70-f4acd2cfdaef, root terminal은 term_a4c93b27-6d82-45be-a321-16dbc6a4bed9다. 이 값은 관측 기록이며 재개 시 Orca CLI에서 다시 확인한다. 삭제한 플랫폼 세션 handle은 재사용하지 않는다.

이전 연결과 검증 이력은 [정리 전 보관본](archive/2026-09-23-before-worktree-cleanup/orca-sessions-and-worktrees.md)에 있다. 새 API·iOS·Android 구현은 메인에서 범위를 조율하고 최신 main의 역할별 하위 worktree·담당 세션으로 배정한다. 과거 세션이나 검증 결과를 현재 활성 작업·새 검증으로 해석하지 않는다.
