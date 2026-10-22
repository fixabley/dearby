-- 명함 QR 코드(native-v1.md "명함 QR 코드"). 여러 명이 쓰는 코드, 원문 없이 SHA-256 hex만.
-- expires_at: 발급 30분, 닫기·새로고침 때 지금+2분으로 당긴다. revoked_at: 명함 철회 때
create table card_qr_codes
(
    id         uuid primary key,
    card_id    uuid                     not null references business_cards (id) on delete cascade,
    code_hash  char(64)                 not null unique,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone
);
create index card_qr_codes_card_id_idx on card_qr_codes (card_id);
