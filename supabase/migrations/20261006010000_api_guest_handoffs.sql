-- Home-screen session handoff (native-v1 2026-10-06): one live code per guest session.
-- Only the code digest is stored; the token ciphertext needs the code itself to decrypt and is cleared on use/expiry.
CREATE TABLE dearby_api.guest_handoffs(digest text PRIMARY KEY,session_digest text NOT NULL UNIQUE REFERENCES dearby_api.guest_sessions(digest) ON UPDATE RESTRICT ON DELETE CASCADE,token_ciphertext text,expires_at bigint NOT NULL,used_at bigint);
REVOKE ALL ON dearby_api.guest_handoffs FROM PUBLIC, anon, authenticated, service_role;
GRANT SELECT,INSERT,UPDATE,DELETE ON dearby_api.guest_handoffs TO dearby_api_runtime;
ALTER TABLE dearby_api.guest_handoffs ENABLE ROW LEVEL SECURITY;
CREATE POLICY api_runtime ON dearby_api.guest_handoffs TO dearby_api_runtime USING(true) WITH CHECK(true);
