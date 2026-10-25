-- 2026-10-25 광고 목적 개인정보 제3자 제공 동의(선택, native-v1.md "광고 목적 개인정보 제3자 제공 동의(선택)"). 제공받는 자 Google LLC(AdMob).
-- 행이 있고 version이 서버 상수와 같을 때만 앱이 광고 SDK를 시작·요청한다. 철회하면 행을 지운다(철회 시각은 남기지 않는다). 계정 삭제와 함께 지워진다
create table user_ad_consents
(
    user_id      uuid primary key references users (id) on delete cascade,
    version      varchar(20)              not null,
    consented_at timestamp with time zone not null
);
