# 앱 사용 통계(Firebase Analytics) 이벤트 사전 — 2026-10-25

사용자 결정: "Analytics SDK 넣어줘"(광고 ID 없이), "분야 등을 지정해서 넣을 수도"(사용자 속성), "퍼널 통과율 같은 것도 GA로 적용해주세요. 이탈률, 도달율도". iOS·Android가 같은 이름을 쓴다. 웹은 2026-10-26 사용자 요청으로 같은 GA4 속성의 웹 데이터 스트림에 아래 "웹" 절의 이벤트만 보낸다. 개인정보: 광고 ID·SSAID·사용자 ID를 보내지 않고(2026-10-25부터 앱에 AdMob 광고가 있어 광고 SDK는 광고 ID를 쓰지만 Firebase 사용 통계는 여전히 쓰지 않는다), 지원 자격 값(나이·재학 등)·이름·연락처·명함 내용은 어떤 이벤트·속성에도 넣지 않는다. 설정 `개인정보` 섹션의 `사용 통계 보내기`(설명 "어떤 화면과 기능을 쓰는지 모아 앱을 고치는 데 써요. 이름·연락처는 보내지 않아요." — Analytics 앱 인스턴스 ID가 있어 "개인정보 없이"라고 쓰지 않는다, 2026-10-25)를 끄면 수집을 멈춘다.

## 사용자 속성 (GA 맞춤 정의 — 사용자 범위로 등록)

| 이름 | 값 |
|---|---|
| `interest_fields` | 추천 설정의 상위 분야 nameEn 쉼표 목록(예 `it,engineering`, 36자에서 자름) |
| `interest_kinds` | 추천 설정의 활동 종류 코드 소문자 쉼표 목록 |
| `has_eligibility` | `yes`/`no`(지원 자격 정보 입력 여부만) |
| `signed_in` | `yes`/`no` |

## 퍼널 이벤트 (GA 탐색 › 유입경로 탐색으로 통과율·이탈률을 본다)

### 1. 추천 설정 퍼널
| 이벤트 | 시점 | 매개변수 |
|---|---|---|
| `funnel_start` | 시작 화면이 보일 때 | `entry` = `auto`(발견 첫 진입)·`queue_row`·`profile` |
| `funnel_step` | 각 질문 화면이 보일 때 | `step` = `fields`·`subfields`·`categories`·`traits`, `index`(1부터), `total`(그 사용자 질문 수) |
| `funnel_skip` | `건너뛰기` | `step` |
| `funnel_later` | 시작 화면 `나중에` | — |
| `funnel_complete` | 결과 화면 도달 | `fields_count`, `kinds_count`, `traits_count`, `found`(n개) |
| `funnel_exit` | 결과 전 화면을 닫음(뒤로·스와이프) | `step` |

유입경로 예: `funnel_start` → `funnel_step(step=fields)` → `funnel_step(step=categories)` → `funnel_step(step=traits)` → `funnel_complete`. 단계별 이탈 = 다음 단계에 오지 않은 비율.

### 2. 맞춤 대기열 도달
| 이벤트 | 시점 | 매개변수 |
|---|---|---|
| `queue_open` | 대기열 화면 열림 | `size`(오늘 카드 수), `seen`(이미 반응한 수) |
| `queue_card_view` | 카드가 화면 중앙에 1초 이상 | `position`(1부터), `activity_id`, `field`, `category` |
| `queue_card_complete` | 마지막 장면까지 봄 | `position`, `scenes` |
| `queue_like` · `queue_dismiss` | 하트·관심 없어요(이유 `reason`) | `position`, `activity_id`, `field`, `category` |
| `queue_detail` | `자세히` | `position`, `activity_id` |
| `queue_end` | 끝 화면 도달 | `viewed`(본 카드 수) |
| `queue_exit` | 끝 전 닫음 | `position`(마지막으로 본 카드) |
| `queue_ad_shown` | 대기열 끝 광고 카드가 화면에 보임(열람당 최대 1번, 2026-10-25) | `viewed`(그때까지 본 활동 카드 수) |
| `att_prompt_result` (선택, iOS만) | 대기열 첫 광고 요청 전 ATT 창에 답함(창을 띄운 경우만) | `status` = `authorized`·`denied`·`restricted` |
| `queue_ad_skipped_unloaded` | 마지막 카드에서 넘겼는데 광고가 로드돼 있지 않거나 실패해 광고 카드를 건너뜀 | `reason` = `loading`(아직 요청 중)·`failed` (Release에 광고 ID가 없거나 `DEARBY_ADS_ENABLED`가 꺼져(2026-10-26부터 기본, native-v1.md "광고 끄기") 광고를 끈 빌드는 보내지 않는다. 광고 미동의 계정에게 그 자리에서 광고 동의 시트를 띄운 열람도 광고를 요청하지 않았으므로 보내지 않는다, 2026-10-26) |

