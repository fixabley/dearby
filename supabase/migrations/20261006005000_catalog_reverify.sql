-- Deterministic re-verification of published activities (user decision 2026-10-06, option B).
-- An administrator's verification stores one official quote; the worker re-fetches the official page
-- without Codex and, while the quote is still present, extends valid_until by 24 hours.
create table public.catalog_activity_evidence (
 activity_id uuid primary key references public.catalog_activities(id) on delete cascade,
 quote text not null check(length(quote) between 20 and 200),
 verified_by uuid, verified_at timestamptz not null default now(),
 last_check_at timestamptz, last_check_ok boolean, last_error text, last_body_sha256 text
);
alter table public.catalog_activity_evidence enable row level security;
revoke all on public.catalog_activity_evidence from anon,authenticated;
grant select on public.catalog_activity_evidence to authenticated;
create policy admin_read on public.catalog_activity_evidence for select to authenticated using ((select public.is_catalog_admin()));

-- A re-verification changes only the verification window: it must not bump updated_at (admin edit
-- conflicts) and is audited as 'reverify' by its own function instead of a full-row audit.
create or replace function public.catalog_before_write() returns trigger language plpgsql set search_path = '' as $$
begin
 if tg_table_name = 'catalog_activities' and tg_op = 'UPDATE' and current_setting('dearby.catalog_reverify', true) = 'on'
  and (to_jsonb(new) - array['source_checked_at','valid_until']) = (to_jsonb(old) - array['source_checked_at','valid_until']) then
  return new;
 end if;
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
create or replace function public.catalog_audit_write() returns trigger language plpgsql security definer set search_path = '' as $$
begin
 if current_setting('dearby.catalog_reverify', true) = 'on' then return coalesce(new,old); end if;
 insert into public.catalog_audit_log(actor_id,resource,record_id,operation,before_data,after_data)
 values(auth.uid(),tg_table_name,coalesce(new.id,old.id),tg_op,case when tg_op <> 'INSERT' then to_jsonb(old) end,case when tg_op <> 'DELETE' then to_jsonb(new) end);
 return coalesce(new,old);
end $$;

drop function public.verify_catalog_activity(uuid,text);
create function public.verify_catalog_activity(activity_id uuid, evidence_note text, evidence_quote text default null) returns public.catalog_activities
 language plpgsql security definer set search_path = '' as $$
declare result public.catalog_activities; quote text := trim(evidence_quote); begin
 if not public.is_catalog_admin() then raise insufficient_privilege using message = 'Catalog administrator required'; end if;
 if length(trim(evidence_note)) < 10 then raise exception 'Official evidence note must contain at least 10 characters'; end if;
 if quote is not null and (length(quote) not between 20 and 200 or quote ~ '[\r\n]' or quote like '%…%' or quote like '%...%') then
  raise exception 'Evidence quote must be one 20-200 character passage without line breaks or ellipses';
 end if;
 update public.catalog_activities set source_checked_at=now(),valid_until=now()+interval '24 hours',freshness='verified',source_note=trim(evidence_note)
 where id=activity_id returning * into result;
 if not found then raise exception 'Activity not found'; end if;
 -- Evidence belongs to the latest verification only; no quote means no automatic re-verification.
 if quote is null then
  delete from public.catalog_activity_evidence e where e.activity_id=result.id;
 else
  insert into public.catalog_activity_evidence(activity_id,quote,verified_by,verified_at) values(result.id,quote,auth.uid(),now())
  on conflict on constraint catalog_activity_evidence_pkey do update set quote=excluded.quote,verified_by=excluded.verified_by,verified_at=excluded.verified_at,
   last_check_at=null,last_check_ok=null,last_error=null,last_body_sha256=null;
 end if;
 return result;
end $$;
revoke all on function public.verify_catalog_activity(uuid,text,text) from public;
grant execute on function public.verify_catalog_activity(uuid,text,text) to authenticated;

-- Published, still unedited since verification (an edit resets freshness), expiring within 3 hours,
-- and not checked in the last hour, so a failing page is retried hourly without Codex.
create function public.catalog_reverify_candidates() returns jsonb
language sql stable security definer set search_path = '' as $$
 select coalesce(jsonb_agg(jsonb_build_object('id',a.id,'officialUrl',a.official_url,'quote',e.quote,'hosts',p.collection_hosts) order by a.valid_until),'[]'::jsonb)
 from public.catalog_activities a join public.catalog_activity_evidence e on e.activity_id=a.id join public.catalog_programs p on p.id=a.program_id
 where a.publication_status='published' and a.freshness='verified' and a.valid_until < now()+interval '3 hours'
  and (e.last_check_at is null or e.last_check_at < now()-interval '1 hour');
$$;
revoke all on function public.catalog_reverify_candidates() from public,anon,authenticated;
grant execute on function public.catalog_reverify_candidates() to service_role;

create function public.reverify_catalog_activity(activity_id uuid, ok boolean, body_sha256 text default null, reason text default null) returns boolean
language plpgsql security definer set search_path = '' as $$
declare old_row public.catalog_activities; new_row public.catalog_activities; begin
 if ok is null or (not ok and nullif(trim(reason),'') is null) then raise exception 'Re-verification result required'; end if;
 select * into old_row from public.catalog_activities a where a.id=activity_id for update;
 if not found or not exists(select 1 from public.catalog_activity_evidence e where e.activity_id=old_row.id) then
  raise exception 'No re-verification evidence';
 end if;
 update public.catalog_activity_evidence e set last_check_at=now(),last_check_ok=ok,last_error=case when ok then null else left(reason,2000) end,
  last_body_sha256=left(body_sha256,64) where e.activity_id=old_row.id;
 -- Failure leaves the activity untouched; it expires naturally at valid_until.
 if not ok or old_row.publication_status<>'published' or old_row.freshness<>'verified' then return false; end if;
 perform set_config('dearby.catalog_reverify','on',true);
 update public.catalog_activities a set source_checked_at=now(),valid_until=now()+interval '24 hours' where a.id=old_row.id returning * into new_row;
 perform set_config('dearby.catalog_reverify','',true);
 insert into public.catalog_audit_log(actor_id,resource,record_id,operation,before_data,after_data)
 values(null,'catalog_activities',old_row.id,'reverify',
  jsonb_build_object('source_checked_at',old_row.source_checked_at,'valid_until',old_row.valid_until,'freshness',old_row.freshness),
  jsonb_build_object('source_checked_at',new_row.source_checked_at,'valid_until',new_row.valid_until,'freshness',new_row.freshness));
 return true;
end $$;
revoke all on function public.reverify_catalog_activity(uuid,boolean,text,text) from public,anon,authenticated;
grant execute on function public.reverify_catalog_activity(uuid,boolean,text,text) to service_role;
