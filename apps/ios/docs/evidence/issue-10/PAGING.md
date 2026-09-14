# 추천 피드 한 장 이동 후속

2026-09-15 KST 사용자 요청. 부모 PR8에는 AX 크기에서 height=nil 및 DiscoveryPagingBehavior(enabled:false)로 자유스크롤하는 분기가 있었으며 #10 이전부터 존재했다. 이번 검증 전용4156에서 AX5를 사용 중이었고 일반 large로 복원했다; 사용자 보호 기기(A434/C38E)는 조회·변경하지 않아 사용자 기기의 실제 설정을 단정하지 않는다.

바깥 page는 항상 viewport 고정 높이이며 SwiftUI viewAligned(limitBehavior: .alwaysByOne)로 한 번의 이동을 한 공고로 제한한다. 큰글자의 NoticeCard 내용은 별도 DiscoveryCardPage 내부 native ScrollView로 읽는다. 같은 세로축 중첩에서 카드 내용 스크롤이 우선하므로 큰글자에서는 이전/다음 공고 native 버튼도 제공해 제스처 경합 없이 넘길 수 있다. 카드 데이터/본문/즐겨찾기/더블탭 handler는 변경하지 않았다.

이번 Debug build+run16:46:46Z PASS. 일반 large에서 MCP swipe distance0.9/duration0.1의 빠른 위 fling 1→2, 아래 fling2→1 확인([2번 페이지](feed-page-2.png)). Orca window3055의 기존 저장된 첫 공고 제목에 click-count2 primitive를 사용해 discovery.feedback의 `한국농어촌공사 저장됨` 변화를 확인했다; 이미 저장된 ID를 사용해 즐겨찾기 집합은 변경하지 않았다. AX5에서 다음공고 버튼1→2, 카드 내부 fast swipe로 상세/저장 버튼까지 읽으면서 현재2/4 유지([AX2번](feed-page-2-ax5.png)), 상세 버튼으로 DB손해보험 상세 진입 확인. 바깥 스크롤 상태와 안쪽 내용 스크롤을 분리했다. 일반 large/AX5 외 모든 크기·실기기·VoiceOver 음성 및 중첩 경계의 모든 제스처 조합은 미검증이다.

[Apple alwaysByOne 문서](https://developer.apple.com/documentation/swiftui/viewalignedscrolltargetbehavior/limitbehavior/alwaysbyone)를 이번 확인했고 설치 SDK 지원 범위에서 사용했다. 별도 라이브러리나 SDK 업그레이드는 없다.
