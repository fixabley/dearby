-- 2026-10-22 사용자 결정(native-v1.md "내 활동 서버 저장·일정 상태·겹침 확인 시간")

-- 일정마다 고를 수 있는 진행 상태. 행이 없으면 서버가 기본 세 개(대기·통과·탈락)를 응답에 채운다(저장하지 않음). 일정이 지워지면 함께 지운다
create table schedule_statuses
(
    id          bigint generated always as identity primary key,
    schedule_id bigint                   not null references schedules (id) on delete cascade,
    name        varchar(20)              not null,
    kind        varchar(20)              not null check (kind in ('WAITING', 'PASSED', 'FAILED', 'OTHER')),
    sort_order  integer                  not null,
    created_at  timestamp with time zone not null -- 이름은 일정 안에서 겹치지 않게 코드가 확인한다(순서를 바꾸며 이름을 맞바꿀 수 있게 DB 유일 제약은 두지 않음)
);
create index schedule_statuses_schedule_id_idx on schedule_statuses (schedule_id);

-- 사용자가 내 활동에 넣은 활동. activity_id는 논리키(활동이 지워져도 남아 "삭제된 활동"으로 보인다). 계정 삭제와 함께 지운다
create table user_activities
(
    id          uuid primary key,
    user_id     uuid                     not null references users (id) on delete cascade,
    activity_id bigint                   not null, -- 논리키 activities
    created_at  timestamp with time zone not null,
    updated_at  timestamp with time zone not null,
    unique (user_id, activity_id)
);

-- 사용자 활동의 일정별 상태. 어드민 상태를 고르면 schedule_status_id, 기본 상태를 고르면 default_kind(둘 중 하나만)
create table user_activity_schedule_statuses
(
    user_activity_id   uuid                     not null, -- 논리키 user_activities
    schedule_id        bigint                   not null, -- 논리키 schedules
    schedule_status_id bigint,                            -- 논리키 schedule_statuses
    default_kind       varchar(20) check (default_kind in ('WAITING', 'PASSED', 'FAILED')),
    updated_at         timestamp with time zone not null,
    primary key (user_activity_id, schedule_id),
    check ((schedule_status_id is null) <> (default_kind is null))
);
create index user_activity_schedule_statuses_status_idx on user_activity_schedule_statuses (schedule_status_id);

-- 겹침 확인 시간(서울 시각): 날짜만 있는 일정도 이 시간대로 기기 캘린더와 비교한다. 둘 다 있거나 둘 다 없고, 끝이 시작보다 뒤
alter table schedules
    add column overlap_start_time time,
    add column overlap_end_time   time,
    add constraint schedules_overlap_time_check check (
        (overlap_start_time is null and overlap_end_time is null)
            or (overlap_start_time is not null and overlap_end_time is not null and overlap_end_time > overlap_start_time));
