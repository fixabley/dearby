-- Public snapshot: add Organization.parentId and include all ancestor organizations (catalog-v1 2026-10-06).
-- Body otherwise unchanged from 20260929010000_catalog_criteria.sql.
-- Apply to production only after the API organization schema accepts parentId (see catalog-v1.md).
create or replace function public.catalog_public_snapshot() returns jsonb language sql stable security definer set search_path = '' as $$
 with recursive visible as (select * from public.catalog_activities where publication_status='published'),
 -- Referenced organizations plus every ancestor, so each parentId resolves inside the same snapshot.
 public_organizations(id, parent_id) as (
  select o.id, o.parent_id from public.catalog_organizations o where exists(select 1 from visible a where a.organization_id=o.id)
  union
  select o.id, o.parent_id from public.catalog_organizations o join public_organizations c on o.id=c.parent_id)
 select jsonb_build_object('generatedAt',now(),
 'organizations', coalesce((select jsonb_agg(jsonb_build_object('id',o.id,'name',o.name,'description',o.description,'parentId',o.parent_id) order by o.id)
  from public.catalog_organizations o where o.id in (select id from public_organizations)),'[]'::jsonb),
 'programs', coalesce((select jsonb_agg(jsonb_build_object('id',p.id,'organizationId',p.organization_id,'title',p.title,'description',p.description) order by p.id)
  from public.catalog_programs p where exists(select 1 from visible a where a.program_id=p.id)),'[]'::jsonb),
 'activities', coalesce((select jsonb_agg(jsonb_build_object(
  'id',id,'programId',program_id,'organizationId',organization_id,'title',title,'summary',summary,
  'participationType',participation_type,'recruitmentStatus',recruitment_status,'isRecruiting',false,
  'recruitmentStartAt',recruitment_start_at,'recruitmentEndAt',recruitment_end_at,'dateLabel',date_label,
  'location',location,'cost',cost,'audience',nullif(concat_ws(' · ',nullif(array_to_string(public.catalog_condition_text(criteria->'audience'),' · '),''),nullif(audience,'')),''),'qualification',nullif(concat_ws(' · ',nullif(array_to_string(public.catalog_condition_text(criteria->'qualification'),' · '),''),nullif(qualification,'')),''),'roles',case when (criteria->'roles') - '역할'='{}'::jsonb and jsonb_typeof(criteria#>'{roles,역할}')='array' then array(select public.catalog_value_text(v) from jsonb_array_elements(criteria#>'{roles,역할}') v) else public.catalog_condition_text(criteria->'roles') end,'schedules',schedules,
  'officialUrl',official_url,'applicationUrl',application_url,'imageUrl',image_url,
  'sourceCheckedAt',source_checked_at,'validUntil',valid_until,'freshness',freshness,'sourceNote',source_note) order by id) from visible),'[]'::jsonb));
$$;
