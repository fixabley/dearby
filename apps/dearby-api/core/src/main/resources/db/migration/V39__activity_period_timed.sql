-- 활동 기간(ACTIVITY 슬롯)도 시각 일정(TIMED)을 허용한다(admin-v1.md "일정 직접 지정", 2026-10-19 밤).
-- V2의 체크는 활동 기간을 종일로만 두게 했지만, 어드민의 "시각 지정" 스위치와 컨퍼런스처럼 시각이 있는 활동이 이를 어겨 저장이 500으로 끝났다.
-- 공개 조회(OpenActivityConditions.activityStart)는 start_at·start_date를 함께 읽으므로 그대로 동작한다.
alter table schedules drop constraint schedules_check1;
