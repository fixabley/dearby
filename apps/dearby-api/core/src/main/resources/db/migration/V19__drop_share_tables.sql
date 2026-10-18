-- 2026-10-18 명함 공유 삭제(#234) 뒤 남은 공유 기록 테이블을 지운다. 공유 기록은 복구하지 않는다.
-- receipts·guest_cards·business_cards는 그대로다(받은 명함·웹 저장·명함 발행은 남는다).
drop table if exists guest_card_shares;
drop table if exists wallet_shares;
drop table if exists card_shares;
