# 카드 URL host·네이티브 아이콘 표시 — 2026-09-15

NoticeCardViewModel의 신청 위치 각 항목과 schedule.onlineUrl을 표시할 때 문자열 전체가 http(s) URL이면 상세 ExternalLinkCard와 같은 URL.host를 사용한다. path/query/fragment/port는 표시에서 빠지고 www 등 host는 유지한다. 공백이 포함된 주소·자유텍스트·URL 혼합 안내, 비웹 스킴, host 없는 값은 원문을 유지한다. 모델 원본 URL과 State 구조와 콜백/지도 동작/날짜 레이아웃은 그대로다. 사용자 정정에 따라 날짜·위치·지도 이모지를 SF Symbols calendar/mappin.and.ellipse/map으로 교체했다. 날짜·위치는 secondary 색상과 20pt 아이콘 열로 본문과 분리하며, 지도 버튼의 44pt 터치 영역과 접근성 이름을 유지했다.

## 이번 실행과 증거

- 저장소 루트에서 `bash apps/ios/docs/evidence/card-url-host/verify.sh`: NoticeViewModelTests Swift 6 재컴파일 및 번들/공통 두 sample 실행, FSD 101파일 및 guard fixtures PASS, exit 0. 실제 명령은 verify.sh, 결과는 tests.txt. 신청 URL/온라인 URL/path-query, submissionLocations 주소·혼합 문구 보존, 모델 원문 보존 fixture 포함.
- XcodeBuildMCP session_show_defaults/list_sims 후 Dearby.xcodeproj, scheme Dearby, 이 checkout의 apps/ios/build/DerivedData 및 A617D464-41FC-4C33-A3AC-A109D5C9F054 지정, `build_run_sim({})` 성공. 07:20:47Z 시작, 7.6초, PID 42661, 경고/오류 없음. build.txt 참조.
- `snapshot_ui({})`와 `xcrun simctl io A617D464-41FC-4C33-A3AC-A109D5C9F054 screenshot <파일>` 후 이미지 직접 확인: first-card.png에서 cieat.cbnu.ac.kr 한 줄, 날짜·행사 장소·지도 버튼 확인.
- snapshot의 e22 scroll ref에 `swipe({withinElementRef:"e22",direction:"up",distance:0.75})` 3회로 4번째 카드 이동. multiple-online.png에서 신청 및 여러 일정, 온라인 URL 미확인 표시 확인. 매 swipe가 반환한 최신 snapshot의 ref를 사용했다.

## 한계와 유지 상태

다중 일정 번들 샘플은 실제 onlineUrl이 없어 화면에서 도메인 라벨을 검증할 수 없다. 온라인 URL 있는 경우는 테스트 fixture로 검증했고 실행 번들 데이터를 바꾸지 않았다. 전체 지도/제스처/전체 회귀, AX/VoiceOver/실기기 검증은 이번에 반복하지 않았다. 앱은 전용 Simulator에서 실행 유지한다. 기존 run-20260915 및 역할 문서의 미커밋 실행 기록은 보존하며 이번 커밋에서 제외한다. push/PR 없음.
