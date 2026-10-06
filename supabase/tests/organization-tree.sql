-- Organization hierarchy rules. Writes fixtures inside one transaction and rolls back.
-- Local Supabase only; never run against the cloud project.
BEGIN;
INSERT INTO public.catalog_organizations(id,name,parent_id) VALUES
 ('00000000-0000-4000-8000-0000000000a1','L1',null),
 ('00000000-0000-4000-8000-0000000000a2','L2','00000000-0000-4000-8000-0000000000a1'),
 ('00000000-0000-4000-8000-0000000000a3','L3','00000000-0000-4000-8000-0000000000a2'),
 ('00000000-0000-4000-8000-0000000000a4','L4','00000000-0000-4000-8000-0000000000a3'),
 ('00000000-0000-4000-8000-0000000000b1','Root',null),
 ('00000000-0000-4000-8000-0000000000b2','Child','00000000-0000-4000-8000-0000000000b1'),
 ('00000000-0000-4000-8000-0000000000b3','Grandchild','00000000-0000-4000-8000-0000000000b2'),
 ('00000000-0000-4000-8000-0000000000c1','Draft only',null);
INSERT INTO public.catalog_programs(id,organization_id,title) VALUES
 ('00000000-0000-4000-8000-0000000000d4','00000000-0000-4000-8000-0000000000a4','Deep program'),
 ('00000000-0000-4000-8000-0000000000dc','00000000-0000-4000-8000-0000000000c1','Draft program');
INSERT INTO public.catalog_activities(program_id,organization_id,title,official_url,publication_status) VALUES
 ('00000000-0000-4000-8000-0000000000d4','00000000-0000-4000-8000-0000000000a4','Published','https://example.com/a','published'),
 ('00000000-0000-4000-8000-0000000000dc','00000000-0000-4000-8000-0000000000c1','Draft','https://example.com/b','draft');

CREATE FUNCTION pg_temp.rejects(statement text, expected text) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
 BEGIN
  EXECUTE statement;
 EXCEPTION WHEN check_violation OR foreign_key_violation THEN
  IF SQLSTATE <> expected THEN RAISE EXCEPTION 'Wrong error % for: %', SQLSTATE, statement; END IF;
  RETURN;
 END;
 RAISE EXCEPTION 'Accepted: %', statement;
END $$;

DO $$ BEGIN
 -- Depth: level 5 under a level-4 organization.
 PERFORM pg_temp.rejects($q$INSERT INTO public.catalog_organizations(name,parent_id) VALUES('L5','00000000-0000-4000-8000-0000000000a4')$q$,'23514');
 -- Cycles: self, direct (L1 under L2) and indirect (L1 under L4).
 PERFORM pg_temp.rejects($q$UPDATE public.catalog_organizations SET parent_id=id WHERE id='00000000-0000-4000-8000-0000000000a1'$q$,'23514');
 PERFORM pg_temp.rejects($q$UPDATE public.catalog_organizations SET parent_id='00000000-0000-4000-8000-0000000000a2' WHERE id='00000000-0000-4000-8000-0000000000a1'$q$,'23514');
 PERFORM pg_temp.rejects($q$UPDATE public.catalog_organizations SET parent_id='00000000-0000-4000-8000-0000000000a4' WHERE id='00000000-0000-4000-8000-0000000000a1'$q$,'23514');
 -- Moving a 3-level subtree under level 2 would reach level 5.
 PERFORM pg_temp.rejects($q$UPDATE public.catalog_organizations SET parent_id='00000000-0000-4000-8000-0000000000a2' WHERE id='00000000-0000-4000-8000-0000000000b1'$q$,'23514');
 -- Deletion with a child organization or a program is refused.
 PERFORM pg_temp.rejects($q$DELETE FROM public.catalog_organizations WHERE id='00000000-0000-4000-8000-0000000000b1'$q$,'23503');
 PERFORM pg_temp.rejects($q$DELETE FROM public.catalog_organizations WHERE id='00000000-0000-4000-8000-0000000000c1'$q$,'23503');
END $$;

-- Allowed: the same subtree under level 1 reaches exactly level 4; a leaf can be deleted; unchanged parent updates pass.
UPDATE public.catalog_organizations SET parent_id='00000000-0000-4000-8000-0000000000a1' WHERE id='00000000-0000-4000-8000-0000000000b1';
UPDATE public.catalog_organizations SET name='L4 renamed', parent_id=parent_id WHERE id='00000000-0000-4000-8000-0000000000a4';
DELETE FROM public.catalog_organizations WHERE id='00000000-0000-4000-8000-0000000000b3';
UPDATE public.catalog_organizations SET parent_id=null WHERE id='00000000-0000-4000-8000-0000000000b1';

SET LOCAL ROLE anon;
DO $$
DECLARE snapshot jsonb := public.catalog_public_snapshot(); ids text[];
BEGIN
 SELECT array_agg(o->>'id' ORDER BY o->>'id') INTO ids FROM jsonb_array_elements(snapshot->'organizations') o;
 IF ids IS DISTINCT FROM ARRAY['00000000-0000-4000-8000-0000000000a1','00000000-0000-4000-8000-0000000000a2',
   '00000000-0000-4000-8000-0000000000a3','00000000-0000-4000-8000-0000000000a4'] THEN
  RAISE EXCEPTION 'Snapshot must contain the referenced organization and all ancestors only: %', ids;
 END IF;
 IF EXISTS(SELECT FROM jsonb_array_elements(snapshot->'organizations') o
   WHERE NOT (o ? 'parentId') OR (o->>'parentId' IS NOT NULL AND NOT o->>'parentId' = ANY(ids))) THEN
  RAISE EXCEPTION 'Every organization needs parentId resolving inside the snapshot';
 END IF;
 IF (SELECT o->'parentId' FROM jsonb_array_elements(snapshot->'organizations') o WHERE o->>'id'='00000000-0000-4000-8000-0000000000a1') <> 'null'::jsonb THEN
  RAISE EXCEPTION 'Top-level parentId must be null';
 END IF;
END $$;
ROLLBACK;
