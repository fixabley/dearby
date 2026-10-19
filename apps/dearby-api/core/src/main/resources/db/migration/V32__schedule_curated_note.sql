-- 2026-10-19 운영자가 기간을 직접 지정할 때 남기는 사유(admin-v1.md "일정 직접 지정" 사유). 지정 해제하면 함께 지운다
alter table schedules add column curated_note varchar(300);
