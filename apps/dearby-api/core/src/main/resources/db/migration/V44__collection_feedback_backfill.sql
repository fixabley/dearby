-- 2026-10-20 수집 피드백 채우기: 지금까지 남은 운영자 수정에서 되살릴 수 있는 것만 넣는다. 여러 번 실행해도 같은 행을 두 번 넣지 않는다.
-- 되살릴 수 없는 것: 삭제한 초안(dismissed_activity_sources에는 출처 id만 있고 이름은 활동과 함께 지워졌다), 운영자가 고치기 전의 수집 값(curated_fields에는 필드 이름만 있다)

-- 날짜 문구 → 운영자가 지정한 날짜(지운 일정 제외)
insert into collection_feedback (kind, program_id, host, source_text, corrected_value, reason, activity_id, created_at)
select 'DATE_CURATED', a.program_id, lower(substring(a.official_url from '^https?://([^/:?#]+)')), s.date_label,
       jsonb_strip_nulls(jsonb_build_object('scheduleId', s.id, 'slot', s.slot, 'title', s.title, 'startDate', s.start_date, 'endDate', s.end_date_exclusive - 1, 'startAt', s.start_at, 'endAt', s.end_at)),
       nullif(s.curated_note, ''), a.id, s.updated_at
from schedules s
         join activities a on a.id = s.activity_id
where s.curated
  and not s.curated_deleted
  and s.date_label <> ''
  and (s.start_date is not null or s.start_at is not null)
  and not exists (select 1 from collection_feedback f where f.kind = 'DATE_CURATED' and (f.corrected_value ->> 'scheduleId')::bigint = s.id);

-- 운영자가 지정한 필드(수집한 활동만). 고치기 전 값은 없어 운영자 값만 남긴다(from 없음)
insert into collection_feedback (kind, program_id, host, source_text, corrected_value, activity_id, created_at)
select 'FIELD_CURATED', a.program_id, lower(substring(a.official_url from '^https?://([^/:?#]+)')), a.name,
       jsonb_build_object('field', f.field, 'to', case f.field
           when 'name' then to_jsonb(a.name)
           when 'category' then to_jsonb(a.category)
           when 'eligibility' then a.eligibility
           else (select coalesce(jsonb_agg(t.name order by t.sort_order), '[]'::jsonb) from activity_tags at join tags t on t.id = at.tag_id where at.activity_id = a.id)
           end),
       a.id, a.updated_at
from activities a
         cross join lateral jsonb_array_elements_text(a.curated_fields) as f(field)
where a.source_id is not null
  and f.field in ('name', 'category', 'eligibility', 'tags')
  and not exists (select 1 from collection_feedback c where c.kind = 'FIELD_CURATED' and c.activity_id = a.id and c.corrected_value ->> 'field' = f.field);

-- 조심 목록. 항목을 지우면 이 행도 지운다(watchEntryId)
insert into collection_feedback (kind, program_id, source_text, corrected_value, reason, created_at)
select 'WATCH_' || w.kind, w.program_id, w.name,
       jsonb_strip_nulls(jsonb_build_object('watchEntryId', w.id, 'canonicalActivityName', c.name)), w.reason, w.created_at
from collection_watchlist w
         left join activities c on c.id = w.canonical_activity_id
where not exists (select 1 from collection_feedback f where f.kind like 'WATCH\_%' and (f.corrected_value ->> 'watchEntryId')::bigint = w.id);
