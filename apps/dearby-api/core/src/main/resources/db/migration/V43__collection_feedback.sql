-- 2026-10-20 수집 피드백: 운영자가 어드민에서 바로잡은 것을 수집 에이전트가 검색해 판단에 쓴다(search_feedback 도구, 지시문의 최근 10건, 제출 판정).
-- kind: DATE_CURATED(날짜 문구 → 운영자가 지정한 날짜), PERIOD_SWAP(모집·활동 기간 바꿈), FIELD_CURATED(필드의 수집 값 → 운영자 값),
-- DRAFT_DISMISSED(수집 초안 삭제), WATCH_ALIAS·WATCH_EXCLUDE(조심 목록). source_text는 수집기가 본 원문(문구·이름·수집 값)이고 글자 세 개 단위 유사도로 찾는다.
-- activity_id는 참조 키가 아니다(삭제된 활동의 피드백도 남는다)
create table collection_feedback
(
    id              bigint generated always as identity primary key,
    kind            varchar(20)              not null check (kind in ('DATE_CURATED', 'PERIOD_SWAP', 'FIELD_CURATED', 'DRAFT_DISMISSED', 'WATCH_ALIAS', 'WATCH_EXCLUDE')),
    program_id      bigint references programs (id) on delete set null,
    host            varchar(253),
    source_text     varchar(1000)            not null,
    corrected_value jsonb                    not null default '{}'::jsonb,
    reason          varchar(500),
    activity_id     bigint,
    created_at      timestamp with time zone not null
);
create index collection_feedback_source_text_trgm_idx on collection_feedback using gin (source_text gin_trgm_ops);
create index collection_feedback_program_kind_idx on collection_feedback (program_id, kind);
