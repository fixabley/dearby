create extension if not exists pg_cron with schema pg_catalog;
alter table public.catalog_programs add column collection_enabled boolean not null default false;
alter table public.catalog_programs add column collection_hosts text[] not null default '{}';
-- Existing official links are the trust boundary, not search-result domains.
update public.catalog_programs p set collection_hosts=(
 select coalesce(array_agg(distinct lower(substring(a.official_url from '^https?://([^/:?#]+)'))),'{}')
 from public.catalog_activities a where a.program_id=p.id
), collection_enabled=p.title !~ '^\[로컬';
create table public.catalog_collection_settings (
 id boolean primary key default true check(id), enabled boolean not null default false
);
insert into public.catalog_collection_settings default values;
create table public.catalog_collection_jobs (
 id uuid primary key default gen_random_uuid(), program_id uuid not null references public.catalog_programs(id),
 program_name text not null, scheduled_day date not null,
 status text not null default 'queued' check(status in ('queued','running','succeeded','failed','blocked','skipped')),
 attempts integer not null default 0, lease_token uuid, lease_until timestamptz,
 available_at timestamptz not null default now(), created_at timestamptz not null default now(),
 started_at timestamptz, finished_at timestamptz, error text, stats jsonb not null default '{}', usage jsonb not null default '{}',
 unique(program_id,scheduled_day)
);
create index catalog_collection_jobs_claim on public.catalog_collection_jobs(status,available_at,scheduled_day);
create table public.catalog_collection_results (
 id uuid primary key default gen_random_uuid(), program_id uuid not null references public.catalog_programs(id),
 job_id uuid not null references public.catalog_collection_jobs(id), source_url text not null,
 occurrence text not null check(length(occurrence) between 1 and 200),
 activity_id uuid references public.catalog_activities(id) on delete set null,
 proposed_activity jsonb not null, evidence jsonb not null,
 last_applied_updated_at timestamptz, checked_at timestamptz not null default now(),
 status text not null check(status in ('applied','review')),
 unique(program_id,source_url,occurrence)
);
do $$ declare t text; begin
 foreach t in array array['catalog_collection_settings','catalog_collection_jobs','catalog_collection_results'] loop
  execute format('alter table public.%I enable row level security',t);
  execute format('revoke all on public.%I from anon,authenticated',t);
  execute format('grant select on public.%I to authenticated',t);
  execute format('create policy admin_read on public.%I for select to authenticated using ((select public.is_catalog_admin()))',t);
 end loop;
end $$;

create function public.enqueue_catalog_collection(target_program uuid default null) returns integer
language plpgsql security definer set search_path = '' as $$
declare added integer; today date := (now() at time zone 'Asia/Seoul')::date; begin
 insert into public.catalog_collection_jobs(program_id,program_name,scheduled_day)
 select id,title,today from public.catalog_programs where collection_enabled and (target_program is null or id=target_program)
 on conflict(program_id,scheduled_day) do nothing;
 get diagnostics added=row_count;
 update public.catalog_collection_jobs set status='skipped',finished_at=now(),error='Replaced by a newer daily collection'
 where status in ('queued','failed') and scheduled_day<today;
 return added;
end $$;
revoke all on function public.enqueue_catalog_collection(uuid) from public,anon,authenticated;
grant execute on function public.enqueue_catalog_collection(uuid) to service_role;
create function public.request_catalog_collection(target_program uuid default null) returns integer
language plpgsql security definer set search_path = '' as $$
begin
 if not public.is_catalog_admin() then raise insufficient_privilege using message='Catalog administrator required'; end if;
 return public.enqueue_catalog_collection(target_program);
end $$;
revoke all on function public.request_catalog_collection(uuid) from public,anon;
grant execute on function public.request_catalog_collection(uuid) to authenticated;

