-- 2026-10-26 사용자 결정(native-v1.md "선발 과정·활동 일정 분리"): 일정이 선발 과정(SELECTION)인지 활동 일정(ACTIVITY)인지.
-- null이면 서버가 읽을 때 같은 규칙(SchedulePhase.resolve)으로 채운다. 모집 슬롯은 늘 SELECTION, 활동 기간 슬롯은 늘 ACTIVITY
alter table schedules
    add column phase varchar(20) check (phase in ('SELECTION', 'ACTIVITY')),
    add constraint schedules_slot_phase_check check (
        slot is null or phase is null
            or (slot = 'APPLICATION' and phase = 'SELECTION')
            or (slot = 'ACTIVITY' and phase = 'ACTIVITY'));

-- 기존 행은 지금까지 앱이 쓰던 판정("통과 여부는 선발 단계에만")으로 한 번 채운다:
-- 모집 슬롯, 마감·발표·면접 세부 일정, 운영자가 상태를 지정한(schedule_statuses 행이 있는) EVENT 세부 일정은 선발, 나머지는 활동
update schedules
set phase = case
                when slot = 'APPLICATION' then 'SELECTION'
                when slot = 'ACTIVITY' then 'ACTIVITY'
                when kind in ('DEADLINE', 'ANNOUNCEMENT', 'INTERVIEW') then 'SELECTION'
                when exists (select 1 from schedule_statuses s where s.schedule_id = schedules.id) then 'SELECTION'
                else 'ACTIVITY'
    end;
