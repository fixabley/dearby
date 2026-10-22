-- 명함 신고·차단(native-v1.md "명함 신고·차단", admin-v1.md "명함 신고"). 명함·사용자는 논리키(주인이 지워도 신고 근거가 남는다).
-- 게스트는 세션 digest(guest_sessions.digest)로 가리킨다. 코드만으로 본 사람의 신고는 신고자가 없다
create table card_reports
(
    id                    uuid primary key,
    card_id               uuid                     not null,
    reporter_user_id      uuid,
    reporter_guest_digest varchar(64),
    reason                varchar(20)              not null check (reason in ('SPAM', 'IMPERSONATION', 'INAPPROPRIATE', 'OTHER')),
    detail                varchar(500),
    card_snapshot         jsonb                    not null,
    status                varchar(10)              not null default 'OPEN' check (status in ('OPEN', 'RESOLVED', 'DISMISSED')),
    created_at            timestamp with time zone not null,
    resolved_at           timestamp with time zone,
    resolved_by           uuid,
    note                  varchar(1000),
    check (num_nonnulls(reporter_user_id, reporter_guest_digest) <= 1)
);
create index card_reports_card_id_idx on card_reports (card_id);
create index card_reports_status_idx on card_reports (status, created_at desc);

-- 차단 단위는 명함 주인(계정). 차단한 쪽은 계정 또는 게스트 세션 중 하나
create table card_blocks
(
    id                   uuid primary key,
    blocker_user_id      uuid,
    blocker_guest_digest varchar(64),
    blocked_user_id      uuid                     not null,
    name                 varchar(200)             not null,
    created_at           timestamp with time zone not null,
    check (num_nonnulls(blocker_user_id, blocker_guest_digest) = 1)
);
create unique index card_blocks_user_idx on card_blocks (blocker_user_id, blocked_user_id) where blocker_user_id is not null;
create unique index card_blocks_guest_idx on card_blocks (blocker_guest_digest, blocked_user_id) where blocker_guest_digest is not null;
create index card_blocks_blocked_idx on card_blocks (blocked_user_id);