도달률 = `queue_card_view(position=n)` 사용자 수 ÷ `queue_open` 사용자 수(n=1…10), 완주율 = `queue_end` ÷ `queue_open`. 광고 카드는 끝 화면 전이므로 광고 카드에서 닫으면 `queue_exit`(`position` = 마지막 활동 카드 번호)를 보내고 완주로 세지 않는다. 광고 노출률 = `queue_ad_shown` ÷ (`queue_ad_shown` + `queue_ad_skipped_unloaded`). 클릭·수익은 AdMob 보고서로 본다([native-v1.md](native-v1.md) "맞춤 대기열 끝 광고").

### 3. 관심 → 신청 전환
| 이벤트 | 매개변수 |
|---|---|
| `activity_view`(활동 상세 열림) | `activity_id`, `field`, `category`, `source` = `discovery`·`queue`·`favorites`·`link`·`push`·`inbox`(알림 목록 행)·`my_activity`(내 활동 목록) |
| `favorite_add` · `favorite_remove` | `type` = `program`·`activity`·`session`(세션 찜, 2026-10-26 추가 — 아래 "비공개 테스트 성공 지표"; `id` = 세션 id, `activity_id` 함께), `id`, `field`, `category` |
| `notify_level_set` | `type`, `level` = `all`·`application`·`none` |
| `myactivity_add` | `activity_id`, `field`, `category`, `source` |
| `apply_click`(공식 신청 링크) | `activity_id`, `field`, `category`, `source` |
| `push_open`(알림을 눌러 앱 열림) | `kind` = `new`·`application`·`schedule`·`summary` |
| `session_reminder_scheduled`(찜한 세션 이동 알림을 기기에 예약함, 2026-10-26, [native-v1.md](native-v1.md) "찜한 세션 이동 알림") | `count`(이번에 예약한 알림 수; 찜 변경·스위치·포그라운드 복귀로 다시 예약해 실제로 넣을 때마다. 로컬 알림이라 눌러도 `push_open`은 보내지 않는다) |

유입경로 예: `activity_view` → `favorite_add` 또는 `myactivity_add` → `apply_click`.

