-- Additive repair of the already applied WIP migration. No data reset.
alter table public.catalog_collection_settings add column pause_reason text;
create or replace function public.valid_collection_hosts(hosts text[]) returns boolean
language sql immutable set search_path='' as $$
 select cardinality(hosts)<=20 and not exists(select 1 from unnest(hosts) h
  where h is null or length(h)>253 or h !~ '^([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z]([a-z0-9-]*[a-z0-9])?$');
$$;
alter table public.catalog_programs add constraint valid_collection_hosts check(public.valid_collection_hosts(collection_hosts));
create or replace function public.claim_catalog_collection(target_program uuid default null) returns jsonb
language plpgsql security definer set search_path = '' as $$
declare job public.catalog_collection_jobs; program public.catalog_programs; begin
 if not pg_try_advisory_xact_lock(72491029) then return null; end if;
 -- Expired owners cannot write results; at most one subscription task runs at once.
 update public.catalog_collection_jobs set status=case when attempts>=3 then 'blocked' else 'failed' end,
  error='Worker lease expired',lease_token=null,lease_until=null,available_at=now()+interval '5 minutes'
 where status='running' and lease_until<now();
 if exists(select 1 from public.catalog_collection_settings where pause_reason is not null) then return null; end if;
 if exists(select 1 from public.catalog_collection_jobs where status='running') then return null; end if;
 select j.* into job from public.catalog_collection_jobs j join public.catalog_programs p on p.id=j.program_id
 where j.status in ('queued','failed') and j.attempts<3 and j.scheduled_day=(now() at time zone 'Asia/Seoul')::date and j.available_at<=now() and p.collection_enabled
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
create or replace function public.fail_catalog_collection(job_id uuid, token uuid, reason text, blocked boolean default false) returns void
language plpgsql security definer set search_path = '' as $$
begin
 if reason is null or blocked is null then raise exception 'Failure reason required'; end if;
 update public.catalog_collection_jobs set status=case when blocked or attempts>=3 then 'blocked' else 'failed' end,
  error=left(reason,2000),finished_at=now(),available_at=now()+interval '30 minutes',lease_token=null,lease_until=null
 where id=job_id and status='running' and lease_token=token and lease_until>now();
 if not found then raise exception 'Collection lease lost'; end if;
 if blocked and reason like 'BLOCKED: Codex subscription%' then
  update public.catalog_collection_settings set pause_reason=left(reason,2000) where id;
 end if;
end $$;
revoke all on function public.fail_catalog_collection(uuid,uuid,text,boolean) from public,anon,authenticated;
grant execute on function public.fail_catalog_collection(uuid,uuid,text,boolean) to service_role;

create or replace function public.finish_catalog_collection(job_id uuid, token uuid, items jsonb, run_usage jsonb default '{}', warnings jsonb default '[]') returns jsonb
language plpgsql security definer set search_path = '' as $$
declare job public.catalog_collection_jobs; program public.catalog_programs; entry jsonb; source public.catalog_collection_results;
 activity public.catalog_activities; proposed public.catalog_activities; target uuid; applied timestamptz;
 created_count integer:=0; updated_count integer:=0; review_count integer:=0; state text; result jsonb; host text; loses_information boolean; duplicates jsonb;
