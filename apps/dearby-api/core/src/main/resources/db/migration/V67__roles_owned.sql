-- 역할 귀속(native-v1.md "이력 (History) — 역할 매칭", admin-v1.md "이력 검토·역할", 2026-11-03 밤 결정).
-- roles는 조직·프로그램·활동 중 하나에 귀속되고 이름은 그 단위 안에서 띄어쓰기를 뺀 값으로 유일하다.
-- activity_roles(수집이 공고에서 읽은 모집 역할 문구)는 roles(activity_id, source COLLECTED)로 옮기고 테이블을 지운다.
-- profile_histories는 역할(role_id, 지워지면 null)과 귀속 단위(owner_kind·owner_id 논리키)만 남기되 role_name·owner_name에 이름을 늘 복사해 두어
-- 역할·단위가 지워져도 이력 표시가 비지 않는다. 승인 상태는 이관에서 바꾸지 않는다.

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

-- 3. profile_histories 귀속 단위. 옛 열은 논리키라 지워진 단위를 가리킬 수 있으므로 먼저 끊는다
alter table profile_histories
    add column owner_kind varchar(20),
    add column owner_id   bigint,       -- 논리키 organizations·programs·activities. 이름만 직접 적은 단위면 null
    add column owner_name varchar(200); -- 단위 이름 복사본(지워져도 표시용)
alter table profile_histories alter column role_name type varchar(200);
update profile_histories set activity_id = null where activity_id is not null and not exists (select 1 from activities a where a.id = activity_id);
update profile_histories set program_id = null where program_id is not null and not exists (select 1 from programs p where p.id = program_id);
update profile_histories set organization_id = null where organization_id is not null and not exists (select 1 from organizations o where o.id = organization_id);
update profile_histories set role_id = null where role_id is not null and not exists (select 1 from roles r where r.id = role_id);
-- 직접 적은 이름이 있으면 그 이름을 지킨다(활동 → 프로그램), 없으면 등록된 가장 구체적인 단위, 그것도 없으면 조직 이름
update profile_histories set owner_kind = 'ACTIVITY', owner_name = activity_name where owner_kind is null and activity_id is null and activity_name is not null;
update profile_histories set owner_kind = 'PROGRAM', owner_name = program_name where owner_kind is null and program_id is null and program_name is not null;
update profile_histories h set owner_kind = 'ACTIVITY', owner_id = activity_id, owner_name = a.name from activities a where h.owner_kind is null and a.id = h.activity_id;
update profile_histories h set owner_kind = 'PROGRAM', owner_id = program_id, owner_name = p.name from programs p where h.owner_kind is null and p.id = h.program_id;
update profile_histories h set owner_kind = 'ORGANIZATION', owner_id = organization_id, owner_name = o.name from organizations o where h.owner_kind is null and o.id = h.organization_id;
update profile_histories set owner_kind = 'ORGANIZATION', owner_name = coalesce(organization_name, program_name, activity_name) where owner_kind is null;
-- 역할 이름 복사본(등록 역할을 가리키면 그 이름)
update profile_histories h set role_name = r.name from roles r where r.id = h.role_id;