## 메뉴별 접근률 (2026-10-25 사용자 "각 메뉴별 접근률도")
| 이벤트 | 시점 | 매개변수 |
|---|---|---|
| `tab_select` | 하단 탭을 누름(앱 시작 때 첫 탭은 보내지 않음) | `tab` = `discovery`·`my_activity`·`cards`·`profile` |
| `menu_open` | 탭 안의 주요 메뉴·행을 엶 | `menu` = `my_activity_list`·`favorites`·`queue`·`funnel`·`eligibility`·`interests`·`notifications`·`my_cards`·`received_cards`·`card_create`·`profile_edit`·`history`·`settings`·`notifications_inbox`(알림 목록, 종 버튼)(새 메뉴는 이 목록에 먼저 더한다) |
| `discovery_mode` | 발견 맨 위 `프로그램 \| 이벤트` 전환을 사용자가 바꿈(앱 시작 때 기억된 값은 보내지 않음, 2026-10-26) | `mode` = `program`·`event` |
| `discovery_status_filter` | 발견 필터 시트의 `적용`·칩 ×·빈 결과 버튼으로 `활동 상태` 묶음이 실제로 바뀜(기억된 값 복원·시트 안 체크만은 보내지 않음, 2026-10-26, [native-v1.md](native-v1.md) "활동 상태"·"필터 시트") | `groups` = 고른 묶음 id 쉼표 목록(`available`·`in_progress`·`past` 순, 예 `available,past`; `in_progress`는 2026-10-26 재정의 뒤 `진행·예정`) |
| `discovery_category_filter` | 발견 필터 시트의 `적용`·칩 ×·빈 결과 버튼으로 `종류`가 실제로 바뀜(기억된 값 복원·`프로그램 \| 이벤트` 전환으로 바뀐 선택은 보내지 않음; 이벤트 모드에는 종류 필터가 없다, 2026-10-26, [native-v1.md](native-v1.md) "종류 필터"·"필터 시트") | `categories` = 고른 종류 코드 소문자 쉼표 목록(계약 순서, 예 `bootcamp,club`; 모두 지우면 빈 문자열) |
| `today_card_tap` | 발견 맨 위 `오늘 내 행사` 카드를 누름(2026-10-26, [native-v1.md](native-v1.md) "오늘 내 행사 카드") | `target` = `timetable`(오늘, 블록 시간표 시트)·`progress`(진행 단계)·`qr`(`명함 QR`), `activity_id`, `field`, `category` |
| `contact_verify` | 프로필 수정의 연락처 인증을 끝냄(2026-10-26, [native-v1.md](native-v1.md) "연락처 인증"): GitHub 연결은 콜백 뒤(`complete` 200이 `ok`, 시트 취소·`error=cancelled`는 `cancelled`, 그 밖은 `failed`), 이메일은 `확인`을 눌러 `verify`가 답한 뒤(200 `ok`, 400·그 밖 `failed`; 인증번호 받기만 하고 그만둔 것은 보내지 않음). 주소·사용자명은 넣지 않는다 | `kind` = `github`·`email`, `result` = `ok`·`cancelled`·`failed` |

접근률 = 기간 안 `tab_select(tab=x)` 또는 `menu_open(menu=x)` 사용자 수 ÷ 활성 사용자 수. GA 탐색 › 자유 형식에서 측정기준 `tab`·`menu`, 측정항목 `활성 사용자`로 본다. 매개변수 `tab`, `menu`도 맞춤 측정기준으로 등록한다.

## 이탈률·참여

GA4 기본 `engagement_rate`(참여 세션 비율)과 그 반대인 이탈률을 그대로 쓴다. 화면 이름은 `screen_view`의 `screen_name`에 FSD 페이지 이름(예 `discovery`, `activity_detail`, `queue`, `favorites`, `my_activity`, `profile`)을 넣는다. 화면별 이탈은 GA 탐색 › 경로 탐색에서 본다.

## 구현 메모 (iOS 2026-10-25)
- 개수·순서 매개변수(`index`, `total`, `position`, `size`, `seen`, `viewed`, `scenes`, `*_count`, `found`)는 숫자로 보낸다. `seen`은 오늘 카드 중 이미 하트를 누른 수(관심 없어요는 카드가 빠져 남지 않는다).
- `queue_dismiss`의 `reason` = `dismiss`·`less_field`·`hide_program`·`ineligible`(⋯ 선택; 되돌리기 전에 보낸다). `funnel_step`의 하위 분야 화면(분야 하나씩·한 화면 묶음)은 모두 `subfields`.
- 화면 이름: `discovery`, `activity_detail`, `program_detail`, `queue`, `funnel`, `my_activity`, `activity_progress`, `favorites`, `my_cards`, `received_cards`, `profile`, `settings`, `notifications_inbox`, `timetable`(시간표 모달 시트 — 활동 상세의 일정 행을 눌러 열 때마다, 2026-10-26; 하루 시간표(날짜 행의 `시간표 보기`, 블록 시트의 `이 날 전체 보기`)도 같은 화면 이름이고 시트를 열 때 한 번 — 2026-10-26 "하루 시간표"), `session_favorites`(즐겨찾기 › 내 시간표 — 2026-10-26 표현 변경, 화면 이름은 그대로), `card_qr`(내 명함 QR 시트 — 앱 구현 시, 2026-10-26 "비공개 테스트 성공 지표"). (`favorites_calendar` 화면·메뉴 값은 2026-10-26 즐겨찾기 목록 보기를 없애며 지웠다. 즐겨찾기는 `favorites` 하나다.)
- `menu_open(history)`는 프로필 이력 입력, `menu_open(notifications)`는 프로필의 `기기 알림이 꺼져 있어요` 행(iOS는 알림 묶음이 프로필 안에 펼쳐져 있다).
- `push_open`의 `kind`: 지금 푸시 페이로드에 종류가 없어 묶음 알림(`activityId` 없음)만 `summary`로 보내고 나머지는 `kind` 없이 보낸다. 서버가 페이로드에 종류를 넣으면 채운다.
- `session_reminder_scheduled`(2026-10-26): 찜한 세션 이동 알림은 기기 로컬 알림이라 예약 때만 보내고, 눌러 열릴 때는 `push_open`을 보내지 않는다(상세의 `activity_view`는 source `push`로 간다).

