-- 2026-10-15 사용자 결정: 활동 수집을 Spring Batch(batch 모듈) + Claude Haiku로 옮긴다.
-- 수집한 활동은 초안(DRAFT)으로 들어가고 관리자가 게시(PUBLISHED)해야 공개 API에 보인다

-- 게시 상태. 기존 활동(V5로 가져온 Let'Swift)은 이미 공개 중이라 PUBLISHED, 새 행의 기본값은 DRAFT
alter table activities add column publication_status varchar(20) not null default 'PUBLISHED'
    check (publication_status in ('DRAFT', 'PUBLISHED'));
alter table activities alter column publication_status set default 'DRAFT';
create index activities_publication_status_idx on activities (publication_status);

-- 프로그램별 수집 설정(2026-10-15 사용자 결정: 새로 입력, Supabase 설정은 가져오지 않음).
-- 수집은 allowed_hosts 안의 https 페이지만 검색·확인한다
create table program_sources
(
    id            bigint generated always as identity primary key,
    program_id    bigint        not null, -- 논리키 programs
    official_url  varchar(2000) not null, -- 수집을 시작할 공식 안내 페이지(https)
    allowed_hosts varchar(253)[] not null, -- 검색·원문 확인을 허용하는 정확한 호스트. 비어 있으면 수집하지 않는다
    enabled       boolean       not null default true,
    created_at    timestamp     not null,
    updated_at    timestamp     not null
);
create index program_sources_program_id_idx on program_sources (program_id);

-- 수집 한 번(프로그램 설정 하나 × 한국 날짜 하루)의 결과. Spring Batch 실행 기록과 별개로 관리자가 보는 기록이다
create table collection_runs
(
    id                  bigint generated always as identity primary key,
    program_source_id   bigint                   not null, -- 논리키 program_sources
    run_date            date                     not null, -- 한국 날짜
    started_at          timestamp with time zone not null,
    finished_at         timestamp with time zone,
    outcome             varchar(20) check (outcome in ('SUCCEEDED', 'FAILED', 'SKIPPED')), -- null: 실행 중
    found_count         integer                  not null default 0, -- 모델이 찾은 후보 수
    saved_count         integer                  not null default 0, -- 원문 확인을 통과해 초안으로 저장한 수
    rejected_reasons    varchar[]                not null default '{}', -- 후보를 뺀 이유(원문에 구절 없음 등)
    error               varchar,
    input_tokens        bigint                   not null default 0,
    output_tokens       bigint                   not null default 0,
    web_search_requests integer                  not null default 0,
    unique (program_source_id, run_date)
);
