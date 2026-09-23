# 제품 재기획 — 현재 상태와 인계

갱신: 2026-09-23 KST. 제품 기획 원본과 발표 인계 사본을 Git으로 보존하는 작업이다. 신규 인터뷰·앱 구현·미확정 명세 채택은 범위 밖이다.

## 사용자 목적과 승인 범위

IT·개발에 관심 있는 대학생이 흩어진 교외 활동을 원하는 방향과 경험으로 빠르게 찾도록 첫 버전 기획 명세를 완성한다. 2026-09-20 기획 목표 Seed를 합의했고 2026-09-22까지 세부 규칙을 확인했다. Seed 합의와 명세·구현 완료는 다르다.

2026-09-23 메인에서 이 checkout의 문서 검토·주제별 commit·origin push·main 대상 PR 작성을 명시적으로 승인했다. PR 병합과 worktree 삭제는 메인 담당이다. 이전 commit/push 금지는 이번 승인 범위에서 대체됐다. 다른 checkout·공통 계약·앱 코드는 수정하지 않는다.

## 최신 기획 정본

- [재기획](../product/replanning-2026-09.md): 프로그램 카드·상세 회차별 공고, 고유 프로그램 수, 선택 직무 기준 모집 상태 우선과 경험→마감→시작 순 정렬, 스크랩/추천 반응 분리, 현재 회차 경험의 근거, 0건 부분 조합 선택 후 즉시 적용·해제 조건 안내·되돌리기.
- [방향/경험 두 축](../product/tag-types-and-profile-draft.md)과 [행위별 속성](../product/action-based-experience-tags-draft.md): 다중 방향/경험 각각 OR·유형 사이 AND, 최우선 경험 우선, 행위와 선택 속성을 경험 1개로 처리, 추천 전체/부분 일치 구분, 경로 포함 하위 조건 선택, 제작/과제수행 경계. 평면 6개 태그는 이전 제안이며 전체 사전 확정이 아니다.
- [출처·처리 초안](../product/initial-sources-and-ingestion-draft.md), [경쟁 조사](../product/competitive-landscape-2026-09.md), [태그 사례](../product/tag-review-examples-2026-09-21.md): 조사일의 근거와 모델 제안. 운영 데이터 승인·현재 모집 확인·실제 자동 수집 검증으로 해석하지 않는다.
- [Socrates 원본](../../.symposium/scratch/socrates.md): canonical Seed와 사용자 승인 이력. 이전 사이클은 역사 기록이며 후속 승인이 우선한다.

미완료: 최초 출처 최종 채택·갱신/중복 세부 정책, 전체 속성/어휘 사전, 추천 행동 가중치·부분 일치끼리 순위, 동시 모집 회차 조건 결합, 종료/날짜 미확인 정렬, 부분 조합 정렬·개수·단일 조건 포함, 정보 저장/보관 정책, 성공 평가 수치. 구현 전 메인과 플랫폼 영향 조율이 필요하다.

## 발표자료 소유권

[발표 폴더](../product/presentation/README.md)는 2026-09-22 dearby-ir에 넘긴 보존 사본이다. 후속 편집은 `/Users/jominjun/Documents/dearby-ir/incoming/replanning-2026-09-22/` 인계를 기준으로 진행한다. 두 저장소는 자동 동기화되지 않으며 이번에는 다른 저장소를 수정하지 않았다.

기존 기록: 15개 원본 파일 복사·SHA-256 일치 확인, 전달 요청 `9b9a1013-26c7-4ac9-b533-25cbc8640fe7`의 input_accepted receipt가 있다. 당시 수신 턴 시작·완료까지 증명하는 receipt는 아니다. 기존 대상 terminal ID를 현재 live 상태로 재사용하지 않는다.

## 이번 보존·검증

origin/main `3e56a74`를 2026-09-23 fetch 후 fast-forward merge했다. 충돌 없음. 메인 하위 Orca 연결과 runtime `d692ee72-2585-499c-bc70-f4acd2cfdaef`, 자기 terminal `term_3e87ffb4-02fd-4e49-9366-e957a41932bf`를 이번에 확인했다. 브랜치는 `fixabley/dearby-product-replanning`이며 재개 시 실시간 상태를 다시 확인한다.

이번 새 실행: 현행 기획 Markdown 상대 링크 검사, JSON 구문·15장·참조 경로 검사, HTML 재생성 후 바이트 일치, PPTX ZIP/XML 무결성·15장/노트 15개, 기존 package-integrity 및 layout-geometry 검사 통과(각 finding 0). 사용 도구는 Presentations 26.921.11914이며 크기 12192000×6858000 EMU, 표 5/13/14장, Apple SD Gothic Neo 정책과 heading/bullet geometry를 검사했다. 최종 PPTX SHA-256은 `7ce3c5a60b55b094bcdd1b63e1943183ad03ca1aa30ab7b0b42174aa337c10dd`로 이전 final receipt와 일치한다.

Ponytail: HTML 생성기와 보존 PPTX 생성 흐름을 검토했으며 이번 보존 범위에서 제거할 과도 설계 후보 없음. PPTX 생성 원본의 머신별 경로는 보관 당시 한계로 명시했고 이식 작업을 추가하지 않았다.

과거 결과(이번 재실행 아님): 2026-09-22 PPTX 렌더/시각 검사·Artifact Tool import, HTML 877×939/1440×900/390×844 조작·화면 검사. 이번에는 새 렌더, 브라우저 상호작용, PowerPoint 원어플리케이션, 전체화면·인쇄, 웹 출처 재조회, 앱 빌드/테스트를 실행하지 않았다.

## 정리 결과와 다음 행동

담당 최종 커밋 e1f5724까지 [PR #35](https://github.com/fixabley/dearby/pull/35)에 push했다. 메인은 공통 인계 문서를 후속 커밋으로 통합하고 CI·병합을 담당한다. 신규 기획 구현은 시작하지 않았다.

임시 렌더·중간 빌드 44개 파일은 Git에 넣지 않고 메인이 전체 checkout과 함께 외부 백업했다. 각 파일의 archive 내용과 SHA-256을 대조한 뒤 담당 세션과 worktree를 제거했다. 정확한 중간물 목록은 [백업 목록](../../.symposium/scratch/archive/2026-09-23-product-preservation/local-backup-manifest.md), 백업 위치·복원 방법은 [Git·Orca 운영](orca-sessions-and-worktrees.md)을 따른다. 원본 build.mjs/write_content.py와 정리 전 역할 인계는 [보관함](../../.symposium/scratch/archive/2026-09-23-product-preservation/README.md)에 보존했다.

기획 명세 미완료 항목은 위에 남겼다. 후속 기획은 이 문서와 정본에서 재개하며, 앱 구현은 사용자 지시 후 공통 계약과 플랫폼 범위를 조율한다. 이전 terminal handle을 활성 세션으로 재사용하지 않는다.
