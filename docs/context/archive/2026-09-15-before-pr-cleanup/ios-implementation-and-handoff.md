> 최신 상태 (2026-09-15 02:00 KST): #10 최신 요청 구현·검증 완료, iOS PR12 / Android PR11 Draft push 완료. 두 담당 세션 retained, active 작업 없음. 최종 head·검증·한계는 [issue-10-device-calendar.md](issue-10-device-calendar.md)의 최종 완료 확인 참조. 아래 진행 중 기록은 이전 이력입니다.

# iOS — 구현과 인계

갱신: 2026-09-15 01:34 KST. #2 PR8 head458f12d 완료·미병합. 현재 같은 dearby-ios-2 worktree의 fixabley/dearby-ios-calendar-busy 브랜치에서 #10 구현 중. Task task_11eadc19d0b0 / Dispatch ctx_a0a1c01f677b, terminal term_92037054-bdf7-447f-b5aa-fdf99c59141d active. 최신 결정/검증/미완료는 [기기 캘린더 인계](issue-10-device-calendar.md)가 정본이다. 최신 요청은 타임라인 본문 Primary/보조색 블록+교집합 점선+경고, 요일뒤줄바꿈, 한국시간라벨제거. 아래 #2 검증은 과거 실행이며 #10 완료가 아니다.

## #2 구현과 검증

Shared/UI에 native 간격·semantic 표면·InformationRow·MetadataRow·StatusMessage·large Primary/Secondary 정책을 두고 탐색·상세·즐겨찾기에 적용했다. 상세/즐겨찾기는 native List/Section, AX 크기의 탐색은 자연 스크롤로 같은 카드 버튼에 접근한다. 일정별 굵은 이름→기간→장소/온라인과 개별 캘린더/지도 아이콘, 신청 별도 구획을 구현했다. Shared/Lib CompactPeriod와 State/VM 표시 조립을 추가하고 원본 domain·SwiftData·샘플 규격은 유지한다.

Xcode26.6/SDK26.6 빌드(15:01:30Z 시작) 성공, 전체 standalone 회귀 exit0와 FSD69/negative fixtures 통과. 새 전용 4156AE92-5308-4050-93DC-C9E241918DCB에서 실제 미저장 카드 doubletap→저장, 목록/삭제, 신청/행사 EventKit 진입/취소, Maps 실행, light/dark·AX5 일정/버튼 스크롤 확인. root가 실제 상세·AX5 PNG와 로그를 검토했다. 상세 증거는 worker의 apps/ios/docs/evidence/issue-02/metadata/README.md, 전체 보고는 apps/ios/docs/VERIFICATION-ISSUE-02.md다.

처음 사용한 A434에서 원인 미상의 UI 전환 후 새 기기로 분리했다. 새 기기에서도 MCP의 sheet 배경 AX target 문제를 확인해 실제 window screenshot/좌표 입력으로 판정했다. 사용자 입력 또는 앱 회귀로 단정하지 않는다. A434/C38E는 이후 조작하지 않는다. 새 기기 light/large로 복원하고 리뷰용 유지.

실제 Calendar Save·VoiceOver 음성·모든 기종/방향·다단계 모든 editor 실행은 미검증. 최신 Maps 실행을 확인했고 정확한 pin 증거는 후속 UI 전 c1045f1의 followup/map-launch.png다. 캘린더 원본URL 메모·phase 매칭은 기존 mapper 회귀와 State 순서 검증을 유지한다.

## 구현 기준

Dearby / `io.fixabley.dearby`, SwiftUI, Swift 6, iOS 26 이상. 프로젝트는 `apps/ios/Dearby.xcodeproj`, scheme은 Dearby다. 실제 트리와 노출 진입점은 [iOS 아키텍처](../../apps/ios/ARCHITECTURE.md)를 따른다.

- App이 session·저장소·공유 상태의 수명과 화면 라우팅을 소유한다.
- `FavoriteOrganizations`는 @MainActor @Observable 공유 원본이며 UserDefaults 키 `dearby.favoriteOrganizationIDs.v1`을 유지한다.
- NoticeCardViewModel·NoticeDetailViewModel은 독립 NoticeModel/OrganizationModel을 조합해 State를 제공한다. 단순 표시 UI에 ViewModel을 기계적으로 추가하지 않는다.
- `Widgets/Notice/NoticeCard/`, `Widgets/Organization/FavoriteOrganizationCard/`에는 View·ViewModel·State를 같은 폴더에 둔다.
- `Shared/UI/Buttons`의 PrimaryButton/SecondaryButton은 네이티브 버튼 스타일을 제공한다. NoticeCardSaveButton이 조직명·하트·콜백을 조합한다. 공고 제목은 카드 안에 둔다.

## 저장과 조회

Notice/Organization 각각 인메모리 Repository → SwiftData ID 조회 → 번들 mock 순서다. 외부 결과를 명시적으로 저장한 뒤 메모리에 반영하며 missing과 오류를 구분한다. 조직은 id·name·parent ID를 저장하고 이름·경로는 화면 State에서 조합한다.

App의 SwiftDataSnapshotStore가 레코드·manifest·snapshot 변경을 transaction으로 조율한다. snapshot 교체 시 session/L1을 갱신하고 실패 시 rollback하며 DB를 임의 초기화하지 않는다. 기본 저장 위치는 Application Support의 `DearbyNoticeCache/notices.store`다. 현재 작은 동기 mock은 MainActor 기반이고 실제 API 비동기 연결은 별도 범위다. UI body에서 디스크 조회를 수행하지 않는다.

## 지도·캘린더와 다음 작업

장소 좌표·원문 근거 및 신청/활동 기간별 편집기 연결을 유지한다. 이벤트 notes는 검증된 공고 원본 URL 하나이며 없으면 빈 문자열이다. 별도 event.url의 신청/온라인 링크는 유지한다. 실제 일정 저장은 사용자가 편집기에서 결정한다.

#2에서는 SwiftUI 기본 표현을 우선하되 탐색·더블탭 저장·즐겨찾기·상세 동작과 현재 데이터 경계를 보존한다. Observation·Repository 비교의 상세 판단 이력은 [보관 문서](archive/2026-09-14-before-consolidation/context/ios-architecture-comparison-swiftui-ice-cubes.md)에 있다. SDK 관련 설명은 당시 확인값이므로 도입 시 재확인한다.

[검증 결과·명령·기기 주의사항](verification-and-local-devices.md) / [앱 실행 안내](../../apps/ios/README.md)
