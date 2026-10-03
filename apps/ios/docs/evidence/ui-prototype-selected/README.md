# selected UI — iOS Simulator 확인

2026-10-04 KST, iPhone Air / iOS 26.5 기존 Simulator에서 실행한 SwiftUI 화면입니다.
`f8701df` 구현 기준입니다. 이 파일들은 실제 native UI 캡처이고 앱 배경 이미지가 아닙니다.
조율 메시지 `msg_839b077881a6`에 따라 주요 PNG만 저장소에 보존합니다.
전체 xcresult/로그/추가 캡처는 프로젝트 밖 `~/.dearby-signing/ios-ui-prototype-worker/`에 있습니다.

| 화면 | 확인 |
| --- | --- |
| [발견](discovery.png) | 고정 3활동·사진·필터·5탭 |
| [행사 일정](activity-schedule.png) | 2026-10-24 13–17시, 고정 5세션 |
| [겹침 1건](calendar-overlap.png) | 14–15시 60분, 30분 격자·2열·이어짐·확인 |
| [겹침 0건](calendar-clear.png) | 정상 빈 결과와 닫기 |
| [QR](qr.png) | 정적 example.com QR, 기본 글씨에서 내 명함 타일 표시 |
| [QR 공유 메뉴](qr-share-menu.png) | 흰색 시트·구분선·로컬 안내 |
| [명함함](wallet.png) | compact 이력·이전/다음·상세/주기 CTA |
| [공유 카드](shared-card.png) | 선택 정보와 저장/주기 두 CTA |
| [보낼 명함](send-card-picker.png) | 카드 선택·상세·보내기 CTA |
| [프로필](profile.png) | 단일 제목·연락처·활동 이력 |

발견/프로필은 `final-ui-tests.xcresult`, 일정/겹침은 `review-ui-tests.xcresult`,
QR/공유/명함함은 `handoff-ui-tests.xcresult`, 보낼 명함은 `identified-send-tests.xcresult`의 정지 캡처입니다.
각 캡처의 source 영역은 마지막 커밋과 같습니다.

전체 9 단위/9 UI 검사 통과 후 후속 시각 보정 흐름을 다시 검사했습니다.
최대 글씨의 부분 노출 버튼을 XCTest가 hittable로 판단한 실패 1건은 하단 CTA 위로
스크롤 위치를 이동시켜 재검사했습니다. 보낼명함 상세 버튼은 고유 접근성 ID로 구분하고 실제 열기/닫기까지 통과했습니다. 결과 전체 기록은 [인계 문서](../../../../../docs/context/ios-ui-prototype.md)에 있습니다.
최대 글씨에서는 화면 내용이 스크롤되며 기본 글씨 캡처와 같은 한 화면 밀도를 강제하지 않습니다.
VoiceOver 실제 낭독·iPad·외부 공유 대상 전송·실기기 설치는 이 증거의 범위가 아닙니다.
