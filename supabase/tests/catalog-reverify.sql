-- Re-verification scenario for a DISPOSABLE migrated database only (writes, then deletes, fixture rows).
-- Statements autocommit so now() differs between steps and updated_at preservation is observable.
\set ON_ERROR_STOP 1
insert into auth.users(id,aud,role,raw_app_meta_data) values('00000000-0000-4000-8000-0000000000a1','authenticated','authenticated','{"catalog_admin":"true"}');
insert into public.catalog_organizations(id,name) values('00000000-0000-4000-8000-0000000000b1','Reverify fixture');
insert into public.catalog_programs(id,organization_id,title,collection_hosts) values('00000000-0000-4000-8000-0000000000c1','00000000-0000-4000-8000-0000000000b1','Reverify fixture','{official.example}');
insert into public.catalog_activities(id,program_id,organization_id,title,official_url,publication_status)
values('00000000-0000-4000-8000-0000000000d1','00000000-0000-4000-8000-0000000000c1','00000000-0000-4000-8000-0000000000b1','Fixture 2026','https://official.example/2026','published');

-- Administrator verification with an evidence quote; malformed quotes are rejected.
begin;
select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-0000000000a1","role":"authenticated"}',true), set_config('request.jwt.claim.sub','00000000-0000-4000-8000-0000000000a1',true);
set local role authenticated;
do $$ begin
 begin
  perform public.verify_catalog_activity('00000000-0000-4000-8000-0000000000d1','공식 페이지에서 일정 확인','짧은 구절');
  raise exception 'short quote accepted';
 exception when raise_exception then
  if sqlerrm not like 'Evidence quote must be%' then raise; end if;
 end;
 begin
  perform public.verify_catalog_activity('00000000-0000-4000-8000-0000000000d1','공식 페이지에서 일정 확인','2026년 10월 행사 … 참가 신청 안내입니다');
  raise exception 'ellipsis quote accepted';
 exception when raise_exception then
  if sqlerrm not like 'Evidence quote must be%' then raise; end if;
 end;
 perform public.verify_catalog_activity('00000000-0000-4000-8000-0000000000d1','공식 페이지에서 일정 확인','  공식 프로그램 2026년 하반기 참가 신청 안내입니다.  ');
 if (select quote from public.catalog_activity_evidence) <> '공식 프로그램 2026년 하반기 참가 신청 안내입니다.' then raise exception 'quote not stored trimmed'; end if;
 begin
  perform public.reverify_catalog_activity('00000000-0000-4000-8000-0000000000d1',true);
  raise exception 'administrator can call worker reverify';
 exception when insufficient_privilege then null; end;
end $$;
commit;

-- Make it expire within 3 hours (setup as owner; only verification columns change).
update public.catalog_activities set source_checked_at=now()-interval '22 hours',valid_until=now()+interval '2 hours' where id='00000000-0000-4000-8000-0000000000d1';
do $$ begin
 if jsonb_array_length(public.catalog_reverify_candidates())<>1 or public.catalog_reverify_candidates()->0->>'quote' is null
  or public.catalog_reverify_candidates()->0->'hosts'->>0<>'official.example' then raise exception 'candidate missing'; end if;
end $$;
create temp table snap as select updated_at,valid_until,(select count(*) from public.catalog_audit_log) audits from public.catalog_activities where id='00000000-0000-4000-8000-0000000000d1';

-- Failure: activity untouched, reason recorded, not retried within the hour.
do $$ begin
 if public.reverify_catalog_activity('00000000-0000-4000-8000-0000000000d1',false,null,'BLOCKED: Official page exceeds 3 MB') then raise exception 'failure extended'; end if;
 if (select valid_until from public.catalog_activities where id='00000000-0000-4000-8000-0000000000d1') <> (select valid_until from snap) then raise exception 'failure changed activity'; end if;
 if (select last_check_ok or last_error not like 'BLOCKED:%' from public.catalog_activity_evidence) then raise exception 'failure not recorded'; end if;
 if jsonb_array_length(public.catalog_reverify_candidates())<>0 then raise exception 'retried within the hour'; end if;
end $$;

-- Success: window extended 24h, updated_at preserved, one 'reverify' audit row with verification columns only.
do $$ declare a public.catalog_activities; log public.catalog_audit_log; begin
 if not public.reverify_catalog_activity('00000000-0000-4000-8000-0000000000d1',true,repeat('a',64)) then raise exception 'success not extended'; end if;
 select * into a from public.catalog_activities where id='00000000-0000-4000-8000-0000000000d1';
 if a.updated_at <> (select updated_at from snap) then raise exception 'updated_at bumped'; end if;
 if a.valid_until <> a.source_checked_at+interval '24 hours' or a.source_checked_at <> now() then raise exception 'window not extended'; end if;
 if (select count(*) from public.catalog_audit_log) <> (select audits from snap)+1 then raise exception 'unexpected audit rows'; end if;
 select * into log from public.catalog_audit_log order by id desc limit 1;
 if log.operation<>'reverify' or log.actor_id is not null
  or (select array_agg(k order by k) from jsonb_object_keys(log.after_data) k) <> array['freshness','source_checked_at','valid_until'] then raise exception 'audit shape'; end if;
 if not (select last_check_ok and last_error is null and last_body_sha256=repeat('a',64) from public.catalog_activity_evidence) then raise exception 'success not recorded'; end if;
end $$;

-- An edit after verification resets freshness; re-verification can no longer extend it.
update public.catalog_activities set title='Fixture 2026 edited' where id='00000000-0000-4000-8000-0000000000d1';
do $$ begin
 if public.reverify_catalog_activity('00000000-0000-4000-8000-0000000000d1',true) then raise exception 'edited activity extended'; end if;
 if (select freshness from public.catalog_activities where id='00000000-0000-4000-8000-0000000000d1')<>'stale' then raise exception 'edit kept verified'; end if;
end $$;

-- Verification without a quote removes automatic re-verification.
begin;
select set_config('request.jwt.claims','{"sub":"00000000-0000-4000-8000-0000000000a1","role":"authenticated"}',true), set_config('request.jwt.claim.sub','00000000-0000-4000-8000-0000000000a1',true);
set local role authenticated;
select (public.verify_catalog_activity(activity_id:='00000000-0000-4000-8000-0000000000d1',evidence_note:='공식 페이지에서 다시 확인')).id;
do $$ begin if exists(select 1 from public.catalog_activity_evidence) then raise exception 'evidence kept without quote'; end if; end $$;
commit;

delete from public.catalog_audit_log where record_id in ('00000000-0000-4000-8000-0000000000d1','00000000-0000-4000-8000-0000000000c1','00000000-0000-4000-8000-0000000000b1');
delete from public.catalog_activities where id='00000000-0000-4000-8000-0000000000d1';
delete from public.catalog_programs where id='00000000-0000-4000-8000-0000000000c1';
delete from public.catalog_organizations where id='00000000-0000-4000-8000-0000000000b1';
delete from public.catalog_audit_log where record_id in ('00000000-0000-4000-8000-0000000000d1','00000000-0000-4000-8000-0000000000c1','00000000-0000-4000-8000-0000000000b1');
delete from auth.users where id='00000000-0000-4000-8000-0000000000a1';
select 'catalog-reverify scenario passed';
