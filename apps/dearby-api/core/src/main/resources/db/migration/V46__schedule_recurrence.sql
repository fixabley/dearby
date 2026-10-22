-- 2026-10-22 사용자 결정(native-v1.md "반복 일정과 겹침 결과 묶음"): RFC 5545 RRULE 본문(접두사 없음, 서버가 정규화한 허용 부분집합)
alter table schedules
    add column recurrence varchar(200);