## 웹 (2026-10-26)

사용자 요청 "웹에도 Google Analytics". 같은 GA4 속성에 웹 데이터 스트림을 더한다(`apps/web` `shared/ui/google-analytics.tsx`, 의존성 없이 `next/script` + gtag; GA 웹 측정 ID `G-C8238MCXS6`은 코드에 두지 않고 Vercel 환경 변수로만 넣는다).
- 켜는 조건: Vercel 프로덕션 배포(`VERCEL_ENV=production`)이고 `NEXT_PUBLIC_GA_MEASUREMENT_ID`(`G-…`)가 있을 때만 스크립트를 넣는다. 로컬·프리뷰·빈 값은 아무것도 넣지 않는다. 웹에는 끄는 설정이 없다(처리방침에 브라우저 쿠키 차단으로 거부하는 방법을 적는다).
- 개인정보: `allow_google_signals`·`allow_ad_personalization_signals`는 `false`, 사용자 ID 없음. 사용자 속성은 2026-10-26 웹 로그인부터 `signed_in` 하나만(`shared/lib/analytics.ts` `setSignedIn`; 앱과 같은 규칙으로 로그인 + 현재 동의가 있을 때만 `yes`, 그 외 `no`). 자동 페이지뷰를 끄고 App Router 경로가 바뀔 때마다 `page_view`를 직접 보낸다. `page_location`은 쿼리·프래그먼트를 지우고(명함 `?code=`, 토큰, 검색어 `q`) 개별 경로를 `/cards/[id]`·`/activities/[id]`로 템플릿화한다(명함 id는 한 사람을 가리킨다; 활동별 수는 `activity_view`의 `activity_id`로 본다). 규칙은 `shared/lib/analytics.ts` `analyticsUrl` 하나다. 명함 id·이름·연락처·명함 내용은 어떤 이벤트에도 넣지 않는다.

