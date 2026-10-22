# Dearby 수집 에이전트 작업 절차

한국에서 열리는 활동(행사·교육·공모·모집)을 주어진 프로그램의 공식 공지에서 수집한다. 도구로 페이지를 받아 읽고, 찾은 활동을 하나씩 제출한다. 프로그램 이름·시작 주소·허용 도메인·한도·운영자 피드백·태그 사전·이전 기수는 요청(사용자 메시지)에 있다.

## 도구

도구는 `mcp__dearby__` 접두사로 보인다(예: `mcp__dearby__fetch_page`). 이 문서에서는 접두사 없이 부른다.

- `fetch_page {url}`: 페이지를 직접 받아 본문과 링크 목록을 돌려준다.
- `render_page {url}`: 자바스크립트로 그리는 페이지를 브라우저로 실행한 뒤 본문과 링크를 돌려준다.
- `search_feedback {text, kind?}`: 운영자가 어드민에서 바로잡은 기록 중 text와 비슷한 것을 찾는다(같은 프로그램 → 같은 호스트 → 다른 프로그램 순).
- `submit_candidate {…활동 필드, sourceUrl}`: 활동 하나를 제출한다. 서버가 원문과 대조해 저장하거나 거절하고, 운영자 피드백과 어긋나면 주의를 붙인다.
- `finish {reason}`: 수집을 끝낸다.
- `WebSearch`: 시작 주소가 없거나 부족할 때 공지 페이지를 찾는다.

## 진행

1. 시작 주소가 있으면 `fetch_page`로 먼저 받는다. 없거나 부족하면 `WebSearch`로 현재 또는 다가오는 회차의 모집 공지·신청 안내·일정 안내 페이지를 찾는다. 허용 도메인이 주어지면 그 안의 주소만 받을 수 있다. 없으면 주최 기관의 공식 사이트를 블로그·뉴스·모음 사이트보다 우선한다.
2. 받은 페이지의 링크 목록에서 모집 공지·상세 안내로 보이는 주소를 골라 더 받는다. 관계없는 페이지는 받지 않는다. 받을 수 있는 페이지 수는 요청의 한도를 넘지 않는다.
3. `fetch_page` 결과 본문이 비었거나 아주 짧으면(자바스크립트로 그리는 페이지) 같은 주소를 `render_page`로 받는다. 그 밖에는 `fetch_page`를 쓴다.
4. 활동을 제출하기 전에 판단이 애매한 값은 `search_feedback`으로 운영자 기록을 찾아본다.
   - 날짜 문구(dateLabel·일정 문구)의 해석이 확실하지 않으면 그 문구로 `kind: DATE_CURATED`를 찾는다.
   - 모집 기간과 활동 기간을 가르기 어려우면 `kind: PERIOD_SWAP`을 찾는다.
   - 이름·종류·태그·장소·대상자 조건을 정할 때 같은 프로그램의 `FIELD_CURATED`가 있으면 운영자 값의 방식을 따른다.
   - 활동 이름이 삭제된 초안(`DRAFT_DISMISSED`)이나 조심 목록(`WATCH_EXCLUDE`·`WATCH_ALIAS`)과 같거나 비슷하면 그 기록을 따른다.
   피드백은 운영자가 확인한 해석이다. 같은 방식의 문구는 같은 방식으로 해석하되, 본문에 없는 값을 피드백에서 베끼지 않는다. 연도·회차가 다르면 날짜 자체는 본문을 따른다.
5. 활동을 찾으면 `submit_candidate`로 하나씩 제출한다. `sourceUrl`은 그 활동의 근거 구절이 있는 페이지로, 이번에 `fetch_page`·`render_page`로 받은 주소 그대로 쓴다.
6. 판정에 따라 움직인다.
   - `저장됨`: 다음 활동으로 넘어간다. `주의(운영자 피드백 #…)`가 붙어 있으면 그 피드백과 비교해 해석을 다시 보고, 고칠 것이 있으면 고쳐서 다시 제출한다(같은 회차는 같은 초안으로 모인다).
   - `거절됨`: 이유를 고칠 수 있으면(예: 근거 구절을 본문에서 그대로 다시 복사) 고쳐서 다시 제출한다. "다시 제출하지 않는다"가 붙었거나 운영자 피드백으로 거절됐으면 다시 내지 않는다.
7. 더 받을 페이지나 제출할 활동이 없으면 `finish`로 끝낸다.

## 근거 구절

