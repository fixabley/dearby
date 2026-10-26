-- 2026-10-26 사용자 결정(native-v1.md "연락처 인증"): 프로필 연락처의 GitHub 항목은 GitHub 로그인으로, 이메일 항목은 인증번호로 확인한다.
-- 값은 클라이언트가 보내지 못하고 서버가 인증 결과로만 켠다. 수동으로 값을 고치면 다시 false
alter table contacts add column verified boolean not null default false;
alter table contacts add column verified_at timestamp with time zone;

-- GitHub 연결의 한 번만 쓰는 중간 상태(apple_sign_in_states와 같은 모양). START: 인가 요청의 state(id). RESULT: 콜백이 받은 login을 앱이 complete로 바꿔 가기 전까지
create table github_contact_links
(
    id         uuid primary key,
    user_id    uuid                     not null references users (id) on delete cascade,
    step       varchar(10)              not null check (step in ('START', 'RESULT')),
    platform   varchar(10)              not null check (platform in ('ios', 'android', 'web')),
    login      varchar(39),
    expires_at timestamp with time zone not null
);

-- 이메일 인증번호. 같은 이메일 1분당 1회·하루 10회 제한을 세기 위해 쓴 뒤에도 하루 동안 남긴다(consumed_at). 코드는 SHA-256
create table email_contact_challenges
(
    id          uuid primary key,
    user_id     uuid                     not null references users (id) on delete cascade,
    email       varchar(320)             not null,
    code_hash   varchar(64)              not null,
    attempts    integer                  not null default 0,
    created_at  timestamp with time zone not null,
    expires_at  timestamp with time zone not null,
    consumed_at timestamp with time zone
);
create index email_contact_challenges_email_idx on email_contact_challenges (lower(email), created_at);
