-- 2026-10-19 개인정보 동의(native-v1.md "개인정보 동의"): 프로필을 처음 저장하기 전에 개인정보 수집·이용 동의와 이용약관 동의를 기록한다.
-- 버전은 서버 설정(dearby.consent.*)의 문서 시행일이며, 문서가 바뀌면 버전이 달라져 다시 동의한다. 계정 삭제와 함께 지워진다
create table user_consents
(
    user_id         uuid primary key references users (id) on delete cascade,
    privacy_version varchar(20)              not null,
    terms_version   varchar(20)              not null,
    consented_at    timestamp with time zone not null
);