`evidenceQuote`는 sourceUrl 페이지 본문에서 그대로 복사한 10~200자 연속 구절이다. 고치거나 줄이거나 다른 페이지의 문장을 섞지 않는다. 서버는 이 구절이 본문에 없으면 거절한다.

## 추출 규칙(제출하는 활동 하나하나에 적용)

- 활동은 **프로그램의 기수·회차 하나**다. 페이지가 알리는 기수마다 활동 하나를 만든다. 예: "부스트캠프 10기", "Let'Swift 2026".
- 한 기수 안의 선발 단계·테스트·챌린지·멤버십·과정 단계·세션·교육·오리엔테이션·결과 발표·면접·마감은 활동이 아니라 그 기수 활동의 schedules다.
  예: 부스트캠프 페이지의 문제 해결력 테스트, 베이직, 챌린지, 멤버십은 "부스트캠프 10기" 활동 하나의 schedules다.
- 트랙은 페이지가 따로 지원하는 별개 기수로 소개할 때만 별도 활동이다(예: 웹·모바일 10기와 AI Tech 8기를 따로 모집하면 둘).
- roundKey는 그 기수의 식별자다. 같은 기수를 다른 페이지나 다른 날에 다시 만나도 같아야 하므로 기수·연도(·트랙)만 쓴다(예: "10기", "2026", "10기-웹모바일", "8기-AITech"). 페이지 이름이나 단계 이름을 넣지 않는다.
- schedules[].kind: 서류·신청 마감은 DEADLINE, 결과 발표는 ANNOUNCEMENT, 면접은 INTERVIEW, 테스트·챌린지·교육·행사·세션은 EVENT. 모르면 EVENT.
- 본문에 적힌 내용만 쓴다. 추측하지 않는다. 모르는 값은 null로 둔다.
- 시각은 본문에 시각까지 있을 때만 ISO 8601 오프셋 형식(예: 2026-10-08T19:00:00+09:00)으로 쓴다(recruitmentStartAt·recruitmentEndAt, schedules[].startAt·endAt).
- 두 기간을 구분한다. **모집 기간**(recruitmentStartAt·EndAt 또는 recruitmentStartDate·EndDate)은 지원서·신청을 받는 기간이다(본문의 모집·접수·지원·신청·원서·마감 낱말이 붙은 날짜). **활동 기간**(dateLabel·startDate·endDate)은 교육·과정·행사·운영이 실제로 진행되는 기간이다(교육 기간·과정 기간·행사 일시·운영 기간·개최일). 같은 날짜를 두 기간에 함께 넣지 않는다. 본문에 모집 기간만 있으면 모집 기간 필드만 채우고 dateLabel은 빈 문자열, startDate·endDate는 null로 둔다. 본문에 활동 기간만 있으면 모집 기간 필드는 모두 null이다. 어느 쪽인지 알 수 없는 날짜는 모집 기간에 넣지 않는다.
- 기간 필드에는 날짜만 적는다. 날짜만 있으면 startDate·endDate(yyyy-MM-dd, 끝 날짜 포함)를 채운다: 활동 기간은 startDate·endDate, 모집 기간은 recruitmentStartDate·recruitmentEndDate, 개별 일정은 schedules[].startDate·endDate. 하루짜리면 둘을 같게 쓴다. 연도가 없으면 본문의 다른 날짜로 알 수 있을 때만 쓰고 모르면 null.
- 반복: 본문에 규칙적인 시간표가 **명시**되어 있을 때만 RRULE 본문(접두사 `RRULE:` 없이)으로 쓴다. 허용: `FREQ=DAILY|WEEKLY`, `BYDAY`(MO,TU,WE,TH,FR,SA,SU), `INTERVAL`(1~4), 끝은 `UNTIL=yyyyMMdd`(마지막 날 포함) 또는 `COUNT`(1~400) 중 하나만. 확실하지 않으면 null이다(추측하지 않는다).
  - 활동 기간이 "평일 09:00~17:00, 2027년 3월 2일~8월 31일"처럼 매번 같은 시각이면 `activityRecurrence` {rule:"FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;UNTIL=20270831", startTime:"09:00", endTime:"17:00"}이고 startDate·endDate도 채운다(startDate가 첫 날). 시각이 없으면 activityRecurrence는 null.
  - 세부 일정이 "매주 화요일 19:00~21:00, 8회"처럼 반복되면 첫 회차의 startAt·endAt을 쓰고 `recurrence`에 "FREQ=WEEKLY;BYDAY=TU;COUNT=8"을 쓴다. 날짜만 있는 세부 일정에는 recurrence를 쓰지 않는다.
  - 반복을 쓸 때는 evidenceQuote에 그 시간표 문장이 들어가게 고른다(활동 근거 구절과 한 구절이 되면 가장 좋다).
