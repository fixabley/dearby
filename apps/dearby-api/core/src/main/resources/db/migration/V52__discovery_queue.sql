-- 2026-10-24 맞춤 대기열(스토리형 쇼츠)·조직 친화도·활동 벡터(shared/contracts/native-v1.md "맞춤 대기열(스토리형 쇼츠)·조직 친화도·활동 벡터").
-- 운영 id를 적지 않는다. 벡터는 pgvector(운영·개발·테스트 DB 모두 pgvector/pgvector:pg17)
create extension if not exists vector;

-- 대기열 장면 ①·②: 수집이 공개 설명으로 만들고 운영자가 고친다(curated_fields의 hook·highlights)
alter table activities
    add column hook       varchar(100),
    add column highlights jsonb; -- [{icon, text}] 최대 3개. icon은 cost·period·team·mentor·award·place·talk·ticket

-- 활동 벡터. 모델을 바꿀 때 새 모델 벡터를 다 만든 뒤 읽기를 넘기도록 (활동, 모델)마다 한 행을 둔다(activities 행을 읽을 때 2KB 벡터를 끌고 다니지 않는다).
-- source_hash = sha256(모델 + 임베딩 글): 글이 바뀐 활동만 다시 임베딩한다
create table activity_embeddings
(
    activity_id bigint                   not null references activities (id) on delete cascade,
    model       varchar(200)             not null,
    embedding   halfvec(1024)            not null,
    source_hash char(64)                 not null,
    updated_at  timestamp with time zone not null,
    primary key (activity_id, model)
);

-- 태그 이름 사전 벡터(신호가 없는 사용자의 취향 = 추천 설정 태그 벡터 평균)
create table tag_embeddings
(
    tag_id      bigint                   not null references tags (id) on delete cascade,
    model       varchar(200)             not null,
    embedding   halfvec(1024)            not null,
    source_hash char(64)                 not null,
    updated_at  timestamp with time zone not null,
    primary key (tag_id, model)
);

-- 지금 읽는 모델(한 행). 배치가 새 모델로 게시 활동·태그를 모두 임베딩한 뒤에만 바꾼다. 행이 없으면 벡터 점수를 쓰지 않는다
create table embedding_settings
(
    id           boolean primary key default true check (id),
    active_model varchar(200)             not null,
    updated_at   timestamp with time zone not null
);

-- 프로그램 커버(세로 4:5, 1080×1350 권장, PNG·JPEG·WebP 350KB 이하). 로고처럼 DB에 둔다. enabled=false면 공개하지 않는다(주최 측 요청)
create table program_covers
(
    program_id   bigint primary key references programs (id) on delete cascade,
    content_type varchar(100)             not null,
    data         bytea                    not null,
    source_page  varchar(2000),  -- 이미지를 확인한 공식 페이지
    source_image varchar(2000),  -- 원본 이미지 주소
    enabled      boolean                  not null default true,
    updated_at   timestamp with time zone not null
);

-- 대기열 반응과 내 활동에서 지움(REMOVE). (사용자, 활동, 종류)마다 한 행이고 다시 오면 시각만 새로 한다(같은 화면을 여러 번 봐도 가중치가 불지 않는다).
-- 사용자 데이터는 서버 밖으로 보내지 않는다. 계정·활동을 지우면 함께 지워진다
create table recommendation_signals
(
    user_id     uuid                     not null references users (id) on delete cascade,
    activity_id bigint                   not null references activities (id) on delete cascade,
    kind        varchar(20)              not null check (kind in ('LIKE', 'DISMISS', 'LESS_FIELD', 'HIDE_PROGRAM', 'INELIGIBLE', 'COMPLETE', 'SKIP_EARLY', 'OPEN_DETAIL', 'REMOVE')),
    tag_id      bigint references tags (id) on delete cascade, -- LESS_FIELD의 분야
    created_at  timestamp with time zone not null,
    primary key (user_id, activity_id, kind)
);

-- 조직 친화도 0~1(대기열을 만들 때 다시 계산)
create table user_org_affinity
(
    user_id         uuid                     not null references users (id) on delete cascade,
    organization_id bigint                   not null references organizations (id) on delete cascade,
    score           real                     not null check (score between 0 and 1),
    updated_at      timestamp with time zone not null,
    primary key (user_id, organization_id)
);

-- 사용자 취향 벡터(활동·태그 벡터로만 계산, 글을 임베딩하지 않는다). model이 지금 읽는 모델과 다르면 비교하지 않고 다시 만든다
create table user_taste
(
    user_id    uuid primary key references users (id) on delete cascade,
    model      varchar(200)             not null,
    embedding  halfvec(1024)            not null,
    updated_at timestamp with time zone not null
);

-- 날짜(서울)별 대기열. 더 받기는 같은 날짜 뒤 position으로 이어 붙인다. 지난 날짜는 다시 만들 때 지운다
create table discovery_queue
(
    user_id      uuid                     not null references users (id) on delete cascade,
    queue_date   date                     not null,
    position     smallint                 not null,
    activity_id  bigint                   not null references activities (id) on delete cascade,
    reasons      jsonb                    not null, -- 사람이 읽는 이유 문구 배열
    generated_at timestamp with time zone not null,
    primary key (user_id, queue_date, position)
);
