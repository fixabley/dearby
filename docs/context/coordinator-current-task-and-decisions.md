# 현재 작업과 결정

2026-10-04 확인. 현재 구현 범위는 [모바일 프로토타입](mobile-ui-prototype.md), 협업 규칙은 [AGENTS.md](../../AGENTS.md)를 따른다. 이 문서는 진행 상태만 기록한다.

## 2026-10-18 결정 — 명함 공유·Supabase 삭제

사용자 결정(이 세션): "명함 공유 기능 지워줘"에 범위 C(전 스택)를 확정했고, 이어 "supabase 관련도 모두 지워줘", "orca 워크트리에 열린 세션도 다 지워줘"를 요청했다. 어제 기록의 "NFC 공유는 보류(QR 유지)"는 이 결정으로 대체됐다.

- **지운 것:** API `POST /v1/cards/:id/shares`·`GET /v1/shares/:id`·`PUT /v1/wallet/shares/:id`·`PUT /v1/guest/shares/:id`와 `GET /v1/wallet`·`GET /v1/guest/cards`의 `shares`, CardShare·WalletShare·GuestShare 도메인; 웹 `/s/:shareId`·`/saved` 활동별 보기·AASA/assetlinks 라우트; nginx 저장소 사본의 공유 location; iOS·Android의 QR 표시·OS 공유·카메라/사진 스캔·유니버설 링크/App Links·공유 명함 화면·공유 저장·활동별 묶음(`DEARBY_WEB_ORIGIN`·카메라 권한·zxing·CameraX 포함); `supabase/`·`apps/catalog-worker`·CI `catalog-worker` job·Supabase 문서 3개.
- **남긴 것:** 명함 발행·고치기, 공개 명함 `GET /v1/cards/:id`와 웹 `/cards/:id`, 웹 비로그인 저장(`/v1/guest/cards*`·세션·홈 화면 잇기), `GET /v1/wallet`(기존 receipt 목록, 새로 받는 경로 없음), 패스키 `webcredentials`·AASA/assetlinks 정적 파일(wid.io.kr), 애플 로그인 `dearby://auth/apple`.
- **운영 반영 완료(2026-10-18):** nginx reload로 공유 location 제거(공개 `GET /v1/shares/*` 404), API·admin·batch 이미지를 main으로 재배포(1차 `c95d128e`, 2차 PR #235 병합 `a0f6433b`). Flyway V19 `drop share tables` 적용 성공(마이그레이션 19개), 운영 DB에 `card_shares`·`wallet_shares`·`guest_card_shares`가 없고 `business_cards`·`receipts`·`guest_cards`는 남아 있다. TS 워커 launchd와 plist, Actions 변수 `DEARBY_WEB_ORIGIN`, 공유 Git stash 4개, `~/.supabase`, 루트 `supabase/`도 지웠다. 배포 사본 폴더 이름 `api-kotlin-2a061187`은 그대로지만 내용은 `a0f6433b`다. 남은 사용자 확인: Vercel `dearby-web` 배포가 `c95d128e` 이후인지, Vercel 미사용 환경값 4개 삭제.
- **하단 탭바(사용자 요청, 같은 날):** 네이티브 기본 탭바를 쓴다. iOS는 직접 그린 버튼 행 대신 `TabView`+`Tab`(상세 진입·첫 프로필 퍼널에서는 `.toolbar(.hidden, for: .tabBar)`), Android는 Material 3 `NavigationBar`를 테마 기본값으로 쓰고 색·글자 크기·구분선 덧씌움을 뺐다.
- **UI 결정(메인):** QR 탭을 `내 명함` 탭으로 바꿔 명함 목록·만들기·고치기만 남겼다. 받은 명함 탭은 유지했지만 받을 경로가 없어 빈 상태가 기본이다. 탭 제거 여부는 사용자 결정이 필요하다.
- **테스트:** API 기존 테스트에서 공유 단계를 뺐다(CardFlowTest 2개는 철회·지갑 목록 검증으로 축소, GuestFlowTest·AccountDeletionFlowTest 공유 단계 삭제). 새 테스트 파일은 만들지 않았다.
- Orca 세션·워크트리·브랜치(같은 날 사용자 요청): `main`·`admin-agent`·`ux-research`·`api-agent` 워크트리의 터미널 4개를 닫고, `.claude/worktrees/` 아래 완료 워크트리 16개를 제거했다(모두 미커밋·미푸시 변경 없음). 브랜치는 병합된 로컬 129개·원격 14개, 원격 사본이 있는 로컬 10개, 로컬 전용 미병합 7개, PR 없는 미병합 원격 6개, 열린 PR #158·#169·#170의 브랜치 3개를 지웠다(PR은 브랜치 삭제로 닫힘). 남은 브랜치는 `main`과 `chore/drop-share-tables`(PR #235)뿐이다(`chore/remove-card-sharing`은 병합 후 삭제). 지운 미병합 브랜치의 마지막 커밋: feat/capstone-presentation `e9e36ca6`, feat/discovery-admin `ce856b07`, feat/ios-design-simplification `086fd94d`, fixabley/catalog-subscription-collector `d7e5da7b`, fixabley/dearby-android-design-simplification `da438461`, fixabley/ui-agent `ed7b272b`, ui/calendar-overlap `8beacff3`, ui/identity-avatar-order `8d648e95`, feat/mobile-device-calendar `a88c3c02`, feat/android-activities `c8cf82a1`, feat/ios-activities `10cd5dba`, fixabley/dearby-android `6ccf46fa`, fixabley/dearby-api `88f88950`, fixabley/dearby-ios `6f090faa`, fixabley/dearby-ios-architecture-tests `ee9c4e09`, ui/pretendard-flow-screens `31b52dea`(로컬 reflog·GitHub GC 전까지만 복구 가능). 루트 워크스페이스의 `supabase/`(로컬 CLI 상태 포함)와 `~/.supabase`도 지웠다.

## 2026-10-18 결정 — 대상자 조건(eligibility) 규격

사용자 지시: 수집된 활동의 대상자를 중첩 가능한 JSON으로 규격화, 범위는 `from`·`to`, 검색 가능, 필드 어휘는 한글 + 영문 쌍 저장, 카멜케이스 금지(snake_case). 계약 "대상자 조건(eligibility)". `activities.eligibility jsonb` + 검색용 `activity_eligibility_terms`, `GET /v1/activities`·`/v1/discovery/programs` 필터(영문 snake_case 키), 수집 지시문 추출, 어드민 JSON 편집, 세 플랫폼 활동 상세 "대상" 칩. 필터 UI는 다음 범위. 에이전트 팀 5개(API+batch·어드민·iOS·Android·웹).

## 2026-10-18 결정 — 구글 로그인

사용자 요청 "구글 로그인 추가". 애플 로그인의 Android 웹 방식(서버 코드 흐름, `dearby://auth/google` 콜백)을 iOS·Android 모두에 쓰고 SDK는 넣지 않는다. 계약 "구글 로그인". 처음 보는 계정은 애플과 같이 기기 패스키를 조용히 묻고 연결 또는 새 계정. refresh token은 저장하지 않는다. 운영 반영에는 사용자가 Google Cloud OAuth 클라이언트(웹 애플리케이션, 리디렉션 `https://api.dearby.wid.io.kr/v1/auth/google/callback`)를 만들어 `.env.prod`에 `GOOGLE_CLIENT_ID`·`GOOGLE_CLIENT_SECRET`·`GOOGLE_CALLBACK_URL`을 넣어야 한다. 값이 없으면 503이고 앱은 버튼을 숨긴다. 에이전트 팀(API·iOS·Android fork 3개).

## 2026-10-18 결정 — 발견 탭 프로그램 묶음

사용자 제안: 발견을 활동 카드 대신 프로그램 단위로 보여 주고 누르면 아코디언으로 열린 활동이 보이게. 메인이 제안한 규칙(열린 활동 없는 프로그램 숨김, 프로그램·조직·활동 이름 검색, 유형 칩은 활동에 적용, 열린 활동 1개면 펼침, 웹도 함께)을 사용자가 그대로 승인했다. 계약 "발견 프로그램 묶음". API `GET /v1/programs`(ProgramSummary) 신설, 펼침은 기존 `GET /v1/activities?programId`. 에이전트 팀(API·iOS·Android·웹 fork 4개)으로 병렬 구현.

## 2026-10-18 결정 — 명함 꾸미기 편집기·웹 버전 삭제

실기기 확인 뒤 사용자: "실시간 편집 이런 퀄리티면 그냥 빼는 게 좋을 것 같아" → 선택지 중 "꾸미기 제거 + 웹 버전도 제거"를 확정했다. 명함 제작은 이름·공개 연락처·이력 선택과 읽기 전용 미리보기만 남고, `template`은 앱 내장 기본값을 보낸다. 같은 날 만든 웹 버전(#239, V20)은 API·웹·앱에서 되돌리고 V21로 열을 지운다(삭제 시점 운영 DB에 웹 버전을 쓴 명함 1장이 있었고 그 웹 본문은 사라진다. 사용자 테스트 명함). 사용자 요청으로 에이전트 팀(API·웹·iOS·Android fork 4개) 병렬 작업.

## 2026-10-18 결정 — 이력 입력 퍼널 분리

실기기 확인 중 사용자 지적: 조직(직장) 이력 퍼널이 프로그램(활동처럼 보임) 단계를 묻고, 활동 이력 퍼널이 조직 단계부터 묻는다. 결정: 조직 퍼널은 조직 → 역할 → 기간 → 설명, 활동 퍼널은 활동(전체 검색, 소속 조직·프로그램 표시) → 역할 → 기간 → 설명만 묻는다. 활동 정보에서 고른 활동은 서버가 `activityId`로 소속 조직·프로그램을 채우고, 직접 입력한 활동만 뒤에 조직(필수)·프로그램(선택)을 묻는다. 계약 "이력" 2026-10-18 항목. 사용자 요청으로 API·iOS·Android 담당 fork 서브에이전트 3개를 동시에 돌렸다(Claude 팀).

## 2026-10-18 결정 — 명함 웹 버전 (같은 날 삭제, 위 항목)

사용자 요청 "명함 제작할 때 모바일 사이즈와 웹 사이즈 버전을 따로, 프레이머 브레이크포인트처럼". 검토 결과 마크다운 템플릿에는 레이아웃이 없어 폭별 레이아웃 브레이크포인트는 불가능하고, 사용자가 **내용 버전 2개**(모바일 기본 + 선택 웹) 안을 골랐다. 웹 버전이 없는 명함은 넓은 화면에서도 모바일 버전을 그대로 보인다. 계약은 [native-v1 "웹 버전"](../../shared/contracts/native-v1.md#웹-버전--2026-10-18-사용자-결정). 구현: API `webTemplate`·`webIntroduction`(V20 열 2개), 웹 `/cards/:id` 640px 초과에서 웹 버전(CSS만), iOS·Android 명함 제작의 `웹 버전 따로 쓰기` 스위치와 `모바일 | 웹` 세그먼트(플랫폼 기본 컨트롤). 앱은 항상 모바일 버전을 보인다. PR #239 병합(`2c2e0d6c`) 뒤 같은 날 운영 반영: API 재배포로 Flyway V20 적용(20개 검증, `business_cards.web_template`·`web_introduction` 확인), Vercel이 main 푸시로 웹 배포 완료. 실기기 화면 확인은 미실행. 사용자 제안 "웹뷰 편집기(터치·드래그)"는 캔버스형 편집이 목표일 때만 레이아웃 모델과 함께 검토하기로 하고 보류했다.

## 통합된 작업

- 2026-10-17(2): 직무(#230 서버 V18·시드 11/56개, 운영 `2a061187`; #231 어드민 웹 "직무" 메뉴 배포) · 첫 프로필 아코디언 퍼널·탭바 숨김·직무 태그(#232, TestFlight) · Supabase 마이그레이션 CI job 제거와 main 필수 체크 변경(#229). 사용자 결정: 직무 직접 입력 + 관리자 승인, 시작 분류 시드. NFC 공유는 보류(QR 유지).
- 2026-10-17: 웹 비로그인 저장 Kotlin API 복원(#225, 운영 `08e738f9`, DB V17, `GUEST_PROXY_SECRET`은 [앱 간 공유 환경값](env-shared-keys.md)) · 첫 프로필 토스식 단계 입력(#226) · 애플 로그인 기기 패스키 자동 연결(#227). TestFlight run 38993158350 업로드. 남은 일: Android QR "이 명함 고치기", 데이터 넣기 끝 삽입만, 실기기 흐름 확인. 미병합 원격 브랜치 삭제는 2026-10-18 완료.
- 다섯 탭과 selected 시안의 예시 흐름을 [PR72](https://github.com/fixabley/dearby/pull/72)로 통합했다.
- 대체된 화면 증거와 이전 모바일 연동 도구 정리를 [PR73](https://github.com/fixabley/dearby/pull/73)로 통합했다. 이번 문서 정리 시작 기준은 `ec9dfec98c13661ab332c2567bfba1bb35f1329a`다.
- 완료한 하위 워크트리·작업 복제 폴더와 해당 복구 전용 백업을 제거했다. 상세 범위는 [Git·워크트리 기록](orca-sessions-and-worktrees.md)에 있다.
- 문서 진입 경로를 현재 모바일·보존한 서비스·과거 근거로 정리했다. 검사와 스킬별 결과는 [문서 정리 기록](documentation-maintenance.md)에 남긴다.

## 담당 세션 — 2026-10-06

[에이전트 운영](agent-roster.md)에 따라 `3a3e40f`(PR76) 기준으로 Orca worktree `ui-agent`·`flow-agent`·`collector-agent`·`admin-agent`를 만들고 각각 Claude 세션을 시작했다. 첫 지시는 담당 영역 파악·기준선 검사·계획 보고 후 대기이며 구현은 아직 배정하지 않았다. PR76에서 수집 워커 소스를 main으로 옮겼다.

## 2026-10-06 목표와 결정 (메인 조율 세션)

목표: 활동 신청이 쉬워야 하고, 네트워킹 때 명함 제작·교환이 간결해야 한다. Mobbin을 참고한다(조사: `docs/research/activity-and-qr-friction-2026-10-06.md`).

- **모바일 실제 연결 범위:** 발견(실제 활동 정보)·로그인·명함 발행·QR 공유·받기는 실제 서비스다(QR 공유·받기는 2026-10-18 삭제). 계약은 `shared/contracts/native-v1.md` "모바일 실제 연결 경계"다. 캘린더 겹침·참여 확정 표시·서버 간 명함 전달은 예시로 남는다.
- **비로그인 사용자:** 앱 미설치·미로그인 사용자다. 공개 명함 링크 `/cards/:id`가 기본 브라우저에서 열린다(2026-10-18까지는 QR의 `/s/<shareId>`). 서버가 만료 없는 게스트 세션 ID 쿠키를 발급한다. iOS 홈 화면 웹 앱은 Safari와 쿠키가 분리되므로(iOS 26.5 시뮬레이터 확인) 1회용 코드로 세션을 잇는다.
- **연결:** 모든 연결은 도메인 기반이고 `DEARBY_API_ORIGIN`·`DEARBY_WEB_ORIGIN` 환경값으로 지정한다. 운영 값은 저장소에 두지 않고 Vercel 환경변수·GitHub Actions 변수·서버의 저장소 밖 env 파일에만 둔다.
- **도메인:** API는 `api.dearby.wid.io.kr`이고 Vercel `DEARBY_API_ORIGIN`도 이 값이다. 인증서(`wid.io.kr`, `api.dearby.wid.io.kr`)는 2027-01-04에 만료되며 수동 dns-01이라 자동 갱신이 안 된다. 외부 도달은 이중 NAT를 해소해 확인했다(#65).
- **유니버설 링크:** Team ID `4V9FVN3VQF`, 경로 `/s/*`이고 AASA를 운영 웹에 배포했다. Android 서명 SHA-256은 미정이다(#90). 2026-10-18 공유 삭제로 `/s/*` 유니버설 링크·App Links는 없어졌다(패스키 `webcredentials`는 유지).
- **인증 메일:** 네이버 SMTP(`fixabley@naver.com`)이고 실제 수신을 확인했다(#89 닫음). 실제 IP를 볼 수 없는 구조(OrbStack이 출발 주소를 바꿈)라 인증 제한은 이메일 단위와 서비스 전체 상한으로 한다(#131).
- **활동 정보:** 공개는 `published` 활동만이다. 발견 노출은 24시간 공식 확인 + 모집 중 + 마감 전이다. 모집 상태는 기간으로 추론한다(#129). 게시 활동은 공식 페이지의 기준 구절로 하루 1회 자동 재확인한다(#123·#124·#126, Codex 미사용). 원문 한도는 3MB다.
- **CI:** iOS 검사는 iOS 입력이 바뀐 PR에서만 돌린다(#130). main 보호의 strict(최신 반영 필수)는 껐다.
- **migration 순서:** #84(000000) → #123(005000) → #109(010000) → #83(020000·030000). 병합하면 운영 DB에 적용된다.
- **배포:** 웹·어드민 Vercel 프로젝트는 main push로 자동 배포되지 않는다(GitHub 배포 기록 없음). 메인이 CLI로 배포한다.

### 남은 일

- 운영 반영: API 배포(#84·#129·#131 포함) → 회원 경로 nginx 공개(#127) → 세션 잇기(#109·#110·#111) → 조직 계층(#85 배포 뒤 #83) → 어드민 배포(#82·#120·#126·#128). 수집 워커 재배포는 2026-10-18 워커 삭제로 없어졌다.
- 모바일: 로그인 시트·명함 발행(5b). QR 공유·스캔·wallet 저장은 2026-10-18 삭제.
- 2026-10-18 운영 반영은 위 "운영 반영 완료"대로 끝났다. 남은 것은 Vercel 배포 확인과 Vercel 환경값 4개 삭제(사용자), 받은 명함 탭 제거 여부 결정.
- 사용자: Android Play 앱 서명 SHA-256, 대화에 노출된 메일 비밀번호 정리, 고정 IP(보류), 인증서 갱신 방식.

## 로컬 상태와 다음 작업

시작 시 이미 삭제되어 있던 `apps/ios/Dearby.xcodeproj/project.pbxproj`와 `apps/ios/Dearby.xcodeproj/xcshareddata/xcschemes/Dearby.xcscheme`은 사용자 변경으로 보존한다. 추적되지 않는 원본 시안 폴더도 유지한다. 이번 정리에 포함하거나 복구하지 않는다.

이 로컬 checkout에서 iOS를 새로 빌드하려면 프로젝트 파일 상태를 먼저 확인해야 한다. 프로젝트 생성 명령은 [iOS 실행 안내](../../apps/ios/README.md)에 있다. 기존 기기 설치·서명·운영 서버 확인은 이번 문서 정리에서 재실행하지 않는다.

문서 정리 변경을 검토·통합한 다음, 새 제품 요청이 있을 때 해당 플랫폼 실행 안내와 현재 코드를 기준으로 작업한다. 예시 화면의 완료를 실제 계정·캘린더·명함 전송 기능의 완료로 간주하지 않는다.

[이전 조율 기록](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/coordinator-current-task-and-decisions.md)은 9월 작업의 결정·검증 경위를 보존한다. 당시의 포트·세션·진행 상태를 현재 실행 지시로 사용하지 않는다.
