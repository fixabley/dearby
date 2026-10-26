# 현재 작업과 결정

현재 구현 범위와 협업 규칙은 [AGENTS.md](../../AGENTS.md), 계약은 [native-v1.md](../../shared/contracts/native-v1.md)·[admin-v1.md](../../shared/contracts/admin-v1.md)를 따른다. 이 문서는 진행 상태와 결정 로그만 기록한다. 2026-10-19 정리: 2026-10-18·19 결정을 주제별로 합쳤고 대체된 내용은 각 항목의 "변경 이력"과 Git 고정 링크로 남긴다. 2026-10-26 정리: 그날 병합된 PR #376~#442의 결정을 "2026-10-26 결정" 한 절에 주제별로 모았다.

## 현재 상태 — 2026-10-26 확인

이전 판(2026-10-19 확인)은 [`dc93f57d`](https://github.com/fixabley/dearby/blob/dc93f57d/docs/context/coordinator-current-task-and-decisions.md)에 있다. 배포 절차·기기 식별자·배치 시각은 그대로다.

- **운영 API**(`api.dearby.wid.io.kr`, `~/.dearby-deploy/api-kotlin-2a061187` compose: api·admin·batch·postgres). 배포 절차: rsync(소스, `data`·`.env.prod`·`build`·`.gradle` 제외) → `docker compose -f compose.prod.yaml --env-file .env.prod up -d --build api admin batch`. 마이그레이션은 컨테이너 기동 때 Flyway가 적용한다. 2026-10-26 조율 세션 보고: 그날 서버 PR을 순서대로 배포해 Flyway **V62**(선발 과정·활동 일정 `phase`, PR #416) → **V63**(게스트 명함 교환 맥락, PR #432) → **V64**(연락처 인증, PR #437; 원래 V63이었으나 운영에 이미 적용된 #432의 V63과 번호가 겹쳐 [`8fabad22`](https://github.com/fixabley/dearby/commit/8fabad22)로 V64로 바꿈)까지 적용. 이 문서를 쓰는 세션의 HTTP 확인(2026-10-26 밤, 읽기 전용): `GET /v1/activities/156` 응답에 `phase`·`status`·`hasTimetable`이 있고(V62·#405), `GET /v1/discovery/programs?kind=event&status=…`가 `statusCounts`를 돌려주며, `GET /v1/me/contacts/github/callback`이 404가 아니라 503(#437 코드는 배포됐고 `GITHUB_*` env는 비어 있음). 정확한 배포 커밋은 확인하지 못했다.
- **웹** `dearby.wid.io.kr`: Vercel GitHub 연동 자동 배포는 2026-10-26부터 **main 푸시만**(PR #413, 미리보기 배포 중단). 같은 날 Hobby 일일 배포 한도에 걸려 main 배포가 `Deployment rate limited — retry in 24 hours`로 막혔고 자동 재시도는 없다 — 한도가 풀리면 수동 Redeploy가 필요하다(아래 "남은 일"). **어드민** `admin.dearby.wid.io.kr`: Vercel 프로젝트 `dearby-admin`은 연동이 없어 `git archive origin/main apps/admin` 사본에서 `npx vercel link --project dearby-admin && npx vercel deploy --prod`로 배포(사용자 작업, 2026-10-26 phase·시간표 편집 반영 여부는 이 문서에서 확인하지 않음).
- **기기**: iPhone(UDID `00008120-0011258C2107C01E`)·Android(`R3CY10GNW9F`, 패키지 `io.wid.dearby`). 2026-10-26은 iPhone USB가 연결되지 않아 그날 PR의 iOS 실기기 화면 확인을 모두 미실행으로 기록했다(각 PR 본문). 시뮬레이터·에뮬레이터는 쓰지 않는다. 실기기 Debug 설치에는 운영 origin·Google 값이 필수다(2026-10-25 기록).
- **운영 데이터**(2026-10-26 조율 세션 보고 + 이 세션 HTTP 확인): 컨퍼런스의 수집 조각 일정을 큐레이션 가져오기(`--import-curation`, PR #402)로 "큰 블록 + 트랙·세션 시간표"로 정리했다. 조율 보고는 컨퍼런스 8건이고, 공개 API(`GET /v1/activities?kind=event&status=…`)에서 `hasTimetable`인 활동은 7건 — 156 우아콘 2026, 202 FECONF 2026, 203 FEConf 2025, 204 드로이드나이츠 2026, 205 드로이드나이츠 2025, 213 GDG DevFest Cloud x Seoul 2025, 228 삼성 AI 포럼 2026(차이 1건은 미공개 활동일 수 있다 — 추정). 정리 전 백업 파일은 조율 세션이 보관하며 경로는 이 문서에 적지 않았다(저장소·`~/.dearby-deploy`에서 찾지 못함). PR #405 적용으로 모집 일정이 없는 204 드로이드나이츠 2026·227 파이토치 데이 코리아 2026은 열린 활동에서 마감(`NO_RECRUITMENT`)으로 바뀌었다.
- **동의 버전**: 서버 `CONSENT_PRIVACY_VERSION` 기본값 `2026-10-26`(application.yaml·compose), iOS·Android 앱 상수는 `2026-10-28`(PR #441·#440), 웹 `consent-versions.ts`는 `2026-10-26`. 서버 값을 올리기 전까지 2026-10-28 빌드의 새 동의는 400 `CONSENT_VERSION`이다(아래 "남은 일").
- **로그인**: 패스키·Apple·Google(네이티브) 운영 중. 새 계정은 애플·구글만(2026-10-25). 웹 로그인 1단계(PR #438)는 코드만 main에 있고 Vercel env(Apple Services ID·Google 웹 클라이언트)와 서버 `GOOGLE_WEB_CLIENT_ID`·`WEBAUTHN_ALLOWED_ORIGINS`가 아직 없어 운영에서 쓸 수 없다.
- **연락처 인증**: GitHub·이메일 인증 코드는 배포됐지만 `GITHUB_CLIENT_ID/SECRET/CALLBACK_URL`·`MAIL_HOST/FROM` 등이 없어 503 → 앱은 그 버튼을 숨긴다.
- **배치**: 매일 00:00 수집, 04:00 명함 회수(Asia/Seoul). 일회성 옵션(`--collect-now`·`--recall-now`·`--import-curation` 등)으로 띄운 `run --rm` 컨테이너는 2026-10-26부터 `@Scheduled` 정기 작업을 켜지 않는다(PR #402).

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

## 2026-10-26 결정 — 우아콘(2026-10-28) 전 묶음 (PR #376~#442)

하루에 PR #376~#442가 main에 들어갔다(KST 10:50~23:26). 세부 규칙은 계약의 해당 절(절 이름을 적음), 사용자 발언은 각 PR 본문에 있다. 같은 날 안에서 바뀐 결정은 최종 상태를 적고 중간 상태는 "변경 이력"으로 남긴다. 아래 모든 PR의 앱 화면 흐름은 실기기 미연결로 미실행이고 lint·빌드·API 테스트만 통과했다.

### 쐐기와 성공 지표

- **쐐기(조율 세션 기록)**: 쐐기는 **컨퍼런스 당일 + 명함 교환**으로 결정(결정문 그대로; "비공개 테스트에서 가치를 처음 증명할 지점"은 이 문서의 해석). 2026-10-28 우아콘에 맞춰 오늘 내 행사 카드·시간표 모달·세션 이동 알림·교환 세션 맥락·테스터 모집 CTA를 그 전에 넣었다.
- **성공 지표 2개**(PR #426): ① 내 활동에 추가한 활동이 실제 참석까지 이어지는가, ② 테스터 1인이 맞교환 3회. 이벤트는 analytics-v1.md "비공개 테스트 성공 지표"(`stage_mark`·`participation_reached`·`activity_day_action`·`favorite_add type=session`·`card_received`·`card_exchange_completed`·`card_exchange_milestone`·사용자 속성 `card_exchanges`·화면 `card_qr`), 정의·GA4 계산식·목표값(추정)·일정·테스터 부탁은 [비공개 테스트 계획](test-plan-2026-10.md). 우아콘 2026(활동 156)은 `신청 기간`·`입장권 추첨 결과 발송` 두 선발 단계가 있어 통과 표시가 있어야 `참여 중`이 된다.
- **GA 맞춤 측정기준**(조율 세션 보고): analytics-v1.md "GA 콘솔에서 할 일(사용자)" 목록의 **23개**(사용자 범위 5: 기존 4 + `card_exchanges`, 이벤트 범위 18: 기존 14 + `activity_id`·`via`·`action`·`reciprocal`)를 2026-10-26 등록 완료. 그 뒤 PR #442가 더한 웹 `tester_cta_tap`의 `platform`·`target`은 등록 여부를 확인하지 못했다(미등록으로 추정).

### 활동 상태·필터·종류

- **모집 일정 없는 활동은 마감**(PR #405 서버, #403·#404 앱): 종류와 상관없이 모집 기간이 없으면 마감. `nearestIsActivityStart`는 항상 false(폐지 예정). 계약 "모집 일정 없는 활동은 마감"·"활동 상태".
- **활동 상태** `Activity.status` 6종(RECRUITING_UPCOMING·RECRUITING·RECRUITMENT_CLOSED·NO_RECRUITMENT·IN_PROGRESS·ENDED, 서울 기준)·`ProgramSummary.statusCounts`·`status` 쉼표 필터(PR #405). 앱·웹은 서버 status로 배지를 그리고 NO_RECRUITMENT는 `모집 일정 미정`(#406·#407).
- **상태 묶음 최종**(PR #429 Android·#431 iOS·웹): `지원 가능` = RECRUITING_UPCOMING·RECRUITING(기본), `진행·예정` = IN_PROGRESS·RECRUITMENT_CLOSED·NO_RECRUITMENT, `지난 활동` = ENDED. id `available`·`in_progress`·`past`는 저장값·URL·통계 호환으로 유지(`in_progress`의 뜻이 넓어짐). 변경 이력: 같은 날 먼저 들어간 #406·#407은 `지원 가능`·`진행 중`(IN_PROGRESS)·`지난 활동`(RECRUITMENT_CLOSED+NO_RECRUITMENT+ENDED) 드롭다운이었다.
- **필터 시트**(#429·#431): 발견 상단은 `프로그램 | 이벤트`와 검색만, 상태·종류는 `필터` 버튼(적용 수 배지) → 시트(섹션별 다중 선택, `초기화`는 초안만 되돌림, `적용` 한 번에 한 요청), 적용된 필터는 칩(× 해제). 계약 "필터 시트". 변경 이력: #406·#407·#414·#415의 상태·종류 두 줄 드롭다운을 대체.
- **종류 아이콘 라벨·종류 필터**(PR #414 Android·#415 iOS·웹): 사용자 "각 프로그램마다 부트캠프 컨퍼런스 같은 것도 아이콘라벨로 해서 카드 좌상단에", "필터링 옵션에 추가". 접힌 카드 이름 위 아이콘+종류 이름, 아이콘 표는 계약 "의미별 고정 이름"(워크샵·스터디·해커톤은 태그 아이콘과 겹쳐 다른 글리프). **이벤트 모드에서는 종류 필터를 숨긴다**(#429·#431; 운영 이벤트가 전부 컨퍼런스 — 둘 이상 종류가 들어오면 다시 켠다).
- **모집 날짜 표기**(#403·#404): 시작·끝이 있으면 `10.1 – 10.20` + 모집 중 `D-n`, 상세의 별도 `모집 마감` 행 제거. **시작 D-n 배지**(PR #423 Android·#424 iOS·웹): 사용자 "모집 예정 활동에 시작 D-5 배지". `RECRUITING_UPCOMING` + 모집 시작(`startAt` 또는 `startDate`)에 `시작 D-n`/`오늘 시작`, 카드는 `모집 예정` 칩 왼쪽·상세는 상태 배지 오른쪽(세 플랫폼 공통). 계약 "모집 예정 시작 D-day".
- **발견 `프로그램 | 이벤트` 전환**(오전, PR #396 서버 `kind`, #395·#398·#399): 이벤트 = CONFERENCE·WORKSHOP·MEETUP·LECTURE, 미분류는 프로그램 쪽. 대기열 장면의 행사 판정도 같은 목록(#400).

### 시간표·진행 단계

- **트랙·세션 시간표**(오전, PR #392 API·#394 어드민 편집·#395 웹·#398 iOS·#399 Android, 보강 #401 `hasTimetable`·찜 목록 `schedule`/`track`): 블록(세부 일정)에 트랙 × 세션 표, 세션 찜·`찜한 세션` 나만의 시간표(시간순 한 줄), 즐겨찾기 위 `찜한 세션` 진입. 계약 "트랙·세션 시간표".
- **시간표는 모달**(PR #408 Android·#409 웹·#410 iOS): 사용자 "그럼 차라리 모달로 보여줘", "웹도 마찬가지로 진행해줘". 활동 상세의 `hasTimetable` 블록 행 전체를 누르면 시트/다이얼로그(`{활동} · {블록}`), push 화면·인라인 펼침 삭제. iOS는 로그인·동의 시트가 시간표 시트 위에서도 뜨도록 `shared/ui/PromptLayer`.
- **내 활동 시간표·진행 단계 화면 정리**(PR #411·#412 → #417·#418): 사용자 "내 활동에서도 시간표를 볼 수 있으면", "진행단계 페이지가 너무 난잡한데 활동탐색 카드처럼 깔끔하게", "마진이 너무 크고 항목별 경계가 뚜렷하지 않아". 머리는 발견 `ActivityCard` 재사용(태그 칩 줄은 뺌, #420·#421), 단계는 한 줄씩이고 상태는 선발 단계만. 최종: 시간표 있는 단계 행에 시간표 아이콘(`tablecells`/`ViewTimeline`) + 펼친 메뉴 `시간표 보기`(#417·#418, 사용자 "진행단계에서 시간표 있는 일정은 시간표 아이콘 표시해주고 펼쳤을때 시간표보기 버튼"). 변경 이력: #411·#412의 별도 `시간표` 섹션은 같은 날 삭제. 계약 "진행 단계 화면 정리".
- **선발 과정·활동 일정 분리**(PR #416 V62·#419 웹/어드민·#420 Android·#421 iOS·#422 어드민 보충): 사용자 "선발 프로세스랑 실제 활동이랑 분리". `Schedule.phase`(SELECTION|ACTIVITY, 늘 값 있음; 모집 슬롯 SELECTION·활동 기간 ACTIVITY 고정, 세부 일정은 저장값 또는 옛 판정). 활동 상세·진행 단계는 `선발 과정`·`활동 일정` 두 구역, 통과·대기·탈락은 SELECTION만. **조율 결정**: V62 백필은 슬롯 행과 DEADLINE·ANNOUNCEMENT·INTERVIEW만 채우고 EVENT는 null(상태 지정 시 저절로 SELECTION); 선발 모두 통과면 `참여 중` 배지는 `활동 일정` 머리글에 **하나만**(계약 본문도 수정, [`14a409b9`](https://github.com/fixabley/dearby/commit/14a409b9)). 어드민 PATCH `phase: null`은 자동 판정으로 되돌리기, 응답 `phaseSource: STORED|RESOLVED`(#422). 수집기·큐레이션 가져오기도 `phase?`. 계약 "선발 과정·활동 일정 분리", admin-v1 "선발 과정·활동 일정(phase)".
- **통과 여부는 선발 단계에만**(#403·#404, 오전 중간 규칙): 지원·DEADLINE·ANNOUNCEMENT·INTERVIEW·상태 지정 EVENT만 — 같은 날 `phase`로 대체(옛 서버 응답 폴백으로 남음).
- **지난 선발 단계 통과 제안**(PR #436 iOS·#435 Android): 사용자 "지난 선발 단계 자동 통과 제안 넣어줘". 내 활동에 넣은 직후, 트랙 순서로 **첫 미래 선발 단계 앞까지**(조율 2026-10-26: 미래 단계 뒤의 날짜 없는 단계는 제외) 표시 없는 지난 선발 단계가 있으면 `지난 선발 단계를 통과로 표시할까요?`를 활동마다 한 번 묻고, `통과로 표시`는 단계마다 기존 API로 `PASSED`(`stage_mark via=autopass_prompt`). 자동 표시는 하지 않는다. 계약 "지난 선발 단계 통과 제안".
- **큐레이션 가져오기 보강**(PR #402): `activities[].schedules`로 세부 일정 블록 삭제·수정·생성, `timetables[].scheduleRef`, dry run은 읽기 전용 트랜잭션, 일회성 옵션은 스케줄 끔. admin-v1 "운영 데이터 가져오기".

### 우아콘 당일 기능

- **찜한 세션 이동 알림**(PR #425 iOS·#428 Android): 사용자 "컨퍼런스 참여 시 중간에 이동 알림"의 1단계 — 찜한 세션 **10분 전 기기 로컬 알림**(서버 변경 없음), 제목 `10분 뒤 · {트랙} · {room}`, 같은 시각은 하나로 묶음, 설정 › 알림 `세션 이동 알림` 스위치(기본 켬), 알림 탭 → 활동 상세 위 블록 시간표 `찜한 세션` 보기. 권한은 첫 세션 하트·스위치 켬이 한 번 묻는다(첫 프로그램 하트와 같은 규칙으로 해석). Android는 `AlarmManager` 정확 알람(14+ 미허용이면 비정확, `알람 및 리마인더` 안내 행). 통계 `session_reminder_scheduled(count)`. 계약 "찜한 세션 이동 알림". 실제 발화·탭 라우팅은 실기기 미확인.
- **오늘 내 행사 카드**(PR #434 iOS·#430 Android): 발견 맨 위(맞춤 대기열 행 위). 내 활동의 활동 일정 블록(`phase: ACTIVITY`, 길이 7일 이하)이 오늘이거나 7일 안 시작하는 활동 하나(`외 n개`). 오늘: `오늘` 배지 + 다음 찜 세션(진행 중이면 `지금 ·`) 또는 다음 블록, 누르면 그 블록 시간표 시트(찜 있으면 `찜한 세션` 탭), `명함 QR` 버튼은 활동 칩이 이 활동으로 맞춰진 QR. 7일 안: `D-n` + 첫 블록, 누르면 진행 단계. 비로그인·해당 없음이면 없음, 추가 서버 호출 없음. 구현 판단: 7일 넘는 블록(몇 달짜리 활동 기간) 제외, 당일 블록이 모두 끝나도 그날은 `오늘`. 통계 `today_card_tap`. 계약 "오늘 내 행사 카드".
- **명함 교환 세션 맥락**(PR #432 서버·#427 웹·#434 iOS·#430 Android): 받은 명함에 어느 프로그램·활동뿐 아니라 **어느 일정 블록·세션(트랙·방)에서 받았는지**를 서버가 저장 시각으로 한 번 판정 — 받는 사람이 찜한 진행 중 세션 → 진행 중 블록(겹치면 짧은 것, 시각 블록만) → 활동만. 맞교환은 원 Receipt 복사, 이미 저장된 것은 재판정 없음. `receipts.context`에 `scheduleId`·`sessionId`, `guest_cards`에 V63 컬럼, 응답 `context`에 `scheduleId/scheduleTitle/sessionId/sessionTitle/trackName/room`. 화면: 목록 행 캡션 `{프로그램} · {블록} · {트랙} · {방}`, 상세 둘째 줄 세션 제목(iOS #434 기준으로 계약 통일, Android·웹 맞춤). `import-guest`도 맥락을 그대로 옮긴다. 계약 "명함 교환" › "교환 세션 맥락".
- **테스터 모집 CTA**(PR #442 웹): 사용자 "우아콘 가서 명함 교환할 때, 비공개 테스터 모집으로 연결할 수 있을까?". `/cards/:id` 저장 버튼 아래·`/saved` 상단(비로그인)·로그인 시트에 `비공개 테스트로 앱 받기` — iOS는 TestFlight 링크로 바로, Android·데스크톱은 Google 그룹 가입 → Play 테스트 참여 단계 시트. Vercel env 3개(`NEXT_PUBLIC_TESTFLIGHT_URL`·`NEXT_PUBLIC_PLAY_GROUP_URL`·`NEXT_PUBLIC_PLAY_TEST_URL`, 빌드 시점 값, 비면 그 플랫폼 숨김). 통계 `tester_cta_tap(platform, target)`.

### 계정·연락처·웹

- **연락처 인증**(PR #437 서버 V64·#440 Android·#441 iOS): 사용자 "깃헙·인스타처럼 앱 로그인을 지원하는 것들은 연락처에 등록할 때 로그인으로 등록", "이메일도 Spring Mail 활용해서 인증". **GitHub**은 OAuth 연결(start → 콜백 → 1회용 code → complete; 콜백만으로 끝내지 않는 이유는 계정 연결 CSRF), **이메일**은 6자리 인증번호(10분, 1분당 1회·하루 10회, 5회 오류 폐기). `Contact.verified`·`verifiedAt`은 서버만 켜고 값을 고치면 풀림, 성공 시 프로필 버전 +1. **조율 판단으로 범위는 둘만**: Instagram은 Basic Display API 종료(비즈니스 계정만), 카카오 로그인은 카카오톡 ID·오픈프로필 URL을 주지 않음, LinkedIn OIDC는 이름만, Behance API 신규 발급 중단 — 수동 입력만. 운영 env(`GITHUB_*`, `MAIL_*`)가 없으면 503이고 앱은 버튼을 숨긴다. 통계 `contact_verify`. 계약 "연락처 인증".
- **웹 로그인 1단계**(PR #433 서버·#438 웹): 사용자 "웹도 로그인이랑 관련 기능 추가해줘". 서버: `POST /v1/wallet/import-guest`(비로그인 저장 → 계정 지갑, 받은 날·활동 맥락 유지, 400 `GUEST_SESSION_INVALID`), `GOOGLE_WEB_CLIENT_ID` 허용 aud. 웹: `__Host-dearby_access`(60초)·`__Host-dearby_refresh`(90일) HttpOnly 쿠키, 전용 `POST /api/auth/refresh`(Web Locks, 회전 경쟁 방지), 로그인 시트(Apple JS·Google GIS·WebAuthn 표준 JSON), 동의 시트, `/settings`(계정·패스키·정보), 비로그인 저장은 **물어서** 옮김(자동 아님), GA 사용자 속성 `signed_in`. 계정 기능(즐겨찾기·내 활동·받은 명함·프로필 등)은 2단계. 계약 "웹 로그인"·"웹 화면 — 1단계", [사용자 웹](web-implementation-and-handoff.md).
- **처리방침 2026-10-28판**(PR #439, 사용자 승인 2026-10-26): 연락처 인증 상태·GitHub 사용자명, 교환 시점 일정 블록·세션, 세션 이동 알림(기기 안), 웹 로그인 세션 쿠키, 이메일 인증 요청(하루 보관), 처리 위탁 `Google LLC (Gmail SMTP)`·국외 이전 행, 시행 2026-10-28(고지 2026-10-27). **재동의 순서**: 클라이언트 상수 먼저(iOS `ConsentVersions.privacy`·Android `Consent.PRIVACY` = `2026-10-28`, #441·#440 완료; 웹 `apps/web/src/features/auth/model/consent-versions.ts`는 아직 `2026-10-26`이라 올려서 Vercel Redeploy에 묶는다) → 서버 `CONSENT_PRIVACY_VERSION`은 앱·웹 빌드가 배포된 뒤 올린다(아직 `2026-10-26`). 서버만 먼저 올리면 옛 빌드의 동의가 400·프로필 쓰기 403, 올리지 않으면 10-28 빌드의 새 동의가 400 `CONSENT_VERSION`이다. 미해결 불일치: #439는 SMTP 공급자를 Gmail로 적었지만 #437은 공급자를 사용자가 고르는 것으로 두었다(다른 공급자면 5·6절·변경 이력 수정). 변경 이력: 오전 #385로 처리방침 버전 `2026-10-26`(사진·소개 링크·웹 사용 통계)을 먼저 올렸다.
- **광고 기본 꺼짐**(PR #429 Android·#431 iOS): 초기 테스트 기간은 맞춤 대기열 끝 광고·광고 동의 요청·ATT를 빌드 플래그 `DEARBY_ADS_ENABLED`(기본 false)로 끈다. 코드·SDK·동의 흐름은 유지, 다시 켤 때는 CI·릴리스 워크플로에 변수가 필요. 부작용: 꺼진 동안 설정의 광고 동의 스위치도 숨어 이미 남은 동의를 앱에서 철회할 수 없다. 계약 "맞춤 대기열 끝 광고" › "광고 끄기".

### 배포·운영

- **Vercel 미리보기 배포 중단**(PR #413): PR·브랜치 push마다 생기던 미리보기가 Hobby 일일 배포 한도를 다 써 main 배포(#407·#409·#410)가 막혔다. `vercel.json` `git.deploymentEnabled: {"**": false, "main": true}`로 **main만 자동 배포**. 조율 결정: 한도를 아끼기 위해 웹 PR은 가능한 한 **main 병합을 묶어서** 한다. 웹 PR 확인은 로컬 `next build`·`next dev`.
- **운영 배포·데이터**: 위 "현재 상태"(V62·V63·V64, 컨퍼런스 시간표 정리).
- **오전 묶음(PR #376~#399, 10:50~15:03 KST, 계약에 반영됨)**: Android 스크롤 끝 키보드·탭바(#376), 명함 템플릿 폐지(#377), 프로필 사진·명함 사진(#378·#379·#380), 연락처 브랜드 아이콘 단색·사용자명 입력(#381), 소개 링크·소개 글 폐지(V60, #383), GA4 웹 스트림·Vercel Web Analytics(#384), 처리방침 동의 버전 2026-10-26(#385), **즐겨찾기 캘린더만**(#386: `목록 | 캘린더` 전환·목록 보기 제거, 날짜 없는 즐겨찾기 묶음, 기기 저장 보기 값은 읽지도 지우지도 않음 — 계약 "즐겨찾기 활동 캘린더 보기"), 겹침 시간표 기기 일정 블록·눈금 비례(#387), iOS 실기기 수동 UI 흐름 XCUITest(CI 미포함, #388), 웹 홈 화면 바로가기 안내·빈 목록 Safari 안내(#389·#390), Android 실기기 플로우 테스트 수정(#391), 시간표 API·앱·웹·어드민(#392~#395·#398·#399), 겹침 결과 시간표 연달아(#393), iOS 명함 만들기 로드 간헐 실패(#397).

## 남은 일·사용자 결정 대기 — 2026-10-26 갱신

이전 목록은 [`dc93f57d`](https://github.com/fixabley/dearby/blob/dc93f57d/docs/context/coordinator-current-task-and-decisions.md). 태그·Google OAuth 클라이언트·일회성 batch 종료는 끝나 지웠다.

- **사용자 작업(스토어·배포)**: iOS TestFlight 외부 테스터 그룹 만들기·베타 앱 심사 제출(조율 세션 기록, 2026-10-26 미완). Play 비공개 테스트 릴리스 상태 확인(테스터 그룹·검토 상태). 웹 Vercel **Redeploy**(한도 해제 뒤; PR #413 시점에 #407 이후 main 배포가 막혀 있었고 그 뒤 반영 여부는 미확인 — #438 웹 로그인·#442 테스터 CTA의 첫 실제 확인도 그 뒤). 어드민 Vercel CLI 배포(phase 구분·시간표 편집 포함). 테스터 CTA Vercel env 3개 입력.
- **사용자 결정 — 서버 동의 버전 올리기 시점**: iOS·Android 2026-10-28 빌드(TestFlight·Play 테스트)가 테스터 기기에 배포되고 웹 `consent-versions.ts`도 `2026-10-28`로 올려 Redeploy한 뒤 `CONSENT_PRIVACY_VERSION=2026-10-28`로 api·admin 재생성. 그 전에는 2026-10-28 빌드의 새 가입이 400이고, 올리면 옛 빌드 사용자는 업데이트 전까지 프로필 쓰기 403. 처리방침의 SMTP 공급자(Gmail 명시)도 함께 확정.
- **사용자 작업(운영 env)**: 연락처 인증 — GitHub OAuth App(콜백 `https://api.dearby.wid.io.kr/v1/me/contacts/github/callback`) → `GITHUB_CLIENT_ID`·`GITHUB_CLIENT_SECRET`·`GITHUB_CALLBACK_URL`; SMTP 공급자 → `MAIL_HOST`·`MAIL_PORT`·`MAIL_USERNAME`·`MAIL_PASSWORD`·`MAIL_FROM`, `wid.io.kr` SPF·DKIM. 웹 로그인 — Vercel `NEXT_PUBLIC_APPLE_SERVICES_ID`(`io.wid.dearby.web`, Services ID 도메인·Return URL 등록)·`NEXT_PUBLIC_APPLE_REDIRECT_URI`·`NEXT_PUBLIC_GOOGLE_CLIENT_ID`, 서버 `GOOGLE_WEB_CLIENT_ID`·`WEBAUTHN_ALLOWED_ORIGINS`에 `https://dearby.wid.io.kr`. GA 맞춤 측정기준에 `platform`·`target` 등록(표시 이름 구분).
- **검증 미실행**: iPhone USB 미연결로 2026-10-26 PR 전부의 iOS 실기기 화면 확인 미실행(기기 연결 뒤 세션 이동 알림 발화·탭 라우팅, 오늘 카드, 필터 시트, 연락처 인증, 자동 통과 시트 순으로 확인). XCUITest(#388) 재실행. 스토어 스크린샷 재촬영·그래픽 이미지 재제작(필터 시트·종류 라벨·진행 단계 화면이 바뀜).
- **앱 구현 — 분석 이벤트**(계약·계획은 #426, 코드 확인 2026-10-26 밤): 있음 — `stage_mark`(iOS는 수동 표시·제안 모두, Android는 제안 경로만), `today_card_tap`, `contact_verify`, `session_reminder_scheduled`, Android 화면 `card_qr`. 없음 — `participation_reached`, `activity_day_action`, `card_received`, `card_exchange_completed`, `card_exchange_milestone`, 사용자 속성 `card_exchanges`, iOS 화면 `card_qr`, `favorite_add/remove type=session`, Android 수동 `stage_mark`. 2026-10-27 빌드에 들어가지 않으면 우아콘 지표는 test-plan "구현 전 상태"의 대체 계산.
- **다음 기능(순서는 조율 세션 기록)**: #425의 1단계(기기 로컬 알림)에 이어 iOS 라이브 액티비티 → Android 진행 중(ongoing) 알림 → 홈 화면 위젯. 웹 계정 기능 2단계(즐겨찾기·세션 찜·내 활동·받은 명함·프로필·알림 설정·연결된 로그인·패스키 이름). `If-Match`·`Accept-Language` 전달·`requireAccount` 호출처(#438 미완).
- **운영 환경**: OrbStack을 로그인 시 자동 시작하도록 하고 api·admin·batch 컨테이너 가동 감시를 둔다(조율 세션 기록, 미착수).
- **이전부터 남은 것**: 활동 이력에 "신청만 한 활동" 처리, 수집기 세 가지 수정안(턴 20·URL 추측 금지·피드백 자동 첨부), 프로그램 상세 화면 여부, 내 명함 용도별 프리셋, SSAFY 수집 설정, Vercel 미사용 환경값 4개 삭제, Android 출시 서명(SHA-1·SHA-256), Google 로고 자산, 인증서(`wid.io.kr`, `api.dearby.wid.io.kr`) 2027-01-04 만료(수동 dns-01), 운영자 지정 해제 API 없음, 비로그인 저장자는 맞교환할 수 없어 30일 뒤 회수(의도).

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

### 패스키 가입 제거 (2026-10-25)

- 사용자 결정: 로그인 화면의 "새 패스키로 시작"을 없애고 새 계정은 애플·구글로만 만든다. 기존 패스키 계정의 로그인과 설정의 패스키 관리는 유지, 서버의 Bearer 없는 패스키 가입 API는 배포된 옛 앱 때문에 남긴다. 세부는 계약 "패스키 로그인".

### 2026-10-26

위 "2026-10-26 결정 — 우아콘(2026-10-28) 전 묶음"에 주제별로 있다(지난 선발 단계 통과 제안·즐겨찾기 캘린더만·명함 교환 세션 맥락 포함).

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
