-- 2026-10-19 세부 일정 편집기(shared/contracts/admin-v1.md "세부 일정 편집기")
-- 운영자가 지운 세부 일정은 행을 남기고 표시만 한다: 응답·공개 API에서 빠지고, 수집이 같은 제목·시작의 일정을 다시 넣지 않는다
alter table schedules add column curated_deleted boolean not null default false;
