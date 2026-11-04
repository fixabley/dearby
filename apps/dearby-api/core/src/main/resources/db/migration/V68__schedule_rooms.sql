-- 2026-11-05 시간표 방 식별자(native-v1.md "같은 방이면 그 자리에서 대기"). 같은 방 판정은 이름 문자열이 아니라 room id로만 한다.
-- 방은 일정 블록(schedules 행) 하나의 방 사전이다. 블록을 지우면 함께 지워진다. 이름 중복은 서버 검증(TimetableRules)이 막는다
create table schedule_rooms
(
    id          bigint generated always as identity primary key,
    schedule_id bigint       not null references schedules (id) on delete cascade,
    name        varchar(100) not null,
    position    integer      not null
);
create index schedule_rooms_schedule_idx on schedule_rooms (schedule_id);

-- 트랙의 방, 그리고 세션 자체의 방(전폭 세션 등; 세션 값이 트랙 값보다 우선하고 그 판정은 앱이 한다). 방을 지우면 비운다
alter table schedule_tracks
    add column room_id bigint references schedule_rooms (id) on delete set null;
alter table schedule_sessions
    add column room_id bigint references schedule_rooms (id) on delete set null;
create index schedule_tracks_room_idx on schedule_tracks (room_id);
create index schedule_sessions_room_idx on schedule_sessions (room_id);

-- 한 번의 이관: 블록 안에서 같은 room 문자열(앞뒤 공백만 정리)을 한 방으로 묶는다. 순서는 그 방을 처음 쓰는 트랙 순서.
-- 이름이 조금 다른 같은 방(띄어쓰기 등)은 합치지 않으므로 어드민이 블록마다 확인한다
insert into schedule_rooms (schedule_id, name, position)
select schedule_id, name, (row_number() over (partition by schedule_id order by first_position, name)) - 1
from (select schedule_id, trim(room) as name, min(position) as first_position
      from schedule_tracks
      where trim(coalesce(room, '')) <> ''
      group by schedule_id, trim(room)) rooms;

update schedule_tracks t
set room_id = r.id
from schedule_rooms r
where r.schedule_id = t.schedule_id
  and r.name = trim(t.room);

-- 옛 schedule_tracks.room은 이 이관 뒤 읽지도 쓰지도 않는다(API의 Track.room은 방 이름에서 만든다).
-- 배포 중 옛 컨테이너가 잠깐 읽어도 깨지지 않게 남겨 두고, 앱이 roomId로 옮긴 뒤 다음 정리 마이그레이션에서 지운다
comment on column schedule_tracks.room is 'V68 이후 쓰지 않음(schedule_rooms로 이관). 다음 정리에서 삭제';
