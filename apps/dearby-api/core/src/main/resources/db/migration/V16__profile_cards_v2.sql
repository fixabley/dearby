-- 2026-10-16 사용자 결정: 프로필·명함 v2(native-v1.md "프로필·명함 v2"). 옛 TS API 이후 비어 있던 프로필·명함·공유·지갑을 Kotlin이 제공한다.
-- 운영의 아래 표는 모두 비어 있었다(2026-10-16 읽기 전용 확인). 클라이언트가 UUID id를 쓰므로 UUID 키로 다시 만든다.
-- 이력은 단계별 입력(조직/활동 → 조직 → 프로그램 → 활동 → 역할 → 기간)이라 profile_activity_histories를 대체한다
drop table if exists business_card_activity_histories;
drop table if exists business_card_contacts;
drop table if exists business_cards;
drop table if exists profile_activity_history_roles;
drop table if exists profile_activity_histories;
drop table if exists contacts;
drop table if exists profiles;

create table profiles
(
    user_id      uuid primary key, -- 논리키 users
    name         varchar(100)             not null,
    job          varchar(100)             not null,
    introduction varchar                  not null, -- 마크다운 템플릿({{profile.name}}, {{contact:<id>}})
    version      integer                  not null, -- 저장마다 +1. profile_versions.version과 같다
    created_at   timestamp with time zone not null,
    updated_at   timestamp with time zone not null
);

-- id는 클라이언트가 만든 UUID. PUT /v1/profile이 전체를 바꾼다
create table contacts
(
    id       uuid primary key,
    user_id  uuid         not null references users (id) on delete cascade,
    kind     varchar(20)  not null check (kind in ('PHONE', 'EMAIL', 'KAKAO', 'INSTAGRAM', 'GITHUB', 'BEHANCE')),
    label    varchar(100) not null,
    value    varchar(500) not null,
    position integer      not null
);
create index contacts_user_id_idx on contacts (user_id);

-- 역할 카탈로그(이력의 역할). Role 도메인이 없던 자리. 이름은 띄어쓰기를 뺀 값으로 유일(V12 규칙)
create table roles
(
    id         bigint generated always as identity primary key,
    name       varchar(100)             not null,
    name_en    varchar(100),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);
create unique index roles_name_uq on roles (regexp_replace(name, '\s', '', 'g'));

-- 카탈로그 id가 있으면 그것을, 없으면 직접 입력한 *_name을 쓴다. 직접 입력이 있으면 approval_status = PENDING(관리자 승인 전 명함 불가)
create table profile_histories
(
    id                  uuid primary key,
    user_id             uuid                     not null references users (id) on delete cascade,
    kind                varchar(20)              not null check (kind in ('ORGANIZATION', 'ACTIVITY')),
    organization_id     bigint, -- 논리키 organizations
    organization_name   varchar(200),
    program_id          bigint, -- 논리키 programs
    program_name        varchar(200),
    activity_id         bigint, -- 논리키 activities
    activity_name       varchar(200),
    role_id             bigint, -- 논리키 roles
    role_name           varchar(100),
    start_date          date                     not null,
    end_date            date,
    description         varchar                  not null,
    position            integer                  not null,
    approval_status     varchar(20)              not null check (approval_status in ('APPROVED', 'PENDING', 'REJECTED')),
    approval_reason     varchar(500),
    verification_status varchar(20)              not null check (verification_status in ('UNVERIFIED', 'PENDING', 'VERIFIED', 'REJECTED')),
    created_at          timestamp with time zone not null,
    updated_at          timestamp with time zone not null
);
create index profile_histories_user_id_idx on profile_histories (user_id);
create index profile_histories_review_idx on profile_histories (approval_status, verification_status);

-- 인증자료. 파일은 DB에 둔다(이력당 3개, 5MB). 명함·공유에는 싣지 않는다
create table history_evidence
(
    id           uuid primary key,
    history_id   uuid                     not null references profile_histories (id) on delete cascade,
    user_id      uuid                     not null,
    file_name    varchar(255)             not null,
    content_type varchar(100)             not null,
    size         integer                  not null,
    data         bytea                    not null,
    uploaded_at  timestamp with time zone not null
);
create index history_evidence_history_id_idx on history_evidence (history_id);

-- 저장마다 남기는 프로필 전체 스냅샷(Profile JSON). 되돌리기는 새 버전을 만든다
create table profile_versions
(
    user_id    uuid                     not null references users (id) on delete cascade,
    version    integer                  not null,
    snapshot   jsonb                    not null,
    created_at timestamp with time zone not null,
    primary key (user_id, version)
);

-- 발행 시점 스냅샷. introduction은 템플릿을 채운 결과, contacts·histories는 공개 선택분의 JSON
create table business_cards
(
    id           uuid primary key,
    user_id      uuid                     not null, -- 논리키 users
    name         varchar(200)             not null,
    description  varchar                  not null,
    profile_name varchar(100)             not null,
    job          varchar(100)             not null,
    introduction varchar                  not null,
    contacts     jsonb                    not null,
    histories    jsonb                    not null,
    revoked      boolean                  not null default false,
    seq          bigint generated always as identity,
    created_at   timestamp with time zone not null
);
create index business_cards_user_id_idx on business_cards (user_id);

-- 공유 기록. activities는 공유 시점의 {id(숫자), title} 복사본
create table card_shares
(
    id         uuid primary key,
    card_id    uuid                     not null references business_cards (id) on delete cascade,
    activities jsonb                    not null,
    created_at timestamp with time zone not null
);
create index card_shares_card_id_idx on card_shares (card_id);

-- 받은 명함(지갑). 같은 사용자+명함은 하나
create table receipts
(
    id           uuid primary key,
    recipient_id uuid                     not null references users (id) on delete cascade,
    card_id      uuid                     not null, -- 논리키 business_cards(지운 명함은 지갑에서 빠진다)
    context      jsonb                    not null,
    received_at  timestamp with time zone not null,
    seq          bigint generated always as identity,
    unique (recipient_id, card_id)
);

-- 받은 명함에 연결된 공유 기록(명함당 20개)
create table wallet_shares
(
    receipt_id uuid                     not null references receipts (id) on delete cascade,
    share_id   uuid                     not null, -- 논리키 card_shares
    card_id    uuid                     not null,
    saved_at   timestamp with time zone not null,
    seq        bigint generated always as identity,
    primary key (receipt_id, share_id)
);
