# 사용자 Calendar screenshot 후속 — 2026-09-15 KST

사용자 제공 `/tmp/dearby-calendar-reference-1.png`와 `-2.png`를 view_image로 직접 확인했다. 실제 이미지의 굵은 제목, 요일·오전/오후·부터/까지 문장, 도메인/열기 링크 카드와 시간 gutter/가로선/연속 파란 블록을 참고한다. 공유 버튼은 요구 범위가 아니므로 추가하지 않는다. 원본 링크를 열거나 공유하거나 일정 저장을 실행하지 않는다.

## 기간 문장과 링크 카드

EventPeriodPresentation은 같은날 날짜 한 번, 한국어 요일/오전·오후/분·초와 부터·까지를 표시한다. 기존 날짜만/시간대/충돌/역전/invalid의 원문 fallback은 유지한다. ExternalLinkCard는 검증된 URL과 접근성 label만 받고 native Link/borderedProminent/capsule을 사용한다. 신청 URL은 VM에서 원래 applicationInformation.url로, 온라인 URL은 기존 State에서 투영한다. 안전정책은 기존 HTTP(S)/host/무자격증명/무공백 검사를 그대로 재사용하며 네트워크 preview를 요청하지 않는다.

이 기능에서 실행: 독립 DetailPresentationTests 및 FSD74 PASS, Xcode26.6/iOS26.5 전용4156 build/run 2026-09-14 15:36:14Z 성공. 테스트는 요일·한국어 시간·원문/주소·안전URL을 포함하며 전체 standalone/OS 흐름 재실행이 아니다. 실제 화면 검증은 아래 타임라인 통합 검증에서 구별해 기록한다.
