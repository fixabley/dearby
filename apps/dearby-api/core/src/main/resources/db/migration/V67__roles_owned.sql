-- 역할 귀속(native-v1.md "이력 (History) — 역할 매칭", admin-v1.md "이력 검토·역할", 2026-11-03 밤 결정).
-- roles는 조직·프로그램·활동 중 하나에 귀속되고 이름은 그 단위 안에서 띄어쓰기를 뺀 값으로 유일하다.
-- activity_roles(수집이 공고에서 읽은 모집 역할 문구)는 roles(activity_id, source COLLECTED)로 옮기고 테이블을 지운다.
-- profile_histories는 역할 하나(role_id 또는 직접 적은 role_name)와 귀속 단위(owner_kind·owner_id·owner_name)만 남긴다.

-- 1. roles 귀속 열. 이름은 activity_roles.name(200자)을 받도록 넓힌다
alter table roles
    add column organization_id bigint references organizations (id) on delete cascade,
    add column program_id      bigint references programs (id) on delete cascade,
    add column activity_id     bigint references activities (id) on delete cascade,
    add column source          varchar(20) not null default 'CURATED' check (source in ('CURATED', 'COLLECTED')),
    add column position        integer     not null default 0;
alter table roles alter column name type varchar(200);
alter table roles alter column source drop default;
alter table roles alter column position drop default;
drop index roles_name_uq;

-- 2. activity_roles → roles. 같은 활동 안에서 띄어쓰기만 다른 이름은 앞 순서 하나만 남긴다
insert into roles (activity_id, name, source, position, created_at, updated_at)
select distinct on (activity_id, regexp_replace(name, '\s', '', 'g')) activity_id, name, 'COLLECTED', position, now(), now()
from activity_roles
order by activity_id, regexp_replace(name, '\s', '', 'g'), position;
drop table activity_roles;

-- 3. profile_histories 귀속 단위: kind=ACTIVITY는 activity_id, 아니면 program_id → organization_id 순
alter table profile_histories
    add column owner_kind varchar(20),
    add column owner_id   bigint,       -- 논리키 organizations·programs·activities. 이름만 직접 적은 단위면 null(승인 대기)
    add column owner_name varchar(200); -- 직접 적은 귀속 단위 이름(owner_id가 null일 때만)
update profile_histories set owner_kind = 'ACTIVITY', owner_id = activity_id where kind = 'ACTIVITY' and activity_id is not null;
-- 활동 이름만 직접 적은 활동 이력은 그 이름을 지키려고 단위 없는 활동 귀속(승인 대기)으로 옮긴다. 조직·프로그램 id는 승인 때 관리자가 활동을 고르면서 다시 채워지므로 버린다
update profile_histories
set owner_kind = 'ACTIVITY', owner_name = activity_name, approval_status = 'PENDING'
where owner_kind is null and kind = 'ACTIVITY' and activity_name is not null;
update profile_histories set owner_kind = 'PROGRAM', owner_id = program_id where owner_kind is null and program_id is not null;
update profile_histories set owner_kind = 'ORGANIZATION', owner_id = organization_id where owner_kind is null and organization_id is not null;
-- 등록된 단위가 하나도 없는 직접 입력 이력은 지우지 않고 조직 이름만 남겨 승인 대기로 둔다(2026-11-03 밤 조율 결정: 사용자 데이터·인증자료 보존)
update profile_histories
set owner_kind = 'ORGANIZATION', owner_name = organization_name, approval_status = 'PENDING'
where owner_kind is null;
-- 단위 없는 이력이 전역 역할을 가리키면 이름만 남긴다(전역 역할은 아래에서 지운다)
update profile_histories h
set role_id = null, role_name = r.name
from roles r
where r.id = h.role_id and h.owner_id is null;

