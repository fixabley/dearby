create table user_roles
(
    user_id uuid        not null references users (id) on delete cascade,
    role    varchar(20) not null check (role in ('ADMIN', 'USER')),
    primary key (user_id, role)
);

-- 토큰 원문(UUID)은 저장하지 않고 SHA-256(hex)만 저장
create table refresh_tokens
(
    token_hash varchar(64) primary key,
    user_id    uuid                     not null references users (id) on delete cascade,
    expires_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    revoked_at timestamp with time zone
);
create index refresh_tokens_user_id_idx on refresh_tokens (user_id);