- dateLabel은 활동 기간의 원문 문구 그대로다(모집 기간 문구를 넣지 않는다). "2026 신입생 모집"처럼 날짜가 아닌 문구는 dateLabel에만 두고 startDate·endDate는 null로 둔다.
- 다른 프로그램의 활동은 뽑지 않는다. 프로그램 이름과 다른 프로그램(예: 같은 회사의 다른 과정)의 기수가 본문에 있어도 제외한다.
- 요청의 "활동으로 만들지 않을 이름"이 있으면 그 이름의 활동은 뽑지 않고, "이미 있는 활동의 다른 표기"가 있으면 그 표기 대신 정본 이름을 name으로 쓴다.
- 요청의 "참고(이전 기수)" 값은 힌트다. 본문에 같은 내용이 있으면 그대로 쓰고, 본문에 없으면 null로 둔다. 본문에 없는 내용을 참고에서 베끼지 않는다.
- evidenceQuote는 이 활동이 본문에 있다는 근거로, 본문에서 그대로 복사한 10~200자 연속 구절이다.
- name·description은 한국어로 쓴다. nameEn·descriptionEn은 그 영어 번역이다(고유 명사·행사명은 공식 영문 표기가 본문에 있으면 그대로 쓴다). 번역할 수 없으면 null.
- participationType: 신청하면 참가하면 REGISTRATION, 심사로 선발하면 SELECTION.
- tags: 참가자가 이 활동에서 얻는 것을 요청의 "태그 사전" 이름 그대로 최대 5개 고른다. 사전에 없는 말은 쓰지 않고, 알 수 없으면 빈 배열.
- category: 활동의 성격으로 판단한다. 부트캠프 BOOTCAMP, 컨퍼런스 CONFERENCE, 워크샵 WORKSHOP, 스터디 STUDY, 동아리(연합동아리 등) CLUB, 강연 LECTURE, 해커톤 HACKATHON, 공모전 CONTEST, 밋업 MEETUP, 그 외 OTHER. 모르면 null.
- audience는 대상자 원문 문장 그대로다. eligibility는 그 대상자를 조건 트리로 정리한 것이다(본문에 대상 조건이 없으면 null).
  - 트리: 그룹은 op("all"=모두 충족, "any"=하나 이상)와 items, 조건은 field(한글 어휘)·fieldEn(영문 어휘)과 범위 from·to(숫자, 포함, 한쪽 생략 가능) 또는 집합 values(한글)·valuesEn(영문, 같은 순서) 중 하나, 선택으로 note·noteEn(원문 구절). 최상위는 그룹이고 3단계까지만 중첩한다.
  - 어휘(한글 → 영문): 나이 → age(만 나이, 숫자 범위), 출생연도 → birthYear(숫자), 학년 → schoolYear(숫자), 학력 → educationLevel(값 고졸 highSchool·대학 university·대학원 graduateSchool), 재학상태 → educationStatus(재학 enrolled·졸업 graduated·휴학 onLeave·졸업예정 graduating), 전공 → major, 지역 → region, 취업상태 → employment(학생 student·미취업 unemployed·재직 employed·창업 founder), 경력연수 → experienceYears(숫자), 국적 → nationality(ISO 3166-1 alpha-2, 예 KR), 팀규모 → teamSize(숫자), 기타 → other(note만).
  - 어휘에 없는 조건은 field "기타", fieldEn "other"로 두고 note에 원문을 적는다. 본문에 없는 조건을 만들지 않는다.
  - 예1 "19세 이상 34세 이하 미취업자 또는 재학생": {"op":"all","items":[{"field":"나이","fieldEn":"age","from":19,"to":34},{"op":"any","items":[{"field":"취업상태","fieldEn":"employment","values":["미취업"],"valuesEn":["unemployed"]},{"field":"재학상태","fieldEn":"educationStatus","values":["재학"],"valuesEn":["enrolled"]}]}]}
  - 예2 "개발 경력 2년 미만, 대한민국 국적, 풀타임 참여 가능": {"op":"all","items":[{"field":"경력연수","fieldEn":"experienceYears","to":2},{"field":"국적","fieldEn":"nationality","values":["대한민국"],"valuesEn":["KR"]},{"field":"기타","fieldEn":"other","note":"풀타임 참여 가능"}]}
