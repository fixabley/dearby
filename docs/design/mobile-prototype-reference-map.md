# 모바일 프로토타입 디자인 적용표

사용자 승인: 2026-10-03~04. iOS·Android는 화면과 예시 흐름을 남기고 서버 연결을 제거한다.
추가 승인으로 `selected` 안의 명함·프로필·QR·명함함 화면도 모두 포함한다.
기존 네이티브 시각 기준의 형태·색·간격은 재사용하되 실제 서비스·권한 요구는 이번 승인으로 대체한다.

## 입력 조사

`01a0dd49-8ce1-7263-957a-d63f220da862/selected`를 재귀 조사했다.
현재 흐름 5장, 다른 승인 화면 11장, 로고 2장, 캘린더 후보 4장과 설명 파일 3개가 있다.
화면 16장과 로고 2장은 [기존 승인 목록](approved/manifest.json)에 대응한다.
캘린더는 이번 사용자가 직접 첨부한 [A안](approved/calendar-overlap-a.png)이 정본이다.
폴더명의 `needs-confirmation`과 과거 manifest의 미확정 표기는 이번 사용자 선택보다 우선하지 않는다.

## 화면과 이동

| 시안 | 적용 화면과 예시 동작 |
| --- | --- |
| [탐색](approved/discovery.png) | 로고·제목·유형 칩·사진 카드. 카드 → 상세. 활동 즐겨찾기는 없다(2026-10-06 결정). |
| [상세 상단](approved/activity-detail-top.png) | 큰 사진·분류 배지·조직·정렬된 정보 행·소개·고정 신청 버튼. |
| [상세 일정](approved/activity-detail-schedule.png) | 세로 일정표와 겹침 확인 버튼 → 예시 캘린더 선택/결과. |
| [상세 하단](approved/activity-detail-bottom.png) | 참가 안내·장소·출처, 예시 외부 링크. |
| [신청 상태](approved/activity-applied.png) | 신청 예시 표시 후 상세 상단의 얇은 체크 배너. |
| [겹침 A](approved/calendar-overlap-a.png) | 하단 시트, 두 열의 시간표, 주황 겹침 구간, 날짜·분수·건수, 확인/닫기. |
| [프로필](approved/profile.png) | 연락처 행·활동 연표·편집/추가 화면. 변경은 메모리만 사용. |
| [비로그인 프로필](approved/profile-guest.png) | 시작 버튼으로 예시 프로필 전환. 실제 계정 입력 없음. |
| [QR 보여주기](approved/qr-show.png) | 큰 예시 QR, 확대, 명함 보기, 내 명함 타일 선택. |
| [새 명함](approved/qr-new-card.png) | + 타일 → 안내 → 명함 편집. |
| [QR 찍기](approved/qr-scan.png) | 스캔 영역과 예시 명함 열기. 카메라·사진 권한 요청 없음. |
| [공유 메뉴](approved/qr-share-menu.png) | 링크/복사/이미지 메뉴와 예시 피드백. 실제 명함 게시 없음. |
| [공유 명함](approved/shared-card.png) | 큰 연락처 아이콘·연표, 저장/나도 주기 → 예시 명함 선택. |
| [명함 편집](approved/card-editor.png) | 연락처 공개 선택·이력 개별/전체 선택·미리보기·예시 생성. |
| [받은 명함](approved/wallet.png) | 검색·두 그룹·겹친 명함 카드·넘기기·상세·나도 주기. |
| [반환 완료 명함함](approved/wallet-reciprocal-only.png) | 예시 보내기로 미반환이 0이 되면 그룹 문구 숨김. |
| [보낼 명함 선택](approved/send-card-picker.png) | 내 명함 묶음·이름/직무 헤더·선택·상세·새 명함·예시 보내기. |

기본 탭은 발견·내 활동·QR·받은 명함·내 프로필이다(2026-10-06 결정, iOS·Android 반영). 내 활동은 신청한 활동을 일정순으로 보여 주고 항목·상세에서 `참여 확정 표시`를 켜고 끈다. 별도 시안이 없으므로 탐색 카드 스타일을 재사용한다.
화면에 보이는 문구는 예시 목적에 맞게 조정한다. 휴대폰 프레임·상태 표시줄·생성 이미지의 노이즈는 재현하지 않는다.
실제 접수·기기 저장·전송 완료로 오해할 문구는 예시임을 알 수 있도록 바꾼다.

## 시간표와 데이터

