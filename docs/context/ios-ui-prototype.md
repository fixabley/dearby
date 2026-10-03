# iOS selected UI 프로토타입 인계

최종 검증: 2026-10-04 00:27 KST, checkout `ios-ui-prototype`, 기준 `82ae19f`.
Orca terminal `term_235408d9-1d3e-45d3-a451-b376d51bff65`, task `task_1caf2d331938`, dispatch `ctx_9d06642750f2`.
소유: `apps/ios/**`, 이 문서. root workflow/다른 앱/운영 데이터/서명 설정은 수정하지 않음.

## 최신 승인과 화면

조율 메시지 `msg_b7c1bf674d59`로 최초 calendar 요청에 selected 전체 UI가 추가 승인됨.
이전 숨김 경계보다 최신 5탭 승인이 우선하며, 서비스·영구저장 금지는 유지함.
원본 selected의 current-flow 5장, other-approved 11장, 로고 2장과 calendar 시안 A를 직접 열어 비교함.

- 발견·저장: 사진 카드 3개, 참가등록/선발 필터, 세션 북마크, 상세로 이동.
- 상세: 사진·조직·정보행·소개·일정·참가 안내·장소·출처·하단 CTA·신청완료 배너.
  컨퍼런스는 13–13:30/13:30–14:30/14:30–15/15–16/16–17의 고정 5세션.
  나머지 활동은 각 활동 시작/끝의 진행 시간을 표시함.
- 신청: 예시 안내 시트와 로컬 완료/취소. HTTPS 예시 링크만 외부 브라우저로 위임.
- 일정: 선택 단계 유지, 결과에서 선택폼 숨김. 둥근 시트/핸들/건수/X, 날짜/60분 요약,
  30분 2열 시간표, 청록 활동·파랑 바쁜 시간, 주황 겹침 밴드/점선/라벨, 이어짐 표시.
  마지막 확인은 닫기, 여러 결과는 다음, 0건은 별도 결과. 큰 글씨는 large 시트와 스크롤.
- 프로필: 게스트→예시 로그인, 연락처/이력/편집. 실제 인증 없음.
- QR: 예시 QR 표시·확대, 카드 선택/새 카드, 공유 메뉴, 스캔 모양/사진 선택→예시 카드.
  공유 메뉴는 불투명 흰 시트. 카메라·사진·클립보드·QR 생성/디코딩·외부 전달 없음.
- 명함: 공개 연락처/이력 선택, 전체 선택, 프리셋, 미리보기, 생성/기존 카드 편집.
  편집은 선택한 카드 ID를 유지하며 새 카드를 추가하지 않음.
- 명함함: 검색·미교환/상호교환·위아래 넘김·상세·보낼 카드 선택·예시 교환 완료.
  모두 교환하면 분류 탭을 숨김. 공유 카드 저장/전달 표시는 현재 세션에서만 변경됨.

## fixture·상태·구조

- `DemoActivities.swift`: 컨퍼런스 10/24 13–17시 코엑스, 캠프 11/7 10–18시 서울,
  밋업 11/21 14–17시 온라인. Asia/Seoul·무료, 모집중/예정은 날짜와 무관한 고정 예시.
  조율 추가 승인으로 공식/신청 링크는 404 없는 `https://example.com`으로 통일함.
- `CalendarConflictState.swift`: 2026-10-24 14–15시 고정 바쁜 시간. 컨퍼런스 60분/1건, 나머지 0건.
- `DemoIdentity.swift`: 가상 이름·연락처·활동 이력, 내 카드 3개와 받은 카드 5개.
- Catalog/IdentityViewModel과 View state만 사용. 종료 후 초기 fixture로 돌아오며 기존 기기 데이터를
  읽기·초기화·삭제·마이그레이션하지 않음. 명함은 생성/편집 당시 선택 정보의 메모리 사본.
- 실제 재사용 버튼/배지/아바타/정보행/세그먼트/시트 헤더는 `shared/ui`.
  명함 카드·덱·연락처·이력은 `entities/identity/ui`. Repository/service 대체 래퍼 없음.
- root 제공 생성 사진 3장/고정 QR을 assets에 원본 그대로 복사. 로고·GitHub mark·AppIcon 보존.
- API/auth/Keychain/SwiftData/파일 저장/EventKit/카메라/푸시 실행코드와 권한 문구/API origin/ATS/URL scheme 없음.
  활동의 HTTPS 링크 ShareLink만 OS 공유를 사용하며 명함 공유는 화면 예시임.
- Debug `com.dearby.dearby`, Release `io.wid.dearby`, 버전 `0.1.0 (1)` 유지.

## 검증 증거

프로젝트 밖 `~/.dearby-signing/ios-ui-prototype-worker/`에 로그/xcresult/캡처 보관.
기존 iPhone Air iOS 26.5 `567FC150-8A0F-4F12-9349-F2652BF79E80`만 사용.
새 Simulator/clone, 실기기 조작, 서명·인증서/Keychain 접근 없음.

