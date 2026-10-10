-- 패스키 옵션 요청이 발급한 challenge. 세션 대신 DB에 두어 어느 인스턴스든 완료 요청을 받을 수 있다.
-- 완료 요청이 조건부 delete로 꺼내므로 한 번만 쓸 수 있다. 쓰지 않고 만료된 행은 다음 발급 때 지운다
create table webauthn_challenges
(
    id             uuid primary key,
    ceremony       varchar(20)              not null check (ceremony in ('REGISTRATION', 'AUTHENTICATION')),
    challenge      bytea                    not null,
    -- 가입에서만: 새 계정의 UserId(= user_entities.name)와 패스키 user handle(= user_entities.id, base64url)
    user_id        uuid,
    user_entity_id varchar(1000),
    display_name   varchar(200),
    expires_at     timestamp with time zone not null
);
create index webauthn_challenges_expires_at_idx on webauthn_challenges (expires_at);