| 이벤트 | 시점 | 매개변수 |
|---|---|---|
| `page_view` | 경로 변경마다(첫 로드 포함) | `page_location`(위 정리) |
| `card_view` | 공개 명함(`/cards/:id`)을 불러옴 | `activity_id`(QR에 교환 활동이 있을 때만) |
| `card_open_app` | `Dearby 앱에 저장`(앱 링크 `dearby://cards/…`)을 누름 | — |
| `card_save_browser` | `브라우저에 저장`이 저장 목록 확인까지 성공 | — |
| `home_shortcut_prompt_shown` | 홈 화면 바로가기 안내가 보임(화면에 띄울 때마다, [native-v1.md](native-v1.md) "홈 화면 바로가기 안내") | `place`(`card`·`saved`), `kind`(`prompt`·`ios-safari`·`ios-chrome`·`chrome`·`samsung`) |
| `home_shortcut_install` | 안내의 `홈 화면에 추가`(`prompt()`)에 사용자가 답함 | `outcome`(`accepted`·`dismissed`) |
| `activity_view` | 활동 상세(`/activities/:id`)를 불러옴 | `activity_id`, `category`, `source` = `link` |
| `apply_click` | 하단 CTA가 신청 링크일 때 누름(공식 안내 링크는 보내지 않음) | `activity_id`, `category`, `source` = `link` |
| `discovery_status_filter` | 발견 필터 시트 `적용`·칩 ×·`지난 활동도 보기`로 `활동 상태`를 바꿈(앱과 같음) | `groups` |
| `discovery_category_filter` | 발견 필터 시트 `적용`·칩 ×로 `종류`를 바꿈(앱과 같음, 빈 안내의 `종류 필터 지우기` 포함) | `categories` |
| `timetable_view` | 활동 상세에서 일정 행을 눌러 시간표 모달을 열 때마다(2026-10-26, [native-v1.md](native-v1.md) "트랙·세션 시간표"). 하루 시간표 모달(날짜 행의 `시간표 보기`·블록 모달의 `이 날 전체 보기`, "하루 시간표")도 열 때 `activity_id`로 한 번 | `activity_id` |
| `myactivity_add` | 활동 카드·활동 상세의 `+`로 내 활동에 추가 성공(2026-10-26 웹 2단계 B, [native-v1.md](native-v1.md) "웹 로그인" › 2단계 B; 앱과 같은 이름) | `activity_id`, `category`, `source` = `discovery`(발견 카드)·`link`(활동 상세) |
| `stage_mark` | 진행 단계(`/mine/:id`)에서 선발 단계의 상태를 고름(서버 저장 전에 보냄). 지난 선발 단계 통과 제안의 `통과로 표시`는 단계마다 보내며 `via=autopass_prompt` | `activity_id`, `category`, `kind` = `passed`·`waiting`·`failed`·`other`, `via`(제안에서만) |
| `today_card_tap` | 발견 맨 위 `오늘 내 행사` 카드를 누름 | `target` = `timetable`(시간표 모달)·`progress`(진행 단계; 웹은 `qr` 없음), `activity_id`, `category` |
| `tester_cta_tap` | 비공개 테스트 초대의 링크를 누름 — 공개 명함 저장 버튼 아래·`/saved` 상단(비로그인)·로그인 시트의 `비공개 테스트로 앱 받기`(2026-10-26, [native-v1.md](native-v1.md) "명함 교환" › 받는 사람(웹)). iOS는 버튼이 곧 TestFlight 링크, Android·데스크톱은 시트 안의 단계 링크를 누를 때 보낸다(시트를 여는 것만으로는 보내지 않음) | `platform`(`ios`·`android`·`desktop`, UA 판정), `target`(`testflight`·`play_group`·`play_test`) |

Vercel Web Analytics(`@vercel/analytics` `<Analytics />`, 같은 PR에서 사용자 요청): 프로덕션 배포에서만 렌더하고 `beforeSend`로 같은 `analyticsUrl`을 적용해 페이지뷰만 보낸다(쿠키 없음, 커스텀 이벤트 없음 — Hobby 플랜 제한). Speed Insights는 넣지 않는다. 대시보드 프로젝트 › Analytics 사용 설정은 사용자가 켠다.
`activity_view`·`apply_click`은 앱과 같은 이름이라 GA에서 플랫폼(스트림) 측정기준으로 나눠 본다. 웹은 공개 태그 사전을 불러오지 않으므로 `field`를 보내지 않는다.
GA 웹 스트림(2026-10-26 생성)은 **향상된 측정을 끈다**: 켜 두면 GA가 정리 전 URL로 페이지뷰·검색어·아웃바운드 클릭을 보낸다. 신청 링크 클릭은 위 `apply_click`으로만 센다. 데이터 보관 기간은 속성 전체(앱 스트림 포함) 설정이라 탐색 보고서 기간과 함께 사용자가 정한다(2개월·14개월).

## 비공개 테스트 성공 지표 — 2026-10-26 사용자 결정