-- 4. 전역 역할을 가리키는 이력과 승인된 직접 입력 역할은 그 단위 아래 같은 이름의 역할을 만들어 다시 가리킨다
insert into roles (organization_id, program_id, activity_id, name, name_en, source, position, created_at, updated_at)
select distinct on (h.owner_kind, h.owner_id, regexp_replace(coalesce(r.name, h.role_name), '\s', '', 'g'))
       case when h.owner_kind = 'ORGANIZATION' then h.owner_id end,
       case when h.owner_kind = 'PROGRAM' then h.owner_id end,
       case when h.owner_kind = 'ACTIVITY' then h.owner_id end,
       coalesce(r.name, h.role_name), r.name_en, 'CURATED', 0, now(), now()
from profile_histories h
         left join roles r on r.id = h.role_id
where h.owner_id is not null
  and ((r.id is not null and r.organization_id is null and r.program_id is null and r.activity_id is null)
    or (r.id is null and h.role_name is not null and h.approval_status = 'APPROVED'))
  and not exists (select 1
                  from roles o
                  where o.organization_id is not distinct from case when h.owner_kind = 'ORGANIZATION' then h.owner_id end
                    and o.program_id is not distinct from case when h.owner_kind = 'PROGRAM' then h.owner_id end
                    and o.activity_id is not distinct from case when h.owner_kind = 'ACTIVITY' then h.owner_id end
                    and regexp_replace(o.name, '\s', '', 'g') = regexp_replace(coalesce(r.name, h.role_name), '\s', '', 'g'))
order by h.owner_kind, h.owner_id, regexp_replace(coalesce(r.name, h.role_name), '\s', '', 'g'), h.created_at;

update profile_histories h
set role_id = o.id, role_name = null
from roles o
where h.owner_id is not null
  and o.organization_id is not distinct from case when h.owner_kind = 'ORGANIZATION' then h.owner_id end
  and o.program_id is not distinct from case when h.owner_kind = 'PROGRAM' then h.owner_id end
  and o.activity_id is not distinct from case when h.owner_kind = 'ACTIVITY' then h.owner_id end
  and regexp_replace(o.name, '\s', '', 'g') = regexp_replace(coalesce((select r.name from roles r where r.id = h.role_id), h.role_name), '\s', '', 'g')
  and ((h.role_id is null and h.approval_status = 'APPROVED')
    or exists (select 1 from roles r where r.id = h.role_id and r.organization_id is null and r.program_id is null and r.activity_id is null));

-- 5. 전역 역할은 더 이상 아무 이력도 가리키지 않으므로 지운다. 승인됐지만 역할·단위가 없는 이력은 다시 승인 대기
delete from roles where organization_id is null and program_id is null and activity_id is null;
update profile_histories set approval_status = 'PENDING' where approval_status = 'APPROVED' and (owner_id is null or role_id is null);

-- 6. 제약과 색인. 귀속 열은 정확히 하나, 이름은 단위 안에서 유일(null은 서로 다르므로 열마다 부분 색인)
alter table roles add constraint roles_owner_check check (num_nonnulls(organization_id, program_id, activity_id) = 1);
create unique index roles_organization_name_uq on roles (organization_id, regexp_replace(name, '\s', '', 'g')) where organization_id is not null;
create unique index roles_program_name_uq on roles (program_id, regexp_replace(name, '\s', '', 'g')) where program_id is not null;
create unique index roles_activity_name_uq on roles (activity_id, regexp_replace(name, '\s', '', 'g')) where activity_id is not null;

alter table profile_histories
    alter column owner_kind set not null,
    add constraint profile_histories_owner_kind_check check (owner_kind in ('ORGANIZATION', 'PROGRAM', 'ACTIVITY')),
    add constraint profile_histories_approved_check check (approval_status <> 'APPROVED' or (owner_id is not null and role_id is not null)),
    drop column kind,
    drop column organization_id,
    drop column organization_name,
    drop column program_id,
    drop column program_name,
    drop column activity_id,
    drop column activity_name;
create index profile_histories_role_id_idx on profile_histories (role_id);