-- 4. 전역 역할을 가리키는 이력과 승인된 직접 입력 역할은 그 단위 아래 같은 이름의 역할을 만들어 다시 가리킨다(순서는 그 단위의 마지막 다음부터)
with wanted as (
    select distinct on (h.owner_kind, h.owner_id, regexp_replace(h.role_name, '\s', '', 'g'))
           h.owner_kind, h.owner_id, h.role_name as name, r.name_en, h.created_at
    from profile_histories h
             left join roles r on r.id = h.role_id
    where h.owner_id is not null and h.role_name is not null
      and ((r.id is not null and r.organization_id is null and r.program_id is null and r.activity_id is null)
        or (r.id is null and h.approval_status = 'APPROVED'))
      and not exists (select 1
                      from roles o
                      where o.organization_id is not distinct from case when h.owner_kind = 'ORGANIZATION' then h.owner_id end
                        and o.program_id is not distinct from case when h.owner_kind = 'PROGRAM' then h.owner_id end
                        and o.activity_id is not distinct from case when h.owner_kind = 'ACTIVITY' then h.owner_id end
                        and regexp_replace(o.name, '\s', '', 'g') = regexp_replace(h.role_name, '\s', '', 'g'))
    order by h.owner_kind, h.owner_id, regexp_replace(h.role_name, '\s', '', 'g'), h.created_at
)
insert into roles (organization_id, program_id, activity_id, name, name_en, source, position, created_at, updated_at)
select case when w.owner_kind = 'ORGANIZATION' then w.owner_id end,
       case when w.owner_kind = 'PROGRAM' then w.owner_id end,
       case when w.owner_kind = 'ACTIVITY' then w.owner_id end,
       w.name, w.name_en, 'CURATED',
       coalesce((select max(o.position)
                 from roles o
                 where o.organization_id is not distinct from case when w.owner_kind = 'ORGANIZATION' then w.owner_id end
                   and o.program_id is not distinct from case when w.owner_kind = 'PROGRAM' then w.owner_id end
                   and o.activity_id is not distinct from case when w.owner_kind = 'ACTIVITY' then w.owner_id end), -1)
           + row_number() over (partition by w.owner_kind, w.owner_id order by w.created_at),
       now(), now()
from wanted w;

update profile_histories h
set role_id = o.id
from roles o
where h.owner_id is not null and h.role_name is not null
  and o.organization_id is not distinct from case when h.owner_kind = 'ORGANIZATION' then h.owner_id end
  and o.program_id is not distinct from case when h.owner_kind = 'PROGRAM' then h.owner_id end
  and o.activity_id is not distinct from case when h.owner_kind = 'ACTIVITY' then h.owner_id end
  and regexp_replace(o.name, '\s', '', 'g') = regexp_replace(h.role_name, '\s', '', 'g')
  and (h.role_id is null and h.approval_status = 'APPROVED'
    or exists (select 1 from roles r where r.id = h.role_id and r.organization_id is null and r.program_id is null and r.activity_id is null));

-- 5. 전역 역할은 지운다. 단위 없는 이력이 가리키던 것은 이름만 남는다(role_name은 위에서 복사했다)
update profile_histories h set role_id = null from roles r where r.id = h.role_id and r.organization_id is null and r.program_id is null and r.activity_id is null;
delete from roles where organization_id is null and program_id is null and activity_id is null;

-- 6. 제약과 색인. 귀속 열은 정확히 하나, 이름은 단위 안에서 유일(null은 서로 다르므로 열마다 부분 색인). 이력의 role_id는 역할이 지워지면 null(이름은 role_name에 남는다)
alter table roles add constraint roles_owner_check check (num_nonnulls(organization_id, program_id, activity_id) = 1);
create unique index roles_organization_name_uq on roles (organization_id, regexp_replace(name, '\s', '', 'g')) where organization_id is not null;
create unique index roles_program_name_uq on roles (program_id, regexp_replace(name, '\s', '', 'g')) where program_id is not null;
create unique index roles_activity_name_uq on roles (activity_id, regexp_replace(name, '\s', '', 'g')) where activity_id is not null;

alter table profile_histories
    alter column owner_kind set not null,
    add constraint profile_histories_owner_kind_check check (owner_kind in ('ORGANIZATION', 'PROGRAM', 'ACTIVITY')),
    add constraint profile_histories_role_id_fkey foreign key (role_id) references roles (id) on delete set null,
    drop column kind,
    drop column organization_id,
    drop column organization_name,
    drop column program_id,
    drop column program_name,
    drop column activity_id,
    drop column activity_name;
create index profile_histories_role_id_idx on profile_histories (role_id);