비공개 테스트의 성공 기준 둘([테스트 계획](../../docs/context/test-plan-2026-10.md)): ① 내 활동에 추가한 활동이 활동 일정(`phase: ACTIVITY`)에 도달하고 참석까지 이어지는가, ② 테스터 1인이 명함 교환(맞교환 완료)을 3회 이상 하는가. 기존 이벤트로 되는 것은 그대로 쓰고(내 활동 추가 `myactivity_add`, 활동 상세 `activity_view(source=my_activity)`, 시간표 `screen_view(timetable)`·웹 `timetable_view`), 아래만 더한다. 앱 구현은 다음 작업이다(iOS·Android 같은 이름; 웹은 2026-10-26 2단계 B부터 내 활동이 있어 `myactivity_add`·`stage_mark`·`today_card_tap`만 "웹" 표대로 보내고, 지갑·`participation_reached`·`activity_day_action`은 아직 없다). 개인정보 원칙은 위와 같다 — 활동 id·세션 id·개수만 보내고 명함 id·명함 내용·상대 계정은 어떤 매개변수에도 넣지 않는다.

### 지표 1 — 추가한 활동이 참석까지
| 이벤트 | 시점 | 매개변수 |
|---|---|---|
| `stage_mark` | 진행 단계 화면에서 선발 단계(`SELECTION`)의 상태를 고름(기본 대기·통과·탈락과 어드민 지정 상태 모두; 서버 저장 전에 보냄). 지난 선발 단계 통과 제안(native-v1.md "지난 선발 단계 통과 제안" 2026-10-26)의 `통과로 표시`도 표시한 단계마다 보내며 `via=autopass_prompt`를 더한다(진행 단계 화면에서 고른 것은 `via` 없음) | `activity_id`, `kind` = `passed`·`waiting`·`failed`·`other`(일정 상태의 `kind` 소문자), `via`(제안에서만) = `autopass_prompt` |
| `participation_reached` | 내 활동의 활동이 처음 **참여 중**이 됨 — `via=selection_passed`: 선발 단계가 모두 `통과`로 표시되고 뒤에 활동 단계가 남음(진행 트랙의 `참여 중` 배지와 같은 조건, 표시를 바꾼 직후 판정). `via=no_selection`: 선발 단계가 하나도 없는 활동(모집 슬롯도 `SELECTION` 세부 일정도 없음)을 내 활동에 추가함(`myactivity_add`와 함께; 모집 슬롯이 있는 활동은 컨퍼런스라도 해당하지 않는다 — 운영의 우아콘 2026(id 156)은 `신청 기간`·`입장권 추첨 결과 발송`이 `SELECTION`이다, 2026-10-26 확인). 활동마다 기기에 보냈음을 기록해 한 번만 보낸다(표시를 되돌렸다 다시 통과해도 다시 보내지 않음) | `activity_id`, `via` = `selection_passed`·`no_selection` |
| `activity_day_action` | 내 활동에 있는 활동의 `ACTIVITY` 일정 블록(활동 기간·`ACTIVITY` 세부 일정)이 진행 중인 날(서울 날짜, 블록 시작일~끝일)에 그 활동에서 한 행동. 탈락 표시한 활동은 보내지 않음. 같은 날 반복해도 매번 보내고, 지표는 사용자 수로 센다 | `activity_id`, `action` = `progress`(진행 단계 화면 열기)·`detail`(활동 상세 열기)·`timetable`(시간표 시트 열기)·`session_favorite`(세션 찜) |
| `favorite_add` · `favorite_remove`(확장) | `type` = `session`을 더한다: 시간표·내 시간표(찜한 세션)에서 세션 하트 | `type` = `session`, `id` = 세션 id, `activity_id`(세션이 속한 활동), `field`·`category`는 그 활동 것 |

- 지표 1 계산: 분모 = 기간 안 `myactivity_add` 사용자 수(활동별 `activity_id`), 단계 = `participation_reached` 사용자 수, 분자 = `activity_day_action` 사용자 수. 유입경로 탐색 `myactivity_add` → `participation_reached` → `activity_day_action`을 `activity_id`·`signed_in=yes`로 걸러 본다. 선발 없는 활동은 두 번째 단계가 추가와 같은 순간이므로 "추가 → 당일 행동"만 뜻이 있다.
- "활동 일정 시작 시각이 지났고 탈락 표시가 없음"이라는 수동 참석 판정은 앱 이벤트가 아니라 서버 기록(`user_activities`·`user_activity_schedule_statuses`·`schedules.phase`)으로 센다(테스트 계획 "서버 기록으로 대조").