활동은 고정된 예시 세 개를 사용한다. 첫 활동은 2026-10-24 13:00~17:00,
비교 일정은 같은 날 14:00~15:00(서울 시간)이다. 따라서 A안의 배치를 따르되 60분·1/1로 표시한다.
마지막 확인은 상세로 돌아가며, 다른 두 활동은 겹침 없음 화면이다.
실제 기기 일정·연락처·서버 데이터를 읽지 않는다. 예시 상태는 앱 프로세스 종료 후 초기화된다.

## 공통 컴포넌트

사용자 요청에 따라 반복되는 표현은 각 플랫폼 `shared/ui`에서 먼저 분리하고 화면에서 참조한다.
버튼·배지·아바타·정보 행·시트 헤더·빈 화면 안내처럼 도메인과 무관한 표현이 대상이다.
색상·간격·버튼 높이는 [기존 시각 기준](native-visual-contract.md)의 공통 표현을 따른다.
명함 묶음·연락처·활동 연표처럼 도메인 의미가 있는 조합은 해당 `entities` 또는 `widgets`에서 재사용한다.
`shared`가 명함/활동 모델을 가져오는 역방향 의존성, 한 곳만 쓰는 얇은 래퍼, 별도 테마/탐색 엔진은 만들지 않는다.
SwiftUI와 Compose의 UI 소스는 각 플랫폼 안에서 공유하고, 이미지 원본은 `shared/assets/prototype`에 둔다.

| 공통 값 | 기준 |
| --- | --- |
| 청록 / 민트 | `#007F80` / `#F0FCFA` |
| 보조 글자 / 구분선 | `#657078` / `#E3E8EA` |
| 옅은 배경 / 본문 | `#F5F7F8` / `#172027` |
| 화면 여백 / 주요 간격 | 20 / 8·12·16·24 pt 또는 dp |
| 주요 버튼 | 최소 높이 50, 모서리 11 pt 또는 dp |

각 앱의 기존 스타일 상수로 적용한다. 별도 토큰 로더나 런타임 설정은 추가하지 않는다.
운영체제 글자 확대·뒤로가기·터치 영역 기준은 유지한다.

## 화면을 수정할 때

| 변경 대상 | iOS | Android |
| --- | --- | --- |
| 색상·주요 버튼·로고 | `Sources/shared/ui/DearbyStyle.swift` | `shared/ui/Components.kt` |
| 배지·아바타·정보 행·시트 헤더 | `Sources/shared/ui/DearbyComponents.swift` | `shared/ui/Components.kt` |
| 두 항목 전환·검색 칸·묶음 머리글·선택 칩·인라인 입력 칸 | `Sources/shared/ui/DearbyControls.swift`(두 항목 전환은 `DearbyComponents.swift`) | `shared/ui/Controls.kt` |
| 명함·연락처·활동 이력 조합 | `Sources/entities/identity/ui` | `widgets/card/cardContent` |
| 활동 목록·상세 | `Sources/widgets/catalog/ui` | `pages/catalog` |
| 프로필·QR·명함함 | `Sources/widgets/identity/ui` | `pages/profile`, `pages/qr`, `pages/wallet` |
| 공통 이미지 원본 | 저장소 루트 `shared/assets/prototype` | 저장소 루트 `shared/assets/prototype` |

2026-10-06 추가한 공통 입력 컴포넌트의 이름·인자는 [신청 활동·명함 공유 정본](../context/feature-applied-activities-and-cards.md#ui-컴포넌트-공개-api)을 따른다. 웹 `SectionHeader`·`Segments`는 `미반영`이다.
Swift 경로는 `apps/ios`, Kotlin 경로는 `apps/android/app/src/main/java/com/dearby/nativeapp` 기준이다.
먼저 공통 표현을 수정하고 해당 화면들이 이를 참조하도록 한다. 화면 이동과 예시 상태는 공통 UI 컴포넌트에 넣지 않는다.
이미지 원본을 바꿀 때에는 iOS 이미지 세트와 Android drawable 사본도 함께 갱신한다.

## 검증 상태

시안 대응 화면과 공통 UI 분리를 구현하고 실제 캡처로 배치와 주요 이동을 확인했다.
[iOS 캡처](../../apps/ios/docs/evidence/ui-prototype-selected/README.md),
[Android 캡처](../../apps/android/evidence/selected-card-flows/README.md)를 함께 본다.
플랫폼 검사 범위와 후속 재검증은 [iOS 인계](../context/ios-ui-prototype.md)와
[Android 인계](../context/android-ui-prototype.md)에 기록했다. 운영체제 기본 아이콘·서체·시트 동작에 따른 차이가 있으며 픽셀 단위 동일성을 뜻하지 않는다.
