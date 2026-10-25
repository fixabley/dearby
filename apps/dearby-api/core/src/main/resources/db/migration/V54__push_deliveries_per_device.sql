-- 2026-10-25 사용자 결정: 알림 중복 방지를 기기마다(native-v1.md "프로그램·활동별 알림 수준"). 기기마다 그 시간대 아침 8시에 그날 거리를 받는다.
-- device_token은 논리키(기기를 지워도 기록은 남고, 같은 토큰이 다시 오면 그날 다시 보내지 않는다). 이전 기록은 기기를 모르므로 빈 문자열
alter table push_deliveries
    add column device_token varchar(500) not null default '';
alter table push_deliveries
    drop constraint push_deliveries_pkey,
    add primary key (user_id, device_token, activity_id, kind, delivery_date);
alter table push_deliveries
    alter column device_token drop default;
