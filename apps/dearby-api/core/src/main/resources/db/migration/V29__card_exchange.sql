-- 명함 교환(native-v1.md "명함 교환", 2026-10-19 사용자 결정): 받은 명함에 교환한 활동만 남기고, 단방향은 30일 뒤 회수한다.
-- 회수는 recalled_at을 적고 행은 보존한다. 목록·개수는 recalled_at이 null인 행만 센다.
alter table receipts
    add column recalled_at timestamp with time zone;

-- 비로그인 저장도 같은 규칙. 기존 행의 saved_at은 이 마이그레이션 시각이 되어 30일 뒤 회수된다
alter table guest_cards
    add column activity_id bigint, -- 논리키 activities. 모르는 id는 null로 저장한다
    add column saved_at    timestamp with time zone not null default now(),
    add column recalled_at timestamp with time zone;

create index receipts_recall_idx on receipts (received_at) where recalled_at is null;
create index guest_cards_recall_idx on guest_cards (saved_at) where recalled_at is null;
