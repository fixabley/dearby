-- Preserve usage on failure as well as success, without changing billing route.
drop function public.fail_catalog_collection(uuid,uuid,text,boolean);
create function public.fail_catalog_collection(job_id uuid, token uuid, reason text, blocked boolean default false, run_usage jsonb default '{}') returns void
language plpgsql security definer set search_path = '' as $$
begin
 if reason is null or blocked is null or run_usage is null or jsonb_typeof(run_usage)<>'object' then raise exception 'Failure metadata required'; end if;
 update public.catalog_collection_jobs set status=case when blocked or attempts>=3 then 'blocked' else 'failed' end,
  error=left(reason,2000),usage=run_usage,finished_at=now(),available_at=now()+interval '30 minutes',lease_token=null,lease_until=null
 where id=job_id and status='running' and lease_token=token and lease_until>now();
 if not found then raise exception 'Collection lease lost'; end if;
 if blocked and reason like 'BLOCKED: Codex subscription%' then
  update public.catalog_collection_settings set pause_reason=left(reason,2000) where id;
 end if;
end $$;
revoke all on function public.fail_catalog_collection(uuid,uuid,text,boolean,jsonb) from public,anon,authenticated;
grant execute on function public.fail_catalog_collection(uuid,uuid,text,boolean,jsonb) to service_role;

create or replace function public.resume_catalog_collection(job_id uuid) returns void
language plpgsql security definer set search_path='' as $$
declare target uuid;
begin
 if not public.is_catalog_admin() then raise insufficient_privilege using message='Catalog administrator required'; end if;
 update public.catalog_collection_jobs set status=case when scheduled_day=(now() at time zone 'Asia/Seoul')::date then 'queued' else 'skipped' end,
  attempts=0,available_at=now(),error=null,finished_at=null
 where id=job_id and status in ('failed','blocked') returning program_id into target;
 if not found then raise exception 'Only failed or blocked jobs can be resumed'; end if;
 update public.catalog_collection_settings set pause_reason=null where id;
 perform public.enqueue_catalog_collection(target);
end $$;