create function public.claim_catalog_collection(target_program uuid default null) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare job public.catalog_collection_jobs; program public.catalog_programs; begin
 if not pg_try_advisory_xact_lock(72491029) then return null; end if;
 -- Expired owners cannot write results; at most one subscription task runs at once.
 update public.catalog_collection_jobs set status=case when attempts>=3 then 'blocked' else 'failed' end,
  error='Worker lease expired',lease_token=null,lease_until=null,available_at=now()+interval '5 minutes'
 where status='running' and lease_until<now();
 if exists(select 1 from public.catalog_collection_jobs where status='running') then return null; end if;
 select j.* into job from public.catalog_collection_jobs j join public.catalog_programs p on p.id=j.program_id
 where j.status in ('queued','failed') and j.attempts<3 and j.available_at<=now() and p.collection_enabled
  and (target_program is null or j.program_id=target_program)
 order by j.scheduled_day desc,j.created_at,j.id for update of j skip locked limit 1;
 if not found then return null; end if;
 select * into program from public.catalog_programs where id=job.program_id;
 update public.catalog_collection_jobs set status='running',attempts=attempts+1,lease_token=gen_random_uuid(),
  lease_until=now()+interval '10 minutes',started_at=now(),finished_at=null,error=null
 where id=job.id returning * into job;
 return jsonb_build_object('job',to_jsonb(job),'program',to_jsonb(program),'knownActivities',
  coalesce((select jsonb_agg(jsonb_build_object('id',id,'title',title,'officialUrl',official_url,'dateLabel',date_label))
   from public.catalog_activities where program_id=program.id),'[]'::jsonb));
end $$;
revoke all on function public.claim_catalog_collection(uuid) from public,anon,authenticated;
grant execute on function public.claim_catalog_collection(uuid) to service_role;
create function public.fail_catalog_collection(job_id uuid, token uuid, reason text, blocked boolean default false) returns void
language plpgsql security definer set search_path = '' as $$
begin
 update public.catalog_collection_jobs set status=case when blocked or attempts>=3 then 'blocked' else 'failed' end,
  error=left(reason,2000),finished_at=now(),available_at=now()+interval '30 minutes',lease_token=null,lease_until=null
 where id=job_id and status='running' and lease_token=token and lease_until>now();
 if not found then raise exception 'Collection lease lost'; end if;
end $$;
revoke all on function public.fail_catalog_collection(uuid,uuid,text,boolean) from public,anon,authenticated;
grant execute on function public.fail_catalog_collection(uuid,uuid,text,boolean) to service_role;

create function public.finish_catalog_collection(job_id uuid, token uuid, items jsonb, run_usage jsonb default '{}', warnings jsonb default '[]') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare job public.catalog_collection_jobs; program public.catalog_programs; entry jsonb; source public.catalog_collection_results;
 activity public.catalog_activities; proposed public.catalog_activities; target uuid; applied timestamptz;
 created_count integer:=0; updated_count integer:=0; review_count integer:=0; state text; result jsonb; host text;
