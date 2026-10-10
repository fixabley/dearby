-- domain 패키지 기준 스키마. PostgreSQL·H2(테스트) 공통 문법만 사용
-- 시각: LocalDateTime → timestamp, ZonedDateTime → timestamp with time zone + time_zone(존 ID)
-- 삭제: 사용자 소유 데이터는 사용자와 함께 삭제(cascade), 기관·프로그램·활동은 참조가 있으면 삭제 거부

create table users
(
    id uuid primary key
);

create table profiles
(
    id         bigint generated always as identity primary key,
    user_id    uuid         not null unique references users (id) on delete cascade,
    name       varchar(100) not null,
    created_at timestamp    not null,
    updated_at timestamp    not null
);

create table contacts
(
    id      bigint generated always as identity primary key,
    user_id uuid         not null references users (id) on delete cascade,
    kind    varchar(20)  not null check (kind in ('PHONE', 'EMAIL', 'KAKAO', 'INSTAGRAM', 'GITHUB', 'BEHANCE')),
    label   varchar(100) not null,
    value   varchar(500) not null
);
create index contacts_user_id_idx on contacts (user_id);

create table organizations
(
    id                     bigint generated always as identity primary key,
    parent_organization_id bigint references organizations (id),
    name                   varchar(200)     not null,
    lat                    double precision not null,
    lng                    double precision not null,
    address_line1          varchar(300)     not null,
    address_line2          varchar(300)     not null,
    created_at             timestamp        not null,
    updated_at             timestamp        not null
);
create index organizations_parent_organization_id_idx on organizations (parent_organization_id);

create table organization_contacts
(
    id              bigint generated always as identity primary key,
    organization_id bigint       not null references organizations (id) on delete cascade,
    kind            varchar(20)  not null check (kind in ('PHONE', 'EMAIL', 'KAKAO', 'INSTAGRAM', 'GITHUB', 'BEHANCE')),
    label           varchar(100) not null,
    value           varchar(500) not null
);
create index organization_contacts_organization_id_idx on organization_contacts (organization_id);

create table organization_members
(
    id              bigint generated always as identity primary key,
    user_id         uuid      not null references users (id) on delete cascade,
    organization_id bigint    not null references organizations (id),
    description     varchar   not null,
    period_from     timestamp,
    period_to       timestamp,
    created_at      timestamp not null,
    updated_at      timestamp not null
);
create index organization_members_user_id_idx on organization_members (user_id);
create index organization_members_organization_id_idx on organization_members (organization_id);

create table programs
(
    id              bigint generated always as identity primary key,
    organization_id bigint       not null references organizations (id),
    name            varchar(200) not null,
    period_from     timestamp,
    period_to       timestamp,
    created_at      timestamp    not null,
    updated_at      timestamp    not null
);
create index programs_organization_id_idx on programs (organization_id);

create table program_contacts
(
    id         bigint generated always as identity primary key,
    program_id bigint       not null references programs (id) on delete cascade,
    kind       varchar(20)  not null check (kind in ('PHONE', 'EMAIL', 'KAKAO', 'INSTAGRAM', 'GITHUB', 'BEHANCE')),
    label      varchar(100) not null,
    value      varchar(500) not null
);
create index program_contacts_program_id_idx on program_contacts (program_id);

create table program_staff
(
    id          bigint generated always as identity primary key,
    user_id     uuid    not null references users (id) on delete cascade,
    program_id  bigint  not null references programs (id),
    description varchar not null
);
create index program_staff_user_id_idx on program_staff (user_id);
create index program_staff_program_id_idx on program_staff (program_id);

create table activities
(
    id          bigint generated always as identity primary key,
    program_id  bigint       not null references programs (id),
    name        varchar(200) not null,
    description varchar      not null,
    created_at  timestamp    not null,
    updated_at  timestamp    not null
);
create index activities_program_id_idx on activities (program_id);

create table activity_contacts
(
    id          bigint generated always as identity primary key,
    activity_id bigint       not null references activities (id) on delete cascade,
    kind        varchar(20)  not null check (kind in ('PHONE', 'EMAIL', 'KAKAO', 'INSTAGRAM', 'GITHUB', 'BEHANCE')),
    label       varchar(100) not null,
    value       varchar(500) not null
);
create index activity_contacts_activity_id_idx on activity_contacts (activity_id);

