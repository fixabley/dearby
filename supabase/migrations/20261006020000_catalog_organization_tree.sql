-- Nested organizations (catalog-v1 "Organization hierarchy — 2026-10-06"): at most 4 levels, no cycles.
-- Deletion of an organization with child organizations or programs is refused by the foreign keys.
alter table public.catalog_organizations
 add column parent_id uuid references public.catalog_organizations(id) on delete restrict;
create index catalog_organizations_parent on public.catalog_organizations(parent_id);

create function public.catalog_organization_tree_guard() returns trigger language plpgsql set search_path = '' as $$
declare ancestors integer; cyclic boolean; height integer;
begin
 if new.parent_id is null or (tg_op = 'UPDATE' and new.parent_id is not distinct from old.parent_id) then return new; end if;
 -- Serialize tree moves so two concurrent reparents cannot commit a cycle neither one saw.
 perform pg_advisory_xact_lock(hashtext('public.catalog_organizations.parent_id'));
 with recursive chain(id, parent_id, level) as (
  select o.id, o.parent_id, 1 from public.catalog_organizations o where o.id = new.parent_id
  union
  select o.id, o.parent_id, c.level + 1 from public.catalog_organizations o join chain c on o.id = c.parent_id where c.level < 5
 ) select count(*), coalesce(bool_or(id = new.id), false) into ancestors, cyclic from chain;
 if cyclic then
  raise exception 'Organization cannot be its own ancestor' using errcode = 'check_violation';
 end if;
 with recursive subtree(id, level) as (
  select new.id, 1
  union all
  select o.id, s.level + 1 from public.catalog_organizations o join subtree s on o.parent_id = s.id where s.level < 5
 ) select max(level) into height from subtree;
 if ancestors + height > 4 then
  raise exception 'Organization hierarchy cannot exceed 4 levels' using errcode = 'check_violation';
 end if;
 return new;
end $$;
create trigger organization_tree before insert or update of parent_id on public.catalog_organizations
 for each row execute function public.catalog_organization_tree_guard();
