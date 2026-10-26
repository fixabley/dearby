-- 2026-10-25 사용자 결정(native-v1.md "명함 지우기"): 명함을 지우면 서버에서 그 명함의 내용을 바로 비운다.
-- 행과 id·user_id·revoked·seq·created_at·updated_at은 남긴다(card_qr_codes·receipts·guest_cards·card_reports가 card_id를 가리킨다).
-- 이 마이그레이션은 이미 지운(revoked = true) 기존 명함의 내용도 같은 값으로 비운다. 사용자 의도상 지운 명함에는 내용이 없어야 하기 때문이다.
-- 철회된 명함은 어떤 조회에서도 읽지 않으므로(모두 revoked = false로 거른다) 화면에는 영향이 없다. NOT NULL 열이라 빈 값으로 덮는다.
-- 신고 근거인 card_reports.card_snapshot은 건드리지 않는다(신고일로부터 1개월 뒤 batch가 지우는 기존 규칙을 따른다).
update business_cards
set name         = '',
    description  = '',
    template     = '',
    introduction = '',
    profile_name = '',
    job          = '',
    contacts     = '[]',
    histories    = '[]'
where revoked;
