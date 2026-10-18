# 현재 작업과 결정

2026-10-04 확인. 현재 구현 범위는 [모바일 프로토타입](mobile-ui-prototype.md), 협업 규칙은 [AGENTS.md](../../AGENTS.md)를 따른다. 이 문서는 진행 상태만 기록한다.

## 2026-10-18 결정 — 명함 공유·Supabase 삭제

사용자 결정(이 세션): "명함 공유 기능 지워줘"에 범위 C(전 스택)를 확정했고, 이어 "supabase 관련도 모두 지워줘", "orca 워크트리에 열린 세션도 다 지워줘"를 요청했다. 어제 기록의 "NFC 공유는 보류(QR 유지)"는 이 결정으로 대체됐다.

- **지운 것:** API `POST /v1/cards/:id/shares`·`GET /v1/shares/:id`·`PUT /v1/wallet/shares/:id`·`PUT /v1/guest/shares/:id`와 `GET /v1/wallet`·`GET /v1/guest/cards`의 `shares`, CardShare·WalletShare·GuestShare 도메인; 웹 `/s/:shareId`·`/saved` 활동별 보기·AASA/assetlinks 라우트; nginx 저장소 사본의 공유 location; iOS·Android의 QR 표시·OS 공유·카메라/사진 스캔·유니버설 링크/App Links·공유 명함 화면·공유 저장·활동별 묶음(`DEARBY_WEB_ORIGIN`·카메라 권한·zxing·CameraX 포함); `supabase/`·`apps/catalog-worker`·CI `catalog-worker` job·Supabase 문서 3개.
- **남긴 것:** 명함 발행·고치기, 공개 명함 `GET /v1/cards/:id`와 웹 `/cards/:id`, 웹 비로그인 저장(`/v1/guest/cards*`·세션·홈 화면 잇기), `GET /v1/wallet`(기존 receipt 목록, 새로 받는 경로 없음), 패스키 `webcredentials`·AASA/assetlinks 정적 파일(wid.io.kr), 애플 로그인 `dearby://auth/apple`.
- **운영 반영은 하지 않았다:** 운영 DB 공유 테이블(`card_shares`·`wallet_shares`·`guest_card_shares`)은 남아 있고 삭제 마이그레이션은 별도 승인 PR. jOOQ 생성 코드도 테이블이 있는 동안 유지한다. 운영 nginx(`~/nginx`), Vercel 환경값(`DEARBY_APPLE_TEAM_ID` 등 4개), GitHub Actions 변수 `DEARBY_WEB_ORIGIN`, 이 Mac의 TS 워커 launchd(`com.dearby.catalog-subscription-worker`)·`~/.dearby-deploy` 사본은 사용자가 정리한다. 이미 배포된 QR·`/s/` 링크는 웹 배포 뒤 404가 된다.
- **UI 결정(메인):** QR 탭을 `내 명함` 탭으로 바꿔 명함 목록·만들기·고치기만 남겼다. 받은 명함 탭은 유지했지만 받을 경로가 없어 빈 상태가 기본이다. 탭 제거 여부는 사용자 결정이 필요하다.
- **테스트:** API 기존 테스트에서 공유 단계를 뺐다(CardFlowTest 2개는 철회·지갑 목록 검증으로 축소, GuestFlowTest·AccountDeletionFlowTest 공유 단계 삭제). 새 테스트 파일은 만들지 않았다.
- Orca 세션: `main`·`admin-agent`·`ux-research`·`api-agent` 워크트리의 터미널 4개를 닫았다(미커밋 변경 없음 확인). 다른 저장소의 세션과 워크트리 자체는 남겼다.

## 통합된 작업

- 2026-10-17(2): 직무(#230 서버 V18·시드 11/56개, 운영 `2a061187`; #231 어드민 웹 "직무" 메뉴 배포) · 첫 프로필 아코디언 퍼널·탭바 숨김·직무 태그(#232, TestFlight) · Supabase 마이그레이션 CI job 제거와 main 필수 체크 변경(#229). 사용자 결정: 직무 직접 입력 + 관리자 승인, 시작 분류 시드. NFC 공유는 보류(QR 유지).
- 2026-10-17: 웹 비로그인 저장 Kotlin API 복원(#225, 운영 `08e738f9`, DB V17, `GUEST_PROXY_SECRET`은 [앱 간 공유 환경값](env-shared-keys.md)) · 첫 프로필 토스식 단계 입력(#226) · 애플 로그인 기기 패스키 자동 연결(#227). TestFlight run 38993158350 업로드. 남은 일: Android QR "이 명함 고치기", 데이터 넣기 끝 삽입만, 실기기 흐름 확인, 미병합 원격 브랜치 9개 삭제(사용자).
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
- 2026-10-18 이후: 공유 삭제 PR 병합 → API·웹 배포 → 운영 nginx 공유 location 제거 → 공유 테이블 삭제 마이그레이션(별도 승인) → Vercel·Actions의 미사용 환경값 정리 → TS 워커 launchd 내리기(사용자).
- 사용자: Android Play 앱 서명 SHA-256, 대화에 노출된 메일 비밀번호 정리, 고정 IP(보류), 인증서 갱신 방식.

## 로컬 상태와 다음 작업

시작 시 이미 삭제되어 있던 `apps/ios/Dearby.xcodeproj/project.pbxproj`와 `apps/ios/Dearby.xcodeproj/xcshareddata/xcschemes/Dearby.xcscheme`은 사용자 변경으로 보존한다. 추적되지 않는 원본 시안 폴더도 유지한다. 이번 정리에 포함하거나 복구하지 않는다.

이 로컬 checkout에서 iOS를 새로 빌드하려면 프로젝트 파일 상태를 먼저 확인해야 한다. 프로젝트 생성 명령은 [iOS 실행 안내](../../apps/ios/README.md)에 있다. 기존 기기 설치·서명·운영 서버 확인은 이번 문서 정리에서 재실행하지 않는다.

문서 정리 변경을 검토·통합한 다음, 새 제품 요청이 있을 때 해당 플랫폼 실행 안내와 현재 코드를 기준으로 작업한다. 예시 화면의 완료를 실제 계정·캘린더·명함 전송 기능의 완료로 간주하지 않는다.

[이전 조율 기록](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/coordinator-current-task-and-decisions.md)은 9월 작업의 결정·검증 경위를 보존한다. 당시의 포트·세션·진행 상태를 현재 실행 지시로 사용하지 않는다.
