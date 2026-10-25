-- 2026-10-25 즐겨찾기(하트)·새 모집 푸시 알림·프로그램·활동별 알림 수준(shared/contracts/native-v1.md "즐겨찾기(하트)·새 모집 푸시 알림").
-- 사용자별 표는 모두 users cascade라 계정 삭제와 함께 지워진다. 기기 토큰·알림 기록은 서버 밖(APNs·FCM 발송 말고)으로 내보내지 않고 로그에도 남기지 않는다

-- 게시 시각: "즐겨찾기 이후 게시된 활동"(새 모집)의 기준. 게시로 바뀔 때 저장소(updatePublicationStatus)가 처음 한 번 채운다.
-- 기존 게시 활동의 정확한 게시 시각은 모르므로 만든 시각으로 채운다(이 마이그레이션 전에는 즐겨찾기가 없어 새 모집으로 잡히지 않는다)
alter table activities
    add column published_at timestamp with time zone;
update activities set published_at = created_at at time zone 'UTC' where publication_status = 'PUBLISHED';

-- 즐겨찾기. target_id는 논리키(활동 또는 프로그램 id): 지워졌거나 게시되지 않은 대상은 읽을 때 뺀다.
-- notify_level: 알림 수준 ALL(모든 일정 + 신규) · APPLICATION(신청일 + 신규, 기본) · NONE. 활동 단위가 프로그램 단위보다 앞선다
-- seen_at: 즐겨찾기 화면에서 그 프로그램을 연 시각(새 모집 배지를 끈다)
create table user_favorites
(
    user_id      uuid                     not null references users (id) on delete cascade,
    target_type  varchar(10)              not null check (target_type in ('ACTIVITY', 'PROGRAM')),
    target_id    bigint                   not null,
    notify_level varchar(20)              not null default 'APPLICATION' check (notify_level in ('ALL', 'APPLICATION', 'NONE')),
    created_at   timestamp with time zone not null,
    seen_at      timestamp with time zone,
    primary key (user_id, target_type, target_id)
);
create index user_favorites_target_idx on user_favorites (target_type, target_id);

-- 푸시 기기 토큰. 한 기기 토큰은 한 사용자에게만(다른 계정으로 로그인하면 옮겨 간다). time_zone은 IANA 이름(그 시간대 아침 8시에 보낸다)
create table push_devices
(
    token       varchar(500) primary key,
    user_id     uuid                     not null references users (id) on delete cascade,
    platform    varchar(10)              not null check (platform in ('IOS', 'ANDROID')),
    time_zone   varchar(64)              not null default 'Asia/Seoul',
    app_version varchar(50),
    updated_at  timestamp with time zone not null
);
create index push_devices_user_idx on push_devices (user_id);

-- 알림 전체 스위치(프로필 "알림" 행의 "새 모집 알림"). 행이 없으면 켜짐
create table notification_settings
(
    user_id         uuid primary key references users (id) on delete cascade,
    new_recruitment boolean                  not null default true,
    updated_at      timestamp with time zone not null
);

-- 보낸 알림 거리. (사용자, 활동, 종류, 그 기기 시간대의 날짜)마다 한 번. 신규(NEW_ACTIVITY)는 날짜와 상관없이 한 번만 보낸다
create table push_deliveries
(
    user_id       uuid                     not null references users (id) on delete cascade,
    activity_id   bigint                   not null references activities (id) on delete cascade,
    kind          varchar(20)              not null check (kind in ('NEW_ACTIVITY', 'APPLICATION_START', 'DEADLINE_EVE', 'DEADLINE', 'SCHEDULE')),
    delivery_date date                     not null,
    sent_at       timestamp with time zone not null,
    primary key (user_id, activity_id, kind, delivery_date)
);
