-- 2026-10-22 조직 로고(admin-v1.md "조직 로고", native-v1.md "조직 로고와 로고 계승"): 프로그램 로고가 없으면 조직·상위 조직 로고를 쓴다.
-- 프로그램 로고(V38)와 같은 모양. 조직을 지우면 함께 지워진다
create table organization_logos
(
    organization_id bigint primary key references organizations (id) on delete cascade,
    content_type    varchar(100)             not null,
    data            bytea                    not null,
    updated_at      timestamp with time zone not null
);
