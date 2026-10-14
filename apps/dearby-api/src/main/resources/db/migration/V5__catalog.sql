-- 공개 카탈로그를 V2 도메인 테이블(organizations·programs·activities·schedules)에 복사해 둔다.
-- 원본은 Supabase catalog_public_snapshot()이고, 주기 동기화가 source_id(원본 UUID)로 행을 맞춘다.
-- 각 열 주석의 "원본"은 스냅샷 필드, "사용"은 그 값을 읽는 앱이다. 쓰지 않는 열은 확인 후 지운다

-- 원본 organizations[]. 스냅샷에는 주소가 없어 주소 열을 비워 둘 수 있게 한다(넣을 때는 네 값을 모두)
alter table organizations add column source_id uuid;                          -- 원본 id
alter table organizations add column description varchar not null default ''; -- 원본 description · 사용: 없음
alter table organizations alter column lat drop not null;
alter table organizations alter column lng drop not null;
alter table organizations alter column address_line1 drop not null;
alter table organizations alter column address_line2 drop not null;
alter table organizations add constraint organizations_address_all_or_none check (
    (lat is null and lng is null and address_line1 is null and address_line2 is null)
        or (lat is not null and lng is not null and address_line1 is not null and address_line2 is not null)
    );
create unique index organizations_source_id_idx on organizations (source_id);

-- 원본 programs[]. name ← title
alter table programs add column source_id uuid;                          -- 원본 id
alter table programs add column description varchar not null default ''; -- 원본 description · 사용: 없음
create unique index programs_source_id_idx on programs (source_id);

-- 원본 activities[]. name ← title, description ← summary(사용: iOS·Android·웹)
-- 모집 기간(recruitmentStartAt·EndAt)은 application 일정(slot = 'APPLICATION', kind = 'DEADLINE')으로,
-- 활동 일정 문구(dateLabel)는 activitySchedule(slot = 'ACTIVITY')의 date_label로, schedules[]는 slot 없는 일정으로 넣는다
alter table activities add column source_id uuid; -- 원본 id
alter table activities add column participation_type varchar(20) not null default 'REGISTRATION'
    check (participation_type in ('REGISTRATION', 'SELECTION'));   -- 원본 participationType · 사용: iOS·Android·웹
alter table activities add column recruitment_status varchar(20) not null default 'UNKNOWN'
    check (recruitment_status in ('OPEN', 'SCHEDULED', 'CLOSED', 'UNKNOWN')); -- 원본 recruitmentStatus(운영자 선택값) · 사용: iOS·Android·웹
alter table activities add column location varchar(300);          -- 원본 location(주소 문자열, 좌표 없음) · 사용: iOS·Android·웹
alter table activities add column cost varchar(300);              -- 원본 cost · 사용: iOS·Android·웹
alter table activities add column audience varchar(1000);         -- 원본 audience · 사용: iOS·Android·웹
alter table activities add column qualification varchar(1000);    -- 원본 qualification · 사용: 웹
alter table activities add column image_url varchar(2000);        -- 원본 imageUrl · 사용: 없음
alter table activities add column official_url varchar(2000) not null default ''; -- 원본 officialUrl · 사용: iOS·Android·웹
alter table activities add column application_url varchar(2000); -- 원본 applicationUrl · 사용: iOS·Android·웹
alter table activities add column source_checked_at timestamp with time zone; -- 원본 sourceCheckedAt · 사용: iOS·Android·웹
alter table activities add column valid_until timestamp with time zone;       -- 원본 validUntil · 사용: iOS·Android·웹
alter table activities add column freshness varchar(20) not null default 'STALE'
    check (freshness in ('VERIFIED', 'STALE', 'UNAVAILABLE'));     -- 원본 freshness · 사용: iOS·Android·웹
alter table activities add column source_note varchar not null default ''; -- 원본 sourceNote · 사용: iOS·Android·웹
create unique index activities_source_id_idx on activities (source_id);

-- 원본 activities[].roles(모집 역할 문구) · 사용: iOS·Android·웹. 순서를 유지한다
create table activity_roles
(
    activity_id bigint       not null references activities (id) on delete cascade,
    position    integer      not null,
    name        varchar(200) not null,
    primary key (activity_id, position)
);

-- 원본 activities[].schedules[].id. 원본 일정에는 종류·상태가 없어 kind = 'EVENT', status = 'CONFIRMED'로 넣는다
alter table schedules add column source_id uuid;
create unique index schedules_source_id_idx on schedules (source_id);