### 지표 2 — 명함 교환 3회
| 이벤트 | 시점 | 매개변수 |
|---|---|---|
| `card_received` | QR·링크로 받은 명함을 계정 지갑에 저장 성공(`PUT /v1/wallet/cards/:cardId` 201; 이미 있어 200이면 보내지 않음). 웹 비로그인 저장은 기존 `card_save_browser` | `activity_id`(교환 활동이 있을 때), `reciprocal` = `yes`·`no`(응답 Receipt의 `reciprocal`) |
| `card_exchange_completed` | 받은 명함 하나가 **맞교환**(`Receipt.reciprocal: true`)이 됨. 기기에 "맞교환 상태였던 받은 명함 id 집합"을 두고 지갑을 불러올 때마다 비교해, 새로 맞교환이 된 항목마다 1회(내가 `내 명함도 건네기`에 성공한 직후의 다시 불러오기, 상대가 내 명함을 받아 맞교환이 된 것을 내가 지갑을 열어 알게 된 때, 저장하자마자 이미 맞교환인 때 모두 이 규칙 하나로 잡힌다). 집합은 로그아웃하면 지운다 | `activity_id`(그 Receipt의 교환 활동, 있을 때), `via` = `reciprocate`(직전에 내가 건넴)·`wallet`(그 밖) |
| `card_exchange_milestone` | 아래 `card_exchanges`가 처음 3에 이름(기기에 기록해 계정마다 한 번만) | `count` = 3(숫자) |

- 사용자 속성 `card_exchanges`: 지갑의 맞교환 항목 수(`reciprocal: true`인 Receipt 수; 회수·철회된 것은 목록에 없으므로 빠진다). 지갑을 불러올 때마다 다시 계산해 설정하고, 로그아웃하면 지운다(`signed_in`과 같이). 한 사람이 명함 두 장을 건넸으면 2로 세는 한계가 있고, 정확한 "상대 계정 수"는 서버 `receipts`로 대조한다.
- 화면 이름 `card_qr`(내 명함 QR 시트)를 `screen_view`에 더한다. 교환 깔때기: `screen_view(card_qr)` → 웹 `card_view`·`card_open_app` → `card_received` → `card_exchange_completed` → `card_exchange_milestone`.
- 지표 2 계산: `card_exchange_milestone` 사용자 수 ÷ 기간 안 활성 사용자 수(`signed_in=yes`). 분포는 자유 형식 탐색에서 측정기준 `card_exchanges`, 측정항목 활성 사용자. 사용자 수는 Analytics 앱 인스턴스(기기) 단위이고 사용자 ID를 보내지 않으므로, 한 테스터가 두 기기를 쓰면 둘로 보인다.

## GA 콘솔에서 할 일(사용자)
맞춤 정의 › 맞춤 측정기준에 위 사용자 속성 4개(사용자 범위)와 매개변수 `step`, `entry`, `tab`, `menu`, `position`, `field`, `category`, `source`, `type`, `level`, `kind`, `reason`, `groups`, `status`(이벤트 범위; `status`는 iOS `att_prompt_result`를 넣을 때)을 등록한다. 등록 전 데이터는 보고서 측정기준으로 쓸 수 없다.
비공개 테스트 지표(2026-10-26)용으로 `activity_id`, `via`, `action`, `reciprocal`(이벤트 범위)과 `card_exchanges`(사용자 범위)를 더 등록한다. 웹 `tester_cta_tap`의 `platform`·`target`(이벤트 범위)도 등록한다(`platform`은 GA 기본 '플랫폼' 측정기준과 이름이 겹치므로 표시 이름을 구분한다). `activity_id`는 기존 `activity_view`·`myactivity_add`에도 있지만 등록되어 있지 않아 활동별로 나눠 볼 수 없었다 — 10/28 우아콘 전에 등록해야 그날 데이터를 활동별로 볼 수 있다. `count`는 값이 3 하나라 등록하지 않아도 된다.
