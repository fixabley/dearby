-- Card share records and guest share links (native-v1 2026-10-06). Activities are a title snapshot JSON.
CREATE TABLE dearby_api.card_shares(id text PRIMARY KEY,card_id text NOT NULL REFERENCES dearby_api.cards(id) ON UPDATE RESTRICT ON DELETE RESTRICT,activities text NOT NULL,created_at text NOT NULL,ordinal bigserial NOT NULL,UNIQUE(id,card_id));
-- Removing a saved guest card (or its session) cascades its share links; a link cannot point at another card's share.
CREATE TABLE dearby_api.guest_card_shares(session_digest text NOT NULL,card_id text NOT NULL,share_id text NOT NULL,saved_at text NOT NULL,ordinal bigserial NOT NULL,PRIMARY KEY(session_digest,share_id),
 FOREIGN KEY(session_digest,card_id) REFERENCES dearby_api.guest_cards(session_digest,card_id) ON UPDATE RESTRICT ON DELETE CASCADE,
 FOREIGN KEY(share_id,card_id) REFERENCES dearby_api.card_shares(id,card_id) ON UPDATE RESTRICT ON DELETE RESTRICT);
CREATE INDEX guest_card_shares_session_digest_card_id_idx ON dearby_api.guest_card_shares(session_digest,card_id);
-- The original API migration granted only tables existing at that time; repeat its boundary explicitly.
REVOKE ALL ON dearby_api.card_shares,dearby_api.guest_card_shares FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON dearby_api.card_shares_ordinal_seq,dearby_api.guest_card_shares_ordinal_seq FROM PUBLIC, anon, authenticated, service_role;
GRANT SELECT,INSERT,UPDATE,DELETE ON dearby_api.card_shares,dearby_api.guest_card_shares TO dearby_api_runtime;
GRANT USAGE,SELECT ON dearby_api.card_shares_ordinal_seq,dearby_api.guest_card_shares_ordinal_seq TO dearby_api_runtime;
ALTER TABLE dearby_api.card_shares ENABLE ROW LEVEL SECURITY;
ALTER TABLE dearby_api.guest_card_shares ENABLE ROW LEVEL SECURITY;
CREATE POLICY api_runtime ON dearby_api.card_shares TO dearby_api_runtime USING(true) WITH CHECK(true);
CREATE POLICY api_runtime ON dearby_api.guest_card_shares TO dearby_api_runtime USING(true) WITH CHECK(true);
