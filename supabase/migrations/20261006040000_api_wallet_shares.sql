-- Member wallet share links (native-v1 "앱에서 받은 공유 저장 (회원 wallet)" 2026-10-06).
-- Receipts are never deleted and withdrawal only hides them, so links follow the same retention.
-- (share_id, card_id) must match an existing share of that card; the receipt's card is checked by the API.
CREATE TABLE dearby_api.wallet_shares(receipt_id text NOT NULL REFERENCES dearby_api.receipts(id) ON UPDATE RESTRICT ON DELETE RESTRICT,card_id text NOT NULL,share_id text NOT NULL,saved_at text NOT NULL,ordinal bigserial NOT NULL,PRIMARY KEY(receipt_id,share_id),
 FOREIGN KEY(share_id,card_id) REFERENCES dearby_api.card_shares(id,card_id) ON UPDATE RESTRICT ON DELETE RESTRICT);
REVOKE ALL ON dearby_api.wallet_shares FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON dearby_api.wallet_shares_ordinal_seq FROM PUBLIC, anon, authenticated, service_role;
GRANT SELECT,INSERT,UPDATE,DELETE ON dearby_api.wallet_shares TO dearby_api_runtime;
GRANT USAGE,SELECT ON dearby_api.wallet_shares_ordinal_seq TO dearby_api_runtime;
ALTER TABLE dearby_api.wallet_shares ENABLE ROW LEVEL SECURITY;
CREATE POLICY api_runtime ON dearby_api.wallet_shares TO dearby_api_runtime USING(true) WITH CHECK(true);
