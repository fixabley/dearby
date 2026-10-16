-- 2026-10-16 사용자 결정: 애플 로그인(iOS·Android·웹). 외부 로그인 계정과 Dearby 사용자의 연결.
-- 애플 sub는 같은 팀의 앱 ID와 Services ID에서 같으므로 기기와 관계없이 한 계정으로 이어진다. 이메일로 계정을 합치지 않는다(가린 이메일이 올 수 있다)
create table user_identities
(
    provider     varchar(20)              not null check (provider in ('APPLE')),
    subject      varchar(255)             not null,
    user_id      uuid                     not null,
    email        varchar(320),
    -- 애플은 이름을 첫 인가 때만 준다. 프로필을 채울 때 쓸 수 있게 남긴다
    display_name varchar(200),
    created_at   timestamp with time zone not null,
    primary key (provider, subject)
);
create index user_identities_user_id_idx on user_identities (user_id);

-- 애플 로그인 중간 상태. 한 번만 꺼낼 수 있고(조건부 delete) 만료된 행은 다음 발급 때 지운다
--   NONCE: 발급한 nonce(웹 방식이면 앱이 보낸 verifier 해시도)
--   CALLBACK: 웹 방식 콜백에서 확인한 애플 계정. 앱이 verifier로 바꿔 간다
--   LINK: 아직 Dearby 계정이 없는 애플 계정. 새 계정을 만들거나 패스키 계정에 연결한다
create table apple_sign_in_states
(
    id            uuid primary key,
    step          varchar(20)              not null check (step in ('NONCE', 'CALLBACK', 'LINK')),
    nonce         varchar(100),
    verifier_hash varchar(100),
    subject       varchar(255),
    email         varchar(320),
    display_name  varchar(200),
    expires_at    timestamp with time zone not null
);
create index apple_sign_in_states_expires_at_idx on apple_sign_in_states (expires_at);
