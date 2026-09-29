-- Discovery only. Native account/card tables remain in the existing API database.
create table public.catalog_organizations (
 id uuid primary key default gen_random_uuid(), name text not null check(length(trim(name)) between 1 and 200),
 description text not null default '', updated_at timestamptz not null default now()
);
create table public.catalog_programs (
 id uuid primary key default gen_random_uuid(), organization_id uuid not null references public.catalog_organizations(id),
 title text not null check(length(trim(title)) between 1 and 300), description text not null default '',
 updated_at timestamptz not null default now(), unique(id, organization_id)
);
create function public.valid_catalog_schedules(items jsonb) returns boolean language plpgsql stable set search_path = '' as $$
declare item jsonb; begin
 if jsonb_typeof(items) <> 'array' then return false; end if;
 for item in select * from jsonb_array_elements(items) loop
  if jsonb_typeof(item) <> 'object' or not (item ?& array['id','title','startAt','endAt','dateLabel','timeZone'])
   or jsonb_typeof(item->'title') <> 'string' or jsonb_typeof(item->'dateLabel') <> 'string'
   or not exists(select 1 from pg_timezone_names where name = item->>'timeZone') then return false; end if;
  perform (item->>'id')::uuid;
  if item->>'startAt' is not null and (item->>'startAt') !~ '(Z|[+-][0-9]{2}:[0-9]{2})$' then return false; end if;
  if item->>'endAt' is not null and (item->>'endAt') !~ '(Z|[+-][0-9]{2}:[0-9]{2})$' then return false; end if;
  if item->>'startAt' is not null and item->>'endAt' is not null and (item->>'startAt')::timestamptz >= (item->>'endAt')::timestamptz then return false; end if;
  perform (item->>'startAt')::timestamptz, (item->>'endAt')::timestamptz;
 end loop;
 return (select count(*) = count(distinct value->>'id') from jsonb_array_elements(items));
 exception when others then return false;
end $$;
create table public.catalog_activities (
 id uuid primary key default gen_random_uuid(), program_id uuid not null, organization_id uuid not null,
 title text not null check(length(trim(title)) between 1 and 300), summary text not null default '',
 participation_type text not null default 'registration' check(participation_type in ('registration','selection')),
 recruitment_status text not null default 'unknown' check(recruitment_status in ('open','scheduled','closed','unknown')),
 recruitment_start_at timestamptz, recruitment_end_at timestamptz,
 date_label text not null default '', location text, cost text, audience text, qualification text,
 roles text[] not null default '{}', schedules jsonb not null default '[]' check(public.valid_catalog_schedules(schedules)),
 official_url text not null check(official_url ~ '^https?://[^[:space:]/]+'),
 application_url text check(application_url ~ '^https?://[^[:space:]/]+'),
 image_url text check(image_url ~ '^https?://[^[:space:]/]+'),
 publication_status text not null default 'draft' check(publication_status in ('draft','published','hidden')),
 source_checked_at timestamptz, valid_until timestamptz,
 freshness text not null default 'stale' check(freshness in ('verified','stale','unavailable')),
 source_note text not null default '', updated_at timestamptz not null default now(),
 foreign key(program_id,organization_id) references public.catalog_programs(id,organization_id),
 check(recruitment_start_at is null or recruitment_end_at is null or recruitment_start_at < recruitment_end_at),
 check(freshness <> 'verified' or (source_checked_at is not null and valid_until is not null and length(trim(source_note)) > 0
   and valid_until > source_checked_at and valid_until <= source_checked_at + interval '24 hours'))
);
create index catalog_activities_publication on public.catalog_activities(publication_status);
create index catalog_activities_program on public.catalog_activities(program_id,organization_id);
create index catalog_programs_organization on public.catalog_programs(organization_id);
create table public.catalog_audit_log (
 id bigint generated always as identity primary key, occurred_at timestamptz not null default now(),
 actor_id uuid, resource text not null, record_id uuid not null, operation text not null,
 before_data jsonb, after_data jsonb
);
-- Query trusted Auth storage so revocation takes effect without waiting for JWT expiry.
create function public.is_catalog_admin() returns boolean language sql stable security definer set search_path = '' as $$
 select exists(select 1 from auth.users where id = auth.uid() and raw_app_meta_data->>'catalog_admin' = 'true');
