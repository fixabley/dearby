-- API storage is private. Supabase SQL migrations are the only DDL pipeline.
CREATE SCHEMA dearby_api;
REVOKE ALL ON SCHEMA dearby_api FROM PUBLIC, anon, authenticated, service_role;
DO $$ BEGIN
 IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='dearby_api_runtime') THEN
  CREATE ROLE dearby_api_runtime NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT NOREPLICATION NOBYPASSRLS;
 END IF;
 IF EXISTS(SELECT FROM pg_roles WHERE rolname='dearby_api_runtime' AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolinherit OR rolreplication OR rolbypassrls)) THEN
  RAISE EXCEPTION 'Unsafe API runtime role';
 END IF;
END $$;
CREATE TABLE dearby_api.profiles(id text PRIMARY KEY,email text NOT NULL UNIQUE,data text NOT NULL);
CREATE TABLE dearby_api.challenges(id text PRIMARY KEY,email text NOT NULL,digest text NOT NULL,expires_at bigint NOT NULL,attempts integer NOT NULL DEFAULT 0,consumed boolean NOT NULL DEFAULT false);
CREATE TABLE dearby_api.rate_limits(key text PRIMARY KEY,window_start bigint NOT NULL,count integer NOT NULL);
CREATE TABLE dearby_api.sessions(digest text PRIMARY KEY,profile_id text NOT NULL REFERENCES dearby_api.profiles(id) ON UPDATE RESTRICT ON DELETE RESTRICT,expires_at bigint NOT NULL);
CREATE TABLE dearby_api.cards(id text PRIMARY KEY,owner_id text NOT NULL REFERENCES dearby_api.profiles(id) ON UPDATE RESTRICT ON DELETE RESTRICT,data text NOT NULL,revoked boolean NOT NULL DEFAULT false,ordinal bigserial NOT NULL);
CREATE TABLE dearby_api.receipts(id text PRIMARY KEY,recipient_id text NOT NULL REFERENCES dearby_api.profiles(id) ON UPDATE RESTRICT ON DELETE RESTRICT,card_id text NOT NULL REFERENCES dearby_api.cards(id) ON UPDATE RESTRICT ON DELETE RESTRICT,context text NOT NULL,received_at text NOT NULL,ordinal bigserial NOT NULL);
CREATE INDEX receipts_recipient_id_card_id_idx ON dearby_api.receipts(recipient_id,card_id);
CREATE TABLE dearby_api.exchanges(sender_id text NOT NULL REFERENCES dearby_api.profiles(id) ON UPDATE RESTRICT ON DELETE RESTRICT,request_id text NOT NULL,body text NOT NULL,receipt_id text NOT NULL REFERENCES dearby_api.receipts(id) ON UPDATE RESTRICT ON DELETE RESTRICT,delivered_at text NOT NULL,PRIMARY KEY(sender_id,request_id));
CREATE TABLE dearby_api.guest_sessions(digest text PRIMARY KEY);
CREATE TABLE dearby_api.guest_cards(session_digest text NOT NULL REFERENCES dearby_api.guest_sessions(digest) ON DELETE CASCADE ON UPDATE RESTRICT,card_id text NOT NULL REFERENCES dearby_api.cards(id) ON UPDATE RESTRICT ON DELETE RESTRICT,ordinal bigserial NOT NULL,PRIMARY KEY(session_digest,card_id));
CREATE TABLE dearby_api.catalog_organizations(id text PRIMARY KEY,content text NOT NULL);
CREATE TABLE dearby_api.catalog_programs(id text PRIMARY KEY,organization_id text NOT NULL REFERENCES dearby_api.catalog_organizations(id) ON UPDATE RESTRICT ON DELETE RESTRICT,content text NOT NULL,UNIQUE(id,organization_id));
CREATE TABLE dearby_api.catalog_activities(id text PRIMARY KEY,program_id text NOT NULL,organization_id text NOT NULL,source_key text NOT NULL UNIQUE,content text NOT NULL,last_failure text,good_body_sha256 text,FOREIGN KEY(program_id,organization_id) REFERENCES dearby_api.catalog_programs(id,organization_id) ON UPDATE RESTRICT ON DELETE RESTRICT);
CREATE TABLE dearby_api.catalog_refreshes(source_key text PRIMARY KEY,attempted_at text NOT NULL,succeeded boolean NOT NULL,body_sha256 text,note text NOT NULL);
CREATE TABLE dearby_api.sqlite_migrations(name text PRIMARY KEY);
REVOKE ALL ON ALL TABLES IN SCHEMA dearby_api FROM PUBLIC, anon, authenticated, service_role;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA dearby_api FROM PUBLIC, anon, authenticated, service_role;
GRANT USAGE ON SCHEMA dearby_api TO dearby_api_runtime;
GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA dearby_api TO dearby_api_runtime;
GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA dearby_api TO dearby_api_runtime;
DO $$ DECLARE tab record; BEGIN
 FOR tab IN SELECT tablename FROM pg_tables WHERE schemaname='dearby_api' LOOP
  EXECUTE format('ALTER TABLE dearby_api.%I ENABLE ROW LEVEL SECURITY',tab.tablename);
  EXECUTE format('CREATE POLICY api_runtime ON dearby_api.%I TO dearby_api_runtime USING(true) WITH CHECK(true)',tab.tablename);
 END LOOP;
END $$;
GRANT USAGE ON SCHEMA public TO dearby_api_runtime;
GRANT EXECUTE ON FUNCTION public.catalog_public_snapshot() TO dearby_api_runtime;
-- No LOGIN password, exposed-schema setting, managed auth grants, or default grants here.