-- slot: Activity.application = 'APPLICATION', Activity.activitySchedule = 'ACTIVITY', Activity.schedules = null
-- time_type: ScheduleTime.Timed = 'TIMED', ScheduleTime.AllDay = 'ALL_DAY', 날짜 미정 = null
create table schedules
(
    id                 bigint generated always as identity primary key,
    activity_id        bigint       not null references activities (id) on delete cascade,
    slot               varchar(20) check (slot in ('APPLICATION', 'ACTIVITY')),
    title              varchar(200) not null,
    kind               varchar(20)  not null check (kind in ('DEADLINE', 'ANNOUNCEMENT', 'INTERVIEW', 'EVENT')),
    status             varchar(20)  not null check (status in ('TENTATIVE', 'CONFIRMED', 'CANCELLED')),
    time_type          varchar(20) check (time_type in ('TIMED', 'ALL_DAY')),
    start_at           timestamp with time zone,
    end_at             timestamp with time zone,
    time_zone          varchar(64),
    start_date         date,
    end_date_exclusive date,
    date_label         varchar(200) not null,
    lat                double precision,
    lng                double precision,
    address_line1      varchar(300),
    address_line2      varchar(300),
    conference_url     varchar(2000),
    sequence           integer      not null,
    created_at         timestamp    not null,
    updated_at         timestamp    not null,
    unique (activity_id, slot),
    check (
        (time_type is null and start_at is null and end_at is null and time_zone is null
            and start_date is null and end_date_exclusive is null)
            or (time_type = 'TIMED' and start_at is not null and time_zone is not null
            and (end_at is null or end_at >= start_at)
            and start_date is null and end_date_exclusive is null)
            or (time_type = 'ALL_DAY' and start_date is not null and end_date_exclusive > start_date
            and start_at is null and end_at is null and time_zone is null)
        ),
    check (slot is distinct from 'ACTIVITY' or time_type is distinct from 'TIMED'),
    check (
        (lat is null and lng is null and address_line1 is null and address_line2 is null)
            or (lat is not null and lng is not null and address_line1 is not null and address_line2 is not null)
        )
);

create table activity_participations
(
    id          bigint generated always as identity primary key,
    user_id     uuid      not null references users (id) on delete cascade,
    activity_id bigint    not null references activities (id),
    created_at  timestamp not null,
    updated_at  timestamp not null,
    unique (user_id, activity_id)
);
create index activity_participations_activity_id_idx on activity_participations (activity_id);

create table profile_activity_histories
(
    id          bigint generated always as identity primary key,
    user_id     uuid      not null references users (id) on delete cascade,
    activity_id bigint    not null references activities (id),
    description varchar   not null,
    period_from timestamp,
    period_to   timestamp,
    created_at  timestamp not null,
    updated_at  timestamp not null
);
create index profile_activity_histories_user_id_idx on profile_activity_histories (user_id);
create index profile_activity_histories_activity_id_idx on profile_activity_histories (activity_id);

create table business_cards
(
    id      bigint generated always as identity primary key,
    user_id uuid         not null references users (id) on delete cascade,
    title   varchar(200) not null
);
create index business_cards_user_id_idx on business_cards (user_id);

create table business_card_contacts
(
    business_card_id bigint not null references business_cards (id) on delete cascade,
    contact_id       bigint not null references contacts (id) on delete cascade,
    primary key (business_card_id, contact_id)
);

create table business_card_activity_histories
(
    business_card_id            bigint not null references business_cards (id) on delete cascade,
    profile_activity_history_id bigint not null references profile_activity_histories (id) on delete cascade,
    primary key (business_card_id, profile_activity_history_id)
);

-- roles: List<Id>. Role 도메인이 아직 없어 role_id 외래 키는 보류
create table organization_member_roles
(
    organization_member_id bigint not null references organization_members (id) on delete cascade,
    role_id                bigint not null,
    primary key (organization_member_id, role_id)
);

create table program_staff_roles
(
    program_staff_id bigint not null references program_staff (id) on delete cascade,
    role_id          bigint not null,
    primary key (program_staff_id, role_id)
);

create table profile_activity_history_roles
(
    profile_activity_history_id bigint not null references profile_activity_histories (id) on delete cascade,
    role_id                     bigint not null,
    primary key (profile_activity_history_id, role_id)
);
