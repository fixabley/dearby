-- 2026-10-26 사용자 결정(native-v1.md "선발 과정·활동 일정 분리"): 일정이 선발 과정(SELECTION)인지 활동 일정(ACTIVITY)인지.
-- null이면 서버가 읽을 때 같은 규칙(SchedulePhase.resolve)으로 채운다. 모집 슬롯은 늘 SELECTION, 활동 기간 슬롯은 늘 ACTIVITY
alter table schedules
    add column phase varchar(20) check (phase in ('SELECTION', 'ACTIVITY')),
    add constraint schedules_slot_phase_check check (
        slot is null or phase is null
            or (slot = 'APPLICATION' and phase = 'SELECTION')
            or (slot = 'ACTIVITY' and phase = 'ACTIVITY'));

-- 기존 행: 모집 슬롯은 SELECTION, 활동 기간 슬롯은 ACTIVITY, 마감·발표·면접 세부 일정은 SELECTION으로 채운다(조율 세션 결정 2026-10-26).
-- 그 밖의 세부 일정(EVENT 등)은 null로 남겨 읽을 때 판정한다: 운영자가 상태를 지정하면 예전 앱 판정처럼 저절로 SELECTION이 된다.
-- 운영자가 어드민에서 phase를 직접 정한 행만 저장값이 우선이다
update schedules
set phase = case
                when slot = 'APPLICATION' then 'SELECTION'
                when slot = 'ACTIVITY' then 'ACTIVITY'
                else 'SELECTION'
    end
where slot is not null
   or kind in ('DEADLINE', 'ANNOUNCEMENT', 'INTERVIEW');
