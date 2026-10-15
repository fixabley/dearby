-- 관리자가 지운 수집 활동의 출처(activities.source_id). 수집 배치는 여기 있는 출처를 다시 초안으로 만들지 않는다(2026-10-15)
create table dismissed_activity_sources
(
    source_id    uuid primary key,
    dismissed_at timestamp with time zone not null
);
