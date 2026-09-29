-- Read-only validation for a newly migrated or paused migration target. Never seeds data.
BEGIN READ ONLY;
DO $$
DECLARE relation regclass;
BEGIN
 IF (SELECT count(*) FROM pg_tables WHERE schemaname='public' AND tablename LIKE 'catalog_%') <> 7 THEN
  RAISE EXCEPTION 'Expected seven catalog tables';
 END IF;
 FOR relation IN SELECT oid FROM pg_class WHERE relnamespace='public'::regnamespace AND relkind='r' LOOP
  IF NOT (SELECT relrowsecurity FROM pg_class WHERE oid=relation) THEN RAISE EXCEPTION 'Catalog RLS missing'; END IF;
  IF has_table_privilege('anon',relation,'SELECT,INSERT,UPDATE,DELETE') THEN RAISE EXCEPTION 'Anonymous raw-table access'; END IF;
 END LOOP;
 IF NOT has_function_privilege('anon','public.catalog_public_snapshot()','EXECUTE') THEN RAISE EXCEPTION 'Public snapshot unavailable'; END IF;
 IF has_function_privilege('anon','public.claim_catalog_collection(uuid)','EXECUTE') OR
    has_function_privilege('authenticated','public.claim_catalog_collection(uuid)','EXECUTE') OR
    NOT has_function_privilege('service_role','public.claim_catalog_collection(uuid)','EXECUTE') THEN
  RAISE EXCEPTION 'Collection worker boundary invalid';
 END IF;
 IF has_function_privilege('anon','public.verify_catalog_activity(uuid,text)','EXECUTE') OR
    NOT has_function_privilege('authenticated','public.verify_catalog_activity(uuid,text)','EXECUTE') THEN
  RAISE EXCEPTION 'Administrator RPC boundary invalid';
 END IF;
 IF EXISTS(SELECT FROM public.catalog_collection_settings WHERE enabled) THEN
  RAISE EXCEPTION 'Collection must remain paused until cutover approval';
 END IF;
END $$;
SET LOCAL ROLE authenticated;
DO $$ BEGIN
 IF public.is_catalog_admin() OR EXISTS(SELECT FROM public.catalog_activities) THEN
  RAISE EXCEPTION 'Ordinary authenticated user can read private catalog data';
 END IF;
END $$;
SET LOCAL ROLE anon;
DO $$ BEGIN
 IF jsonb_typeof(public.catalog_public_snapshot()->'activities') IS DISTINCT FROM 'array' THEN
  RAISE EXCEPTION 'Public snapshot shape invalid';
 END IF;
END $$;
ROLLBACK;
