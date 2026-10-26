-- 2026-10-26 트랙·세션 시간표(shared/contracts/native-v1.md "트랙·세션 시간표"). 시간표는 일정 블록(schedules 행) 하나에 딸린 하위 시간표다.
-- 기존 일정은 그대로 두고(이관 없음) 블록에 트랙·세션을 붙인다. 블록을 지우면(행 삭제) 시간표와 세션 찜도 함께 지워진다.
-- 세부 일정 블록에 시간표가 붙으면 저장소가 그 블록을 curated로 올려 재수집이 지웠다 다시 넣지 않게 한다(세션 id·찜 유지)

-- 트랙(강연장). position 순서가 표시 순서이고, 앱이 그 순서로 팔레트에서 색을 고른다(서버는 색을 저장하지 않는다)
create table schedule_tracks
(
    id          bigint generated always as identity primary key,
    schedule_id bigint       not null references schedules (id) on delete cascade,
    name        varchar(50)  not null,
    room        varchar(100),
    position    integer      not null -- 이름 중복은 서버 검증(TimetableRules)이 막는다
);
create index schedule_tracks_schedule_idx on schedule_tracks (schedule_id);

-- 세션. track_id가 null이면 모든 트랙에 걸친 전폭 세션(키노트·쉬는시간·점심, 트랙이 없는 시간표의 세션)
-- speakers: [{name, affiliation}] (최대 10)
create table schedule_sessions
(
    id          bigint generated always as identity primary key,
    schedule_id bigint                   not null references schedules (id) on delete cascade,
    track_id    bigint references schedule_tracks (id) on delete cascade,
    kind        varchar(20)              not null check (kind in ('TALK', 'KEYNOTE', 'BREAK', 'MEAL', 'OTHER')),
    title       varchar(200)             not null,
    description varchar(2000),
    start_at    timestamp with time zone not null,
    end_at      timestamp with time zone not null,
    speakers    jsonb                    not null default '[]'::jsonb,
    check (end_at > start_at)
);
create index schedule_sessions_schedule_idx on schedule_sessions (schedule_id, start_at);

-- 세션 분야 태그(기존 태그 사전). position은 보낸 순서
create table schedule_session_tags
(
    session_id bigint  not null references schedule_sessions (id) on delete cascade,
    tag_id     bigint  not null references tags (id) on delete cascade,
    position   integer not null,
    primary key (session_id, tag_id)
);

-- 세션 찜(로그인만). 계정 삭제(users cascade)와 함께 지워진다. 활동 즐겨찾기·알림·추천 신호와는 따로다
create table user_session_favorites
(
    user_id    uuid                     not null references users (id) on delete cascade,
    session_id bigint                   not null references schedule_sessions (id) on delete cascade,
    created_at timestamp with time zone not null,
    primary key (user_id, session_id)
);
create index user_session_favorites_session_idx on user_session_favorites (session_id);
