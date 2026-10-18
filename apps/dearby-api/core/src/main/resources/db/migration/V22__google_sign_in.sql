-- 2026-10-18 사용자 결정: 구글 로그인(iOS·Android 모두 웹 방식, native-v1.md "구글 로그인").
-- 외부 로그인 계정 provider에 GOOGLE을 허용하고, 애플 로그인 중간 상태 표를 provider별로 함께 쓴다(구조가 같다).
-- 구글은 refresh token을 저장하지 않으므로(계정 삭제 때 폐기할 토큰이 없다) client_id·refresh_token 열은 비워 둔다
alter table user_identities drop constraint user_identities_provider_check;
alter table user_identities add constraint user_identities_provider_check check (provider in ('APPLE', 'GOOGLE'));
alter table apple_sign_in_states add column provider varchar(20) not null default 'APPLE' check (provider in ('APPLE', 'GOOGLE'));
