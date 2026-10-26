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
| `queue_ad_skipped_unloaded` | 마지막 카드에서 넘겼는데 광고가 로드돼 있지 않거나 실패해 광고 카드를 건너뜀 | `reason` = `loading`(아직 요청 중)·`failed` (Release에 광고 ID가 없어 광고를 끈 빌드는 보내지 않는다. 광고 미동의 계정에게 그 자리에서 광고 동의 시트를 띄운 열람도 광고를 요청하지 않았으므로 보내지 않는다, 2026-10-26) |

도달률 = `queue_card_view(position=n)` 사용자 수 ÷ `queue_open` 사용자 수(n=1…10), 완주율 = `queue_end` ÷ `queue_open`. 광고 카드는 끝 화면 전이므로 광고 카드에서 닫으면 `queue_exit`(`position` = 마지막 활동 카드 번호)를 보내고 완주로 세지 않는다. 광고 노출률 = `queue_ad_shown` ÷ (`queue_ad_shown` + `queue_ad_skipped_unloaded`). 클릭·수익은 AdMob 보고서로 본다([native-v1.md](native-v1.md) "맞춤 대기열 끝 광고").

### 3. 관심 → 신청 전환
| 이벤트 | 매개변수 |
|---|---|
| `activity_view`(활동 상세 열림) | `activity_id`, `field`, `category`, `source` = `discovery`·`queue`·`favorites`·`link`·`push`·`inbox`(알림 목록 행)·`my_activity`(내 활동 목록) |
| `favorite_add` · `favorite_remove` | `type` = `program`·`activity`, `id`, `field`, `category` |
| `notify_level_set` | `type`, `level` = `all`·`application`·`none` |
| `myactivity_add` | `activity_id`, `field`, `category`, `source` |
| `apply_click`(공식 신청 링크) | `activity_id`, `field`, `category`, `source` |
| `push_open`(알림을 눌러 앱 열림) | `kind` = `new`·`application`·`schedule`·`summary` |

유입경로 예: `activity_view` → `favorite_add` 또는 `myactivity_add` → `apply_click`.

## 메뉴별 접근률 (2026-10-25 사용자 "각 메뉴별 접근률도")
| 이벤트 | 시점 | 매개변수 |
|---|---|---|
| `tab_select` | 하단 탭을 누름(앱 시작 때 첫 탭은 보내지 않음) | `tab` = `discovery`·`my_activity`·`cards`·`profile` |
| `menu_open` | 탭 안의 주요 메뉴·행을 엶 | `menu` = `my_activity_list`·`favorites`·`queue`·`funnel`·`eligibility`·`interests`·`notifications`·`my_cards`·`received_cards`·`card_create`·`profile_edit`·`history`·`settings`·`notifications_inbox`(알림 목록, 종 버튼)(새 메뉴는 이 목록에 먼저 더한다) |
| `discovery_mode` | 발견 맨 위 `프로그램 \| 이벤트` 전환을 사용자가 바꿈(앱 시작 때 기억된 값은 보내지 않음, 2026-10-26) | `mode` = `program`·`event` |

접근률 = 기간 안 `tab_select(tab=x)` 또는 `menu_open(menu=x)` 사용자 수 ÷ 활성 사용자 수. GA 탐색 › 자유 형식에서 측정기준 `tab`·`menu`, 측정항목 `활성 사용자`로 본다. 매개변수 `tab`, `menu`도 맞춤 측정기준으로 등록한다.

## 이탈률·참여

GA4 기본 `engagement_rate`(참여 세션 비율)과 그 반대인 이탈률을 그대로 쓴다. 화면 이름은 `screen_view`의 `screen_name`에 FSD 페이지 이름(예 `discovery`, `activity_detail`, `queue`, `favorites`, `my_activity`, `profile`)을 넣는다. 화면별 이탈은 GA 탐색 › 경로 탐색에서 본다.

