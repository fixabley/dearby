-- 2026-11-05 디스코드 연결 기반(native-v1.md "디스코드 연결 구현 기록 — 2026-11-05"). 로그인 수단이 아니다(user_identities에 넣지 않는다).

-- OAuth 한 번만 쓰는 중간 상태(github_contact_links와 같은 모양). flow ACCOUNT: 계정 연결(identify guilds.join), GUILD: 봇 서버 연결(bot).
-- START: 인가 요청의 state(id). RESULT: 콜백이 받은 디스코드 인가 code를 앱이 complete(Bearer)로 바꿔 가기 전까지. 토큰 교환은 complete에서 한다
create table discord_oauth_states
(
    id           uuid primary key,
    user_id      uuid                     not null references users (id) on delete cascade,
    flow         varchar(10)              not null check (flow in ('ACCOUNT', 'GUILD')),
    step         varchar(10)              not null check (step in ('START', 'RESULT')),
    platform     varchar(10)              not null check (platform in ('ios', 'android', 'web')),
    discord_code varchar(200),
    expires_at   timestamp with time zone not null
);

-- 연결한 디스코드 계정(사용자당 하나, 디스코드 계정도 한 사용자에게만). 토큰은 AES-256-GCM 암호문(iv 12바이트 || 암호문+태그), 키는 DISCORD_TOKEN_KEY
create table discord_accounts
(
    user_id                  uuid primary key references users (id) on delete cascade,
    discord_user_id          varchar(20)              not null unique,
    username                 varchar(32)              not null,
    access_token_ciphertext  bytea                    not null,
    refresh_token_ciphertext bytea                    not null,
    token_expires_at         timestamp with time zone not null,
    linked_at                timestamp with time zone not null
);

-- 봇을 넣은 디스코드 서버. 연결한 사용자(앞으로 개설자) 소유. 같은 서버를 여러 사람이 연결할 수 있다
create table discord_guild_connections
(
    id         uuid primary key,
    user_id    uuid                     not null references users (id) on delete cascade,
    guild_id   varchar(20)              not null,
    guild_name varchar(100)             not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    unique (user_id, guild_id)
);