$$;
revoke all on function public.is_catalog_admin() from public;
grant execute on function public.is_catalog_admin() to authenticated;

create function public.catalog_before_write() returns trigger language plpgsql set search_path = '' as $$
begin
 new.updated_at = now();
 if tg_table_name = 'catalog_activities' then
  if tg_op = 'UPDATE' and (to_jsonb(new) - array['updated_at','source_checked_at','valid_until','freshness','source_note','publication_status'])
    is distinct from (to_jsonb(old) - array['updated_at','source_checked_at','valid_until','freshness','source_note','publication_status']) then
   new.source_checked_at = null; new.valid_until = null; new.freshness = 'stale';
  end if;
  if new.source_checked_at > now() then raise exception 'Source verification cannot be in the future'; end if;
 end if;
 return new;
end $$;
create function public.catalog_audit_write() returns trigger language plpgsql security definer set search_path = '' as $$
begin
 insert into public.catalog_audit_log(actor_id,resource,record_id,operation,before_data,after_data)
 values(auth.uid(),tg_table_name,coalesce(new.id,old.id),tg_op,case when tg_op <> 'INSERT' then to_jsonb(old) end,case when tg_op <> 'DELETE' then to_jsonb(new) end);
 return coalesce(new,old);
end $$;
revoke all on function public.catalog_audit_write() from public;
do $$ declare t text; begin
 foreach t in array array['catalog_organizations','catalog_programs','catalog_activities'] loop
  execute format('alter table public.%I enable row level security',t);
  execute format('revoke all on public.%I from anon, authenticated',t);
  execute format('grant select, insert, update on public.%I to authenticated',t);
  execute format('create policy admin_access on public.%I for all to authenticated using ((select public.is_catalog_admin())) with check ((select public.is_catalog_admin()))',t);
  execute format('create trigger before_write before insert or update on public.%I for each row execute function public.catalog_before_write()',t);
  execute format('create trigger audit_write after insert or update or delete on public.%I for each row execute function public.catalog_audit_write()',t);
 end loop;
end $$;
alter table public.catalog_audit_log enable row level security;
revoke all on public.catalog_audit_log from anon, authenticated;
grant select on public.catalog_audit_log to authenticated;
create policy admin_read_audit on public.catalog_audit_log for select to authenticated using ((select public.is_catalog_admin()));

create function public.verify_catalog_activity(activity_id uuid, evidence_note text) returns public.catalog_activities
 language plpgsql security definer set search_path = '' as $$
declare result public.catalog_activities; begin
 if not public.is_catalog_admin() then raise insufficient_privilege using message = 'Catalog administrator required'; end if;
 if length(trim(evidence_note)) < 10 then raise exception 'Official evidence note must contain at least 10 characters'; end if;
 update public.catalog_activities set source_checked_at=now(),valid_until=now()+interval '24 hours',freshness='verified',source_note=trim(evidence_note)
 where id=activity_id returning * into result;
 if not found then raise exception 'Activity not found'; end if;
 return result;
end $$;
revoke all on function public.verify_catalog_activity(uuid,text) from public;
grant execute on function public.verify_catalog_activity(uuid,text) to authenticated;

-- One transaction snapshot. Only published records and their parents leave the database.
create function public.catalog_public_snapshot() returns jsonb language sql stable security definer set search_path = '' as $$
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
  'location',location,'cost',cost,'audience',audience,'qualification',qualification,'roles',roles,'schedules',schedules,
  'officialUrl',official_url,'applicationUrl',application_url,'imageUrl',image_url,
  'sourceCheckedAt',source_checked_at,'validUntil',valid_until,'freshness',freshness,'sourceNote',source_note) order by id) from visible),'[]'::jsonb));
$$;
revoke all on function public.catalog_public_snapshot() from public;
grant execute on function public.catalog_public_snapshot() to anon, authenticated;
