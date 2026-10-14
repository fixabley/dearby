-- domain 패키지 기준 스키마. PostgreSQL·H2(테스트) 공통 문법만 사용
-- 시각: LocalDateTime → timestamp, ZonedDateTime → timestamp with time zone + time_zone(존 ID)
-- 참조: 값 테이블(연락처·역할·일정·명함 구성)만 부모에 FK(on delete cascade)를 건다.
--   다른 애그리거트를 가리키는 id는 FK 없는 논리키이고 인덱스만 둔다. 함께 지울 데이터는 애플리케이션이 지운다(계정 삭제 등)
-- 카탈로그: organizations·programs·activities·schedules·activity_roles가 원본이다(2026-10-14 결정, Supabase는 쓰지 않음).
--   source_id는 수집 워커가 공식 출처마다 정하는 UUID(기존 Supabase 카탈로그 id와 같음)로, 다시 수집할 때 같은 행을 찾는 키다.
--   열 주석의 "원본"은 기존 Supabase 스냅샷(catalog_public_snapshot) 필드, "사용"은 그 값을 읽는 앱

create table users
(
    id uuid primary key
);

create table profiles
(
    id         bigint generated always as identity primary key,
    user_id    uuid         not null unique, -- 논리키 users
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

-- 원본 organizations[]. 스냅샷에는 주소가 없어 주소 열을 비워 둘 수 있다(넣을 때는 네 값을 모두)
create table organizations
(
    id                     bigint generated always as identity primary key,
    source_id              uuid unique,             -- 원본 id
    parent_organization_id bigint,                  -- 논리키 organizations. 원본 parentId
    name                   varchar(200)     not null, -- 원본 name · 사용: iOS·Android·웹
    lat                    double precision,
    lng                    double precision,
    address_line1          varchar(300),
    address_line2          varchar(300),
    created_at             timestamp        not null,
    updated_at             timestamp        not null,
    check (
        (lat is null and lng is null and address_line1 is null and address_line2 is null)
            or (lat is not null and lng is not null and address_line1 is not null and address_line2 is not null)
        )
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
    user_id         uuid      not null, -- 논리키 users
    organization_id bigint    not null, -- 논리키 organizations
    description     varchar   not null,
    period_from     timestamp,
    period_to       timestamp,
    created_at      timestamp not null,
    updated_at      timestamp not null
);
create index organization_members_user_id_idx on organization_members (user_id);
create index organization_members_organization_id_idx on organization_members (organization_id);

-- 원본 programs[]
create table programs
(
    id              bigint generated always as identity primary key,
    source_id       uuid unique,           -- 원본 id
    organization_id bigint       not null, -- 논리키 organizations. 원본 organizationId
    name            varchar(200) not null, -- 원본 title · 사용: 웹
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
    user_id     uuid    not null, -- 논리키 users
    program_id  bigint  not null, -- 논리키 programs
    description varchar not null
);
create index program_staff_user_id_idx on program_staff (user_id);
create index program_staff_program_id_idx on program_staff (program_id);

-- 원본 activities[]. 모집 기간(recruitmentStartAt·EndAt)은 application 일정(slot = 'APPLICATION', kind = 'DEADLINE'),
-- 활동 일정 문구(dateLabel)는 activitySchedule(slot = 'ACTIVITY')의 date_label, schedules[]는 slot 없는 일정으로 넣는다
create table activities
(
    id                 bigint generated always as identity primary key,
    source_id          uuid unique,            -- 원본 id
    program_id         bigint        not null, -- 논리키 programs. 원본 programId
    name               varchar(200)  not null, -- 원본 title · 사용: iOS·Android·웹
    description        varchar       not null, -- 원본 summary · 사용: iOS·Android·웹
    participation_type varchar(20)   not null check (participation_type in ('REGISTRATION', 'SELECTION')), -- 원본 participationType · 사용: iOS·Android·웹
    recruitment_status varchar(20)   not null check (recruitment_status in ('OPEN', 'SCHEDULED', 'CLOSED', 'UNKNOWN')), -- 원본 recruitmentStatus(운영자 선택값) · 사용: iOS·Android·웹
    location           varchar(300),           -- 원본 location(주소 문구, 좌표 없음) · 사용: iOS·Android·웹
    cost               varchar(300),           -- 원본 cost · 사용: iOS·Android·웹
    audience           varchar(1000),          -- 원본 audience · 사용: iOS·Android·웹
    official_url       varchar(2000) not null, -- 원본 officialUrl · 사용: iOS·Android·웹
    application_url    varchar(2000),          -- 원본 applicationUrl · 사용: iOS·Android·웹
    source_checked_at  timestamp with time zone, -- 원본 sourceCheckedAt · 사용: iOS·Android·웹
    valid_until        timestamp with time zone, -- 원본 validUntil · 사용: iOS·Android·웹
    freshness          varchar(20)   not null check (freshness in ('VERIFIED', 'STALE', 'UNAVAILABLE')), -- 원본 freshness · 사용: iOS·Android·웹
    source_note        varchar       not null, -- 원본 sourceNote · 사용: iOS·Android·웹
    created_at         timestamp     not null,
    updated_at         timestamp     not null
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

-- 원본 activities[].roles(모집 역할 문구) · 사용: iOS·Android·웹. 순서를 유지한다
create table activity_roles
(
    activity_id bigint       not null references activities (id) on delete cascade,
    position    integer      not null,
    name        varchar(200) not null,
    primary key (activity_id, position)
);

-- slot: Activity.application = 'APPLICATION', Activity.activitySchedule = 'ACTIVITY', Activity.schedules = null
-- time_type: ScheduleTime.Timed = 'TIMED'(start_at·end_at 중 하나 이상), ScheduleTime.AllDay = 'ALL_DAY', 날짜 미정 = null
-- 원본 schedules[]에는 종류·상태가 없어 kind = 'EVENT', status = 'CONFIRMED'로 넣는다
create table schedules
(
    id                 bigint generated always as identity primary key,
    activity_id        bigint       not null references activities (id) on delete cascade,
    source_id          uuid unique, -- 원본 activities[].schedules[].id
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
            or (time_type = 'TIMED' and (start_at is not null or end_at is not null) and time_zone is not null
            and (start_at is null or end_at is null or end_at >= start_at)
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
    user_id     uuid      not null, -- 논리키 users
    activity_id bigint    not null, -- 논리키 activities
    created_at  timestamp not null,
    updated_at  timestamp not null,
    unique (user_id, activity_id)
);
create index activity_participations_activity_id_idx on activity_participations (activity_id);

create table profile_activity_histories
(
    id          bigint generated always as identity primary key,
    user_id     uuid      not null, -- 논리키 users
    activity_id bigint    not null, -- 논리키 activities
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
    user_id uuid         not null, -- 논리키 users
    title   varchar(200) not null
);
create index business_cards_user_id_idx on business_cards (user_id);

create table business_card_contacts
(
    business_card_id bigint not null references business_cards (id) on delete cascade,
    contact_id       bigint not null, -- 논리키 contacts
    primary key (business_card_id, contact_id)
);
create index business_card_contacts_contact_id_idx on business_card_contacts (contact_id);

create table business_card_activity_histories
(
    business_card_id            bigint not null references business_cards (id) on delete cascade,
    profile_activity_history_id bigint not null, -- 논리키 profile_activity_histories
    primary key (business_card_id, profile_activity_history_id)
);
create index business_card_activity_histories_history_id_idx on business_card_activity_histories (profile_activity_history_id);

-- roles: List<Id>. Role 도메인이 아직 없어 role_id는 아직 아무것도 가리키지 않는다
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
