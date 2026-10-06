-- Public snapshot exposes public_note only, never the internal source_note. Writes fixtures and rolls back.
-- Local Supabase only; never run against the cloud project.
BEGIN;
INSERT INTO public.catalog_organizations(id,name) VALUES ('00000000-0000-4000-8000-0000000000e1','Public note org');
INSERT INTO public.catalog_programs(id,organization_id,title) VALUES ('00000000-0000-4000-8000-0000000000e2','00000000-0000-4000-8000-0000000000e1','Public note program');
INSERT INTO public.catalog_activities(id,program_id,organization_id,title,official_url,publication_status,
  source_checked_at,valid_until,freshness,source_note,public_note) VALUES
 ('00000000-0000-4000-8000-0000000000e3','00000000-0000-4000-8000-0000000000e2','00000000-0000-4000-8000-0000000000e1',
  'With public note','https://example.com/a','published',now(),now()+interval '1 hour','verified','INTERNAL-MEMO-1','공식 공지에서 마감 일시를 확인했어요.'),
 ('00000000-0000-4000-8000-0000000000e4','00000000-0000-4000-8000-0000000000e2','00000000-0000-4000-8000-0000000000e1',
  'Without public note','https://example.com/b','published',now(),now()+interval '1 hour','verified','INTERNAL-MEMO-2',default);

SET LOCAL ROLE anon;
DO $$
DECLARE snapshot jsonb := public.catalog_public_snapshot();
BEGIN
 IF (SELECT a->>'sourceNote' FROM jsonb_array_elements(snapshot->'activities') a WHERE a->>'id'='00000000-0000-4000-8000-0000000000e3')
    IS DISTINCT FROM '공식 공지에서 마감 일시를 확인했어요.' THEN
  RAISE EXCEPTION 'sourceNote must come from public_note';
 END IF;
 IF (SELECT a->>'sourceNote' FROM jsonb_array_elements(snapshot->'activities') a WHERE a->>'id'='00000000-0000-4000-8000-0000000000e4') IS DISTINCT FROM '' THEN
  RAISE EXCEPTION 'Empty public_note must export an empty sourceNote';
 END IF;
 IF snapshot::text LIKE '%INTERNAL-MEMO%' THEN RAISE EXCEPTION 'Internal source_note leaked into the public snapshot'; END IF;
 IF (SELECT count(*) FROM jsonb_array_elements(snapshot->'organizations') o WHERE o ? 'parentId') = 0 THEN
  RAISE EXCEPTION 'Snapshot lost the organization hierarchy';
 END IF;
END $$;
RESET ROLE;

-- Changing the visitor-facing text is a content change: it clears verification.
UPDATE public.catalog_activities SET public_note='마감 일정이 바뀌었어요.' WHERE id='00000000-0000-4000-8000-0000000000e3';
DO $$ BEGIN
 IF (SELECT freshness FROM public.catalog_activities WHERE id='00000000-0000-4000-8000-0000000000e3') <> 'stale' THEN
  RAISE EXCEPTION 'Editing public_note must clear verification';
 END IF;
END $$;
ROLLBACK;
