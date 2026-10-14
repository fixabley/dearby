-- 기존 Supabase 카탈로그를 한 번 가져온다(catalog_public_snapshot, generatedAt 2026-10-14T11:53:19Z).
-- 이후 카탈로그는 Kotlin DB가 원본이다. 대응 규칙은 V2 머리 주석과 열 주석을 따른다

insert into organizations (source_id, parent_organization_id, name, created_at, updated_at)
values ('73669b82-f4f9-5c6c-becc-9b6de6ba271f', null, 'Let’Swift 준비위원회', localtimestamp, localtimestamp);

insert into programs (source_id, organization_id, name, created_at, updated_at)
select '2e8633be-5cac-5d8e-a4c0-42d2e89185db', id, 'Let’Swift', localtimestamp, localtimestamp
from organizations
where source_id = '73669b82-f4f9-5c6c-becc-9b6de6ba271f';

insert into activities (source_id, program_id, name, description, participation_type, recruitment_status,
                        location, cost, audience, official_url, application_url,
                        source_checked_at, valid_until, freshness, source_note, created_at, updated_at)
select '01b773dc-5e13-4d14-a182-9642b47d596e',
       id,
       'Let''Swift 2026 · 개발 환경의 변화 워크숍',
       '68k Macintosh, NeXT, PPC Classic, Intel OS X 체험과 개발 환경 변화 세션으로 구성된 워크숍입니다.',
       'REGISTRATION',
       'OPEN',
       '강남 코드스쿼드 오피스, 서울 강남구 강남대로62길 23 4층',
       null,
       'Swift를 다루는 시니어, 주니어, 학생 등',
       'https://letswift.kr/2026/',
       'https://ticketa.co/e/vdb8aajs/l/website',
       timestamp with time zone '2026-10-06 05:09:06.235653+00:00',
       timestamp with time zone '2026-10-07 05:09:06.235653+00:00',
       'VERIFIED',
       '',
       localtimestamp,
       localtimestamp
from programs
where source_id = '2e8633be-5cac-5d8e-a4c0-42d2e89185db';

-- 모집 기간(recruitmentStartAt·EndAt) → application 일정
insert into schedules (activity_id, slot, title, kind, status, time_type, start_at, end_at, time_zone,
                       date_label, sequence, created_at, updated_at)
select id, 'APPLICATION', '신청 기간', 'DEADLINE', 'CONFIRMED', 'TIMED',
       timestamp with time zone '2026-10-01 05:07:00+00:00',
       timestamp with time zone '2026-10-07 15:00:00+00:00',
       'Asia/Seoul', '', 0, localtimestamp, localtimestamp
from activities
where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e';

-- dateLabel → 활동 기간 일정(날짜 미정, 문구만)
insert into schedules (activity_id, slot, title, kind, status, date_label, sequence, created_at, updated_at)
select id, 'ACTIVITY', name, 'EVENT', 'CONFIRMED', '2026-10-08', 0, localtimestamp, localtimestamp
from activities
where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e';

-- schedules[]
insert into schedules (activity_id, source_id, title, kind, status, time_type, start_at, end_at, time_zone,
                       date_label, sequence, created_at, updated_at)
select id, '73eed714-9a87-5417-9905-4dbc90de7b75', 'Let''Swift 2026 · 개발 환경의 변화 워크숍', 'EVENT', 'CONFIRMED', 'TIMED',
       timestamp with time zone '2026-10-08 10:00:00+00:00',
       timestamp with time zone '2026-10-08 13:00:00+00:00',
       'Asia/Seoul', '2026-10-08', 0, localtimestamp, localtimestamp
from activities
where source_id = '01b773dc-5e13-4d14-a182-9642b47d596e';
