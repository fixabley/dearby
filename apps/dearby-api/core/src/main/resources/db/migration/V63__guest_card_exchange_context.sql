-- 2026-10-26 사용자 결정(native-v1.md "명함 교환" → "교환 세션 맥락"): 받은 명함에 어느 일정 블록·세션에서 받았는지를 저장 시각으로 판정해 남긴다.
-- 로그인 지갑은 receipts.context jsonb에 scheduleId·sessionId를 더 쓰므로 마이그레이션이 없다. 비로그인 저장은 activity_id처럼 논리키 컬럼으로 둔다.
-- 블록·세션이 지워져도 id는 남고 응답의 제목만 null이 된다(활동과 같은 규칙)
alter table guest_cards
    add column schedule_id bigint, -- 논리키 schedules
    add column session_id  bigint; -- 논리키 schedule_sessions
