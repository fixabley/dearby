# iOS 승인 화면 충실도 인계 — issue #50

검증일:2026-09-27 KST. Checkout `/Users/jominjun/Documents/dearby/dearby-ios`, branch `feat/ios-visual-fidelity`, clean 상태에서 정본 commit `c86f809`로 시작. Terminal `term_472cad7d-204b-4362-975d-4661f37b6aad`, task `task_517938ce17a9`, dispatch `ctx_af275322501e`. Coordinator `term_1cbabda3-9f12-4f38-9655-ef8a0a3548fd`. 사용자 요청대로 완료 후 retain; push/PR/통합은 coordinator 담당.

이전 활동 구현 인계는 [보존 문서](../../apps/ios/docs/context-archive/ios-before-visual-2026-09-27.md). [검증 보고서](../../apps/ios/docs/evidence/visual-fidelity/README.md), [원본/실제 나란히 비교](../../apps/ios/docs/evidence/visual-fidelity/comparison.html), [최종 무가공 PNG와 시각·출처](../../apps/ios/docs/evidence/visual-fidelity/after/manifest.json)가 이번 작업의 증거다.

## 변경과 커밋

- `34419d3`: 승인 로고 PNG·공유 시각 요소·흰색/청록색·평면5탭.
- `6b37cec`: 발견 좌측 썸네일/우측 정보, 상세 섹션/공식 CTA.
- `9c89fa6`: 전체 프로필/공유 프로필 위계, 연락처 행/큰 아이콘, 연표, 민트 카드 묶음. 공식 GitHub 원본 아이콘 출처 포함.
- `4ebb46f`: 큰 QR·하단 공유 시트·새 명함/타일, 두 명함함 그룹, 내 명함 선택/직접 만들기, 저장 확인·비로그인 반환 로그인/취소. 전송 선택 상태를 일시적 CardModel 하나로 정리.
- `acb24a2`: 상세 탭 숨김·흰색 navigation·얇은 신청 strip·고정 CTA, 실제 탭 경계 기반 회귀, Behance 줄바꿈 수정.
- `1c2de21`: 본문 없는 DELETE에 JSON Content-Type을 보내지 않아 실제 서버 로그아웃204 정상 처리. 실제 폐기/토큰401 회귀와 테스트 타깃 전용 시각 fixture.
- 마지막 증거/문서 커밋은 git log/worker_done 참고. 게시 이력 재작성·push 없음.

## 실제 검증

- 단일 허가 Simulator `B3BE9C9D-9B2E-43BE-8D16-D4F89A97C9BE` / iPhone17 / iOS27만 사용.
- 구조16검사, strict SwiftLint57파일/위반0, 최종 Debug build-for-testing와 Release Simulator build 통과. 실제 실행 로그·명령은 보고서에 기록.
- 단위/저장/계약/실제 catalog HTTP29검사 통과. 별도29검사에서 bodyless 실제 logout, local session 제거, 원격 폐기 경고 없음, 폐기 token401 확인.
- 실제 테스트 메일 sink OTP로 로그인/게시/public projection/import 회귀 통과. 선택하지 않은 연락처·이력은 공개 GET에 없음. 메일 실제 배달 검증으로 표현하지 않는다.
- Catalog 저장·조직/프로그램·신청 자기기록 재시작 복원과 source-only/application prompt 구분, profile/QR 확대/명함함/선택/편집, 최대 접근성 글자 UI3검사13:43:14 통과(142.981초).
- 비로그인 탭/login gate 통과. 공유 명함 저장 경고 → 취소 → 반환 로그인 → 닫기 → 비로그인 유지/전송 없음/명함함 비어 있음13:48:10 통과(25.706초). iOS27의 cancel-role 숨김은 명시적 취소 버튼으로 해결.
- 조율 세션이 주요 실제 캡처를 검토했고, 후속으로 Behance와 명함함/보내기 상단 캡처 보강을 요청해 반영. 마지막 profile/wallet/send/editor 상단/하단 캡처·이력 전체 선택이 연락처를 공개하지 않는 회귀와 source presentation2검사13:52:57 통과(64.041초). 최신 PNG 시각은 manifest 참고.
- Ponytail 검토: 불필요한 중복 recipient state 제거, 실제 공유 카드/연표/버튼만 유지. 테마/네비게이션 엔진·중간 wrapper 없음. 저장/접근성 회귀는 별도 확인.

## 남은 경계와 차이

현재 API에는 썸네일 URL·컨퍼런스/동아리 분류가 없어서 미제공 자리표시자/등록·선발 분류를 유지한다(#51). 실시간 수집이나 다수 모집 공고를 꾸미지 않는다. 프로필·명함 캡처는 테스트 타깃 전용 fixture를 실제 로컬 서버에 게시한 자료이며 앱 Sources에는 샘플 레코드가 없다.

캘린더 충돌/추가, 푸시, 외부 신청 폼 자동 입력/제출, 운영 메일/HTTPS 인증, Universal Links/실기 카메라·사진 권한을 완료로 주장하지 않는다. 생성·발행·상대 전달은 기존 auth/server success/지속 idempotency 조건을 유지한다. 비로그인 저장은 카드ID/context만 저장하며, 확인 취소·실패로 저장 완료 처리하지 않는다. 이름/연락처값/날짜 등 시안 샘플은 제품 데이터로 복제하지 않았다.

iOS27 native toolbar/sheet chrome는 시스템 동작을 유지해 시안의 그림과 완전히 동일하지 않다. 가장 큰 글자에서 스크롤·수직 헤더·명시적 카드 이동이 동작한다. 별도 작은 화면 기기와 VoiceOver 음성 순회는 실행하지 않았고 다른 세션 기기를 건드리지 않았다. QR 크기를 줄여 타일을 억지로 한 화면에 넣지 않았으며 긴 이력/명함은 스크롤된다.


## CardDeck 후속 수정 — 2026-09-27 13:59 KST

담당 terminal `term_87063272-bb44-49fe-a5f6-b2be9d02a0e0`, task `task_94395276c3ad`, dispatch `ctx_d9a62d0bc9d9`. 소스 commit `02cd13082aad54f92fff25577d326d9e0620d41b`: 중간 카드 불투명 mint, title3 기준 scaled56pt 노출, 뒤 헤더 한 줄/minimumScaleFactor0.8. 이름·직업·명함 배지를 유지하며 glyph 잘림/비침을 수정했다. 카드 선택/스와이프/전송 idempotency/접근성 크기 뒤 카드 숨김은 미변경.

지정 Simulator/API로 Debug build 및 파일 단위 strict lint0건, 최종 rebuild와 인증·sheet title 확인 후 상단 캡처 단일 XCUITest13:59:15 통과(10.079초). 기존 테스트는 캡처 전용으로 임시 축소 후 원문 복구; 전체 회귀는 재실행하지 않았다. Orca helper의 SimulatorKit 로드 실패는 XCUITest로 우회하고 설정 미변경. [수정 PNG](../../apps/ios/docs/evidence/visual-fidelity/after/send-card-picker-top.png)와 manifest를 교체했으며 보고서에 첫 캡처의 줄바꿈 발견/수정, 실제 실행 범위와 제한을 기록했다. Ponytail review: Lean already. Ship. 세션은 사용자 요청대로 유지하며 coordinator가 통합·retain한다.