begin
 select * into job from public.catalog_collection_jobs where id=job_id for update;
 if not found or job.status<>'running' or job.lease_token is distinct from token or job.lease_until<=now() then raise exception 'Collection lease lost'; end if;
 select * into program from public.catalog_programs where id=job.program_id;
 if not program.collection_enabled then raise exception 'Collection disabled for program'; end if;
 if jsonb_typeof(items)<>'array' or jsonb_array_length(items)>10 then raise exception 'Invalid collection result'; end if;
 for entry in select * from jsonb_array_elements(items) loop
  host:=lower(substring(entry->>'source_url' from '^https://([^/:?#]+)'));
  if host is null or not host=any(program.collection_hosts) or length(entry->>'occurrence') not between 1 and 200
   or jsonb_typeof(entry->'activity')<>'object' or jsonb_typeof(entry->'evidence')<>'object' then raise exception 'Invalid official source'; end if;
  select * into source from public.catalog_collection_results
   where program_id=program.id and source_url=entry->>'source_url' and occurrence=entry->>'occurrence' for update;
  target:=source.activity_id;
  if target is null then
   -- Adopt only an exact legacy match; never overwrite a different round sharing a homepage.
   select id into target from public.catalog_activities where program_id=program.id and official_url=entry->>'source_url'
    and title=entry#>>'{activity,title}' order by id limit 1;
  end if;
  activity:=null;
  if target is not null then select * into activity from public.catalog_activities where id=target for update; end if;
  proposed:=jsonb_populate_record(null::public.catalog_activities,entry->'activity');
  state:='applied'; applied:=source.last_applied_updated_at;
  if activity.id is null then
   insert into public.catalog_activities(program_id,organization_id,title,summary,participation_type,recruitment_status,
    recruitment_start_at,recruitment_end_at,date_label,location,cost,audience,qualification,roles,schedules,official_url,application_url,source_note)
   values(program.id,program.organization_id,proposed.title,coalesce(proposed.summary,''),coalesce(proposed.participation_type,'registration'),
    coalesce(proposed.recruitment_status,'unknown'),proposed.recruitment_start_at,proposed.recruitment_end_at,coalesce(proposed.date_label,''),
    proposed.location,proposed.cost,proposed.audience,proposed.qualification,coalesce(proposed.roles,'{}'),coalesce(proposed.schedules,'[]'),
    entry->>'source_url',proposed.application_url,'자동 수집 초안 · 공식 원문과 모집 상태를 관리자가 확인해야 합니다.') returning id,updated_at into target,applied;
   created_count:=created_count+1;
  elsif source.last_applied_updated_at is not null and activity.updated_at=source.last_applied_updated_at then
   -- JSON containment compares only collected fields. Unchanged pages don't invalidate verification.
   if not to_jsonb(activity) @> (entry->'activity') then
    update public.catalog_activities set title=proposed.title,summary=coalesce(proposed.summary,''),participation_type=coalesce(proposed.participation_type,'registration'),
     recruitment_status=coalesce(proposed.recruitment_status,'unknown'),recruitment_start_at=proposed.recruitment_start_at,recruitment_end_at=proposed.recruitment_end_at,
     date_label=coalesce(proposed.date_label,''),location=proposed.location,cost=proposed.cost,audience=proposed.audience,qualification=proposed.qualification,
     roles=coalesce(proposed.roles,'{}'),schedules=coalesce(proposed.schedules,'[]'),application_url=proposed.application_url
    where id=target returning updated_at into applied;
    updated_count:=updated_count+1;
   end if;
  else
   if not to_jsonb(activity) @> (entry->'activity') then state:='review'; review_count:=review_count+1; end if;
  end if;
  insert into public.catalog_collection_results(program_id,job_id,source_url,occurrence,activity_id,proposed_activity,evidence,last_applied_updated_at,status)
   values(program.id,job.id,entry->>'source_url',entry->>'occurrence',target,entry->'activity',entry->'evidence',applied,state)
   on conflict(program_id,source_url,occurrence) do update set job_id=excluded.job_id,activity_id=excluded.activity_id,
    proposed_activity=excluded.proposed_activity,evidence=excluded.evidence,last_applied_updated_at=excluded.last_applied_updated_at,checked_at=now(),status=excluded.status;
 end loop;
 result:=jsonb_build_object('found',jsonb_array_length(items),'created',created_count,'updated',updated_count,'review',review_count,'warnings',warnings);
 update public.catalog_collection_jobs set status='succeeded',finished_at=now(),stats=result,usage=run_usage,lease_token=null,lease_until=null where id=job.id;
 return result;
end $$;
revoke all on function public.finish_catalog_collection(uuid,uuid,jsonb,jsonb,jsonb) from public,anon,authenticated;
grant execute on function public.finish_catalog_collection(uuid,uuid,jsonb,jsonb,jsonb) to service_role;

-- Disabled until the subscription worker is configured. 00:00 UTC = 09:00 Asia/Seoul.
select cron.schedule('dearby-daily-program-collection','0 0 * * *',
 $$select public.enqueue_catalog_collection() where (select enabled from public.catalog_collection_settings where id=true)$$);
