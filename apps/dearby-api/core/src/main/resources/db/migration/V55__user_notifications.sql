-- 2026-10-25 알림 목록(shared/contracts/native-v1.md "알림 목록(발견 우상단 종)"). 매일 만든 알림 거리를 사용자 시간대 8시에 쌓고, 푸시는 이 행을 근거로 기기마다 보낸다.
-- 푸시 권한·전체 스위치와 상관없이 쌓이고(알림 수준 NONE만 빠진다) 90일이 지나면 배치가 지운다. users·activities cascade
create table user_notifications
(
    id          bigint generated always as identity primary key,
    user_id     uuid                     not null references users (id) on delete cascade,
    activity_id bigint                   not null references activities (id) on delete cascade,
    program_id  bigint                   not null, -- 논리키 programs(카드 묶음)
    kind        varchar(20)              not null check (kind in ('NEW', 'APPLICATION_START', 'DEADLINE_EVE', 'DEADLINE', 'ANNOUNCEMENT', 'INTERVIEW', 'EVENT', 'ACTIVITY_START')),
    local_date  date                     not null, -- 그 알림 거리의 날짜(만든 시간대 기준). NEW는 처음 만든 날
    created_at  timestamp with time zone not null,
    read_at     timestamp with time zone,
    unique (user_id, activity_id, kind, local_date)
);
create index user_notifications_user_idx on user_notifications (user_id, created_at desc);

-- 보낸 푸시: 알림 행 × 기기 토큰마다 한 번(V54의 사용자·활동·종류·날짜 기록을 대신한다. 옛 기록은 버린다)
drop table push_deliveries;
create table push_deliveries
(
    notification_id bigint                   not null references user_notifications (id) on delete cascade,
    device_token    varchar(500)             not null, -- 논리키 push_devices
    sent_at         timestamp with time zone not null,
    primary key (notification_id, device_token)
);
