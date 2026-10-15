-- 2026-10-15 사용자 결정: OS 패스키 목록에 계정 이름으로 UUID 대신 가입 때 넣은 이름(기본 "Dearby")을 보여 준다.
-- 그래서 user_entities.name은 사람이 읽는 이름이 되어 여러 계정이 같은 값을 가질 수 있다.
-- 계정 연결은 user handle(user_entities.id = 사용자 UUID 16바이트의 base64url)로 한다. 이전 가입분은 name에 UUID가 남아 있다
alter table user_entities drop constraint user_entities_name_key;
