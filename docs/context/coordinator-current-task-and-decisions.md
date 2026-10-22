# 현재 작업과 결정

현재 구현 범위와 협업 규칙은 [AGENTS.md](../../AGENTS.md), 계약은 [native-v1.md](../../shared/contracts/native-v1.md)·[admin-v1.md](../../shared/contracts/admin-v1.md)를 따른다. 이 문서는 진행 상태와 결정 로그만 기록한다. 2026-10-19 정리: 2026-10-18·19 결정을 주제별로 합쳤고 대체된 내용은 각 항목의 "변경 이력"과 Git 고정 링크로 남긴다.

## 현재 상태 — 2026-10-19 확인

- **운영 API**(`api.dearby.wid.io.kr`, `~/.dearby-deploy/api-kotlin-2a061187` compose: api·admin·batch·postgres): main `65834aaf`(PR #271)까지 배포(이후 #272·#273은 앱·문서만), Flyway **V37**까지 적용(2026-10-19 밤). 배포 절차: rsync(소스, `data`·`.env.prod`·`build`·`.gradle` 제외) → `docker compose -f compose.prod.yaml --env-file .env.prod up -d --build api admin batch`. 마이그레이션은 컨테이너 기동 때 Flyway가 적용한다.
- **웹** `dearby.wid.io.kr`: Vercel GitHub 연동으로 main 푸시마다 자동 배포. **어드민** `admin.dearby.wid.io.kr`: Vercel 프로젝트 `dearby-admin`은 연동이 없어 `git archive origin/main apps/admin` 사본에서 `npx vercel link --project dearby-admin && npx vercel deploy --prod`로 배포(2026-10-19 PR #270까지 반영).
- **기기**: iPhone(UDID `00008120-0011258C2107C01E`, USB)과 Android(`R3CY10GNW9F`, 패키지 `io.wid.dearby`) 모두 2026-10-19 저녁 main(PR #272 포함) 빌드 설치. 시뮬레이터·에뮬레이터는 쓰지 않는다.
- **운영 데이터**: 활동 전부 초안(DRAFT)이라 공개 활동 목록은 비어 있고, 발견 `openOnly=false`로 3개 프로그램이 보인다. 2026-10-19 재수집 결과 프로그램 16·17·19에서 초안 6개 추가(새 지시문·종류·날짜 예시 적용). 명함 3개는 모두 철회됨(사용자 삭제 확인).
- **로그인**: 패스키·Apple 운영 중. Google은 네이티브 SDK로 전환됐지만 `.env.prod`의 `GOOGLE_CLIENT_IDS`와 `~/.dearby-deploy/mobile.env`가 비어 503·버튼 숨김.
- **배치**: 매일 00:00 수집, 04:00 명함 회수(Asia/Seoul). `run --rm batch --collect-now/--recall-now`로 띄운 일회성 컨테이너는 작업 뒤 종료되지 않고 스케줄러와 함께 남으므로 수동으로 멈춘다(2026-10-19 두 개 정리).

## 2026-10-19 저녁 결정 — 기기 캘린더 실제 연동·태그·브라우저 렌더링

사용자 결정(가치·기술 문서 작성 중 발견한 불일치 보고 뒤): "기기 캘린더 연동 구현해주세요. 태그 구현해주세요. 종류 일괄 적용은 그냥 적용. 일회성 batch 컨테이너는 스스로 종료, DAN 주소 수정." 이어 우와콘 수집 실패 원인(자바스크립트로 그리는 단일 페이지 앱, 서버 HTML 글자 121자) 보고 뒤 "브라우저 렌더링 경로 추가, 설정에 '렌더링 필요' 표시" 승인.
- 캘린더: iOS EventKit(`requestFullAccessToEvents`)·Android `CalendarContract.Instances`로 실제 비교. 종일 일정 제외, 제목은 화면에만, 저장·전송 없음, 권한 안내 화면 뒤 요청. 고정 예시 비교는 iOS 미리보기에만 남음.
- 태그: 계약 "태그(얻을 수 있는 것)"대로 API(V34 사전·연결·q 매칭·tagIds 필터·수집 추출)·어드민(검색+직접 입력 선택기, 태그 사전 페이지)·iOS·Android·웹(칩·필터 줄). 태그 필터는 펼친 활동 요청에도 함께 보낸다(세 플랫폼 통일).
- 렌더링: `program_collection_settings.render_pages`(V35), Playwright Chromium, 같은 경계. 일회성 `--collect-now`·`--recall-now`는 작업 뒤 JVM 종료.
- DAN 수집 주소는 운영 DB에서 `https://dan.naver.com/{yy}`로 바꿈(`/26`은 아직 404).

## 2026-10-19 결정 — iOS 번들 ID `io.wid.dearby`

사용자: "iOS 번들 아이디 io.wid.dearby로 변경해줘. 애플 식별자에도 그렇게 했어." Debug·Release 모두 `io.wid.dearby`(이전: Debug `com.dearby.dearby`, Release `io.wid.dearby`). AASA(`infra/nginx/html/.well-known/apple-app-site-association`)는 이미 두 appID를 모두 담고 있고, 서버 `APPLE_CLIENT_IDS`에도 두 번들이 모두 들어 있어 전환 중 로그인이 끊기지 않는다. Google iOS 클라이언트의 번들(`io.wid.dearby`)과도 일치. 기기의 옛 번들 앱은 별도 앱으로 남는다(데이터 공유 없음). 기기 설치·실행 명령의 번들 ID는 `io.wid.dearby`.

## 2026-10-19 밤 결정 — 로고·동의·스토어 준비·렌더링 수정

- **로고**: 악수를 추상화한 다섯 시안 중 사용자가 **B(맞물린 두 고리)** 선택(PR #283). `shared/assets/brand/`(symbol·wordmark·icon-source·README), iOS AppIcon·DearbySymbol·LaunchLogo, Android 적응형 아이콘·`dearby_symbol`·스플래시, 웹 파비콘·apple-icon·로고 교체. 사용자 요청으로 시작 화면·로그인 시트에는 심볼 + "Dearby" 이름(`DearbyBrand`), 일반 화면 머리의 로고는 제거.
- **개인정보 동의**(PR #284): 프로필 첫 저장 전 필수 동의 화면(두 체크 + /privacy·/terms 인앱 브라우저), V37 `user_consents`, 쓰기 403 `CONSENT_REQUIRED`, 버전 2026-10-19. 캘린더 안내 화면에는 방침 링크만(체크 강제 없음 — 기기 안 비교라 수집 동의 대상 아님).
- **스토어 준비**: TestFlight는 CI가 매일 자동 업로드 중. Android 업로드 키 `~/.dearby-deploy/android-upload.jks`(+`android-signing.env`, 백업 필요), 서명 설정(PR #281), 서명된 AAB `~/.dearby-deploy/store/dearby-0.1.0-1.aab`(콘솔 수동 업로드). 웹 `/privacy`·`/terms`(PR #282)와 `docs/store/listing-2026-10-19.md`. 남은 사용자 입력: 운영자 이름·연락 이메일·서버 소재지, ASC API 키·Play 서비스 계정 JSON(API 제출용), iOS 스크린샷, 콘솔 설문.
- **iOS 번들 ID** `io.wid.dearby`(Debug 포함, PR #277). Google 값은 `~/.dearby-deploy`의 plist·JSON에서 `.env.prod`(`GOOGLE_CLIENT_IDS`)·`mobile.env`로 입력, API `/auth/google/nonce` 200.
- **브라우저 렌더링**(PR #276·#285): `render_pages`(V35) + Playwright Chromium. 실행 시 브라우저 재설치 시도로 멈추던 문제는 `PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1`로 해결. 우와콘(설정에서 렌더링 켬) 수집 성공: "우아콘 2026"(컨퍼런스, 10/28, 태그 개발지식·네트워킹·발표) 초안. batch 이미지 3.1GB(Chromium 1.06GB + 빌드 단계 Node 층 — 줄일 여지).
- **일회성 `--collect-now`**: 비동기 실행이 끝날 때까지 기다린 뒤 종료(PR #280 회귀 수정). 수집 기록은 프로그램당 하루 한 건이라 같은 날 다시 돌리려면 그날 행을 지워야 한다.
- **기타**: 세부 일정 편집기(PR #280, V36), 토글 이름 `마감 숨기기`(PR #286), 3년 창·지난 활동 항상 표시(PR #279), 기기 캘린더 실제 연동(PR #275), 태그(PR #278, V34), 프로그램 기본 종류(PR #270, V33).

## 2026-10-19 밤 결정 — 발견 카드 리뉴얼·활동 장소·네이티브 디자인 규칙

사용자 요청: "활동탐색 UI 리뉴얼… 안드로이드 One UI 최신 **알림 UI** 애니메이션 참고. 로고 좌측, 프로그램 이름 상단, 하위 활동 개수 우측, 태그는 아래. 누르면 자연스럽게 펼쳐지고, 펼친 뒤 숫자 자리에 화살표 버튼. 활동 카드는 로고 없이 활동명·모집 일정·활동 일정·활동 장소·모집 상태(우상단)." HTML 시안 승인("매우 좋아요") 뒤 추가: 지나지 않은 모집 기간 강조, 장소를 어드민에서 지오코딩(주소 검색 → 좌표)으로 입력(ICS 전환 때 만든 `schedules.address_line1/2·lat·lng` 재사용).
- 계약: native-v1 "발견 카드 리뉴얼 — 2026-10-19"·"활동 장소 — 2026-10-19", admin-v1 "활동 장소와 지오코딩". `Activity.location`(문구)·`place {name,address,lat,lng}|null`. `GET /v1/admin/geocode?q=`(ADMIN, 2~100자, 최대 10건, `source` kakao|nominatim). 어드민 PATCH/POST `place`(생략 유지·null 지움·객체 교체), `curated_fields`에 `place`.
- 서버: place는 ACTIVITY 슬롯 schedules 행에 저장(없으면 생성, sequence 증가, `curated` 안 건드림). 키는 **admin 컨테이너**의 `KAKAO_REST_API_KEY`(`.env.prod`에 입력 완료, 카카오맵 서비스 사용자가 켬, 키워드 검색 200 확인). 비어 있으면 Nominatim. 수집기가 curated place를 보존하는 경로는 컴파일·배치 테스트만 통과했고 전용 테스트는 없다(미검증 기록).
- 클라이언트: 웹·iOS·Android 모두 시안 구조(아바타 44 → 이름·조직·태그, 우측 원형 배지 ↔ 펼침 화살표, 마감/시작 메타, 활동 카드 상태 칩 + 모집·활동·장소 줄, 지나지 않은 모집은 민트 바탕 청록 굵은 값). 상세 화면 장소 섹션은 iOS `maps://`, Android `geo:` 인텐트, 웹 지도 링크.
- **새 규칙(AGENTS.md)**: 사용자 지시 "모바일은 플랫폼 별 네이티브 디자인을 지향해줘. 비슷한 역할이 있으면." 같은 역할의 네이티브 컴포넌트가 있으면 시안의 커스텀 표현 대신 그것을 쓴다. iOS는 SwiftUI 기본 컴포넌트(bordered/borderedProminent 버튼, `LabeledContent`, 시스템 grouped 색, 시스템 스프링, SF Symbols), Android는 Material 3·One UI 관례. 이번 카드에 바로 적용.
- 남은 일: 프로그램 로고 필드(`programs.logo_url` + 어드민 업로드)는 아직 없어 아바타는 이름 첫 글자.

## 2026-10-19 밤 결정 — 프로그램 로고·태그 칩 줄 제거·탭 제목/배경 통일·색 체계·다크 모드

PR #289(발견 카드 리뉴얼·장소·지오코딩) 배포 직후 사용자 요청 여섯 건. 계약은 native-v1 "프로그램 로고·태그 칩 줄 제거·탭 제목 통일"(색 체계·다크 모드 bullet 포함), admin-v1 "프로그램 로고".
- **프로그램 로고**: "로고도 어드민에서 추가할 수 있게." `program_logos`(V38, bytea — 인증자료와 같은 방식), 어드민 PUT/DELETE/GET `/v1/admin/programs/:id/logo`, 공개 `GET /v1/programs/:id/logo`(1년 캐시, `?v=` 캐시 깨기), `ProgramSummary.logoUrl`. 세 클라이언트 카드 로고 자리에 표시(없으면 첫 글자 아바타).
- **태그 칩 줄 제거**: "태그는 직접 보여주지 말고 검색 조건으로" → 카드가 아니라 **검색창 아래 칩 줄**을 말한 것(사용자 정정). 칩 줄·`tagIds` 전송만 없애고 `q` 태그 매칭·카드 칩·API 파라미터는 유지.
- **탭 제목·머리 여백**: "위쪽 탭바 여백이 너무 많아 활동 둘러보기가 탭바 위치로", "좌상단 메뉴 이름 통일성 없음, 내 명함 기준". 다섯 탭 모두 내 명함의 제목 줄(굵은 제목, 좌우 20, 안전 영역 아래 8)을 공통 컴포넌트로 그린다(iOS `PageTitle` 신설, Android `ScreenHeader`).
- **배경·색 체계**: "배경색도 통일성이 없어, 테마에 맞춰" + "프라이머리·포인트 색이 아이폰 테마 색과 조합이 안 좋다, 어떻게?" → 추천안 A(중립색은 시스템에 맡기고 청록은 액센트 하나) 승인 "적용해줘". iOS 시스템 grouped 색·`.primary/.secondary/.separator`, 민트는 `teal.opacity(0.12)`; Android 청록 시드 M3 color scheme, 상수 Mint·Soft·Line·Quiet·Ink 제거. 선택지 B(청록 색 자체 변경)·C(다이내믹 컬러)는 로고 변경·브랜드 상실로 비추천.
- **다크 모드**: "다크모드도 호환되게." 라이트 강제 제거, 시스템 따름, 청록 다크 변형 #3FB3B4, 웹도 `prefers-color-scheme` 토큰.
- **활동 상세 이미지 제거**: "UI는 만족스러워. 활동 상세 페이지에 이미지 섹션 없애줘도 좋을 것 같아." 세 플랫폼 상세 머리 이미지 제거, `imageUrl`은 API·모델에 유지(iOS·Android 내 활동 행은 계속 쓴다).
- 구현 결정(조율): Android 민트는 primaryContainer를 연하게 덮어씀(#D9F2F1/#1D4140), 구분선은 `surfaceContainerHighest`, 배경은 `surfaceContainerLow`+흰 카드(iOS grouped와 같은 구조), 벨은 발견 탭에만. iOS는 토큰 이름을 유지한 채 시스템 색 별칭으로 바꾸고 `onTeal`(다크에서 어두운 글자) 추가. 웹 머리는 심볼 SVG(`currentColor`) + Pretendard Bold "Dearby"(승인 워드마크 구성), theme-color 흰/#181f21, `--teal` #007F80로 정정, `logo-teal.png` 삭제. 디자인 계약 문서(`docs/design/native-visual-contract.md`)의 옛 워드마크·흰 배경 규칙을 현행화.
- 진행: 워크트리 `logo`(브랜치 `feat/program-logo-compact-header`)에서 API·어드민·iOS·Android·웹 에이전트 동시 작업 → PR → 배포(API V38, 어드민 Vercel, 기기 설치).

## 2026-10-19 밤 운영 장애 두 건 (PR #291)

- **활동 편집 저장 500** — 사용자 "어드민에서 프로그램 편집이 안돼". 로그: `PATCH /v1/admin/activities/:id` → `schedules_check1` 위반. V2 체크가 활동 기간(ACTIVITY 슬롯)의 시각 일정을 금지했는데 어드민 "시각 지정" 스위치가 그것을 보냈다. 수정: V39로 체크 제거, 도메인 `Activity` init guard 제거, 어드민 테스트에 시각 활동 기간 케이스. 공개 조회는 `activityStart = coalesce(start_at, start_date 자정)`이라 변경 없음.
- **동의 저장 400 "동의 문서 버전이 현재 버전과 다릅니다"** — 로그 14건 `Consent version mismatch: 2026-10-19/2026-10-19`. 원인: `compose.prod.yaml`이 `CONSENT_*_VERSION: ${…:-}`로 **빈 문자열**을 넣어 application.yaml 기본값(2026-10-19)을 덮어 서버 버전이 ""가 됐다. 수정: compose 기본값을 `2026-10-19`로. 운영은 compose만 바꿔 api·admin 컨테이너를 재생성해 즉시 해소(13:59 KST 확인 `CONSENT=2026-10-19/2026-10-19`). 교훈: `${VAR:-}`는 "없으면 비움"이라 Spring의 `${ENV:기본}`과 겹치면 기본값이 죽는다 — 기본값은 한 곳(compose)에만 둔다.
- **발견 탭 스크롤 동작**(고정 제목·접히는 검색 영역·풀투 리프레시·맨 위로 버튼) 계약을 같은 PR에 담았고 구현은 워크트리 `scroll`(iOS·Android 에이전트)에서 별도 PR.

## 2026-10-19 밤 결정 — 발견 스크롤·폼·일정 표시·설정·레이아웃 묶음 (PR #293·#294, 후속 폴리시·설정 PR)

- **배포·설치**: PR #289(카드 리뉴얼·장소)·#290(로고·헤더·색 체계·다크)·#291(V39 활동 기간 시각·동의 compose)·#292(applicationUrl)·#293(폼)·#294(스크롤·신청 확인·일정 표시) 모두 main. 운영 API는 #292까지 배포(V39 적용, `applicationUrl` 노출 확인), 어드민 웹은 #290까지. 두 기기 모두 #294·#293 포함 main 빌드 설치(2026-10-19 23:5x KST). 다크 모드·헤더·폼·스크롤 실기기 화면 확인은 사용자 몫으로 남김(시뮬레이터 미사용).
- **발견 탭 스크롤**(계약 "발견 탭 스크롤 동작"): 제목·알림 고정, 검색 영역 enter-always 접힘, 풀투 리프레시, 맨 위로(iOS는 사용자 요청으로 시스템 원형 `borderedProminent` 버튼, Android `SmallFloatingActionButton`), 새로 고침 실패 시 목록 유지. 검색창은 시스템 검색 모양(iOS 44, Android `SearchBarDefaults.InputField`).
- **폼은 플랫폼 기본**: iOS `Form` inset grouped + 시스템 필드/버튼, `DearbyInlineField`·`DearbyButtonStyle` 삭제; Android `OutlinedTextField` 기본·`ListItem`·기본 `Button`, `Controls.kt` 삭제, 접근성 라벨만. 키보드는 필드 밖 탭·스크롤로 내림(iOS `dearbyDismissKeyboard`, Android `dismissKeyboardOnTap` + 셸 `imePadding`·`adjustResize`).
- **신청 확인 완화**: 모집 마감 외 모두 묻고 CTA는 `applicationUrl ?? officialUrl`. 마감 판정은 모집 마감 또는(모집 정보 없을 때) 활동 끝/시작이 지남.
- **일정 표시 우선순위**(사용자 지적): 구조화 날짜 > 수집 원문. 세 플랫폼 공통 형식(같은 날 접기, 연도는 올해와 다른 날짜에만, 끝만 있으면 모집 `마감`·활동 `까지`). Android 포매터 통합, iOS `ActivityStageModel.text`.
- **후속 묶음(워크트리 `polish`, 진행 중)**: 청록 채움 면 위 흰 글자(`tealFill`), 비스크롤 화면 중앙 배치·CTA·`CenteredMessage`·카드 최소화, 아이콘 통일(토스 참고, 의미별 고정 이름), 다섯 탭 알림+설정 버튼, 설정 화면(계정·연결된 로그인·패스키·정보). 설정 API(`GET /v1/me`, identities·passkeys 목록/해제/이름/삭제, 409 `LAST_SIGN_IN_METHOD`)는 워크트리 `settings`.
- **대기 중 사용자 결정**: 이력에 "신청만 한 활동"(역할 선택, 일정 기반 상태, 명함 공개 범위, 내 활동과의 연결) — 제안 6개 항목 중 1·4·5 확정 필요.

## 남은 일·사용자 결정 대기

- **구현 예정(설계·계약 완료)**: 태그(native-v1 "태그", 어휘 사전·검색·필터·어드민 검색+직접 입력) — API·어드민·iOS·Android·웹.
- **사용자 결정 대기**: 프로그램 상세 화면(이전 활동 한 줄 목록 vs 종류 묶음; "지난 활동"이 펼침 안에 들어가 핵심은 해소), 내 명함 용도별 프리셋, 일괄 적용 때 운영자가 비운 종류를 지킬지, 일회성 batch 컨테이너가 작업 뒤 스스로 종료하도록 고칠지, SSAFY 수집 설정, Vercel 미사용 환경값 4개 삭제, Android 출시 서명(SHA-1·SHA-256), Google 로고 자산.
- **사용자 작업**: Google Cloud OAuth 클라이언트 3개 생성 뒤 `.env.prod`(`GOOGLE_CLIENT_IDS`)·`~/.dearby-deploy/mobile.env` 입력 → API 재기동·기기 재설치.  인증서(`wid.io.kr`, `api.dearby.wid.io.kr`) 2027-01-04 만료, 수동 dns-01.
- **운영 메모**: 운영자 지정 해제 API 없음. 비로그인 저장자는 맞교환할 수 없어 30일 뒤 회수된다(의도).

## 결정 로그 — 2026-10-18·19

주제별로 합쳤고 날짜순이 아니다. 세부 규칙은 계약의 해당 절, 대체된 내용은 각 절의 "변경 이력".

### 명함 공유 삭제 → 명함 교환 (2026-10-18 → 2026-10-19)

- 2026-10-18 사용자: "명함 공유 기능 지워줘"(범위 C 전 스택), "supabase 관련도 모두 지워줘", "orca 워크트리에 열린 세션도 다 지워줘". **지운 것:** API `POST /v1/cards/:id/shares`·`GET /v1/shares/:id`·`PUT /v1/wallet/shares/:id`·`PUT /v1/guest/shares/:id`와 `GET /v1/wallet`·`GET /v1/guest/cards`의 `shares`, CardShare·WalletShare·GuestShare 도메인; 웹 `/s/:shareId`·`/saved` 활동별 보기·AASA/assetlinks 라우트; nginx 공유 location; iOS·Android의 QR 표시·OS 공유·카메라/사진 스캔·유니버설 링크/App Links·공유 명함 화면·공유 저장·활동별 묶음(`DEARBY_WEB_ORIGIN`·카메라 권한·zxing·CameraX 포함); `supabase/`·`apps/catalog-worker`·CI `catalog-worker` job·Supabase 문서 3개. **남긴 것:** 명함 발행·고치기, 공개 명함 `GET /v1/cards/:id`와 웹 `/cards/:id`, 웹 비로그인 저장, `GET /v1/wallet`, 패스키 `webcredentials`·AASA/assetlinks 정적 파일(wid.io.kr), 애플 로그인 `dearby://auth/apple`. **운영 반영:** nginx reload, API·admin·batch 재배포(`c95d128e`, PR #235 `a0f6433b`), Flyway V19 `drop share tables`(운영 DB에 `card_shares`·`wallet_shares`·`guest_card_shares` 없음), TS 워커 launchd·plist, Actions 변수 `DEARBY_WEB_ORIGIN`, Git stash 4개, `~/.supabase`, 루트 `supabase/` 삭제. 배포 사본 폴더 이름 `api-kotlin-2a061187`은 그대로. 테스트는 기존 클래스에서 공유 단계만 뺐다(새 테스트 파일 없음).
- 같은 날 **하단 탭바**는 네이티브 기본(iOS `TabView`+`Tab`, 상세·첫 프로필 퍼널에서 `.toolbar(.hidden, for: .tabBar)`; Android Material 3 `NavigationBar` 기본값), QR 탭은 `내 명함` 탭으로. 받은 명함 탭은 유지(2026-10-19 명함 교환으로 받을 경로가 생겨 유지 확정).
- Orca 세션·워크트리·브랜치 정리(2026-10-18): `main`·`admin-agent`·`ux-research`·`api-agent` 터미널 4개 닫음, `.claude/worktrees/` 완료 워크트리 16개 제거(미커밋·미푸시 없음). 브랜치는 병합된 로컬 129·원격 14, 원격 사본 있는 로컬 10, 로컬 전용 미병합 7, PR 없는 미병합 원격 6, 열린 PR #158·#169·#170의 브랜치 3개 삭제. 지운 미병합 브랜치의 마지막 커밋: feat/capstone-presentation `e9e36ca6`, feat/discovery-admin `ce856b07`, feat/ios-design-simplification `086fd94d`, fixabley/catalog-subscription-collector `d7e5da7b`, fixabley/dearby-android-design-simplification `da438461`, fixabley/ui-agent `ed7b272b`, ui/calendar-overlap `8beacff3`, ui/identity-avatar-order `8d648e95`, feat/mobile-device-calendar `a88c3c02`, feat/android-activities `c8cf82a1`, feat/ios-activities `10cd5dba`, fixabley/dearby-android `6ccf46fa`, fixabley/dearby-api `88f88950`, fixabley/dearby-ios `6f090faa`, fixabley/dearby-ios-architecture-tests `ee9c4e09`, ui/pretendard-flow-screens `31b52dea`(로컬 reflog·GitHub GC 전까지만 복구 가능).
- 2026-10-18 저녁 사용자: "나중에 공유기능 추가할 예정이야"(옛 구현은 복원하지 않음). 2026-10-19 사용자가 "배포된 명함은 또 달라야"(배부마다 QR·사본, 활동별 일괄 관리)를 제안 → 비판 검토(사본은 디지털 명함의 장점을 버림, 주인 쪽 일괄 관리는 할 일이 없음, 사전 배부는 만나는 순간에 느림, 오늘 지운 것과 같은 구조) → 사용자가 이유를 설명(옛 흐름이 제작→배부로 부자연스러움; 활동 뒤 연락하지 않는 사람에게서 연락처 회수) → 최종 결정 "배포별로 하지는 말고, 어느 프로그램에서 교환했는지만 표시해줘. 단방향이면 1달 뒤 회수해줘." 계약 "명함 교환": 명함별 QR(공개 주소 + 활동) → 웹에서 보고 `dearby://cards/…`로 앱 저장 또는 브라우저 저장, 프로그램 칩·`맞교환`/`N일 뒤 사라져요` 배지, `내 명함도 건네기`, 단방향 30일 회수(`recalled_at`, batch 04:00). 배부 단위·앱 안 스캔·유니버설 링크 없음. 공개 `/cards/:id`는 회수 뒤에도 열린다(사용자에게 알림). PR #261, V29.

### 명함 표현·내 명함 (2026-10-18)

- Mobbin 조사 뒤 사용자: "Outlook 연락처처럼 보여주는 게 검색하기 용이할 것 같아" → 받은 명함 초성/알파벳 색인 목록(PR #252). "명함 디자인 mobbin 참고해서 가져와줘" → 카드 면·2열 내 명함 격자·웹 `/cards/:id` 공통 레이아웃(PR #256). "이미 제공되는 정보인데 명함에 텍스트로 표시되는 거 지워줘" → 기본 템플릿을 `{{profile.introduction}}`만으로, 기존 명함은 V27·V28로 정리(PR #257·#258; 전화·이메일도 제작 화면에서 뺄 수 있게 됨). "내 명함 삭제가 없어" → `이 명함 지우기`(PR #259).
- 2026-10-18 오전 "모바일 사이즈와 웹 사이즈 버전을 따로, 프레이머 브레이크포인트처럼" → 마크다운 템플릿에는 레이아웃이 없어 **내용 버전 2개**로 구현(PR #239, V20 `webTemplate`·`webIntroduction`, 웹 640px 초과에서 웹 버전) → 실기기 확인 뒤 "실시간 편집 이런 퀄리티면 그냥 빼는 게 좋을 것 같아" → 꾸미기 편집기·웹 버전 삭제(PR #242, V21; 당시 웹 버전을 쓴 사용자 테스트 명함 1장의 웹 본문 소실). "웹뷰 편집기(터치·드래그)" 제안은 캔버스형 편집이 목표일 때만 레이아웃 모델과 함께 검토하기로 보류.
- 사용자 결정 대기: 내 명함 용도별 프리셋(컨퍼런스·피어·사교·윗사람·헤드헌팅).

### 이력 입력 퍼널 분리 (2026-10-18)

실기기 확인 중 지적: 조직 이력 퍼널이 프로그램 단계를 묻고 활동 퍼널이 조직부터 묻는다. 조직 퍼널은 조직 → 역할 → 기간 → 설명, 활동 퍼널은 활동(전체 검색) → 역할 → 기간 → 설명. 고른 활동은 서버가 `activityId`로 소속 조직·프로그램을 채우고, 직접 입력한 활동만 조직(필수)·프로그램(선택)을 뒤에 묻는다(PR #241).

### 발견 탭 (2026-10-18 → 2026-10-19)

- 2026-10-18 사용자: 활동 카드 대신 프로그램 단위, 누르면 아코디언으로 열린 활동. 메인 제안 규칙 승인(PR #244, `GET /v1/discovery/programs`, 정수 오프셋 커서; `/v1/programs`는 이력 검색이 써서 경로 변경).
- 2026-10-19 "활동 종류도 추가"(부트캠프·컨퍼런스·워크샵·스터디·강연 등, 프로그램 하위 그룹화) → 10종(해커톤·공모전·밋업·기타 보완, "연합동아리 같은 동아리 활동도 있어"로 `CLUB` 추가), V31, 수집 추출·필터(PR #265·#266). "정렬은 모집일 순서로" → 모집 마감 순(모집일을 마감일로 읽음, 사용자 이견 없음). "모집 기간이 미정인 활동들도 활동 시작일을 모집 종료일로 취급" → 정렬 키 모집 마감 → 활동 시작 → 없음(PR #269·#270). "종류는 프로그램에서 일괄지정" → `programs.category`(V33, PR #270).
- 2026-10-19 "프로그램을 누르면 활동이 노출되지 않아. inner 조인인지 outer인지" → 조인은 outer; 서버가 시각 모집 기간만 보고 날짜 모집 기간(AIGEN 1기)을 "없음"으로, 앱이 `확인 필요`로 걸러낸 것이 원인. "눌렀을 때 따로 조회해오는 방법도 좋아" → 판정 서버 일원화 + `GET /v1/activities?open=true`. "프로그램 단위로 리스트 조회… 당근의 판매중인 상품만 보기처럼 모집중인 프로그램만 보기, 없으면 최근 2년 이내 활동 기준" → `openOnly` 토글·`latestActivityAt`·`지난 활동`(PR #271). 제안했던 별도 프로그램 상세 화면은 "지난 활동"이 펼침 안에 들어가 핵심이 해소됐고, 별도 화면 여부는 결정 대기.
- 2026-10-19 "프로그램이랑 활동에 태그… 창업, 창의성, 개발지식, 프로젝트 개발, 협업, 멘토링처럼 사용자가 얻을 수 있는 요소… 검색결과에 같이 노출" + "편집시 직접 검색 및 직접입력" → 계약 "태그"(통제 어휘 사전, 프로그램·활동 연결, 검색·필터, 어드민 검색+직접 입력). 구현 예정.

### 내 활동 진행 트랙 (2026-10-18)

사용자 제안(NEXT STEP 참고 이미지): 신청한 활동의 전형 단계를 트랙으로. 별도 페이지 대신 `내 활동` 탭을 세그먼트 트랙 + `D-3 · 면접` 배지 행으로, 상세는 세로 타임라인에서 통과·대기·탈락을 사용자가 표시(기기 저장). 단계 = APPLICATION 슬롯 `지원` → 세부 일정 → ACTIVITY 슬롯 `활동`(세부 일정이 없어도 두 단계라 활동 종료 전까지 진행 중). PR #255.

### 로그인 (2026-10-18 → 2026-10-19)

"구글 로그인 추가하고 싶어" → 2026-10-18 웹 방식(서버 코드 흐름, PR #246, V22; 운영 값 미설정으로 503) → "네이티브로 넣을 수는 없어?" 보류 → 2026-10-19 "구글 개발자 계정 인증을 마쳐서 OAuth2 SDK를 추가할 수 있을 것 같아" → 네이티브 전환(PR #262: `POST /v1/auth/google/nonce`·`POST /v1/auth/google`, `GOOGLE_CLIENT_IDS` 허용 목록, iOS GoogleSignIn-iOS 10.x, Android Credential Manager; 웹 방식·시크릿 삭제). 디버그 keystore SHA-1은 채팅으로 전달. 같은 날 Android 패키지 `io.wid.dearby`(PR #245).

### 수집·어드민 (2026-10-18 → 2026-10-19)

- 2026-10-18 "한 url에서 여러개를 수집… 활동은 프로그램의 기수별로 쪼개는 역할" → 활동 = 기수, 단계는 schedules(PR #243). 초안 122개 삭제 뒤 재수집 11개(SSAFY만 실패: 설정 없음 + 웹 전체 검색 `request timed out`).
- 2026-10-18 "대상자 관련된 걸 중첩가능한 json으로… from to… 검색 가능… 필드 어휘는 한글, 영문도 함께" → 계약 "대상자 조건"(V23, `activity_eligibility_terms`, PR #247). 표기는 처음 "카멜케이스 쓰지 말아줘"로 snake_case로 적었다가 같은 날 "데이터베이스가 아니면 스네이크 케이스 사용하지 말아줘"로 **JSON·쿼리·코드는 camelCase, DB 열만 snake_case**로 확정. 어드민 JSON 편집은 "지난번에 수정페이지에 구현한 것처럼" → 폼 편집기(PR #250).
- 2026-10-18 "어드민에 활동기간이 문자열로 돼있는건 뭐야" → 날짜 전용 필드 추출; "애매한건 문자열 보고 내가 직접 지정" → 일정 직접 지정(`schedules.curated`); "중복인거는 내가 지정해서 조심하라고 목록에 추가" → 조심 목록(V24, PR #248). "이전에 있던 활동의 정보를 가져와서 자동완성" → `inherited_fields`(V25, PR #253); 부작용(재수집이 수집 필드를 갱신하지 않음) → `curated_fields`(V26, PR #254).
- 2026-10-19 "모집기간과 활동 기간 순서 바꿔줘"(PR #260) → "모집기간과 활동 기간이 서로 바뀐 경우가 있어 원인 파악" → 지시문에 두 기간 정의, 어드민 `기간 확인` 태그·`모집 ↔ 활동 바꾸기`(PR #263). "공식 안내 url도 내가 수정" → URL 편집·운영자 지정(PR #264). "toss.im/slash-23, slash-24 와 같이 관리되는 경우" → 주소 토큰 `{year}`·`{yy}`·`{round}`, "UI상에서는 태그처럼… 아니면 안내문구" → 태그 버튼+안내+미리보기, "수집 url은 현시점 수집페이지를 기입" → `fetched_urls`(V30), "프로그램 조직 편집기능" → 편집 모달, "활동 추가 플로우가 어드민에 없어" → `POST /v1/admin/activities`, "탐색메뉴처럼 프로그램 하위에 활동들이 표시" → 묶음 보기(PR #266). "날짜 문자열… 직접 지정했을 때 피드백으로… 정규화된 날짜값으로 수집" → 지정 일정을 지시문 해석 예시로(PR #267). "모집기간에 마감일자만 넣었는데 입력되지 않아"·"마감시간만 입력할 수도 있게"·"직접 입력했을 때 사유도 입력하게"·"최하위 조직별로 그루핑, 필터도" → PR #268(V32). "프로그램 삭제 플로우가 없어" → 삭제 행동(PR #270). 2026-10-19 수동 재수집 1회(초안 6개 추가).

### 화면·내 활동·수집 루프·배포 (2026-10-19 밤 → 2026-10-22)

- 2026-10-19~20 화면: 발견 카드 리뉴얼·활동 장소 지오코딩(PR #289), 프로그램 로고(V38)·검색 밑 태그 칩 줄 제거·탭 제목 통일·색 체계 A(시스템 중립색+청록)·다크 모드·상세 이미지 제거(PR #290), 폼은 플랫폼 기본·키보드 내리기(PR #293), 발견 스크롤 동작·일정 표시 우선순위(PR #294), 설정 화면(V40, PR #295), 명함 탭 통합·아이콘 통일·iOS 시스템 내비게이션 바·플로팅 라벨·프로필 읽기/수정 모드(PR #296). 근거 문장은 계약 각 절.
- 2026-10-20 "어드민에서 프로그램 편집이 안돼" → V39(PR #291). "동의 문서 버전이 다릅니다" → compose 기본값(PR #291). "신청 사이트 들어간 후 신청 여부 확인" → 마감 전 모든 활동(PR #292). 단기 행사 이력 제외(PR #297), 명함 고치기 PUT(V41, PR #298), 내 활동 빼기(PR #299).
- 2026-10-20 수집기 "CLI만 쓰게"·"피드백을 RAG처럼"·"워크플로우 md 파일을 배치가 호출" → Claude Code CLI + 로컬 MCP 도구 서버 + `WORKFLOW.md`(V42·V43·V44, PR #300). 첫 실운영에서 4개 프로그램이 턴 한도에 걸려 세 가지 수정안(턴 20·URL 추측 금지·피드백 자동 첨부)을 제안했고 사용자 답 대기.
- 2026-10-21 "웹 리디자인… iOS랑 비슷하게, shadcn" + "변경된 API를 사용하지 못하는 것" → 프록시 핫픽스(PR #301)·리디자인(PR #302).
- 2026-10-22 내 활동 서버 저장·일정 상태(어드민 지정)·겹침 확인 시간·캘린더에 추가, "참여확정은 없어도 괜찮을 것 같아" → 제거, 재동의 "진행"(V45, PR #304). 스토어 제출은 "수동으로 동작시킬 수 있으면" → workflow_dispatch 두 개, GCP 조직 정책으로 키 발급이 막혀 Workload Identity Federation(PR #303). "반복 일정… 겹치는 게 있다면 중복 제거 후 하나만" → "그렇게 해 주세요" → 계약 "반복 일정과 겹침 결과 묶음"(V46 예정).
- 결정 대기: 활동 이력에 "신청만 한 활동" 처리, 수집기 세 가지 수정안.

### 운영 반영 로그

- 2026-10-18 저녁: PR #241·#242·#243·#244·#245·#246·#247·#248 병합, API·admin·batch 재배포(Flyway 24), 웹·어드민 Vercel 배포, 두 실기기 설치, 초안 122개 삭제·재수집 11개.
- 2026-10-18 밤~2026-10-19: PR #250·#252·#253·#254·#255·#256·#257·#258·#259·#260·#261·#262·#263·#264·#265·#266·#267·#268·#269·#270·#271 병합, Flyway V26→V33, 각 병합마다 API·admin·batch 재배포, 어드민 CLI 배포(#260·#263·#264·#266·#268·#270), 웹 Vercel 자동 배포, iPhone 설치(마지막 #269), Android는 #261까지 설치 뒤 USB 분리.

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
- **배포(2026-10-06 당시):** 웹·어드민 Vercel 프로젝트는 main push로 자동 배포되지 않아 메인이 CLI로 배포했다. 2026-10-18부터 사용자 웹 `dearby-web`은 GitHub 연동으로 main 푸시마다 자동 배포되고, 어드민 `dearby-admin`은 연동이 없어 지금도 CLI로 배포한다(위 "현재 상태").

### 남은 일(2026-10-06 당시)

당시 목록(운영 반영 순서 #84→#127→#109·#110·#111→#85·#83→#82·#120·#126·#128, 모바일 로그인 시트·명함 발행, Android Play 앱 서명 SHA-256, 메일 비밀번호 정리, 고정 IP, 인증서 갱신 방식)은 2026-10-18까지 운영 반영이 끝났거나 위 "남은 일·사용자 결정 대기"로 옮겼다. 수집 워커 재배포는 2026-10-18 워커 삭제로 없어졋고, QR 공유·스캔·wallet 저장은 2026-10-18 삭제 뒤 2026-10-19 "명함 교환"으로 대체됐다.

## 로컬 상태와 다음 작업

시작 시 이미 삭제되어 있던 `apps/ios/Dearby.xcodeproj/project.pbxproj`와 `apps/ios/Dearby.xcodeproj/xcshareddata/xcschemes/Dearby.xcscheme`은 사용자 변경으로 보존한다. 추적되지 않는 원본 시안 폴더도 유지한다. 이번 정리에 포함하거나 복구하지 않는다.

이 로컬 checkout에서 iOS를 새로 빌드하려면 프로젝트 파일 상태를 먼저 확인해야 한다. 프로젝트 생성 명령은 [iOS 실행 안내](../../apps/ios/README.md)에 있다. 기존 기기 설치·서명·운영 서버 확인은 이번 문서 정리에서 재실행하지 않는다.

문서 정리 변경을 검토·통합한 다음, 새 제품 요청이 있을 때 해당 플랫폼 실행 안내와 현재 코드를 기준으로 작업한다. 예시 화면의 완료를 실제 계정·캘린더·명함 전송 기능의 완료로 간주하지 않는다.

[이전 조율 기록](https://github.com/fixabley/dearby/blob/ec9dfec98c13661ab332c2567bfba1bb35f1329a/docs/context/coordinator-current-task-and-decisions.md)은 9월 작업의 결정·검증 경위를 보존한다. 당시의 포트·세션·진행 상태를 현재 실행 지시로 사용하지 않는다.