begin
 select * into job from public.catalog_collection_jobs where id=job_id for update;
 if not found or job.status<>'running' or job.lease_token is distinct from token or job.lease_until<=now() then raise exception 'Collection lease lost'; end if;
 select * into program from public.catalog_programs where id=job.program_id;
 if not program.collection_enabled then raise exception 'Collection disabled for program'; end if;
 if items is null or jsonb_typeof(items)<>'array' or jsonb_array_length(items)>10 then raise exception 'Invalid collection result'; end if;
 if run_usage is null or jsonb_typeof(run_usage)<>'object' or warnings is null or jsonb_typeof(warnings)<>'array' then raise exception 'Invalid run metadata'; end if;
 if jsonb_array_length(items)=0 and jsonb_array_length(warnings)>0 then raise exception 'All candidates failed source verification'; end if;
 if exists(select 1 from jsonb_array_elements(items) e group by e->>'source_url',e->>'occurrence' having count(*)>1) then raise exception 'Duplicate collection identity'; end if;
 for entry in select * from jsonb_array_elements(items) loop
  host:=lower(substring(entry->>'source_url' from '^https://([^/:?#]+)'));
  if host is null or not host=any(program.collection_hosts)
   or (entry->>'source_url') !~ '^https://[a-zA-Z0-9.-]+(/[^[:space:]]*)?$'
   or coalesce(length(trim(entry->>'occurrence')),0) not between 1 and 200
   or jsonb_typeof(entry->'activity') is distinct from 'object'
   or jsonb_typeof(entry->'evidence') is distinct from 'object'
   or coalesce(length(entry#>>'{evidence,quote}'),0) not between 20 and 200
   or coalesce(length(trim(entry#>>'{activity,title}')),0) not between 1 and 300
   or not ((entry->'activity') ?& array['summary','participation_type','recruitment_status','date_label','roles','schedules'])
   or coalesce(entry#>>'{activity,participation_type}','') not in ('registration','selection')
   or coalesce(entry#>>'{activity,recruitment_status}','') not in ('open','scheduled','closed','unknown')
   or jsonb_typeof(entry#>'{activity,roles}') is distinct from 'array'
   or jsonb_typeof(entry#>'{activity,schedules}') is distinct from 'array'
   or (entry->'activity') - array['title','summary','participation_type','recruitment_status','recruitment_start_at','recruitment_end_at','date_label','location','cost','audience','qualification','roles','schedules','application_url'] <> '{}'::jsonb
   then raise exception 'Invalid official source or activity'; end if;
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
  -- A newly unknown/empty field is a proposal, never an erasure of known data.
  loses_information:=exists(select 1 from jsonb_each(entry->'activity') e
   where e.value in ('null'::jsonb,'""'::jsonb,'[]'::jsonb,'"unknown"'::jsonb)
    and to_jsonb(activity)->e.key not in ('null'::jsonb,'""'::jsonb,'[]'::jsonb,'"unknown"'::jsonb));
  duplicates:=coalesce((select jsonb_agg(id) from public.catalog_activities
   where program_id=program.id and id is distinct from target
    and (official_url=entry->>'source_url' or title=proposed.title)),'[]'::jsonb);
  entry:=jsonb_set(entry,'{evidence}',(entry->'evidence') || jsonb_build_object('possible_duplicates',duplicates,'verification_scope','quote_presence_only'));

  if activity.id is null then
   insert into public.catalog_activities(program_id,organization_id,title,summary,participation_type,recruitment_status,
    recruitment_start_at,recruitment_end_at,date_label,location,cost,audience,qualification,roles,schedules,official_url,application_url,source_note)
   values(program.id,program.organization_id,proposed.title,coalesce(proposed.summary,''),coalesce(proposed.participation_type,'registration'),
    coalesce(proposed.recruitment_status,'unknown'),proposed.recruitment_start_at,proposed.recruitment_end_at,coalesce(proposed.date_label,''),
    proposed.location,proposed.cost,proposed.audience,proposed.qualification,coalesce(proposed.roles,'{}'),coalesce(proposed.schedules,'[]'),
    entry->>'source_url',proposed.application_url,'자동 수집 초안 · 공식 원문과 모집 상태를 관리자가 확인해야 합니다.') returning id,updated_at into target,applied;
   created_count:=created_count+1;
  elsif source.last_applied_updated_at is not null and activity.updated_at=source.last_applied_updated_at
   and activity.publication_status='draft' and activity.source_checked_at is null and not loses_information then
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


create function public.resume_catalog_collection(job_id uuid) returns void
language plpgsql security definer set search_path='' as $$
begin
 if not public.is_catalog_admin() then raise insufficient_privilege using message='Catalog administrator required'; end if;
 update public.catalog_collection_jobs set status='queued',attempts=0,available_at=now(),error=null,finished_at=null
 where id=job_id and status in ('failed','blocked') and scheduled_day=(now() at time zone 'Asia/Seoul')::date;
 if not found then raise exception 'Only failed or blocked jobs from today can be resumed'; end if;
 update public.catalog_collection_settings set pause_reason=null where id;
end $$;
revoke all on function public.resume_catalog_collection(uuid) from public,anon;
grant execute on function public.resume_catalog_collection(uuid) to authenticated;
