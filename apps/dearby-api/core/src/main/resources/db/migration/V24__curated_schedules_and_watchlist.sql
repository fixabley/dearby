-- 2026-10-18 어드민 일정 직접 지정과 조심 목록(shared/contracts/admin-v1.md "일정 직접 지정과 조심 목록")

-- 운영자가 직접 지정한 일정. 수집기가 초안을 갱신할 때 덮어쓰지 않는다
alter table schedules add column curated boolean not null default false;

-- 조심 목록: 프로그램별로 수집기가 피할 이름. ALIAS는 정본 활동의 다른 표기(정본 초안을 갱신), EXCLUDE는 이 프로그램 활동이 아닌 이름(저장하지 않음)
create table collection_watchlist
(
    id                    bigint generated always as identity primary key,
    program_id            bigint                   not null references programs (id) on delete cascade,
    name                  varchar(200)             not null,
    normalized_name       varchar(200)             not null, -- 소문자·공백 제거
    kind                  varchar(10)              not null check (kind in ('ALIAS', 'EXCLUDE')),
    canonical_activity_id bigint references activities (id) on delete set null,
    reason                varchar(500),
    created_at            timestamp with time zone not null,
    unique (program_id, normalized_name)
);