## 구현 메모 (iOS 2026-10-25)
- 개수·순서 매개변수(`index`, `total`, `position`, `size`, `seen`, `viewed`, `scenes`, `*_count`, `found`)는 숫자로 보낸다. `seen`은 오늘 카드 중 이미 하트를 누른 수(관심 없어요는 카드가 빠져 남지 않는다).
- `queue_dismiss`의 `reason` = `dismiss`·`less_field`·`hide_program`·`ineligible`(⋯ 선택; 되돌리기 전에 보낸다). `funnel_step`의 하위 분야 화면(분야 하나씩·한 화면 묶음)은 모두 `subfields`.
- 화면 이름: `discovery`, `activity_detail`, `program_detail`, `queue`, `funnel`, `my_activity`, `activity_progress`, `favorites`, `my_cards`, `received_cards`, `profile`, `settings`, `notifications_inbox`. (`favorites_calendar` 화면·메뉴 값은 2026-10-26 즐겨찾기 목록 보기를 없애며 지웠다. 즐겨찾기는 `favorites` 하나다.)
- `menu_open(history)`는 프로필 이력 입력, `menu_open(notifications)`는 프로필의 `기기 알림이 꺼져 있어요` 행(iOS는 알림 묶음이 프로필 안에 펼쳐져 있다).
- `push_open`의 `kind`: 지금 푸시 페이로드에 종류가 없어 묶음 알림(`activityId` 없음)만 `summary`로 보내고 나머지는 `kind` 없이 보낸다. 서버가 페이로드에 종류를 넣으면 채운다.

## 웹 (2026-10-26)

사용자 요청 "웹에도 Google Analytics". 같은 GA4 속성에 웹 데이터 스트림을 더한다(`apps/web` `shared/ui/google-analytics.tsx`, 의존성 없이 `next/script` + gtag; GA 웹 측정 ID `G-C8238MCXS6`은 코드에 두지 않고 Vercel 환경 변수로만 넣는다).
- 켜는 조건: Vercel 프로덕션 배포(`VERCEL_ENV=production`)이고 `NEXT_PUBLIC_GA_MEASUREMENT_ID`(`G-…`)가 있을 때만 스크립트를 넣는다. 로컬·프리뷰·빈 값은 아무것도 넣지 않는다. 웹에는 끄는 설정이 없다(처리방침에 브라우저 쿠키 차단으로 거부하는 방법을 적는다).
- 개인정보: `allow_google_signals`·`allow_ad_personalization_signals`는 `false`, 사용자 ID·사용자 속성 없음. 자동 페이지뷰를 끄고 App Router 경로가 바뀔 때마다 `page_view`를 직접 보낸다. `page_location`은 쿼리·프래그먼트를 지우고(명함 `?code=`, 토큰, 검색어 `q`) 개별 경로를 `/cards/[id]`·`/activities/[id]`로 템플릿화한다(명함 id는 한 사람을 가리킨다; 활동별 수는 `activity_view`의 `activity_id`로 본다). 규칙은 `shared/lib/analytics.ts` `analyticsUrl` 하나다. 명함 id·이름·연락처·명함 내용은 어떤 이벤트에도 넣지 않는다.

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

Vercel Web Analytics(`@vercel/analytics` `<Analytics />`, 같은 PR에서 사용자 요청): 프로덕션 배포에서만 렌더하고 `beforeSend`로 같은 `analyticsUrl`을 적용해 페이지뷰만 보낸다(쿠키 없음, 커스텀 이벤트 없음 — Hobby 플랜 제한). Speed Insights는 넣지 않는다. 대시보드 프로젝트 › Analytics 사용 설정은 사용자가 켠다.
`activity_view`·`apply_click`은 앱과 같은 이름이라 GA에서 플랫폼(스트림) 측정기준으로 나눠 본다. 웹은 공개 태그 사전을 불러오지 않으므로 `field`를 보내지 않는다.
GA 웹 스트림(2026-10-26 생성)은 **향상된 측정을 끈다**: 켜 두면 GA가 정리 전 URL로 페이지뷰·검색어·아웃바운드 클릭을 보낸다. 신청 링크 클릭은 위 `apply_click`으로만 센다. 데이터 보관 기간은 속성 전체(앱 스트림 포함) 설정이라 탐색 보고서 기간과 함께 사용자가 정한다(2개월·14개월).

## GA 콘솔에서 할 일(사용자)
맞춤 정의 › 맞춤 측정기준에 위 사용자 속성 4개(사용자 범위)와 매개변수 `step`, `entry`, `tab`, `menu`, `position`, `field`, `category`, `source`, `type`, `level`, `kind`, `reason`, `status`(이벤트 범위; `status`는 iOS `att_prompt_result`를 넣을 때)을 등록한다. 등록 전 데이터는 보고서 측정기준으로 쓸 수 없다.
