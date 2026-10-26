-- 2026-10-26 명함 사진(native-v1.md "프로필 사진" › 명함에 싣기). 발행·고치기 시점 프로필 사진 바이트를 명함 쪽에 복사한다(스냅샷).
-- photo_updated_at은 복사한 프로필 사진의 updated_at(photoUrl의 v)이고 사진이 없으면 null. 명함 지우기 때 사진 행을 지우고 null로 둔다
alter table business_cards add column photo_updated_at timestamp with time zone;

create table business_card_photos
(
    card_id uuid primary key references business_cards (id) on delete cascade,
    data    bytea not null
);

-- 신고 시점 명함 사진(신고 근거). 신고 행과 함께 지워진다(신고일 1개월 뒤·계정 삭제)
create table card_report_photos
(
    report_id uuid primary key references card_reports (id) on delete cascade,
    data      bytea not null
);
