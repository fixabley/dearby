-- 2026-10-16 사용자 결정: 계정 삭제 때 애플 토큰을 폐기한다(App Store 심사 기준 5.1.1(v)).
-- 폐기에는 애플 토큰 엔드포인트에서 받은 refresh token과 그 토큰을 받은 client_id(번들 ID 또는 Services ID)가 필요하다.
-- 로그인 때 authorization code를 바꿔 받아 둔다. 서명 키(.p8)가 설정되지 않은 동안 로그인한 계정은 비어 있다
alter table user_identities add column client_id varchar(255);
alter table user_identities add column refresh_token varchar(1000);
alter table apple_sign_in_states add column client_id varchar(255);
alter table apple_sign_in_states add column refresh_token varchar(1000);
