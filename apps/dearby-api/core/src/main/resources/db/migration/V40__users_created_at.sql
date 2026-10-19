-- 2026-10-19 사용자 결정(native-v1.md "설정"): 계정 화면의 가입일. users에는 시각이 없었으므로
-- 기존 행은 가장 이른 외부 로그인 연결·패스키·프로필 시각으로 채운다(패스키 시각은 시간대 없는 열이라 근사치).
-- 패스키는 user handle(UUID 16바이트 base64url) 또는 2026-10-15 이전 가입분의 name(UUID 문자열)으로 찾는다
alter table users add column created_at timestamp with time zone not null default now();
update users u
set created_at = least(
    (select min(i.created_at) from user_identities i where i.user_id = u.id),
    (select min(c.created)
     from user_credentials c
              join user_entities e on e.id = c.user_entity_user_id
     where e.id = rtrim(translate(encode(decode(replace(u.id::text, '-', ''), 'hex'), 'base64'), '+/', '-_'), '=')
        or e.name = u.id::text),
    (select p.created_at from profiles p where p.user_id = u.id),
    u.created_at);
