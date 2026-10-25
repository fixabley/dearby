# 앱 사용 통계(Firebase Analytics) 이벤트 사전 — 2026-10-25

사용자 결정: "Analytics SDK 넣어줘"(광고 ID 없이), "분야 등을 지정해서 넣을 수도"(사용자 속성), "퍼널 통과율 같은 것도 GA로 적용해주세요. 이탈률, 도달율도". iOS·Android가 같은 이름을 쓴다(웹은 Analytics를 넣지 않는다). 개인정보: 광고 ID·SSAID·사용자 ID를 보내지 않고, 지원 자격 값(나이·재학 등)·이름·연락처·명함 내용은 어떤 이벤트·속성에도 넣지 않는다. 프로필 `사용 통계 보내기`를 끄면 수집을 멈춘다.

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

도달률 = `queue_card_view(position=n)` 사용자 수 ÷ `queue_open` 사용자 수(n=1…10), 완주율 = `queue_end` ÷ `queue_open`.

### 3. 관심 → 신청 전환
| 이벤트 | 매개변수 |
|---|---|
| `activity_view`(활동 상세 열림) | `activity_id`, `field`, `category`, `source` = `discovery`·`queue`·`favorites`·`link`·`push` |
| `favorite_add` · `favorite_remove` | `type` = `program`·`activity`, `id`, `field`, `category` |
| `notify_level_set` | `type`, `level` = `all`·`application`·`none` |
| `myactivity_add` | `activity_id`, `field`, `category`, `source` |
| `apply_click`(공식 신청 링크) | `activity_id`, `field`, `category`, `source` |
| `push_open`(알림을 눌러 앱 열림) | `kind` = `new`·`application`·`schedule`·`summary` |

유입경로 예: `activity_view` → `favorite_add` 또는 `myactivity_add` → `apply_click`.

## 이탈률·참여

GA4 기본 `engagement_rate`(참여 세션 비율)과 그 반대인 이탈률을 그대로 쓴다. 화면 이름은 `screen_view`의 `screen_name`에 FSD 페이지 이름(예 `discovery`, `activity_detail`, `queue`, `favorites`, `favorites_calendar`, `my_activity`, `profile`)을 넣는다. 화면별 이탈은 GA 탐색 › 경로 탐색에서 본다.

## GA 콘솔에서 할 일(사용자)
맞춤 정의 › 맞춤 측정기준에 위 사용자 속성 4개(사용자 범위)와 매개변수 `step`, `entry`, `position`, `field`, `category`, `source`, `type`, `level`, `kind`, `reason`(이벤트 범위)을 등록한다. 등록 전 데이터는 보고서 측정기준으로 쓸 수 없다.
