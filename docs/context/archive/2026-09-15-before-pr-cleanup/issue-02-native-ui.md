# 구현 완료·리뷰 대기 — #2 네이티브 Shared/UI

## 목표

iOS `Shared/UI`와 Android `shared/ui`에 각 플랫폼의 native 디자인을 최대한 따르는 디자인 시스템을 구축하고 기존 탐색·즐겨찾기·상세에서 사용한다. 기능·책임은 맞추되 SwiftUI와 Compose Material3의 외형·관례를 각각 따른다.

## 범위

- semantic 색·타이포·간격·표면 및 현재 화면에 필요한 공통 버튼·정보 표시·상태 표현을 정리한다.
- iOS 시스템 스타일·기본 컨트롤, Android MaterialTheme·Material3 기본 컴포넌트를 우선한다.
- Shared UI는 표시값/슬롯·콜백만 받으며 도메인 상태·저장소에 접근하지 않는다.
- 공고/조직 카드와 저장 문구는 Widgets에, 공고 제목은 카드 내부에 유지한다.
- 시스템 컴포넌트를 직접 사용할 수 있는 경우 불필요한 wrapper·네비게이션 엔진을 만들지 않는다.
- 기존 사용자 흐름·모델·캐시·즐겨찾기 저장 호환성·지도/캘린더를 유지한다. 새 기능·API 변경은 제외한다.

## 추가 표시 요구사항

- 반복 라벨과 명확한 동작은 native 아이콘으로 간소화하며 접근성 이름·터치 영역과 저장 대상 조직의 식별성을 유지한다.
- 기간·장소는 아이콘 옆에 짧게 표시하고 시스템 타이포그래피로 제목·보조 정보의 위계를 만든다.
- 각 일정은 **굵은 일정 이름 → 기간 → 장소/온라인** 순서다. 신청은 별도 구획이며 여러 일정의 캘린더 버튼은 각 항목과 정확히 연결한다.
- 화면 State/ViewModel의 표시 변환은 허용하며 도메인·캐시·일정 배열 규격과 원본 URL 메모 정책은 유지한다.

## 완료 기준

- [x] 실제 사용하는 Shared/UI 구성요소와 native 대응·입력·사용처·예제를 문서화한다.
- [x] 기존 화면에 적용하고 custom UI를 유지할 경우 이유를 남긴다.
- [x] light/dark·큰 글자·관련 상태를 확인하고 주요 접근성 이름·터치 영역을 검증한다.
- [x] 카드 넘김·더블탭/버튼 저장·즐겨찾기·상세·삭제·재실행 저장 유지와 지도/캘린더를 회귀 확인한다.
- [x] 변경 전후 화면, 각 플랫폼 빌드·관련 테스트와 미검증 한계를 기록한다.

## 진행

#1과 PR #3~#6은 main에 통합 완료. 최신 사용자 목표에 맞춰 2026-09-14 구현 착수. 플랫폼별 Orca worktree/담당 세션, 기능·컴포넌트별 작은 커밋, 플랫폼별 PR로 진행한다. 공통 기준은 `docs/architecture/native-design-system.md`다.

## 디자인 참고

- iOS: https://www.figma.com/ko-kr/community/file/1527721578857867021/ios-and-ipados-26
- App Store처럼 익숙한 네이티브 표현 방향. 실제 Figma 내용은 아직 미확인이고 현재 SDK의 SwiftUI 공식 컴포넌트를 기준으로 한다.
- Android: Compose Material3·MaterialTheme와 플랫폼 접근성 관례.

구현/검증 완료, Draft PR 리뷰 대기: #7 공통 기준(6b1c5e3), #8 iOS(83c3f9a), #9 Android(ff8ccaa). main 미병합.

최종 iOS build/standalone/FSD69·light/dark/AX5·실제 제스처/신청·행사 EventKit 진입·취소·Maps 실행을 확인했다. Android JVM46·계측39·FSD68·Debug/Release·lint 오류0 및 기본/2배 light/dark 화면36개를 확인했다. 캘린더 실제 저장·VoiceOver/TalkBack 전체 음성·모든 실기기/태블릿 조합은 미검증이며 플랫폼 검증 문서에 범위를 명시했다.
