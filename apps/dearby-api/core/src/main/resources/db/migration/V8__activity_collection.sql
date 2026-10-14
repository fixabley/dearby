-- 2026-10-15 사용자 결정: 활동 수집을 Spring Batch(batch 모듈) + Claude Haiku로 옮긴다.
-- 수집한 활동은 초안(DRAFT)으로 들어가고 관리자가 게시(PUBLISHED)해야 공개 API에 보인다

-- 게시 상태. 기존 활동(V5로 가져온 Let'Swift)은 이미 공개 중이라 PUBLISHED, 새 행의 기본값은 DRAFT
alter table activities add column publication_status varchar(20) not null default 'PUBLISHED'
    check (publication_status in ('DRAFT', 'PUBLISHED'));
alter table activities alter column publication_status set default 'DRAFT';
create index activities_publication_status_idx on activities (publication_status);

-- 수집한 활동의 출처가 프로그램의 공식 도메인(허용 호스트)인지. 웹 전체 검색에서 나온 초안은 false로, 관리자가 출처를 확인해야 한다
alter table activities add column official_source boolean not null default true;

-- 프로그램별 수집 설정(2026-10-15 사용자 결정). 모든 프로그램이 수집 대상이고, 이 행이 없으면 아래 기본값을 쓴다.
-- allowed_hosts가 있으면 그 호스트 안에서만 검색·원문 확인, 비어 있으면 "프로그램 이름 + 기관 이름"으로 웹 전체를 검색한다.
-- interval_days: 수집에 성공한 날부터 이 일수 동안은 다시 수집하지 않는다(1 = 매일)
create table program_collection_settings
(
    program_id    bigint primary key, -- 논리키 programs
    official_url  varchar(2000),  -- 수집을 시작할 공식 안내 페이지(https). 없으면 검색 결과만 쓴다
    allowed_hosts varchar(253)[] not null default '{}', -- 검색·원문 확인을 허용하는 정확한 호스트
    interval_days integer        not null default 1 check (interval_days >= 1),
    enabled       boolean        not null default true,
    created_at    timestamp      not null,
    updated_at    timestamp      not null
);

-- 수집 한 번(프로그램 하나 × 한국 날짜 하루)의 결과. Spring Batch 실행 기록과 별개로 관리자가 보는 기록이다
create table collection_runs
(
    id                  bigint generated always as identity primary key,
    program_id          bigint                   not null, -- 논리키 programs
    run_date            date                     not null, -- 한국 날짜
    official_only       boolean                  not null, -- 허용 호스트 안에서만 수집했는지(false: 웹 전체 검색)
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
    unique (program_id, run_date)
);
create index collection_runs_program_id_run_date_idx on collection_runs (program_id, run_date desc);
