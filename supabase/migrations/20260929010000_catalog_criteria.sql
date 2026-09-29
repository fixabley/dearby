-- Structured admin conditions; legacy sentences remain explanatory notes.
create extension if not exists pg_trgm with schema extensions;
create function public.catalog_condition_key(value text) returns text
language sql immutable strict set search_path = '' as $$
 select trim(regexp_replace(normalize(replace(value, chr(160), ' '), NFC), '[[:space:]]+', ' ', 'g'));
$$;
create or replace function public.valid_catalog_json(value jsonb, depth integer default 0) returns boolean
language plpgsql immutable set search_path = '' as $$
declare entry record; seen text[] := '{}'; item jsonb; begin
 if depth>8 then return false; end if;
 case jsonb_typeof(value)
 when 'null','boolean' then return true;
 when 'number' then return abs((value #>> '{}')::numeric) <= 9007199254740991;
 when 'string' then return length(value #>> '{}') <= 2000;
 when 'array' then
  if jsonb_array_length(value)>100 then return false; end if;
  for item in select * from jsonb_array_elements(value) loop
   if not public.valid_catalog_json(item,depth+1) then return false; end if;
  end loop;
 when 'object' then
  if (select count(*) from jsonb_object_keys(value))>30 then return false; end if;
  for entry in select * from jsonb_each(value) loop
   if length(entry.key) not between 1 and 80 or entry.key <> public.catalog_condition_key(entry.key)
    or lower(entry.key)=any(seen) or not public.valid_catalog_json(entry.value,depth+1) then return false; end if;
   seen := array_append(seen,lower(entry.key));
  end loop;
  if value ?& array['부터','까지'] and value - array['부터','까지'] = '{}'::jsonb
   and jsonb_typeof(value->'부터')='number' and jsonb_typeof(value->'까지')='number'
   and (value->>'부터')::numeric > (value->>'까지')::numeric then return false; end if;
 else return false;
 end case;
 return true;
end $$;
create or replace function public.valid_catalog_criteria(criteria jsonb) returns boolean
language sql immutable set search_path = '' as $$
 select criteria is not null and octet_length(criteria::text)<=65536
  and jsonb_typeof(criteria)='object' and criteria ?& array['audience','qualification','roles']
  and criteria - array['audience','qualification','roles']='{}'::jsonb
  and jsonb_typeof(criteria->'audience')='object' and public.valid_catalog_json(criteria->'audience')
  and jsonb_typeof(criteria->'qualification')='object' and public.valid_catalog_json(criteria->'qualification')
  and jsonb_typeof(criteria->'roles')='object' and public.valid_catalog_json(criteria->'roles');
$$;
alter table public.catalog_activities add column criteria jsonb not null
 default '{"audience":{},"qualification":{},"roles":{}}'::jsonb
 check(public.valid_catalog_criteria(criteria));
-- Import existing role lists losslessly. Audience/qualification sentences are not split.
update public.catalog_activities set criteria=jsonb_build_object('audience','{}'::jsonb,'qualification','{}'::jsonb,
 'roles',jsonb_build_object('역할',to_jsonb(roles))) where cardinality(roles)>0;
create function public.catalog_import_legacy_roles() returns trigger language plpgsql set search_path = '' as $$
begin
 if tg_op = 'INSERT' then
  if new.criteria->'roles' = '{}'::jsonb and cardinality(new.roles)>0 then
   new.criteria := jsonb_set(new.criteria,'{roles}',jsonb_build_object('역할',to_jsonb(new.roles)));
  end if;
 elsif new.roles is distinct from old.roles then
  new.criteria := jsonb_set(new.criteria,'{roles}',case when cardinality(new.roles)>0 then jsonb_build_object('역할',to_jsonb(new.roles)) else '{}'::jsonb end);
 end if;
 return new;
end $$;
create trigger aa_import_legacy_roles before insert or update on public.catalog_activities
 for each row execute function public.catalog_import_legacy_roles();

create or replace function public.catalog_value_text(value jsonb) returns text
language plpgsql immutable set search_path = '' as $$
begin
 case jsonb_typeof(value)
 when 'null' then return '미지정';
 when 'string','number','boolean' then return value #>> '{}';
 when 'array' then return coalesce((select string_agg(public.catalog_value_text(v),', ' order by n) from jsonb_array_elements(value) with ordinality a(v,n)),'[]');
 when 'object' then
  if value - array['부터','까지']='{}'::jsonb and value <> '{}'::jsonb
   and (not(value ? '부터') or jsonb_typeof(value->'부터')='number')
   and (not(value ? '까지') or jsonb_typeof(value->'까지')='number') then
   return case when value ?& array['부터','까지'] then (value->>'부터') || ' ~ ' || (value->>'까지')
    when value ? '부터' then (value->>'부터') || ' 이상' else (value->>'까지') || ' 이하' end;
  end if;
  return coalesce((select string_agg(key || ': ' || public.catalog_value_text(v),'; ' order by key) from jsonb_each(value) e(key,v)),'{}');
 end case;
 return '';
end $$;
revoke all on function public.catalog_value_text(jsonb) from public,anon,authenticated;
create or replace function public.catalog_condition_text(fields jsonb) returns text[]
language sql immutable set search_path = '' as $$
 select coalesce(array_agg(key || ': ' || public.catalog_value_text(value) order by key),'{}') from jsonb_each(fields);
$$;
revoke all on function public.catalog_condition_text(jsonb) from public,anon,authenticated;
create or replace function public.catalog_criteria_suggestions(category text, query_text text default '', parent_path text[] default '{}')
returns jsonb language plpgsql stable security definer set search_path = '' as $$
declare result jsonb; begin
 if not public.is_catalog_admin() then raise insufficient_privilege using message='Catalog administrator required'; end if;
 if category is null or category not in ('audience','qualification','roles') or query_text is null or length(query_text)>80
  or parent_path is null or cardinality(parent_path)>8 then raise exception 'Invalid suggestion query'; end if;
 with recursive nodes(path,value) as (
  select '{}'::text[],a.criteria->category from public.catalog_activities a
  union all
  select n.path || e.key,e.value from nodes n cross join lateral (
   select key,value from jsonb_each(case when jsonb_typeof(n.value)='object' then n.value else '{}'::jsonb end)
   union all
   select '*',value from jsonb_array_elements(case when jsonb_typeof(n.value)='array' then n.value else '[]'::jsonb end)
  ) e where cardinality(n.path)<cardinality(parent_path)
 ), entries as (
  select e.key,e.value from nodes n cross join lateral jsonb_each(case when jsonb_typeof(n.value)='object' then n.value else '{}'::jsonb end) e
  where n.path=parent_path
 ), keys as (
  select key,count(*) as uses,case when count(distinct jsonb_typeof(value))>1 then 'mixed' else min(jsonb_typeof(value)) end as kind
  from entries where query_text='' or position(lower(public.catalog_condition_key(query_text)) in lower(key))>0
    or extensions.similarity(lower(key),lower(query_text))>0.15
  group by key order by (lower(key)=lower(query_text)) desc,extensions.similarity(lower(key),lower(query_text)) desc,count(*) desc,key limit 20
 )
 select coalesce(jsonb_agg(jsonb_build_object('key',k.key,'kind',k.kind,'values',
  (select coalesce(jsonb_agg(v),'[]'::jsonb) from (
   select distinct vals.v #>> '{}' as v from entries e cross join lateral jsonb_array_elements(
    case jsonb_typeof(e.value) when 'array' then e.value when 'string' then jsonb_build_array(e.value) else '[]'::jsonb end) vals(v)
   where e.key=k.key and jsonb_typeof(vals.v)='string' order by v limit 30
  ) candidates))), '[]'::jsonb) into result from keys k;
 return result;
end $$;
revoke all on function public.catalog_criteria_suggestions(text,text,text[]) from public,anon;
grant execute on function public.catalog_criteria_suggestions(text,text,text[]) to authenticated;
create index if not exists catalog_activities_criteria_gin on public.catalog_activities using gin(criteria);

create or replace function public.catalog_public_snapshot() returns jsonb language sql stable security definer set search_path = '' as $$
 with visible as (select * from public.catalog_activities where publication_status='published')
 select jsonb_build_object('generatedAt',now(),
 'organizations', coalesce((select jsonb_agg(jsonb_build_object('id',o.id,'name',o.name,'description',o.description) order by o.id)
  from public.catalog_organizations o where exists(select 1 from visible a where a.organization_id=o.id)),'[]'::jsonb),
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
