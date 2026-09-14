# 네이티브 디자인 시스템

## 목표와 범위

이슈 #2의 목표는 iOS `Shared/UI`, Android `shared/ui`에 플랫폼 네이티브 디자인을 최대한 따르는 디자인 시스템을 두고 기존 화면에서 사용하도록 하는 것이다. 두 앱의 기능·책임은 맞추되 외형·컴포넌트 API의 기계적 동일성을 요구하지 않는다.

구조·상태·저장 규칙은 [공통 아키텍처](native-apps.md)를 유지한다. 실제 파일/API·적용 화면·검증 증거는 각 앱의 디자인 시스템 문서에서 관리한다.

## 책임과 배치

| 위치 | 책임 |
| --- | --- |
| Shared/UI | 시스템 기반 색·타이포·간격·표면 규칙, 범용 버튼·정보 표시·상태 표현, preview와 사용 계약 |
| Entities/UI | 도메인 분류 등 해당 엔티티만의 표현 |
| Widgets/<Domain>/<Widget> | 공고·조직 카드와 저장 상태 문구, 화면 State·콜백 조합 |
| Pages/App | 화면 레이아웃·네비게이션·탭·시트 구성과 상태 소유·주입 |

Shared/UI에는 NoticeModel·OrganizationModel·Repository·전역 상태를 넣지 않는다. 값/콘텐츠 슬롯과 콜백을 받고 도메인 문구·ID·조회는 상위에서 전달한다. 위젯의 View·ViewModel·State 동위 배치를 유지한다. 공고 제목은 카드 내부 Text로 남긴다.

## 네이티브 우선 원칙

- iOS는 SwiftUI 시스템 텍스트 스타일·semantic foreground/background·기본 control style과 interaction을 우선한다. 임의 RGB·고정 글자 크기·장식 효과로 시스템 동작을 덮지 않는다.
- Android는 MaterialTheme의 colorScheme/typography/shapes 및 Material3 컴포넌트 기본값을 우선한다. 동적 색상과 light/dark 정책을 앱 문서에 명시한다.
- 동일한 역할은 일관된 표현을 사용하되 단순 시스템 API alias를 전부 만드는 것은 목표가 아니다. 재사용되는 정책/조합이 있을 때만 Shared 컴포넌트를 추가한다.
- Primary/Secondary는 행동의 강조 수준이다. destructive·disabled 등의 의미와 접근성은 원래 native control에 전달한다. 저장 상태는 도메인 위젯에서 처리한다.
- 페이지의 NavigationStack/TabView, Material3 NavigationBar·sheet 등은 플랫폼 기본 컴포넌트를 직접 사용할 수 있다. 불필요한 공통 네비게이션 엔진을 만들지 않는다.
- 현재 화면에서 실제 사용하는 요소부터 구성하며 사용하지 않는 거대한 컴포넌트 목록이나 새 디자인/DI 라이브러리를 도입하지 않는다.

## 정보 위계와 일정 표시

- 반복되는 기간·장소 라벨은 플랫폼 아이콘과 간소화한 값으로 표현한다. 의미가 명확한 동작은 아이콘만 사용할 수 있지만 접근성 이름과 native 터치 영역은 유지한다. 저장 대상 조직은 화면에서 식별할 수 있어야 한다.
- 제목·일정 이름·보조 정보는 시스템 글꼴의 크기·굵기·semantic 색으로 위계를 만든다. 큰 글자를 제한하거나 중요한 내용을 잘라내지 않는다.
- 일정 배열의 각 항목은 **일정 이름 → 기간 → 장소/온라인** 순서로 표시한다. ‘온라인 예선’, ‘본선’ 등의 이름은 굵은 소제목으로 강조하고 신청은 별도 구획으로 구분한다.
- 각 일정의 캘린더 동작은 해당 항목의 기간·장소를 사용한다. 메모는 검증된 공고 원본 URL만 담는다. 날짜 축약 시 연도 경계·시간·불확실성을 임의로 없애지 않는다.
- 표시용 State·ViewModel의 변환은 허용한다. 일정 배열·도메인 모델·영속 캐시 규격은 유지하며 Shared/UI가 날짜 원본이나 저장소를 조회하지 않는다.

## 화면 적용과 검증

현재 탐색·즐겨찾기·상세의 공통 스타일을 적용 대상으로 삼는다. 카드 넘기기·더블탭 조직 저장·명시적 삭제·관련 상세·지도·캘린더 및 기존 저장/캐시 계약을 유지한다. 이 경험에 필요한 커스텀 UI는 이유와 native 구성요소를 문서화한다.

각 컴포넌트의 역할, 입력·이벤트, 대응하는 native API, 실제 사용처와 preview를 문서화한다. light/dark·큰 글자·disabled/선택 상태 등 관련 상태를 확인한다. 주요 컨트롤의 이름·터치 영역·스크롤 접근성을 확인하며 실행하지 못한 검증을 통과로 표시하지 않는다.

변경 전후 화면과 관련 빌드·테스트를 기록한다. 기능·컴포넌트 단위 커밋에 필요한 코드·문서·검증을 함께 담고 플랫폼 PR을 분리한다. 실제 서버 연결·데이터 모델 재설계·새 제품 기능은 이번 범위 밖이다.

## 참고 자료

Android의 MaterialTheme 구성과 동적 색상 정책은 [공식 Compose Material3 안내](https://developer.android.com/develop/ui/compose/designsystems/material3)를 참고한다. iOS는 [Apple HIG](https://developer.apple.com/design/human-interface-guidelines)와 사용 API의 공식 문서·현재 SDK를 확인한다.

사용자가 제시한 [Figma iOS/iPadOS 26](https://www.figma.com/ko-kr/community/file/1527721578857867021/ios-and-ipados-26)는 시각 참고 자료다. 원본을 확인하지 못한 상태에서 그 디자인을 재현했다고 주장하지 않는다. App Store처럼 익숙한 네이티브 표현이라는 방향으로 이해하며 플랫폼 전체 UI를 복제하지 않는다.