- Calendar 커밋 `04a0f16`: Debug 빌드, 단위 5개/UI 3개, SwiftLint 0건, 구조 17개 통과.
  `timeline-tests.xcresult`, `timeline-evidence`에서 시안 A와 직접 비교, 60분/1건/정확한 시간대 확인.
- 첫 selected 전체 검증: `full-ui-tests.xcresult` 단위 8개/UI 7개 통과.
  `full-ui-evidence` 정지 캡처를 원본과 비교해 프로필 중복 제목, QR 타일 잘림,
  명함 덱/공유카드 CTA 밀림과 공유 시트 투명 배경을 발견하고 수정함.
- `final-ui-tests.xcresult`: 단위 9개/UI 9개 전체 통과. 기본 및 최대 접근성 글씨,
  기존 카드 ID 보존 편집, 모두 상호 교환시 그룹 숨김, 세션 초기화를 검사함.
  `final-ui-evidence`의 QR 타일/명함함 CTA/프로필 단일 제목/calendar를 직접 비교했고 root도 확인함.
- 후속 시각 보정 후 `review-ui-tests.xcresult`: 단위 9개, 기본 calendar/QR 흐름 통과.
  최대 글씨 calendar 부분 노출 버튼 탭이 빗나가 시트 미표시 실패 1회 발생.
  AX hierarchy에서 calendar y712–838과 sticky CTA y679–866의 겹침을 확인함.
  하단 고정 CTA를 피해 버튼 중심을 이동시키고 시트 표시를 기다리도록 테스트만 보정.
- `handoff-ui-tests.xcresult`: 최대 글씨 calendar→명함 상세, QR/공유/편집 흐름 통과.
  보낼명함 상세 버튼의 이름 기반 hittable assertion은 실패했으나 정지 캡처에서
  상세/보내기 CTA 모두 완전히 보이는 것을 확인함. predicate 대기만으로도 실패하여
  배경 명함함과 같은 이름의 버튼을 구분하는 `send-card-preview` 접근성 식별자 1줄을 추가함.
  기존 DerivedData가 이전 assertion/줄번호를 출력한 정황도 있어 VerifyDerivedData로 분리 재빌드함.
- `identified-send-tests.xcresult`: 고유 버튼 조회/실제 상세 열기/닫기/예시 보내기/검색 UI 1개 통과.
  앞의 최대 글씨/QR 통과와 합쳐 후속 변경 흐름을 모두 확인. 대기 또는 이름 조회 실패를
  앱 기능 성공으로 간주하지 않고 실제 화면 이동까지 검사했으며 미해결 검사 실패 없음.
  실패 런(review/handoff/send/verify)의 로그와 attachments는 보존했고,
  장시간 자동 simctl diagnose 자식만 중단함. Simulator 자체를 종료/삭제하지 않음.
- `apps/ios/docs/evidence/ui-prototype-selected/`: root 추가 요청에 따라 주요 PNG 10장과 README 보존.
  원본 시안과 정지 캡처를 직접 비교해 기본 글씨의 타일/CTA 가시성, 단일 제목,
  불투명 시트, 정확한 Asia/Seoul 시간표를 확인함. 큰 글씨는 스크롤과 화면 이동을 검증함.
- 최신 Release 무서명 빌드 통과(`handoff-release.log`), SwiftLint 0건(`handoff-lint.log`),
  구조 17개/6 suites 통과(`handoff-architecture.log`). 검사 규칙 disable 없음.
- generator 재생성 pbxproj/scheme 동일, 자산 4장 root 원본과 SHA256 동일,
  양 빌드 plist의 앱 ID/버전 유지 확인. `git diff --check` 통과.

## 과설계·일반 리뷰

ponytail-review: 미사용 이력 설명 필드/분기, 중복 ColorScheme 지정,
이미 배열인 filter 결과의 Array 래핑, init과 중복된 State 초기값을 제거함.
검사 목적으로 규칙을 비활성화하거나 mock repository/범용 일정 생성기를 추가하지 않음.
일정 fixture 5세션·화면 state·실제 반복 UI만 유지. 최종 추가 제거 후보 없음: Lean already. Ship.
정확성은 별도로 ID 보존 편집, 선택 정보만 노출, 상태 초기화, 0건/경계시간,
권한·서비스 API 부재, 기본/큰 글씨 동작을 검사함.

## 커밋·남은 범위

- 기존 서비스 단순화: `2bf32d9` (이전 task 완료).
- calendar 시안 A: `04a0f16`.
- 사진/QR 자산 및 출처: `357888e`.
- selected 전체 UI 구현·검사·문서: `f8701df` (root 통합 완료).
- 후속은 접근성 식별자 1줄·그 버튼의 실제 이동 검사·PNG/검증 기록만 포함.
  디자인/데이터/동작 변경 없음. 요청된 구현·검증 완료.

복구 태그 `backup/mobile-service-before-prototype-20261003` 보존.
push/통합/실기기 설치·서명과 세션 retain은 root 소유.
iPad/VoiceOver 실제 낭독/실제 외부 브라우저·공유 대상 전송은 이번 검증에 포함하지 않음.
